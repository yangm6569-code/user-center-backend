package cn.scysn.iam.domain.identity

import cn.scysn.iam.domain.shared.Attributes
import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.requireCode
import cn.scysn.iam.domain.shared.requireName
import java.time.OffsetDateTime

enum class UserStatus(val code: String) {
    PENDING("pending"),
    ACTIVE("active"),
    DISABLED("disabled"),
    FROZEN("frozen"),
    ARCHIVED("archived");
}

enum class AccountType(val code: String) {
    EMPLOYEE("employee"),
    SUPPLIER("supplier"),
    CUSTOMER("customer"),
    SYSTEM("system");
}

enum class RequiredAction(val code: String) {
    CHANGE_PASSWORD("CHANGE_PASSWORD"),
    BIND_MFA("BIND_MFA"),
    VERIFY_EMAIL("VERIFY_EMAIL");
}

data class UserProfile(
    val employeeNo: String?,
    val accountType: AccountType,
    val factoryCode: String?,
    val departmentCode: String?,
    val orgUnitIds: Set<String>,
    val attributes: Attributes = emptyMap(),
)

data class UserRoleGrant(
    val roleId: String,
    val effectiveFrom: OffsetDateTime?,
    val effectiveTo: OffsetDateTime?,
    val reason: String?,
)

class User private constructor(
    val id: String,
    username: String,
    displayName: String,
    email: String?,
    phone: String?,
    profile: UserProfile,
    passwordHash: String,
    passwordTemporary: Boolean,
    status: UserStatus,
    enabled: Boolean,
    requiredActions: Set<RequiredAction>,
    roleGrants: Set<UserRoleGrant>,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var username: String = username
        private set
    var displayName: String = displayName
        private set
    var email: String? = email
        private set
    var phone: String? = phone
        private set
    var profile: UserProfile = profile
        private set
    var passwordHash: String = passwordHash
        private set
    var passwordTemporary: Boolean = passwordTemporary
        private set
    var status: UserStatus = status
        private set
    var enabled: Boolean = enabled
        private set
    var requiredActions: Set<RequiredAction> = requiredActions
        private set
    var roleGrants: Set<UserRoleGrant> = roleGrants
        private set
    var lastLoginAt: OffsetDateTime? = null
        private set
    var passwordChangedAt: OffsetDateTime? = null
        private set
    var freezeUntil: OffsetDateTime? = null
        private set
    var freezeReason: String? = null
        private set
    var archivedAt: OffsetDateTime? = null
        private set
    var archiveReason: String? = null
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validateIdentity(username, displayName, email, phone)
        require(passwordHash.isNotBlank()) { "密码哈希不能为空" }
    }

    companion object {
        fun create(
            id: String = newId("user"),
            username: String,
            displayName: String,
            employeeNo: String?,
            email: String?,
            phone: String?,
            accountType: AccountType,
            factoryCode: String?,
            departmentCode: String?,
            orgUnitIds: Set<String>,
            passwordHash: String,
            forceChangePassword: Boolean,
            attributes: Attributes = emptyMap(),
        ): User {
            val actions = if (forceChangePassword) setOf(RequiredAction.CHANGE_PASSWORD) else emptySet()
            return User(
                id = id,
                username = username,
                displayName = displayName,
                email = email,
                phone = phone,
                profile = UserProfile(
                    employeeNo = employeeNo,
                    accountType = accountType,
                    factoryCode = factoryCode,
                    departmentCode = departmentCode,
                    orgUnitIds = orgUnitIds,
                    attributes = attributes
                ),
                passwordHash = passwordHash,
                passwordTemporary = forceChangePassword,
                status = UserStatus.PENDING,
                enabled = true,
                requiredActions = actions,
                roleGrants = emptySet(),
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            username: String,
            displayName: String,
            email: String?,
            phone: String?,
            profile: UserProfile,
            passwordHash: String,
            passwordTemporary: Boolean,
            status: UserStatus,
            enabled: Boolean,
            requiredActions: Set<RequiredAction>,
            roleGrants: Set<UserRoleGrant>,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
            lastLoginAt: OffsetDateTime?,
            passwordChangedAt: OffsetDateTime?,
            freezeUntil: OffsetDateTime?,
            freezeReason: String?,
            archivedAt: OffsetDateTime?,
            archiveReason: String?,
        ): User {
            val user = User(
                id = id,
                username = username,
                displayName = displayName,
                email = email,
                phone = phone,
                profile = profile,
                passwordHash = passwordHash,
                passwordTemporary = passwordTemporary,
                status = status,
                enabled = enabled,
                requiredActions = requiredActions,
                roleGrants = roleGrants,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            user.lastLoginAt = lastLoginAt
            user.passwordChangedAt = passwordChangedAt
            user.freezeUntil = freezeUntil
            user.freezeReason = freezeReason
            user.archivedAt = archivedAt
            user.archiveReason = archiveReason
            return user
        }
    }

    fun updateProfile(
        displayName: String?,
        email: String?,
        phone: String?,
        factoryCode: String?,
        departmentCode: String?,
        orgUnitIds: Set<String>?,
        attributes: Attributes?,
    ) {
        val nextDisplayName = displayName ?: this.displayName
        val nextEmail = email ?: this.email
        val nextPhone = phone ?: this.phone
        validateIdentity(username, nextDisplayName, nextEmail, nextPhone)
        this.displayName = nextDisplayName
        this.email = nextEmail
        this.phone = nextPhone
        this.profile = profile.copy(
            factoryCode = factoryCode ?: profile.factoryCode,
            departmentCode = departmentCode ?: profile.departmentCode,
            orgUnitIds = orgUnitIds ?: profile.orgUnitIds,
            attributes = attributes ?: profile.attributes
        )
        touch()
    }

    fun renameLogin(username: String) {
        validateIdentity(username, displayName, email, phone)
        this.username = username
        touch()
    }

    fun enable(reason: String?) {
        requireHighRiskReason(reason)
        require(status != UserStatus.ARCHIVED) { "归档用户不能启用" }
        status = UserStatus.ACTIVE
        enabled = true
        touch()
    }

    fun disable(reason: String?) {
        requireHighRiskReason(reason)
        require(status != UserStatus.ARCHIVED) { "归档用户不能禁用" }
        status = UserStatus.DISABLED
        enabled = false
        touch()
    }

    fun freeze(freezeUntil: OffsetDateTime, freezeReason: String?) {
        requireHighRiskReason(freezeReason)
        require(status != UserStatus.ARCHIVED) { "归档用户不能冻结" }
        require(freezeUntil.isAfter(now())) { "冻结到期时间必须晚于当前时间" }
        status = UserStatus.FROZEN
        enabled = false
        this.freezeUntil = freezeUntil
        this.freezeReason = freezeReason
        touch()
    }

    fun unfreeze(reason: String?) {
        requireHighRiskReason(reason)
        require(status == UserStatus.FROZEN) { "只有冻结用户可以解冻" }
        status = UserStatus.ACTIVE
        enabled = true
        freezeUntil = null
        freezeReason = null
        touch()
    }

    fun archive(reason: String?) {
        requireHighRiskReason(reason)
        status = UserStatus.ARCHIVED
        enabled = false
        archivedAt = now()
        archiveReason = reason
        touch()
    }

    fun resetPassword(passwordHash: String, forceChangePassword: Boolean, reason: String?) {
        requireHighRiskReason(reason)
        require(passwordHash.isNotBlank()) { "密码哈希不能为空" }
        this.passwordHash = passwordHash
        this.passwordTemporary = forceChangePassword
        this.passwordChangedAt = now()
        this.requiredActions = if (forceChangePassword) {
            requiredActions + RequiredAction.CHANGE_PASSWORD
        } else {
            requiredActions - RequiredAction.CHANGE_PASSWORD
        }
        touch()
    }

    fun changePassword(passwordHash: String) {
        require(passwordHash.isNotBlank()) { "密码哈希不能为空" }
        this.passwordHash = passwordHash
        this.passwordTemporary = false
        this.passwordChangedAt = now()
        this.requiredActions = requiredActions - RequiredAction.CHANGE_PASSWORD
        touch()
    }

    fun grantRoles(roleIds: Set<String>, effectiveFrom: OffsetDateTime?, effectiveTo: OffsetDateTime?, reason: String?) {
        require(roleIds.isNotEmpty()) { "roleIds 不能为空" }
        requireHighRiskReason(reason)
        val grants = roleIds.map { roleId ->
            requireCode(roleId, "角色ID")
            UserRoleGrant(roleId, effectiveFrom, effectiveTo, reason)
        }
        roleGrants = (roleGrants.filterNot { existing -> roleIds.contains(existing.roleId) } + grants).toSet()
        touch()
    }

    fun revokeRole(roleId: String) {
        requireCode(roleId, "角色ID")
        roleGrants = roleGrants.filterNot { it.roleId == roleId }.toSet()
        touch()
    }

    fun markLogin() {
        lastLoginAt = now()
        touch()
    }

    fun ensureCanLogin() {
        when (status) {
            UserStatus.DISABLED -> throw IllegalStateException("用户已禁用")
            UserStatus.FROZEN -> throw IllegalStateException("用户已冻结")
            UserStatus.ARCHIVED -> throw IllegalStateException("用户已归档")
            UserStatus.PENDING, UserStatus.ACTIVE -> Unit
        }
    }

    private fun touch() {
        updatedAt = now()
    }
}

private fun validateIdentity(username: String, displayName: String, email: String?, phone: String?) {
    requireCode(username, "用户名")
    requireName(displayName, "姓名")
    if (email != null) {
        require(email.length <= 255) { "邮箱长度不能超过 255 个字符" }
        require(email.contains("@")) { "邮箱格式不正确" }
    }
    if (phone != null) {
        require(phone.length <= 32) { "手机号长度不能超过 32 个字符" }
    }
}

private fun requireHighRiskReason(reason: String?) {
    require(!reason.isNullOrBlank()) { "高风险操作必须填写原因" }
    require(reason.length <= 512) { "原因长度不能超过 512 个字符" }
}
