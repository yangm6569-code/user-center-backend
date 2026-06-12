package cn.scysn.iam.domain.organization

import cn.scysn.iam.domain.shared.Attributes
import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.requireCode
import cn.scysn.iam.domain.shared.requireName
import java.time.OffsetDateTime

class OrgUnit private constructor(
    val id: String,
    parentId: String?,
    code: String,
    name: String,
    type: String,
    sortOrder: Int,
    enabled: Boolean,
    roleIds: Set<String>,
    attributes: Attributes,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var parentId: String? = parentId
        private set
    var code: String = code
        private set
    var name: String = name
        private set
    var type: String = type
        private set
    var sortOrder: Int = sortOrder
        private set
    var enabled: Boolean = enabled
        private set
    var roleIds: Set<String> = roleIds
        private set
    var attributes: Attributes = attributes
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validate(code, name, type)
    }

    companion object {
        fun create(
            parentId: String?,
            code: String,
            name: String,
            type: String,
            sortOrder: Int,
            attributes: Attributes = emptyMap(),
        ): OrgUnit {
            return OrgUnit(
                id = newId("org"),
                parentId = parentId,
                code = code,
                name = name,
                type = type,
                sortOrder = sortOrder,
                enabled = true,
                roleIds = emptySet(),
                attributes = attributes,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            parentId: String?,
            code: String,
            name: String,
            type: String,
            sortOrder: Int,
            enabled: Boolean,
            roleIds: Set<String>,
            attributes: Attributes,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): OrgUnit {
            return OrgUnit(
                id = id,
                parentId = parentId,
                code = code,
                name = name,
                type = type,
                sortOrder = sortOrder,
                enabled = enabled,
                roleIds = roleIds,
                attributes = attributes,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun update(parentId: String?, code: String?, name: String?, type: String?, sortOrder: Int?, attributes: Attributes?) {
        val nextCode = code ?: this.code
        val nextName = name ?: this.name
        val nextType = type ?: this.type
        validate(nextCode, nextName, nextType)
        require(parentId != id) { "父组织不能是自身" }
        this.parentId = parentId ?: this.parentId
        this.code = nextCode
        this.name = nextName
        this.type = nextType
        this.sortOrder = sortOrder ?: this.sortOrder
        this.attributes = attributes ?: this.attributes
        touch()
    }

    fun bindRoles(roleIds: Set<String>, mode: String) {
        require(roleIds.isNotEmpty()) { "roleIds 不能为空" }
        roleIds.forEach { requireCode(it, "角色ID") }
        this.roleIds = when (mode) {
            "append" -> this.roleIds + roleIds
            "replace" -> roleIds
            else -> throw IllegalArgumentException("mode 只能是 append 或 replace")
        }
        touch()
    }

    fun revokeRole(roleId: String) {
        requireCode(roleId, "角色ID")
        roleIds = roleIds - roleId
        touch()
    }

    fun disable() {
        enabled = false
        touch()
    }

    fun enable() {
        enabled = true
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

private fun validate(code: String, name: String, type: String) {
    requireCode(code, "组织编码")
    requireName(name, "组织名称")
    requireCode(type, "组织类型")
}
