package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.SessionApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "会话管理", description = "后台会话查询、会话终止和当前账号会话自助管理接口")
class SessionController(
    private val sessionApplicationService: SessionApplicationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/sessions")
    @Operation(summary = "分页查询会话", description = "按用户、客户端应用和会话状态分页查询登录会话。")
    fun sessions(
        @Parameter(description = "用户 ID") @RequestParam(required = false) userId: String?,
        @Parameter(description = "客户端 ID") @RequestParam(required = false) clientId: String?,
        @Parameter(description = "会话状态，如 active、revoked、expired") @RequestParam(required = false) status: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<SessionResponse>> {
        return ApiResponse.ok(
            sessionApplicationService.page(userId, clientId, status, PageQuery(page, pageSize))
                .mapItems { it.toResponse() }
        )
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "终止会话", description = "管理员终止指定登录会话。")
    fun terminate(@PathVariable id: String): ResponseEntity<Void> {
        sessionApplicationService.terminate(id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/account/sessions")
    @Operation(summary = "查询我的会话", description = "查询当前账号的全部登录会话。")
    fun accountSessions(): ApiResponse<List<SessionResponse>> {
        return ApiResponse.ok(sessionApplicationService.currentUserSessions(currentUserProvider.userId()).map { it.toResponse() })
    }

    @DeleteMapping("/account/sessions/{id}")
    @Operation(summary = "终止我的会话", description = "当前账号自助终止自己的某个登录会话。")
    fun terminateAccountSession(@PathVariable id: String): ResponseEntity<Void> {
        sessionApplicationService.terminateCurrentUserSession(currentUserProvider.userId(), id)
        return ResponseEntity.noContent().build()
    }
}
