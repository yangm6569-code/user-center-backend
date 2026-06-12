package cn.scysn.iam.infrastructure.persistence.jpa.repository

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
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime

interface IamUserJpaRepository : JpaRepository<IamUserEntity, String> {
    fun findByUsername(username: String): IamUserEntity?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
    fun existsByRoleGrantsJsonContaining(roleId: String): Boolean

    @Query(
        """
        select u from IamUserEntity u
        where (:status is null or u.status = :status)
        """
    )
    fun searchWithoutKeyword(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<IamUserEntity>

    @Query(
        """
        select u from IamUserEntity u
        left join IamUserProfileEntity p on p.userId = u.id
        where (:status is null or u.status = :status)
          and p.orgUnitIdsJson like concat('%', :orgUnitId, '%')
        """
    )
    fun searchWithoutKeywordByOrgUnit(
        @Param("status") status: String?,
        @Param("orgUnitId") orgUnitId: String,
        pageable: Pageable,
    ): Page<IamUserEntity>

    @Query(
        """
        select u from IamUserEntity u
        left join IamUserProfileEntity p on p.userId = u.id
        where (:status is null or u.status = :status)
          and (
            lower(u.username) like lower(concat('%', :keyword, '%'))
            or lower(u.displayName) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.phone, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(p.employeeNo, '')) like lower(concat('%', :keyword, '%'))
          )
        """
    )
    fun searchWithKeyword(
        @Param("keyword") keyword: String?,
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<IamUserEntity>

    @Query(
        """
        select u from IamUserEntity u
        left join IamUserProfileEntity p on p.userId = u.id
        where (:status is null or u.status = :status)
          and p.orgUnitIdsJson like concat('%', :orgUnitId, '%')
          and (
            lower(u.username) like lower(concat('%', :keyword, '%'))
            or lower(u.displayName) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(u.phone, '')) like lower(concat('%', :keyword, '%'))
            or lower(coalesce(p.employeeNo, '')) like lower(concat('%', :keyword, '%'))
          )
        """
    )
    fun searchWithKeywordByOrgUnit(
        @Param("keyword") keyword: String?,
        @Param("status") status: String?,
        @Param("orgUnitId") orgUnitId: String,
        pageable: Pageable,
    ): Page<IamUserEntity>
}

interface IamUserCredentialJpaRepository : JpaRepository<IamUserCredentialEntity, String>

interface IamUserProfileJpaRepository : JpaRepository<IamUserProfileEntity, String> {
    fun existsByEmployeeNo(employeeNo: String): Boolean
}

interface IamOrgUnitJpaRepository : JpaRepository<IamOrgUnitEntity, String> {
    fun existsByCode(code: String): Boolean
    fun existsByParentId(parentId: String): Boolean
    fun existsByRoleIdsJsonContaining(roleId: String): Boolean
    fun findAllByOrderBySortOrderAscNameAsc(): List<IamOrgUnitEntity>
}

interface IamRoleJpaRepository : JpaRepository<IamRoleEntity, String> {
    fun existsByAppIdAndRoleCode(appId: String?, roleCode: String): Boolean

    @Query(
        """
        select r from IamRoleEntity r
        where (:appId is null or r.appId = :appId)
          and (:roleType is null or r.roleType = :roleType)
        """
    )
    fun searchWithoutKeyword(
        @Param("appId") appId: String?,
        @Param("roleType") roleType: String?,
        pageable: Pageable,
    ): Page<IamRoleEntity>

    @Query(
        """
        select r from IamRoleEntity r
        where (:appId is null or r.appId = :appId)
          and (:roleType is null or r.roleType = :roleType)
          and (
            lower(r.roleCode) like lower(concat('%', :keyword, '%'))
            or lower(r.roleName) like lower(concat('%', :keyword, '%'))
          )
        """
    )
    fun searchWithKeyword(
        @Param("appId") appId: String?,
        @Param("roleType") roleType: String?,
        @Param("keyword") keyword: String?,
        pageable: Pageable,
    ): Page<IamRoleEntity>
}

interface IamResourceJpaRepository : JpaRepository<IamResourceEntity, String> {
    fun existsByAppIdAndResourceCode(appId: String, resourceCode: String): Boolean

    @Query(
        """
        select r from IamResourceEntity r
        where (:appId is null or r.appId = :appId)
          and (:resourceType is null or r.resourceType = :resourceType)
        order by r.sortOrder asc, r.resourceName asc
        """
    )
    fun searchWithoutKeyword(
        @Param("appId") appId: String?,
        @Param("resourceType") resourceType: String?,
    ): List<IamResourceEntity>

    @Query(
        """
        select r from IamResourceEntity r
        where (:appId is null or r.appId = :appId)
          and (:resourceType is null or r.resourceType = :resourceType)
          and (
            lower(r.resourceCode) like lower(concat('%', :keyword, '%'))
            or lower(r.resourceName) like lower(concat('%', :keyword, '%'))
          )
        order by r.sortOrder asc, r.resourceName asc
        """
    )
    fun searchWithKeyword(
        @Param("appId") appId: String?,
        @Param("resourceType") resourceType: String?,
        @Param("keyword") keyword: String?,
    ): List<IamResourceEntity>
}

interface IamPermissionJpaRepository : JpaRepository<IamPermissionEntity, String> {
    fun existsByAppIdAndPermissionCode(appId: String, permissionCode: String): Boolean

    @Query(
        """
        select p from IamPermissionEntity p
        where (:appId is null or p.appId = :appId)
          and (:resourceId is null or p.resourceId = :resourceId)
        order by p.permissionCode asc
        """
    )
    fun searchWithoutKeyword(
        @Param("appId") appId: String?,
        @Param("resourceId") resourceId: String?,
    ): List<IamPermissionEntity>

    @Query(
        """
        select p from IamPermissionEntity p
        where (:appId is null or p.appId = :appId)
          and (:resourceId is null or p.resourceId = :resourceId)
          and (
            lower(p.permissionCode) like lower(concat('%', :keyword, '%'))
            or lower(p.permissionName) like lower(concat('%', :keyword, '%'))
            or lower(p.action) like lower(concat('%', :keyword, '%'))
          )
        order by p.permissionCode asc
        """
    )
    fun searchWithKeyword(
        @Param("appId") appId: String?,
        @Param("resourceId") resourceId: String?,
        @Param("keyword") keyword: String?,
    ): List<IamPermissionEntity>
}

interface IamClientAppJpaRepository : JpaRepository<IamClientAppEntity, String> {
    fun findByClientId(clientId: String): IamClientAppEntity?
    fun existsByClientId(clientId: String): Boolean

    @Query(
        """
        select a from IamClientAppEntity a
        where (:status is null or a.status = :status)
        """
    )
    fun searchWithoutKeyword(
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<IamClientAppEntity>

    @Query(
        """
        select a from IamClientAppEntity a
        where (:status is null or a.status = :status)
          and (
            lower(a.clientId) like lower(concat('%', :keyword, '%'))
            or lower(a.name) like lower(concat('%', :keyword, '%'))
          )
        """
    )
    fun searchWithKeyword(
        @Param("keyword") keyword: String?,
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<IamClientAppEntity>
}

interface IamSessionJpaRepository : JpaRepository<IamSessionEntity, String> {
    fun findByUserIdAndStatus(userId: String, status: String): List<IamSessionEntity>

    @Query(
        """
        select s from IamSessionEntity s
        where (:userId is null or s.userId = :userId)
          and (:clientId is null or s.clientId = :clientId)
          and (:status is null or s.status = :status)
        """
    )
    fun search(
        @Param("userId") userId: String?,
        @Param("clientId") clientId: String?,
        @Param("status") status: String?,
        pageable: Pageable,
    ): Page<IamSessionEntity>
}

interface IamSsoSessionJpaRepository : JpaRepository<IamSsoSessionEntity, String> {
    fun findByUserIdAndStatus(userId: String, status: String): List<IamSsoSessionEntity>
}

interface IamAuthorizationCodeJpaRepository : JpaRepository<IamAuthorizationCodeEntity, String>

interface IamAuditEventJpaRepository : JpaRepository<IamAuditEventEntity, String> {
    @Query(
        """
        select e from IamAuditEventEntity e
        where e.eventCategory = :eventCategory
          and (:actorId is null or e.actorId = :actorId)
          and (:targetId is null or e.targetId = :targetId)
          and (:userId is null or e.actorId = :userId or e.targetId = :userId)
          and (:eventType is null or e.eventType = :eventType)
          and (:result is null or e.result = :result)
          and (:riskType is null or e.eventType = :riskType)
          and (:startTime is null or e.createdAt >= :startTime)
          and (:endTime is null or e.createdAt <= :endTime)
        """
    )
    fun search(
        @Param("eventCategory") eventCategory: String,
        @Param("actorId") actorId: String?,
        @Param("targetId") targetId: String?,
        @Param("userId") userId: String?,
        @Param("eventType") eventType: String?,
        @Param("result") result: String?,
        @Param("riskType") riskType: String?,
        @Param("startTime") startTime: OffsetDateTime?,
        @Param("endTime") endTime: OffsetDateTime?,
        pageable: Pageable,
    ): Page<IamAuditEventEntity>
}
