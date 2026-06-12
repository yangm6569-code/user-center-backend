# 用户中台 SSO、应用隔离与网关鉴权备忘

本文档整理本轮讨论结论，方便后续继续开发用户中台、业务系统接入和网关鉴权。

## 1. 资源、权限点、角色、组织的关系

核心关系：

```text
组织 -> 角色 -> 权限点 -> 资源
用户 -> 组织 -> 继承角色 -> 获得权限点 -> 访问资源
```

说明：

- 资源：被保护的对象，例如菜单、按钮、API、报表、数据对象。
- 权限点：对资源的动作，例如 `mes_order_view`、`mes_report_export`。
- 角色：权限点集合，例如 `production_manager`、`mes_admin`。
- 组织：可以绑定角色，用户加入组织后自动继承组织角色。

首版范围已经收敛为：

```text
用户直接角色 + 组织继承角色
```

不做岗位、不做分组。

## 2. 权限点为什么可以绑定资源

创建权限点时 `resourceId` 的作用是标识这个权限点作用在哪个资源上。

例如：

```text
资源：GET /mes-api/orders
权限点：mes_order_view
动作：view
```

这样有效权限接口可以返回菜单、按钮、API 对应的权限摘要。

但当前项目里 `resourceId` 是可空的，也就是权限点不强制绑定资源。如果暂时只想靠权限编码控制，也可以不传 `resourceId`。

## 3. 组织继承角色

建议规则：

```text
父组织绑定角色后，子组织默认继承。
用户加入子组织后，可以继承子组织角色，也可以继承父组织角色。
用户移出组织后，该组织带来的继承角色自动回收。
```

示例：

```text
制造部 -> 绑定 production_manager
  └─ 一车间
      └─ A 班组
```

用户加入 `A 班组` 后，可以继承 `制造部` 的 `production_manager`。

当前项目已补充：

- 组织可以绑定角色。
- 组织可以移除角色。
- 用户可以加入组织。
- 用户可以移出组织。
- 有效权限计算会合并用户直接角色、所属组织角色、父组织角色。

相关接口：

```text
POST   /api/v1/org-units/{id}/roles
DELETE /api/v1/org-units/{id}/roles/{roleId}
POST   /api/v1/org-units/{id}/users/{userId}
DELETE /api/v1/org-units/{id}/users/{userId}
GET    /api/v1/users/{id}/permissions/effective?appId=mes-api
```

## 4. 前端应用、后端应用都要接入吗

建议都接入，但职责不同：

```text
前端应用：负责登录跳转、拿 Token、控制菜单按钮展示
后端应用：负责接收 API 请求，必须做接口鉴权
用户中台：负责登录、SSO、Token 签发、组织角色权限管理
网关：负责统一验 Token、应用隔离、API 权限点鉴权
```

不能只接前端。只接前端只能隐藏按钮，用户仍可能直接调用后端 API。

建议应用拆分：

```text
mes-web：MES 前端应用
mes-api：MES 后端 API 应用
user-center-console：用户中台控制台
```

## 5. Token 里的 aud 是什么

`aud` 是 JWT 的标准字段，意思是 `audience`，表示这个 Token 的目标接收方。

示例：

```json
{
  "iss": "user-center",
  "sub": "user-001",
  "aud": "mes-api",
  "azp": "mes-web",
  "exp": 1760000000
}
```

字段含义：

```text
iss：Token 签发方，例如 user-center
sub：用户 ID，例如 user-001
aud：Token 目标后端应用，例如 mes-api
azp：发起授权的前端应用，例如 mes-web
exp：过期时间
```

网关校验：

```text
请求 /mes-api/** 时，要求 Token.aud == mes-api。
请求 /report-api/** 时，要求 Token.aud == report-api。
```

如果拿 `aud=mes-api` 的 Token 去访问 `report-api`，网关应拒绝。

## 6. 应用隔离怎么实现

应用隔离分两层：

### 第一层：签发 Token 前隔离

用户登录某个应用时，用户中台检查用户是否有这个应用下的有效角色。

```text
有 mes-api 角色 -> 可以签发 aud=mes-api 的 Token
没有 mes-api 角色 -> 拒绝登录/授权，返回 403
```

当前项目已补充这一层。

### 第二层：网关/API 访问时隔离

网关根据路由判断当前请求属于哪个应用。

```yaml
routes:
  - path: /mes-api/**
    appId: mes-api

  - path: /report-api/**
    appId: report-api
```

网关规则：

```text
当前请求匹配哪个 appId，就要求 Token.aud 等于哪个 appId。
```

这层当前还没有完整实现，需要后续补网关。

## 7. 网关如何做 API 鉴权

首版推荐用 YAML 维护 API 权限规则，简单、直观、好落地。

示例：

```yaml
uc-gateway:
  routes:
    - id: mes-api
      app-id: mes-api
      uri: http://localhost:18082
      path-prefix: /mes-api

  permission-rules:
    - app-id: mes-api
      method: GET
      path: /mes-api/orders/**
      permission: mes_order_view

    - app-id: mes-api
      method: POST
      path: /mes-api/orders
      permission: mes_order_create

    - app-id: mes-api
      method: POST
      path: /mes-api/reports/export
      permission: mes_report_export

  whitelist:
    - /mes-api/actuator/health
```

网关处理流程：

```text
1. 接收请求。
2. 读取 Authorization: Bearer xxx。
3. 使用用户中台 JWKS 公钥本地验签。
4. 校验 exp、iss、aud。
5. 根据 method + path 匹配权限点。
6. 调用户中台有效权限接口查询用户权限。
7. 有权限则转发给业务后端。
8. 无权限则返回 403。
```

转发给后端时可以注入用户上下文：

```http
X-User-Id: user-001
X-Username: zhangsan
X-App-Id: mes-api
```

注意：业务后端必须只能被网关访问，不能被外部绕过。

## 8. SSO 是怎么实现的

SSO 的核心不是 Access Token，而是用户中台自己的登录态 Cookie。

```text
SSO Cookie：证明用户已经在用户中台登录过。
Authorization Code：证明某个应用这次授权合法。
Access Token：给某个具体应用调用 API 用。
```

首次访问 MES：

```text
1. 用户访问 MES 前端。
2. MES 跳转用户中台 /oauth/authorize。
3. 用户中台发现没有 SSO Cookie。
4. 展示登录页。
5. 用户登录成功。
6. 用户中台写入 UC_SSO_SESSION Cookie。
7. 用户中台判断用户是否有 MES 应用权限。
8. 有权限则生成 authorization code，重定向回 MES。
9. MES 用 code 调 /oauth/token 换 Token。
```

再访问报表系统：

```text
1. 用户访问报表系统。
2. 报表系统跳转用户中台 /oauth/authorize。
3. 用户中台发现已有 UC_SSO_SESSION。
4. 不再要求输入账号密码。
5. 判断用户是否有报表系统权限。
6. 有权限则发 code。
7. 报表系统用 code 换自己的 Token。
```

重要结论：

```text
一个 SSO Cookie 可以换多个应用各自的 Token。
不是替换上一个应用的 Token。
```

## 9. 用户会有多个 Token 吗

会。

例如：

```text
MES Token：aud=mes-api
Report Token：aud=report-api
```

但每个前端应用只保存和使用自己的 Token。

```text
MES 前端调用 MES 后端时，带 MES Token。
报表前端调用报表后端时，带 Report Token。
```

网关用 `aud` 防止用错：

```text
请求 /mes-api/**，Token.aud 必须是 mes-api。
请求 /report-api/**，Token.aud 必须是 report-api。
```

## 10. 当前项目状态

当前已具备：

- Spring Data JPA 项目，不是 MyBatis。
- 用户、组织、角色、资源、权限点基础模型。
- SSO Session Cookie：登录成功后写入 `UC_SSO_SESSION`。
- authorization code 授权码模式：`/oauth/authorize` 生成一次性 code，`/oauth/token` 消费 code 换 Token。
- redirect_uri 白名单校验。
- 组织绑定角色。
- 用户加入/移出组织。
- 用户继承组织及父组织角色。
- 有效权限接口。
- 登录时应用隔离：没有某应用角色，不允许登录该应用。

当前还缺：

- 网关模块。
- 网关 API 权限点鉴权。

已补充：

- Access Token 已替换为真实 JWT，包含 `iss`、`sub`、`aud`、`azp`、`sid`、`iat`、`exp`、`jti`。
- JWKS 已返回真实 RSA 公钥，可供网关或业务后端验签。

## 11. 下一步建议

优先顺序：

```text
1. 补轻量网关：JWT 验签 + aud 校验。
2. 补网关 method + path -> permissionCode 鉴权。
3. 再考虑权限缓存、权限版本号、动态资源规则。
```
