package cn.scysn.iam.domain.auth

import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.randomSecret
import java.time.OffsetDateTime

enum class SessionStatus(val code: String) {
    ACTIVE("active"),
    REVOKED("revoked"),
    EXPIRED("expired");
}

class LoginSession private constructor(
    val id: String,
    val userId: String,
    val clientId: String,
    val deviceId: String?,
    val ip: String?,
    val userAgent: String?,
    status: SessionStatus,
    val createdAt: OffsetDateTime,
    lastActiveAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
) {
    var status: SessionStatus = status
        private set
    var lastActiveAt: OffsetDateTime = lastActiveAt
        private set
    var revokedAt: OffsetDateTime? = null
        private set
    var revokedReason: String? = null
        private set

    companion object {
        fun create(
            userId: String,
            clientId: String,
            deviceId: String?,
            ip: String?,
            userAgent: String?,
            ttlSeconds: Long,
        ): LoginSession {
            val createdAt = now()
            return LoginSession(
                id = newId("sess"),
                userId = userId,
                clientId = clientId,
                deviceId = deviceId,
                ip = ip,
                userAgent = userAgent,
                status = SessionStatus.ACTIVE,
                createdAt = createdAt,
                lastActiveAt = createdAt,
                expiresAt = createdAt.plusSeconds(ttlSeconds)
            )
        }

        fun restore(
            id: String,
            userId: String,
            clientId: String,
            deviceId: String?,
            ip: String?,
            userAgent: String?,
            status: SessionStatus,
            createdAt: OffsetDateTime,
            lastActiveAt: OffsetDateTime,
            expiresAt: OffsetDateTime,
            revokedAt: OffsetDateTime?,
            revokedReason: String?,
        ): LoginSession {
            val session = LoginSession(
                id = id,
                userId = userId,
                clientId = clientId,
                deviceId = deviceId,
                ip = ip,
                userAgent = userAgent,
                status = status,
                createdAt = createdAt,
                lastActiveAt = lastActiveAt,
                expiresAt = expiresAt
            )
            session.revokedAt = revokedAt
            session.revokedReason = revokedReason
            return session
        }
    }

    fun touch() {
        require(status == SessionStatus.ACTIVE) { "非活跃会话不能更新活跃时间" }
        lastActiveAt = now()
    }

    fun revoke(reason: String?) {
        if (status == SessionStatus.REVOKED) {
            return
        }
        status = SessionStatus.REVOKED
        revokedAt = now()
        revokedReason = reason
    }
}

class SsoSession private constructor(
    val id: String,
    val userId: String,
    status: SessionStatus,
    val createdAt: OffsetDateTime,
    lastActiveAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
) {
    var status: SessionStatus = status
        private set
    var lastActiveAt: OffsetDateTime = lastActiveAt
        private set

    companion object {
        fun create(userId: String, ttlSeconds: Long): SsoSession {
            val createdAt = now()
            return SsoSession(
                id = newId("sso"),
                userId = userId,
                status = SessionStatus.ACTIVE,
                createdAt = createdAt,
                lastActiveAt = createdAt,
                expiresAt = createdAt.plusSeconds(ttlSeconds)
            )
        }

        fun restore(
            id: String,
            userId: String,
            status: SessionStatus,
            createdAt: OffsetDateTime,
            lastActiveAt: OffsetDateTime,
            expiresAt: OffsetDateTime,
        ): SsoSession {
            return SsoSession(id, userId, status, createdAt, lastActiveAt, expiresAt)
        }
    }

    fun isActive(referenceTime: OffsetDateTime = now()): Boolean {
        return status == SessionStatus.ACTIVE && expiresAt.isAfter(referenceTime)
    }

    fun touch() {
        require(status == SessionStatus.ACTIVE) { "非活跃 SSO 会话不能更新活跃时间" }
        lastActiveAt = now()
    }

    fun revoke() {
        status = SessionStatus.REVOKED
    }
}

class AuthorizationCode private constructor(
    val code: String,
    val userId: String,
    val clientId: String,
    val redirectUri: String,
    val scope: String?,
    used: Boolean,
    val createdAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    usedAt: OffsetDateTime?,
) {
    var used: Boolean = used
        private set
    var usedAt: OffsetDateTime? = usedAt
        private set

    companion object {
        fun create(userId: String, clientId: String, redirectUri: String, scope: String?, ttlSeconds: Long = 300): AuthorizationCode {
            val createdAt = now()
            return AuthorizationCode(
                code = randomSecret("code_"),
                userId = userId,
                clientId = clientId,
                redirectUri = redirectUri,
                scope = scope,
                used = false,
                createdAt = createdAt,
                expiresAt = createdAt.plusSeconds(ttlSeconds),
                usedAt = null
            )
        }

        fun restore(
            code: String,
            userId: String,
            clientId: String,
            redirectUri: String,
            scope: String?,
            used: Boolean,
            createdAt: OffsetDateTime,
            expiresAt: OffsetDateTime,
            usedAt: OffsetDateTime?,
        ): AuthorizationCode {
            return AuthorizationCode(code, userId, clientId, redirectUri, scope, used, createdAt, expiresAt, usedAt)
        }
    }

    fun consume(clientId: String, redirectUri: String) {
        require(!used) { "授权码已使用" }
        require(expiresAt.isAfter(now())) { "授权码已过期" }
        require(this.clientId == clientId) { "client_id 与授权码不匹配" }
        require(this.redirectUri == redirectUri) { "redirect_uri 与授权码不匹配" }
        used = true
        usedAt = now()
    }
}

data class TokenSet(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
    val sessionId: String,
)

data class LoginUserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val employeeNo: String?,
)

data class LoginResult(
    val tokenType: String,
    val accessToken: String,
    val expiresIn: Long,
    val refreshToken: String,
    val refreshExpiresIn: Long,
    val sessionId: String,
    val ssoSessionId: String,
    val requiredActions: List<String>,
    val user: LoginUserSummary,
)

data class CurrentUserInfo(
    val id: String,
    val username: String,
    val displayName: String,
    val employeeNo: String?,
    val email: String?,
    val phone: String?,
    val factoryCode: String?,
    val departmentCode: String?,
    val status: String,
    val requiredActions: List<String>,
)

data class JwkSet(
    val keys: List<Map<String, String>>,
)
