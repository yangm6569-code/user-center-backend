package cn.scysn.usercenter.api

data class CurrentLoginUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val roles: List<String>,
)

data class UserCenterPermissionInfo(
    val roleCode: String,
    val clientId: String,
    val resourceCode: String,
    val scope: String,
)
