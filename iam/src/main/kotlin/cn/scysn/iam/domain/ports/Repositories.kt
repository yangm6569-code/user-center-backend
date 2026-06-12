package cn.scysn.iam.domain.ports

import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.AuthorizationCode
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.auth.SsoSession
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.organization.OrgUnit
import java.time.OffsetDateTime

data class UserSearchQuery(
    val keyword: String? = null,
    val status: String? = null,
    val orgUnitId: String? = null,
    val page: PageQuery = PageQuery(),
)

data class RoleSearchQuery(
    val appId: String? = null,
    val roleType: String? = null,
    val keyword: String? = null,
    val page: PageQuery = PageQuery(),
)

data class ResourceSearchQuery(
    val appId: String? = null,
    val resourceType: String? = null,
    val keyword: String? = null,
)

data class PermissionSearchQuery(
    val appId: String? = null,
    val resourceId: String? = null,
    val keyword: String? = null,
)

data class AppSearchQuery(
    val keyword: String? = null,
    val status: String? = null,
    val page: PageQuery = PageQuery(),
)

data class SessionSearchQuery(
    val userId: String? = null,
    val clientId: String? = null,
    val status: String? = null,
    val page: PageQuery = PageQuery(),
)

data class AuditSearchQuery(
    val eventCategory: String,
    val actorId: String? = null,
    val targetId: String? = null,
    val userId: String? = null,
    val eventType: String? = null,
    val result: String? = null,
    val riskType: String? = null,
    val startTime: OffsetDateTime? = null,
    val endTime: OffsetDateTime? = null,
    val page: PageQuery = PageQuery(),
)

interface UserRepository {
    fun save(user: User): User
    fun findUserById(id: String): User?
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmployeeNo(employeeNo: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
    fun search(query: UserSearchQuery): PageResponse<User>
    fun findUsersByIds(ids: Set<String>): List<User>
}

interface OrgUnitRepository {
    fun save(orgUnit: OrgUnit): OrgUnit
    fun findOrgUnitById(id: String): OrgUnit?
    fun findAll(): List<OrgUnit>
    fun existsOrgCode(code: String): Boolean
    fun hasChildren(id: String): Boolean
    fun hasRole(id: String): Boolean
    fun deleteOrgUnit(id: String)
}

interface RoleRepository {
    fun save(role: Role): Role
    fun findRoleById(id: String): Role?
    fun search(query: RoleSearchQuery): PageResponse<Role>
    fun existsRoleCode(appId: String?, roleCode: String): Boolean
    fun deleteRole(id: String)
}

interface ResourceRepository {
    fun save(resource: Resource): Resource
    fun findResourceById(id: String): Resource?
    fun search(query: ResourceSearchQuery): List<Resource>
    fun existsResourceCode(appId: String, resourceCode: String): Boolean
}

interface PermissionRepository {
    fun save(permission: Permission): Permission
    fun findPermissionById(id: String): Permission?
    fun findPermissionsByIds(ids: Set<String>): List<Permission>
    fun search(query: PermissionSearchQuery): List<Permission>
    fun existsPermissionCode(appId: String, permissionCode: String): Boolean
}

interface ClientAppRepository {
    fun save(app: ClientApp): ClientApp
    fun findAppById(id: String): ClientApp?
    fun findByClientId(clientId: String): ClientApp?
    fun search(query: AppSearchQuery): PageResponse<ClientApp>
    fun existsByClientId(clientId: String): Boolean
}

interface SessionRepository {
    fun save(session: LoginSession): LoginSession
    fun findSessionById(id: String): LoginSession?
    fun search(query: SessionSearchQuery): PageResponse<LoginSession>
    fun findActiveByUserId(userId: String): List<LoginSession>
}

interface SsoSessionRepository {
    fun save(session: SsoSession): SsoSession
    fun findSsoSessionById(id: String): SsoSession?
    fun findActiveSsoByUserId(userId: String): List<SsoSession>
}

interface AuthorizationCodeRepository {
    fun save(code: AuthorizationCode): AuthorizationCode
    fun findAuthorizationCode(code: String): AuthorizationCode?
}

interface AuditRepository {
    fun save(event: AuditEvent): AuditEvent
    fun search(query: AuditSearchQuery): PageResponse<AuditEvent>
}
