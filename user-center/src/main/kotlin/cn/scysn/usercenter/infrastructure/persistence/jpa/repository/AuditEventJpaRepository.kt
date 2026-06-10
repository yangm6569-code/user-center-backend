package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.AuditEventEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface AuditEventJpaRepository : JpaRepository<AuditEventEntity, Long> {
    fun findByCategory(category: AuditCategory, pageable: Pageable): Page<AuditEventEntity>

    fun findByCategoryAndTargetId(category: AuditCategory, targetId: Long, pageable: Pageable): Page<AuditEventEntity>
}
