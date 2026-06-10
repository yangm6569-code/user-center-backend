package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.PageResult
import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.RequestMetadata
import cn.scysn.usercenter.application.UserAccountApplicationService
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springdoc.core.annotations.ParameterObject
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "用户中台-用户")
@RestController
@RequestMapping("user-center/users")
class UserAccountController(
    private val userAccountApplicationService: UserAccountApplicationService,
) {
    @GetMapping("page")
    @Operation(summary = "用户分页")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_view','user_center_user_manage')")
    fun page(@ParameterObject query: UserAccountPageQuery): Result<PageResult<UserAccountVO>> =
        Result.ok(userAccountApplicationService.page(query))

    @GetMapping("detail")
    @Operation(summary = "用户详情")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_view','user_center_user_manage')")
    fun detail(@RequestParam id: Long): Result<UserAccountVO> =
        Result.ok(userAccountApplicationService.detail(id))

    @PostMapping
    @Operation(summary = "创建用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun create(
        @RequestBody create: UserAccountCreateDTO,
        servletRequest: HttpServletRequest,
    ): Result<Long> =
        Result.ok(userAccountApplicationService.create(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest)))

    @PutMapping
    @Operation(summary = "修改用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun update(
        @RequestBody update: UserAccountUpdateDTO,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        userAccountApplicationService.update(update, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("修改成功")
    }

    @PostMapping("disable")
    @Operation(summary = "禁用用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun disable(@RequestParam id: Long, servletRequest: HttpServletRequest): Result<String> {
        userAccountApplicationService.disable(id, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("禁用成功")
    }

    @PostMapping("enable")
    @Operation(summary = "启用用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun enable(@RequestParam id: Long, servletRequest: HttpServletRequest): Result<String> {
        userAccountApplicationService.enable(id, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("启用成功")
    }

    @PostMapping("freeze")
    @Operation(summary = "冻结用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','security_admin','user_center_user_manage')")
    fun freeze(
        @RequestParam id: Long,
        @RequestBody freeze: UserAccountFreezeDTO,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        userAccountApplicationService.freeze(id, freeze, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("冻结成功")
    }

    @PostMapping("unfreeze")
    @Operation(summary = "解冻用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','security_admin','user_center_user_manage')")
    fun unfreeze(@RequestParam id: Long, servletRequest: HttpServletRequest): Result<String> {
        userAccountApplicationService.unfreeze(id, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("解冻成功")
    }

    @PostMapping("reset-password")
    @Operation(summary = "重置密码")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','security_admin','user_center_user_manage')")
    fun resetPassword(
        @RequestParam id: Long,
        @RequestBody reset: UserAccountResetPasswordDTO,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        userAccountApplicationService.resetPassword(id, reset, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("重置成功")
    }

    @DeleteMapping
    @Operation(summary = "归档用户")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','user_center_user_manage')")
    fun archive(
        @RequestParam id: Long,
        @RequestBody(required = false) archive: UserAccountArchiveDTO?,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        userAccountApplicationService.archive(id, archive ?: UserAccountArchiveDTO(), currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("归档成功")
    }

    @GetMapping("sessions")
    @Operation(summary = "用户会话")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','security_admin','user_center_user_view')")
    fun sessions(@RequestParam id: Long): Result<List<LoginSessionVO>> =
        Result.ok(userAccountApplicationService.sessions(id))

    @DeleteMapping("sessions")
    @Operation(summary = "终止会话")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','user_admin','security_admin','user_center_user_manage')")
    fun revokeSession(
        @RequestParam userId: Long,
        @RequestParam sessionId: Long,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        userAccountApplicationService.revokeSession(userId, sessionId, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("终止成功")
    }
}
