package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.PermissionEffect
import jakarta.persistence.*

@Entity
@Table(name = "uc_permission")
class PermissionEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    var role: RoleEntity = RoleEntity()

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    var resource: ResourceEntity = ResourceEntity()

    @Column(name = "scope", nullable = false, length = 80)
    var scope: String = ""

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    var policy: PolicyEntity? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 16)
    var effect: PermissionEffect = PermissionEffect.ALLOW
}
