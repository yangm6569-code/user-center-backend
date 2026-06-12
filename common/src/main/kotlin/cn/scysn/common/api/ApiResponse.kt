package cn.scysn.common.api

import java.util.UUID

data class ApiResponse<T>(
    val code: String,
    val message: String,
    val traceId: String,
    val data: T? = null,
    val details: List<ApiErrorDetail> = emptyList(),
) {
    companion object {
        fun <T> ok(data: T? = null, message: String = "success"): ApiResponse<T> {
            return ApiResponse(
                code = "OK",
                message = message,
                traceId = TraceIds.current(),
                data = data
            )
        }
    }
}

data class ApiErrorDetail(
    val field: String? = null,
    val code: String,
    val message: String,
)

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Int,
) {
    companion object {
        fun <T> of(items: List<T>, page: Int, pageSize: Int, total: Long): PageResponse<T> {
            val normalizedPageSize = pageSize.coerceAtLeast(1)
            val totalPages = if (total == 0L) {
                0
            } else {
                ((total + normalizedPageSize - 1) / normalizedPageSize).toInt()
            }
            return PageResponse(
                items = items,
                page = page,
                pageSize = normalizedPageSize,
                total = total,
                totalPages = totalPages
            )
        }
    }
}

object TraceIds {
    private val holder = ThreadLocal<String>()

    fun current(): String {
        return holder.get() ?: UUID.randomUUID().toString().replace("-", "").also { holder.set(it) }
    }

    fun set(traceId: String?) {
        holder.set(traceId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().replace("-", ""))
    }

    fun clear() {
        holder.remove()
    }
}
