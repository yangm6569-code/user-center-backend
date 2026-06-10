package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.domain.model.AuditResult
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "uc_audit_event")
class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    var category: AuditCategory = AuditCategory.ADMIN

    @Column(name = "event_type", nullable = false, length = 120)
    var eventType: String = ""

    @Column(name = "actor_id")
    var actorId: Long? = null

    @Column(name = "target_id")
    var targetId: Long? = null

    @Column(name = "client_id", length = 120)
    var clientId: String? = null

    @Column(name = "ip", length = 80)
    var ip: String? = null

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 24)
    var result: AuditResult = AuditResult.SUCCESS

    @Column(name = "reason", length = 500)
    var reason: String? = null

    @Column(name = "trace_id", nullable = false, length = 80)
    var traceId: String = ""

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: MutableMap<String, Any?> = mutableMapOf()

    @Column(name = "create_time", nullable = false)
    var createTime: java.time.Instant = java.time.Instant.now()
}
