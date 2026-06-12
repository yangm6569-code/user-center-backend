package cn.scysn.common.error

class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

fun businessError(errorCode: ErrorCode, message: String = errorCode.message): Nothing {
    throw BusinessException(errorCode, message)
}
