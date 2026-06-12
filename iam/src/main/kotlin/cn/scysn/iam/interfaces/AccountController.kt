package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.AccountApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "账号自助中心", description = "当前用户资料查看、资料更新和登录历史查询接口")
class AccountController(
    private val accountApplicationService: AccountApplicationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/profile")
    @Operation(summary = "查看我的资料", description = "查询当前登录账号的用户资料。")
    fun profile(): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(accountApplicationService.profile(currentUserProvider.userId()).toDetailResponse())
    }

    @PatchMapping("/profile")
    @Operation(summary = "更新我的资料", description = "当前账号自助更新姓名、邮箱和手机号。")
    fun updateProfile(@RequestBody request: UpdateAccountProfileRequest): ApiResponse<UserDetailResponse> {
        return ApiResponse.ok(
            accountApplicationService.updateProfile(
                currentUserProvider.userId(),
                request.displayName,
                request.email,
                request.phone
            ).toDetailResponse()
        )
    }

    @GetMapping("/login-history")
    @Operation(summary = "查询我的登录历史", description = "分页查询当前账号的登录审计记录。")
    fun loginHistory(
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<AuditEventResponse>> {
        return ApiResponse.ok(
            accountApplicationService.loginHistory(currentUserProvider.userId(), PageQuery(page, pageSize))
                .mapItems { it.toResponse() }
        )
    }
}
