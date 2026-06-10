package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.PageResult
import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.AuditApplicationService
import cn.scysn.usercenter.domain.model.AuditCategory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "用户中台-审计")
@RestController
@RequestMapping("user-center/audit")
class AuditController(
    private val auditApplicationService: AuditApplicationService,
) {
    @GetMapping("login-events")
    @Operation(summary = "登录审计")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','audit_admin','user_center_audit_view')")
    fun loginEvents(
        @RequestParam(defaultValue = "1") page: Long,
        @RequestParam(defaultValue = "10") size: Long,
    ): Result<PageResult<AuditEventVO>> =
        Result.ok(auditApplicationService.page(AuditCategory.LOGIN, page, size))

    @GetMapping("admin-events")
    @Operation(summary = "管理审计")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','audit_admin','user_center_audit_view')")
    fun adminEvents(
        @RequestParam(defaultValue = "1") page: Long,
        @RequestParam(defaultValue = "10") size: Long,
        @RequestParam(required = false) targetId: Long?,
    ): Result<PageResult<AuditEventVO>> =
        Result.ok(auditApplicationService.page(AuditCategory.ADMIN, page, size, targetId))

    @GetMapping("token-events")
    @Operation(summary = "Token 审计")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','audit_admin','user_center_audit_view')")
    fun tokenEvents(
        @RequestParam(defaultValue = "1") page: Long,
        @RequestParam(defaultValue = "10") size: Long,
    ): Result<PageResult<AuditEventVO>> =
        Result.ok(auditApplicationService.page(AuditCategory.TOKEN, page, size))

    @GetMapping("permission-events")
    @Operation(summary = "权限审计")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','audit_admin','user_center_audit_view')")
    fun permissionEvents(
        @RequestParam(defaultValue = "1") page: Long,
        @RequestParam(defaultValue = "10") size: Long,
        @RequestParam(required = false) targetId: Long?,
    ): Result<PageResult<AuditEventVO>> =
        Result.ok(auditApplicationService.page(AuditCategory.PERMISSION, page, size, targetId))
}
