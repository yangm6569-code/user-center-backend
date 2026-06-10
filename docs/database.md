# 用户中台数据库设计

数据库使用 PostgreSQL，Schema 由 Flyway 管理：

```text
app/src/main/resources/db/migration/V1__init_user_center_schema.sql
```

当前实现是用户中台自持账号、会话、角色、资源、权限和审计数据，不依赖 Keycloak 表结构，也不保存 Keycloak 用户 ID。

## 核心表

| 表名 | 说明 |
| --- | --- |
| `uc_user_account` | 用户账号、密码哈希、生命周期状态、组织属性 |
| `uc_access_application` | 接入应用、客户端配置、回调地址、Token TTL、密钥哈希 |
| `uc_role` | 平台角色和应用角色 |
| `uc_group` | 组织、岗位、外部和自定义分组 |
| `uc_user_group` | 用户与分组关系 |
| `uc_user_role` | 用户直接角色授权，支持过期时间 |
| `uc_group_role` | 分组角色授权，用于继承权限 |
| `uc_resource` | 应用资源，支持菜单、API、数据、报表 |
| `uc_policy` | ABAC、时间、IP、组合策略定义 |
| `uc_permission` | 角色对资源、范围、策略的授权 |
| `uc_login_session` | 登录会话和 refreshToken 哈希 |
| `uc_mfa_credential` | MFA/WebAuthn 凭据引用，预留二次认证能力 |
| `uc_audit_event` | 登录、Token、用户、权限、管理操作等审计事件 |

## 用户账号

`uc_user_account` 关键字段：

| 字段 | 说明 |
| --- | --- |
| `username` | 登录账号，唯一 |
| `email` | 邮箱，唯一 |
| `phone` | 手机号，唯一 |
| `employee_no` | 工号，唯一 |
| `password_hash` | BCrypt 密码哈希 |
| `status` | `PENDING`、`ACTIVE`、`DISABLED`、`ARCHIVED` |
| `account_type` | `EMPLOYEE`、`SUPPLIER`、`CUSTOMER`、`SERVICE` |
| `factory_code` | 工厂编码，用于数据权限 |
| `department_code` | 部门编码，用于数据权限 |
| `position_code` | 岗位编码，用于数据权限 |
| `failed_login_count` | 连续登录失败次数 |
| `locked_until` | 爆破保护锁定时间 |
| `freeze_until` | 业务冻结到期时间 |
| `archived_at` | 归档时间，逻辑删除使用 |

## 权限模型

有效权限计算链路：

```text
用户
  -> uc_user_role 直接角色
  -> uc_user_group + uc_group_role 继承角色
  -> uc_permission 权限
  -> uc_resource 资源
  -> uc_policy 可选策略
```

热点索引：

| 索引/约束 | 用途 |
| --- | --- |
| `idx_uc_user_account_status` | 用户状态筛选 |
| `idx_uc_user_account_factory_department` | 工厂/部门数据权限筛选 |
| `ux_uc_role_app_code` | 同一应用下角色编码唯一 |
| `idx_uc_permission_role` | 按角色计算权限 |
| `idx_uc_login_session_user_status` | 查询用户有效会话 |
| `idx_uc_audit_event_category_created` | 审计分类分页 |
| `idx_uc_audit_event_actor_created` | 按操作人查询审计 |
| `idx_uc_audit_event_target_created` | 按目标对象查询审计 |

## 迁移约定

生产环境配置 `spring.jpa.hibernate.ddl-auto=validate`，表结构只通过 Flyway 迁移变更。

已经在共享环境执行过的迁移文件不要修改；后续变更新增 `V2__xxx.sql`、`V3__xxx.sql`。
