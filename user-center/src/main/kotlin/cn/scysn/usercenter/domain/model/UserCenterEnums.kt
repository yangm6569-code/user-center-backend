package cn.scysn.usercenter.domain.model

enum class UserStatus {
    PENDING,
    ACTIVE,
    DISABLED,
    ARCHIVED,
}

enum class AccountType {
    EMPLOYEE,
    SUPPLIER,
    CUSTOMER,
    SERVICE,
}

enum class ApplicationType {
    WEB,
    API,
    MOBILE,
    DEVICE,
    SERVICE,
}

enum class ApplicationStatus {
    ACTIVE,
    DISABLED,
}

enum class RoleScope {
    PLATFORM,
    APPLICATION,
}

enum class GroupType {
    ORGANIZATION,
    POSITION,
    EXTERNAL,
    CUSTOM,
}

enum class ResourceType {
    MENU,
    API,
    DATA,
    REPORT,
}

enum class PolicyType {
    ROLE,
    GROUP,
    USER_ATTRIBUTE,
    TIME,
    IP_RANGE,
    COMPOSITE,
}

enum class PermissionEffect {
    ALLOW,
    DENY,
}

enum class SessionStatus {
    ACTIVE,
    REVOKED,
    EXPIRED,
}

enum class MfaType {
    TOTP,
    WEBAUTHN,
    SMS,
    EMAIL,
}

enum class AuditCategory {
    LOGIN,
    TOKEN,
    USER,
    PERMISSION,
    ADMIN,
    SELF_SERVICE,
    RISK,
}

enum class AuditResult {
    SUCCESS,
    FAILURE,
}
