package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.AccountType
import cn.scysn.usercenter.domain.model.UserStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "uc_user_account")
class UserAccountEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "username", nullable = false, unique = true, length = 80)
    var username: String = ""

    @Column(name = "email", unique = true, length = 160)
    var email: String? = null

    @Column(name = "phone", unique = true, length = 40)
    var phone: String? = null

    @Column(name = "display_name", nullable = false, length = 120)
    var displayName: String = ""

    @Column(name = "password_hash", nullable = false, length = 120)
    var passwordHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    var status: UserStatus = UserStatus.PENDING

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 24)
    var accountType: AccountType = AccountType.EMPLOYEE

    @Column(name = "employee_no", unique = true, length = 80)
    var employeeNo: String? = null

    @Column(name = "factory_code", length = 80)
    var factoryCode: String? = null

    @Column(name = "department_code", length = 80)
    var departmentCode: String? = null

    @Column(name = "position_code", length = 80)
    var positionCode: String? = null

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false

    @Column(name = "failed_login_count", nullable = false)
    var failedLoginCount: Int = 0

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null

    @Column(name = "freeze_until")
    var freezeUntil: Instant? = null

    @Column(name = "freeze_reason", length = 500)
    var freezeReason: String? = null

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null

    @Column(name = "password_changed_at")
    var passwordChangedAt: Instant? = null

    @Column(name = "archived_at")
    var archivedAt: Instant? = null

    @Column(name = "archived_by")
    var archivedBy: Long? = null

    @Column(name = "archive_reason", length = 500)
    var archiveReason: String? = null

    @Column(name = "deleted", nullable = false)
    var deleted: Boolean = false

    fun isLocked(now: Instant): Boolean = lockedUntil?.isAfter(now) == true

    fun isFrozen(now: Instant): Boolean = freezeUntil?.isAfter(now) == true
}
