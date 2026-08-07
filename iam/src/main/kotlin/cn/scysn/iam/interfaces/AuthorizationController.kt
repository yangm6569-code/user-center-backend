package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.AuthorizationApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "权限授权", description = "角色、资源、权限点、用户角色授权和有效权限查询接口")
class AuthorizationController(
    private val authorizationApplicationService: AuthorizationApplicationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/roles")
    @Operation(summary = "分页查询角色", description = "按应用、角色类型和关键字分页查询角色列表。")
    fun roles(
        @Parameter(description = "系统域 ID") @RequestParam(required = false) domainId: String?,
        @Parameter(description = "应用 ID，不传则查询全部应用范围角色") @RequestParam(required = false) appId: String?,
        @Parameter(description = "角色类型，如 platform、app") @RequestParam(required = false) roleType: String?,
        @Parameter(description = "关键字，匹配角色编码或名称") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<RoleResponse>> {
        return ApiResponse.ok(
            authorizationApplicationService.roles(domainId, appId, roleType, keyword, PageQuery(page, pageSize))
                .mapItems { it.toResponse() }
        )
    }

    @PostMapping("/roles")
    @Operation(summary = "创建角色", description = "创建平台角色或应用角色。")
    fun createRole(@Valid @RequestBody request: CreateRoleRequest): ApiResponse<RoleResponse> {
        return ApiResponse.ok(authorizationApplicationService.createRole(request.toCommand()).toResponse())
    }

    @PatchMapping("/roles/{id}")
    @Operation(summary = "更新角色", description = "更新角色名称、描述和启用状态。")
    fun updateRole(@PathVariable id: String, @RequestBody request: UpdateRoleRequest): ApiResponse<RoleResponse> {
        return ApiResponse.ok(authorizationApplicationService.updateRole(id, request.toCommand()).toResponse())
    }

    @DeleteMapping("/roles/{id}")
    @Operation(summary = "删除角色", description = "根据角色 ID 删除角色。")
    fun deleteRole(@PathVariable id: String): ResponseEntity<Void> {
        authorizationApplicationService.deleteRole(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/roles/{id}/permissions")
    @Operation(summary = "绑定角色权限点", description = "为角色绑定或替换权限点集合。")
    fun bindRolePermissions(@PathVariable id: String, @Valid @RequestBody request: BindRolePermissionsRequest): ApiResponse<RoleResponse> {
        return ApiResponse.ok(authorizationApplicationService.bindRolePermissions(id, request.toCommand()).toResponse())
    }

    @PostMapping("/users/{id}/roles")
    @Operation(summary = "授予用户角色", description = "给指定用户授予一个或多个角色，可设置生效时间范围。")
    fun grantUserRoles(@PathVariable id: String, @Valid @RequestBody request: GrantUserRolesRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(authorizationApplicationService.grantUserRoles(id, request.toCommand()).toDetailResponse())
    }

    @DeleteMapping("/users/{id}/roles/{roleId}")
    @Operation(summary = "撤销用户角色", description = "撤销指定用户的某个角色授权。")
    fun revokeUserRole(@PathVariable id: String, @PathVariable roleId: String): ResponseEntity<Void> {
        authorizationApplicationService.revokeUserRole(id, roleId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/resources")
    @Operation(summary = "查询资源列表", description = "按应用、资源类型和关键字查询菜单、页面、按钮、API 等资源。")
    fun resources(
        @Parameter(description = "系统域 ID") @RequestParam(required = false) domainId: String?,
        @Parameter(description = "应用 ID") @RequestParam(required = false) appId: String?,
        @Parameter(description = "业务域 ID") @RequestParam(required = false) businessDomainId: String?,
        @Parameter(description = "资源类型，如 menu、page、button、api、report、data") @RequestParam(required = false) resourceType: String?,
        @Parameter(description = "关键字，匹配资源编码或名称") @RequestParam(required = false) keyword: String?,
    ): ApiResponse<List<ResourceResponse>> {
        return ApiResponse.ok(authorizationApplicationService.resources(domainId, appId, businessDomainId, resourceType, keyword).map { it.toResponse() })
    }

    @PostMapping("/resources")
    @Operation(summary = "创建资源", description = "创建菜单、页面、按钮、API 等资源定义。")
    fun createResource(@Valid @RequestBody request: CreateResourceRequest): ApiResponse<ResourceResponse> {
        return ApiResponse.ok(authorizationApplicationService.createResource(request.toCommand()).toResponse())
    }

    @PatchMapping("/resources/{id}")
    @Operation(summary = "更新资源", description = "更新资源名称、路径、方法、排序、启用状态和扩展属性。")
    fun updateResource(@PathVariable id: String, @RequestBody request: UpdateResourceRequest): ApiResponse<ResourceResponse> {
        return ApiResponse.ok(authorizationApplicationService.updateResource(id, request.toCommand()).toResponse())
    }

    @DeleteMapping("/resources/{id}")
    @Operation(summary = "删除资源", description = "根据资源 ID 删除资源。")
    fun deleteResource(@PathVariable id: String): ResponseEntity<Void> {
        authorizationApplicationService.deleteResource(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/permissions")
    @Operation(summary = "查询权限点列表", description = "按应用、资源和关键字查询权限点。")
    fun permissions(
        @Parameter(description = "系统域 ID") @RequestParam(required = false) domainId: String?,
        @Parameter(description = "应用 ID") @RequestParam(required = false) appId: String?,
        @Parameter(description = "业务域 ID") @RequestParam(required = false) businessDomainId: String?,
        @Parameter(description = "资源 ID") @RequestParam(required = false) resourceId: String?,
        @Parameter(description = "关键字，匹配权限编码或名称") @RequestParam(required = false) keyword: String?,
    ): ApiResponse<List<PermissionResponse>> {
        return ApiResponse.ok(authorizationApplicationService.permissions(domainId, appId, businessDomainId, resourceId, keyword).map { it.toResponse() })
    }

    @PostMapping("/permissions")
    @Operation(summary = "创建权限点", description = "创建可授权的权限点，并可关联到具体资源。")
    fun createPermission(@Valid @RequestBody request: CreatePermissionRequest): ApiResponse<PermissionResponse> {
        return ApiResponse.ok(authorizationApplicationService.createPermission(request.toCommand()).toResponse())
    }

    @PatchMapping("/permissions/{id}")
    @Operation(summary = "更新权限点", description = "更新权限点名称、动作和描述。")
    fun updatePermission(@PathVariable id: String, @RequestBody request: UpdatePermissionRequest): ApiResponse<PermissionResponse> {
        return ApiResponse.ok(authorizationApplicationService.updatePermission(id, request.toCommand()).toResponse())
    }

    @DeleteMapping("/permissions/{id}")
    @Operation(summary = "删除权限点", description = "根据权限点 ID 删除权限点。")
    fun deletePermission(@PathVariable id: String): ResponseEntity<Void> {
        authorizationApplicationService.deletePermission(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/permissions/effective")
    @Operation(summary = "查询当前用户有效权限", description = "计算当前用户在指定应用下实际生效的角色、权限点、菜单、按钮和数据范围。")
    fun currentEffective(@RequestParam appId: String): ApiResponse<Any> {
        return ApiResponse.ok(authorizationApplicationService.effectivePermissions(currentUserProvider.userId(), appId))
    }

    @GetMapping("/users/{id}/permissions/effective")
    @Operation(summary = "查询指定用户有效权限", description = "计算指定用户在指定应用下实际生效的角色、权限点、菜单、按钮和数据范围。")
    fun userEffective(@PathVariable id: String, @RequestParam appId: String): ApiResponse<Any> {
        return ApiResponse.ok(authorizationApplicationService.effectivePermissions(id, appId))
    }

    @GetMapping("/gateway/api-rules")
    @Operation(summary = "查询网关 API 权限规则", description = "按应用返回可用于网关鉴权的 API 资源和权限点规则。")
    fun apiRules(@RequestParam appId: String): ApiResponse<List<ApiPermissionRuleResponse>> {
        return ApiResponse.ok(authorizationApplicationService.apiPermissionRules(appId).map { it.toResponse() })
    }
}
