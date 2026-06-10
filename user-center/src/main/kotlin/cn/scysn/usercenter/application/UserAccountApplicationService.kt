package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.ConflictException
import cn.scysn.common.base.domain.NotFoundException
import cn.scysn.common.base.domain.PageResult
import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.domain.model.SessionStatus
import cn.scysn.usercenter.domain.model.UserStatus
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserAccountEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.LoginSessionJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserAccountJpaRepository
import cn.scysn.usercenter.interfaces.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserAccountApplicationService(
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val loginSessionJpaRepository: LoginSessionJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditApplicationService: AuditApplicationService,
) {
    @Transactional(readOnly = true)
    fun page(query: UserAccountPageQuery): PageResult<UserAccountVO> {
        val pageable = PageRequest.of(
            (query.page - 1).coerceAtLeast(0).toInt(),
            query.size.coerceIn(1, 200).toInt(),
            Sort.by("createTime").descending(),
        )
        val keywordPattern = query.keyword
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { "%$it%" }
            ?: "%"
        val result = userAccountJpaRepository.pageByQuery(keywordPattern, query.status, pageable)
        return PageResult(result.content.map { it.toVO() }, query.page, query.size, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun detail(id: Long): UserAccountVO =
        userAccountJpaRepository.findById(id).filter { !it.deleted }.orElseThrow { NotFoundException("用户不存在: $id") }.toVO()

    @Transactional
    fun create(create: UserAccountCreateDTO, actorId: Long?, metadata: RequestMetadata?): Long {
        assertUnique(create.username, create.email, create.phone, create.employeeNo)
        val user = userAccountJpaRepository.save(
            UserAccountEntity().apply {
                username = create.username
                email = create.email
                phone = create.phone
                displayName = create.displayName
                passwordHash = passwordEncoder.encode(create.password)
                status = if (create.active) UserStatus.ACTIVE else UserStatus.PENDING
                accountType = create.accountType
                employeeNo = create.employeeNo
                factoryCode = create.factoryCode
                departmentCode = create.departmentCode
                positionCode = create.positionCode
                passwordChangedAt = Instant.now()
            },
        )
        auditApplicationService.record(
            AuditCategory.USER,
            "USER_CREATED",
            actorId,
            user.id,
            metadata = metadata,
            payload = mapOf("username" to user.username, "status" to user.status.name),
        )
        return user.id ?: 0
    }

    @Transactional
    fun register(register: UserAccountRegisterDTO, metadata: RequestMetadata?): UserAccountVO {
        assertUnique(register.username, register.email, register.phone, null)
        val user = userAccountJpaRepository.save(
            UserAccountEntity().apply {
                username = register.username
                email = register.email
                phone = register.phone
                displayName = register.displayName
                passwordHash = passwordEncoder.encode(register.password)
                status = UserStatus.PENDING
                passwordChangedAt = Instant.now()
            },
        )
        auditApplicationService.record(AuditCategory.SELF_SERVICE, "USER_REGISTERED", targetId = user.id, metadata = metadata)
        return user.toVO()
    }

    @Transactional
    fun update(update: UserAccountUpdateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(update.id).orElseThrow { NotFoundException("用户不存在: ${update.id}") }
        update.email?.let {
            if (it != user.email && userAccountJpaRepository.existsByEmailAndDeletedFalse(it)) throw ConflictException("邮箱已存在: $it")
            user.email = it
            user.emailVerified = false
        }
        update.phone?.let {
            if (it != user.phone && userAccountJpaRepository.existsByPhoneAndDeletedFalse(it)) throw ConflictException("手机号已存在: $it")
            user.phone = it
        }
        update.displayName?.let { user.displayName = it }
        update.accountType?.let { user.accountType = it }
        update.employeeNo?.let {
            if (it != user.employeeNo && userAccountJpaRepository.existsByEmployeeNoAndDeletedFalse(it)) throw ConflictException("员工编号已存在: $it")
            user.employeeNo = it
        }
        update.factoryCode?.let { user.factoryCode = it }
        update.departmentCode?.let { user.departmentCode = it }
        update.positionCode?.let { user.positionCode = it }
        user.touch()
        auditApplicationService.record(AuditCategory.USER, "USER_UPDATED", actorId, user.id, metadata = metadata)
    }

    @Transactional
    fun disable(id: Long, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.status = UserStatus.DISABLED
        user.touch()
        revokeUserSessions(id)
        auditApplicationService.record(AuditCategory.USER, "USER_DISABLED", actorId, id, metadata = metadata)
    }

    @Transactional
    fun enable(id: Long, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.status = UserStatus.ACTIVE
        user.failedLoginCount = 0
        user.lockedUntil = null
        user.touch()
        auditApplicationService.record(AuditCategory.USER, "USER_ENABLED", actorId, id, metadata = metadata)
    }

    @Transactional
    fun freeze(id: Long, freeze: UserAccountFreezeDTO, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.freezeUntil = freeze.freezeUntil
        user.freezeReason = freeze.reason
        user.touch()
        revokeUserSessions(id)
        auditApplicationService.record(
            AuditCategory.USER,
            "USER_FROZEN",
            actorId,
            id,
            metadata = metadata,
            payload = mapOf("freezeUntil" to freeze.freezeUntil.toString(), "reason" to freeze.reason),
        )
    }

    @Transactional
    fun unfreeze(id: Long, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.freezeUntil = null
        user.freezeReason = null
        user.touch()
        auditApplicationService.record(AuditCategory.USER, "USER_UNFROZEN", actorId, id, metadata = metadata)
    }

    @Transactional
    fun resetPassword(id: Long, reset: UserAccountResetPasswordDTO, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.passwordHash = passwordEncoder.encode(reset.newPassword)
        user.passwordChangedAt = Instant.now()
        user.failedLoginCount = 0
        user.lockedUntil = null
        user.touch()
        revokeUserSessions(id)
        auditApplicationService.record(AuditCategory.USER, "PASSWORD_RESET", actorId, id, metadata = metadata)
    }

    @Transactional
    fun archive(id: Long, archive: UserAccountArchiveDTO, actorId: Long?, metadata: RequestMetadata?) {
        val user = userAccountJpaRepository.findById(id).orElseThrow { NotFoundException("用户不存在: $id") }
        user.status = UserStatus.ARCHIVED
        user.archivedAt = Instant.now()
        user.archivedBy = actorId
        user.archiveReason = archive.reason
        user.deleted = true
        user.touch()
        revokeUserSessions(id)
        auditApplicationService.record(AuditCategory.USER, "USER_ARCHIVED", actorId, id, metadata = metadata)
    }

    @Transactional(readOnly = true)
    fun sessions(id: Long): List<LoginSessionVO> =
        loginSessionJpaRepository.findByUser_IdAndStatusOrderByCreateTimeDesc(id, SessionStatus.ACTIVE).map { it.toVO() }

    @Transactional
    fun revokeSession(userId: Long, sessionId: Long, actorId: Long?, metadata: RequestMetadata?) {
        val session = loginSessionJpaRepository.findById(sessionId).orElseThrow { NotFoundException("会话不存在: $sessionId") }
        if (session.user.id != userId) throw NotFoundException("会话不存在: $sessionId")
        session.status = SessionStatus.REVOKED
        session.revokedAt = Instant.now()
        session.touch()
        auditApplicationService.record(AuditCategory.USER, "SESSION_REVOKED", actorId, userId, session.application?.clientId, metadata)
    }

    private fun assertUnique(username: String, email: String?, phone: String?, employeeNo: String?) {
        if (userAccountJpaRepository.existsByUsernameAndDeletedFalse(username)) throw ConflictException("账号已存在: $username")
        if (!email.isNullOrBlank() && userAccountJpaRepository.existsByEmailAndDeletedFalse(email)) throw ConflictException("邮箱已存在: $email")
        if (!phone.isNullOrBlank() && userAccountJpaRepository.existsByPhoneAndDeletedFalse(phone)) throw ConflictException("手机号已存在: $phone")
        if (!employeeNo.isNullOrBlank() && userAccountJpaRepository.existsByEmployeeNoAndDeletedFalse(employeeNo)) throw ConflictException("员工编号已存在: $employeeNo")
    }

    private fun revokeUserSessions(userId: Long) {
        loginSessionJpaRepository.findByUser_IdAndStatusOrderByCreateTimeDesc(userId, SessionStatus.ACTIVE).forEach {
            it.status = SessionStatus.REVOKED
            it.revokedAt = Instant.now()
            it.touch()
        }
    }
}
