package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.PageQuery
import cn.scysn.usercenter.domain.model.*
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

@Schema(description = "登录请求")
data class LoginByPasswordDTO(
    @field:Schema(description = "账号", required = true) val username: String,
    @field:Schema(description = "密码", required = true) val password: String,
    @field:Schema(description = "接入应用clientId") val clientId: String? = null,
)

data class RefreshTokenDTO(val refreshToken: String)

data class LogoutDTO(val refreshToken: String? = null)

data class LoginTokenVO(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshExpiresAt: Instant,
    val user: UserAccountVO,
    val roles: List<RoleVO>,
    val permissions: List<PermissionVO>,
)

data class CurrentUserVO(
    val user: UserAccountVO,
    val roles: List<RoleVO>,
    val permissions: List<PermissionVO>,
)

data class UserAccountCreateDTO(
    val username: String,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String,
    val password: String,
    val accountType: AccountType = AccountType.EMPLOYEE,
    val employeeNo: String? = null,
    val factoryCode: String? = null,
    val departmentCode: String? = null,
    val positionCode: String? = null,
    val active: Boolean = true,
)

data class UserAccountRegisterDTO(
    val username: String,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String,
    val password: String,
)

data class UserAccountUpdateDTO(
    val id: Long,
    val email: String? = null,
    val phone: String? = null,
    val displayName: String? = null,
    val accountType: AccountType? = null,
    val employeeNo: String? = null,
    val factoryCode: String? = null,
    val departmentCode: String? = null,
    val positionCode: String? = null,
)

data class UserAccountFreezeDTO(
    val freezeUntil: Instant,
    val reason: String,
)

data class UserAccountResetPasswordDTO(val newPassword: String)

data class UserAccountArchiveDTO(val reason: String? = null)

data class UserAccountPageQuery(
    override val page: Long = 1,
    override val size: Long = 10,
    val keyword: String? = null,
    val status: UserStatus? = null,
) : PageQuery(page, size)

data class UserAccountVO(
    val id: Long,
    val username: String,
    val email: String?,
    val phone: String?,
    val displayName: String,
    val status: UserStatus,
    val accountType: AccountType,
    val employeeNo: String?,
    val factoryCode: String?,
    val departmentCode: String?,
    val positionCode: String?,
    val emailVerified: Boolean,
    val lockedUntil: Instant?,
    val freezeUntil: Instant?,
    val freezeReason: String?,
    val lastLoginAt: Instant?,
    val createTime: Instant,
    val updateTime: Instant,
)

data class LoginSessionVO(
    val id: Long,
    val clientId: String?,
    val status: SessionStatus,
    val ip: String?,
    val userAgent: String?,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val createTime: Instant,
)

data class GroupCreateDTO(
    val code: String,
    val name: String,
    val groupType: GroupType,
    val parentId: Long? = null,
)

data class GroupUpdateDTO(
    val id: Long,
    val name: String? = null,
    val groupType: GroupType? = null,
)

data class GroupVO(
    val id: Long,
    val code: String,
    val name: String,
    val groupType: GroupType,
    val parentId: Long?,
    val path: String,
)

data class GroupTreeVO(
    val id: Long,
    val code: String,
    val name: String,
    val groupType: GroupType,
    val parentId: Long?,
    val path: String,
    val children: List<GroupTreeVO> = emptyList(),
)

data class GroupRoleBindingVO(
    val role: RoleVO,
    val sourceGroupId: Long,
    val sourceGroupCode: String,
    val sourceGroupName: String,
    val sourceGroupPath: String,
    val inherited: Boolean,
)

data class GroupRoleSummaryVO(
    val groupId: Long,
    val directRoles: List<GroupRoleBindingVO>,
    val inheritedRoles: List<GroupRoleBindingVO>,
)

data class UserRoleBindingVO(
    val role: RoleVO,
    val source: String,
    val sourceGroupId: Long? = null,
    val sourceGroupCode: String? = null,
    val sourceGroupName: String? = null,
    val sourceGroupPath: String? = null,
    val inherited: Boolean = false,
    val expiresAt: Instant? = null,
    val reason: String? = null,
)

data class UserAuthorizationDetailVO(
    val userId: Long,
    val groups: List<GroupVO>,
    val directRoles: List<UserRoleBindingVO>,
    val groupRoles: List<UserRoleBindingVO>,
    val effectiveRoles: List<RoleVO>,
    val permissions: List<PermissionVO>,
)

data class AccessApplicationCreateDTO(
    val clientId: String,
    val name: String,
    val appType: ApplicationType,
    val ownerDept: String? = null,
    val redirectUris: List<String> = emptyList(),
    val logoutUris: List<String> = emptyList(),
    val allowedScopes: List<String> = emptyList(),
    val accessTokenTtlSeconds: Long = 900,
    val refreshTokenTtlSeconds: Long = 604800,
)

data class AccessApplicationUpdateDTO(
    val id: Long,
    val name: String? = null,
    val appType: ApplicationType? = null,
    val ownerDept: String? = null,
    val status: ApplicationStatus? = null,
    val redirectUris: List<String>? = null,
    val logoutUris: List<String>? = null,
    val allowedScopes: List<String>? = null,
    val accessTokenTtlSeconds: Long? = null,
    val refreshTokenTtlSeconds: Long? = null,
)

data class AccessApplicationVO(
    val id: Long,
    val clientId: String,
    val name: String,
    val appType: ApplicationType,
    val ownerDept: String?,
    val status: ApplicationStatus,
    val secretVersion: Int,
    val redirectUris: List<String>,
    val logoutUris: List<String>,
    val allowedScopes: List<String>,
    val accessTokenTtlSeconds: Long,
    val refreshTokenTtlSeconds: Long,
)

data class RotatedSecretVO(
    val applicationId: Long,
    val clientId: String,
    val secretVersion: Int,
    val clientSecret: String,
)

data class RoleCreateDTO(
    val code: String,
    val name: String,
    val description: String? = null,
    val scope: RoleScope,
    val applicationId: Long? = null,
)

data class RoleAssignDTO(
    val roleId: Long,
    val expiresAt: Instant? = null,
    val reason: String? = null,
)

data class RoleVO(
    val id: Long,
    val code: String,
    val name: String,
    val description: String?,
    val scope: RoleScope,
    val applicationId: Long?,
    val clientId: String?,
)

data class ResourceCreateDTO(
    val applicationId: Long,
    val resourceCode: String,
    val resourceType: ResourceType,
    val name: String,
    val parentId: Long? = null,
    val attributes: Map<String, Any?> = emptyMap(),
)

data class PolicyCreateDTO(
    val applicationId: Long,
    val code: String,
    val name: String,
    val policyType: PolicyType,
    val rule: Map<String, Any?> = emptyMap(),
)

data class PermissionCreateDTO(
    val roleId: Long,
    val resourceId: Long,
    val scope: String,
    val policyId: Long? = null,
    val effect: PermissionEffect = PermissionEffect.ALLOW,
)

data class PermissionVO(
    val id: Long,
    val roleId: Long,
    val roleCode: String,
    val resourceId: Long,
    val resourceCode: String,
    val clientId: String,
    val scope: String,
    val policyId: Long?,
    val effect: PermissionEffect,
)

data class EffectivePermissionVO(
    val userId: Long,
    val roles: List<RoleVO>,
    val permissions: List<PermissionVO>,
)

data class AuditEventVO(
    val id: Long,
    val category: AuditCategory,
    val eventType: String,
    val actorId: Long?,
    val targetId: Long?,
    val clientId: String?,
    val ip: String?,
    val userAgent: String?,
    val result: AuditResult,
    val reason: String?,
    val traceId: String,
    val payload: Map<String, Any?>,
    val createTime: Instant,
)
