package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.iam.application.OrganizationApplicationService
import cn.scysn.iam.application.OrgUnitTreeNode
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
@RequestMapping("/api/v1/org-units")
@Tag(name = "组织管理", description = "组织树查询、组织节点创建、修改和删除接口")
class OrganizationController(
    private val organizationApplicationService: OrganizationApplicationService,
) {
    @GetMapping
    @Operation(summary = "查询组织树", description = "按根节点查询组织树，可选择是否包含已禁用组织节点。")
    fun tree(
        @Parameter(description = "根组织节点 ID，不传则查询整棵组织树") @RequestParam(required = false) rootId: String?,
        @Parameter(description = "是否包含已禁用节点") @RequestParam(defaultValue = "false") includeDisabled: Boolean,
    ): ApiResponse<List<OrgUnitTreeNode>> {
        return ApiResponse.ok(organizationApplicationService.tree(rootId, includeDisabled))
    }

    @PostMapping
    @Operation(summary = "创建组织节点", description = "创建公司、部门、车间等组织节点。")
    fun create(@Valid @RequestBody request: CreateOrgUnitRequest): ApiResponse<OrgUnitResponse> {
        return ApiResponse.ok(organizationApplicationService.create(request.toCommand()).toResponse())
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新组织节点", description = "更新组织节点的父级、编码、名称、类型、排序和启用状态。")
    fun update(@PathVariable id: String, @RequestBody request: UpdateOrgUnitRequest): ApiResponse<OrgUnitResponse> {
        return ApiResponse.ok(organizationApplicationService.update(id, request.toCommand()).toResponse())
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "绑定组织角色", description = "为组织节点追加或替换角色；用户加入该组织或其子组织后会自动继承这些角色。")
    fun bindRoles(@PathVariable id: String, @Valid @RequestBody request: BindOrgUnitRolesRequest): ApiResponse<OrgUnitResponse> {
        return ApiResponse.ok(organizationApplicationService.bindRoles(id, request.toCommand()).toResponse())
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "查询组织绑定角色", description = "查询直接绑定在指定组织节点上的角色列表。")
    fun roles(@PathVariable id: String): ApiResponse<List<RoleResponse>> {
        return ApiResponse.ok(organizationApplicationService.roles(id).map { it.toResponse() })
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @Operation(summary = "回收组织角色", description = "从组织节点移除角色；用户从该组织继承的对应角色会自动失效。")
    fun revokeRole(@PathVariable id: String, @PathVariable roleId: String): ApiResponse<OrgUnitResponse> {
        return ApiResponse.ok(organizationApplicationService.revokeRole(id, roleId).toResponse())
    }

    @PostMapping("/{id}/users/{userId}")
    @Operation(summary = "添加组织成员", description = "将用户加入指定组织；用户会自动继承该组织及父组织绑定的角色。")
    fun addUser(@PathVariable id: String, @PathVariable userId: String): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(organizationApplicationService.addUser(id, userId).toDetailResponse())
    }

    @PostMapping("/{id}/users")
    @Operation(summary = "批量添加组织成员", description = "将多个用户一次性加入指定组织；用户会自动继承该组织及父组织绑定的角色。")
    fun addUsers(@PathVariable id: String, @Valid @RequestBody request: AddOrgUnitUsersRequest): ApiResponse<List<UserDetailResponse>> {
        return ApiResponse.ok(organizationApplicationService.addUsers(id, request.userIds).map { it.toDetailResponse() })
    }

    @DeleteMapping("/{id}/users/{userId}")
    @Operation(summary = "移除组织成员", description = "将用户从指定组织移除；该组织带来的继承角色会自动回收。")
    fun removeUser(@PathVariable id: String, @PathVariable userId: String): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(organizationApplicationService.removeUser(id, userId).toDetailResponse())
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除组织节点", description = "删除组织节点；存在子节点或成员时不允许删除。")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        organizationApplicationService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
