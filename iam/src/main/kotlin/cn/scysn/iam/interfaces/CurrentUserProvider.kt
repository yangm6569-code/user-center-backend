package cn.scysn.iam.interfaces

import cn.scysn.common.error.ErrorCode
import cn.scysn.common.error.businessError
import cn.scysn.iam.application.support.TokenIssuer
import cn.scysn.iam.domain.ports.SsoSessionRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class CurrentUserProvider(
    private val request: HttpServletRequest,
    private val ssoSessionRepository: SsoSessionRepository,
    private val tokenIssuer: TokenIssuer,
) {
    fun userId(): String {
        return bearerUserId()
            ?: request.getHeader("X-User-Id")?.takeIf { it.isNotBlank() }
            ?: businessError(ErrorCode.UNAUTHORIZED, "缺少有效登录态")
    }

    fun ssoUserId(): String? {
        return cookie(SSO_COOKIE_NAME)
            ?.let { ssoSessionRepository.findSsoSessionById(it) }
            ?.takeIf { it.isActive() }
            ?.userId
    }

    fun cookie(name: String): String? {
        return request.cookies?.firstOrNull { it.name == name }?.value
    }

    private fun bearerUserId(): String? {
        val token = bearerToken() ?: return null
        val claims = runCatching { tokenIssuer.verifyAndReadClaims(token) }
            .getOrElse { businessError(ErrorCode.TOKEN_INVALID, "Access Token 无效或已过期") }
        return claims["sub"] as? String
            ?: businessError(ErrorCode.TOKEN_INVALID, "Access Token 缺少 sub")
    }

    private fun bearerToken(): String? {
        val prefix = "Bearer "
        return request.getHeader("Authorization")
            ?.takeIf { it.startsWith(prefix, ignoreCase = true) }
            ?.substring(prefix.length)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}

private const val SSO_COOKIE_NAME = "UC_SSO_SESSION"
