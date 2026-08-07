package cn.scysn.iam.infrastructure.bootstrap

import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.support.PasswordHasher
import cn.scysn.iam.domain.authorization.BusinessDomain
import cn.scysn.iam.domain.authorization.DataScopeConfig
import cn.scysn.iam.domain.authorization.FieldPermission
import cn.scysn.iam.domain.authorization.FieldPermissionType
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.ResourceType
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.authorization.RoleType
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.clientapp.TokenPolicy
import cn.scysn.iam.domain.identity.AccountType
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.organization.OrgUnit
import cn.scysn.iam.domain.ports.BusinessDomainRepository
import cn.scysn.iam.domain.ports.ClientAppRepository
import cn.scysn.iam.domain.ports.DataScopeConfigRepository
import cn.scysn.iam.domain.ports.FieldPermissionRepository
import cn.scysn.iam.domain.ports.FieldPermissionSearchQuery
import cn.scysn.iam.domain.ports.OrgUnitRepository
import cn.scysn.iam.domain.ports.PermissionRepository
import cn.scysn.iam.domain.ports.PermissionSearchQuery
import cn.scysn.iam.domain.ports.ResourceRepository
import cn.scysn.iam.domain.ports.ResourceSearchQuery
import cn.scysn.iam.domain.ports.RoleRepository
import cn.scysn.iam.domain.ports.RoleSearchQuery
import cn.scysn.iam.domain.ports.SystemDomainRepository
import cn.scysn.iam.domain.ports.UserRepository
import cn.scysn.iam.domain.system.SystemDomain
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class IamSeedData(
    private val userRepository: UserRepository,
    private val appRepository: ClientAppRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val roleRepository: RoleRepository,
    private val resourceRepository: ResourceRepository,
    private val permissionRepository: PermissionRepository,
    private val systemDomainRepository: SystemDomainRepository,
    private val businessDomainRepository: BusinessDomainRepository,
    private val fieldPermissionRepository: FieldPermissionRepository,
    private val dataScopeConfigRepository: DataScopeConfigRepository,
    private val passwordHasher: PasswordHasher,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        seedDomains()
        seedApps()
        seedOrg()
        seedAdmin()
        seedPermissions()
        seedMesResources()
        seedBusinessDomains()
    }

    private fun seedDomains() {
        listOf(
            Triple("user-center", "用户中台", "用户中台自身管理域"),
            Triple("mes", "MES", "MES 系统域"),
            Triple("report", "报表演示", "报表演示系统域")
        ).forEach { (code, name, description) ->
            if (systemDomainRepository.findByCode(code) == null) {
                systemDomainRepository.save(SystemDomain.create(code, name, description))
            }
        }
    }

    private fun seedApps() {
        listOf(
            "user-center-console" to "用户中台管理控制台",
            "mes-web" to "MES Web",
            "mes-api" to "MES API",
            "report-web" to "Report Web"
        ).forEach { (clientId, name) ->
            val redirectUris = defaultRedirectUris(clientId)
            val existing = appRepository.findByClientId(clientId)
            val domainId = defaultDomainId(clientId)
            if (existing == null) {
                val (app, _) = ClientApp.create(
                    domainId = domainId,
                    clientId = clientId,
                    name = name,
                    appType = if (clientId.endsWith("-api")) "api" else "web",
                    ownerDept = "信息技术部",
                    redirectUris = redirectUris,
                    logoutUris = emptySet(),
                    tokenPolicy = TokenPolicy(900, 7200, 43200)
                )
                appRepository.save(app)
            } else if (existing.domainId != domainId || !existing.redirectUris.containsAll(redirectUris)) {
                existing.update(
                    name = name,
                    appType = if (clientId.endsWith("-api")) "api" else "web",
                    ownerDept = "信息技术部",
                    domainId = domainId,
                    redirectUris = existing.redirectUris + redirectUris,
                    logoutUris = existing.logoutUris,
                    tokenPolicy = existing.tokenPolicy,
                    status = existing.status,
                    oidcConfig = null
                )
                appRepository.save(existing)
            }
        }
    }

    private fun defaultDomainId(clientId: String): String {
        return when {
            clientId.startsWith("mes-") -> "mes"
            clientId.startsWith("report-") -> "report"
            else -> "user-center"
        }
    }

    private fun defaultRedirectUris(clientId: String): Set<String> {
        return when (clientId) {
            "user-center-console" -> setOf("http://example.test/callback", "http://localhost:5173/oauth/callback")
            "mes-web" -> setOf(
                "http://localhost:5174/oauth/callback",
                "http://localhost:8081/demo-apps/mes/index.html",
                "http://localhost:8001/sso/callback",
                "http://127.0.0.1:8001/sso/callback",
                "http://localhost:8002/sso/callback",
                "http://127.0.0.1:8002/sso/callback"
            )
            "report-web" -> setOf(
                "http://localhost:5175/oauth/callback",
                "http://localhost:8081/demo-apps/report/index.html"
            )
            else -> emptySet()
        }
    }

    private fun seedOrg() {
        if (!orgUnitRepository.existsOrgCode("ROOT")) {
            orgUnitRepository.save(
                OrgUnit.create(
                    parentId = null,
                    code = "ROOT",
                    name = "默认组织",
                    type = "company",
                    sortOrder = 0
                )
            )
        }
    }

    private fun seedAdmin() {
        if (!userRepository.existsByUsername("admin")) {
            val user = User.create(
                id = "user-001",
                username = "admin",
                displayName = "系统管理员",
                employeeNo = "ADMIN001",
                email = "admin@example.com",
                phone = null,
                accountType = AccountType.EMPLOYEE,
                factoryCode = null,
                departmentCode = null,
                orgUnitIds = emptySet(),
                passwordHash = passwordHasher.hash("Admin@123456"),
                forceChangePassword = false
            )
            userRepository.save(user)
        }
    }

    private fun seedPermissions() {
        val app = appRepository.findByClientId("user-center-console") ?: return
        if (!resourceRepository.existsResourceCode(app.clientId, "user_center")) {
            val resource = resourceRepository.save(
                Resource.create(
                    domainId = app.domainId,
                    appId = app.clientId,
                    parentId = null,
                    resourceCode = "user_center",
                    resourceName = "用户中台",
                    resourceType = ResourceType.MENU,
                    path = "/user-center",
                    method = null,
                    sortOrder = 0,
                    attributes = emptyMap()
                )
            )
            val permission = permissionRepository.save(
                Permission.create(
                    domainId = app.domainId,
                    appId = app.clientId,
                    resourceId = resource.id,
                    permissionCode = "user_center_user_manage",
                    permissionName = "管理用户",
                    action = "manage",
                    description = "用户中台用户管理"
                )
            )
            val role = Role.create(
                domainId = app.domainId,
                appId = app.clientId,
                roleCode = "platform_super_admin",
                roleName = "平台超级管理员",
                roleType = RoleType.APP,
                description = "初始化管理员角色",
                enabled = true
            )
            role.bindPermissions(setOf(permission.id), "replace", "初始化授权")
            roleRepository.save(role)
            userRepository.findByUsername("admin")?.let {
                it.grantRoles(setOf(role.id), null, null, "初始化管理员")
                userRepository.save(it)
            }
        }
        seedMesDemoAccess()
    }

    private fun seedMesDemoAccess() {
        val app = appRepository.findByClientId("mes-web") ?: return
        val role = findOrCreateRole(app.domainId, app.clientId, "mes_demo_user", "MES 演示用户")
        userRepository.findByUsername("admin")?.let {
            if (it.roleGrants.none { grant -> grant.roleId == role.id }) {
                it.grantRoles(setOf(role.id), null, null, "初始化 MES 演示访问")
                userRepository.save(it)
            }
        }
    }

    private fun findOrCreateRole(domainId: String, appId: String, roleCode: String, roleName: String): Role {
        findExistingRole(domainId, appId, roleCode)?.let { return it }
        if (!roleRepository.existsRoleCode(domainId, roleCode) && !roleRepository.existsRoleCode(appId, roleCode)) {
            return roleRepository.save(
                Role.create(
                    domainId = domainId,
                    appId = appId,
                    roleCode = roleCode,
                    roleName = roleName,
                    roleType = RoleType.APP,
                    description = "用于 SSO 与应用隔离演示",
                    enabled = true
                )
            )
        }
        findExistingRole(domainId, appId, roleCode)?.let { return it }
        return roleRepository.search(
            RoleSearchQuery(
                appId = appId,
                domainId = domainId,
                keyword = roleCode,
                page = PageQuery(page = 1, pageSize = 50)
            )
        ).items.firstOrNull { it.roleCode == roleCode }
            ?: error("角色已存在但无法加载: $appId/$roleCode")
    }

    private fun findExistingRole(domainId: String, appId: String, roleCode: String): Role? {
        return roleRepository.search(
            RoleSearchQuery(
                appId = appId,
                keyword = roleCode,
                page = PageQuery(page = 1, pageSize = 50)
            )
        ).items.firstOrNull { it.roleCode == roleCode }
            ?: roleRepository.search(
                RoleSearchQuery(
                    domainId = domainId,
                    keyword = roleCode,
                    page = PageQuery(page = 1, pageSize = 50)
                )
            ).items.firstOrNull { it.roleCode == roleCode }
    }

    // ============================================================
    // MES 资源权限种子数据
    // ============================================================
    // 两套资源：
    //   1. mes-web 应用: MENU + PAGE 资源 → 前端菜单渲染 + 页面级权限
    //   2. mes-api 应用: API 资源 + Permission → 网关鉴权规则

    private fun seedMesResources() {
        seedMesWebResources()
        seedMesApiResources()
        seedMesRoles()
    }

    // ---------- mes-web: 菜单和页面 ----------

    private fun seedMesWebResources() {
        val app = appRepository.findByClientId("mes-web") ?: return
        val d = app.domainId
        val aid = app.clientId

        // 一级菜单
        val m1 = seedMenu(d, aid, null, "mes_production", "生产管理", "/productMng", 1)
        val m2 = seedMenu(d, aid, null, "mes_quality", "质量管理", "/qualityMng", 2)
        val m3 = seedMenu(d, aid, null, "mes_material", "物料管理", "/materialMng", 3)
        val m4 = seedMenu(d, aid, null, "mes_resource", "资源管理", "/resourceMng", 4)
        val m5 = seedMenu(d, aid, null, "mes_tech", "技术管理", "/techMng", 5)
        val m6 = seedMenu(d, aid, null, "mes_energy", "能源管理", "/energyMng", 6)
        val m7 = seedMenu(d, aid, null, "mes_system", "系统设置", "/system", 7)

        // 生产管理
        seedPage(d, aid, m1.id, "mes_production_overview", "生产概览", "/productMng/overview", 1)
        seedPage(d, aid, m1.id, "mes_production_order", "生产订单", "/productMng/productionOrder", 2)
        seedPage(d, aid, m1.id, "mes_production_operation_order", "工序订单", "/productMng/operationOrder", 3)
        seedPage(d, aid, m1.id, "mes_production_step_order", "工步订单", "/productMng/stepOrder", 4)
        seedPage(d, aid, m1.id, "mes_production_deferred_review", "工步延期审核", "/productMng/deferredReview", 5)
        seedPage(d, aid, m1.id, "mes_production_product_management", "车体流转", "/productMng/productManagement", 6)
        seedPage(d, aid, m1.id, "mes_production_schedule", "排程管理", "/productMng/schedule", 7)

        // 质量管理
        seedPage(d, aid, m2.id, "mes_quality_overview", "质量概览", "/qualityMng/overview", 1)
        seedPage(d, aid, m2.id, "mes_quality_process_detect", "过程检测", "/qualityMng/processDetect", 2)
        seedPage(d, aid, m2.id, "mes_quality_delivery_inspect", "交付检测", "/qualityMng/deliveryInspect", 3)

        // 物料管理
        seedPage(d, aid, m3.id, "mes_material_overview", "物料概览", "/materialMng/overview", 1)
        seedPage(d, aid, m3.id, "mes_material_requirement_plan", "物料需求计划", "/materialMng/requirementPlan", 2)
        seedPage(d, aid, m3.id, "mes_material_receipt", "物料接收", "/materialMng/materialReceipt", 3)
        seedPage(d, aid, m3.id, "mes_material_inspection", "来料审核", "/materialMng/materialInspection", 4)
        seedPage(d, aid, m3.id, "mes_material_management", "物料管理", "/materialMng/materialMng", 5)
        seedPage(d, aid, m3.id, "mes_material_auxiliary", "辅料管理", "/materialMng/auxiliaryMaterialMng", 6)
        seedPage(d, aid, m3.id, "mes_material_bom", "产品BOM", "/materialMng/productBOM", 7)

        // 资源管理 - 子菜单
        val sm1 = seedMenu(d, aid, m4.id, "mes_resource_equipment", "设备管理", "/resourceMng/equipment", 1)
        seedPage(d, aid, sm1.id, "mes_resource_equipment_mng", "设备管理", "/resourceMng/equipment/equipmentMng", 1)
        seedPage(d, aid, sm1.id, "mes_resource_inspection_item", "点检项目", "/resourceMng/equipment/inspectionItem", 2)
        seedPage(d, aid, sm1.id, "mes_resource_inspection_plan", "点检计划", "/resourceMng/equipment/inspectionPlan", 3)
        seedPage(d, aid, sm1.id, "mes_resource_inspection_task", "点检任务", "/resourceMng/equipment/inspectionTask", 4)
        seedPage(d, aid, sm1.id, "mes_resource_fault_report", "设备故障", "/resourceMng/equipment/faultReport", 5)

        val sm2 = seedMenu(d, aid, m4.id, "mes_resource_fixture", "工装管理", "/resourceMng/fixture", 2)
        seedPage(d, aid, sm2.id, "mes_resource_fixture_mng", "工装管理", "/resourceMng/fixture/fixtureMng", 1)
        seedPage(d, aid, sm2.id, "mes_resource_fixture_detection", "工装定检统计", "/resourceMng/fixture/fixtureDetectionStatistics", 2)
        seedPage(d, aid, sm2.id, "mes_resource_fixture_repair", "定保小修", "/resourceMng/fixture/fixtureMinorRepair", 3)

        val sm3 = seedMenu(d, aid, m4.id, "mes_resource_tool", "工具管理", "/resourceMng/tool", 3)
        seedPage(d, aid, sm3.id, "mes_resource_tool_mng", "工具管理", "/resourceMng/tool/toolMng", 1)
        seedPage(d, aid, sm3.id, "mes_resource_tool_detection", "工具定检统计", "/resourceMng/tool/toolDetectionStatistics", 2)
        seedPage(d, aid, sm3.id, "mes_resource_tool_repair", "定保小修", "/resourceMng/tool/toolMinorRepair", 3)

        val sm4 = seedMenu(d, aid, m4.id, "mes_resource_person", "人员管理", "/resourceMng/person", 4)
        seedPage(d, aid, sm4.id, "mes_resource_person_mng", "人员管理", "/resourceMng/person/personMng", 1)
        seedPage(d, aid, sm4.id, "mes_resource_qualification_mng", "技能资质管理", "/resourceMng/person/qualificationMng", 2)
        seedPage(d, aid, sm4.id, "mes_resource_qualification_audit", "资质审核", "/resourceMng/person/qualificationAuditMng", 3)

        // 技术管理
        seedPage(d, aid, m5.id, "mes_tech_process", "工艺路线", "/techMng/processMng", 1)
        seedPage(d, aid, m5.id, "mes_tech_steps", "工序管理", "/techMng/stepsMng", 2)
        seedPage(d, aid, m5.id, "mes_tech_documentation", "技术文件", "/techMng/technicalDocumentation", 3)
        seedPage(d, aid, m5.id, "mes_tech_qm_notifications", "QM通知", "/techMng/qmNotifications", 4)
        seedPage(d, aid, m5.id, "mes_tech_engineering_changes", "工程更改", "/techMng/engineeringChanges", 5)
        seedPage(d, aid, m5.id, "mes_tech_temporary_notice", "临时通知", "/techMng/temporaryNotice", 6)
        seedPage(d, aid, m5.id, "mes_tech_production_abnormalities", "生产异常", "/techMng/productionAbnormalities", 7)

        // 能源管理
        seedPage(d, aid, m6.id, "mes_energy_overview", "能源概览", "/energyMng/overview", 1)
        seedPage(d, aid, m6.id, "mes_energy_list", "能源管理", "/energyMng/list", 2)

        // 系统设置
        seedPage(d, aid, m7.id, "mes_system_notice", "通知管理", "/system/notice", 1)
        seedPage(d, aid, m7.id, "mes_system_safety_bulletin", "安全通报", "/system/safetyBulletin", 2)
        seedPage(d, aid, m7.id, "mes_system_production_line", "产线配置", "/system/productionLineConfig", 3)
        seedPage(d, aid, m7.id, "mes_system_org_structure", "组织架构", "/system/organizationalStructure", 4)
        seedPage(d, aid, m7.id, "mes_system_team", "班组管理", "/system/teamManagement", 5)
        seedPage(d, aid, m7.id, "mes_system_role", "角色管理", "/system/roleManagement", 6)
    }

    // ---------- mes-api: API 资源 + 权限（网关用） ----------

    private fun seedMesApiResources() {
        val app = appRepository.findByClientId("mes-api") ?: return
        val d = app.domainId
        val aid = app.clientId

        // 每个 API 资源绑定一个 Permission，网关 /gateway/api-rules 会返回这些规则
        // 规则: method + path 匹配 → 需要 permissionCode

        // 认证（公开，创建但不绑角色）
        seedApi(d, aid, "mes_api_auth_login", "登录", "POST", "/auth/login/password", "mes_api_auth_login", "登录")
        seedApi(d, aid, "mes_api_auth_2fa", "双因子验证", "POST", "/auth/login/password/verify-2fa", "mes_api_auth_2fa", "双因子验证")

        // 系统管理
        seedApi(d, aid, "mes_api_person", "人员管理", "*", "/system/person/**", "mes_api_person_manage", "管理用户")
        seedApi(d, aid, "mes_api_role", "角色管理", "*", "/system/roles/**", "mes_api_role_manage", "管理角色")

        // 生产管理
        seedApi(d, aid, "mes_api_production", "生产管理", "*", "/production/**", "mes_api_production_access", "访问生产管理")

        // 质量管理
        seedApi(d, aid, "mes_api_quality", "质量管理", "*", "/quality/**", "mes_api_quality_access", "访问质量管理")

        // 物料管理
        seedApi(d, aid, "mes_api_material", "物料管理", "*", "/material/**", "mes_api_material_access", "访问物料管理")

        // 资源管理
        seedApi(d, aid, "mes_api_resource", "资源管理", "*", "/resource/**", "mes_api_resource_access", "访问资源管理")

        // 技术管理
        seedApi(d, aid, "mes_api_tech", "技术管理", "*", "/technical/**", "mes_api_tech_access", "访问技术管理")

        // 能源管理
        seedApi(d, aid, "mes_api_energy", "能源管理", "*", "/energy/**", "mes_api_energy_access", "访问能源管理")

        // 首页
        seedApi(d, aid, "mes_api_home", "首页", "*", "/home/**", "mes_api_home_access", "访问首页")

        // 通用
        seedApi(d, aid, "mes_api_common", "通用接口", "*", "/common/**", "mes_api_common_access", "访问通用接口")
    }

    // ---------- MES 角色 ----------

    private fun seedMesRoles() {
        val webApp = appRepository.findByClientId("mes-web") ?: return
        val d = webApp.domainId

        // 收集 mes-web 的所有页面权限 (PAGE + MENU)
        val webPermIds = resourceRepository.search(ResourceSearchQuery(appId = webApp.clientId))
            .filter { it.resourceType == ResourceType.PAGE || it.resourceType == ResourceType.MENU }
            .flatMap { res -> permissionRepository.search(PermissionSearchQuery(resourceId = res.id)) }
            .map { it.id }.toSet()

        // 收集 mes-api 的所有 API 权限
        val apiApp = appRepository.findByClientId("mes-api")
        val apiPermIds = apiApp?.let { app ->
            resourceRepository.search(ResourceSearchQuery(appId = app.clientId))
                .filter { it.resourceType == ResourceType.API }
                .flatMap { res -> permissionRepository.search(PermissionSearchQuery(resourceId = res.id)) }
                .map { it.id }.toSet()
        } ?: emptySet()

        val allPermIds = webPermIds + apiPermIds

        // MES 管理员：拥有全部权限
        val adminRole = findOrCreateRole(d, webApp.clientId, "mes_admin", "MES 管理员")
        if (allPermIds.isNotEmpty()) {
            adminRole.bindPermissions(allPermIds, "replace", "初始化 MES 管理员权限")
            roleRepository.save(adminRole)
        }

        // MES 普通用户：拥有全部页面权限，但 API 权限只有读
        val userRole = findOrCreateRole(d, webApp.clientId, "mes_user", "MES 普通用户")
        if (webPermIds.isNotEmpty()) {
            userRole.bindPermissions(webPermIds, "replace", "初始化 MES 普通用户权限")
            roleRepository.save(userRole)
        }

        // 给 admin 用户授予 MES 管理员角色
        userRepository.findByUsername("admin")?.let {
            if (it.roleGrants.none { grant -> grant.roleId == adminRole.id }) {
                it.grantRoles(setOf(adminRole.id), null, null, "初始化 MES 管理员")
                userRepository.save(it)
            }
        }
    }

    // ---------- 辅助方法 ----------

    private fun seedMenu(domainId: String, appId: String, parentId: String?, code: String, name: String, path: String?, sortOrder: Int): Resource {
        val existing = resourceRepository.search(ResourceSearchQuery(appId = appId)).firstOrNull { it.resourceCode == code }
        if (existing != null) return existing
        val res = resourceRepository.save(
            Resource.create(domainId = domainId, appId = appId, parentId = parentId, resourceCode = code, resourceName = name, resourceType = ResourceType.MENU, path = path, method = null, sortOrder = sortOrder, attributes = emptyMap())
        )
        // MENU 资源必须关联 Permission，effectivePermissions 才会返回菜单
        val permCode = "${code}_view"
        if (!permissionRepository.existsPermissionCode(appId, permCode)) {
            permissionRepository.save(Permission.create(domainId = domainId, appId = appId, resourceId = res.id, permissionCode = permCode, permissionName = "查看$name", action = "view", description = "查看${name}菜单"))
        }
        return res
    }

    private fun seedPage(domainId: String, appId: String, parentId: String, code: String, name: String, path: String?, sortOrder: Int): Resource {
        val existing = resourceRepository.search(ResourceSearchQuery(appId = appId)).firstOrNull { it.resourceCode == code }
        if (existing != null) return existing
        val res = resourceRepository.save(
            Resource.create(domainId = domainId, appId = appId, parentId = parentId, resourceCode = code, resourceName = name, resourceType = ResourceType.PAGE, path = path, method = null, sortOrder = sortOrder, attributes = emptyMap())
        )
        val permCode = "${code}_view"
        if (!permissionRepository.existsPermissionCode(appId, permCode)) {
            permissionRepository.save(Permission.create(domainId = domainId, appId = appId, resourceId = res.id, permissionCode = permCode, permissionName = "查看$name", action = "view", description = "查看${name}页面"))
        }
        return res
    }

    private fun seedApi(domainId: String, appId: String, resCode: String, resName: String, method: String, path: String, permCode: String, permName: String) {
        if (resourceRepository.existsResourceCode(appId, resCode)) return
        val res = resourceRepository.save(
            Resource.create(domainId = domainId, appId = appId, parentId = null, resourceCode = resCode, resourceName = resName, resourceType = ResourceType.API, path = path, method = method, sortOrder = 0, attributes = emptyMap())
        )
        if (!permissionRepository.existsPermissionCode(appId, permCode)) {
            permissionRepository.save(Permission.create(domainId = domainId, appId = appId, resourceId = res.id, permissionCode = permCode, permissionName = permName, action = "access", description = "访问${resName}"))
        }
    }

    // ---------- 业务域 + 字段权限 + 数据范围 种子数据 ----------

    private fun seedBusinessDomains() {
        val webApp = appRepository.findByClientId("mes-web") ?: return
        val mesDomainId = webApp.domainId

        seedProductionOrderDomain(mesDomainId, webApp.clientId)
        seedQualityDomain(mesDomainId, webApp.clientId)
        seedMaterialDomain(mesDomainId, webApp.clientId)
    }

    private fun seedProductionOrderDomain(domainId: String, appId: String) {
        val bd = findOrCreateBusinessDomain(domainId, appId, "mes_production_order", "生产订单管理", "MES 生产订单业务域")
        val adminRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_admin")).items.firstOrNull() ?: return
        val userRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_user")).items.firstOrNull() ?: return

        // 只在首次初始化时配置
        if (fieldPermissionRepository.search(FieldPermissionSearchQuery(bd.id)).isNotEmpty()) return

        configureFieldPermissions(bd.id, adminRole.id, listOf(
            "order_no" to FieldPermissionType.VISIBLE,
            "product_name" to FieldPermissionType.VISIBLE,
            "product_code" to FieldPermissionType.VISIBLE,
            "quantity" to FieldPermissionType.VISIBLE,
            "unit_price" to FieldPermissionType.VISIBLE,
            "total_amount" to FieldPermissionType.VISIBLE,
            "customer_name" to FieldPermissionType.VISIBLE,
            "delivery_date" to FieldPermissionType.VISIBLE,
            "status" to FieldPermissionType.VISIBLE,
            "priority" to FieldPermissionType.VISIBLE,
            "workshop" to FieldPermissionType.VISIBLE,
            "production_line" to FieldPermissionType.VISIBLE,
            "planned_start" to FieldPermissionType.VISIBLE,
            "planned_end" to FieldPermissionType.VISIBLE,
            "actual_start" to FieldPermissionType.VISIBLE,
            "actual_end" to FieldPermissionType.VISIBLE,
            "defect_rate" to FieldPermissionType.VISIBLE,
            "responsible_person" to FieldPermissionType.VISIBLE,
        ))

        // 普通用户：敏感字段脱敏
        configureFieldPermissions(bd.id, userRole.id, listOf(
            "order_no" to FieldPermissionType.VISIBLE,
            "product_name" to FieldPermissionType.VISIBLE,
            "product_code" to FieldPermissionType.VISIBLE,
            "quantity" to FieldPermissionType.VISIBLE,
            "unit_price" to FieldPermissionType.MASKED,
            "total_amount" to FieldPermissionType.MASKED,
            "customer_name" to FieldPermissionType.HIDDEN,
            "delivery_date" to FieldPermissionType.VISIBLE,
            "status" to FieldPermissionType.VISIBLE,
            "priority" to FieldPermissionType.VISIBLE,
            "workshop" to FieldPermissionType.VISIBLE,
            "production_line" to FieldPermissionType.VISIBLE,
            "planned_start" to FieldPermissionType.VISIBLE,
            "planned_end" to FieldPermissionType.VISIBLE,
            "actual_start" to FieldPermissionType.READONLY,
            "actual_end" to FieldPermissionType.READONLY,
            "defect_rate" to FieldPermissionType.VISIBLE,
            "responsible_person" to FieldPermissionType.VISIBLE,
        ))

        // 数据范围：管理员全量，普通用户本部门
        seedDataScope(bd.id, adminRole.id, "all", emptySet())
        seedDataScope(bd.id, userRole.id, "department", setOf("dept_production"))
    }

    private fun seedQualityDomain(domainId: String, appId: String) {
        val bd = findOrCreateBusinessDomain(domainId, appId, "mes_quality_inspection", "质量检测管理", "MES 质量检测业务域")
        val adminRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_admin")).items.firstOrNull() ?: return
        val userRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_user")).items.firstOrNull() ?: return

        configureFieldPermissions(bd.id, adminRole.id, listOf(
            "inspection_no" to FieldPermissionType.VISIBLE,
            "product_name" to FieldPermissionType.VISIBLE,
            "batch_no" to FieldPermissionType.VISIBLE,
            "sampling_rate" to FieldPermissionType.VISIBLE,
            "qualified_qty" to FieldPermissionType.VISIBLE,
            "defect_qty" to FieldPermissionType.VISIBLE,
            "defect_reason" to FieldPermissionType.VISIBLE,
            "inspector" to FieldPermissionType.VISIBLE,
            "inspection_date" to FieldPermissionType.VISIBLE,
            "result" to FieldPermissionType.VISIBLE,
            "remark" to FieldPermissionType.VISIBLE,
        ))

        configureFieldPermissions(bd.id, userRole.id, listOf(
            "inspection_no" to FieldPermissionType.VISIBLE,
            "product_name" to FieldPermissionType.VISIBLE,
            "batch_no" to FieldPermissionType.VISIBLE,
            "sampling_rate" to FieldPermissionType.READONLY,
            "qualified_qty" to FieldPermissionType.VISIBLE,
            "defect_qty" to FieldPermissionType.VISIBLE,
            "defect_reason" to FieldPermissionType.HIDDEN,
            "inspector" to FieldPermissionType.VISIBLE,
            "inspection_date" to FieldPermissionType.VISIBLE,
            "result" to FieldPermissionType.VISIBLE,
            "remark" to FieldPermissionType.READONLY,
        ))

        seedDataScope(bd.id, adminRole.id, "all", emptySet())
        seedDataScope(bd.id, userRole.id, "department", setOf("dept_quality"))
    }

    private fun seedMaterialDomain(domainId: String, appId: String) {
        val bd = findOrCreateBusinessDomain(domainId, appId, "mes_material_mgmt", "物料管理", "MES 物料管理业务域")
        val adminRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_admin")).items.firstOrNull() ?: return
        val userRole = roleRepository.search(RoleSearchQuery(appId = appId, keyword = "mes_user")).items.firstOrNull() ?: return

        configureFieldPermissions(bd.id, adminRole.id, listOf(
            "material_code" to FieldPermissionType.VISIBLE,
            "material_name" to FieldPermissionType.VISIBLE,
            "spec" to FieldPermissionType.VISIBLE,
            "unit" to FieldPermissionType.VISIBLE,
            "stock_qty" to FieldPermissionType.VISIBLE,
            "safety_stock" to FieldPermissionType.VISIBLE,
            "unit_cost" to FieldPermissionType.VISIBLE,
            "supplier_name" to FieldPermissionType.VISIBLE,
            "warehouse" to FieldPermissionType.VISIBLE,
            "last_purchase_price" to FieldPermissionType.VISIBLE,
            "bom_version" to FieldPermissionType.VISIBLE,
        ))

        configureFieldPermissions(bd.id, userRole.id, listOf(
            "material_code" to FieldPermissionType.VISIBLE,
            "material_name" to FieldPermissionType.VISIBLE,
            "spec" to FieldPermissionType.VISIBLE,
            "unit" to FieldPermissionType.VISIBLE,
            "stock_qty" to FieldPermissionType.VISIBLE,
            "safety_stock" to FieldPermissionType.READONLY,
            "unit_cost" to FieldPermissionType.MASKED,
            "supplier_name" to FieldPermissionType.HIDDEN,
            "warehouse" to FieldPermissionType.VISIBLE,
            "last_purchase_price" to FieldPermissionType.MASKED,
            "bom_version" to FieldPermissionType.READONLY,
        ))

        seedDataScope(bd.id, adminRole.id, "all", emptySet())
        seedDataScope(bd.id, userRole.id, "factory", setOf("factory_a"))
    }

    private fun findOrCreateBusinessDomain(domainId: String, appId: String, code: String, name: String, description: String?): BusinessDomain {
        val existing = businessDomainRepository.findByCode(appId, code)
        if (existing != null) return existing
        return businessDomainRepository.save(BusinessDomain.create(domainId, appId, code, name, description))
    }

    private fun configureFieldPermissions(businessDomainId: String, roleId: String, permissions: List<Pair<String, FieldPermissionType>>) {
        val existing = fieldPermissionRepository.search(FieldPermissionSearchQuery(businessDomainId, roleId))
            .map { it.fieldCode }.toSet()
        permissions.forEach { (fieldCode, permType) ->
            if (fieldCode !in existing) {
                fieldPermissionRepository.save(
                    FieldPermission.create(businessDomainId, roleId, fieldCode, fieldCode, permType)
                )
            }
        }
    }

    private fun seedDataScope(businessDomainId: String, roleId: String, scopeType: String, scopeValue: Set<String>) {
        if (dataScopeConfigRepository.findByBusinessDomainIdAndRoleId(businessDomainId, roleId) == null) {
            dataScopeConfigRepository.save(DataScopeConfig.create(businessDomainId, roleId, scopeType, scopeValue))
        }
    }
}
