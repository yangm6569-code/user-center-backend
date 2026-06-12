package cn.scysn.iam.infrastructure.persistence.jpa.entity

import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.AuthorizationCode
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.auth.SessionStatus
import cn.scysn.iam.domain.auth.SsoSession
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.ResourceType
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.authorization.RoleType
import cn.scysn.iam.domain.clientapp.AppStatus
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.clientapp.TokenPolicy
import cn.scysn.iam.domain.identity.AccountType
import cn.scysn.iam.domain.identity.RequiredAction
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.identity.UserProfile
import cn.scysn.iam.domain.identity.UserRoleGrant
import cn.scysn.iam.domain.identity.UserStatus
import cn.scysn.iam.domain.organization.OrgUnit
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(
    name = "iam_user",
    indexes = [
        Index(name = "idx_iam_user_username", columnList = "username", unique = true),
        Index(name = "idx_iam_user_status", columnList = "status"),
        Index(name = "idx_iam_user_email", columnList = "email", unique = true),
        Index(name = "idx_iam_user_phone", columnList = "phone", unique = true)
    ]
)
open class IamUserEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(nullable = false, length = 128)
    open var username: String = ""

    @Column(name = "display_name", nullable = false, length = 128)
    open var displayName: String = ""

    @Column(length = 255)
    open var email: String? = null

    @Column(length = 32)
    open var phone: String? = null

    @Column(nullable = false, length = 32)
    open var status: String = UserStatus.PENDING.code

    @Column(nullable = false)
    open var enabled: Boolean = true

    @Column(name = "required_actions_json", nullable = false, columnDefinition = "text")
    open var requiredActionsJson: String = "[]"

    @Column(name = "role_grants_json", nullable = false, columnDefinition = "text")
    open var roleGrantsJson: String = "[]"

    @Column(name = "last_login_at")
    open var lastLoginAt: OffsetDateTime? = null

    @Column(name = "freeze_until")
    open var freezeUntil: OffsetDateTime? = null

    @Column(name = "freeze_reason", length = 512)
    open var freezeReason: String? = null

    @Column(name = "archived_at")
    open var archivedAt: OffsetDateTime? = null

    @Column(name = "archive_reason", length = 512)
    open var archiveReason: String? = null

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(credential: IamUserCredentialEntity, profile: IamUserProfileEntity): User {
        return User.restore(
            id = id,
            username = username,
            displayName = displayName,
            email = email,
            phone = phone,
            profile = profile.toDomain(),
            passwordHash = credential.passwordHash,
            passwordTemporary = credential.passwordTemporary,
            status = enumByCode(status, UserStatus.entries) { it.code },
            enabled = enabled,
            requiredActions = JsonColumns.readSet(requiredActionsJson),
            roleGrants = JsonColumns.readSet(roleGrantsJson),
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastLoginAt = lastLoginAt,
            passwordChangedAt = credential.passwordChangedAt,
            freezeUntil = freezeUntil,
            freezeReason = freezeReason,
            archivedAt = archivedAt,
            archiveReason = archiveReason
        )
    }

    companion object {
        fun fromDomain(user: User): IamUserEntity {
            return IamUserEntity().apply {
                id = user.id
                username = user.username
                displayName = user.displayName
                email = user.email
                phone = user.phone
                status = user.status.code
                enabled = user.enabled
                requiredActionsJson = JsonColumns.write(user.requiredActions)
                roleGrantsJson = JsonColumns.write(user.roleGrants)
                lastLoginAt = user.lastLoginAt
                freezeUntil = user.freezeUntil
                freezeReason = user.freezeReason
                archivedAt = user.archivedAt
                archiveReason = user.archiveReason
                createdAt = user.createdAt
                updatedAt = user.updatedAt
            }
        }
    }
}

@Entity
@Table(name = "iam_user_credential")
open class IamUserCredentialEntity {
    @Id
    @Column(name = "user_id", length = 64)
    open var userId: String = ""

    @Column(name = "password_hash", nullable = false, length = 256)
    open var passwordHash: String = ""

    @Column(name = "password_temporary", nullable = false)
    open var passwordTemporary: Boolean = false

    @Column(name = "password_changed_at")
    open var passwordChangedAt: OffsetDateTime? = null

    companion object {
        fun fromDomain(user: User): IamUserCredentialEntity {
            return IamUserCredentialEntity().apply {
                userId = user.id
                passwordHash = user.passwordHash
                passwordTemporary = user.passwordTemporary
                passwordChangedAt = user.passwordChangedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_user_profile",
    indexes = [
        Index(name = "idx_iam_user_profile_employee_no", columnList = "employee_no", unique = true),
        Index(name = "idx_iam_user_profile_factory", columnList = "factory_code"),
        Index(name = "idx_iam_user_profile_department", columnList = "department_code")
    ]
)
open class IamUserProfileEntity {
    @Id
    @Column(name = "user_id", length = 64)
    open var userId: String = ""

    @Column(name = "employee_no", length = 64)
    open var employeeNo: String? = null

    @Column(name = "account_type", nullable = false, length = 32)
    open var accountType: String = AccountType.EMPLOYEE.code

    @Column(name = "factory_code", length = 64)
    open var factoryCode: String? = null

    @Column(name = "department_code", length = 64)
    open var departmentCode: String? = null

    @Column(name = "org_unit_ids_json", nullable = false, columnDefinition = "text")
    open var orgUnitIdsJson: String = "[]"

    @Column(name = "attributes_json", nullable = false, columnDefinition = "text")
    open var attributesJson: String = "{}"

    fun toDomain(): UserProfile {
        return UserProfile(
            employeeNo = employeeNo,
            accountType = enumByCode(accountType, AccountType.entries) { it.code },
            factoryCode = factoryCode,
            departmentCode = departmentCode,
            orgUnitIds = JsonColumns.readSet(orgUnitIdsJson),
            attributes = JsonColumns.readAttributes(attributesJson)
        )
    }

    companion object {
        fun fromDomain(user: User): IamUserProfileEntity {
            return IamUserProfileEntity().apply {
                userId = user.id
                employeeNo = user.profile.employeeNo
                accountType = user.profile.accountType.code
                factoryCode = user.profile.factoryCode
                departmentCode = user.profile.departmentCode
                orgUnitIdsJson = JsonColumns.write(user.profile.orgUnitIds)
                attributesJson = JsonColumns.write(user.profile.attributes)
            }
        }
    }
}

@Entity
@Table(
    name = "iam_org_unit",
    indexes = [
        Index(name = "idx_iam_org_unit_code", columnList = "code", unique = true),
        Index(name = "idx_iam_org_unit_parent", columnList = "parent_id")
    ]
)
open class IamOrgUnitEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "parent_id", length = 64)
    open var parentId: String? = null

    @Column(nullable = false, length = 128)
    open var code: String = ""

    @Column(nullable = false, length = 128)
    open var name: String = ""

    @Column(nullable = false, length = 64)
    open var type: String = ""

    @Column(name = "sort_order", nullable = false)
    open var sortOrder: Int = 0

    @Column(nullable = false)
    open var enabled: Boolean = true

    @Column(name = "role_ids_json", nullable = false, columnDefinition = "text")
    open var roleIdsJson: String = "[]"

    @Column(name = "attributes_json", nullable = false, columnDefinition = "text")
    open var attributesJson: String = "{}"

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): OrgUnit {
        return OrgUnit.restore(
            id = id,
            parentId = parentId,
            code = code,
            name = name,
            type = type,
            sortOrder = sortOrder,
            enabled = enabled,
            roleIds = JsonColumns.readSet(roleIdsJson),
            attributes = JsonColumns.readAttributes(attributesJson),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(orgUnit: OrgUnit): IamOrgUnitEntity {
            return IamOrgUnitEntity().apply {
                id = orgUnit.id
                parentId = orgUnit.parentId
                code = orgUnit.code
                name = orgUnit.name
                type = orgUnit.type
                sortOrder = orgUnit.sortOrder
                enabled = orgUnit.enabled
                roleIdsJson = JsonColumns.write(orgUnit.roleIds)
                attributesJson = JsonColumns.write(orgUnit.attributes)
                createdAt = orgUnit.createdAt
                updatedAt = orgUnit.updatedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_role",
    indexes = [
        Index(name = "idx_iam_role_app_code", columnList = "app_id,role_code", unique = true),
        Index(name = "idx_iam_role_type", columnList = "role_type")
    ]
)
open class IamRoleEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "app_id", length = 128)
    open var appId: String? = null

    @Column(name = "role_code", nullable = false, length = 128)
    open var roleCode: String = ""

    @Column(name = "role_name", nullable = false, length = 128)
    open var roleName: String = ""

    @Column(name = "role_type", nullable = false, length = 32)
    open var roleType: String = RoleType.PLATFORM.code

    @Column(length = 512)
    open var description: String? = null

    @Column(nullable = false)
    open var enabled: Boolean = true

    @Column(name = "permission_ids_json", nullable = false, columnDefinition = "text")
    open var permissionIdsJson: String = "[]"

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): Role {
        return Role.restore(
            id = id,
            appId = appId,
            roleCode = roleCode,
            roleName = roleName,
            roleType = enumByCode(roleType, RoleType.entries) { it.code },
            description = description,
            enabled = enabled,
            permissionIds = JsonColumns.readSet(permissionIdsJson),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(role: Role): IamRoleEntity {
            return IamRoleEntity().apply {
                id = role.id
                appId = role.appId
                roleCode = role.roleCode
                roleName = role.roleName
                roleType = role.roleType.code
                description = role.description
                enabled = role.enabled
                permissionIdsJson = JsonColumns.write(role.permissionIds)
                createdAt = role.createdAt
                updatedAt = role.updatedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_resource",
    indexes = [
        Index(name = "idx_iam_resource_app_code", columnList = "app_id,resource_code", unique = true),
        Index(name = "idx_iam_resource_type", columnList = "resource_type"),
        Index(name = "idx_iam_resource_parent", columnList = "parent_id")
    ]
)
open class IamResourceEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "app_id", nullable = false, length = 128)
    open var appId: String = ""

    @Column(name = "parent_id", length = 64)
    open var parentId: String? = null

    @Column(name = "resource_code", nullable = false, length = 128)
    open var resourceCode: String = ""

    @Column(name = "resource_name", nullable = false, length = 128)
    open var resourceName: String = ""

    @Column(name = "resource_type", nullable = false, length = 32)
    open var resourceType: String = ResourceType.MENU.code

    @Column(length = 512)
    open var path: String? = null

    @Column(length = 16)
    open var method: String? = null

    @Column(name = "sort_order", nullable = false)
    open var sortOrder: Int = 0

    @Column(nullable = false)
    open var enabled: Boolean = true

    @Column(name = "attributes_json", nullable = false, columnDefinition = "text")
    open var attributesJson: String = "{}"

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): Resource {
        return Resource.restore(
            id = id,
            appId = appId,
            parentId = parentId,
            resourceCode = resourceCode,
            resourceName = resourceName,
            resourceType = enumByCode(resourceType, ResourceType.entries) { it.code },
            path = path,
            method = method,
            sortOrder = sortOrder,
            enabled = enabled,
            attributes = JsonColumns.readAttributes(attributesJson),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(resource: Resource): IamResourceEntity {
            return IamResourceEntity().apply {
                id = resource.id
                appId = resource.appId
                parentId = resource.parentId
                resourceCode = resource.resourceCode
                resourceName = resource.resourceName
                resourceType = resource.resourceType.code
                path = resource.path
                method = resource.method
                sortOrder = resource.sortOrder
                enabled = resource.enabled
                attributesJson = JsonColumns.write(resource.attributes)
                createdAt = resource.createdAt
                updatedAt = resource.updatedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_permission",
    indexes = [
        Index(name = "idx_iam_permission_app_code", columnList = "app_id,permission_code", unique = true),
        Index(name = "idx_iam_permission_resource", columnList = "resource_id")
    ]
)
open class IamPermissionEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "app_id", nullable = false, length = 128)
    open var appId: String = ""

    @Column(name = "resource_id", length = 64)
    open var resourceId: String? = null

    @Column(name = "permission_code", nullable = false, length = 128)
    open var permissionCode: String = ""

    @Column(name = "permission_name", nullable = false, length = 128)
    open var permissionName: String = ""

    @Column(nullable = false, length = 128)
    open var action: String = ""

    @Column(length = 512)
    open var description: String? = null

    @Column(nullable = false)
    open var enabled: Boolean = true

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): Permission {
        return Permission.restore(
            id = id,
            appId = appId,
            resourceId = resourceId,
            permissionCode = permissionCode,
            permissionName = permissionName,
            action = action,
            description = description,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(permission: Permission): IamPermissionEntity {
            return IamPermissionEntity().apply {
                id = permission.id
                appId = permission.appId
                resourceId = permission.resourceId
                permissionCode = permission.permissionCode
                permissionName = permission.permissionName
                action = permission.action
                description = permission.description
                enabled = permission.enabled
                createdAt = permission.createdAt
                updatedAt = permission.updatedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_client_app",
    indexes = [
        Index(name = "idx_iam_client_app_client_id", columnList = "client_id", unique = true),
        Index(name = "idx_iam_client_app_status", columnList = "status")
    ]
)
open class IamClientAppEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "client_id", nullable = false, length = 128)
    open var clientId: String = ""

    @Column(nullable = false, length = 128)
    open var name: String = ""

    @Column(name = "app_type", nullable = false, length = 64)
    open var appType: String = ""

    @Column(name = "owner_dept", length = 128)
    open var ownerDept: String? = null

    @Column(name = "redirect_uris_json", nullable = false, columnDefinition = "text")
    open var redirectUrisJson: String = "[]"

    @Column(name = "logout_uris_json", nullable = false, columnDefinition = "text")
    open var logoutUrisJson: String = "[]"

    @Column(name = "access_token_ttl_seconds", nullable = false)
    open var accessTokenTtlSeconds: Long = 900

    @Column(name = "refresh_token_idle_seconds", nullable = false)
    open var refreshTokenIdleSeconds: Long = 7200

    @Column(name = "refresh_token_max_seconds", nullable = false)
    open var refreshTokenMaxSeconds: Long = 43200

    @Column(name = "sso_session_idle_seconds", nullable = false)
    open var ssoSessionIdleSeconds: Long = 7200

    @Column(name = "sso_session_max_seconds", nullable = false)
    open var ssoSessionMaxSeconds: Long = 43200

    @Column(nullable = false, length = 32)
    open var status: String = AppStatus.ACTIVE.code

    @Column(name = "secret_version", nullable = false)
    open var secretVersion: Int = 1

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): ClientApp {
        return ClientApp.restore(
            id = id,
            clientId = clientId,
            name = name,
            appType = appType,
            ownerDept = ownerDept,
            redirectUris = JsonColumns.readSet(redirectUrisJson),
            logoutUris = JsonColumns.readSet(logoutUrisJson),
            tokenPolicy = TokenPolicy(
                accessTokenTtlSeconds = accessTokenTtlSeconds,
                refreshTokenIdleSeconds = refreshTokenIdleSeconds,
                refreshTokenMaxSeconds = refreshTokenMaxSeconds,
                ssoSessionIdleSeconds = ssoSessionIdleSeconds,
                ssoSessionMaxSeconds = ssoSessionMaxSeconds
            ),
            status = enumByCode(status, AppStatus.entries) { it.code },
            secretVersion = secretVersion,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(app: ClientApp): IamClientAppEntity {
            return IamClientAppEntity().apply {
                id = app.id
                clientId = app.clientId
                name = app.name
                appType = app.appType
                ownerDept = app.ownerDept
                redirectUrisJson = JsonColumns.write(app.redirectUris)
                logoutUrisJson = JsonColumns.write(app.logoutUris)
                accessTokenTtlSeconds = app.tokenPolicy.accessTokenTtlSeconds
                refreshTokenIdleSeconds = app.tokenPolicy.refreshTokenIdleSeconds
                refreshTokenMaxSeconds = app.tokenPolicy.refreshTokenMaxSeconds
                ssoSessionIdleSeconds = app.tokenPolicy.ssoSessionIdleSeconds
                ssoSessionMaxSeconds = app.tokenPolicy.ssoSessionMaxSeconds
                status = app.status.code
                secretVersion = app.secretVersion
                createdAt = app.createdAt
                updatedAt = app.updatedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_session",
    indexes = [
        Index(name = "idx_iam_session_user", columnList = "user_id"),
        Index(name = "idx_iam_session_client", columnList = "client_id"),
        Index(name = "idx_iam_session_status", columnList = "status")
    ]
)
open class IamSessionEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "user_id", nullable = false, length = 64)
    open var userId: String = ""

    @Column(name = "client_id", nullable = false, length = 128)
    open var clientId: String = ""

    @Column(name = "device_id", length = 128)
    open var deviceId: String? = null

    @Column(length = 64)
    open var ip: String? = null

    @Column(name = "user_agent", length = 512)
    open var userAgent: String? = null

    @Column(nullable = false, length = 32)
    open var status: String = SessionStatus.ACTIVE.code

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "last_active_at", nullable = false)
    open var lastActiveAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "expires_at", nullable = false)
    open var expiresAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "revoked_at")
    open var revokedAt: OffsetDateTime? = null

    @Column(name = "revoked_reason", length = 512)
    open var revokedReason: String? = null

    fun toDomain(): LoginSession {
        return LoginSession.restore(
            id = id,
            userId = userId,
            clientId = clientId,
            deviceId = deviceId,
            ip = ip,
            userAgent = userAgent,
            status = enumByCode(status, SessionStatus.entries) { it.code },
            createdAt = createdAt,
            lastActiveAt = lastActiveAt,
            expiresAt = expiresAt,
            revokedAt = revokedAt,
            revokedReason = revokedReason
        )
    }

    companion object {
        fun fromDomain(session: LoginSession): IamSessionEntity {
            return IamSessionEntity().apply {
                id = session.id
                userId = session.userId
                clientId = session.clientId
                deviceId = session.deviceId
                ip = session.ip
                userAgent = session.userAgent
                status = session.status.code
                createdAt = session.createdAt
                lastActiveAt = session.lastActiveAt
                expiresAt = session.expiresAt
                revokedAt = session.revokedAt
                revokedReason = session.revokedReason
            }
        }
    }
}

@Entity
@Table(
    name = "iam_sso_session",
    indexes = [
        Index(name = "idx_iam_sso_session_user", columnList = "user_id"),
        Index(name = "idx_iam_sso_session_status", columnList = "status")
    ]
)
open class IamSsoSessionEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "user_id", nullable = false, length = 64)
    open var userId: String = ""

    @Column(nullable = false, length = 32)
    open var status: String = SessionStatus.ACTIVE.code

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "last_active_at", nullable = false)
    open var lastActiveAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "expires_at", nullable = false)
    open var expiresAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): SsoSession {
        return SsoSession.restore(
            id = id,
            userId = userId,
            status = enumByCode(status, SessionStatus.entries) { it.code },
            createdAt = createdAt,
            lastActiveAt = lastActiveAt,
            expiresAt = expiresAt
        )
    }

    companion object {
        fun fromDomain(session: SsoSession): IamSsoSessionEntity {
            return IamSsoSessionEntity().apply {
                id = session.id
                userId = session.userId
                status = session.status.code
                createdAt = session.createdAt
                lastActiveAt = session.lastActiveAt
                expiresAt = session.expiresAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_authorization_code",
    indexes = [
        Index(name = "idx_iam_authorization_code_user", columnList = "user_id"),
        Index(name = "idx_iam_authorization_code_client", columnList = "client_id")
    ]
)
open class IamAuthorizationCodeEntity {
    @Id
    @Column(length = 128)
    open var code: String = ""

    @Column(name = "user_id", nullable = false, length = 64)
    open var userId: String = ""

    @Column(name = "client_id", nullable = false, length = 128)
    open var clientId: String = ""

    @Column(name = "redirect_uri", nullable = false, length = 1024)
    open var redirectUri: String = ""

    @Column(length = 512)
    open var scope: String? = null

    @Column(nullable = false)
    open var used: Boolean = false

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "expires_at", nullable = false)
    open var expiresAt: OffsetDateTime = OffsetDateTime.now()

    @Column(name = "used_at")
    open var usedAt: OffsetDateTime? = null

    fun toDomain(): AuthorizationCode {
        return AuthorizationCode.restore(
            code = code,
            userId = userId,
            clientId = clientId,
            redirectUri = redirectUri,
            scope = scope,
            used = used,
            createdAt = createdAt,
            expiresAt = expiresAt,
            usedAt = usedAt
        )
    }

    companion object {
        fun fromDomain(code: AuthorizationCode): IamAuthorizationCodeEntity {
            return IamAuthorizationCodeEntity().apply {
                this.code = code.code
                userId = code.userId
                clientId = code.clientId
                redirectUri = code.redirectUri
                scope = code.scope
                used = code.used
                createdAt = code.createdAt
                expiresAt = code.expiresAt
                usedAt = code.usedAt
            }
        }
    }
}

@Entity
@Table(
    name = "iam_audit_event",
    indexes = [
        Index(name = "idx_iam_audit_category_time", columnList = "event_category,created_at"),
        Index(name = "idx_iam_audit_actor", columnList = "actor_id"),
        Index(name = "idx_iam_audit_target", columnList = "target_id"),
        Index(name = "idx_iam_audit_result", columnList = "result")
    ]
)
open class IamAuditEventEntity {
    @Id
    @Column(length = 64)
    open var id: String = ""

    @Column(name = "event_category", nullable = false, length = 64)
    open var eventCategory: String = ""

    @Column(name = "event_type", nullable = false, length = 128)
    open var eventType: String = ""

    @Column(name = "actor_id", length = 128)
    open var actorId: String? = null

    @Column(name = "target_id", length = 128)
    open var targetId: String? = null

    @Column(name = "client_id", length = 128)
    open var clientId: String? = null

    @Column(length = 64)
    open var ip: String? = null

    @Column(name = "user_agent", length = 512)
    open var userAgent: String? = null

    @Column(nullable = false, length = 32)
    open var result: String = "success"

    @Column(length = 512)
    open var reason: String? = null

    @Column(name = "trace_id", length = 64)
    open var traceId: String? = null

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    open var payloadJson: String = "{}"

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now()

    fun toDomain(): AuditEvent {
        return AuditEvent(
            id = id,
            eventCategory = eventCategory,
            eventType = eventType,
            actorId = actorId,
            targetId = targetId,
            clientId = clientId,
            ip = ip,
            userAgent = userAgent,
            result = result,
            reason = reason,
            traceId = traceId,
            payload = JsonColumns.readAttributes(payloadJson),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(event: AuditEvent): IamAuditEventEntity {
            return IamAuditEventEntity().apply {
                id = event.id
                eventCategory = event.eventCategory
                eventType = event.eventType
                actorId = event.actorId
                targetId = event.targetId
                clientId = event.clientId
                ip = event.ip
                userAgent = event.userAgent
                result = event.result
                reason = event.reason
                traceId = event.traceId
                payloadJson = JsonColumns.write(event.payload)
                createdAt = event.createdAt
            }
        }
    }
}

private fun <E : Enum<E>> enumByCode(code: String, entries: Iterable<E>, codeOf: (E) -> String): E {
    return entries.firstOrNull { codeOf(it) == code }
        ?: throw IllegalArgumentException("Unknown enum code: $code")
}
