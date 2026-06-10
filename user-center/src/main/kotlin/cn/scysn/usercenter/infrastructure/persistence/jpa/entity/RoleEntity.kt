package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.RoleScope
import jakarta.persistence.*

@Entity
@Table(name = "uc_role")
class RoleEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "code", nullable = false, length = 120)
    var code: String = ""

    @Column(name = "name", nullable = false, length = 160)
    var name: String = ""

    @Column(name = "description", length = 500)
    var description: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 24)
    var scope: RoleScope = RoleScope.PLATFORM

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: AccessApplicationEntity? = null
}
