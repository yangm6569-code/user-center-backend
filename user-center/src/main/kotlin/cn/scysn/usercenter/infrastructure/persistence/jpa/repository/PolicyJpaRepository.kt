package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.PolicyEntity
import org.springframework.data.jpa.repository.JpaRepository

interface PolicyJpaRepository : JpaRepository<PolicyEntity, Long> {
    fun existsByApplication_IdAndCode(applicationId: Long, code: String): Boolean
}
