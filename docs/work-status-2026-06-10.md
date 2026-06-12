# 用户中台后端当前工作状态

日期：2026-06-10  
工程目录：`E:\DownLoad\Idea-project\codxFile\公司项目`  
模板说明：`E:\Template-V3` 仅作为架构参考，未修改模板项目。

## 1. 当前完成情况

已新建用户中台后端工程，采用 Kotlin + Spring Boot 3，多模块结构：

| 模块 | 职责 |
| --- | --- |
| `app` | Spring Boot 启动入口与运行配置 |
| `common` | 统一响应、分页、TraceId、错误码、全局异常处理 |
| `iam` | 用户中台业务模块，内部按 `domain/application/interfaces/infrastructure` 分层 |

`iam` 模块已按模板项目风格落地：

| 分层 | 目录 | 说明 |
| --- | --- | --- |
| 领域层 | `iam/src/main/kotlin/cn/scysn/iam/domain` | 充血模型、领域行为、仓储端口 |
| 应用层 | `iam/src/main/kotlin/cn/scysn/iam/application` | 用例编排、唯一性检查、审计记录 |
| 接口层 | `iam/src/main/kotlin/cn/scysn/iam/interfaces` | REST Controller、请求/响应 DTO |
| 基础设施层 | `iam/src/main/kotlin/cn/scysn/iam/infrastructure` | 内存仓储适配器、开发种子数据 |

## 2. 已实现接口范围

已实现以下首版接口骨架，可启动联调：

| 功能域 | 路径 |
| --- | --- |
| 认证 Token | `/api/v1/auth/**` |
| SSO 兼容 | `/oauth/**` |
| 用户生命周期 | `/api/v1/users/**` |
| 组织树 | `/api/v1/org-units/**` |
| 角色授权 | `/api/v1/roles/**`、`/api/v1/users/{id}/roles/**` |
| 资源权限点 | `/api/v1/resources/**`、`/api/v1/permissions/**` |
| 有效权限 | `/api/v1/permissions/effective`、`/api/v1/users/{id}/permissions/effective` |
| 应用接入 | `/api/v1/apps/**` |
| 会话 | `/api/v1/sessions/**`、`/api/v1/account/sessions/**` |
| 自助中心 | `/api/v1/account/profile`、`/api/v1/account/login-history` |
| 审计 | `/api/v1/audit/**` |
| 导入导出 | `/api/v1/imports/users`、`/api/v1/exports/audit-events` |

## 3. 明确排除内容

按最新要求，首版不实现：

- 岗位管理接口：`/api/v1/positions/**`
- 业务分组接口：`/api/v1/groups/**`
- 分组成员维护
- 分组角色授权
- 用户 DTO 中的 `positionCode`
- 用户 DTO 中的 `groupIds`
- 数据库模型中的 `iam_position`、`iam_group`、`iam_user_group`、`iam_group_role`

源码扫描结果：岗位/分组相关关键字只存在于文档的“已移除功能”说明里，没有出现在接口路径或 DTO 实现中。

## 4. 当前实现方式

目前为了先把后端接口跑通，基础设施层使用内存仓储：

```text
iam/src/main/kotlin/cn/scysn/iam/infrastructure/repository/memory/InMemoryIamStore.kt
```

后续接 PostgreSQL/JPA 时，保留 domain port 不变，替换 infrastructure adapter 即可。

开发态种子数据：

| 类型 | 内容 |
| --- | --- |
| 默认账号 | `admin` |
| 默认密码 | `Admin@123456` |
| 默认用户 ID | `user-001` |
| 默认应用 | `user-center-console`、`mes-web`、`mes-api` |

当前 Token/JWKS 是开发占位实现：

```text
iam/src/main/kotlin/cn/scysn/iam/application/support/SecurityPorts.kt
```

后续需要替换为真实 JWT 签发、Refresh Token 哈希存储、JWKS 密钥管理。

## 5. 验证记录

本机默认 Java 是 8，Spring Boot 3 需要 JDK 17。验证时使用：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

已通过：

```powershell
.\gradlew.bat :app:compileKotlin --console=plain
```

也已通过：

```powershell
.\gradlew.bat :app:bootJar --console=plain
```

临时启动服务并访问以下接口成功：

```text
GET http://localhost:18080/api/v1/auth/jwks
```

返回了开发占位 JWKS：

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "dev-stub-key",
      "use": "sig",
      "alg": "RS256"
    }
  ]
}
```

## 6. 下次继续建议

优先顺序建议：

1. 增加 Flyway 迁移脚本，按当前范围移除岗位/分组表，仅保留用户、组织、角色、权限、应用、会话、审计相关表。
2. 将 `InMemoryIamStore` 替换为 JPA/PostgreSQL adapter。
3. 接入 Spring Security，补 JWT 验签、权限注解和当前用户解析。
4. 替换 `StubTokenIssuer`，实现真实 Access Token、Refresh Token 轮换和复用检测。
5. 增加 OpenAPI/Knife4j 文档输出。
6. 给用户状态流转、角色授权、Refresh Token 轮换补测试。

## 7. 常用启动命令

```powershell
cd E:\DownLoad\Idea-project\codxFile\公司项目
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:bootRun
```

默认端口：

```text
8080
```
