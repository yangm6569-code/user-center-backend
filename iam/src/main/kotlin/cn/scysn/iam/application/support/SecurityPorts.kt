package cn.scysn.iam.application.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Base64
import java.util.UUID

interface PasswordHasher {
    fun hash(raw: String): String
    fun matches(raw: String, hash: String): Boolean
}

@Component
class BCryptPasswordHasher : PasswordHasher {
    private val encoder = BCryptPasswordEncoder()

    override fun hash(raw: String): String {
        require(raw.length >= 8) { "密码长度不能少于 8 位" }
        return encoder.encode(raw)
    }

    override fun matches(raw: String, hash: String): Boolean {
        return encoder.matches(raw, hash)
    }
}

interface TokenIssuer {
    fun issuer(): String

    fun issueAccessToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
    ): String

    fun issueIdToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
        username: String,
        displayName: String,
        email: String?,
        nonce: String? = null,
    ): String

    fun issueRefreshToken(): String
    fun jwks(): List<Map<String, String>>
    fun verifyAndReadClaims(token: String): Map<String, Any?>
}

@Component
class JwtTokenIssuer(
    private val objectMapper: ObjectMapper,
    @Value("\${user-center.security.issuer:http://127.0.0.1:8081}")
    private val configuredIssuer: String,
) : TokenIssuer {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private val secureRandom = SecureRandom()
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val keyId: String = createKeyId(keyPair.public.encoded)

    override fun issuer(): String = configuredIssuer.trimEnd('/')

    override fun issueAccessToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
    ): String {
        val now = Instant.now().epochSecond
        return issueJwt(
            mapOf(
                "iss" to issuer(),
                "sub" to userId,
                "aud" to audience,
                "azp" to authorizedParty,
                "sid" to sessionId,
                "iat" to now,
                "exp" to now + ttlSeconds,
                "jti" to UUID.randomUUID().toString()
            )
        )
    }

    override fun issueIdToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
        username: String,
        displayName: String,
        email: String?,
        nonce: String?,
    ): String {
        val now = Instant.now().epochSecond
        val payload = linkedMapOf<String, Any?>(
            "iss" to issuer(),
            "sub" to userId,
            "aud" to audience,
            "azp" to authorizedParty,
            "sid" to sessionId,
            "auth_time" to now,
            "iat" to now,
            "exp" to now + ttlSeconds,
            "jti" to UUID.randomUUID().toString(),
            "preferred_username" to username,
            "name" to displayName,
            "email_verified" to (email != null)
        )
        email?.let { payload["email"] = it }
        nonce?.takeIf { it.isNotBlank() }?.let { payload["nonce"] = it }
        return issueJwt(payload)
    }

    override fun issueRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "rt_${encoder.encodeToString(bytes)}"
    }

    override fun jwks(): List<Map<String, String>> {
        val publicKey = keyPair.public as RSAPublicKey
        return listOf(
            mapOf(
                "kty" to "RSA",
                "kid" to keyId,
                "use" to "sig",
                "alg" to "RS256",
                "n" to encodeUnsigned(publicKey.modulus),
                "e" to encodeUnsigned(publicKey.publicExponent)
            )
        )
    }

    override fun verifyAndReadClaims(token: String): Map<String, Any?> {
        val parts = token.split(".")
        require(parts.size == 3) { "JWT 格式不正确" }
        val signingInput = "${parts[0]}.${parts[1]}"
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initVerify(keyPair.public)
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        require(signature.verify(decoder.decode(parts[2]))) { "JWT 签名无效" }
        val claims = objectMapper.readValue<Map<String, Any?>>(decoder.decode(parts[1]))
        require(claims["iss"] == issuer()) { "JWT issuer 不匹配" }
        val expiresAt = (claims["exp"] as? Number)?.toLong() ?: 0
        require(expiresAt > Instant.now().epochSecond) { "JWT 已过期" }
        return claims
    }

    private fun issueJwt(payload: Map<String, Any?>): String {
        val header = mapOf(
            "alg" to "RS256",
            "typ" to "JWT",
            "kid" to keyId
        )
        val signingInput = "${base64Json(header)}.${base64Json(payload)}"
        return "$signingInput.${sign(signingInput)}"
    }

    private fun base64Json(value: Map<String, Any?>): String {
        return encoder.encodeToString(objectMapper.writeValueAsBytes(value))
    }

    private fun sign(signingInput: String): String {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(keyPair.private)
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        return encoder.encodeToString(signature.sign())
    }

    private fun encodeUnsigned(value: BigInteger): String {
        val bytes = value.toByteArray()
        val unsigned = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        return encoder.encodeToString(unsigned)
    }

    private fun createKeyId(publicKeyBytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes).copyOfRange(0, 8)
        return "uc-${encoder.encodeToString(digest)}"
    }
}
