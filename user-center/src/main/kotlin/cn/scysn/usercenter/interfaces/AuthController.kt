package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.AuthApplicationService
import cn.scysn.usercenter.application.RequestMetadata
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*

@Tag(name = "用户中台-认证")
@RestController
@RequestMapping("user-center/auth")
class AuthController(
    private val authApplicationService: AuthApplicationService,
) {
    @PostMapping("login/password")
    @Operation(summary = "账号密码登录")
    fun loginByPassword(
        @RequestBody request: LoginByPasswordDTO,
        servletRequest: HttpServletRequest,
    ): Result<LoginTokenVO> =
        Result.ok(authApplicationService.login(request, RequestMetadata.from(servletRequest)))

    @PostMapping("refresh")
    @Operation(summary = "刷新Token")
    fun refresh(
        @RequestBody request: RefreshTokenDTO,
        servletRequest: HttpServletRequest,
    ): Result<LoginTokenVO> =
        Result.ok(authApplicationService.refresh(request, RequestMetadata.from(servletRequest)))

    @PostMapping("logout")
    @Operation(summary = "退出登录")
    fun logout(
        @RequestBody(required = false) request: LogoutDTO?,
        servletRequest: HttpServletRequest,
    ): Result<String> {
        val principal = runCatching { currentLoginPrincipal() }.getOrNull()
        authApplicationService.logout(request ?: LogoutDTO(), principal, RequestMetadata.from(servletRequest))
        return Result.ok("退出成功")
    }

    @GetMapping("me")
    @Operation(summary = "当前登录用户")
    fun me(): Result<CurrentUserVO> =
        Result.ok(authApplicationService.me(currentLoginPrincipal()))

    @PostMapping("register")
    @Operation(summary = "用户自助注册")
    fun register(
        @RequestBody request: UserAccountRegisterDTO,
        servletRequest: HttpServletRequest,
    ): Result<UserAccountVO> =
        Result.ok(authApplicationService.register(request, RequestMetadata.from(servletRequest)))
}
