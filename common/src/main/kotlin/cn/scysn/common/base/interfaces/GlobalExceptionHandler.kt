package cn.scysn.common.base.interfaces

import cn.scysn.common.base.domain.ApiException
import cn.scysn.common.base.domain.Result
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun apiException(ex: ApiException): ResponseEntity<Result<Any>> =
        ResponseEntity.status(ex.status).body(Result.error(ex.status, ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ResponseEntity<Result<Any>> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: "参数错误"}"
        }
        return ResponseEntity.badRequest().body(Result.error(HttpStatus.BAD_REQUEST, message))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraint(ex: ConstraintViolationException): ResponseEntity<Result<Any>> =
        ResponseEntity.badRequest().body(Result.error(HttpStatus.BAD_REQUEST, ex.message ?: "参数错误"))

    @ExceptionHandler(AuthenticationException::class)
    fun authentication(ex: AuthenticationException): ResponseEntity<Result<Any>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.error(HttpStatus.UNAUTHORIZED, "未认证"))

    @ExceptionHandler(AccessDeniedException::class)
    fun accessDenied(ex: AccessDeniedException): ResponseEntity<Result<Any>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(HttpStatus.FORBIDDEN, "无权限"))

    @ExceptionHandler(Exception::class)
    fun generic(ex: Exception): ResponseEntity<Result<Any>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误"))
}
