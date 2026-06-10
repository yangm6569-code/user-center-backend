package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.ConflictException
import cn.scysn.common.base.domain.NotFoundException
import cn.scysn.usercenter.api.UserCenterPermissionInfo
import cn.scysn.usercenter.api.service.UserCenterPermissionService
import cn.scysn.usercenter.domain.model.*
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.*
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.*
import cn.scysn.usercenter.interfaces.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class AuthorizationApplicationService(
    private val roleJpaRepository: RoleJpaRepository,
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val accessApplicationJpaRepository: AccessApplicationJpaRepository,
    private val groupJpaRepository: GroupJpaRepository,
    private val userGroupJpaRepository: UserGroupJpaRepository,
    private val userRoleJpaRepository: UserRoleJpaRepository,
    private val groupRoleJpaRepository: GroupRoleJpaRepository,
    private val resourceJpaRepository: ResourceJpaRepository,
    private val policyJpaRepository: PolicyJpaRepository,
    private val permissionJpaRepository: PermissionJpaRepository,
    private val auditApplicationService: AuditApplicationService,
) : UserCenterPermissionService {
    @Transactional(readOnly = true)
    fun roles(): List<RoleVO> =
        roleJpaRepository.findAllByOrderByCodeAsc().map { it.toVO() }

    @Transactional
    fun createRole(create: RoleCreateDTO, actorId: Long?, metadata: RequestMetadata?): RoleVO {
        val application = create.applicationId?.let {
            accessApplicationJpaRepository.findById(it).orElseThrow { NotFoundException("应用不存在: $it") }
        }
        if (create.scope == RoleScope.APPLICATION && application == null) {
            throw ConflictException("应用角色必须指定applicationId")
        }
        if (create.scope == RoleScope.PLATFORM && application != null) {
            throw ConflictException("平台角色不能绑定应用")
        }
        val roleExists = if (create.scope == RoleScope.PLATFORM) {
            roleJpaRepository.existsByCodeAndApplicationIsNull(create.code)
        } else {
            roleJpaRepository.existsByCodeAndApplication_Id(create.code, create.applicationId!!)
        }
        if (roleExists) {
            throw ConflictException("角色编码已存在: ${create.code}")
        }
        val role = roleJpaRepository.save(
            RoleEntity().apply {
                code = create.code
                name = create.name
                description = create.description
                scope = create.scope
                this.application = application
            },
        )
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "ROLE_CREATED",
            actorId,
            role.id,
            role.application?.clientId,
            metadata,
            payload = mapOf("code" to role.code, "scope" to role.scope.name),
        )
        return role.toVO()
    }

    @Transactional
    fun assignUserRole(userId: Long, assign: RoleAssignDTO, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(userId).orElseThrow { NotFoundException("用户不存在: $userId") }
        val role = roleJpaRepository.findById(assign.roleId).orElseThrow { NotFoundException("角色不存在: ${assign.roleId}") }
        if (!userRoleJpaRepository.existsByUser_IdAndRole_Id(userId, assign.roleId)) {
            userRoleJpaRepository.save(
                UserRoleEntity().apply {
                    this.user = user
                    this.role = role
                    this.expiresAt = assign.expiresAt
                    this.reason = assign.reason
                },
            )
        }
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "USER_ROLE_ASSIGNED",
            actorId,
            userId,
            role.application?.clientId,
            metadata,
            payload = mapOf("roleId" to role.id, "roleCode" to role.code, "reason" to assign.reason),
        )
    }

    @Transactional
    fun unassignUserRole(userId: Long, roleId: Long, actorId: Long?, metadata: RequestMetadata?) {
        userRoleJpaRepository.deleteByUser_IdAndRole_Id(userId, roleId)
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "USER_ROLE_REMOVED",
            actorId,
            userId,
            metadata = metadata,
            payload = mapOf("roleId" to roleId),
        )
    }

    @Transactional
    fun assignGroupRole(groupId: Long, roleId: Long, actorId: Long?, metadata: RequestMetadata?) {
        val group = groupJpaRepository.findById(groupId).orElseThrow { NotFoundException("分组不存在: $groupId") }
        val role = roleJpaRepository.findById(roleId).orElseThrow { NotFoundException("角色不存在: $roleId") }
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

    @Transactional(readOnly = true)
    fun effectivePermission(userId: Long): EffectivePermissionVO {
        if (!userAccountJpaRepository.existsById(userId)) {
            throw NotFoundException("用户不存在: $userId")
        }
        val roles = (
            userRoleJpaRepository.findActiveRolesByUserId(userId, Instant.now()) +
                groupRoleJpaRepository.findInheritedRolesByUserId(userId)
            ).distinctBy { it.id }
        val roleIds = roles.mapNotNull { it.id }
        val permissions = if (roleIds.isEmpty()) {
            emptyList()
        } else {
            permissionJpaRepository.findByRole_IdIn(roleIds)
                .filter { it.effect == PermissionEffect.ALLOW }
                .distinctBy { "${it.resource.id}:${it.scope}" }
        }
        return EffectivePermissionVO(
            userId = userId,
            roles = roles.map { it.toVO() }.sortedBy { it.code },
            permissions = permissions.map { it.toVO() }.sortedWith(compareBy({ it.clientId }, { it.resourceCode }, { it.scope })),
        )
    }

    @Transactional(readOnly = true)
    fun userAuthorizationDetail(userId: Long): UserAuthorizationDetailVO {
        if (!userAccountJpaRepository.existsById(userId)) {
            throw NotFoundException("User not found: $userId")
        }

        val now = Instant.now()
        val directGroups = userGroupJpaRepository.findAssignmentsByUserId(userId)
            .map { it.group }
            .distinctBy { it.id }
            .sortedBy { it.path }
        val directGroupPaths = directGroups.map { it.path }.toSet()

        val directRoles = userRoleJpaRepository.findActiveRoleAssignmentsByUserId(userId, now)
            .map {
                UserRoleBindingVO(
                    role = it.role.toVO(),
                    source = "USER",
                    inherited = false,
                    expiresAt = it.expiresAt,
                    reason = it.reason,
                )
            }
            .sortedBy { it.role.code }

        val groupRoles = groupRoleJpaRepository.findInheritedRoleAssignmentsByUserId(userId)
            .map {
                UserRoleBindingVO(
                    role = it.role.toVO(),
                    source = "GROUP",
                    sourceGroupId = it.group.id ?: 0,
                    sourceGroupCode = it.group.code,
                    sourceGroupName = it.group.name,
                    sourceGroupPath = it.group.path,
                    inherited = it.group.path !in directGroupPaths,
                )
            }
            .sortedWith(compareBy({ it.sourceGroupPath.orEmpty() }, { it.role.code }))

        val effective = effectivePermission(userId)
        return UserAuthorizationDetailVO(
            userId = userId,
            groups = directGroups.map { it.toVO() },
            directRoles = directRoles,
            groupRoles = groupRoles,
            effectiveRoles = effective.roles,
            permissions = effective.permissions,
        )
    }

    override fun effectivePermissions(userId: Long): List<UserCenterPermissionInfo> =
        effectivePermission(userId).permissions.map {
            UserCenterPermissionInfo(
                roleCode = it.roleCode,
                clientId = it.clientId,
                resourceCode = it.resourceCode,
                scope = it.scope,
            )
        }

    @Transactional
    fun createResource(create: ResourceCreateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val app = accessApplicationJpaRepository.findById(create.applicationId)
            .orElseThrow { NotFoundException("应用不存在: ${create.applicationId}") }
        if (resourceJpaRepository.existsByApplication_IdAndResourceCode(create.applicationId, create.resourceCode)) {
            throw ConflictException("资源编码已存在: ${create.resourceCode}")
        }
        val parent = create.parentId?.let {
            resourceJpaRepository.findById(it).orElseThrow { NotFoundException("父资源不存在: $it") }
        }
        val resource = resourceJpaRepository.save(
            ResourceEntity().apply {
                application = app
                resourceCode = create.resourceCode
                resourceType = create.resourceType
                name = create.name
                this.parent = parent
                attributes = create.attributes.toMutableMap()
            },
        )
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "RESOURCE_CREATED",
            actorId,
            resource.id,
            app.clientId,
            metadata,
            payload = mapOf("resourceCode" to resource.resourceCode),
        )
    }

    @Transactional
    fun createPolicy(create: PolicyCreateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val app = accessApplicationJpaRepository.findById(create.applicationId)
            .orElseThrow { NotFoundException("应用不存在: ${create.applicationId}") }
        if (policyJpaRepository.existsByApplication_IdAndCode(create.applicationId, create.code)) {
            throw ConflictException("策略编码已存在: ${create.code}")
        }
        val policy = policyJpaRepository.save(
            PolicyEntity().apply {
                application = app
                code = create.code
                name = create.name
                policyType = create.policyType
                rule = create.rule.toMutableMap()
            },
        )
        auditApplicationService.record(AuditCategory.PERMISSION, "POLICY_CREATED", actorId, policy.id, app.clientId, metadata)
    }

    @Transactional
    fun createPermission(create: PermissionCreateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val role = roleJpaRepository.findById(create.roleId).orElseThrow { NotFoundException("角色不存在: ${create.roleId}") }
        val resource = resourceJpaRepository.findById(create.resourceId).orElseThrow { NotFoundException("资源不存在: ${create.resourceId}") }
        val policy = create.policyId?.let { policyJpaRepository.findById(it).orElseThrow { NotFoundException("策略不存在: $it") } }
        if (role.scope == RoleScope.APPLICATION && role.application?.id != resource.application.id) {
            throw ConflictException("应用角色只能授权同一应用下的资源")
        }
        val permission = permissionJpaRepository.save(
            PermissionEntity().apply {
                this.role = role
                this.resource = resource
                scope = create.scope
                this.policy = policy
                effect = create.effect
            },
        )
        auditApplicationService.record(
            AuditCategory.PERMISSION,
            "PERMISSION_CREATED",
            actorId,
            permission.id,
            resource.application.clientId,
            metadata,
            payload = mapOf("roleCode" to role.code, "resourceCode" to resource.resourceCode, "scope" to create.scope),
        )
    }
}
