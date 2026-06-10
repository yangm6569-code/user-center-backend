package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.RequestMetadata
import cn.scysn.usercenter.application.UserGroupApplicationService
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户中台-组织分组")
@RestController
@RequestMapping("user-center/groups")
class UserGroupController(
    private val userGroupApplicationService: UserGroupApplicationService,
) {
    @GetMapping
    @Operation(summary = "分组列表")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_view','user_center_user_manage')")
    fun list(): Result<List<GroupVO>> = Result.ok(userGroupApplicationService.list())

    @GetMapping("tree")
    @Operation(summary = "组织分组树")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_view','user_center_user_manage')")
    fun tree(): Result<List<GroupTreeVO>> = Result.ok(userGroupApplicationService.tree())

    @PostMapping
    @Operation(summary = "创建分组")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun create(@RequestBody create: GroupCreateDTO, servletRequest: HttpServletRequest): Result<Long> =
        Result.ok(userGroupApplicationService.create(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest)))

    @PutMapping
    @Operation(summary = "修改分组")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun update(@RequestBody update: GroupUpdateDTO, servletRequest: HttpServletRequest): Result<String> {
        userGroupApplicationService.update(update, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("修改成功")
    }

    @GetMapping("{groupId}/roles")
    @Operation(summary = "分组角色，包含从父组继承的角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage','user_center_user_view')")
    fun roles(@PathVariable groupId: Long): Result<GroupRoleSummaryVO> =
        Result.ok(userGroupApplicationService.roleSummary(groupId))

    @GetMapping("{groupId}/members")
    @Operation(summary = "分组成员")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_view','user_center_user_manage')")
    fun members(
        @PathVariable groupId: Long,
        @RequestParam(defaultValue = "false") includeSubgroups: Boolean,
    ): Result<List<UserAccountVO>> =
        Result.ok(userGroupApplicationService.members(groupId, includeSubgroups))

    @PostMapping("{groupId}/roles")
    @Operation(summary = "给分组绑定角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage')")
    fun assignRole(@PathVariable groupId: Long, @RequestParam roleId: Long, servletRequest: HttpServletRequest): Result<String> {
        userGroupApplicationService.assignRole(groupId, roleId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("绑定成功")
    }

    @DeleteMapping("{groupId}/roles")
    @Operation(summary = "解绑分组直接角色")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_role_manage')")
    fun unassignRole(@PathVariable groupId: Long, @RequestParam roleId: Long, servletRequest: HttpServletRequest): Result<String> {
        userGroupApplicationService.unassignRole(groupId, roleId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("解绑成功")
    }

    @PostMapping("members")
    @Operation(summary = "添加成员")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun addMember(@RequestParam groupId: Long, @RequestParam userId: Long, servletRequest: HttpServletRequest): Result<String> {
        userGroupApplicationService.addMember(groupId, userId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("添加成功")
    }

    @DeleteMapping("members")
    @Operation(summary = "移除成员")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun removeMember(@RequestParam groupId: Long, @RequestParam userId: Long, servletRequest: HttpServletRequest): Result<String> {
        userGroupApplicationService.removeMember(groupId, userId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("移除成功")
    }
}
