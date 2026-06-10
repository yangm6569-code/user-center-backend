package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.AccessApplicationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AccessApplicationJpaRepository : JpaRepository<AccessApplicationEntity, Long> {
    fun findByClientId(clientId: String): AccessApplicationEntity?

    fun existsByClientId(clientId: String): Boolean
}
