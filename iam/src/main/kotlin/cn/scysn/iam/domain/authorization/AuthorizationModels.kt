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
    DATA("data"),
    FIELD("field");
}

data class DataScope(
    val type: String,
    val values: Set<String>,
)

class Role private constructor(
    val id: String,
    domainId: String?,
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
    var domainId: String? = domainId
        private set
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
            domainId: String?,
            appId: String?,
            roleCode: String,
            roleName: String,
            roleType: RoleType,
            description: String?,
            enabled: Boolean,
        ): Role {
            require(roleType != RoleType.PLATFORM || appId.isNullOrBlank()) { "平台角色不能绑定 appId" }
            require(roleType != RoleType.APP || !domainId.isNullOrBlank() || !appId.isNullOrBlank()) { "应用角色必须指定系统域或 appId" }
            return Role(
                id = newId("role"),
                domainId = domainId ?: appId,
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
            domainId: String?,
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
                domainId = domainId ?: appId,
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
    domainId: String,
    appId: String,
    val businessDomainId: String?,
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
    var domainId: String = domainId
        private set
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
            domainId: String?,
            appId: String,
            businessDomainId: String? = null,
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
                domainId = domainId ?: appId,
                appId = appId,
                businessDomainId = businessDomainId,
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
            domainId: String?,
            appId: String,
            businessDomainId: String?,
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
                domainId = domainId ?: appId,
                appId = appId,
                businessDomainId = businessDomainId,
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

    fun update(resourceName: String?, resourceType: ResourceType?, path: String?, method: String?, sortOrder: Int?, enabled: Boolean?, attributes: Attributes?) {
        val nextName = resourceName ?: this.resourceName
        validate(resourceCode, nextName)
        this.resourceName = nextName
        this.resourceType = resourceType ?: this.resourceType
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
    domainId: String,
    appId: String,
    val businessDomainId: String?,
    resourceId: String?,
    permissionCode: String,
    permissionName: String,
    action: String,
    description: String?,
    enabled: Boolean,
    val createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
) {
    var domainId: String = domainId
        private set
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

    fun update(permissionName: String?, action: String?, description: String?) {
        val nextName = permissionName ?: this.permissionName
        validate(permissionCode, nextName)
        this.permissionName = nextName
        this.action = action ?: this.action
        this.description = description ?: this.description
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }

    companion object {
        fun create(
            domainId: String?,
            appId: String,
            businessDomainId: String? = null,
            resourceId: String?,
            permissionCode: String,
            permissionName: String,
            action: String,
            description: String?,
        ): Permission {
            requireCode(appId, "应用ID")
            return Permission(
                id = newId("perm"),
                domainId = domainId ?: appId,
                appId = appId,
                businessDomainId = businessDomainId,
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
            domainId: String?,
            appId: String,
            businessDomainId: String?,
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
                domainId = domainId ?: appId,
                appId = appId,
                businessDomainId = businessDomainId,
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

data class FieldPolicy(
    val visible: Set<String>,
    val hidden: Set<String>,
    val masked: Set<String>,
    val readonly: Set<String>,
)

enum class FieldPermissionType(val code: String) {
    VISIBLE("visible"),
    READONLY("readonly"),
    HIDDEN("hidden"),
    MASKED("masked");
}

class BusinessDomain private constructor(
    val id: String,
    val domainId: String,
    val appId: String,
    var code: String,
    var name: String,
    var description: String?,
    var enabled: Boolean,
    val createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime,
) {
    init {
        validate(code, name)
        requireCode(appId, "应用ID")
        requireCode(domainId, "系统域ID")
    }

    companion object {
        fun create(
            domainId: String,
            appId: String,
            code: String,
            name: String,
            description: String?,
        ): BusinessDomain {
            return BusinessDomain(
                id = newId("bd"),
                domainId = domainId,
                appId = appId,
                code = code,
                name = name,
                description = description,
                enabled = true,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            domainId: String,
            appId: String,
            code: String,
            name: String,
            description: String?,
            enabled: Boolean,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): BusinessDomain {
            return BusinessDomain(id, domainId, appId, code, name, description, enabled, createdAt, updatedAt)
        }
    }

    fun update(name: String?, description: String?, enabled: Boolean?) {
        val nextName = name ?: this.name
        validate(code, nextName)
        this.name = nextName
        this.description = description ?: this.description
        this.enabled = enabled ?: this.enabled
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

class FieldPermission private constructor(
    val id: String,
    val businessDomainId: String,
    val roleId: String,
    var fieldCode: String,
    var fieldName: String?,
    var permissionType: FieldPermissionType,
    var enabled: Boolean,
    val createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime,
) {
    init {
        requireCode(fieldCode, "字段编码")
    }

    companion object {
        fun create(
            businessDomainId: String,
            roleId: String,
            fieldCode: String,
            fieldName: String?,
            permissionType: FieldPermissionType,
        ): FieldPermission {
            return FieldPermission(
                id = newId("fp"),
                businessDomainId = businessDomainId,
                roleId = roleId,
                fieldCode = fieldCode,
                fieldName = fieldName,
                permissionType = permissionType,
                enabled = true,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            businessDomainId: String,
            roleId: String,
            fieldCode: String,
            fieldName: String?,
            permissionType: FieldPermissionType,
            enabled: Boolean,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): FieldPermission {
            return FieldPermission(id, businessDomainId, roleId, fieldCode, fieldName, permissionType, enabled, createdAt, updatedAt)
        }
    }

    fun updateType(type: FieldPermissionType) {
        this.permissionType = type
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

class DataScopeConfig private constructor(
    val id: String,
    val businessDomainId: String,
    val roleId: String,
    var scopeType: String,
    var scopeValue: Set<String>,
    var enabled: Boolean,
    val createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime,
) {
    companion object {
        fun create(
            businessDomainId: String,
            roleId: String,
            scopeType: String,
            scopeValue: Set<String>,
        ): DataScopeConfig {
            return DataScopeConfig(
                id = newId("ds"),
                businessDomainId = businessDomainId,
                roleId = roleId,
                scopeType = scopeType,
                scopeValue = scopeValue,
                enabled = true,
                createdAt = now(),
                updatedAt = now()
            )
        }

        fun restore(
            id: String,
            businessDomainId: String,
            roleId: String,
            scopeType: String,
            scopeValue: Set<String>,
            enabled: Boolean,
            createdAt: OffsetDateTime,
            updatedAt: OffsetDateTime,
        ): DataScopeConfig {
            return DataScopeConfig(id, businessDomainId, roleId, scopeType, scopeValue, enabled, createdAt, updatedAt)
        }
    }

    fun update(scopeType: String?, scopeValue: Set<String>?) {
        this.scopeType = scopeType ?: this.scopeType
        this.scopeValue = scopeValue ?: this.scopeValue
        touch()
    }

    private fun touch() {
        updatedAt = now()
    }
}

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
