package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.AuthorizationApplicationService
import cn.scysn.usercenter.application.RequestMetadata
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "用户中台-授权")
@RestController
@RequestMapping("user-center/authorization")
class AuthorizationController(
    private val authorizationApplicationService: AuthorizationApplicationService,
) {
    @GetMapping("roles")
    @Operation(summary = "角色列表")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage','user_center_user_view')")
    fun roles(): Result<List<RoleVO>> = Result.ok(authorizationApplicationService.roles())

    @PostMapping("roles")
    @Operation(summary = "创建角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage')")
    fun createRole(@RequestBody create: RoleCreateDTO, servletRequest: HttpServletRequest): Result<RoleVO> =
        Result.ok(authorizationApplicationService.createRole(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest)))

    @PostMapping("user-roles")
    @Operation(summary = "绑定用户角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun assignUserRole(
        @RequestParam userId: Long,
        @RequestBody assign: RoleAssignDTO,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        authorizationApplicationService.assignUserRole(userId, assign, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("绑定成功")
    }

    @DeleteMapping("user-roles")
    @Operation(summary = "解绑用户角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun unassignUserRole(@RequestParam userId: Long, @RequestParam roleId: Long, servletRequest: HttpServletRequest): Result<String> {
        authorizationApplicationService.unassignUserRole(userId, roleId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("解绑成功")
    }

    @PostMapping("group-roles")
    @Operation(summary = "绑定分组角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage')")
    fun assignGroupRole(@RequestParam groupId: Long, @RequestParam roleId: Long, servletRequest: HttpServletRequest): Result<String> {
        authorizationApplicationService.assignGroupRole(groupId, roleId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("绑定成功")
    }

    @GetMapping("effective")
    @Operation(summary = "当前用户有效权限")
    fun effective(): Result<EffectivePermissionVO> =
        Result.ok(authorizationApplicationService.effectivePermission(currentLoginPrincipal().userId))

    @GetMapping("users/effective")
    @Operation(summary = "指定用户有效权限")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','audit_admin','user_center_user_view')")
    fun userEffective(@RequestParam userId: Long): Result<EffectivePermissionVO> =
        Result.ok(authorizationApplicationService.effectivePermission(userId))

    @GetMapping("users/detail")
    @Operation(summary = "指定用户授权详情")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','audit_admin','user_center_user_view')")
    fun userAuthorizationDetail(@RequestParam userId: Long): Result<UserAuthorizationDetailVO> =
        Result.ok(authorizationApplicationService.userAuthorizationDetail(userId))

    @PostMapping("resources")
    @Operation(summary = "创建资源")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_permission_manage')")
    fun createResource(@RequestBody create: ResourceCreateDTO, servletRequest: HttpServletRequest): Result<String> {
        authorizationApplicationService.createResource(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("创建成功")
    }

    @PostMapping("policies")
    @Operation(summary = "创建策略")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_permission_manage')")
    fun createPolicy(@RequestBody create: PolicyCreateDTO, servletRequest: HttpServletRequest): Result<String> {
        authorizationApplicationService.createPolicy(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("创建成功")
    }

    @PostMapping("permissions")
    @Operation(summary = "创建权限")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_permission_manage')")
    fun createPermission(@RequestBody create: PermissionCreateDTO, servletRequest: HttpServletRequest): Result<String> {
        authorizationApplicationService.createPermission(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("创建成功")
    }
}
