package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.RoleEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RoleJpaRepository : JpaRepository<RoleEntity, Long> {
    fun findByCodeAndApplicationIsNull(code: String): RoleEntity?

    fun findByCodeAndApplication_Id(code: String, applicationId: Long): RoleEntity?

    fun existsByCodeAndApplicationIsNull(code: String): Boolean

    fun existsByCodeAndApplication_Id(code: String, applicationId: Long): Boolean

    fun findAllByOrderByCodeAsc(): List<RoleEntity>

    @Query(
        """
        select distinct r from RoleEntity r
        left join fetch r.application
        where r.id in :ids
        """,
    )
    fun findAllWithApplicationByIdIn(@Param("ids") ids: Collection<Long>): List<RoleEntity>
}
