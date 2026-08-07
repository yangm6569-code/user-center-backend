package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.AuditApplicationService
import cn.scysn.iam.domain.ports.AuditSearchQuery
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "审计日志", description = "登录、管理操作、令牌、权限和风险事件审计查询接口")
class AuditController(
    private val auditApplicationService: AuditApplicationService,
) {
    @GetMapping("/login-events")
    @Operation(summary = "查询登录审计", description = "分页查询登录成功、登录失败、退出登录等登录类审计事件。")
    fun loginEvents(
        @Parameter(description = "用户 ID") @RequestParam(required = false) userId: String?,
        @Parameter(description = "执行结果，如 success、failure") @RequestParam(required = false) result: String?,
        @Parameter(description = "开始时间，ISO-8601 格式") @RequestParam(required = false) startTime: OffsetDateTime?,
        @Parameter(description = "结束时间，ISO-8601 格式") @RequestParam(required = false) endTime: OffsetDateTime?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return events(AuditSearchQuery("login", userId = userId, result = result, startTime = startTime, endTime = endTime, page = PageQuery(page, pageSize)))
    }

    @GetMapping("/admin-events")
    @Operation(summary = "查询管理操作审计", description = "分页查询管理员操作类审计事件。")
    fun adminEvents(
        @Parameter(description = "操作人用户 ID") @RequestParam(required = false) actorId: String?,
        @Parameter(description = "事件类型") @RequestParam(required = false) eventType: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return events(AuditSearchQuery("admin", eventCategories = ADMIN_AUDIT_CATEGORIES, actorId = actorId, eventType = eventType, page = PageQuery(page, pageSize)))
    }

    @GetMapping("/token-events")
    @Operation(summary = "查询令牌审计", description = "分页查询令牌刷新、令牌失效等令牌类审计事件。")
    fun tokenEvents(
        @Parameter(description = "用户 ID") @RequestParam(required = false) userId: String?,
        @Parameter(description = "事件类型") @RequestParam(required = false) eventType: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return events(AuditSearchQuery("token", userId = userId, eventType = eventType, page = PageQuery(page, pageSize)))
    }

    @GetMapping("/permission-events")
    @Operation(summary = "查询权限审计", description = "分页查询角色授权、权限绑定等权限类审计事件。")
    fun permissionEvents(
        @Parameter(description = "目标对象 ID，如用户 ID、角色 ID") @RequestParam(required = false) targetId: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return events(AuditSearchQuery("permission", targetId = targetId, page = PageQuery(page, pageSize)))
    }

    @GetMapping("/risk-events")
    @Operation(summary = "查询风险事件", description = "分页查询风险类审计事件。")
    fun riskEvents(
        @Parameter(description = "风险类型") @RequestParam(required = false) riskType: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return events(AuditSearchQuery("risk", riskType = riskType, page = PageQuery(page, pageSize)))
    }

    private fun events(query: AuditSearchQuery): ApiResponse<PageResponse<AuditEventResponse>> {
        return ApiResponse.ok(auditApplicationService.events(query).mapItems { it.toResponse() })
    }
}

private val ADMIN_AUDIT_CATEGORIES = setOf("admin", "user", "app", "org", "system", "session")
