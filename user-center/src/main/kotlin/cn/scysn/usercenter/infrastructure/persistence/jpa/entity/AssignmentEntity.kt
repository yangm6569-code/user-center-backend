package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "uc_user_group")
class UserGroupEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccountEntity = UserAccountEntity()

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    var group: GroupEntity = GroupEntity()
}

@Entity
@Table(name = "uc_user_role")
class UserRoleEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccountEntity = UserAccountEntity()

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    var role: RoleEntity = RoleEntity()

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    @Column(name = "reason", length = 500)
    var reason: String? = null
}

@Entity
@Table(name = "uc_group_role")
class GroupRoleEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    var group: GroupEntity = GroupEntity()

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    var role: RoleEntity = RoleEntity()
}
