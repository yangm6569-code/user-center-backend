package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.error.BusinessException
import cn.scysn.common.error.ErrorCode
import cn.scysn.iam.application.AuthApplicationService
import cn.scysn.iam.application.ChangePasswordCommand
import cn.scysn.iam.application.LoginCommand
import cn.scysn.iam.application.RefreshTokenCommand
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Validated
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证管理", description = "登录、刷新令牌、退出登录、当前用户、密码找回与密码修改接口")
class AuthController(
    private val authApplicationService: AuthApplicationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @PostMapping("/login")
    @Operation(summary = "账号密码登录", description = "使用用户名、密码和客户端 ID 登录，成功后返回访问令牌、刷新令牌、会话 ID 和当前用户摘要。")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
    ): ApiResponse<Any> {
        val result = authApplicationService.login(
            LoginCommand(
                username = request.username,
                password = request.password,
                clientId = request.clientId,
                deviceId = request.deviceId,
                rememberMe = request.rememberMe,
                ip = servletRequest.remoteAddr,
                userAgent = servletRequest.getHeader("User-Agent")
            )
        )
        writeSsoCookie(servletResponse, result.ssoSessionId)
        return ApiResponse.ok(result)
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌", description = "使用 Refresh Token 换取新的 Access Token。当前实现会使旧 Refresh Token 失效。")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ApiResponse<Any> {
        return ApiResponse.ok(authApplicationService.refresh(RefreshTokenCommand(request.clientId, request.refreshToken)))
    }

    @PostMapping("/logout")
    @Operation(summary = "退出当前会话", description = "根据 Refresh Token 注销对应登录会话。")
    fun logout(@Valid @RequestBody request: LogoutRequest): ApiResponse<Map<String, Any>> {
        return ApiResponse.ok(authApplicationService.logout(request.refreshToken))
    }

    @PostMapping("/logout-all")
    @Operation(summary = "退出全部会话", description = "注销当前用户的全部活跃会话，可填写退出原因。")
    fun logoutAll(@RequestBody request: LogoutAllRequest, servletResponse: HttpServletResponse): ApiResponse<Map<String, Int>> {
        expireSsoCookie(servletResponse)
        return ApiResponse.ok(authApplicationService.logoutAll(currentUserProvider.userId(), request.reason))
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户", description = "返回当前登录用户的基础资料、状态和必需操作。开发环境默认当前用户为 user-001。")
    fun me(): ApiResponse<Any> {
        return ApiResponse.ok(authApplicationService.currentUser(currentUserProvider.userId()))
    }

    @GetMapping("/jwks")
    @Operation(summary = "获取 JWKS 公钥", description = "返回 JWT 验签使用的 JSON Web Key Set。")
    fun jwks(): Any {
        return authApplicationService.jwks()
    }

    @PostMapping("/password/change")
    @Operation(summary = "修改当前用户密码", description = "校验旧密码后修改当前用户密码，并退出其它会话。")
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest): ApiResponse<Map<String, Boolean>> {
        return ApiResponse.ok(
            authApplicationService.changePassword(
                ChangePasswordCommand(
                    userId = currentUserProvider.userId(),
                    oldPassword = request.oldPassword,
                    newPassword = request.newPassword,
                    confirmPassword = request.confirmPassword
                )
            )
        )
    }

    @PostMapping("/password/reset-request")
    @Operation(summary = "发起密码重置", description = "提交账号、邮箱或客户端信息，发起密码重置流程。当前版本返回受理结果。")
    fun resetRequest(@RequestBody request: PasswordResetRequest): ApiResponse<Map<String, Boolean>> {
        return ApiResponse.ok(authApplicationService.requestPasswordReset(), "如果账号存在，系统将发送密码重置通知")
    }

    @PostMapping("/password/reset-confirm")
    @Operation(summary = "确认密码重置", description = "使用重置令牌提交新密码并确认。")
    fun resetConfirm(@Valid @RequestBody request: PasswordResetConfirmRequest): ApiResponse<Map<String, Boolean>> {
        return ApiResponse.ok(
            authApplicationService.confirmPasswordReset(
                request.resetToken,
                request.newPassword,
                request.confirmPassword
            )
        )
    }
}

@RestController
@RequestMapping("/oauth")
@Tag(name = "OAuth 兼容接口", description = "面向旧系统或第三方系统的 OAuth2 兼容授权、换令牌和用户信息接口")
class OAuthCompatibilityController(
    private val authApplicationService: AuthApplicationService,
    private val currentUserProvider: CurrentUserProvider,
) {
    @GetMapping("/authorize")
    @Operation(summary = "OAuth 授权码跳转", description = "兼容 OAuth2 authorization_code 流程，校验 response_type 后重定向到 redirect_uri 并携带 code。")
    fun authorize(
        @Parameter(description = "客户端 ID") @RequestParam("client_id") clientId: String,
        @Parameter(description = "授权成功后的回调地址") @RequestParam("redirect_uri") redirectUri: String,
        @Parameter(description = "响应类型，当前仅支持 code") @RequestParam("response_type") responseType: String,
        @Parameter(description = "授权范围") @RequestParam("scope", required = false) scope: String?,
        @Parameter(description = "客户端透传状态值") @RequestParam("state", required = false) state: String?,
    ): ResponseEntity<Void> {
        val code = try {
            authApplicationService.authorize(
                ssoSessionId = currentUserProvider.currentSsoSessionId(),
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = responseType,
                scope = scope
            )
        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.UNAUTHORIZED) {
                return ResponseEntity.status(302).location(URI.create(loginLocation(clientId, redirectUri, responseType, scope, state))).build()
            }
            if (e.errorCode == ErrorCode.FORBIDDEN) {
                return ResponseEntity.status(302).location(URI.create(errorLocation(redirectUri, "access_denied", e.message, state))).build()
            }
            throw e
        }
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val location = "$redirectUri${separator}code=${code.code}${state?.let { "&state=$it" } ?: ""}"
        return ResponseEntity.status(302).location(URI.create(location)).build()
    }

    @PostMapping("/token")
    @Operation(summary = "OAuth 授权码换令牌", description = "使用授权码换取 JWT 访问令牌和刷新令牌。")
    fun token(@RequestBody request: Map<String, String>): Map<String, Any> {
        val grantType = request["grant_type"] ?: request["grantType"]
        require(grantType == "authorization_code") { "首版仅支持 authorization_code" }
        val code = request["code"] ?: throw IllegalArgumentException("code 不能为空")
        val clientId = request["client_id"] ?: request["clientId"] ?: throw IllegalArgumentException("client_id 不能为空")
        val redirectUri = request["redirect_uri"] ?: request["redirectUri"] ?: throw IllegalArgumentException("redirect_uri 不能为空")
        val tokenSet = authApplicationService.exchangeAuthorizationCode(code, clientId, redirectUri)
        return mapOf(
            "tokenType" to tokenSet.tokenType,
            "accessToken" to tokenSet.accessToken,
            "expiresIn" to tokenSet.expiresIn,
            "refreshToken" to tokenSet.refreshToken,
            "refreshExpiresIn" to tokenSet.refreshExpiresIn,
            "sessionId" to tokenSet.sessionId
        )
    }

    @GetMapping("/userinfo")
    @Operation(summary = "OAuth 用户信息", description = "返回当前用户的 OAuth userinfo 兼容字段。")
    fun userInfo(): Map<String, String> {
        val userId = currentUserProvider.userId()
        return mapOf(
            "sub" to userId,
            "preferred_username" to userId,
            "name" to userId,
            "employee_no" to ""
        )
    }
}

private fun loginLocation(clientId: String, redirectUri: String, responseType: String, scope: String?, state: String?): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to responseType
    )
    scope?.let { params["scope"] = it }
    state?.let { params["state"] = it }
    val returnTo = "/oauth/authorize?" + params.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
    return "/sso-login.html?return_to=${urlEncode(returnTo)}"
}

private fun errorLocation(redirectUri: String, error: String, description: String, state: String?): String {
    val separator = if (redirectUri.contains("?")) "&" else "?"
    val params = linkedMapOf(
        "error" to error,
        "error_description" to description
    )
    state?.let { params["state"] = it }
    return redirectUri + separator + params.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
}

private fun urlEncode(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
}

private const val SSO_COOKIE_NAME = "UC_SSO_SESSION"

private fun writeSsoCookie(response: HttpServletResponse, sessionId: String) {
    response.addHeader(
        "Set-Cookie",
        "$SSO_COOKIE_NAME=$sessionId; Path=/; HttpOnly; SameSite=Lax; Max-Age=43200"
    )
}

private fun expireSsoCookie(response: HttpServletResponse) {
    response.addHeader(
        "Set-Cookie",
        "$SSO_COOKIE_NAME=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0"
    )
}

private fun CurrentUserProvider.currentSsoSessionId(): String? {
    return cookie(SSO_COOKIE_NAME)
}
