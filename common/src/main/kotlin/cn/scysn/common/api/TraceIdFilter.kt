package cn.scysn.common.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            TraceIds.set(request.getHeader("X-Trace-Id"))
            response.setHeader("X-Trace-Id", TraceIds.current())
            filterChain.doFilter(request, response)
        } finally {
            TraceIds.clear()
        }
    }
}
