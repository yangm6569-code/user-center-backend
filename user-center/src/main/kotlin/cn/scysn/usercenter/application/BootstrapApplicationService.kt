package cn.scysn.usercenter.application

import cn.scysn.usercenter.domain.model.*
import cn.scysn.usercenter.infrastructure.config.UserCenterBootstrapProperties
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.AccessApplicationEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.RoleEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserAccountEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.UserRoleEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.AccessApplicationJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.RoleJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserAccountJpaRepository
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.UserRoleJpaRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class BootstrapApplicationService(
    private val bootstrapProperties: UserCenterBootstrapProperties,
    private val accessApplicationJpaRepository: AccessApplicationJpaRepository,
    private val roleJpaRepository: RoleJpaRepository,
    private val userAccountJpaRepository: UserAccountJpaRepository,
    private val userRoleJpaRepository: UserRoleJpaRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        val consoleApp = accessApplicationJpaRepository.findByClientId("user-center-console")
            ?: accessApplicationJpaRepository.save(
                AccessApplicationEntity().apply {
                    clientId = "user-center-console"
                    name = "用户中台管理端"
                    appType = ApplicationType.WEB
                    ownerDept = "IT"
                    redirectUris = mutableListOf("http://localhost:5173/callback")
                    logoutUris = mutableListOf("http://localhost:5173/logout")
                    allowedScopes = mutableListOf("profile", "user-center")
                },
            )

        val roleMap = listOf(
            "platform_super_admin" to "平台超级管理员",
            "security_admin" to "安全管理员",
            "audit_admin" to "审计员",
            "user_admin" to "用户管理员",
            "app_admin" to "应用管理员",
            "user_center_user_manage" to "用户管理",
            "user_center_user_view" to "用户查看",
            "user_center_role_manage" to "角色管理",
            "user_center_permission_manage" to "权限管理",
            "user_center_audit_view" to "审计查看",
            "user_center_app_manage" to "应用管理",
        ).associate { (code, name) ->
            code to (roleJpaRepository.findByCodeAndApplicationIsNull(code) ?: roleJpaRepository.save(
                RoleEntity().apply {
                    this.code = code
                    this.name = name
                    scope = RoleScope.PLATFORM
                },
            ))
        }

        roleJpaRepository.findByCodeAndApplication_Id("console_operator", consoleApp.id!!)
            ?: roleJpaRepository.save(
                RoleEntity().apply {
                    code = "console_operator"
                    name = "管理台操作员"
                    scope = RoleScope.APPLICATION
                    application = consoleApp
                },
            )

        if (userAccountJpaRepository.count() == 0L) {
            val admin = userAccountJpaRepository.save(
                UserAccountEntity().apply {
                    username = bootstrapProperties.adminUsername
                    email = bootstrapProperties.adminEmail
                    displayName = "平台管理员"
                    passwordHash = passwordEncoder.encode(bootstrapProperties.adminPassword)
                    status = UserStatus.ACTIVE
                    accountType = AccountType.EMPLOYEE
                    emailVerified = true
                    passwordChangedAt = Instant.now()
                },
            )
            userRoleJpaRepository.save(
                UserRoleEntity().apply {
                    user = admin
                    role = roleMap.getValue("platform_super_admin")
                    reason = "bootstrap"
                },
            )
        }
    }
}
