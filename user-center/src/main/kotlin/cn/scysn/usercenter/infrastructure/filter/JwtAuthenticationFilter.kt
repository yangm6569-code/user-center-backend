package cn.scysn.usercenter.infrastructure.filter

import cn.scysn.usercenter.domain.model.SessionStatus
import cn.scysn.usercenter.domain.provider.JwtTokenProvider
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.LoginSessionJpaRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
    private val loginSessionJpaRepository: LoginSessionJpaRepository,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val principal = runCatching { jwtTokenProvider.parse(header.removePrefix("Bearer ").trim()) }
            .getOrNull()
        if (principal != null) {
            val session = loginSessionJpaRepository.findById(principal.sessionId).orElse(null)
            if (session != null && session.status == SessionStatus.ACTIVE && session.expiresAt.isAfter(Instant.now())) {
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    principal.roles.map { SimpleGrantedAuthority(it) },
                )
            }
        }
        filterChain.doFilter(request, response)
    }
}
