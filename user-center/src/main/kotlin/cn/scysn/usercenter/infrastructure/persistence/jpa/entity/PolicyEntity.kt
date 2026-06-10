package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.PolicyType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "uc_policy")
class PolicyEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    var application: AccessApplicationEntity = AccessApplicationEntity()

    @Column(name = "code", nullable = false, length = 160)
    var code: String = ""

    @Column(name = "name", nullable = false, length = 160)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 32)
    var policyType: PolicyType = PolicyType.ROLE

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule", nullable = false, columnDefinition = "jsonb")
    var rule: MutableMap<String, Any?> = mutableMapOf()
}
