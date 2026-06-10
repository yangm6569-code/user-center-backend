package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.domain.model.SessionStatus
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.LoginSessionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface LoginSessionJpaRepository : JpaRepository<LoginSessionEntity, Long> {
    fun findByRefreshTokenHash(refreshTokenHash: String): LoginSessionEntity?

    fun findByUser_IdAndStatusOrderByCreateTimeDesc(userId: Long, status: SessionStatus): List<LoginSessionEntity>
}
