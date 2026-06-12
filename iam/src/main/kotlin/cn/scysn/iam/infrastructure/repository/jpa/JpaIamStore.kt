package cn.scysn.iam.infrastructure.repository.jpa

import cn.scysn.common.api.PageResponse
import cn.scysn.common.error.ErrorCode
import cn.scysn.common.error.businessError
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.AuthorizationCode
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.auth.SessionStatus
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
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamAuditEventEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamAuthorizationCodeEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamClientAppEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamOrgUnitEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamPermissionEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamResourceEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamRoleEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamSessionEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamSsoSessionEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamUserCredentialEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamUserEntity
import cn.scysn.iam.infrastructure.persistence.jpa.entity.IamUserProfileEntity
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamAuditEventJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamAuthorizationCodeJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamClientAppJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamOrgUnitJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamPermissionJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamResourceJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamRoleJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamSessionJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamSsoSessionJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamUserCredentialJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamUserJpaRepository
import cn.scysn.iam.infrastructure.persistence.jpa.repository.IamUserProfileJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class JpaIamStore(
    private val userJpaRepository: IamUserJpaRepository,
    private val userCredentialJpaRepository: IamUserCredentialJpaRepository,
    private val userProfileJpaRepository: IamUserProfileJpaRepository,
    private val orgUnitJpaRepository: IamOrgUnitJpaRepository,
    private val roleJpaRepository: IamRoleJpaRepository,
    private val resourceJpaRepository: IamResourceJpaRepository,
    private val permissionJpaRepository: IamPermissionJpaRepository,
    private val clientAppJpaRepository: IamClientAppJpaRepository,
    private val sessionJpaRepository: IamSessionJpaRepository,
    private val ssoSessionJpaRepository: IamSsoSessionJpaRepository,
    private val authorizationCodeJpaRepository: IamAuthorizationCodeJpaRepository,
    private val auditEventJpaRepository: IamAuditEventJpaRepository,
) :
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

    override fun save(user: User): User {
        userJpaRepository.save(IamUserEntity.fromDomain(user))
        userCredentialJpaRepository.save(IamUserCredentialEntity.fromDomain(user))
        userProfileJpaRepository.save(IamUserProfileEntity.fromDomain(user))
        return user
    }

    @Transactional(readOnly = true)
    override fun findUserById(id: String): User? {
        return userJpaRepository.findById(id).orElse(null)?.toDomainOrNull()
    }

    @Transactional(readOnly = true)
    override fun findByUsername(username: String): User? {
        return userJpaRepository.findByUsername(username)?.toDomainOrNull()
    }

    @Transactional(readOnly = true)
    override fun existsByUsername(username: String): Boolean = userJpaRepository.existsByUsername(username)

    @Transactional(readOnly = true)
    override fun existsByEmployeeNo(employeeNo: String): Boolean = userProfileJpaRepository.existsByEmployeeNo(employeeNo)

    @Transactional(readOnly = true)
    override fun existsByEmail(email: String): Boolean = userJpaRepository.existsByEmail(email)

    @Transactional(readOnly = true)
    override fun existsByPhone(phone: String): Boolean = userJpaRepository.existsByPhone(phone)

    @Transactional(readOnly = true)
    override fun search(query: UserSearchQuery): PageResponse<User> {
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val status = query.status?.takeIf { it.isNotBlank() }
        val orgUnitId = query.orgUnitId?.takeIf { it.isNotBlank() }
        val pageable = pageRequest(query.page, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = when {
            keyword == null && orgUnitId == null -> userJpaRepository.searchWithoutKeyword(status = status, pageable = pageable)
            keyword == null -> userJpaRepository.searchWithoutKeywordByOrgUnit(status = status, orgUnitId = orgUnitId!!, pageable = pageable)
            orgUnitId == null -> userJpaRepository.searchWithKeyword(keyword = keyword!!, status = status, pageable = pageable)
            else -> userJpaRepository.searchWithKeywordByOrgUnit(
                keyword = keyword!!,
                status = status,
                orgUnitId = orgUnitId!!,
                pageable = pageable
            )
        }
        return PageResponse.of(page.content.mapNotNull { it.toDomainOrNull() }, query.page.page, query.page.pageSize, page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun findUsersByIds(ids: Set<String>): List<User> {
        return ids.mapNotNull { findUserById(it) }
    }

    override fun save(orgUnit: OrgUnit): OrgUnit {
        orgUnitJpaRepository.save(IamOrgUnitEntity.fromDomain(orgUnit))
        return orgUnit
    }

    @Transactional(readOnly = true)
    override fun findOrgUnitById(id: String): OrgUnit? = orgUnitJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findAll(): List<OrgUnit> = orgUnitJpaRepository.findAllByOrderBySortOrderAscNameAsc().map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun existsOrgCode(code: String): Boolean = orgUnitJpaRepository.existsByCode(code)

    @Transactional(readOnly = true)
    override fun hasChildren(id: String): Boolean = orgUnitJpaRepository.existsByParentId(id)

    @Transactional(readOnly = true)
    override fun hasRole(id: String): Boolean = orgUnitJpaRepository.existsByRoleIdsJsonContaining(id)

    override fun deleteOrgUnit(id: String) {
        orgUnitJpaRepository.deleteById(id)
    }

    override fun save(role: Role): Role {
        roleJpaRepository.save(IamRoleEntity.fromDomain(role))
        return role
    }

    @Transactional(readOnly = true)
    override fun findRoleById(id: String): Role? = roleJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun search(query: RoleSearchQuery): PageResponse<Role> {
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val appId = query.appId?.takeIf { it.isNotBlank() }
        val roleType = query.roleType?.takeIf { it.isNotBlank() }
        val pageable = pageRequest(query.page, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = if (keyword == null) {
            roleJpaRepository.searchWithoutKeyword(appId = appId, roleType = roleType, pageable = pageable)
        } else {
            roleJpaRepository.searchWithKeyword(appId = appId, roleType = roleType, keyword = keyword, pageable = pageable)
        }
        return PageResponse.of(page.content.map { it.toDomain() }, query.page.page, query.page.pageSize, page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun existsRoleCode(appId: String?, roleCode: String): Boolean {
        return roleJpaRepository.existsByAppIdAndRoleCode(appId, roleCode)
    }

    override fun deleteRole(id: String) {
        if (userJpaRepository.existsByRoleGrantsJsonContaining(id)) {
            businessError(ErrorCode.ROLE_IN_USE)
        }
        if (orgUnitJpaRepository.existsByRoleIdsJsonContaining(id)) {
            businessError(ErrorCode.ROLE_IN_USE)
        }
        roleJpaRepository.deleteById(id)
    }

    override fun save(resource: Resource): Resource {
        resourceJpaRepository.save(IamResourceEntity.fromDomain(resource))
        return resource
    }

    @Transactional(readOnly = true)
    override fun findResourceById(id: String): Resource? = resourceJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun search(query: ResourceSearchQuery): List<Resource> {
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val appId = query.appId?.takeIf { it.isNotBlank() }
        val resourceType = query.resourceType?.takeIf { it.isNotBlank() }
        val resources = if (keyword == null) {
            resourceJpaRepository.searchWithoutKeyword(appId = appId, resourceType = resourceType)
        } else {
            resourceJpaRepository.searchWithKeyword(appId = appId, resourceType = resourceType, keyword = keyword)
        }
        return resources.map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun existsResourceCode(appId: String, resourceCode: String): Boolean {
        return resourceJpaRepository.existsByAppIdAndResourceCode(appId, resourceCode)
    }

    override fun save(permission: Permission): Permission {
        permissionJpaRepository.save(IamPermissionEntity.fromDomain(permission))
        return permission
    }

    @Transactional(readOnly = true)
    override fun findPermissionById(id: String): Permission? = permissionJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findPermissionsByIds(ids: Set<String>): List<Permission> {
        return permissionJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun search(query: PermissionSearchQuery): List<Permission> {
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val appId = query.appId?.takeIf { it.isNotBlank() }
        val resourceId = query.resourceId?.takeIf { it.isNotBlank() }
        val permissions = if (keyword == null) {
            permissionJpaRepository.searchWithoutKeyword(appId = appId, resourceId = resourceId)
        } else {
            permissionJpaRepository.searchWithKeyword(appId = appId, resourceId = resourceId, keyword = keyword)
        }
        return permissions.map { it.toDomain() }
    }

    @Transactional(readOnly = true)
    override fun existsPermissionCode(appId: String, permissionCode: String): Boolean {
        return permissionJpaRepository.existsByAppIdAndPermissionCode(appId, permissionCode)
    }

    override fun save(app: ClientApp): ClientApp {
        clientAppJpaRepository.save(IamClientAppEntity.fromDomain(app))
        return app
    }

    @Transactional(readOnly = true)
    override fun findAppById(id: String): ClientApp? = clientAppJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findByClientId(clientId: String): ClientApp? = clientAppJpaRepository.findByClientId(clientId)?.toDomain()

    @Transactional(readOnly = true)
    override fun search(query: AppSearchQuery): PageResponse<ClientApp> {
        val keyword = query.keyword?.takeIf { it.isNotBlank() }
        val status = query.status?.takeIf { it.isNotBlank() }
        val pageable = pageRequest(query.page, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = if (keyword == null) {
            clientAppJpaRepository.searchWithoutKeyword(status = status, pageable = pageable)
        } else {
            clientAppJpaRepository.searchWithKeyword(keyword = keyword, status = status, pageable = pageable)
        }
        return PageResponse.of(page.content.map { it.toDomain() }, query.page.page, query.page.pageSize, page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun existsByClientId(clientId: String): Boolean = clientAppJpaRepository.existsByClientId(clientId)

    override fun save(session: LoginSession): LoginSession {
        sessionJpaRepository.save(IamSessionEntity.fromDomain(session))
        return session
    }

    @Transactional(readOnly = true)
    override fun findSessionById(id: String): LoginSession? = sessionJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun search(query: SessionSearchQuery): PageResponse<LoginSession> {
        val page = sessionJpaRepository.search(
            userId = query.userId?.takeIf { it.isNotBlank() },
            clientId = query.clientId?.takeIf { it.isNotBlank() },
            status = query.status?.takeIf { it.isNotBlank() },
            pageable = pageRequest(query.page, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return PageResponse.of(page.content.map { it.toDomain() }, query.page.page, query.page.pageSize, page.totalElements)
    }

    @Transactional(readOnly = true)
    override fun findActiveByUserId(userId: String): List<LoginSession> {
        return sessionJpaRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE.code).map { it.toDomain() }
    }

    override fun save(session: SsoSession): SsoSession {
        ssoSessionJpaRepository.save(IamSsoSessionEntity.fromDomain(session))
        return session
    }

    @Transactional(readOnly = true)
    override fun findSsoSessionById(id: String): SsoSession? = ssoSessionJpaRepository.findById(id).orElse(null)?.toDomain()

    @Transactional(readOnly = true)
    override fun findActiveSsoByUserId(userId: String): List<SsoSession> {
        return ssoSessionJpaRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE.code).map { it.toDomain() }
    }

    override fun save(code: AuthorizationCode): AuthorizationCode {
        authorizationCodeJpaRepository.save(IamAuthorizationCodeEntity.fromDomain(code))
        return code
    }

    @Transactional(readOnly = true)
    override fun findAuthorizationCode(code: String): AuthorizationCode? {
        return authorizationCodeJpaRepository.findById(code).orElse(null)?.toDomain()
    }

    override fun save(event: AuditEvent): AuditEvent {
        auditEventJpaRepository.save(IamAuditEventEntity.fromDomain(event))
        return event
    }

    @Transactional(readOnly = true)
    override fun search(query: AuditSearchQuery): PageResponse<AuditEvent> {
        val page = auditEventJpaRepository.search(
            eventCategory = query.eventCategory,
            actorId = query.actorId?.takeIf { it.isNotBlank() },
            targetId = query.targetId?.takeIf { it.isNotBlank() },
            userId = query.userId?.takeIf { it.isNotBlank() },
            eventType = query.eventType?.takeIf { it.isNotBlank() },
            result = query.result?.takeIf { it.isNotBlank() },
            riskType = query.riskType?.takeIf { it.isNotBlank() },
            startTime = query.startTime,
            endTime = query.endTime,
            pageable = pageRequest(query.page, Sort.by(Sort.Direction.DESC, "createdAt"))
        )
        return PageResponse.of(page.content.map { it.toDomain() }, query.page.page, query.page.pageSize, page.totalElements)
    }

    private fun IamUserEntity.toDomainOrNull(): User? {
        val credential = userCredentialJpaRepository.findById(id).orElse(null) ?: return null
        val profile = userProfileJpaRepository.findById(id).orElse(null) ?: return null
        return toDomain(credential, profile)
    }

    private fun pageRequest(page: PageQuery, sort: Sort): PageRequest {
        return PageRequest.of(page.page - 1, page.pageSize, sort)
    }
}
