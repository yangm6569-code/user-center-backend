package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.PermissionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PermissionJpaRepository : JpaRepository<PermissionEntity, Long> {
    fun findByRole_IdIn(roleIds: Collection<Long>): List<PermissionEntity>
}
