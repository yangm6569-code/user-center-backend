package cn.scysn.iam.domain.clientapp

import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.randomSecret
import cn.scysn.iam.domain.shared.requireCode
import cn.scysn.iam.domain.shared.requireName
import java.net.URI
import java.time.OffsetDateTime

enum class AppStatus(val code: String) {
    ACTIVE("active"),
    DISABLED("disabled");
}

data class TokenPolicy(
    val accessTokenTtlSeconds: Long,
    val refreshTokenIdleSeconds: Long,
    val refreshTokenMaxSeconds: Long,
    val ssoSessionIdleSeconds: Long = 7200,
    val ssoSessionMaxSeconds: Long = 43200,
) {
    init {
        require(accessTokenTtlSeconds in 60..86400) { "Access Token TTL 必须在 60 到 86400 秒之间" }
        require(refreshTokenIdleSeconds > accessTokenTtlSeconds) { "Refresh Token idle 必须大于 Access Token TTL" }
        require(refreshTokenMaxSeconds >= refreshTokenIdleSeconds) { "Refresh Token max 必须大于等于 idle" }
    }
}

data class SecretRotation(
    val clientId: String,
    val clientSecret: String,
    val secretVersion: Int,
    val oldSecretExpiresAt: OffsetDateTime,
)

data class OidcClientConfig(
    val issuer: String? = null,
    val discoveryUrl: String? = null,
    val authorizeUrl: String? = null,
    val tokenUrl: String? = null,
    val userInfoUrl: String? = null,
    val jwksUrl: String? = null,
    val scope: String = "openid profile email",
    val responseType: String = "code",
    val grantType: String = "authorization_code",
) {
    fun normalized(): OidcClientConfig {
        return copy(
            issuer = normalizeUrl(issuer),
            discoveryUrl = normalizeUrl(discoveryUrl),
            authorizeUrl = normalizeUrl(authorizeUrl),
            tokenUrl = normalizeUrl(tokenUrl),
            userInfoUrl = normalizeUrl(userInfoUrl),
            jwksUrl = normalizeUrl(jwksUrl),
            scope = scope.trim().ifBlank { "openid profile email" },
            responseType = responseType.trim().ifBlank { "code" },
            grantType = grantType.trim().ifBlank { "authorization_code" }
        )
    }
}

class ClientApp private constructor(
    val id: String,
    domainId: String,
    clientId: String,
    name: String,
    appType: String,
    ownerDept: String?,
    redirectUris: Set<String>,
    logoutUris: Set<String>,
    tokenPolicy: TokenPolicy,
    status: AppStatus,
    secretVersion: Int,
    secretHash: String?,
    previousSecretHash: String?,
    previousSecretExpiresAt: OffsetDateTime?,
    oidcConfig: OidcClientConfig,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var domainId: String = domainId
        private set
    var clientId: String = clientId
        private set
    var name: String = name
        private set
    var appType: String = appType
        private set
    var ownerDept: String? = ownerDept
        private set
    var redirectUris: Set<String> = redirectUris
        private set
    var logoutUris: Set<String> = logoutUris
        private set
    var tokenPolicy: TokenPolicy = tokenPolicy
        private set
    var status: AppStatus = status
        private set
    var secretVersion: Int = secretVersion
        private set
    var secretHash: String? = secretHash
        private set
    var previousSecretHash: String? = previousSecretHash
        private set
    var previousSecretExpiresAt: OffsetDateTime? = previousSecretExpiresAt
        private set
    var oidcConfig: OidcClientConfig = oidcConfig
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        requireCode(domainId, "系统域ID")
        validate(clientId, name, appType)
    }

    companion object {
        fun create(
            domainId: String,
            clientId: String,
            name: String,
            appType: String,
            ownerDept: String?,
            redirectUris: Set<String>,
            logoutUris: Set<String>,
            tokenPolicy: TokenPolicy,
        ): Pair<ClientApp, String> {
            val app = ClientApp(
                id = newId("app"),
                domainId = domainId,
                clientId = clientId,
                name = name,
                appType = appType,
                ownerDept = ownerDept,
                redirectUris = normalizeUriSet("redirectUris", redirectUris),
                logoutUris = normalizeUriSet("logoutUris", logoutUris),
                tokenPolicy = tokenPolicy,
                status = AppStatus.ACTIVE,
                secretVersion = 1,
                secretHash = null,
                previousSecretHash = null,
                previousSecretExpiresAt = null,
                oidcConfig = OidcClientConfig(),
                createdAt = now(),
                updatedAt = now()
            )
            return app to randomSecret("secret_")
        }

        fun restore(
            id: String,
            domainId: String,
            clientId: String,
            name: String,
            appType: String,
            ownerDept: String?,
            redirectUris: Set<String>,
            logoutUris: Set<String>,
            tokenPolicy: TokenPolicy,
            status: AppStatus,
            secretVersion: Int,
            secretHash: String?,
            previousSecretHash: String?,
            previousSecretExpiresAt: OffsetDateTime?,
            oidcConfig: OidcClientConfig = OidcClientConfig(),
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): ClientApp {
            return ClientApp(
                id = id,
                domainId = domainId,
                clientId = clientId,
                name = name,
                appType = appType,
                ownerDept = ownerDept,
                redirectUris = normalizeUriSet("redirectUris", redirectUris),
                logoutUris = normalizeUriSet("logoutUris", logoutUris),
                tokenPolicy = tokenPolicy,
                status = status,
                secretVersion = secretVersion,
                secretHash = secretHash,
                previousSecretHash = previousSecretHash,
                previousSecretExpiresAt = previousSecretExpiresAt,
                oidcConfig = oidcConfig.normalized(),
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun update(
        name: String?,
        appType: String?,
        ownerDept: String?,
        domainId: String?,
        redirectUris: Set<String>?,
        logoutUris: Set<String>?,
        tokenPolicy: TokenPolicy?,
        status: AppStatus?,
        oidcConfig: OidcClientConfig?,
    ) {
        val nextName = name ?: this.name
        val nextType = appType ?: this.appType
        validate(clientId, nextName, nextType)
        this.name = nextName
        this.appType = nextType
        this.domainId = domainId ?: this.domainId
        this.ownerDept = ownerDept ?: this.ownerDept
        this.redirectUris = redirectUris?.let { normalizeUriSet("redirectUris", it) } ?: this.redirectUris
        this.logoutUris = logoutUris?.let { normalizeUriSet("logoutUris", it) } ?: this.logoutUris
        this.tokenPolicy = tokenPolicy ?: this.tokenPolicy
        this.status = status ?: this.status
        this.oidcConfig = oidcConfig?.normalized() ?: this.oidcConfig
        touch()
    }

    fun initializeSecretHash(secretHash: String) {
        require(secretHash.isNotBlank()) { "密钥哈希不能为空" }
        this.secretHash = secretHash
        this.previousSecretHash = null
        this.previousSecretExpiresAt = null
        touch()
    }

    fun rotateSecret(clientSecret: String, secretHash: String, reason: String, gracePeriodMinutes: Long): SecretRotation {
        require(reason.isNotBlank()) { "轮换密钥必须填写原因" }
        require(clientSecret.isNotBlank()) { "客户端密钥不能为空" }
        require(secretHash.isNotBlank()) { "客户端密钥哈希不能为空" }
        require(gracePeriodMinutes in 1..1440) { "宽限期必须在 1 到 1440 分钟之间" }
        val oldSecretExpiresAt = now().plusMinutes(gracePeriodMinutes)
        previousSecretHash = this.secretHash
        previousSecretExpiresAt = previousSecretHash?.let { oldSecretExpiresAt }
        this.secretHash = secretHash
        secretVersion += 1
        touch()
        return SecretRotation(
            clientId = clientId,
            clientSecret = clientSecret,
            secretVersion = secretVersion,
            oldSecretExpiresAt = oldSecretExpiresAt
        )
    }

    fun clearExpiredPreviousSecret(referenceTime: OffsetDateTime = now()) {
        if (previousSecretExpiresAt?.isAfter(referenceTime) == false) {
            previousSecretHash = null
            previousSecretExpiresAt = null
            touch()
        }
    }

    private fun touch() {
        updatedAt = now()
    }
}

private fun validate(clientId: String, name: String, appType: String) {
    requireCode(clientId, "clientId")
    requireName(name, "应用名称")
    requireCode(appType, "应用类型")
}

private fun normalizeUriSet(fieldName: String, values: Set<String>): Set<String> {
    return values.mapNotNull { value ->
        value.trim().takeIf { it.isNotBlank() }?.also { validateHttpUri(fieldName, it) }
    }.toSet()
}

private fun validateHttpUri(fieldName: String, value: String) {
    val uri = runCatching { URI(value) }
        .getOrElse { throw IllegalArgumentException("$fieldName 必须是合法的 URL: $value") }
    require(uri.scheme == "http" || uri.scheme == "https") { "$fieldName 只支持 http 或 https: $value" }
    require(!uri.host.isNullOrBlank()) { "$fieldName 必须包含 host: $value" }
}

private fun normalizeUrl(value: String?): String? {
    val normalized = value?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
    validateHttpUri("OIDC URL", normalized)
    return normalized
}
