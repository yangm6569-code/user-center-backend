package cn.scysn.common.base.domain

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    override val message: String,
) : RuntimeException(message)

class NotFoundException(message: String) : ApiException(HttpStatus.NOT_FOUND, message)

class ConflictException(message: String) : ApiException(HttpStatus.CONFLICT, message)

class UnauthorizedException(message: String = "未认证") : ApiException(HttpStatus.UNAUTHORIZED, message)

class ForbiddenException(message: String = "无权限") : ApiException(HttpStatus.FORBIDDEN, message)
