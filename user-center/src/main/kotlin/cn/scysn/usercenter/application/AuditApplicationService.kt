package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.PageResult
import cn.scysn.common.base.infrastructure.currentTraceId
import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.domain.model.AuditResult
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.AuditEventEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.AuditEventJpaRepository
import cn.scysn.usercenter.interfaces.AuditEventVO
import jakarta.servlet.http.HttpServletRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class RequestMetadata(
    val ip: String?,
    val userAgent: String?,
) {
    companion object {
        fun from(request: HttpServletRequest): RequestMetadata =
            RequestMetadata(
                ip = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
            )
    }
}

@Service
class AuditApplicationService(
    private val auditEventJpaRepository: AuditEventJpaRepository,
) {
    @Transactional
    fun record(
        category: AuditCategory,
        eventType: String,
        actorId: Long? = null,
        targetId: Long? = null,
        clientId: String? = null,
        metadata: RequestMetadata? = null,
        result: AuditResult = AuditResult.SUCCESS,
        reason: String? = null,
        payload: Map<String, Any?> = emptyMap(),
    ) {
        auditEventJpaRepository.save(
            AuditEventEntity().apply {
                this.category = category
                this.eventType = eventType
                this.actorId = actorId
                this.targetId = targetId
                this.clientId = clientId
                this.ip = metadata?.ip
                this.userAgent = metadata?.userAgent
                this.result = result
                this.reason = reason
                this.traceId = currentTraceId()
                this.payload = payload.toMutableMap()
            },
        )
    }

    @Transactional(readOnly = true)
    fun page(category: AuditCategory, page: Long, size: Long, targetId: Long? = null): PageResult<AuditEventVO> {
        val pageable = PageRequest.of(
            (page - 1).coerceAtLeast(0).toInt(),
            size.coerceIn(1, 200).toInt(),
            Sort.by("createTime").descending(),
        )
        val result = if (targetId == null) {
            auditEventJpaRepository.findByCategory(category, pageable)
        } else {
            auditEventJpaRepository.findByCategoryAndTargetId(category, targetId, pageable)
        }
        return PageResult(
            items = result.content.map { it.toVO() },
            page = page,
            size = size,
            total = result.totalElements,
        )
    }
}
