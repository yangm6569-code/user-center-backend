package cn.scysn.usercenter.infrastructure.persistence.jpa.repository

import cn.scysn.usercenter.domain.model.UserStatus
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserAccountEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserAccountJpaRepository : JpaRepository<UserAccountEntity, Long> {
    fun findByUsernameAndDeletedFalse(username: String): UserAccountEntity?

    fun existsByUsernameAndDeletedFalse(username: String): Boolean

    fun existsByEmailAndDeletedFalse(email: String): Boolean

    fun existsByPhoneAndDeletedFalse(phone: String): Boolean

    fun existsByEmployeeNoAndDeletedFalse(employeeNo: String): Boolean

    @Query(
        """
        select u from UserAccountEntity u
        where u.deleted = false
          and (:status is null or u.status = :status)
          and (
            :keywordPattern = '%'
            or lower(u.username) like :keywordPattern
            or lower(u.displayName) like :keywordPattern
            or lower(coalesce(u.email, '')) like :keywordPattern
            or lower(coalesce(u.employeeNo, '')) like :keywordPattern
          )
        """,
    )
    fun pageByQuery(
        @Param("keywordPattern") keywordPattern: String,
        @Param("status") status: UserStatus?,
        pageable: Pageable,
    ): Page<UserAccountEntity>
}
