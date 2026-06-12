package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.UserApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理", description = "用户分页查询、创建、详情、资料维护、状态流转、密码重置和用户会话管理接口")
class UserController(
    private val userApplicationService: UserApplicationService,
) {
    @GetMapping
    @Operation(summary = "分页查询用户", description = "按关键字、用户状态、组织节点分页查询用户列表。")
    fun page(
        @Parameter(description = "关键字，匹配用户名、姓名等信息") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "用户状态，如 active、disabled、frozen、archived") @RequestParam(required = false) status: String?,
        @Parameter(description = "组织节点 ID") @RequestParam(required = false) orgUnitId: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<UserSummaryResponse>> {
        return ApiResponse.ok(
            userApplicationService.page(keyword, status, orgUnitId, PageQuery(page, pageSize))
                .mapItems { it.toSummaryResponse() }
        )
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "创建用户账号、初始化密码和基础资料。")
    fun create(@Valid @RequestBody request: CreateUserRequest): ApiResponse<CreateUserResponse> {
        return ApiResponse.ok(userApplicationService.create(request.toCommand()).toCreateResponse())
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情", description = "根据用户 ID 查询用户基础资料、组织、角色和扩展属性。")
    fun detail(@PathVariable id: String): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.detail(id).toDetailResponse())
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新用户资料", description = "更新用户姓名、邮箱、手机号、组织、工厂/部门等资料。")
    fun update(@PathVariable id: String, @RequestBody request: UpdateUserRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.update(id, request.toCommand()).toDetailResponse())
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用用户", description = "将禁用、冻结等状态的用户恢复为可用状态。")
    fun enable(@PathVariable id: String, @Valid @RequestBody request: ReasonRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.enable(id, request.reason).toDetailResponse())
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用用户", description = "禁用用户账号，并可选择同时注销其活跃会话。")
    fun disable(@PathVariable id: String, @Valid @RequestBody request: DisableUserRequest): ApiResponse<Map<String, Any>> {
        return ApiResponse.ok(userApplicationService.disable(id, request.toCommand()))
    }

    @PostMapping("/{id}/freeze")
    @Operation(summary = "冻结用户", description = "冻结用户到指定时间，并可选择同时注销其活跃会话。")
    fun freeze(@PathVariable id: String, @Valid @RequestBody request: FreezeUserRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.freeze(id, request.toCommand()).toDetailResponse())
    }

    @PostMapping("/{id}/unfreeze")
    @Operation(summary = "解冻用户", description = "解除用户冻结状态。")
    fun unfreeze(@PathVariable id: String, @Valid @RequestBody request: ReasonRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.unfreeze(id, request.reason).toDetailResponse())
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档用户", description = "归档用户账号，并可选择同时注销其活跃会话。")
    fun archive(@PathVariable id: String, @Valid @RequestBody request: ArchiveUserRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(userApplicationService.archive(id, request.toCommand()).toDetailResponse())
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置用户密码", description = "管理员重置指定用户密码，支持临时密码或重置链接模式。")
    fun resetPassword(@PathVariable id: String, @Valid @RequestBody request: ResetPasswordRequest): ApiResponse<Map<String, Any>> {
        return ApiResponse.ok(
            userApplicationService.resetPassword(
                id,
                cn.scysn.iam.application.ResetPasswordCommand(
                    mode = request.mode,
                    temporaryPassword = request.temporaryPassword,
                    forceChangePassword = request.forceChangePassword,
                    reason = request.reason
                )
            )
        )
    }

    @GetMapping("/{id}/sessions")
    @Operation(summary = "查询用户会话", description = "查询指定用户的登录会话列表。")
    fun sessions(@PathVariable id: String): ApiResponse<List<SessionResponse>> {
        return ApiResponse.ok(userApplicationService.sessions(id).map { it.toResponse() })
    }

    @DeleteMapping("/{id}/sessions/{sessionId}")
    @Operation(summary = "终止用户会话", description = "管理员终止指定用户的某个登录会话。")
    fun terminateSession(@PathVariable id: String, @PathVariable sessionId: String): ResponseEntity<Void> {
        userApplicationService.terminateUserSession(id, sessionId)
        return ResponseEntity.noContent().build()
    }
}
