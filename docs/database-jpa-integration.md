# Database / JPA Integration

Date: 2026-06-10

## Current Status

The IAM module now uses Spring Data JPA by default. The previous in-memory store is still available only when the `memory` Spring profile is active.

Default runtime follows the PostgreSQL configuration style from `E:\DownLoad\Idea-project\codxFile\user-center-backend`, but table creation is handled by Hibernate JPA DDL.

```text
jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:user_center}
```

Flyway is disabled. The default JPA mode is:

```text
spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:create}
```

Use `JPA_DDL_AUTO=update` if you want to keep existing local table data between restarts.

The test runtime uses H2 only from `app/src/test/resources/application.yml`.

Start a local PostgreSQL database with:

```powershell
docker compose up -d postgres
```

Run the application with:

```powershell
.\gradlew.bat :app:bootRun
```

## Tables

The implemented first-version scope creates these JPA tables:

- `iam_user`
- `iam_user_credential`
- `iam_user_profile`
- `iam_org_unit`
- `iam_role`
- `iam_resource`
- `iam_permission`
- `iam_client_app`
- `iam_session`
- `iam_audit_event`

The following scope is intentionally not implemented:

- position management
- business groups
- group members
- group role grants
- `iam_position`
- `iam_group`
- `iam_user_group`
- `iam_group_role`

## Verification

Passed locally with JDK 17:

```powershell
.\gradlew.bat :app:compileKotlin --console=plain
.\gradlew.bat :app:test --console=plain
.\gradlew.bat :app:bootJar --console=plain
```
