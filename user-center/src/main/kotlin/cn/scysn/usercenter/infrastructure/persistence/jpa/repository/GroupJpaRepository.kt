package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.GroupEntity
import org.springframework.data.jpa.repository.JpaRepository

interface GroupJpaRepository : JpaRepository<GroupEntity, Long> {
    fun existsByCode(code: String): Boolean

    fun findAllByOrderByPathAsc(): List<GroupEntity>
}
