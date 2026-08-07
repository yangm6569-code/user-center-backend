package cn.scysn.iam.application

import cn.scysn.common.api.PageResponse
import cn.scysn.common.api.TraceIds
import cn.scysn.common.error.ErrorCode
import cn.scysn.common.error.businessError
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.support.PasswordHasher
import cn.scysn.iam.application.support.TokenIssuer
import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.AuthorizationCode
import cn.scysn.iam.domain.auth.CurrentUserInfo
import cn.scysn.iam.domain.auth.JwkSet
import cn.scysn.iam.domain.auth.LoginResult
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.auth.LoginUserSummary
import cn.scysn.iam.domain.auth.SsoSession
import cn.scysn.iam.domain.auth.TokenSet
import cn.scysn.iam.domain.authorization.BusinessDomain
import cn.scysn.iam.domain.authorization.DataScope
import cn.scysn.iam.domain.authorization.DataScopeConfig
import cn.scysn.iam.domain.authorization.EffectivePermission
import cn.scysn.iam.domain.authorization.FieldPermission
import cn.scysn.iam.domain.authorization.FieldPermissionType
import cn.scysn.iam.domain.authorization.FieldPolicy
import cn.scysn.iam.domain.authorization.MenuNode
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.ResourceType
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.authorization.RoleType
import cn.scysn.iam.domain.clientapp.AppStatus
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.clientapp.OidcClientConfig
import cn.scysn.iam.domain.clientapp.SecretRotation
import cn.scysn.iam.domain.clientapp.TokenPolicy
import cn.scysn.iam.domain.identity.AccountType
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.identity.UserStatus
import cn.scysn.iam.domain.organization.OrgUnit
import cn.scysn.iam.domain.ports.AppSearchQuery
import cn.scysn.iam.domain.ports.AuditRepository
import cn.scysn.iam.domain.ports.AuditSearchQuery
import cn.scysn.iam.domain.ports.AuthorizationCodeRepository
import cn.scysn.iam.domain.ports.BusinessDomainRepository
import cn.scysn.iam.domain.ports.BusinessDomainSearchQuery
import cn.scysn.iam.domain.ports.ClientAppRepository
import cn.scysn.iam.domain.ports.DataScopeConfigRepository
import cn.scysn.iam.domain.ports.FieldPermissionRepository
import cn.scysn.iam.domain.ports.FieldPermissionSearchQuery
import cn.scysn.iam.domain.ports.OrgUnitRepository
import cn.scysn.iam.domain.ports.PermissionRepository
import cn.scysn.iam.domain.ports.PermissionSearchQuery
import cn.scysn.iam.domain.ports.ResourceRepository
import cn.scysn.iam.domain.ports.ResourceSearchQuery
import cn.scysn.iam.domain.ports.RoleRepository
import cn.scysn.iam.domain.ports.RoleSearchQuery
import cn.scysn.iam.domain.ports.SessionRepository
import cn.scysn.iam.domain.ports.SessionSearchQuery
import cn.scysn.iam.domain.ports.SsoSessionRepository
import cn.scysn.iam.domain.ports.SystemDomainRepository
import cn.scysn.iam.domain.ports.SystemDomainSearchQuery
import cn.scysn.iam.domain.ports.UserRepository
import cn.scysn.iam.domain.ports.UserSearchQuery
import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.system.SystemDomain
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class AuthApplicationService(
    private val userRepository: UserRepository,
    private val appRepository: ClientAppRepository,
    private val roleRepository: RoleRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val sessionRepository: SessionRepository,
    private val ssoSessionRepository: SsoSessionRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    private val refreshTokenIndex = ConcurrentHashMap<String, String>()

    fun login(command: LoginCommand): LoginResult {
        val user = userRepository.findByUsername(command.username)
            ?: invalidCredentials(command.clientId, command.username, command.ip, command.userAgent)
        val app = appRepository.findByClientId(command.clientId)
            ?: businessError(ErrorCode.NOT_FOUND, "鎺ュ叆搴旂敤涓嶅瓨鍦? ${command.clientId}")
        if (!passwordHasher.matches(command.password, user.passwordHash)) {
            invalidCredentials(command.clientId, command.username, command.ip, command.userAgent)
        }
        ensureLoginAllowed(user)
        ensureUserCanAccessApp(user, app)
        val session = LoginSession.create(
            userId = user.id,
            clientId = app.clientId,
            deviceId = command.deviceId,
            ip = command.ip,
            userAgent = command.userAgent,
            ttlSeconds = app.tokenPolicy.ssoSessionMaxSeconds
        )
        user.markLogin()
        userRepository.save(user)
        sessionRepository.save(session)
        val ssoSession = SsoSession.create(user.id, app.tokenPolicy.ssoSessionMaxSeconds)
        ssoSessionRepository.save(ssoSession)
        val tokenSet = issueTokens(user, app.clientId, session.id, app.tokenPolicy)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGIN_SUCCESS",
                actorId = user.id,
                targetId = user.id,
                clientId = app.clientId,
                traceId = TraceIds.current(),
                ip = command.ip,
                userAgent = command.userAgent
            )
        )
        return LoginResult(
            tokenType = tokenSet.tokenType,
            accessToken = tokenSet.accessToken,
            expiresIn = tokenSet.expiresIn,
            refreshToken = tokenSet.refreshToken,
            refreshExpiresIn = tokenSet.refreshExpiresIn,
            sessionId = tokenSet.sessionId,
            idToken = tokenSet.idToken,
            ssoSessionId = ssoSession.id,
            ssoSessionExpiresIn = remainingSeconds(ssoSession.expiresAt),
            requiredActions = user.requiredActions.map { it.code },
            user = LoginUserSummary(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                employeeNo = user.profile.employeeNo
            )
        )
    }

    fun refresh(command: RefreshTokenCommand): TokenSet {
        val sessionId = refreshTokenIndex[command.refreshToken]
            ?: businessError(ErrorCode.TOKEN_INVALID, "Refresh Token 鏃犳晥鎴栧凡浣跨敤")
        val session = sessionRepository.findSessionById(sessionId)
            ?: businessError(ErrorCode.SESSION_REVOKED)
        if (session.status.code != "active") {
            businessError(ErrorCode.SESSION_REVOKED)
        }
        val app = appRepository.findByClientId(command.clientId)
            ?: businessError(ErrorCode.NOT_FOUND, "鎺ュ叆搴旂敤涓嶅瓨鍦? ${command.clientId}")
        validateClientSecretForToken(app, command.clientSecret)
        if (!refreshTokenIndex.remove(command.refreshToken, sessionId)) {
            businessError(ErrorCode.TOKEN_INVALID, "Refresh Token 鏃犳晥鎴栧凡浣跨敤")
        }
        if (session.clientId != app.clientId) {
            businessError(ErrorCode.TOKEN_INVALID, "Refresh Token clientId mismatch")
        }
        val user = userRepository.findUserById(session.userId)
            ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        ensureLoginAllowed(user)
        session.touch()
        sessionRepository.save(session)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "token",
                eventType = "TOKEN_REFRESHED",
                actorId = session.userId,
                targetId = session.id,
                clientId = session.clientId,
                traceId = TraceIds.current()
            )
        )
        return issueTokens(user, app.clientId, session.id, app.tokenPolicy)
    }

    fun renewSsoSession(ssoSessionId: String?): SsoCookieRenewal? {
        val session = ssoSessionId
            ?.takeIf { it.isNotBlank() }
            ?.let { ssoSessionRepository.findSsoSessionById(it) }
            ?: return null
        if (!session.isActive()) {
            return null
        }
        session.touch()
        ssoSessionRepository.save(session)
        val maxAgeSeconds = remainingSeconds(session.expiresAt)
        return if (maxAgeSeconds > 0) SsoCookieRenewal(session.id, maxAgeSeconds) else null
    }

    fun logout(refreshToken: String): Map<String, Any> {
        val sessionId = refreshTokenIndex.remove(refreshToken)
            ?: businessError(ErrorCode.TOKEN_INVALID, "Refresh Token 鏃犳晥")
        val session = sessionRepository.findSessionById(sessionId)
            ?: businessError(ErrorCode.SESSION_REVOKED)
        session.revoke("鐢ㄦ埛涓诲姩閫€鍑?")
        sessionRepository.save(session)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGOUT",
                actorId = session.userId,
                targetId = session.id,
                clientId = session.clientId,
                traceId = TraceIds.current()
            )
        )
        return mapOf("sessionId" to session.id, "revoked" to true)
    }

    fun logoutAll(userId: String, reason: String?): Map<String, Int> {
        val sessions = sessionRepository.findActiveByUserId(userId)
        sessions.forEach {
            it.revoke(reason)
            sessionRepository.save(it)
        }
        val revokedSsoSessions = revokeActiveSsoSessions(userId)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGOUT_ALL",
                actorId = userId,
                targetId = userId,
                clientId = null,
                reason = reason,
                traceId = TraceIds.current()
            )
        )
        return mapOf("revokedSessions" to sessions.size, "revokedSsoSessions" to revokedSsoSessions)
    }

    fun currentUser(userId: String): CurrentUserInfo {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "褰撳墠鐢ㄦ埛涓嶅瓨鍦?")
        return CurrentUserInfo(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            employeeNo = user.profile.employeeNo,
            email = user.email,
            phone = user.phone,
            factoryCode = user.profile.factoryCode,
            departmentCode = user.profile.departmentCode,
            status = user.status.code,
            requiredActions = user.requiredActions.map { it.code }
        )
    }

    fun ensureUserCanAccessApp(userId: String, appId: String) {
        val app = appRepository.findByClientId(appId) ?: businessError(ErrorCode.NOT_FOUND, "鎺ュ叆搴旂敤涓嶅瓨鍦? $appId")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        ensureUserCanAccessApp(user, app)
    }

    fun authorize(
        ssoSessionId: String?,
        clientId: String,
        redirectUri: String,
        responseType: String,
        scope: String?,
        nonce: String?,
    ): AuthorizationCode {
        require(responseType == "code") { "棣栫増浠呮敮鎸?response_type=code" }
        val app = appRepository.findByClientId(clientId) ?: businessError(ErrorCode.NOT_FOUND, "鎺ュ叆搴旂敤涓嶅瓨鍦? $clientId")
        require(app.status == AppStatus.ACTIVE) { "鎺ュ叆搴旂敤宸茬鐢?" }
        require(app.redirectUris.contains(redirectUri)) { "redirect_uri 鏈湪鎺ュ叆搴旂敤鐧藉悕鍗曚腑" }
        val ssoSession = ssoSessionId
            ?.let { ssoSessionRepository.findSsoSessionById(it) }
            ?: businessError(ErrorCode.UNAUTHORIZED, "缂哄皯鏈夋晥 SSO 鐧诲綍鎬?")
        if (!ssoSession.isActive()) {
            businessError(ErrorCode.UNAUTHORIZED, "SSO 鐧诲綍鎬佸凡澶辨晥")
        }
        ssoSession.touch()
        ssoSessionRepository.save(ssoSession)
        ensureUserCanAccessApp(ssoSession.userId, app.clientId)
        return authorizationCodeRepository.save(
            AuthorizationCode.create(
                userId = ssoSession.userId,
                clientId = app.clientId,
                redirectUri = redirectUri,
                scope = scope,
                nonce = nonce
            )
        )
    }

    fun exchangeAuthorizationCode(codeValue: String, clientId: String, redirectUri: String, clientSecret: String?): TokenSet {
        val app = appRepository.findByClientId(clientId) ?: businessError(ErrorCode.NOT_FOUND, "鎺ュ叆搴旂敤涓嶅瓨鍦? $clientId")
        validateClientSecretForToken(app, clientSecret)
        val code = authorizationCodeRepository.findAuthorizationCode(codeValue)
            ?: businessError(ErrorCode.BAD_REQUEST, "鎺堟潈鐮佷笉瀛樺湪")
        code.consume(clientId, redirectUri)
        authorizationCodeRepository.save(code)
        val user = userRepository.findUserById(code.userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        ensureLoginAllowed(user)
        ensureUserCanAccessApp(user, app)
        val session = LoginSession.create(
            userId = user.id,
            clientId = app.clientId,
            deviceId = null,
            ip = null,
            userAgent = null,
            ttlSeconds = app.tokenPolicy.ssoSessionMaxSeconds
        )
        sessionRepository.save(session)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "token",
                eventType = "TOKEN_ISSUED",
                actorId = user.id,
                targetId = session.id,
                clientId = app.clientId,
                traceId = TraceIds.current()
            )
        )
        return issueTokens(user, app.clientId, session.id, app.tokenPolicy, code.nonce)
    }

    fun jwks(): JwkSet = JwkSet(tokenIssuer.jwks())

    fun oidcConfiguration(): Map<String, Any> {
        val issuer = tokenIssuer.issuer()
        return linkedMapOf(
            "issuer" to issuer,
            "authorization_endpoint" to "$issuer/oauth/authorize",
            "token_endpoint" to "$issuer/oauth/token",
            "userinfo_endpoint" to "$issuer/oauth/userinfo",
            "jwks_uri" to "$issuer/oauth/jwks",
            "response_types_supported" to listOf("code"),
            "grant_types_supported" to listOf("authorization_code", "refresh_token"),
            "subject_types_supported" to listOf("public"),
            "id_token_signing_alg_values_supported" to listOf("RS256"),
            "token_endpoint_auth_methods_supported" to listOf("client_secret_post", "client_secret_basic", "none"),
            "scopes_supported" to listOf("openid", "profile", "email"),
            "claims_supported" to listOf(
                "sub",
                "iss",
                "aud",
                "exp",
                "iat",
                "auth_time",
                "preferred_username",
                "name",
                "email",
                "email_verified",
                "nonce",
                "phone_number",
                "employee_no"
            )
        )
    }

    fun userInfoFromAccessToken(accessToken: String): Map<String, Any?> {
        val claims = runCatching { tokenIssuer.verifyAndReadClaims(accessToken) }
            .getOrElse { businessError(ErrorCode.TOKEN_INVALID, "Access Token 鏃犳晥鎴栧凡杩囨湡") }
        val userId = claims["sub"] as? String
            ?: businessError(ErrorCode.TOKEN_INVALID, "Access Token 缂哄皯 sub")
        val user = userRepository.findUserById(userId)
            ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        ensureLoginAllowed(user)
        return linkedMapOf(
            "sub" to user.id,
            "preferred_username" to user.username,
            "name" to user.displayName,
            "email" to user.email,
            "email_verified" to (user.email != null),
            "phone_number" to user.phone,
            "employee_no" to user.profile.employeeNo
        )
    }

    fun changePassword(command: ChangePasswordCommand): Map<String, Boolean> {
        require(command.newPassword == command.confirmPassword) { "涓ゆ杈撳叆鐨勬柊瀵嗙爜涓嶄竴鑷?" }
        val user = userRepository.findUserById(command.userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        if (!passwordHasher.matches(command.oldPassword, user.passwordHash)) {
            businessError(ErrorCode.INVALID_CREDENTIALS)
        }
        user.changePassword(passwordHasher.hash(command.newPassword))
        userRepository.save(user)
        logoutAll(user.id, "淇敼瀵嗙爜鍚庨€€鍑哄叾浠栦細璇?")
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "user",
                eventType = "PASSWORD_CHANGED",
                actorId = user.id,
                targetId = user.id,
                clientId = null,
                traceId = TraceIds.current()
            )
        )
        return mapOf("changed" to true, "logoutOtherSessions" to true)
    }

    fun requestPasswordReset(): Map<String, Boolean> = mapOf("accepted" to true)

    fun confirmPasswordReset(resetToken: String, newPassword: String, confirmPassword: String): Map<String, Boolean> {
        require(resetToken.isNotBlank()) { "resetToken 涓嶈兘涓虹┖" }
        require(newPassword == confirmPassword) { "涓ゆ杈撳叆鐨勬柊瀵嗙爜涓嶄竴鑷?" }
        return mapOf("changed" to true)
    }

    private fun issueTokens(
        user: User,
        clientId: String,
        sessionId: String,
        tokenPolicy: TokenPolicy,
        nonce: String? = null,
    ): TokenSet {
        val refreshToken = tokenIssuer.issueRefreshToken()
        refreshTokenIndex[refreshToken] = sessionId
        return TokenSet(
            accessToken = tokenIssuer.issueAccessToken(
                userId = user.id,
                audience = clientId,
                authorizedParty = clientId,
                sessionId = sessionId,
                ttlSeconds = tokenPolicy.accessTokenTtlSeconds
            ),
            expiresIn = tokenPolicy.accessTokenTtlSeconds,
            refreshToken = refreshToken,
            refreshExpiresIn = tokenPolicy.refreshTokenMaxSeconds,
            sessionId = sessionId,
            idToken = tokenIssuer.issueIdToken(
                userId = user.id,
                audience = clientId,
                authorizedParty = clientId,
                sessionId = sessionId,
                ttlSeconds = tokenPolicy.accessTokenTtlSeconds,
                username = user.username,
                displayName = user.displayName,
                email = user.email,
                nonce = nonce
            )
        )
    }

    private fun validateClientSecretForToken(app: ClientApp, clientSecret: String?) {
        if (!app.requiresClientSecret()) {
            return
        }
        val secret = clientSecret?.takeIf { it.isNotBlank() }
            ?: businessError(ErrorCode.UNAUTHORIZED, "client_secret 涓嶈兘涓虹┖")
        val currentMatches = app.secretHash?.let { passwordHasher.matches(secret, it) } ?: false
        val previousMatches = app.previousSecretHash
            ?.takeIf { app.previousSecretExpiresAt?.isAfter(OffsetDateTime.now()) == true }
            ?.let { passwordHasher.matches(secret, it) }
            ?: false
        if (!currentMatches && !previousMatches) {
            if (app.secretHash == null && app.previousSecretHash == null) {
                businessError(ErrorCode.UNAUTHORIZED, "瀹㈡埛绔瘑閽ユ湭鍒濆鍖栵紝璇峰厛杞崲瀵嗛挜")
            }
            businessError(ErrorCode.UNAUTHORIZED, "client_secret 鏍￠獙澶辫触")
        }
        app.clearExpiredPreviousSecret()
        appRepository.save(app)
    }

    private fun remainingSeconds(expiresAt: OffsetDateTime): Long {
        return Duration.between(OffsetDateTime.now(), expiresAt).seconds.coerceAtLeast(0)
    }

    private fun ensureLoginAllowed(user: User) {
        when (user.status) {
            UserStatus.DISABLED -> businessError(ErrorCode.USER_DISABLED)
            UserStatus.FROZEN -> businessError(ErrorCode.USER_FROZEN)
            UserStatus.ARCHIVED -> businessError(ErrorCode.USER_ARCHIVED)
            UserStatus.PENDING, UserStatus.ACTIVE -> Unit
        }
        user.ensureCanLogin()
    }

    private fun ensureUserCanAccessApp(user: User, app: ClientApp) {
        val now = OffsetDateTime.now()
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(now) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(now) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        val canAccess = (directRoleIds + inheritedRoleIds)
            .mapNotNull { roleRepository.findRoleById(it) }
            .any { it.enabled && (it.domainId == app.domainId || it.appId == app.clientId) }
        if (!canAccess) {
            businessError(ErrorCode.FORBIDDEN, "鐢ㄦ埛娌℃湁璁块棶绯荤粺鍩?${app.domainId} 鐨勬潈闄?")
        }
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }

    private fun revokeActiveSsoSessions(userId: String): Int {
        val ssoSessions = ssoSessionRepository.findActiveSsoByUserId(userId)
        ssoSessions.forEach {
            it.revoke()
            ssoSessionRepository.save(it)
        }
        return ssoSessions.size
    }

    private fun invalidCredentials(clientId: String, username: String, ip: String?, userAgent: String?): Nothing {
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGIN_FAILURE",
                actorId = null,
                targetId = username,
                clientId = clientId,
                result = "failure",
                reason = ErrorCode.INVALID_CREDENTIALS.name,
                traceId = TraceIds.current(),
                ip = ip,
                userAgent = userAgent
            )
        )
        businessError(ErrorCode.INVALID_CREDENTIALS)
    }
}

@Service
class UserApplicationService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
) {
    fun page(keyword: String?, status: String?, orgUnitId: String?, page: PageQuery): PageResponse<User> {
        return userRepository.search(UserSearchQuery(keyword, status, orgUnitId, page))
    }

    fun create(command: CreateUserCommand): User {
        if (userRepository.existsByUsername(command.username)) businessError(ErrorCode.USERNAME_EXISTS)
        command.employeeNo?.let { if (userRepository.existsByEmployeeNo(it)) businessError(ErrorCode.EMPLOYEE_NO_EXISTS) }
        command.email?.let { if (userRepository.existsByEmail(it)) businessError(ErrorCode.EMAIL_EXISTS) }
        command.phone?.let { if (userRepository.existsByPhone(it)) businessError(ErrorCode.PHONE_EXISTS) }
        val user = User.create(
            username = command.username,
            displayName = command.displayName,
            employeeNo = command.employeeNo,
            email = command.email,
            phone = command.phone,
            accountType = parseAccountType(command.accountType),
            factoryCode = command.factoryCode,
            departmentCode = command.departmentCode,
            orgUnitIds = command.orgUnitIds,
            passwordHash = passwordHasher.hash(command.initialPassword),
            forceChangePassword = command.forceChangePassword,
            attributes = command.attributes
        )
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_CREATED", null, user.id, null, traceId = TraceIds.current()))
        return user
    }

    fun detail(id: String): User = userRepository.findUserById(id) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")

    fun update(id: String, command: UpdateUserCommand): User {
        val user = detail(id)
        user.updateProfile(
            displayName = command.displayName,
            email = command.email,
            phone = command.phone,
            factoryCode = command.factoryCode,
            departmentCode = command.departmentCode,
            orgUnitIds = command.orgUnitIds,
            attributes = command.attributes
        )
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_UPDATED", null, user.id, null, traceId = TraceIds.current()))
        return user
    }

    fun enable(id: String, reason: String): User {
        val user = detail(id)
        user.enable(reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_ENABLED", null, user.id, null, reason = reason, traceId = TraceIds.current()))
        return user
    }

    fun disable(id: String, command: RevokeUserCommand): Map<String, Any> {
        val user = detail(id)
        user.disable(command.reason)
        val revoked = revokeSessionsIfNeeded(user.id, command.revokeSessions, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_DISABLED", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return mapOf("id" to user.id, "status" to user.status.code, "revokedSessions" to revoked)
    }

    fun freeze(id: String, command: FreezeUserCommand): User {
        val user = detail(id)
        user.freeze(command.freezeUntil, command.freezeReason)
        revokeSessionsIfNeeded(user.id, command.revokeSessions, command.freezeReason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_FROZEN", null, user.id, null, reason = command.freezeReason, traceId = TraceIds.current()))
        return user
    }

    fun unfreeze(id: String, reason: String): User {
        val user = detail(id)
        user.unfreeze(reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_UNFROZEN", null, user.id, null, reason = reason, traceId = TraceIds.current()))
        return user
    }

    fun archive(id: String, command: RevokeUserCommand): User {
        val user = detail(id)
        user.archive(command.reason)
        revokeSessionsIfNeeded(user.id, command.revokeSessions, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "USER_ARCHIVED", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return user
    }

    fun resetPassword(id: String, command: ResetPasswordCommand): Map<String, Any> {
        val user = detail(id)
        val rawPassword = when (command.mode) {
            "temporary_password" -> command.temporaryPassword ?: throw IllegalArgumentException("涓存椂瀵嗙爜涓嶈兘涓虹┖")
            "reset_link" -> "ResetLink@${newId("pwd").takeLast(12)}"
            else -> throw IllegalArgumentException("mode 鍙兘鏄?temporary_password 鎴?reset_link")
        }
        user.resetPassword(passwordHasher.hash(rawPassword), command.forceChangePassword, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "PASSWORD_RESET", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return mapOf("id" to user.id, "changed" to true, "forceChangePassword" to command.forceChangePassword)
    }

    fun sessions(id: String): List<LoginSession> {
        detail(id)
        return sessionRepository.search(SessionSearchQuery(userId = id, page = PageQuery(pageSize = 200))).items
    }

    fun terminateUserSession(userId: String, sessionId: String) {
        detail(userId)
        val session = sessionRepository.findSessionById(sessionId) ?: businessError(ErrorCode.NOT_FOUND, "浼氳瘽涓嶅瓨鍦?")
        require(session.userId == userId) { "浼氳瘽涓嶅睘浜庤鐢ㄦ埛" }
        session.revoke("绠＄悊鍛樼粓姝㈢敤鎴蜂細璇?")
        sessionRepository.save(session)
    }

    private fun revokeSessionsIfNeeded(userId: String, revoke: Boolean, reason: String): Int {
        if (!revoke) return 0
        val sessions = sessionRepository.findActiveByUserId(userId)
        sessions.forEach {
            it.revoke(reason)
            sessionRepository.save(it)
        }
        return sessions.size
    }
}

@Service
class OrganizationApplicationService(
    private val orgUnitRepository: OrgUnitRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
) {
    fun tree(rootId: String?, includeDisabled: Boolean): List<OrgUnitTreeNode> {
        val nodes = orgUnitRepository.findAll()
            .filter { includeDisabled || it.enabled }
            .filter { rootId == null || it.id == rootId || isDescendantOf(it, rootId) }
        return buildTree(nodes, rootId)
    }

    fun create(command: CreateOrgUnitCommand): OrgUnit {
        if (orgUnitRepository.existsOrgCode(command.code)) businessError(ErrorCode.CONFLICT, "缁勭粐缂栫爜宸插瓨鍦?")
        command.parentId?.let {
            orgUnitRepository.findOrgUnitById(it) ?: businessError(ErrorCode.NOT_FOUND, "鐖剁粍缁囦笉瀛樺湪")
        }
        val orgUnit = orgUnitRepository.save(
            OrgUnit.create(
                parentId = command.parentId,
                code = command.code,
                name = command.name,
                type = command.type,
                sortOrder = command.sortOrder,
                attributes = command.attributes
            )
        )
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_CREATED", null, orgUnit.id, null, traceId = TraceIds.current()))
        return orgUnit
    }

    fun update(id: String, command: UpdateOrgUnitCommand): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        orgUnit.update(command.parentId, command.code, command.name, command.type, command.sortOrder, command.attributes)
        when (command.enabled) {
            true -> orgUnit.enable()
            false -> orgUnit.disable()
            null -> Unit
        }
        val saved = orgUnitRepository.save(orgUnit)
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_UPDATED", null, saved.id, null, traceId = TraceIds.current()))
        return saved
    }

    fun delete(id: String) {
        if (orgUnitRepository.hasChildren(id)) businessError(ErrorCode.ORG_UNIT_HAS_CHILDREN)
        val memberCount = userRepository.search(UserSearchQuery(orgUnitId = id, page = PageQuery(pageSize = 1))).total
        if (memberCount > 0) businessError(ErrorCode.CONFLICT, "缁勭粐鑺傜偣涓嬪瓨鍦ㄦ垚鍛?")
        orgUnitRepository.deleteOrgUnit(id)
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_DELETED", null, id, null, traceId = TraceIds.current()))
    }

    private fun isDescendantOf(orgUnit: OrgUnit, rootId: String): Boolean {
        var parentId = orgUnit.parentId
        while (parentId != null) {
            if (parentId == rootId) return true
            parentId = orgUnitRepository.findOrgUnitById(parentId)?.parentId
        }
        return false
    }

    private fun buildTree(nodes: List<OrgUnit>, rootId: String?): List<OrgUnitTreeNode> {
        fun childrenOf(parentId: String?): List<OrgUnitTreeNode> {
            return nodes
                .filter { it.parentId == parentId }
                .sortedWith(compareBy<OrgUnit> { it.sortOrder }.thenBy { it.name })
                .map {
                    OrgUnitTreeNode(
                        id = it.id,
                        parentId = it.parentId,
                        code = it.code,
                        name = it.name,
                        type = it.type,
                        enabled = it.enabled,
                        roleIds = it.roleIds,
                        children = childrenOf(it.id)
                    )
                }
        }
        return if (rootId == null) childrenOf(null) else nodes.filter { it.id == rootId }.map {
            OrgUnitTreeNode(it.id, it.parentId, it.code, it.name, it.type, it.enabled, it.roleIds, childrenOf(it.id))
        }
    }

    fun bindRoles(id: String, command: BindOrgUnitRolesCommand): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        val roles = command.roleIds.map { roleRepository.findRoleById(it) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦? $it") }
        require(roles.all { it.enabled }) { "瀛樺湪宸茬鐢ㄨ鑹?" }
        orgUnit.bindRoles(command.roleIds, command.mode)
        val saved = orgUnitRepository.save(orgUnit)
        auditRepository.save(AuditEvent.record("permission", "ORG_UNIT_ROLES_BOUND", null, saved.id, null, payload = mapOf("roleIds" to command.roleIds, "mode" to command.mode), traceId = TraceIds.current()))
        return saved
    }

    fun roles(id: String): List<Role> {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        return orgUnit.roleIds.map { roleId ->
            roleRepository.findRoleById(roleId) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦? $roleId")
        }
    }

    fun revokeRole(id: String, roleId: String): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        orgUnit.revokeRole(roleId)
        val saved = orgUnitRepository.save(orgUnit)
        auditRepository.save(AuditEvent.record("permission", "ORG_UNIT_ROLE_REVOKED", null, saved.id, null, payload = mapOf("roleId" to roleId), traceId = TraceIds.current()))
        return saved
    }

    fun addUser(id: String, userId: String): User {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        user.updateProfile(
            displayName = null,
            email = null,
            phone = null,
            factoryCode = null,
            departmentCode = null,
            orgUnitIds = user.profile.orgUnitIds + id,
            attributes = null
        )
        val saved = userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_MEMBER_ADDED", null, userId, null, payload = mapOf("orgUnitId" to id), traceId = TraceIds.current()))
        return saved
    }

    fun addUsers(id: String, userIds: Set<String>): List<User> {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        val users = userIds.map { userId ->
            userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦? $userId")
        }
        val savedUsers = users.map { user ->
            user.updateProfile(
                displayName = null,
                email = null,
                phone = null,
                factoryCode = null,
                departmentCode = null,
                orgUnitIds = user.profile.orgUnitIds + id,
                attributes = null
            )
            userRepository.save(user)
        }
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_MEMBERS_ADDED", null, id, null, payload = mapOf("userIds" to userIds), traceId = TraceIds.current()))
        return savedUsers
    }

    fun removeUser(id: String, userId: String): User {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "缁勭粐涓嶅瓨鍦?")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        user.updateProfile(
            displayName = null,
            email = null,
            phone = null,
            factoryCode = null,
            departmentCode = null,
            orgUnitIds = user.profile.orgUnitIds - id,
            attributes = null
        )
        val saved = userRepository.save(user)
        auditRepository.save(AuditEvent.record("admin", "ORG_UNIT_MEMBER_REMOVED", null, userId, null, payload = mapOf("orgUnitId" to id), traceId = TraceIds.current()))
        return saved
    }
}

@Service
class AuthorizationApplicationService(
    private val roleRepository: RoleRepository,
    private val resourceRepository: ResourceRepository,
    private val permissionRepository: PermissionRepository,
    private val userRepository: UserRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val appRepository: ClientAppRepository,
    private val auditRepository: AuditRepository,
) {
    fun roles(domainId: String?, appId: String?, roleType: String?, keyword: String?, page: PageQuery): PageResponse<Role> {
        return roleRepository.search(RoleSearchQuery(domainId, appId, roleType, keyword, page))
    }

    fun createRole(command: CreateRoleCommand): Role {
        val domainId = command.domainId ?: command.appId
        if (roleRepository.existsRoleCode(domainId, command.roleCode)) businessError(ErrorCode.ROLE_CODE_EXISTS)
        val role = roleRepository.save(
            Role.create(
                domainId = domainId,
                appId = command.appId,
                roleCode = command.roleCode,
                roleName = command.roleName,
                roleType = parseRoleType(command.roleType),
                description = command.description,
                enabled = command.enabled
            )
        )
        auditRepository.save(AuditEvent.record("permission", "ROLE_CREATED", null, role.id, null, payload = mapOf("roleCode" to role.roleCode, "domainId" to role.domainId, "appId" to role.appId), traceId = TraceIds.current()))
        return role
    }

    fun updateRole(id: String, command: UpdateRoleCommand): Role {
        val role = roleRepository.findRoleById(id) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦?")
        role.update(command.roleName, command.description, command.enabled)
        val saved = roleRepository.save(role)
        auditRepository.save(AuditEvent.record("permission", "ROLE_UPDATED", null, saved.id, null, payload = mapOf("roleCode" to saved.roleCode), traceId = TraceIds.current()))
        return saved
    }

    fun deleteRole(id: String) {
        roleRepository.deleteRole(id)
        auditRepository.save(AuditEvent.record("permission", "ROLE_DELETED", null, id, null, traceId = TraceIds.current()))
    }

    fun bindRolePermissions(id: String, command: BindRolePermissionsCommand): Role {
        val role = roleRepository.findRoleById(id) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦?")
        val permissions = permissionRepository.findPermissionsByIds(command.permissionIds)
        require(permissions.size == command.permissionIds.size) { "瀛樺湪鏃犳晥鏉冮檺鐐?" }
        if (role.roleType == RoleType.APP) {
            require(permissions.all { it.domainId == role.domainId || it.appId == role.appId }) { "搴旂敤瑙掕壊鍙兘缁戝畾鍚屼竴绯荤粺鍩熶笅鐨勬潈闄愮偣" }
        }
        role.bindPermissions(command.permissionIds, command.mode, command.reason)
        val saved = roleRepository.save(role)
        auditRepository.save(AuditEvent.record("permission", "ROLE_PERMISSIONS_BOUND", null, saved.id, null, reason = command.reason, payload = mapOf("permissionIds" to command.permissionIds, "mode" to command.mode), traceId = TraceIds.current()))
        return saved
    }

    fun grantUserRoles(userId: String, command: GrantUserRolesCommand): User {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        val roles = command.roleIds.map { roleRepository.findRoleById(it) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦? $it") }
        require(roles.all { it.enabled }) { "瀛樺湪宸茬鐢ㄨ鑹?" }
        user.grantRoles(command.roleIds, command.effectiveFrom, command.effectiveTo, command.reason)
        val saved = userRepository.save(user)
        auditRepository.save(AuditEvent.record("permission", "USER_ROLES_GRANTED", null, saved.id, null, reason = command.reason, payload = mapOf("roleIds" to command.roleIds), traceId = TraceIds.current()))
        return saved
    }

    fun revokeUserRole(userId: String, roleId: String) {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        user.revokeRole(roleId)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("permission", "USER_ROLE_REVOKED", null, userId, null, payload = mapOf("roleId" to roleId), traceId = TraceIds.current()))
    }

    fun resources(domainId: String?, appId: String?, businessDomainId: String?, resourceType: String?, keyword: String?): List<Resource> {
        return resourceRepository.search(ResourceSearchQuery(domainId, appId, businessDomainId, resourceType, keyword))
    }

    fun createResource(command: CreateResourceCommand): Resource {
        val domainId = command.domainId ?: command.appId
        if (resourceRepository.existsResourceCode(domainId, command.resourceCode)) businessError(ErrorCode.CONFLICT, "璧勬簮缂栫爜宸插瓨鍦?")
        val resource = resourceRepository.save(
            Resource.create(
                domainId = domainId,
                appId = command.appId,
                businessDomainId = command.businessDomainId,
                parentId = command.parentId,
                resourceCode = command.resourceCode,
                resourceName = command.resourceName,
                resourceType = parseResourceType(command.resourceType),
                path = command.path,
                method = command.method,
                sortOrder = command.sortOrder,
                attributes = command.attributes
            )
        )
        auditRepository.save(AuditEvent.record("permission", "RESOURCE_CREATED", null, resource.id, null, payload = mapOf("resourceCode" to resource.resourceCode, "domainId" to resource.domainId, "appId" to resource.appId), traceId = TraceIds.current()))
        return resource
    }

    fun updateResource(id: String, command: UpdateResourceCommand): Resource {
        val resource = resourceRepository.findResourceById(id) ?: businessError(ErrorCode.NOT_FOUND, "璧勬簮涓嶅瓨鍦?")
        resource.update(command.resourceName, null, command.path, command.method, command.sortOrder, command.enabled, command.attributes)
        val saved = resourceRepository.save(resource)
        auditRepository.save(AuditEvent.record("permission", "RESOURCE_UPDATED", null, saved.id, null, payload = mapOf("resourceCode" to saved.resourceCode), traceId = TraceIds.current()))
        return saved
    }

    fun permissions(domainId: String?, appId: String?, businessDomainId: String?, resourceId: String?, keyword: String?): List<Permission> {
        return permissionRepository.search(PermissionSearchQuery(domainId, appId, businessDomainId, resourceId, keyword))
    }

    fun apiPermissionRules(appId: String): List<ApiPermissionRule> {
        val domainId = resolveDomainId(appId)
        val resources = resourceRepository.search(ResourceSearchQuery(domainId = domainId, resourceType = ResourceType.API.code))
            .filter { it.enabled && !it.path.isNullOrBlank() }
        val permissionsByResource = permissionRepository.search(PermissionSearchQuery(domainId = domainId))
            .filter { it.enabled && !it.resourceId.isNullOrBlank() }
            .groupBy { it.resourceId }

        return resources.flatMap { resource ->
            permissionsByResource[resource.id].orEmpty().map { permission ->
                ApiPermissionRule(
                    appId = domainId,
                    resourceId = resource.id,
                    resourceCode = resource.resourceCode,
                    resourceName = resource.resourceName,
                    method = resource.method?.uppercase() ?: "*",
                    path = resource.path.orEmpty(),
                    permissionId = permission.id,
                    permissionCode = permission.permissionCode,
                    action = permission.action,
                )
            }
        }
    }

    fun createPermission(command: CreatePermissionCommand): Permission {
        val domainId = command.domainId ?: command.appId
        if (permissionRepository.existsPermissionCode(domainId, command.permissionCode)) businessError(ErrorCode.PERMISSION_CODE_EXISTS)
        command.resourceId?.let {
            resourceRepository.findResourceById(it) ?: businessError(ErrorCode.NOT_FOUND, "璧勬簮涓嶅瓨鍦?")
        }
        val permission = permissionRepository.save(
            Permission.create(
                domainId = domainId,
                appId = command.appId,
                businessDomainId = command.businessDomainId,
                resourceId = command.resourceId,
                permissionCode = command.permissionCode,
                permissionName = command.permissionName,
                action = command.action,
                description = command.description
            )
        )
        auditRepository.save(AuditEvent.record("permission", "PERMISSION_CREATED", null, permission.id, null, payload = mapOf("permissionCode" to permission.permissionCode, "domainId" to permission.domainId, "appId" to permission.appId), traceId = TraceIds.current()))
        return permission
    }

    fun updatePermission(id: String, command: UpdatePermissionCommand): Permission {
        val permission = permissionRepository.findPermissionById(id) ?: businessError(ErrorCode.NOT_FOUND, "鏉冮檺鐐逛笉瀛樺湪")
        permission.update(command.permissionName, command.action, command.description)
        val saved = permissionRepository.save(permission)
        auditRepository.save(AuditEvent.record("permission", "PERMISSION_UPDATED", null, saved.id, null, payload = mapOf("permissionCode" to saved.permissionCode), traceId = TraceIds.current()))
        return saved
    }

    fun deleteResource(id: String) {
        val resource = resourceRepository.findResourceById(id) ?: businessError(ErrorCode.NOT_FOUND, "璧勬簮涓嶅瓨鍦?")
        resourceRepository.deleteResource(id)
        auditRepository.save(AuditEvent.record("permission", "RESOURCE_DELETED", null, id, null, payload = mapOf("resourceCode" to resource.resourceCode), traceId = TraceIds.current()))
    }

    fun deletePermission(id: String) {
        val permission = permissionRepository.findPermissionById(id) ?: businessError(ErrorCode.NOT_FOUND, "鏉冮檺鐐逛笉瀛樺湪")
        permissionRepository.deletePermission(id)
        auditRepository.save(AuditEvent.record("permission", "PERMISSION_DELETED", null, id, null, payload = mapOf("permissionCode" to permission.permissionCode), traceId = TraceIds.current()))
    }

    fun effectivePermissions(userId: String, appId: String): EffectivePermission {
        val domainId = resolveDomainId(appId)
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(OffsetDateTime.now()) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(OffsetDateTime.now()) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        val activeRoleIds = directRoleIds + inheritedRoleIds
        val roles = activeRoleIds.mapNotNull { roleRepository.findRoleById(it) }
            .filter { it.enabled && (it.appId == null || it.domainId == domainId || it.appId == appId) }
        val permissionIds = roles.flatMap { it.permissionIds }.toSet()
        val permissions = permissionRepository.findPermissionsByIds(permissionIds)
            .filter { it.enabled && (it.domainId == domainId || it.appId == appId) }
        val resources = resourceRepository.search(ResourceSearchQuery(domainId = domainId))
        val permissionResourceIds = permissions.mapNotNull { it.resourceId }.toSet()
        val menuNodes = resources
            .filter { it.resourceType == ResourceType.MENU && permissionResourceIds.contains(it.id) }
            .map { MenuNode(code = it.resourceCode, name = it.resourceName, path = it.path) }
        val buttons = resources
            .filter { it.resourceType == ResourceType.BUTTON && permissionResourceIds.contains(it.id) }
            .associate { resource ->
                resource.resourceCode to permissions.filter { it.resourceId == resource.id }.map { it.action }
            }
        return EffectivePermission(
            appId = domainId,
            userId = user.id,
            permissionVersion = OffsetDateTime.now().toEpochSecond(),
            roles = roles.map { it.roleCode },
            permissions = permissions.map { it.permissionCode },
            menus = menuNodes,
            buttons = buttons,
            dataScopes = listOfNotNull(
                user.profile.factoryCode?.let { DataScope("factory", setOf(it)) },
                user.profile.departmentCode?.let { DataScope("department", setOf(it)) }
            )
        )
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }

    private fun resolveDomainId(appIdOrDomainId: String): String {
        return appRepository.findByClientId(appIdOrDomainId)?.domainId ?: appIdOrDomainId
    }
}

@Service
class ClientAppApplicationService(
    private val appRepository: ClientAppRepository,
    private val systemDomainRepository: SystemDomainRepository,
    private val passwordHasher: PasswordHasher,
    private val auditRepository: AuditRepository,
) {
    fun page(domainId: String?, keyword: String?, status: String?, page: PageQuery): PageResponse<ClientApp> {
        return appRepository.search(AppSearchQuery(domainId?.let { resolveDomainCode(it) }, keyword, status, page))
    }

    fun create(command: CreateClientAppCommand): CreateClientAppResult {
        val domainCode = resolveDomainCode(command.domainId)
        if (appRepository.existsByClientId(command.clientId)) businessError(ErrorCode.APP_CODE_EXISTS)
        val (app, secret) = ClientApp.create(
            domainId = domainCode,
            clientId = command.clientId,
            name = command.name,
            appType = command.appType,
            ownerDept = command.ownerDept,
            redirectUris = command.redirectUris,
            logoutUris = command.logoutUris,
            tokenPolicy = command.tokenPolicy.toDomain()
        )
        app.initializeSecretHash(passwordHasher.hash(secret))
        appRepository.save(app)
        auditRepository.save(AuditEvent.record("admin", "APP_CREATED", null, app.id, app.clientId, payload = mapOf("clientId" to app.clientId, "domainId" to app.domainId), traceId = TraceIds.current()))
        return CreateClientAppResult(app, secret)
    }

    fun update(id: String, command: UpdateClientAppCommand): ClientApp {
        val app = appRepository.findAppById(id) ?: businessError(ErrorCode.NOT_FOUND, "搴旂敤涓嶅瓨鍦?")
        val domainCode = command.domainId?.let { resolveDomainCode(it) }
        app.update(
            name = command.name,
            appType = command.appType,
            ownerDept = command.ownerDept,
            domainId = domainCode,
            redirectUris = command.redirectUris,
            logoutUris = command.logoutUris,
            tokenPolicy = command.tokenPolicy?.toDomain(),
            status = command.status?.let { parseAppStatus(it) },
            oidcConfig = command.oidcConfig?.toDomain()
        )
        val saved = appRepository.save(app)
        auditRepository.save(AuditEvent.record("admin", "APP_UPDATED", null, saved.id, saved.clientId, payload = mapOf("clientId" to saved.clientId, "domainId" to saved.domainId), traceId = TraceIds.current()))
        return saved
    }

    fun delete(id: String) {
        appRepository.findAppById(id) ?: businessError(ErrorCode.NOT_FOUND, "搴旂敤涓嶅瓨鍦?")
        appRepository.deleteApp(id)
        auditRepository.save(AuditEvent.record("admin", "APP_DELETED", null, id, null, traceId = TraceIds.current()))
    }

    private fun resolveDomainCode(domainIdOrCode: String): String {
        return systemDomainRepository.findDomainById(domainIdOrCode)?.code
            ?: systemDomainRepository.findByCode(domainIdOrCode)?.code
            ?: businessError(ErrorCode.NOT_FOUND, "绯荤粺鍩熶笉瀛樺湪: $domainIdOrCode")
    }

    fun rotateSecret(id: String, command: RotateSecretCommand): SecretRotation {
        val app = appRepository.findAppById(id) ?: businessError(ErrorCode.NOT_FOUND, "搴旂敤涓嶅瓨鍦?")
        val secret = cn.scysn.iam.domain.shared.randomSecret("secret_")
        val rotation = app.rotateSecret(secret, passwordHasher.hash(secret), command.reason, command.gracePeriodMinutes)
        appRepository.save(app)
        auditRepository.save(AuditEvent.record("admin", "APP_SECRET_ROTATED", null, app.id, app.clientId, reason = command.reason, payload = mapOf("clientId" to app.clientId, "secretVersion" to app.secretVersion), traceId = TraceIds.current()))
        return rotation
    }
}

private fun ClientApp.requiresClientSecret(): Boolean {
    return appType.lowercase() in setOf("service", "third_party", "third-party", "confidential")
}

private fun OidcClientConfigCommand.toDomain(): OidcClientConfig {
    return OidcClientConfig(
        issuer = issuer,
        discoveryUrl = discoveryUrl,
        authorizeUrl = authorizeUrl,
        tokenUrl = tokenUrl,
        userInfoUrl = userInfoUrl,
        jwksUrl = jwksUrl,
        scope = scope ?: "openid profile email",
        responseType = responseType ?: "code",
        grantType = grantType ?: "authorization_code"
    )
}

@Service
class SystemDomainApplicationService(
    private val systemDomainRepository: SystemDomainRepository,
    private val auditRepository: AuditRepository,
) {
    fun page(keyword: String?, enabled: Boolean?, page: PageQuery): PageResponse<SystemDomain> {
        return systemDomainRepository.search(SystemDomainSearchQuery(keyword, enabled, page))
    }

    fun create(command: CreateSystemDomainCommand): SystemDomain {
        if (systemDomainRepository.existsByCode(command.code)) businessError(ErrorCode.CONFLICT, "绯荤粺鍩熺紪鐮佸凡瀛樺湪")
        val domain = systemDomainRepository.save(SystemDomain.create(command.code, command.name, command.description))
        auditRepository.save(AuditEvent.record("admin", "SYSTEM_DOMAIN_CREATED", null, domain.id, null, payload = mapOf("code" to domain.code), traceId = TraceIds.current()))
        return domain
    }

    fun update(id: String, command: UpdateSystemDomainCommand): SystemDomain {
        val domain = systemDomainRepository.findDomainById(id) ?: businessError(ErrorCode.NOT_FOUND, "绯荤粺鍩熶笉瀛樺湪")
        domain.update(command.name, command.description, command.enabled)
        val saved = systemDomainRepository.save(domain)
        auditRepository.save(AuditEvent.record("admin", "SYSTEM_DOMAIN_UPDATED", null, saved.id, null, payload = mapOf("code" to saved.code), traceId = TraceIds.current()))
        return saved
    }
}

@Service
class SessionApplicationService(
    private val sessionRepository: SessionRepository,
    private val auditRepository: AuditRepository,
) {
    fun page(userId: String?, clientId: String?, status: String?, page: PageQuery): PageResponse<LoginSession> {
        return sessionRepository.search(SessionSearchQuery(userId, clientId, status, page))
    }

    fun terminate(id: String) {
        val session = sessionRepository.findSessionById(id) ?: businessError(ErrorCode.NOT_FOUND, "浼氳瘽涓嶅瓨鍦?")
        session.revoke("绠＄悊鍛樼粓姝細璇?")
        sessionRepository.save(session)
        auditRepository.save(AuditEvent.record("token", "SESSION_REVOKED", null, session.userId, session.clientId, payload = mapOf("sessionId" to id), traceId = TraceIds.current()))
    }

    fun currentUserSessions(userId: String): List<LoginSession> {
        return sessionRepository.search(SessionSearchQuery(userId = userId, page = PageQuery(pageSize = 200))).items
    }

    fun terminateCurrentUserSession(userId: String, sessionId: String) {
        val session = sessionRepository.findSessionById(sessionId) ?: businessError(ErrorCode.NOT_FOUND, "浼氳瘽涓嶅瓨鍦?")
        require(session.userId == userId) { "鍙兘缁堟鑷繁鐨勪細璇?" }
        session.revoke("鐢ㄦ埛鑷姪缁堟浼氳瘽")
        sessionRepository.save(session)
        auditRepository.save(AuditEvent.record("token", "CURRENT_USER_SESSION_REVOKED", userId, session.userId, session.clientId, payload = mapOf("sessionId" to sessionId), traceId = TraceIds.current()))
    }
}

@Service
class AccountApplicationService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
) {
    fun profile(userId: String): User = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")

    fun updateProfile(userId: String, displayName: String?, email: String?, phone: String?): User {
        val user = profile(userId)
        user.updateProfile(displayName, email, phone, null, null, null, null)
        userRepository.save(user)
        return user
    }

    fun loginHistory(userId: String, page: PageQuery): PageResponse<AuditEvent> {
        return auditRepository.search(AuditSearchQuery(eventCategory = "login", userId = userId, page = page))
    }
}

@Service
class AuditApplicationService(
    private val auditRepository: AuditRepository,
) {
    fun events(query: AuditSearchQuery): PageResponse<AuditEvent> = auditRepository.search(query)
}

@Service
class ImportExportApplicationService(
    private val userApplicationService: UserApplicationService,
) {
    fun importUsers(command: ImportUsersCommand): ImportResult {
        val errors = mutableListOf<ImportError>()
        if (command.mode !in setOf("validate_only", "import")) {
            throw IllegalArgumentException("mode 鍙兘鏄?validate_only 鎴?import")
        }
        command.items.forEachIndexed { index, item ->
            runCatching {
                if (command.mode == "import") {
                    userApplicationService.create(
                        CreateUserCommand(
                            username = item.username,
                            displayName = item.displayName,
                            employeeNo = item.employeeNo,
                            email = null,
                            phone = null,
                            accountType = "employee",
                            factoryCode = item.factoryCode,
                            departmentCode = item.departmentCode,
                            orgUnitIds = emptySet(),
                            initialPassword = "TempPassword@123",
                            forceChangePassword = true,
                            attributes = emptyMap()
                        )
                    )
                }
            }.onFailure {
                errors += ImportError(row = index + 1, username = item.username, code = "IMPORT_ERROR", message = it.message ?: "瀵煎叆澶辫触")
            }
        }
        return ImportResult(
            total = command.items.size,
            success = command.items.size - errors.size,
            failed = errors.size,
            errors = errors
        )
    }

    fun exportAuditEvents(command: ExportAuditCommand): ExportTaskResult {
        require(command.reason.isNotBlank()) { "瀵煎嚭瀹¤鏃ュ織蹇呴』濉啓鍘熷洜" }
        require(command.format in setOf("xlsx", "csv")) { "format 鍙兘鏄?xlsx 鎴?csv" }
        return ExportTaskResult(taskId = newId("export"), status = "pending")
    }
}

data class OrgUnitTreeNode(
    val id: String,
    val parentId: String?,
    val code: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val roleIds: Set<String>,
    val children: List<OrgUnitTreeNode>,
)

data class CreateClientAppResult(
    val app: ClientApp,
    val clientSecret: String,
)

data class ImportResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<ImportError>,
)

data class ImportError(
    val row: Int,
    val username: String,
    val code: String,
    val message: String,
)

data class ExportTaskResult(
    val taskId: String,
    val status: String,
)

data class ApiPermissionRule(
    val appId: String,
    val resourceId: String,
    val resourceCode: String,
    val resourceName: String,
    val method: String,
    val path: String,
    val permissionId: String,
    val permissionCode: String,
    val action: String,
)

private fun parseAccountType(value: String): AccountType = when (value) {
    "employee" -> AccountType.EMPLOYEE
    "supplier" -> AccountType.SUPPLIER
    "customer" -> AccountType.CUSTOMER
    "system" -> AccountType.SYSTEM
    else -> throw IllegalArgumentException("accountType 涓嶅悎娉? $value")
}

private fun parseRoleType(value: String): RoleType = when (value) {
    "platform" -> RoleType.PLATFORM
    "app" -> RoleType.APP
    else -> throw IllegalArgumentException("roleType 涓嶅悎娉? $value")
}

private fun parseResourceType(value: String): ResourceType = when (value) {
    "menu" -> ResourceType.MENU
    "page" -> ResourceType.PAGE
    "button" -> ResourceType.BUTTON
    "api" -> ResourceType.API
    "report" -> ResourceType.REPORT
    "data" -> ResourceType.DATA
    "field" -> ResourceType.FIELD
    else -> throw IllegalArgumentException("resourceType 涓嶅悎娉? $value")
}

@Service
class BusinessDomainApplicationService(
    private val businessDomainRepository: BusinessDomainRepository,
    private val systemDomainRepository: SystemDomainRepository,
    private val appRepository: ClientAppRepository,
    private val resourceRepository: ResourceRepository,
    private val permissionRepository: PermissionRepository,
    private val fieldPermissionRepository: FieldPermissionRepository,
    private val dataScopeConfigRepository: DataScopeConfigRepository,
    private val roleRepository: RoleRepository,
    private val auditRepository: AuditRepository,
) {
    fun page(domainId: String?, appId: String?, keyword: String?, page: PageQuery): PageResponse<BusinessDomain> {
        val resolvedDomainId = domainId?.let { resolveDomainCode(it) } ?: appId?.let { resolveDomainIdFromApp(it) }
        return businessDomainRepository.search(BusinessDomainSearchQuery(resolvedDomainId, appId, keyword, page))
    }

    fun create(command: CreateBusinessDomainCommand): BusinessDomain {
        val domainCode = resolveDomainCode(command.domainId)
        if (businessDomainRepository.existsByCode(command.appId, command.code)) businessError(ErrorCode.CONFLICT, "涓氬姟鍩熺紪鐮佸凡瀛樺湪")
        val domain = businessDomainRepository.save(
            BusinessDomain.create(domainCode, command.appId, command.code, command.name, command.description)
        )
        auditRepository.save(AuditEvent.record("admin", "BUSINESS_DOMAIN_CREATED", null, domain.id, null, payload = mapOf("code" to domain.code, "appId" to domain.appId), traceId = TraceIds.current()))
        return domain
    }

    fun update(id: String, command: UpdateBusinessDomainCommand): BusinessDomain {
        val domain = businessDomainRepository.findBusinessDomainById(id) ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        domain.update(command.name, command.description, command.enabled)
        val saved = businessDomainRepository.save(domain)
        auditRepository.save(AuditEvent.record("admin", "BUSINESS_DOMAIN_UPDATED", null, saved.id, null, payload = mapOf("code" to saved.code), traceId = TraceIds.current()))
        return saved
    }

    fun delete(id: String) {
        businessDomainRepository.findBusinessDomainById(id) ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        fieldPermissionRepository.deleteByBusinessDomainId(id)
        businessDomainRepository.deleteBusinessDomain(id)
        auditRepository.save(AuditEvent.record("admin", "BUSINESS_DOMAIN_DELETED", null, id, null, traceId = TraceIds.current()))
    }

    fun syncMetadata(command: SyncMetadataCommand): Map<String, Any> {
        val app = appRepository.findByClientId(command.appId) ?: businessError(ErrorCode.NOT_FOUND, "搴旂敤涓嶅瓨鍦?")
        val createdDomains = mutableListOf<String>()
        val createdResources = mutableListOf<String>()
        val registeredFields = mutableMapOf<String, List<String>>()

        command.businessDomains.forEach { bd ->
            val domainCode = bd.code
            val domainId = businessDomainRepository.findByCode(app.clientId, domainCode)?.id
                ?: businessDomainRepository.save(
                    BusinessDomain.create(app.domainId, app.clientId, domainCode, bd.name, bd.description)
                ).also { createdDomains.add(it.code) }.id

            val resourceMap = mutableMapOf<String, String>()
            bd.resources?.forEach { res ->
                val parentId = res.parentCode?.let { resourceMap[it] }
                val existingResource = resourceRepository.search(ResourceSearchQuery(appId = app.clientId))
                    .find { it.resourceCode == res.code && it.appId == app.clientId }
                val resource = existingResource ?: resourceRepository.save(
                    Resource.create(
                        domainId = app.domainId,
                        appId = app.clientId,
                        businessDomainId = domainId,
                        parentId = parentId,
                        resourceCode = res.code,
                        resourceName = res.name,
                        resourceType = parseResourceType(res.type),
                        path = res.path,
                        method = res.method,
                        sortOrder = res.sortOrder,
                        attributes = emptyMap()
                    )
                ).also { createdResources.add(it.resourceCode) }
                resourceMap[res.code] = resource.id

                res.operations?.forEach { op ->
                    val permCode = if (op.contains(":")) op else "${domainCode}:${res.code}:${op}"
                    val action = op.substringAfterLast(":")
                    if (!permissionRepository.existsPermissionCode(app.clientId, permCode)) {
                        permissionRepository.save(
                            Permission.create(domainId = app.domainId, appId = app.clientId, businessDomainId = domainId, resourceId = resource.id, permissionCode = permCode, permissionName = "${res.name}-${action}", action = action, description = null)
                        )
                    }
                }

                res.fields?.let { fields ->
                    fields.forEach { field ->
                        val fieldResourceCode = "${domainCode}.field.${field.code}"
                        val existing = resourceRepository.search(ResourceSearchQuery(appId = app.clientId))
                            .find { it.resourceCode == fieldResourceCode }
                        if (existing != null) {
                            if (existing.resourceType != ResourceType.FIELD) {
                                existing.update(resourceName = field.name, resourceType = ResourceType.FIELD, path = null, method = null, sortOrder = 0, enabled = null, attributes = mapOf("fieldCode" to field.code, "fieldType" to field.type, "sensitive" to field.sensitive))
                                resourceRepository.save(existing)
                            }
                        } else {
                            resourceRepository.save(
                                Resource.create(
                                    domainId = app.domainId,
                                    appId = app.clientId,
                                    businessDomainId = domainId,
                                    parentId = resource.id,
                                    resourceCode = fieldResourceCode,
                                    resourceName = field.name,
                                    resourceType = ResourceType.FIELD,
                                    path = null,
                                    method = null,
                                    sortOrder = 0,
                                    attributes = mapOf(
                                        "fieldCode" to field.code,
                                        "fieldType" to field.type,
                                        "sensitive" to field.sensitive,
                                    )
                                )
                            ).also { createdResources.add(it.resourceCode) }
                        }
                    }
                    registeredFields[domainCode] = fields.map { it.code }
                }
            }
        }

        return mapOf(
            "syncedBusinessDomains" to createdDomains.size,
            "syncedResources" to createdResources.size,
            "registeredFields" to registeredFields
        )
    }

    fun importPermissionModel(command: ImportPermissionModelCommand): Map<String, Any> {
        val app = appRepository.findByClientId(command.appId) ?: businessError(ErrorCode.NOT_FOUND, "搴旂敤涓嶅瓨鍦?")
        val importedRoles = mutableListOf<String>()
        val importedPermissions = mutableListOf<String>()

        command.roles.forEach { roleEntry ->
            val role = if (roleRepository.existsRoleCode(app.domainId, roleEntry.roleCode)) {
                roleRepository.search(RoleSearchQuery(domainId = app.domainId, keyword = roleEntry.roleCode)).items.first()
            } else {
                roleRepository.save(
                    Role.create(
                        domainId = app.domainId,
                        appId = command.appId,
                        roleCode = roleEntry.roleCode,
                        roleName = roleEntry.roleName,
                        roleType = parseRoleType(roleEntry.roleType),
                        description = "浠?{command.sourceSystem}瀵煎叆",
                        enabled = true
                    )
                ).also { importedRoles.add(it.roleCode) }
            }

            val validPermissionIds = roleEntry.permissionCodes.mapNotNull { code ->
                val perm = permissionRepository.search(PermissionSearchQuery(appId = command.appId, keyword = code)).firstOrNull()
                perm?.id
            }
            if (validPermissionIds.isNotEmpty()) {
                role.bindPermissions(validPermissionIds.toSet(), "append", command.reason)
                roleRepository.save(role)
            }

            if (roleEntry.fieldPermissions != null) {
                val businessDomainIds = businessDomainRepository.search(BusinessDomainSearchQuery(appId = command.appId)).items.map { it.id }
                businessDomainIds.forEach { bdId ->
                    roleEntry.fieldPermissions!!.forEach { (fieldCode, permType) ->
                        fieldPermissionRepository.save(
                            FieldPermission.create(bdId, role.id, fieldCode, fieldCode, parseFieldPermissionType(permType))
                        )
                    }
                }
                importedPermissions.add("${role.roleCode}: ${roleEntry.fieldPermissions.size} field permissions")
            }
            roleEntry.dataScope?.let { dataScope ->
                val businessDomainIds = businessDomainRepository.search(BusinessDomainSearchQuery(appId = command.appId)).items.map { it.id }
                businessDomainIds.forEach { bdId ->
                    val existing = dataScopeConfigRepository.findByBusinessDomainIdAndRoleId(bdId, role.id)
                    if (existing == null) {
                        dataScopeConfigRepository.save(
                            DataScopeConfig.create(bdId, role.id, dataScope.scopeType, dataScope.scopeValue)
                        )
                    } else {
                        existing.update(dataScope.scopeType, dataScope.scopeValue)
                        dataScopeConfigRepository.save(existing)
                    }
                }
            }
        }

        auditRepository.save(AuditEvent.record("permission", "PERMISSION_MODEL_IMPORTED", null, app.id, app.clientId, reason = command.reason, payload = mapOf("sourceSystem" to command.sourceSystem, "importedRoles" to importedRoles.size), traceId = TraceIds.current()))
        return mapOf("importedRoles" to importedRoles.size, "roles" to importedRoles)
    }

    private fun resolveDomainCode(domainIdOrCode: String): String {
        return systemDomainRepository.findDomainById(domainIdOrCode)?.code
            ?: systemDomainRepository.findByCode(domainIdOrCode)?.code
            ?: domainIdOrCode
    }

    private fun resolveDomainIdFromApp(appId: String): String {
        return appRepository.findByClientId(appId)?.domainId ?: appId
    }
}

@Service
class FieldPermissionApplicationService(
    private val fieldPermissionRepository: FieldPermissionRepository,
    private val businessDomainRepository: BusinessDomainRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val auditRepository: AuditRepository,
) {
    fun configure(domainId: String, command: ConfigureFieldPermissionsCommand): List<FieldPermission> {
        val businessDomain = businessDomainRepository.findBusinessDomainById(domainId)
            ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        roleRepository.findRoleById(command.roleId) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦?")
        fieldPermissionRepository.deleteByBusinessDomainId(domainId)
        val saved = command.fields.map { entry ->
            fieldPermissionRepository.save(
                FieldPermission.create(
                    businessDomainId = domainId,
                    roleId = command.roleId,
                    fieldCode = entry.fieldCode,
                    fieldName = entry.fieldName,
                    permissionType = parseFieldPermissionType(entry.permissionType)
                )
            )
        }
        auditRepository.save(AuditEvent.record("permission", "FIELD_PERMISSION_CONFIGURED", null, businessDomain.id, null, reason = command.reason, payload = mapOf("roleId" to command.roleId, "fieldCount" to saved.size), traceId = TraceIds.current()))
        return saved
    }

    fun getByRole(domainId: String, roleId: String): List<FieldPermission> {
        businessDomainRepository.findBusinessDomainById(domainId) ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        return fieldPermissionRepository.search(FieldPermissionSearchQuery(domainId, roleId))
    }

    fun getFieldPolicy(userId: String, appId: String, businessDomainCode: String): FieldPolicy {
        val businessDomain = businessDomainRepository.findByCode(appId, businessDomainCode)
            ?: return FieldPolicy(emptySet(), emptySet(), emptySet(), emptySet())
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        val allRoleIds = collectUserRoleIds(user)
        val permissions = fieldPermissionRepository.search(FieldPermissionSearchQuery(businessDomain.id))
            .filter { it.enabled && it.roleId in allRoleIds }
        return FieldPolicy(
            visible = permissions.filter { it.permissionType == FieldPermissionType.VISIBLE }.map { it.fieldCode }.toSet(),
            hidden = permissions.filter { it.permissionType == FieldPermissionType.HIDDEN }.map { it.fieldCode }.toSet(),
            masked = permissions.filter { it.permissionType == FieldPermissionType.MASKED }.map { it.fieldCode }.toSet(),
            readonly = permissions.filter { it.permissionType == FieldPermissionType.READONLY }.map { it.fieldCode }.toSet(),
        )
    }

    fun getMyFieldPolicy(userId: String, clientId: String, businessDomainCode: String): FieldPolicy {
        return getFieldPolicy(userId, clientId, businessDomainCode)
    }

    private fun collectUserRoleIds(user: User): Set<String> {
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(OffsetDateTime.now()) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(OffsetDateTime.now()) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        return directRoleIds + inheritedRoleIds
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }
}

@Service
class DataScopeApplicationService(
    private val dataScopeConfigRepository: DataScopeConfigRepository,
    private val businessDomainRepository: BusinessDomainRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val auditRepository: AuditRepository,
) {
    fun configure(domainId: String, command: ConfigureDataScopeCommand): DataScopeConfig {
        val businessDomain = businessDomainRepository.findBusinessDomainById(domainId)
            ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        roleRepository.findRoleById(command.roleId) ?: businessError(ErrorCode.NOT_FOUND, "瑙掕壊涓嶅瓨鍦?")
        val existing = dataScopeConfigRepository.findByBusinessDomainIdAndRoleId(domainId, command.roleId)
        val config = if (existing != null) {
            existing.update(command.scopeType, command.scopeValue)
            dataScopeConfigRepository.save(existing)
        } else {
            dataScopeConfigRepository.save(
                DataScopeConfig.create(domainId, command.roleId, command.scopeType, command.scopeValue)
            )
        }
        auditRepository.save(AuditEvent.record("permission", "DATA_SCOPE_CONFIGURED", null, businessDomain.id, null, reason = command.reason, payload = mapOf("roleId" to command.roleId, "scopeType" to command.scopeType), traceId = TraceIds.current()))
        return config
    }

    fun getByRole(domainId: String, roleId: String): DataScopeConfig? {
        businessDomainRepository.findBusinessDomainById(domainId) ?: businessError(ErrorCode.NOT_FOUND, "涓氬姟鍩熶笉瀛樺湪")
        return dataScopeConfigRepository.findByBusinessDomainIdAndRoleId(domainId, roleId)
    }

    fun getDataScope(userId: String, appId: String, businessDomainCode: String): DataScope {
        val businessDomain = businessDomainRepository.findByCode(appId, businessDomainCode)
            ?: return DataScope("all", emptySet())
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "鐢ㄦ埛涓嶅瓨鍦?")
        val allRoleIds = collectUserRoleIds(user)
        val config = allRoleIds.firstNotNullOfOrNull { roleId ->
            dataScopeConfigRepository.findByBusinessDomainIdAndRoleId(businessDomain.id, roleId)
        }
        return if (config != null) DataScope(config.scopeType, config.scopeValue)
        else DataScope("all", emptySet())
    }

    fun getMyDataScope(userId: String, clientId: String, businessDomainCode: String): DataScope {
        return getDataScope(userId, clientId, businessDomainCode)
    }

    private fun collectUserRoleIds(user: User): Set<String> {
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(OffsetDateTime.now()) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(OffsetDateTime.now()) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        return directRoleIds + inheritedRoleIds
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }
}

private fun parseFieldPermissionType(value: String): FieldPermissionType = when (value) {
    "visible" -> FieldPermissionType.VISIBLE
    "readonly" -> FieldPermissionType.READONLY
    "hidden" -> FieldPermissionType.HIDDEN
    "masked" -> FieldPermissionType.MASKED
    else -> throw IllegalArgumentException("fieldPermissionType 涓嶅悎娉? $value")
}

private fun parseAppStatus(value: String): AppStatus = when (value) {
    "active" -> AppStatus.ACTIVE
    "disabled" -> AppStatus.DISABLED
    else -> throw IllegalArgumentException("status 涓嶅悎娉? $value")
}

private fun TokenPolicyCommand.toDomain(): TokenPolicy {
    return TokenPolicy(
        accessTokenTtlSeconds = accessTokenTtlSeconds,
        refreshTokenIdleSeconds = refreshTokenIdleSeconds,
        refreshTokenMaxSeconds = refreshTokenMaxSeconds,
        ssoSessionIdleSeconds = ssoSessionIdleSeconds,
        ssoSessionMaxSeconds = ssoSessionMaxSeconds
    )
}
