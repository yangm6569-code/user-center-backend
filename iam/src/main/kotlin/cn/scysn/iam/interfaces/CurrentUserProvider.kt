package cn.scysn.iam.interfaces

import cn.scysn.iam.domain.ports.SsoSessionRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class CurrentUserProvider(
    private val request: HttpServletRequest,
    private val ssoSessionRepository: SsoSessionRepository,
) {
    fun userId(): String {
        return request.getHeader("X-User-Id")?.takeIf { it.isNotBlank() }
            ?: cookie(SSO_COOKIE_NAME)
                ?.let { ssoSessionRepository.findSsoSessionById(it) }
                ?.takeIf { it.isActive() }
                ?.userId
            ?: "user-001"
    }

    fun cookie(name: String): String? {
        return request.cookies?.firstOrNull { it.name == name }?.value
    }
}

private const val SSO_COOKIE_NAME = "UC_SSO_SESSION"
