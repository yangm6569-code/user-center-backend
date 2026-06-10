package cn.scysn.usercenter.domain.provider

import cn.scysn.usercenter.domain.model.LoginPrincipal
import java.time.Instant

data class AccessTokenInfo(
    val token: String,
    val expiresAt: Instant,
)

interface JwtTokenProvider {
    fun generate(principal: LoginPrincipal, ttlSeconds: Long): AccessTokenInfo

    fun parse(token: String): LoginPrincipal
}
