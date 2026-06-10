package cn.scysn.usercenter.domain.model

data class LoginPrincipal(
    val userId: Long,
    val username: String,
    val sessionId: Long,
    val clientId: String?,
    val roles: List<String>,
)
