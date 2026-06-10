package cn.scysn.usercenter.infrastructure.security

import cn.scysn.common.base.domain.UnauthorizedException
import cn.scysn.usercenter.domain.model.LoginPrincipal
import org.springframework.security.core.context.SecurityContextHolder

fun currentLoginPrincipal(): LoginPrincipal {
    val principal = SecurityContextHolder.getContext().authentication?.principal
    return principal as? LoginPrincipal ?: throw UnauthorizedException()
}
