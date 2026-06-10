package cn.scysn.common.base.domain

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail

@Suppress("unused", "UNCHECKED_CAST")
@Schema(description = "统一返回结果")
data class Result<T>(
    @field:Schema(description = "是否成功") val success: Boolean = true,
    @field:Schema(description = "返回实际数据") val data: T? = null,
    @field:Schema(description = "错误码") val errorCode: Int = HttpStatus.OK.value(),
    @field:Schema(description = "错误信息") val message: String = HttpStatus.OK.reasonPhrase,
    @field:Schema(description = "错误详细信息") val error: ProblemDetail? = null,
) {
    companion object {
        fun <T> ok(data: T? = null): Result<T> = Result(data = data)

        fun <T> ok(data: T? = null, message: String): Result<T> =
            Result(data = data, message = message)

        fun error(problemDetail: ProblemDetail): Result<Any> =
            Result(
                success = false,
                errorCode = problemDetail.status,
                message = problemDetail.title ?: problemDetail.detail ?: "",
                error = problemDetail,
            )

        fun error(status: HttpStatusCode): Result<Any> =
            error(ProblemDetail.forStatus(status))

        fun error(status: HttpStatusCode, detail: String): Result<Any> =
            error(ProblemDetail.forStatusAndDetail(status, detail))

        inline fun <reified T> errorOf(status: HttpStatus, detail: String): Result<T> =
            error(status, detail) as Result<T>
    }
}
