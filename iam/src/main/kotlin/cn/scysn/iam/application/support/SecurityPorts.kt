package cn.scysn.iam.application.support

import com.fasterxml.jackson.databind.ObjectMapper
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
    fun issueAccessToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
    ): String

    fun issueRefreshToken(): String
    fun jwks(): List<Map<String, String>>
}

@Component
class JwtTokenIssuer(
    private val objectMapper: ObjectMapper,
) : TokenIssuer {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val secureRandom = SecureRandom()
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()
    private val keyId: String = createKeyId(keyPair.public.encoded)

    override fun issueAccessToken(
        userId: String,
        audience: String,
        authorizedParty: String,
        sessionId: String,
        ttlSeconds: Long,
    ): String {
        val now = Instant.now().epochSecond
        val header = mapOf(
            "alg" to "RS256",
            "typ" to "JWT",
            "kid" to keyId
        )
        val payload = mapOf(
            "iss" to "user-center",
            "sub" to userId,
            "aud" to audience,
            "azp" to authorizedParty,
            "sid" to sessionId,
            "iat" to now,
            "exp" to now + ttlSeconds,
            "jti" to UUID.randomUUID().toString()
        )
        val signingInput = "${base64Json(header)}.${base64Json(payload)}"
        return "$signingInput.${sign(signingInput)}"
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

    private fun base64Json(value: Map<String, Any>): String {
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
