package cn.scysn.iam.infrastructure.bootstrap

import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.support.PasswordHasher
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
import cn.scysn.iam.domain.ports.ClientAppRepository
import cn.scysn.iam.domain.ports.OrgUnitRepository
import cn.scysn.iam.domain.ports.PermissionRepository
import cn.scysn.iam.domain.ports.ResourceRepository
import cn.scysn.iam.domain.ports.RoleRepository
import cn.scysn.iam.domain.ports.RoleSearchQuery
import cn.scysn.iam.domain.ports.UserRepository
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
    private val passwordHasher: PasswordHasher,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        seedApps()
        seedOrg()
        seedAdmin()
        seedPermissions()
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
            if (existing == null) {
                val (app, _) = ClientApp.create(
                    clientId = clientId,
                    name = name,
                    appType = if (clientId.endsWith("-api")) "api" else "web",
                    ownerDept = "信息技术部",
                    redirectUris = redirectUris,
                    logoutUris = emptySet(),
                    tokenPolicy = TokenPolicy(900, 7200, 43200)
                )
                appRepository.save(app)
            } else if (!existing.redirectUris.containsAll(redirectUris)) {
                existing.update(
                    name = name,
                    appType = if (clientId.endsWith("-api")) "api" else "web",
                    ownerDept = "信息技术部",
                    redirectUris = existing.redirectUris + redirectUris,
                    logoutUris = existing.logoutUris,
                    tokenPolicy = existing.tokenPolicy,
                    status = existing.status
                )
                appRepository.save(existing)
            }
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
                    appId = app.clientId,
                    resourceId = resource.id,
                    permissionCode = "user_center_user_manage",
                    permissionName = "管理用户",
                    action = "manage",
                    description = "用户中台用户管理"
                )
            )
            val role = Role.create(
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
        val role = findOrCreateRole(app.clientId, "mes_demo_user", "MES 演示用户")
        userRepository.findByUsername("admin")?.let {
            if (it.roleGrants.none { grant -> grant.roleId == role.id }) {
                it.grantRoles(setOf(role.id), null, null, "初始化 MES 演示访问")
                userRepository.save(it)
            }
        }
    }

    private fun findOrCreateRole(appId: String, roleCode: String, roleName: String): Role {
        if (!roleRepository.existsRoleCode(appId, roleCode)) {
            return roleRepository.save(
                Role.create(
                    appId = appId,
                    roleCode = roleCode,
                    roleName = roleName,
                    roleType = RoleType.APP,
                    description = "用于 SSO 与应用隔离演示",
                    enabled = true
                )
            )
        }
        return roleRepository.search(
            RoleSearchQuery(
                appId = appId,
                keyword = roleCode,
                page = PageQuery(page = 1, pageSize = 50)
            )
        ).items.firstOrNull { it.roleCode == roleCode }
            ?: error("角色已存在但无法加载: $appId/$roleCode")
    }
}
