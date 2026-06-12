# 用户中台后端

该工程按 `E:\Template-V3` 的后端分层风格新建，模板项目只作为架构参考，不作为源码修改目标。

## 模块

- `app`：Spring Boot 启动模块。
- `common`：统一响应、分页、错误码、异常处理等公共能力。
- `iam`：用户中台业务模块，内部继续按 `domain`、`application`、`interfaces`、`infrastructure` 分层。

## 首版功能边界

已排除岗位、业务分组、分组成员维护和分组角色授权。首版接口聚焦认证、用户、组织树、角色授权、资源权限点、应用接入、会话、自助中心、审计、导入导出。

模块边界见 [docs/module-boundaries.md](docs/module-boundaries.md)。
