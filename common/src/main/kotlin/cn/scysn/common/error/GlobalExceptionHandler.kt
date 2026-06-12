package cn.scysn.common.error

import cn.scysn.common.api.ApiErrorDetail
import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.TraceIds
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        return error(e.errorCode.name, e.message, e.errorCode.httpStatus)
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class
    )
    fun handleBadRequest(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return error(ErrorCode.BAD_REQUEST.name, e.message ?: ErrorCode.BAD_REQUEST.message, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val details = e.bindingResult.fieldErrors.map {
            ApiErrorDetail(
                field = it.field,
                code = "INVALID",
                message = it.defaultMessage ?: "字段不合法"
            )
        }
        return validationError(details)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiResponse<Nothing>> {
        val details = e.constraintViolations.map {
            ApiErrorDetail(
                field = it.propertyPath?.toString(),
                code = "INVALID",
                message = it.message
            )
        }
        return validationError(details)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return error(
            ErrorCode.INTERNAL_ERROR.name,
            e.message ?: ErrorCode.INTERNAL_ERROR.message,
            HttpStatus.INTERNAL_SERVER_ERROR
        )
    }

    private fun validationError(details: List<ApiErrorDetail>): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            ApiResponse(
                code = ErrorCode.VALIDATION_ERROR.name,
                message = ErrorCode.VALIDATION_ERROR.message,
                traceId = TraceIds.current(),
                details = details
            )
        )
    }

    private fun error(code: String, message: String, status: HttpStatus): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(status).body(
            ApiResponse(
                code = code,
                message = message,
                traceId = TraceIds.current()
            )
        )
    }
}
