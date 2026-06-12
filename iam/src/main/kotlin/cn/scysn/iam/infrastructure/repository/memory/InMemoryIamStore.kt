package cn.scysn.iam.infrastructure.repository.memory

import cn.scysn.common.api.PageResponse
import cn.scysn.common.error.ErrorCode
import cn.scysn.common.error.businessError
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
import cn.scysn.iam.domain.ports.AppSearchQuery
import cn.scysn.iam.domain.ports.AuditRepository
import cn.scysn.iam.domain.ports.AuditSearchQuery
import cn.scysn.iam.domain.ports.AuthorizationCodeRepository
import cn.scysn.iam.domain.ports.ClientAppRepository
import cn.scysn.iam.domain.ports.OrgUnitRepository
import cn.scysn.iam.domain.ports.PermissionRepository
import cn.scysn.iam.domain.ports.PermissionSearchQuery
import cn.scysn.iam.domain.ports.ResourceRepository
import cn.scysn.iam.domain.ports.ResourceSearchQuery
import cn.scysn.iam.domain.ports.RoleRepository
import cn.scysn.iam.domain.ports.RoleSearchQuery
import cn.scysn.iam.domain.ports.SessionRepository
import cn.scysn.iam.domain.ports.SessionSearchQuery
import cn.scysn.iam.domain.ports.SsoSessionRepository
import cn.scysn.iam.domain.ports.UserRepository
import cn.scysn.iam.domain.ports.UserSearchQuery
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
@Profile("memory")
class InMemoryIamStore :
    UserRepository,
    OrgUnitRepository,
    RoleRepository,
    ResourceRepository,
    PermissionRepository,
    ClientAppRepository,
    SessionRepository,
    SsoSessionRepository,
    AuthorizationCodeRepository,
    AuditRepository {

    private val users = ConcurrentHashMap<String, User>()
    private val orgUnits = ConcurrentHashMap<String, OrgUnit>()
    private val roles = ConcurrentHashMap<String, Role>()
    private val resources = ConcurrentHashMap<String, Resource>()
    private val permissions = ConcurrentHashMap<String, Permission>()
    private val apps = ConcurrentHashMap<String, ClientApp>()
    private val sessions = ConcurrentHashMap<String, LoginSession>()
    private val ssoSessions = ConcurrentHashMap<String, SsoSession>()
    private val authorizationCodes = ConcurrentHashMap<String, AuthorizationCode>()
    private val auditEvents = ConcurrentHashMap<String, AuditEvent>()

    override fun save(user: User): User {
        users[user.id] = user
        return user
    }

    override fun findUserById(id: String): User? = users[id]

    override fun findByUsername(username: String): User? = users.values.firstOrNull { it.username == username }

    override fun existsByUsername(username: String): Boolean = users.values.any { it.username == username }

    override fun existsByEmployeeNo(employeeNo: String): Boolean = users.values.any { it.profile.employeeNo == employeeNo }

    override fun existsByEmail(email: String): Boolean = users.values.any { it.email == email }

    override fun existsByPhone(phone: String): Boolean = users.values.any { it.phone == phone }

    override fun search(query: UserSearchQuery): PageResponse<User> {
        val filtered = users.values
            .filter { query.status == null || it.status.code == query.status }
            .filter { query.orgUnitId == null || it.profile.orgUnitIds.contains(query.orgUnitId) }
            .filter {
                query.keyword.isNullOrBlank() ||
                    it.username.contains(query.keyword, true) ||
                    it.displayName.contains(query.keyword, true) ||
                    (it.profile.employeeNo?.contains(query.keyword, true) == true) ||
                    (it.email?.contains(query.keyword, true) == true) ||
                    (it.phone?.contains(query.keyword, true) == true)
            }
            .sortedByDescending { it.createdAt }
        return filtered.page(query.page)
    }

    override fun findUsersByIds(ids: Set<String>): List<User> = ids.mapNotNull { users[it] }

    override fun save(orgUnit: OrgUnit): OrgUnit {
        orgUnits[orgUnit.id] = orgUnit
        return orgUnit
    }

    override fun findOrgUnitById(id: String): OrgUnit? = orgUnits[id]

    override fun findAll(): List<OrgUnit> = orgUnits.values.sortedWith(compareBy<OrgUnit> { it.sortOrder }.thenBy { it.name })

    override fun existsOrgCode(code: String): Boolean = orgUnits.values.any { it.code == code }

    override fun hasChildren(id: String): Boolean = orgUnits.values.any { it.parentId == id }

    override fun hasRole(id: String): Boolean = orgUnits.values.any { it.roleIds.contains(id) }

    override fun deleteOrgUnit(id: String) {
        orgUnits.remove(id)
    }

    override fun save(role: Role): Role {
        roles[role.id] = role
        return role
    }

    override fun findRoleById(id: String): Role? = roles[id]

    override fun search(query: RoleSearchQuery): PageResponse<Role> {
        val filtered = roles.values
            .filter { query.appId == null || it.appId == query.appId }
            .filter { query.roleType == null || it.roleType.code == query.roleType }
            .filter {
                query.keyword.isNullOrBlank() ||
                    it.roleCode.contains(query.keyword, true) ||
                    it.roleName.contains(query.keyword, true)
            }
            .sortedByDescending { it.createdAt }
        return filtered.page(query.page)
    }

    override fun existsRoleCode(appId: String?, roleCode: String): Boolean {
        return roles.values.any { it.appId == appId && it.roleCode == roleCode }
    }

    override fun deleteRole(id: String) {
        val inUse = users.values.any { user -> user.roleGrants.any { it.roleId == id } }
        if (inUse || orgUnits.values.any { it.roleIds.contains(id) }) {
            businessError(ErrorCode.ROLE_IN_USE)
        }
        roles.remove(id)
    }

    override fun save(resource: Resource): Resource {
        resources[resource.id] = resource
        return resource
    }

    override fun findResourceById(id: String): Resource? = resources[id]

    override fun search(query: ResourceSearchQuery): List<Resource> {
        return resources.values
            .filter { query.appId == null || it.appId == query.appId }
            .filter { query.resourceType == null || it.resourceType.code == query.resourceType }
            .filter {
                query.keyword.isNullOrBlank() ||
                    it.resourceCode.contains(query.keyword, true) ||
                    it.resourceName.contains(query.keyword, true)
            }
            .sortedWith(compareBy<Resource> { it.sortOrder }.thenBy { it.resourceName })
    }

    override fun existsResourceCode(appId: String, resourceCode: String): Boolean {
        return resources.values.any { it.appId == appId && it.resourceCode == resourceCode }
    }

    override fun save(permission: Permission): Permission {
        permissions[permission.id] = permission
        return permission
    }

    override fun findPermissionById(id: String): Permission? = permissions[id]

    override fun findPermissionsByIds(ids: Set<String>): List<Permission> = ids.mapNotNull { permissions[it] }

    override fun search(query: PermissionSearchQuery): List<Permission> {
        return permissions.values
            .filter { query.appId == null || it.appId == query.appId }
            .filter { query.resourceId == null || it.resourceId == query.resourceId }
            .filter {
                query.keyword.isNullOrBlank() ||
                    it.permissionCode.contains(query.keyword, true) ||
                    it.permissionName.contains(query.keyword, true) ||
                    it.action.contains(query.keyword, true)
            }
            .sortedBy { it.permissionCode }
    }

    override fun existsPermissionCode(appId: String, permissionCode: String): Boolean {
        return permissions.values.any { it.appId == appId && it.permissionCode == permissionCode }
    }

    override fun save(app: ClientApp): ClientApp {
        apps[app.id] = app
        return app
    }

    override fun findAppById(id: String): ClientApp? = apps[id]

    override fun findByClientId(clientId: String): ClientApp? = apps.values.firstOrNull { it.clientId == clientId }

    override fun search(query: AppSearchQuery): PageResponse<ClientApp> {
        val filtered = apps.values
            .filter { query.status == null || it.status.code == query.status }
            .filter {
                query.keyword.isNullOrBlank() ||
                    it.clientId.contains(query.keyword, true) ||
                    it.name.contains(query.keyword, true)
            }
            .sortedByDescending { it.createdAt }
        return filtered.page(query.page)
    }

    override fun existsByClientId(clientId: String): Boolean = apps.values.any { it.clientId == clientId }

    override fun save(session: LoginSession): LoginSession {
        sessions[session.id] = session
        return session
    }

    override fun findSessionById(id: String): LoginSession? = sessions[id]

    override fun search(query: SessionSearchQuery): PageResponse<LoginSession> {
        val filtered = sessions.values
            .filter { query.userId == null || it.userId == query.userId }
            .filter { query.clientId == null || it.clientId == query.clientId }
            .filter { query.status == null || it.status.code == query.status }
            .sortedByDescending { it.createdAt }
        return filtered.page(query.page)
    }

    override fun findActiveByUserId(userId: String): List<LoginSession> {
        return sessions.values.filter { it.userId == userId && it.status.code == "active" }
    }

    override fun save(session: SsoSession): SsoSession {
        ssoSessions[session.id] = session
        return session
    }

    override fun findSsoSessionById(id: String): SsoSession? = ssoSessions[id]

    override fun findActiveSsoByUserId(userId: String): List<SsoSession> {
        return ssoSessions.values.filter { it.userId == userId && it.status.code == "active" }
    }

    override fun save(code: AuthorizationCode): AuthorizationCode {
        authorizationCodes[code.code] = code
        return code
    }

    override fun findAuthorizationCode(code: String): AuthorizationCode? = authorizationCodes[code]

    override fun save(event: AuditEvent): AuditEvent {
        auditEvents[event.id] = event
        return event
    }

    override fun search(query: AuditSearchQuery): PageResponse<AuditEvent> {
        val filtered = auditEvents.values
            .filter { it.eventCategory == query.eventCategory }
            .filter { query.actorId == null || it.actorId == query.actorId }
            .filter { query.targetId == null || it.targetId == query.targetId }
            .filter { query.userId == null || it.actorId == query.userId || it.targetId == query.userId }
            .filter { query.eventType == null || it.eventType == query.eventType }
            .filter { query.result == null || it.result == query.result }
            .filter { query.riskType == null || it.eventType == query.riskType }
            .filter { query.startTime == null || !it.createdAt.isBefore(query.startTime) }
            .filter { query.endTime == null || !it.createdAt.isAfter(query.endTime) }
            .sortedByDescending { it.createdAt }
        return filtered.page(query.page)
    }

    private fun <T> List<T>.page(pageQuery: PageQuery): PageResponse<T> {
        val from = pageQuery.offset.coerceAtMost(size)
        val to = (from + pageQuery.pageSize).coerceAtMost(size)
        return PageResponse.of(subList(from, to), pageQuery.page, pageQuery.pageSize, size.toLong())
    }
}
