package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.error.BusinessException
import cn.scysn.common.error.ErrorCode
import cn.scysn.iam.application.AuthApplicationService
import cn.scysn.iam.application.ChangePasswordCommand
import cn.scysn.iam.application.LoginCommand
import cn.scysn.iam.application.RefreshTokenCommand
import cn.scysn.iam.application.SsoCookieRenewal
import cn.scysn.iam.domain.auth.TokenSet
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

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
        writeSsoCookie(servletResponse, result.ssoSessionId, result.ssoSessionExpiresIn)
        return ApiResponse.ok(result)
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新访问令牌", description = "使用 Refresh Token 换取新的 Access Token。当前实现会使旧 Refresh Token 失效。")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
        servletResponse: HttpServletResponse,
    ): ApiResponse<Any> {
        val tokenSet = authApplicationService.refresh(RefreshTokenCommand(request.clientId, request.refreshToken))
        authApplicationService.renewSsoSession(currentUserProvider.currentSsoSessionId())
            ?.let { writeSsoCookie(servletResponse, it) }
        return ApiResponse.ok(tokenSet)
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
    @Operation(summary = "获取当前用户", description = "返回当前登录用户的基础资料、状态和必需操作。需要有效 Bearer Token、X-User-Id 或 SSO 登录态。")
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
    @Value("\${user-center.sso.login-url:http://localhost:5173/#/login}")
    private val ssoLoginUrl: String,
) {
    @GetMapping("/authorize")
    @Operation(summary = "OAuth 授权码跳转", description = "兼容 OAuth2 authorization_code 流程，校验 response_type 后重定向到 redirect_uri 并携带 code。")
    fun authorize(
        @Parameter(description = "客户端 ID") @RequestParam("client_id") clientId: String,
        @Parameter(description = "授权成功后的回调地址") @RequestParam("redirect_uri") redirectUri: String,
        @Parameter(description = "响应类型，当前仅支持 code") @RequestParam("response_type") responseType: String,
        @Parameter(description = "授权范围") @RequestParam("scope", required = false) scope: String?,
        @Parameter(description = "客户端透传状态值") @RequestParam("state", required = false) state: String?,
        @Parameter(description = "OIDC nonce，防止重放攻击") @RequestParam("nonce", required = false) nonce: String?,
        servletResponse: HttpServletResponse,
    ): ResponseEntity<Void> {
        val currentSsoSessionId = currentUserProvider.currentSsoSessionId()
        val code = try {
            authApplicationService.authorize(
                ssoSessionId = currentSsoSessionId,
                clientId = clientId,
                redirectUri = redirectUri,
                responseType = responseType,
                scope = scope,
                nonce = nonce
            )
        } catch (e: BusinessException) {
            if (e.errorCode == ErrorCode.UNAUTHORIZED) {
                return ResponseEntity.status(302)
                    .location(URI.create(loginLocation(ssoLoginUrl, clientId, redirectUri, responseType, scope, state, nonce)))
                    .build()
            }
            if (e.errorCode == ErrorCode.FORBIDDEN) {
                return ResponseEntity.status(302).location(URI.create(errorLocation(redirectUri, "access_denied", e.message, state))).build()
            }
            throw e
        }
        authApplicationService.renewSsoSession(currentSsoSessionId)
            ?.let { writeSsoCookie(servletResponse, it) }
        val separator = if (redirectUri.contains("?")) "&" else "?"
        val location = "$redirectUri${separator}code=${code.code}${state?.let { "&state=$it" } ?: ""}"
        return ResponseEntity.status(302).location(URI.create(location)).build()
    }

    @PostMapping("/token", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    @Operation(summary = "OAuth/OIDC 表单换令牌", description = "兼容标准 OIDC 客户端使用 form-urlencoded 调用 token endpoint。")
    fun tokenForm(
        @RequestParam request: Map<String, String>,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): Map<String, Any?> {
        return exchangeToken(request, authorization)
    }

    @PostMapping("/token", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "OAuth/OIDC JSON 换令牌", description = "兼容现有业务系统使用 JSON 调用 token endpoint。")
    fun tokenJson(
        @RequestBody request: Map<String, String>,
        @RequestHeader("Authorization", required = false) authorization: String?,
    ): Map<String, Any?> {
        return exchangeToken(request, authorization)
    }

    @GetMapping("/userinfo")
    @Operation(summary = "OIDC 用户信息", description = "根据 Authorization: Bearer access_token 返回标准 userinfo 字段。")
    fun userInfo(@RequestHeader("Authorization", required = false) authorization: String?): Map<String, Any?> {
        val token = bearerToken(authorization)
        return authApplicationService.userInfoFromAccessToken(token)
    }

    @GetMapping("/jwks")
    @Operation(summary = "OIDC JWKS 公钥", description = "返回第三方应用校验 id_token/access_token 使用的公钥。")
    fun jwks(): Any {
        return authApplicationService.jwks()
    }

    private fun exchangeToken(request: Map<String, String>, authorization: String?): Map<String, Any?> {
        val basicClient = basicClientCredentials(authorization)
        val clientId = basicClient?.first
            ?: request["client_id"]
            ?: request["clientId"]
            ?: throw IllegalArgumentException("client_id 不能为空")
        val clientSecret = basicClient?.second
            ?: request["client_secret"]
            ?: request["clientSecret"]
        val grantType = request["grant_type"] ?: request["grantType"]
        val tokenSet = when (grantType) {
            "authorization_code" -> {
                val code = request["code"] ?: throw IllegalArgumentException("code 不能为空")
                val redirectUri = request["redirect_uri"] ?: request["redirectUri"] ?: throw IllegalArgumentException("redirect_uri 不能为空")
                authApplicationService.exchangeAuthorizationCode(code, clientId, redirectUri, clientSecret)
            }
            "refresh_token" -> {
                val refreshToken = request["refresh_token"] ?: request["refreshToken"] ?: throw IllegalArgumentException("refresh_token 不能为空")
                authApplicationService.refresh(RefreshTokenCommand(clientId, refreshToken, clientSecret))
            }
            else -> throw IllegalArgumentException("grant_type 仅支持 authorization_code 或 refresh_token")
        }
        return tokenResponse(tokenSet)
    }
}

@RestController
class OidcDiscoveryController(
    private val authApplicationService: AuthApplicationService,
) {
    @GetMapping("/.well-known/openid-configuration")
    @Operation(summary = "OIDC Discovery 配置", description = "第三方应用通过该端点发现授权、换令牌、用户信息和 JWKS 地址。")
    fun openIdConfiguration(): Map<String, Any> {
        return authApplicationService.oidcConfiguration()
    }
}

private fun tokenResponse(tokenSet: TokenSet): Map<String, Any?> {
    return linkedMapOf(
        "token_type" to tokenSet.tokenType,
        "access_token" to tokenSet.accessToken,
        "expires_in" to tokenSet.expiresIn,
        "refresh_token" to tokenSet.refreshToken,
        "refresh_expires_in" to tokenSet.refreshExpiresIn,
        "id_token" to tokenSet.idToken,
        "session_id" to tokenSet.sessionId,
        "tokenType" to tokenSet.tokenType,
        "accessToken" to tokenSet.accessToken,
        "expiresIn" to tokenSet.expiresIn,
        "refreshToken" to tokenSet.refreshToken,
        "refreshExpiresIn" to tokenSet.refreshExpiresIn,
        "idToken" to tokenSet.idToken,
        "sessionId" to tokenSet.sessionId
    )
}

private fun bearerToken(authorization: String?): String {
    val prefix = "Bearer "
    return authorization
        ?.takeIf { it.startsWith(prefix, ignoreCase = true) }
        ?.substring(prefix.length)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("缺少 Authorization: Bearer access_token")
}

private fun basicClientCredentials(authorization: String?): Pair<String, String>? {
    val prefix = "Basic "
    if (authorization == null || !authorization.startsWith(prefix, ignoreCase = true)) {
        return null
    }
    val decoded = String(Base64.getDecoder().decode(authorization.substring(prefix.length).trim()), StandardCharsets.UTF_8)
    val separator = decoded.indexOf(':')
    require(separator > 0) { "Basic client 认证格式不正确" }
    return decoded.substring(0, separator) to decoded.substring(separator + 1)
}

private fun loginLocation(
    ssoLoginUrl: String,
    clientId: String,
    redirectUri: String,
    responseType: String,
    scope: String?,
    state: String?,
    nonce: String?,
): String {
    val params = linkedMapOf(
        "client_id" to clientId,
        "redirect_uri" to redirectUri,
        "response_type" to responseType
    )
    scope?.let { params["scope"] = it }
    state?.let { params["state"] = it }
    nonce?.let { params["nonce"] = it }
    val returnTo = "/oauth/authorize?" + params.entries.joinToString("&") { (key, value) ->
        "${urlEncode(key)}=${urlEncode(value)}"
    }
    val separator = if (ssoLoginUrl.contains("?")) "&" else "?"
    return "$ssoLoginUrl${separator}return_to=${urlEncode(returnTo)}"
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

private fun writeSsoCookie(response: HttpServletResponse, renewal: SsoCookieRenewal) {
    writeSsoCookie(response, renewal.sessionId, renewal.maxAgeSeconds)
}

private fun writeSsoCookie(response: HttpServletResponse, sessionId: String, maxAgeSeconds: Long) {
    response.addHeader(
        "Set-Cookie",
        "$SSO_COOKIE_NAME=$sessionId; Path=/; HttpOnly; SameSite=Lax; Max-Age=$maxAgeSeconds"
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
