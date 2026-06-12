package cn.scysn.iam.domain.clientapp

import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.randomSecret
import cn.scysn.iam.domain.shared.requireCode
import cn.scysn.iam.domain.shared.requireName
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

class ClientApp private constructor(
    val id: String,
    clientId: String,
    name: String,
    appType: String,
    ownerDept: String?,
    redirectUris: Set<String>,
    logoutUris: Set<String>,
    tokenPolicy: TokenPolicy,
    status: AppStatus,
    secretVersion: Int,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
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
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validate(clientId, name, appType)
    }

    companion object {
        fun create(
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
                clientId = clientId,
                name = name,
                appType = appType,
                ownerDept = ownerDept,
                redirectUris = redirectUris,
                logoutUris = logoutUris,
                tokenPolicy = tokenPolicy,
                status = AppStatus.ACTIVE,
                secretVersion = 1,
                createdAt = now(),
                updatedAt = now()
            )
            return app to randomSecret("secret_")
        }

        fun restore(
            id: String,
            clientId: String,
            name: String,
            appType: String,
            ownerDept: String?,
            redirectUris: Set<String>,
            logoutUris: Set<String>,
            tokenPolicy: TokenPolicy,
            status: AppStatus,
            secretVersion: Int,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): ClientApp {
            return ClientApp(
                id = id,
                clientId = clientId,
                name = name,
                appType = appType,
                ownerDept = ownerDept,
                redirectUris = redirectUris,
                logoutUris = logoutUris,
                tokenPolicy = tokenPolicy,
                status = status,
                secretVersion = secretVersion,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun update(
        name: String?,
        appType: String?,
        ownerDept: String?,
        redirectUris: Set<String>?,
        logoutUris: Set<String>?,
        tokenPolicy: TokenPolicy?,
        status: AppStatus?,
    ) {
        val nextName = name ?: this.name
        val nextType = appType ?: this.appType
        validate(clientId, nextName, nextType)
        this.name = nextName
        this.appType = nextType
        this.ownerDept = ownerDept ?: this.ownerDept
        this.redirectUris = redirectUris ?: this.redirectUris
        this.logoutUris = logoutUris ?: this.logoutUris
        this.tokenPolicy = tokenPolicy ?: this.tokenPolicy
        this.status = status ?: this.status
        touch()
    }

    fun rotateSecret(reason: String, gracePeriodMinutes: Long): SecretRotation {
        require(reason.isNotBlank()) { "轮换密钥必须填写原因" }
        require(gracePeriodMinutes in 1..1440) { "宽限期必须在 1 到 1440 分钟之间" }
        secretVersion += 1
        touch()
        return SecretRotation(
            clientId = clientId,
            clientSecret = randomSecret("secret_"),
            secretVersion = secretVersion,
            oldSecretExpiresAt = now().plusMinutes(gracePeriodMinutes)
        )
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
