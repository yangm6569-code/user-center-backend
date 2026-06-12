package cn.scysn.common.error

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val httpStatus: HttpStatus,
    val message: String,
) {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求格式错误"),
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "请求参数校验失败"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "未认证"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权限"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "数据冲突"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务异常"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    USER_DISABLED(HttpStatus.FORBIDDEN, "用户已禁用"),
    USER_FROZEN(HttpStatus.FORBIDDEN, "用户已冻结"),
    USER_ARCHIVED(HttpStatus.FORBIDDEN, "用户已归档"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "必须修改密码"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token 无效"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Refresh Token 被重复使用"),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "会话已失效"),

    USERNAME_EXISTS(HttpStatus.CONFLICT, "用户名已存在"),
    EMPLOYEE_NO_EXISTS(HttpStatus.CONFLICT, "员工编号已存在"),
    EMAIL_EXISTS(HttpStatus.CONFLICT, "邮箱已存在"),
    PHONE_EXISTS(HttpStatus.CONFLICT, "手机号已存在"),
    ROLE_CODE_EXISTS(HttpStatus.CONFLICT, "角色编码已存在"),
    PERMISSION_CODE_EXISTS(HttpStatus.CONFLICT, "权限编码已存在"),
    APP_CODE_EXISTS(HttpStatus.CONFLICT, "应用编码已存在"),
    ROLE_IN_USE(HttpStatus.CONFLICT, "角色正在使用"),
    ORG_UNIT_HAS_CHILDREN(HttpStatus.CONFLICT, "组织节点存在子节点"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "权限不足"),
}
