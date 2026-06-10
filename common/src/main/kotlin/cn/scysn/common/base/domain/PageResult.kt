package cn.scysn.common.base.domain

data class PageResult<T>(
    val items: List<T>,
    val page: Long,
    val size: Long,
    val total: Long,
)
