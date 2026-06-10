package cn.scysn.common.base.domain

open class PageQuery(
    open val page: Long = 1,
    open val size: Long = 10,
) {
    val offset: Long
        get() = (page - 1).coerceAtLeast(0) * size.coerceAtLeast(1)
}
