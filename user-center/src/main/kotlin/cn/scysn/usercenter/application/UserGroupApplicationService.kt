package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.ConflictException
import cn.scysn.common.base.domain.NotFoundException
import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.GroupEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.GroupRoleEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserGroupEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.GroupJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.GroupRoleJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.RoleJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserAccountJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserGroupJpaRepository
import cn.scysn.usercenter.interfaces.GroupCreateDTO
import cn.scysn.usercenter.interfaces.GroupRoleBindingVO
import cn.scysn.usercenter.interfaces.GroupRoleSummaryVO
import cn.scysn.usercenter.interfaces.GroupTreeVO
import cn.scysn.usercenter.interfaces.GroupUpdateDTO
import cn.scysn.usercenter.interfaces.GroupVO
import cn.scysn.usercenter.interfaces.UserAccountVO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserGroupApplicationService(
    private val groupJpaRepository: GroupJpaRepository,
    private val roleJpaRepository: RoleJpaRepository,
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val userGroupJpaRepository: UserGroupJpaRepository,
    private val groupRoleJpaRepository: GroupRoleJpaRepository,
    private val auditApplicationService: AuditApplicationService,
) {
    @Transactional(readOnly = true)
    fun list(): List<GroupVO> = groupJpaRepository.findAllByOrderByPathAsc().map { it.toVO() }

    @Transactional(readOnly = true)
    fun tree(): List<GroupTreeVO> {
        val groups = groupJpaRepository.findAllByOrderByPathAsc()
        val childrenByParentId = groups.groupBy { it.parent?.id }

        fun build(group: GroupEntity): GroupTreeVO {
            val children = childrenByParentId[group.id].orEmpty().map { build(it) }
            return group.toTreeVO(children)
        }

        return childrenByParentId[null].orEmpty().map { build(it) }
    }

    @Transactional
    fun create(create: GroupCreateDTO, actorId: Long?, metadata: RequestMetadata?): Long {
        if (groupJpaRepository.existsByCode(create.code)) {
            throw ConflictException("Group code already exists: ${create.code}")
        }
        val parent = create.parentId?.let {
            groupJpaRepository.findById(it).orElseThrow { NotFoundException("Parent group not found: $it") }
        }
        val group = groupJpaRepository.save(
            GroupEntity().apply {
                code = create.code
                name = create.name
                groupType = create.groupType
                this.parent = parent
                path = if (parent == null) "/${create.code}" else "${parent.path}/${create.code}"
            },
        )
        auditApplicationService.record(AuditCategory.ADMIN, "GROUP_CREATED", actorId, group.id, metadata = metadata)
        return group.id ?: 0
    }

    @Transactional
    fun update(update: GroupUpdateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val group = groupJpaRepository.findById(update.id).orElseThrow { NotFoundException("Group not found: ${update.id}") }
        update.name?.let { group.name = it }
        update.groupType?.let { group.groupType = it }
        group.touch()
        auditApplicationService.record(AuditCategory.ADMIN, "GROUP_UPDATED", actorId, group.id, metadata = metadata)
    }

    @Transactional(readOnly = true)
    fun roleSummary(groupId: Long): GroupRoleSummaryVO {
        val group = groupJpaRepository.findById(groupId).orElseThrow { NotFoundException("Group not found: $groupId") }
        val directRoles = groupRoleJpaRepository.findAllByGroup_Id(groupId)
            .map { it.toBinding(inherited = false) }
            .sortedWith(compareBy({ it.sourceGroupPath }, { it.role.code }))
        val inheritedRoles = ancestorPaths(group.path)
            .takeIf { it.isNotEmpty() }
            ?.let { groupRoleJpaRepository.findAllByGroup_PathIn(it) }
            .orEmpty()
            .map { it.toBinding(inherited = true) }
            .sortedWith(compareBy({ it.sourceGroupPath }, { it.role.code }))

        return GroupRoleSummaryVO(
            groupId = group.id ?: 0,
            directRoles = directRoles,
            inheritedRoles = inheritedRoles,
        )
    }

    @Transactional(readOnly = true)
    fun members(groupId: Long, includeSubgroups: Boolean): List<UserAccountVO> {
        val group = groupJpaRepository.findById(groupId).orElseThrow { NotFoundException("Group not found: $groupId") }
        val users = if (includeSubgroups) {
            userGroupJpaRepository.findUsersByGroupPathIncludingDescendants(group.path)
        } else {
            userGroupJpaRepository.findUsersByGroupId(groupId)
        }
        return users.map { it.toVO() }
    }

    @Transactional
    fun assignRole(groupId: Long, roleId: Long, actorId: Long?, metadata: RequestMetadata?) {
        val group = groupJpaRepository.findById(groupId).orElseThrow { NotFoundException("Group not found: $groupId") }
        val role = roleJpaRepository.findById(roleId).orElseThrow { NotFoundException("Role not found: $roleId") }
        if (!groupRoleJpaRepository.existsByGroup_IdAndRole_Id(groupId, roleId)) {
            groupRoleJpaRepository.save(
                GroupRoleEntity().apply {
                    this.group = group
                    this.role = role
                },
            )
        }
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "GROUP_ROLE_ASSIGNED",
            actorId,
            groupId,
            role.application?.clientId,
            metadata,
            payload = mapOf("roleId" to role.id, "roleCode" to role.code),
        )
    }

    @Transactional
    fun unassignRole(groupId: Long, roleId: Long, actorId: Long?, metadata: RequestMetadata?) {
        groupRoleJpaRepository.deleteByGroup_IdAndRole_Id(groupId, roleId)
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "GROUP_ROLE_REMOVED",
            actorId,
            groupId,
            metadata = metadata,
            payload = mapOf("roleId" to roleId),
        )
    }

    @Transactional
    fun addMember(groupId: Long, userId: Long, actorId: Long?, metadata: RequestMetadata?) {
        val group = groupJpaRepository.findById(groupId).orElseThrow { NotFoundException("Group not found: $groupId") }
        val user = userAccountJpaRepository.findById(userId).orElseThrow { NotFoundException("User not found: $userId") }
        if (!userGroupJpaRepository.existsByUser_IdAndGroup_Id(userId, groupId)) {
            userGroupJpaRepository.save(
                UserGroupEntity().apply {
                    this.user = user
                    this.group = group
                },
            )
        }
        auditApplicationService.record(AuditCategory.ADMIN, "GROUP_MEMBER_ADDED", actorId, userId, metadata = metadata)
    }

    @Transactional
    fun removeMember(groupId: Long, userId: Long, actorId: Long?, metadata: RequestMetadata?) {
        userGroupJpaRepository.deleteByUser_IdAndGroup_Id(userId, groupId)
        auditApplicationService.record(AuditCategory.ADMIN, "GROUP_MEMBER_REMOVED", actorId, userId, metadata = metadata)
    }

    private fun ancestorPaths(path: String): List<String> {
        val parts = path.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size <= 1) return emptyList()
        return (1 until parts.size).map { index -> "/" + parts.take(index).joinToString("/") }
    }

    private fun GroupRoleEntity.toBinding(inherited: Boolean): GroupRoleBindingVO =
        GroupRoleBindingVO(
            role = role.toVO(),
            sourceGroupId = group.id ?: 0,
            sourceGroupCode = group.code,
            sourceGroupName = group.name,
            sourceGroupPath = group.path,
            inherited = inherited,
        )
}
