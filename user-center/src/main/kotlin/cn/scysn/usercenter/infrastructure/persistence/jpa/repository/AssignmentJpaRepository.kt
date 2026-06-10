package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.GroupRoleEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.RoleEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserGroupEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserRoleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface UserGroupJpaRepository : JpaRepository<UserGroupEntity, Long> {
    fun existsByUser_IdAndGroup_Id(userId: Long, groupId: Long): Boolean

    fun deleteByUser_IdAndGroup_Id(userId: Long, groupId: Long)

    @Query(
        """
        select ug from UserGroupEntity ug
        join fetch ug.group g
        left join fetch g.parent
        where ug.user.id = :userId
        order by g.path asc
        """,
    )
    fun findAssignmentsByUserId(@Param("userId") userId: Long): List<UserGroupEntity>

    @Query(
        """
        select distinct ug.user from UserGroupEntity ug
        where ug.group.id = :groupId
          and ug.user.deleted = false
        order by ug.user.username asc
        """,
    )
    fun findUsersByGroupId(@Param("groupId") groupId: Long): List<cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserAccountEntity>

    @Query(
        """
        select distinct ug.user from UserGroupEntity ug
        where (ug.group.path = :groupPath or ug.group.path like concat(:groupPath, '/%'))
          and ug.user.deleted = false
        order by ug.user.username asc
        """,
    )
    fun findUsersByGroupPathIncludingDescendants(@Param("groupPath") groupPath: String): List<cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserAccountEntity>
}

interface UserRoleJpaRepository : JpaRepository<UserRoleEntity, Long> {
    fun existsByUser_IdAndRole_Id(userId: Long, roleId: Long): Boolean

    fun deleteByUser_IdAndRole_Id(userId: Long, roleId: Long)

    @Query(
        """
        select ur from UserRoleEntity ur
        join fetch ur.role r
        left join fetch r.application
        where ur.user.id = :userId
          and (ur.expiresAt is null or ur.expiresAt > :now)
        order by r.code asc
        """,
    )
    fun findActiveRoleAssignmentsByUserId(@Param("userId") userId: Long, @Param("now") now: Instant): List<UserRoleEntity>

    @Query(
        """
        select ur.role from UserRoleEntity ur
        where ur.user.id = :userId
          and (ur.expiresAt is null or ur.expiresAt > :now)
        """,
    )
    fun findActiveRolesByUserId(@Param("userId") userId: Long, @Param("now") now: Instant): List<RoleEntity>
}

interface GroupRoleJpaRepository : JpaRepository<GroupRoleEntity, Long> {
    fun existsByGroup_IdAndRole_Id(groupId: Long, roleId: Long): Boolean

    fun findAllByGroup_Id(groupId: Long): List<GroupRoleEntity>

    fun findAllByGroup_PathIn(paths: Collection<String>): List<GroupRoleEntity>

    fun deleteByGroup_IdAndRole_Id(groupId: Long, roleId: Long)

    @Query(
        """
        select distinct gr.role from GroupRoleEntity gr
        where exists (
            select 1 from UserGroupEntity ug
            where ug.user.id = :userId
              and (
                ug.group.path = gr.group.path
                or ug.group.path like concat(gr.group.path, '/%')
              )
        )
        """,
    )
    fun findInheritedRolesByUserId(@Param("userId") userId: Long): List<RoleEntity>

    @Query(
        """
        select distinct gr from GroupRoleEntity gr
        join fetch gr.group g
        join fetch gr.role r
        left join fetch r.application
        where exists (
            select 1 from UserGroupEntity ug
            where ug.user.id = :userId
              and (
                ug.group.path = g.path
                or ug.group.path like concat(g.path, '/%')
              )
        )
        order by g.path asc, r.code asc
        """,
    )
    fun findInheritedRoleAssignmentsByUserId(@Param("userId") userId: Long): List<GroupRoleEntity>
}
