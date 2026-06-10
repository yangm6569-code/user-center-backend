package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.UnauthorizedException
import cn.scysn.usercenter.domain.model.*
import cn.scysn.usercenter.domain.provider.JwtTokenProvider
import cn.scysn.usercenter.infrastructure.config.UserCenterSecurityProperties
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.LoginSessionEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.AccessApplicationJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.LoginSessionJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserAccountJpaRepository
import cn.scysn.usercenter.infrastructure.tool.TokenHashing
import cn.scysn.usercenter.interfaces.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthApplicationService(
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val accessApplicationJpaRepository: AccessApplicationJpaRepository,
    private val loginSessionJpaRepository: LoginSessionJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenHashing: TokenHashing,
    private val jwtTokenProvider: JwtTokenProvider,
    private val authorizationApplicationService: AuthorizationApplicationService,
    private val userAccountApplicationService: UserAccountApplicationService,
    private val auditApplicationService: AuditApplicationService,
    private val securityProperties: UserCenterSecurityProperties,
) {
    @Transactional
    fun login(request: LoginByPasswordDTO, metadata: RequestMetadata?): LoginTokenVO {
        val user = userAccountJpaRepository.findByUsernameAndDeletedFalse(request.username)
            ?: run {
                auditApplicationService.record(
                    AuditCategory.LOGIN,
                    "LOGIN_FAILED",
                    metadata = metadata,
                    result = AuditResult.FAILURE,
                    reason = "bad_credentials",
                    payload = mapOf("username" to request.username),
                )
                throw UnauthorizedException("账号或密码错误")
            }

        val now = Instant.now()
        val blockedReason = when {
            user.status != UserStatus.ACTIVE -> "user_not_active"
            user.isFrozen(now) -> "user_frozen"
            user.isLocked(now) -> "user_locked"
            else -> null
        }
        if (blockedReason != null) {
            auditApplicationService.record(AuditCategory.LOGIN, "LOGIN_FAILED", user.id, user.id, request.clientId, metadata, AuditResult.FAILURE, blockedReason)
            throw UnauthorizedException("账号状态不允许登录")
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            user.failedLoginCount += 1
            if (user.failedLoginCount >= securityProperties.loginFailureThreshold) {
                user.lockedUntil = now.plusSeconds(securityProperties.loginLockMinutes * 60)
            }
            user.touch()
            auditApplicationService.record(
                AuditCategory.LOGIN,
                if (user.lockedUntil != null) "LOGIN_LOCKED" else "LOGIN_FAILED",
                user.id,
                user.id,
                request.clientId,
                metadata,
                AuditResult.FAILURE,
                "bad_credentials",
                payload = mapOf("failedLoginCount" to user.failedLoginCount),
            )
            throw UnauthorizedException("账号或密码错误")
        }

        val app = request.clientId?.let {
            accessApplicationJpaRepository.findByClientId(it) ?: throw UnauthorizedException("应用不存在: $it")
        }
        if (app?.status == ApplicationStatus.DISABLED) {
            throw UnauthorizedException("应用已禁用")
        }

        user.failedLoginCount = 0
        user.lockedUntil = null
        user.lastLoginAt = now
        user.touch()

        val refreshToken = tokenHashing.newOpaqueToken()
        val refreshExpiresAt = now.plusSeconds(app?.refreshTokenTtlSeconds ?: securityProperties.refreshTokenTtlSeconds)
        val session = loginSessionJpaRepository.save(
            LoginSessionEntity().apply {
                this.user = user
                this.application = app
                refreshTokenHash = tokenHashing.sha256(refreshToken)
                ip = metadata?.ip
                userAgent = metadata?.userAgent
                expiresAt = refreshExpiresAt
            },
        )
        val effective = authorizationApplicationService.effectivePermission(user.id ?: 0)
        val principal = LoginPrincipal(
            userId = user.id ?: 0,
            username = user.username,
            sessionId = session.id ?: 0,
            clientId = app?.clientId,
            roles = effective.roles.map { it.code },
        )
        val accessToken = jwtTokenProvider.generate(principal, app?.accessTokenTtlSeconds ?: securityProperties.accessTokenTtlSeconds)
        auditApplicationService.record(AuditCategory.LOGIN, "LOGIN_SUCCESS", user.id, user.id, app?.clientId, metadata)
        auditApplicationService.record(AuditCategory.TOKEN, "TOKEN_ISSUED", user.id, session.id, app?.clientId, metadata)
        return LoginTokenVO(
            accessToken = accessToken.token,
            expiresAt = accessToken.expiresAt,
            refreshToken = refreshToken,
            refreshExpiresAt = refreshExpiresAt,
            user = user.toVO(),
            roles = effective.roles,
            permissions = effective.permissions,
        )
    }

    @Transactional
    fun refresh(request: RefreshTokenDTO, metadata: RequestMetadata?): LoginTokenVO {
        val session = loginSessionJpaRepository.findByRefreshTokenHash(tokenHashing.sha256(request.refreshToken))
            ?: throw UnauthorizedException("Refresh Token无效")
        val now = Instant.now()
        if (session.status != SessionStatus.ACTIVE || session.expiresAt.isBefore(now)) {
            throw UnauthorizedException("Refresh Token已过期")
        }
        val user = session.user
        if (user.status != UserStatus.ACTIVE || user.isFrozen(now)) {
            session.status = SessionStatus.REVOKED
            session.revokedAt = now
            session.touch()
            throw UnauthorizedException("账号状态不允许刷新Token")
        }
        val newRefreshToken = tokenHashing.newOpaqueToken()
        session.refreshTokenHash = tokenHashing.sha256(newRefreshToken)
        session.expiresAt = now.plusSeconds(session.application?.refreshTokenTtlSeconds ?: securityProperties.refreshTokenTtlSeconds)
        session.touch()
        val effective = authorizationApplicationService.effectivePermission(user.id ?: 0)
        val principal = LoginPrincipal(user.id ?: 0, user.username, session.id ?: 0, session.application?.clientId, effective.roles.map { it.code })
        val accessToken = jwtTokenProvider.generate(principal, session.application?.accessTokenTtlSeconds ?: securityProperties.accessTokenTtlSeconds)
        auditApplicationService.record(AuditCategory.TOKEN, "TOKEN_REFRESHED", user.id, session.id, session.application?.clientId, metadata)
        return LoginTokenVO(
            accessToken = accessToken.token,
            expiresAt = accessToken.expiresAt,
            refreshToken = newRefreshToken,
            refreshExpiresAt = session.expiresAt,
            user = user.toVO(),
            roles = effective.roles,
            permissions = effective.permissions,
        )
    }

    @Transactional
    fun logout(request: LogoutDTO, principal: LoginPrincipal?, metadata: RequestMetadata?) {
        val session = request.refreshToken?.let {
            loginSessionJpaRepository.findByRefreshTokenHash(tokenHashing.sha256(it))
        } ?: principal?.let { loginSessionJpaRepository.findById(it.sessionId).orElse(null) }
            ?: throw UnauthorizedException()
        session.status = SessionStatus.REVOKED
        session.revokedAt = Instant.now()
        session.touch()
        auditApplicationService.record(AuditCategory.LOGIN, "LOGOUT", session.user.id, session.id, session.application?.clientId, metadata)
    }

    @Transactional(readOnly = true)
    fun me(principal: LoginPrincipal): CurrentUserVO {
        val user = userAccountJpaRepository.findById(principal.userId).orElseThrow { UnauthorizedException() }
        val effective = authorizationApplicationService.effectivePermission(principal.userId)
        return CurrentUserVO(user.toVO(), effective.roles, effective.permissions)
    }

    @Transactional
    fun register(request: UserAccountRegisterDTO, metadata: RequestMetadata?): UserAccountVO =
        userAccountApplicationService.register(request, metadata)
}
