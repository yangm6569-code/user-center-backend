package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.common.api.PageResponse
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.ClientAppApplicationService
import cn.scysn.iam.application.SystemDomainApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/apps")
@Tag(name = "应用接入", description = "客户端应用分页查询、创建、维护和密钥轮换接口")
class ClientAppController(
    private val clientAppApplicationService: ClientAppApplicationService,
) {
    @GetMapping
    @Operation(summary = "分页查询应用", description = "按关键字和应用状态分页查询接入应用。")
    fun page(
        @Parameter(description = "系统域 ID") @RequestParam(required = false) domainId: String?,
        @Parameter(description = "关键字，匹配客户端 ID 或应用名称") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "应用状态，如 active、disabled") @RequestParam(required = false) status: String?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<ClientAppResponse>> {
        return ApiResponse.ok(
            clientAppApplicationService.page(domainId, keyword, status, PageQuery(page, pageSize))
                .mapItems { it.toResponse() }
        )
    }

    @PostMapping
    @Operation(summary = "创建接入应用", description = "创建客户端应用并生成客户端密钥。")
    fun create(@Valid @RequestBody request: CreateClientAppRequest): ApiResponse<CreateClientAppResponse> {
        return ApiResponse.ok(clientAppApplicationService.create(request.toCommand()).toResponse())
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新接入应用", description = "更新应用名称、类型、负责人部门、回调地址、退出地址、令牌策略和状态。")
    fun update(@PathVariable id: String, @RequestBody request: UpdateClientAppRequest): ApiResponse<ClientAppResponse> {
        return ApiResponse.ok(clientAppApplicationService.update(id, request.toCommand()).toResponse())
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新接入应用", description = "兼容 PUT 方法更新应用设置，逻辑与 PATCH 更新一致。")
    fun updateByPut(@PathVariable id: String, @RequestBody request: UpdateClientAppRequest): ApiResponse<ClientAppResponse> {
        return update(id, request)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除接入应用", description = "根据应用 ID 删除接入应用。")
    fun delete(@PathVariable id: String): ResponseEntity<Void> {
        clientAppApplicationService.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/rotate-secret")
    @Operation(summary = "轮换应用密钥", description = "为接入应用生成新密钥，并设置旧密钥宽限期。")
    fun rotateSecret(@PathVariable id: String, @Valid @RequestBody request: RotateSecretRequest): ApiResponse<RotateSecretResponse> {
        return ApiResponse.ok(clientAppApplicationService.rotateSecret(id, request.toCommand()).toResponse())
    }
}

@RestController
@RequestMapping("/api/v1/system-domains")
@Tag(name = "系统域管理", description = "业务系统域创建、维护和查询接口")
class SystemDomainController(
    private val systemDomainApplicationService: SystemDomainApplicationService,
) {
    @GetMapping
    @Operation(summary = "分页查询系统域", description = "按关键字和启用状态分页查询系统域。")
    fun page(
        @Parameter(description = "关键字，匹配编码或名称") @RequestParam(required = false) keyword: String?,
        @Parameter(description = "是否启用") @RequestParam(required = false) enabled: Boolean?,
        @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") page: Int,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") pageSize: Int,
    ): ApiResponse<PageResponse<SystemDomainResponse>> {
        return ApiResponse.ok(
            systemDomainApplicationService.page(keyword, enabled, PageQuery(page, pageSize))
                .mapItems { it.toResponse() }
        )
    }

    @PostMapping
    @Operation(summary = "创建系统域", description = "创建一个业务系统域，例如 MES。")
    fun create(@Valid @RequestBody request: CreateSystemDomainRequest): ApiResponse<SystemDomainResponse> {
        return ApiResponse.ok(systemDomainApplicationService.create(request.toCommand()).toResponse())
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新系统域", description = "更新系统域名称、描述和启用状态。")
    fun update(@PathVariable id: String, @RequestBody request: UpdateSystemDomainRequest): ApiResponse<SystemDomainResponse> {
        return ApiResponse.ok(systemDomainApplicationService.update(id, request.toCommand()).toResponse())
    }
}
