package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.GroupType
import jakarta.persistence.*

@Entity
@Table(name = "uc_group")
class GroupEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "code", nullable = false, unique = true, length = 120)
    var code: String = ""

    @Column(name = "name", nullable = false, length = 160)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type", nullable = false, length = 24)
    var groupType: GroupType = GroupType.ORGANIZATION

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    var parent: GroupEntity? = null

    @Column(name = "path", nullable = false, length = 1000)
    var path: String = ""
}
