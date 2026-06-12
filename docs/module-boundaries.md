# 用户中台后端模块边界

## 工程模块

| 模块 | 职责 |
| --- | --- |
| `app` | Spring Boot 启动入口与运行配置 |
| `common` | 统一响应、分页、TraceId、错误码、全局异常处理 |
| `iam` | 用户中台业务模块，内部按领域继续分层 |

## `iam` 内部分层

| 分层 | 包 | 职责 |
| --- | --- | --- |
| 领域层 | `cn.scysn.iam.domain` | 充血模型、领域行为、仓储端口 |
| 应用层 | `cn.scysn.iam.application` | 用例编排、唯一性检查、审计记录、事务边界预留 |
| 接口层 | `cn.scysn.iam.interfaces` | REST Controller、请求/响应 DTO、防腐转换 |
| 基础设施层 | `cn.scysn.iam.infrastructure` | 仓储适配器、启动初始化数据 |

## 首版保留功能

| 功能域 | 对应接口 |
| --- | --- |
| 认证 Token | `/api/v1/auth/**`、`/oauth/**` |
| 用户生命周期 | `/api/v1/users/**` |
| 组织树 | `/api/v1/org-units/**` |
| 角色授权 | `/api/v1/roles/**`、`/api/v1/users/{id}/roles/**` |
| 资源权限点 | `/api/v1/resources/**`、`/api/v1/permissions/**` |
| 应用接入 | `/api/v1/apps/**` |
| 会话 | `/api/v1/sessions/**`、`/api/v1/account/sessions/**` |
| 自助中心 | `/api/v1/account/profile`、`/api/v1/account/login-history` |
| 审计 | `/api/v1/audit/**` |
| 导入导出 | `/api/v1/imports/users`、`/api/v1/exports/audit-events` |

## 已移除功能

按最新范围，首版不实现：

- 岗位管理接口：`/api/v1/positions/**`
- 业务分组接口：`/api/v1/groups/**`
- 分组成员维护
- 分组角色授权
- 用户创建/更新 DTO 中的 `positionCode`、`groupIds`
- 数据库模型中的 `iam_position`、`iam_group`、`iam_user_group`、`iam_group_role`

后续如果要恢复这些能力，应新建独立 `organization` 子领域聚合，不要把岗位/分组逻辑塞回用户聚合。
