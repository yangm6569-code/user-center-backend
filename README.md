# 用户中台后端

这是一个不依赖 Keycloak 的用户中台后端实现，按 `E:\Template-V3` 的多模块风格组织：

```text
user-center-backend
├── app              # Spring Boot 启动模块、配置、Flyway 迁移
├── common           # Result、分页、异常、Trace 过滤器等公共能力
├── user-center      # 用户中台业务模块
├── user-center-api  # 对其他业务模块暴露的用户/权限查询 API
├── buildSrc         # Gradle Kotlin convention plugin
└── docs             # 接口和数据库说明
```

## 技术栈

| 组件 | 选型 |
| --- | --- |
| 语言 | Kotlin |
| 构建 | Gradle Kotlin DSL |
| 框架 | Spring Boot |
| 数据库 | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| 迁移 | Flyway |
| 鉴权 | Spring Security + 自签 JWT |
| 密码 | BCrypt |
| 接口文档 | Springdoc / Knife4j |

## Gradle 导入

当前 wrapper 已指向本机已下载的 Gradle 发行包，避免 IDE 再访问 `services.gradle.org`：

```text
E:/DownLoad/Idea-project/codxFile/tmp/gradle/gradle-8.10.2-bin.zip
```

如果项目移动到其他机器，可以在 `gradle/wrapper/gradle-wrapper.properties` 中把 `distributionUrl` 改回官方地址或公司内网镜像。

工程仓库已配置阿里云 Maven 镜像优先，官方源兜底。若公司网络必须走代理，在 `gradle.properties` 中打开并修改 `systemProp.http.proxyHost`、`systemProp.http.proxyPort`、`systemProp.https.proxyHost`、`systemProp.https.proxyPort`。

IntelliJ IDEA 建议：

```text
Gradle JVM: C:\Program Files\Java\jdk-17
Use Gradle from: gradle-wrapper.properties
```

## 本地启动

先启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

再启动后端：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
..\tmp\gradle\gradle-8.10.2\bin\gradle.bat :app:bootRun
```

默认服务地址：

```text
http://localhost:8081/api
```

Swagger UI：

```text
http://localhost:8081/api/swagger-ui.html
```

Knife4j：

```text
http://localhost:8081/api/doc.html
```

## 默认账号

首次启动会自动创建平台管理员：

```text
username: admin
password: Admin@123456
```

可通过环境变量覆盖：

```text
BOOTSTRAP_ADMIN_USERNAME
BOOTSTRAP_ADMIN_PASSWORD
BOOTSTRAP_ADMIN_EMAIL
```

## 文档

接口清单：

```text
docs/api.md
```

数据库设计：

```text
docs/database.md
```

数据库迁移：

```text
app/src/main/resources/db/migration/V1__init_user_center_schema.sql
```

## 构建校验

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
..\tmp\gradle\gradle-8.10.2\bin\gradle.bat :app:compileKotlin --no-daemon --no-configuration-cache
..\tmp\gradle\gradle-8.10.2\bin\gradle.bat :app:bootJar --no-daemon --no-configuration-cache
..\tmp\gradle\gradle-8.10.2\bin\gradle.bat test --no-daemon --no-configuration-cache
```
