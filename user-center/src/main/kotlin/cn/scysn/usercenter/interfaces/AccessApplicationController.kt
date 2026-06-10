package cn.scysn.usercenter.interfaces

import cn.scysn.common.base.domain.Result
import cn.scysn.usercenter.application.AccessApplicationApplicationService
import cn.scysn.usercenter.application.RequestMetadata
import cn.scysn.usercenter.infrastructure.security.currentLoginPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "用户中台-接入应用")
@RestController
@RequestMapping("user-center/apps")
class AccessApplicationController(
    private val accessApplicationApplicationService: AccessApplicationApplicationService,
) {
    @GetMapping
    @Operation(summary = "接入应用列表")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_app_manage')")
    fun list(): Result<List<AccessApplicationVO>> =
        Result.ok(accessApplicationApplicationService.list())

    @PostMapping
    @Operation(summary = "创建接入应用")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_app_manage')")
    fun create(@RequestBody create: AccessApplicationCreateDTO, servletRequest: HttpServletRequest): Result<Long> =
        Result.ok(accessApplicationApplicationService.create(create, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest)))

    @PutMapping
    @Operation(summary = "修改接入应用")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','app_admin','user_center_app_manage')")
    fun update(@RequestBody update: AccessApplicationUpdateDTO, servletRequest: HttpServletRequest): Result<String> {
        accessApplicationApplicationService.update(update, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest))
        return Result.ok("修改成功")
    }

    @PostMapping("rotate-secret")
    @Operation(summary = "轮换应用密钥")
    @PreAuthorize("hasAnyAuthority('platform_super_admin','security_admin','app_admin','user_center_app_manage')")
    fun rotateSecret(@RequestParam id: Long, servletRequest: HttpServletRequest): Result<RotatedSecretVO> =
        Result.ok(accessApplicationApplicationService.rotateSecret(id, currentLoginPrincipal().userId, RequestMetadata.from(servletRequest)))
}
