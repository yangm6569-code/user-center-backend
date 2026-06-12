package cn.scysn.iam.domain.audit

import cn.scysn.iam.domain.shared.Attributes
import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import java.time.OffsetDateTime

data class AuditEvent(
    val id: String,
    val eventCategory: String,
    val eventType: String,
    val actorId: String?,
    val targetId: String?,
    val clientId: String?,
    val ip: String?,
    val userAgent: String?,
    val result: String,
    val reason: String?,
    val traceId: String?,
    val payload: Attributes,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun record(
            eventCategory: String,
            eventType: String,
            actorId: String?,
            targetId: String?,
            clientId: String?,
            result: String = "success",
            reason: String? = null,
            payload: Attributes = emptyMap(),
            traceId: String? = null,
            ip: String? = null,
            userAgent: String? = null,
        ): AuditEvent {
            return AuditEvent(
                id = newId("audit"),
                eventCategory = eventCategory,
                eventType = eventType,
                actorId = actorId,
                targetId = targetId,
                clientId = clientId,
                ip = ip,
                userAgent = userAgent,
                result = result,
                reason = reason,
                traceId = traceId,
                payload = payload,
                createdAt = now()
            )
        }
    }
}
