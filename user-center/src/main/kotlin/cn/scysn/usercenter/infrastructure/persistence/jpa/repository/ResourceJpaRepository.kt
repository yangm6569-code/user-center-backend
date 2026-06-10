package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.ResourceEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ResourceJpaRepository : JpaRepository<ResourceEntity, Long> {
    fun existsByApplication_IdAndResourceCode(applicationId: Long, resourceCode: String): Boolean

    fun findByApplication_IdOrderByResourceCodeAsc(applicationId: Long): List<ResourceEntity>
}
