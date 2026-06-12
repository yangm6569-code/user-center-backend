package cn.scysn.common.page

data class PageQuery(
    val page: Int = 1,
    val pageSize: Int = 20,
) {
    init {
        require(page >= 1) { "page 必须从 1 开始" }
        require(pageSize in 1..200) { "pageSize 必须在 1 到 200 之间" }
    }

    val offset: Int
        get() = (page - 1) * pageSize
}
