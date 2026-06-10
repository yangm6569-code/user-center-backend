# 用户中台 API

服务统一配置了 `server.servlet.context-path=/api`，所以接口完整前缀为：

```text
/api/user-center
```

受保护接口使用：

```http
Authorization: Bearer <accessToken>
```

统一响应结构：

```json
{
  "code": "OK",
  "message": "success",
  "data": {},
  "traceId": "..."
}
```

## 认证

| 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- |
| `POST` | `/api/user-center/auth/login/password` | 账号密码登录，签发 accessToken 和 refreshToken | 否 |
| `POST` | `/api/user-center/auth/refresh` | 刷新 accessToken，并轮换 refreshToken | 否 |
| `POST` | `/api/user-center/auth/logout` | 退出登录，吊销会话 | 可选 |
| `GET` | `/api/user-center/auth/me` | 当前登录用户信息和有效权限 | 是 |
| `POST` | `/api/user-center/auth/register` | 用户自助注册，默认创建待激活账号 | 否 |

登录请求示例：

```json
{
  "username": "admin",
  "password": "Admin@123456",
  "clientId": "user-center-console",
  "rememberMe": false
}
```

## 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user-center/users/page?page=1&size=10&keyword=&status=` | 用户分页 |
| `GET` | `/api/user-center/users/detail?id=1` | 用户详情 |
| `POST` | `/api/user-center/users` | 创建用户 |
| `PUT` | `/api/user-center/users` | 修改用户资料、组织属性 |
| `POST` | `/api/user-center/users/disable?id=1` | 禁用用户并吊销会话 |
| `POST` | `/api/user-center/users/enable?id=1` | 启用用户 |
| `POST` | `/api/user-center/users/freeze?id=1` | 冻结用户 |
| `POST` | `/api/user-center/users/unfreeze?id=1` | 解冻用户 |
| `POST` | `/api/user-center/users/reset-password?id=1` | 重置密码并吊销会话 |
| `DELETE` | `/api/user-center/users?id=1` | 归档用户，逻辑删除 |
| `GET` | `/api/user-center/users/sessions?id=1` | 用户会话列表 |
| `DELETE` | `/api/user-center/users/sessions?userId=1&sessionId=1` | 强制终止会话 |

## 分组

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user-center/groups` | 分组列表，按 path 排序 |
| `POST` | `/api/user-center/groups` | 创建组织、岗位、外部或自定义分组 |
| `PUT` | `/api/user-center/groups` | 修改分组 |
| `POST` | `/api/user-center/groups/members?groupId=1&userId=1` | 添加分组成员 |
| `DELETE` | `/api/user-center/groups/members?groupId=1&userId=1` | 移除分组成员 |

## 接入应用

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user-center/apps` | 接入应用列表 |
| `POST` | `/api/user-center/apps` | 创建接入应用和客户端配置 |
| `PUT` | `/api/user-center/apps` | 修改接入应用 |
| `POST` | `/api/user-center/apps/rotate-secret?id=1` | 轮换客户端密钥，明文只返回一次 |

## 授权

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user-center/authorization/roles` | 角色列表 |
| `POST` | `/api/user-center/authorization/roles` | 创建平台或应用角色 |
| `POST` | `/api/user-center/authorization/user-roles?userId=1` | 给用户绑定角色 |
| `DELETE` | `/api/user-center/authorization/user-roles?userId=1&roleId=1` | 解绑用户角色 |
| `POST` | `/api/user-center/authorization/group-roles?groupId=1&roleId=1` | 给分组绑定角色 |
| `GET` | `/api/user-center/authorization/effective` | 当前用户有效权限 |
| `GET` | `/api/user-center/authorization/users/effective?userId=1` | 指定用户有效权限 |
| `POST` | `/api/user-center/authorization/resources` | 创建应用资源 |
| `POST` | `/api/user-center/authorization/policies` | 创建权限策略 |
| `POST` | `/api/user-center/authorization/permissions` | 创建角色权限授权 |

角色范围：

| 值 | 说明 |
| --- | --- |
| `PLATFORM` | 平台级角色 |
| `APPLICATION` | 应用级角色 |

资源类型：

```text
MENU, API, DATA, REPORT
```

授权效果：

```text
ALLOW, DENY
```

## 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user-center/audit/login-events?page=1&size=10` | 登录审计 |
| `GET` | `/api/user-center/audit/admin-events?page=1&size=10` | 管理操作审计 |
| `GET` | `/api/user-center/audit/token-events?page=1&size=10` | Token 签发/刷新审计 |
| `GET` | `/api/user-center/audit/permission-events?page=1&size=10` | 权限变更审计 |

## 内置权限

启动引导会创建平台管理角色和权限：

```text
platform_super_admin
security_admin
audit_admin
user_admin
app_admin
user_center_user_manage
user_center_user_view
user_center_role_manage
user_center_permission_manage
user_center_audit_view
user_center_app_manage
```

接口使用 Spring Method Security 的 `@PreAuthorize` 做权限控制，未依赖 Keycloak。
