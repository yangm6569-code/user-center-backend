package cn.scysn.iam.application

import cn.scysn.iam.domain.shared.Attributes
import java.time.OffsetDateTime

data class LoginCommand(
    val username: String,
    val password: String,
    val clientId: String,
    val deviceId: String?,
    val rememberMe: Boolean,
    val ip: String?,
    val userAgent: String?,
)

data class RefreshTokenCommand(
    val clientId: String,
    val refreshToken: String,
)

data class ChangePasswordCommand(
    val userId: String,
    val oldPassword: String,
    val newPassword: String,
    val confirmPassword: String,
)

data class CreateUserCommand(
    val username: String,
    val displayName: String,
    val employeeNo: String?,
    val email: String?,
    val phone: String?,
    val accountType: String,
    val factoryCode: String?,
    val departmentCode: String?,
    val orgUnitIds: Set<String>,
    val initialPassword: String,
    val forceChangePassword: Boolean,
    val attributes: Attributes,
)

data class UpdateUserCommand(
    val displayName: String?,
    val email: String?,
    val phone: String?,
    val factoryCode: String?,
    val departmentCode: String?,
    val orgUnitIds: Set<String>?,
    val attributes: Attributes?,
)

data class FreezeUserCommand(
    val freezeUntil: OffsetDateTime,
    val freezeReason: String,
    val revokeSessions: Boolean,
)

data class RevokeUserCommand(
    val reason: String,
    val revokeSessions: Boolean,
)

data class ResetPasswordCommand(
    val mode: String,
    val temporaryPassword: String?,
    val forceChangePassword: Boolean,
    val reason: String,
)

data class GrantUserRolesCommand(
    val roleIds: Set<String>,
    val effectiveFrom: OffsetDateTime?,
    val effectiveTo: OffsetDateTime?,
    val reason: String,
)

data class CreateOrgUnitCommand(
    val parentId: String?,
    val code: String,
    val name: String,
    val type: String,
    val sortOrder: Int,
    val attributes: Attributes,
)

data class UpdateOrgUnitCommand(
    val parentId: String?,
    val code: String?,
    val name: String?,
    val type: String?,
    val sortOrder: Int?,
    val enabled: Boolean?,
    val attributes: Attributes?,
)

data class BindOrgUnitRolesCommand(
    val roleIds: Set<String>,
    val mode: String,
)

data class CreateRoleCommand(
    val appId: String?,
    val roleCode: String,
    val roleName: String,
    val roleType: String,
    val description: String?,
    val enabled: Boolean,
)

data class UpdateRoleCommand(
    val roleName: String?,
    val description: String?,
    val enabled: Boolean?,
)

data class BindRolePermissionsCommand(
    val permissionIds: Set<String>,
    val mode: String,
    val reason: String,
)

data class CreateResourceCommand(
    val appId: String,
    val parentId: String?,
    val resourceCode: String,
    val resourceName: String,
    val resourceType: String,
    val path: String?,
    val method: String?,
    val sortOrder: Int,
    val attributes: Attributes,
)

data class UpdateResourceCommand(
    val resourceName: String?,
    val path: String?,
    val method: String?,
    val sortOrder: Int?,
    val enabled: Boolean?,
    val attributes: Attributes?,
)

data class CreatePermissionCommand(
    val appId: String,
    val resourceId: String?,
    val permissionCode: String,
    val permissionName: String,
    val action: String,
    val description: String?,
)

data class CreateClientAppCommand(
    val clientId: String,
    val name: String,
    val appType: String,
    val ownerDept: String?,
    val redirectUris: Set<String>,
    val logoutUris: Set<String>,
    val tokenPolicy: TokenPolicyCommand,
)

data class UpdateClientAppCommand(
    val name: String?,
    val appType: String?,
    val ownerDept: String?,
    val redirectUris: Set<String>?,
    val logoutUris: Set<String>?,
    val tokenPolicy: TokenPolicyCommand?,
    val status: String?,
)

data class RotateSecretCommand(
    val reason: String,
    val gracePeriodMinutes: Long,
)

data class TokenPolicyCommand(
    val accessTokenTtlSeconds: Long,
    val refreshTokenIdleSeconds: Long,
    val refreshTokenMaxSeconds: Long,
    val ssoSessionIdleSeconds: Long = 7200,
    val ssoSessionMaxSeconds: Long = 43200,
)

data class ImportUsersCommand(
    val mode: String,
    val items: List<ImportUserItemCommand>,
)

data class ImportUserItemCommand(
    val username: String,
    val displayName: String,
    val employeeNo: String?,
    val factoryCode: String?,
    val departmentCode: String?,
)

data class ExportAuditCommand(
    val eventTypes: Set<String>,
    val startTime: OffsetDateTime?,
    val endTime: OffsetDateTime?,
    val format: String,
    val reason: String,
)
