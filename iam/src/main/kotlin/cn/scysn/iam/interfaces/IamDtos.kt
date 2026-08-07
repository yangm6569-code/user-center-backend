package cn.scysn.iam.interfaces

import cn.scysn.common.api.PageResponse
import cn.scysn.iam.application.BindOrgUnitRolesCommand
import cn.scysn.iam.application.BindRolePermissionsCommand
import cn.scysn.iam.application.ApiPermissionRule
import cn.scysn.iam.application.ConfigureDataScopeCommand
import cn.scysn.iam.application.ConfigureFieldPermissionsCommand
import cn.scysn.iam.application.CreateBusinessDomainCommand
import cn.scysn.iam.application.CreateClientAppCommand
import cn.scysn.iam.application.CreateOrgUnitCommand
import cn.scysn.iam.application.CreatePermissionCommand
import cn.scysn.iam.application.CreateResourceCommand
import cn.scysn.iam.application.CreateRoleCommand
import cn.scysn.iam.application.CreateSystemDomainCommand
import cn.scysn.iam.application.CreateUserCommand
import cn.scysn.iam.application.ExportAuditCommand
import cn.scysn.iam.application.FieldPermissionEntry
import cn.scysn.iam.application.FreezeUserCommand
import cn.scysn.iam.application.GrantUserRolesCommand
import cn.scysn.iam.application.ImportDataScopeEntry
import cn.scysn.iam.application.ImportPermissionModelCommand
import cn.scysn.iam.application.ImportRoleEntry
import cn.scysn.iam.application.ImportUserItemCommand
import cn.scysn.iam.application.ImportUsersCommand
import cn.scysn.iam.application.OidcClientConfigCommand
import cn.scysn.iam.application.RevokeUserCommand
import cn.scysn.iam.application.RotateSecretCommand
import cn.scysn.iam.application.SyncBusinessDomainEntry
import cn.scysn.iam.application.SyncFieldEntry
import cn.scysn.iam.application.SyncMetadataCommand
import cn.scysn.iam.application.SyncResourceEntry
import cn.scysn.iam.application.TokenPolicyCommand
import cn.scysn.iam.application.UpdateBusinessDomainCommand
import cn.scysn.iam.application.UpdateClientAppCommand
import cn.scysn.iam.application.UpdateOrgUnitCommand
import cn.scysn.iam.application.UpdatePermissionCommand
import cn.scysn.iam.application.UpdateResourceCommand
import cn.scysn.iam.application.UpdateRoleCommand
import cn.scysn.iam.application.UpdateSystemDomainCommand
import cn.scysn.iam.application.UpdateUserCommand
import cn.scysn.iam.application.CreateClientAppResult
import cn.scysn.iam.application.ExportTaskResult
import cn.scysn.iam.application.ImportResult
import cn.scysn.iam.application.OrgUnitTreeNode
import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.CurrentUserInfo
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.authorization.BusinessDomain
import cn.scysn.iam.domain.authorization.DataScopeConfig
import cn.scysn.iam.domain.authorization.EffectivePermission
import cn.scysn.iam.domain.authorization.FieldPermission
import cn.scysn.iam.domain.authorization.FieldPolicy
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.clientapp.OidcClientConfig
import cn.scysn.iam.domain.clientapp.SecretRotation
import cn.scysn.iam.domain.system.SystemDomain
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.organization.OrgUnit
import cn.scysn.iam.domain.shared.Attributes
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.OffsetDateTime

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    @field:NotBlank val clientId: String,
    val deviceId: String? = null,
    val rememberMe: Boolean = false,
)

data class RefreshTokenRequest(
    @field:NotBlank val clientId: String,
    @field:NotBlank val refreshToken: String,
)

data class LogoutRequest(
    @field:NotBlank val refreshToken: String,
)

data class LogoutAllRequest(
    val reason: String? = null,
)

data class ChangePasswordRequest(
    @field:NotBlank val oldPassword: String,
    @field:NotBlank val newPassword: String,
    @field:NotBlank val confirmPassword: String,
)

data class PasswordResetRequest(
    val username: String?,
    val email: String?,
    val clientId: String?,
)

data class PasswordResetConfirmRequest(
    @field:NotBlank val resetToken: String,
    @field:NotBlank val newPassword: String,
    @field:NotBlank val confirmPassword: String,
)

data class CreateUserRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val displayName: String,
    val employeeNo: String? = null,
    val email: String? = null,
    val phone: String? = null,
    @field:NotBlank val accountType: String = "employee",
    val factoryCode: String? = null,
    val departmentCode: String? = null,
    val orgUnitIds: Set<String> = emptySet(),
    @field:NotBlank val initialPassword: String,
    val forceChangePassword: Boolean = true,
    val attributes: Attributes = emptyMap(),
) {
    fun toCommand(): CreateUserCommand = CreateUserCommand(
        username = username,
        displayName = displayName,
        employeeNo = employeeNo,
        email = email,
        phone = phone,
        accountType = accountType,
        factoryCode = factoryCode,
        departmentCode = departmentCode,
        orgUnitIds = orgUnitIds,
        initialPassword = initialPassword,
        forceChangePassword = forceChangePassword,
        attributes = attributes
    )
}

data class UpdateUserRequest(
    val displayName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val factoryCode: String? = null,
    val departmentCode: String? = null,
    val orgUnitIds: Set<String>? = null,
    val attributes: Attributes? = null,
) {
    fun toCommand(): UpdateUserCommand = UpdateUserCommand(
        displayName = displayName,
        email = email,
        phone = phone,
        factoryCode = factoryCode,
        departmentCode = departmentCode,
        orgUnitIds = orgUnitIds,
        attributes = attributes
    )
}

data class ReasonRequest(
    @field:NotBlank val reason: String,
)

data class DisableUserRequest(
    @field:NotBlank val reason: String,
    val revokeSessions: Boolean = true,
) {
    fun toCommand(): RevokeUserCommand = RevokeUserCommand(reason, revokeSessions)
}

data class FreezeUserRequest(
    @field:NotNull val freezeUntil: OffsetDateTime,
    @field:NotBlank val freezeReason: String,
    val revokeSessions: Boolean = true,
) {
    fun toCommand(): FreezeUserCommand = FreezeUserCommand(freezeUntil, freezeReason, revokeSessions)
}

data class ArchiveUserRequest(
    @field:NotBlank val archiveReason: String,
    val revokeSessions: Boolean = true,
) {
    fun toCommand(): RevokeUserCommand = RevokeUserCommand(archiveReason, revokeSessions)
}

data class ResetPasswordRequest(
    @field:NotBlank val mode: String,
    val temporaryPassword: String? = null,
    val forceChangePassword: Boolean = true,
    @field:NotBlank val reason: String,
)

data class GrantUserRolesRequest(
    @field:NotEmpty val roleIds: Set<String>,
    val effectiveFrom: OffsetDateTime? = null,
    val effectiveTo: OffsetDateTime? = null,
    @field:NotBlank val reason: String,
) {
    fun toCommand(): GrantUserRolesCommand = GrantUserRolesCommand(roleIds, effectiveFrom, effectiveTo, reason)
}

data class CreateOrgUnitRequest(
    val parentId: String? = null,
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    @field:NotBlank val type: String,
    val sortOrder: Int = 0,
    val attributes: Attributes = emptyMap(),
) {
    fun toCommand(): CreateOrgUnitCommand = CreateOrgUnitCommand(parentId, code, name, type, sortOrder, attributes)
}

data class UpdateOrgUnitRequest(
    val parentId: String? = null,
    val code: String? = null,
    val name: String? = null,
    val type: String? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    val attributes: Attributes? = null,
) {
    fun toCommand(): UpdateOrgUnitCommand = UpdateOrgUnitCommand(parentId, code, name, type, sortOrder, enabled, attributes)
}

data class BindOrgUnitRolesRequest(
    @field:NotEmpty val roleIds: Set<String>,
    @field:NotBlank val mode: String = "append",
) {
    fun toCommand(): BindOrgUnitRolesCommand = BindOrgUnitRolesCommand(roleIds, mode)
}

data class AddOrgUnitUsersRequest(
    @field:NotEmpty val userIds: Set<String>,
)

data class CreateRoleRequest(
    val domainId: String? = null,
    val appId: String? = null,
    @field:NotBlank val roleCode: String,
    @field:NotBlank val roleName: String,
    @field:NotBlank val roleType: String,
    val description: String? = null,
    val enabled: Boolean = true,
) {
    fun toCommand(): CreateRoleCommand = CreateRoleCommand(domainId, appId, roleCode, roleName, roleType, description, enabled)
}

data class UpdateRoleRequest(
    val roleName: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
) {
    fun toCommand(): UpdateRoleCommand = UpdateRoleCommand(roleName, description, enabled)
}

data class BindRolePermissionsRequest(
    @field:NotEmpty val permissionIds: Set<String>,
    @field:NotBlank val mode: String,
    @field:NotBlank val reason: String,
) {
    fun toCommand(): BindRolePermissionsCommand = BindRolePermissionsCommand(permissionIds, mode, reason)
}

data class CreateResourceRequest(
    val domainId: String? = null,
    @field:NotBlank val appId: String,
    val businessDomainId: String? = null,
    val parentId: String? = null,
    @field:NotBlank val resourceCode: String,
    @field:NotBlank val resourceName: String,
    @field:NotBlank val resourceType: String,
    val path: String? = null,
    val method: String? = null,
    val sortOrder: Int = 0,
    val attributes: Attributes = emptyMap(),
) {
    fun toCommand(): CreateResourceCommand = CreateResourceCommand(
        domainId, appId, businessDomainId, parentId, resourceCode, resourceName, resourceType, path, method, sortOrder, attributes
    )
}

data class UpdateResourceRequest(
    val resourceName: String? = null,
    val path: String? = null,
    val method: String? = null,
    val sortOrder: Int? = null,
    val enabled: Boolean? = null,
    val attributes: Attributes? = null,
) {
    fun toCommand(): UpdateResourceCommand = UpdateResourceCommand(resourceName, path, method, sortOrder, enabled, attributes)
}

data class CreatePermissionRequest(
    val domainId: String? = null,
    @field:NotBlank val appId: String,
    val businessDomainId: String? = null,
    val resourceId: String? = null,
    @field:NotBlank val permissionCode: String,
    @field:NotBlank val permissionName: String,
    @field:NotBlank val action: String,
    val description: String? = null,
) {
    fun toCommand(): CreatePermissionCommand = CreatePermissionCommand(domainId, appId, businessDomainId, resourceId, permissionCode, permissionName, action, description)
}

data class UpdatePermissionRequest(
    val permissionName: String? = null,
    val action: String? = null,
    val description: String? = null,
) {
    fun toCommand(): UpdatePermissionCommand = UpdatePermissionCommand(permissionName, action, description)
}

data class TokenPolicyRequest(
    val accessTokenTtlSeconds: Long = 900,
    val refreshTokenIdleSeconds: Long = 7200,
    val refreshTokenMaxSeconds: Long = 43200,
    val ssoSessionIdleSeconds: Long = 7200,
    val ssoSessionMaxSeconds: Long = 43200,
) {
    fun toCommand(): TokenPolicyCommand = TokenPolicyCommand(
        accessTokenTtlSeconds,
        refreshTokenIdleSeconds,
        refreshTokenMaxSeconds,
        ssoSessionIdleSeconds,
        ssoSessionMaxSeconds
    )
}

data class CreateClientAppRequest(
    @field:NotBlank val domainId: String = "user-center",
    @field:NotBlank val clientId: String,
    @field:NotBlank val name: String,
    @field:NotBlank val appType: String,
    val ownerDept: String? = null,
    val redirectUris: Set<String> = emptySet(),
    val logoutUris: Set<String> = emptySet(),
    @field:Valid val tokenPolicy: TokenPolicyRequest = TokenPolicyRequest(),
) {
    fun toCommand(): CreateClientAppCommand = CreateClientAppCommand(
        domainId, clientId, name, appType, ownerDept, redirectUris, logoutUris, tokenPolicy.toCommand()
    )
}

data class UpdateClientAppRequest(
    val name: String? = null,
    val domainId: String? = null,
    val appType: String? = null,
    val ownerDept: String? = null,
    val redirectUris: Set<String>? = null,
    val logoutUris: Set<String>? = null,
    @field:Valid val tokenPolicy: TokenPolicyRequest? = null,
    val status: String? = null,
    @field:Valid val oidcConfig: OidcClientConfigRequest? = null,
) {
    fun toCommand(): UpdateClientAppCommand = UpdateClientAppCommand(
        name, domainId, appType, ownerDept, redirectUris, logoutUris, tokenPolicy?.toCommand(), status, oidcConfig?.toCommand()
    )
}

data class OidcClientConfigRequest(
    val issuer: String? = null,
    val discoveryUrl: String? = null,
    val authorizeUrl: String? = null,
    val tokenUrl: String? = null,
    val userInfoUrl: String? = null,
    val jwksUrl: String? = null,
    val scope: String? = null,
    val responseType: String? = null,
    val grantType: String? = null,
) {
    fun toCommand(): OidcClientConfigCommand = OidcClientConfigCommand(
        issuer, discoveryUrl, authorizeUrl, tokenUrl, userInfoUrl, jwksUrl, scope, responseType, grantType
    )
}

data class CreateSystemDomainRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null,
) {
    fun toCommand(): CreateSystemDomainCommand = CreateSystemDomainCommand(code, name, description)
}

data class UpdateSystemDomainRequest(
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
) {
    fun toCommand(): UpdateSystemDomainCommand = UpdateSystemDomainCommand(name, description, enabled)
}

data class RotateSecretRequest(
    @field:NotBlank val reason: String,
    val gracePeriodMinutes: Long = 60,
) {
    fun toCommand(): RotateSecretCommand = RotateSecretCommand(reason, gracePeriodMinutes)
}

data class UpdateAccountProfileRequest(
    val displayName: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

data class ImportUsersRequest(
    @field:NotBlank val mode: String,
    val items: List<ImportUserItemRequest>,
) {
    fun toCommand(): ImportUsersCommand = ImportUsersCommand(
        mode = mode,
        items = items.map { ImportUserItemCommand(it.username, it.displayName, it.employeeNo, it.factoryCode, it.departmentCode) }
    )
}

data class ImportUserItemRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val displayName: String,
    val employeeNo: String? = null,
    val factoryCode: String? = null,
    val departmentCode: String? = null,
)

data class ExportAuditRequest(
    val eventTypes: Set<String> = emptySet(),
    val startTime: OffsetDateTime? = null,
    val endTime: OffsetDateTime? = null,
    @field:NotBlank val format: String = "xlsx",
    @field:NotBlank val reason: String,
) {
    fun toCommand(): ExportAuditCommand = ExportAuditCommand(eventTypes, startTime, endTime, format, reason)
}

data class CreateUserResponse(
    val id: String,
    val username: String,
    val status: String,
    val requiredActions: List<String>,
)

data class UserSummaryResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val employeeNo: String?,
    val email: String?,
    val phone: String?,
    val status: String,
    val factoryCode: String?,
    val departmentCode: String?,
    val createdAt: OffsetDateTime,
)

data class UserDetailResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val employeeNo: String?,
    val email: String?,
    val phone: String?,
    val status: String,
    val accountType: String,
    val factoryCode: String?,
    val departmentCode: String?,
    val orgUnitIds: Set<String>,
    val requiredActions: List<String>,
    val roles: List<String>,
    val attributes: Attributes,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

data class SessionResponse(
    val id: String,
    val userId: String,
    val clientId: String,
    val ip: String?,
    val userAgent: String?,
    val status: String,
    val createdAt: OffsetDateTime,
    val lastActiveAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
)

data class OrgUnitResponse(
    val id: String,
    val parentId: String?,
    val code: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val sortOrder: Int,
    val roleIds: Set<String>,
    val attributes: Attributes,
)

data class RoleResponse(
    val id: String,
    val domainId: String?,
    val appId: String?,
    val roleCode: String,
    val roleName: String,
    val roleType: String,
    val description: String?,
    val enabled: Boolean,
    val permissionIds: Set<String>,
)

data class ResourceResponse(
    val id: String,
    val domainId: String,
    val appId: String,
    val businessDomainId: String?,
    val parentId: String?,
    val resourceCode: String,
    val resourceName: String,
    val resourceType: String,
    val path: String?,
    val method: String?,
    val sortOrder: Int,
    val enabled: Boolean,
    val attributes: Attributes,
)

data class PermissionResponse(
    val id: String,
    val domainId: String,
    val appId: String,
    val businessDomainId: String?,
    val resourceId: String?,
    val permissionCode: String,
    val permissionName: String,
    val action: String,
    val description: String?,
    val enabled: Boolean,
)

data class ApiPermissionRuleResponse(
    val appId: String,
    val resourceId: String,
    val resourceCode: String,
    val resourceName: String,
    val method: String,
    val path: String,
    val permissionId: String,
    val permissionCode: String,
    val action: String,
)

data class ClientAppResponse(
    val id: String,
    val domainId: String,
    val clientId: String,
    val name: String,
    val appType: String,
    val ownerDept: String?,
    val status: String,
    val redirectUris: Set<String>,
    val logoutUris: Set<String>,
    val tokenPolicy: TokenPolicyRequest,
    val secretVersion: Int,
    val oidcConfig: OidcClientConfigResponse,
)

data class OidcClientConfigResponse(
    val issuer: String?,
    val discoveryUrl: String?,
    val authorizeUrl: String?,
    val tokenUrl: String?,
    val userInfoUrl: String?,
    val jwksUrl: String?,
    val scope: String,
    val responseType: String,
    val grantType: String,
)

data class SystemDomainResponse(
    val id: String,
    val code: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
)

data class CreateClientAppResponse(
    val id: String,
    val clientId: String,
    val clientSecret: String,
    val secretVersion: Int,
)

data class RotateSecretResponse(
    val clientId: String,
    val clientSecret: String,
    val secretVersion: Int,
    val oldSecretExpiresAt: OffsetDateTime,
)

data class AuditEventResponse(
    val id: String,
    val eventType: String,
    val actorId: String?,
    val targetId: String?,
    val clientId: String?,
    val ip: String?,
    val userAgent: String?,
    val result: String,
    val reason: String?,
    val traceId: String?,
    val payload: Attributes,
    val createdAt: OffsetDateTime,
)

fun User.toCreateResponse(): CreateUserResponse = CreateUserResponse(
    id = id,
    username = username,
    status = status.code,
    requiredActions = requiredActions.map { it.code },
)

fun User.toSummaryResponse(): UserSummaryResponse = UserSummaryResponse(
    id = id,
    username = username,
    displayName = displayName,
    employeeNo = profile.employeeNo,
    email = email,
    phone = phone,
    status = status.code,
    factoryCode = profile.factoryCode,
    departmentCode = profile.departmentCode,
    createdAt = createdAt,
)

fun User.toDetailResponse(): UserDetailResponse = UserDetailResponse(
    id = id,
    username = username,
    displayName = displayName,
    employeeNo = profile.employeeNo,
    email = email,
    phone = phone,
    status = status.code,
    accountType = profile.accountType.code,
    factoryCode = profile.factoryCode,
    departmentCode = profile.departmentCode,
    orgUnitIds = profile.orgUnitIds,
    requiredActions = requiredActions.map { it.code },
    roles = roleGrants.map { it.roleId },
    attributes = profile.attributes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun LoginSession.toResponse(): SessionResponse = SessionResponse(
    id = id,
    userId = userId,
    clientId = clientId,
    ip = ip,
    userAgent = userAgent,
    status = status.code,
    createdAt = createdAt,
    lastActiveAt = lastActiveAt,
    expiresAt = expiresAt,
)

fun OrgUnit.toResponse(): OrgUnitResponse = OrgUnitResponse(id, parentId, code, name, type, enabled, sortOrder, roleIds, attributes)

fun Role.toResponse(): RoleResponse = RoleResponse(id, domainId, appId, roleCode, roleName, roleType.code, description, enabled, permissionIds)

fun Resource.toResponse(): ResourceResponse = ResourceResponse(
    id, domainId, appId, businessDomainId, parentId, resourceCode, resourceName, resourceType.code, path, method, sortOrder, enabled, attributes
)

fun Permission.toResponse(): PermissionResponse = PermissionResponse(
    id, domainId, appId, businessDomainId, resourceId, permissionCode, permissionName, action, description, enabled
)

fun ApiPermissionRule.toResponse(): ApiPermissionRuleResponse = ApiPermissionRuleResponse(
    appId = appId,
    resourceId = resourceId,
    resourceCode = resourceCode,
    resourceName = resourceName,
    method = method,
    path = path,
    permissionId = permissionId,
    permissionCode = permissionCode,
    action = action,
)

fun ClientApp.toResponse(): ClientAppResponse = ClientAppResponse(
    id = id,
    domainId = domainId,
    clientId = clientId,
    name = name,
    appType = appType,
    ownerDept = ownerDept,
    status = status.code,
    redirectUris = redirectUris,
    logoutUris = logoutUris,
    tokenPolicy = TokenPolicyRequest(
        accessTokenTtlSeconds = tokenPolicy.accessTokenTtlSeconds,
        refreshTokenIdleSeconds = tokenPolicy.refreshTokenIdleSeconds,
        refreshTokenMaxSeconds = tokenPolicy.refreshTokenMaxSeconds,
        ssoSessionIdleSeconds = tokenPolicy.ssoSessionIdleSeconds,
        ssoSessionMaxSeconds = tokenPolicy.ssoSessionMaxSeconds
    ),
    secretVersion = secretVersion,
    oidcConfig = oidcConfig.toResponse(),
)

fun OidcClientConfig.toResponse(): OidcClientConfigResponse = OidcClientConfigResponse(
    issuer = issuer,
    discoveryUrl = discoveryUrl,
    authorizeUrl = authorizeUrl,
    tokenUrl = tokenUrl,
    userInfoUrl = userInfoUrl,
    jwksUrl = jwksUrl,
    scope = scope,
    responseType = responseType,
    grantType = grantType,
)

fun SystemDomain.toResponse(): SystemDomainResponse = SystemDomainResponse(id, code, name, description, enabled)

fun CreateClientAppResult.toResponse(): CreateClientAppResponse = CreateClientAppResponse(
    id = app.id,
    clientId = app.clientId,
    clientSecret = clientSecret,
    secretVersion = app.secretVersion,
)

fun SecretRotation.toResponse(): RotateSecretResponse = RotateSecretResponse(
    clientId = clientId,
    clientSecret = clientSecret,
    secretVersion = secretVersion,
    oldSecretExpiresAt = oldSecretExpiresAt,
)

fun AuditEvent.toResponse(): AuditEventResponse = AuditEventResponse(
    id = id,
    eventType = eventType,
    actorId = actorId,
    targetId = targetId,
    clientId = clientId,
    ip = ip,
    userAgent = userAgent,
    result = result,
    reason = reason,
    traceId = traceId,
    payload = payload,
    createdAt = createdAt,
)

fun CurrentUserInfo.toUserDetailResponse(): UserDetailResponse = UserDetailResponse(
    id = id,
    username = username,
    displayName = displayName,
    employeeNo = employeeNo,
    email = email,
    phone = phone,
    status = status,
    accountType = "employee",
    factoryCode = factoryCode,
    departmentCode = departmentCode,
    orgUnitIds = emptySet(),
    requiredActions = requiredActions,
    roles = emptyList(),
    attributes = emptyMap(),
    createdAt = OffsetDateTime.now(),
    updatedAt = OffsetDateTime.now(),
)

fun <T, R> PageResponse<T>.mapItems(transform: (T) -> R): PageResponse<R> {
    return PageResponse(items.map(transform), page, pageSize, total, totalPages)
}

// --- Business Domain DTOs ---

data class CreateBusinessDomainRequest(
    @field:NotBlank val domainId: String,
    @field:NotBlank val appId: String,
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null,
) {
    fun toCommand(): CreateBusinessDomainCommand = CreateBusinessDomainCommand(domainId, appId, code, name, description)
}

data class UpdateBusinessDomainRequest(
    val name: String? = null,
    val description: String? = null,
    val enabled: Boolean? = null,
) {
    fun toCommand(): UpdateBusinessDomainCommand = UpdateBusinessDomainCommand(name, description, enabled)
}

data class BusinessDomainResponse(
    val id: String,
    val domainId: String,
    val appId: String,
    val code: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

fun BusinessDomain.toResponse(): BusinessDomainResponse = BusinessDomainResponse(
    id, domainId, appId, code, name, description, enabled, createdAt, updatedAt
)

// --- Field Permission DTOs ---

data class FieldPermissionEntryRequest(
    @field:NotBlank val fieldCode: String,
    val fieldName: String? = null,
    @field:NotBlank val permissionType: String,
)

data class ConfigureFieldPermissionsRequest(
    @field:NotBlank val roleId: String,
    @field:NotEmpty val fields: List<FieldPermissionEntryRequest>,
    @field:NotBlank val reason: String,
) {
    fun toCommand(): ConfigureFieldPermissionsCommand = ConfigureFieldPermissionsCommand(
        roleId = roleId,
        fields = fields.map { FieldPermissionEntry(it.fieldCode, it.fieldName, it.permissionType) },
        reason = reason,
    )
}

data class FieldPermissionResponse(
    val id: String,
    val businessDomainId: String,
    val roleId: String,
    val fieldCode: String,
    val fieldName: String?,
    val permissionType: String,
    val enabled: Boolean,
)

data class FieldPolicyResponse(
    val visible: Set<String>,
    val hidden: Set<String>,
    val masked: Set<String>,
    val readonly: Set<String>,
)

fun FieldPermission.toResponse(): FieldPermissionResponse = FieldPermissionResponse(
    id, businessDomainId, roleId, fieldCode, fieldName, permissionType.code, enabled
)

// --- Data Scope DTOs ---

data class ConfigureDataScopeRequest(
    @field:NotBlank val roleId: String,
    @field:NotBlank val scopeType: String,
    val scopeValue: Set<String> = emptySet(),
    @field:NotBlank val reason: String,
) {
    fun toCommand(): ConfigureDataScopeCommand = ConfigureDataScopeCommand(roleId, scopeType, scopeValue, reason)
}

data class DataScopeConfigResponse(
    val id: String,
    val businessDomainId: String,
    val roleId: String,
    val scopeType: String,
    val scopeValue: Set<String>,
    val enabled: Boolean,
)

fun DataScopeConfig.toResponse(): DataScopeConfigResponse = DataScopeConfigResponse(
    id, businessDomainId, roleId, scopeType, scopeValue, enabled
)

// --- Metadata Sync DTOs ---

data class SyncFieldEntryRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    @field:NotBlank val type: String,
    val sensitive: Boolean = false,
)

data class SyncResourceEntryRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    @field:NotBlank val type: String,
    val parentCode: String? = null,
    val path: String? = null,
    val method: String? = null,
    val sortOrder: Int = 0,
    val operations: List<String>? = null,
    val fields: List<SyncFieldEntryRequest>? = null,
)

data class SyncBusinessDomainEntryRequest(
    @field:NotBlank val code: String,
    @field:NotBlank val name: String,
    val description: String? = null,
    val resources: List<SyncResourceEntryRequest>? = null,
)

data class SyncMetadataRequest(
    @field:NotBlank val appId: String,
    @field:NotEmpty val businessDomains: List<SyncBusinessDomainEntryRequest>,
) {
    fun toCommand(): SyncMetadataCommand = SyncMetadataCommand(
        appId = appId,
        businessDomains = businessDomains.map { bd ->
            SyncBusinessDomainEntry(
                code = bd.code,
                name = bd.name,
                description = bd.description,
                resources = bd.resources?.map { res ->
                    SyncResourceEntry(
                        code = res.code,
                        name = res.name,
                        type = res.type,
                        parentCode = res.parentCode,
                        path = res.path,
                        method = res.method,
                        sortOrder = res.sortOrder,
                        operations = res.operations,
                        fields = res.fields?.map { SyncFieldEntry(it.code, it.name, it.type, it.sensitive) },
                    )
                },
            )
        },
    )
}

// --- Permission Model Import DTOs ---

data class ImportDataScopeEntryRequest(
    @field:NotBlank val scopeType: String,
    val scopeValue: Set<String> = emptySet(),
)

data class ImportRoleEntryRequest(
    @field:NotBlank val roleCode: String,
    @field:NotBlank val roleName: String,
    @field:NotBlank val roleType: String,
    val permissionCodes: Set<String> = emptySet(),
    val fieldPermissions: Map<String, String>? = null,
    val dataScope: ImportDataScopeEntryRequest? = null,
)

data class ImportPermissionModelRequest(
    @field:NotBlank val appId: String,
    @field:NotBlank val sourceSystem: String,
    @field:NotEmpty val roles: List<ImportRoleEntryRequest>,
    @field:NotBlank val reason: String,
) {
    fun toCommand(): ImportPermissionModelCommand = ImportPermissionModelCommand(
        appId = appId,
        sourceSystem = sourceSystem,
        roles = roles.map { r ->
            ImportRoleEntry(
                roleCode = r.roleCode,
                roleName = r.roleName,
                roleType = r.roleType,
                permissionCodes = r.permissionCodes,
                fieldPermissions = r.fieldPermissions,
                dataScope = r.dataScope?.let { ImportDataScopeEntry(it.scopeType, it.scopeValue) },
            )
        },
        reason = reason,
    )
}
