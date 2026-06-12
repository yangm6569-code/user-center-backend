package cn.scysn.iam.interfaces

import cn.scysn.common.api.ApiResponse
import cn.scysn.iam.application.ExportTaskResult
import cn.scysn.iam.application.ImportExportApplicationService
import cn.scysn.iam.application.ImportResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "导入导出", description = "用户批量导入和审计日志导出任务接口")
class ImportExportController(
    private val importExportApplicationService: ImportExportApplicationService,
) {
    @PostMapping("/imports/users")
    @Operation(summary = "导入用户", description = "批量校验或导入用户数据，支持 validate_only 和 import 模式。")
    fun importUsers(@Valid @RequestBody request: ImportUsersRequest): ApiResponse<ImportResult> {
        return ApiResponse.ok(importExportApplicationService.importUsers(request.toCommand()))
    }

    @PostMapping("/exports/audit-events")
    @Operation(summary = "导出审计日志", description = "创建审计日志导出任务，支持 xlsx 和 csv 格式。")
    fun exportAuditEvents(@Valid @RequestBody request: ExportAuditRequest): ApiResponse<ExportTaskResult> {
        return ApiResponse.ok(importExportApplicationService.exportAuditEvents(request.toCommand()))
    }
}
