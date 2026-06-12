package cn.scysn.iam.domain.authorization

import cn.scysn.iam.domain.shared.Attributes
import cn.scysn.iam.domain.shared.newId
import cn.scysn.iam.domain.shared.now
import cn.scysn.iam.domain.shared.requireCode
import cn.scysn.iam.domain.shared.requireName
import java.time.OffsetDateTime

enum class RoleType(val code: String) {
    PLATFORM("platform"),
    APP("app");
}

enum class ResourceType(val code: String) {
    MENU("menu"),
    PAGE("page"),
    BUTTON("button"),
    API("api"),
    REPORT("report"),
    DATA("data");
}

data class DataScope(
    val type: String,
    val values: Set<String>,
)

class Role private constructor(
    val id: String,
    appId: String?,
    roleCode: String,
    roleName: String,
    roleType: RoleType,
    description: String?,
    enabled: Boolean,
    permissionIds: Set<String>,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var appId: String? = appId
        private set
    var roleCode: String = roleCode
        private set
    var roleName: String = roleName
        private set
    var roleType: RoleType = roleType
        private set
    var description: String? = description
        private set
    var enabled: Boolean = enabled
        private set
    var permissionIds: Set<String> = permissionIds
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validate(roleCode, roleName)
    }

    companion object {
        fun create(
            appId: String?,
            roleCode: String,
            roleName: String,
            roleType: RoleType,
            description: String?,
            enabled: Boolean,
        ): Role {
            require(roleType != RoleType.APP || !appId.isNullOrBlank()) { "应用角色必须指定 appId" }
            return Role(
                id = newId("role"),
                appId = appId,
                roleCode = roleCode,
                roleName = roleName,
                roleType = roleType,
                description = description,
                enabled = enabled,
                permissionIds = emptySet(),
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            appId: String?,
            roleCode: String,
            roleName: String,
            roleType: RoleType,
            description: String?,
            enabled: Boolean,
            permissionIds: Set<String>,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): Role {
            return Role(
                id = id,
                appId = appId,
                roleCode = roleCode,
                roleName = roleName,
                roleType = roleType,
                description = description,
                enabled = enabled,
                permissionIds = permissionIds,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun update(roleName: String?, description: String?, enabled: Boolean?) {
        val nextName = roleName ?: this.roleName
        validate(roleCode, nextName)
        this.roleName = nextName
        this.description = description ?: this.description
        this.enabled = enabled ?: this.enabled
        touch()
    }

    fun bindPermissions(permissionIds: Set<String>, mode: String, reason: String?) {
        require(permissionIds.isNotEmpty()) { "permissionIds 不能为空" }
        require(!reason.isNullOrBlank()) { "角色授权必须填写原因" }
        this.permissionIds = when (mode) {
            "append" -> this.permissionIds + permissionIds
            "replace" -> permissionIds
            else -> throw IllegalArgumentException("mode 只能是 append 或 replace")
        }
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

class Resource private constructor(
    val id: String,
    appId: String,
    parentId: String?,
    resourceCode: String,
    resourceName: String,
    resourceType: ResourceType,
    path: String?,
    method: String?,
    sortOrder: Int,
    enabled: Boolean,
    attributes: Attributes,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var appId: String = appId
        private set
    var parentId: String? = parentId
        private set
    var resourceCode: String = resourceCode
        private set
    var resourceName: String = resourceName
        private set
    var resourceType: ResourceType = resourceType
        private set
    var path: String? = path
        private set
    var method: String? = method
        private set
    var sortOrder: Int = sortOrder
        private set
    var enabled: Boolean = enabled
        private set
    var attributes: Attributes = attributes
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validate(resourceCode, resourceName)
    }

    companion object {
        fun create(
            appId: String,
            parentId: String?,
            resourceCode: String,
            resourceName: String,
            resourceType: ResourceType,
            path: String?,
            method: String?,
            sortOrder: Int,
            attributes: Attributes,
        ): Resource {
            requireCode(appId, "应用ID")
            return Resource(
                id = newId("res"),
                appId = appId,
                parentId = parentId,
                resourceCode = resourceCode,
                resourceName = resourceName,
                resourceType = resourceType,
                path = path,
                method = method,
                sortOrder = sortOrder,
                enabled = true,
                attributes = attributes,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            appId: String,
            parentId: String?,
            resourceCode: String,
            resourceName: String,
            resourceType: ResourceType,
            path: String?,
            method: String?,
            sortOrder: Int,
            enabled: Boolean,
            attributes: Attributes,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): Resource {
            return Resource(
                id = id,
                appId = appId,
                parentId = parentId,
                resourceCode = resourceCode,
                resourceName = resourceName,
                resourceType = resourceType,
                path = path,
                method = method,
                sortOrder = sortOrder,
                enabled = enabled,
                attributes = attributes,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }

    fun update(resourceName: String?, path: String?, method: String?, sortOrder: Int?, enabled: Boolean?, attributes: Attributes?) {
        val nextName = resourceName ?: this.resourceName
        validate(resourceCode, nextName)
        this.resourceName = nextName
        this.path = path ?: this.path
        this.method = method ?: this.method
        this.sortOrder = sortOrder ?: this.sortOrder
        this.enabled = enabled ?: this.enabled
        this.attributes = attributes ?: this.attributes
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

class Permission private constructor(
    val id: String,
    appId: String,
    resourceId: String?,
    permissionCode: String,
    permissionName: String,
    action: String,
    description: String?,
    enabled: Boolean,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var appId: String = appId
        private set
    var resourceId: String? = resourceId
        private set
    var permissionCode: String = permissionCode
        private set
    var permissionName: String = permissionName
        private set
    var action: String = action
        private set
    var description: String? = description
        private set
    var enabled: Boolean = enabled
        private set
    var updatedAt: OffsetDateTime = updatedAt
        private set

    init {
        validate(permissionCode, permissionName)
        requireCode(action, "权限动作")
    }

    companion object {
        fun create(
            appId: String,
            resourceId: String?,
            permissionCode: String,
            permissionName: String,
            action: String,
            description: String?,
        ): Permission {
            requireCode(appId, "应用ID")
            return Permission(
                id = newId("perm"),
                appId = appId,
                resourceId = resourceId,
                permissionCode = permissionCode,
                permissionName = permissionName,
                action = action,
                description = description,
                enabled = true,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            appId: String,
            resourceId: String?,
            permissionCode: String,
            permissionName: String,
            action: String,
            description: String?,
            enabled: Boolean,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): Permission {
            return Permission(
                id = id,
                appId = appId,
                resourceId = resourceId,
                permissionCode = permissionCode,
                permissionName = permissionName,
                action = action,
                description = description,
                enabled = enabled,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        }
    }
}

data class EffectivePermission(
    val appId: String,
    val userId: String,
    val permissionVersion: Long,
    val roles: List<String>,
    val permissions: List<String>,
    val menus: List<MenuNode>,
    val buttons: Map<String, List<String>>,
    val dataScopes: List<DataScope>,
)

data class MenuNode(
    val code: String,
    val name: String,
    val path: String?,
    val children: List<MenuNode> = emptyList(),
)

private fun validate(code: String, name: String) {
    requireCode(code, "编码")
    requireName(name, "名称")
}
