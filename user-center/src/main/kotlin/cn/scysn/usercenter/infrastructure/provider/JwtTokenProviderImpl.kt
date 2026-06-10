package cn.scysn.usercenter.infrastructure.provider

import cn.scysn.common.base.domain.UnauthorizedException
import cn.scysn.usercenter.domain.model.LoginPrincipal
import cn.scysn.usercenter.domain.provider.AccessTokenInfo
import cn.scysn.usercenter.domain.provider.JwtTokenProvider
import cn.scysn.usercenter.infrastructure.config.UserCenterSecurityProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProviderImpl(
    private val properties: UserCenterSecurityProperties,
) : JwtTokenProvider {
    private val key: SecretKey by lazy {
        val bytes = properties.jwtSecretKey.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) { "JWT密钥长度不能少于32字节" }
        Keys.hmacShaKeyFor(bytes)
    }

    override fun generate(principal: LoginPrincipal, ttlSeconds: Long): AccessTokenInfo {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(ttlSeconds)
        val token = Jwts.builder()
            .issuer(properties.issuer)
            .subject(principal.userId.toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .claim("username", principal.username)
            .claim("sessionId", principal.sessionId)
            .claim("clientId", principal.clientId)
            .claim("roles", principal.roles)
            .signWith(key)
            .compact()
        return AccessTokenInfo(token, expiresAt)
    }

    override fun parse(token: String): LoginPrincipal {
        val claims: Claims = runCatching {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        }.getOrElse { throw UnauthorizedException("Token无效或已过期") }

        @Suppress("UNCHECKED_CAST")
        val roles = claims["roles"] as? List<String> ?: emptyList()
        return LoginPrincipal(
            userId = claims.subject.toLong(),
            username = claims["username"] as String,
            sessionId = (claims["sessionId"] as Number).toLong(),
            clientId = claims["clientId"] as? String,
            roles = roles,
        )
    }
}
