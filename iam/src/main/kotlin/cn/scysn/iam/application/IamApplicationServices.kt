package cn.scysn.iam.application

import cn.scysn.common.api.PageResponse
import cn.scysn.common.api.TraceIds
import cn.scysn.common.error.ErrorCode
import cn.scysn.common.error.businessError
import cn.scysn.common.page.PageQuery
import cn.scysn.iam.application.support.PasswordHasher
import cn.scysn.iam.application.support.TokenIssuer
import cn.scysn.iam.domain.audit.AuditEvent
import cn.scysn.iam.domain.auth.AuthorizationCode
import cn.scysn.iam.domain.auth.CurrentUserInfo
import cn.scysn.iam.domain.auth.JwkSet
import cn.scysn.iam.domain.auth.LoginResult
import cn.scysn.iam.domain.auth.LoginSession
import cn.scysn.iam.domain.auth.LoginUserSummary
import cn.scysn.iam.domain.auth.SsoSession
import cn.scysn.iam.domain.auth.TokenSet
import cn.scysn.iam.domain.authorization.DataScope
import cn.scysn.iam.domain.authorization.EffectivePermission
import cn.scysn.iam.domain.authorization.MenuNode
import cn.scysn.iam.domain.authorization.Permission
import cn.scysn.iam.domain.authorization.Resource
import cn.scysn.iam.domain.authorization.ResourceType
import cn.scysn.iam.domain.authorization.Role
import cn.scysn.iam.domain.authorization.RoleType
import cn.scysn.iam.domain.clientapp.AppStatus
import cn.scysn.iam.domain.clientapp.ClientApp
import cn.scysn.iam.domain.clientapp.SecretRotation
import cn.scysn.iam.domain.clientapp.TokenPolicy
import cn.scysn.iam.domain.identity.AccountType
import cn.scysn.iam.domain.identity.User
import cn.scysn.iam.domain.identity.UserStatus
import cn.scysn.iam.domain.organization.OrgUnit
import cn.scysn.iam.domain.ports.AppSearchQuery
import cn.scysn.iam.domain.ports.AuditRepository
import cn.scysn.iam.domain.ports.AuditSearchQuery
import cn.scysn.iam.domain.ports.AuthorizationCodeRepository
import cn.scysn.iam.domain.ports.ClientAppRepository
import cn.scysn.iam.domain.ports.OrgUnitRepository
import cn.scysn.iam.domain.ports.PermissionRepository
import cn.scysn.iam.domain.ports.PermissionSearchQuery
import cn.scysn.iam.domain.ports.ResourceRepository
import cn.scysn.iam.domain.ports.ResourceSearchQuery
import cn.scysn.iam.domain.ports.RoleRepository
import cn.scysn.iam.domain.ports.RoleSearchQuery
import cn.scysn.iam.domain.ports.SessionRepository
import cn.scysn.iam.domain.ports.SessionSearchQuery
import cn.scysn.iam.domain.ports.SsoSessionRepository
import cn.scysn.iam.domain.ports.UserRepository
import cn.scysn.iam.domain.ports.UserSearchQuery
import cn.scysn.iam.domain.shared.newId
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentHashMap

@Service
class AuthApplicationService(
    private val userRepository: UserRepository,
    private val appRepository: ClientAppRepository,
    private val roleRepository: RoleRepository,
    private val orgUnitRepository: OrgUnitRepository,
    private val sessionRepository: SessionRepository,
    private val ssoSessionRepository: SsoSessionRepository,
    private val authorizationCodeRepository: AuthorizationCodeRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenIssuer: TokenIssuer,
) {
    private val refreshTokenIndex = ConcurrentHashMap<String, String>()

    fun login(command: LoginCommand): LoginResult {
        val user = userRepository.findByUsername(command.username)
            ?: invalidCredentials(command.clientId, command.username, command.ip, command.userAgent)
        val app = appRepository.findByClientId(command.clientId)
            ?: businessError(ErrorCode.NOT_FOUND, "接入应用不存在: ${command.clientId}")
        if (!passwordHasher.matches(command.password, user.passwordHash)) {
            invalidCredentials(command.clientId, command.username, command.ip, command.userAgent)
        }
        ensureLoginAllowed(user)
        ensureUserCanAccessApp(user, app.clientId)
        val session = LoginSession.create(
            userId = user.id,
            clientId = app.clientId,
            deviceId = command.deviceId,
            ip = command.ip,
            userAgent = command.userAgent,
            ttlSeconds = app.tokenPolicy.ssoSessionMaxSeconds
        )
        user.markLogin()
        userRepository.save(user)
        sessionRepository.save(session)
        val ssoSession = SsoSession.create(user.id, app.tokenPolicy.ssoSessionMaxSeconds)
        ssoSessionRepository.save(ssoSession)
        val tokenSet = issueTokens(user.id, app.clientId, session.id, app.tokenPolicy)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGIN_SUCCESS",
                actorId = user.id,
                targetId = user.id,
                clientId = app.clientId,
                traceId = TraceIds.current(),
                ip = command.ip,
                userAgent = command.userAgent
            )
        )
        return LoginResult(
            tokenType = tokenSet.tokenType,
            accessToken = tokenSet.accessToken,
            expiresIn = tokenSet.expiresIn,
            refreshToken = tokenSet.refreshToken,
            refreshExpiresIn = tokenSet.refreshExpiresIn,
            sessionId = tokenSet.sessionId,
            ssoSessionId = ssoSession.id,
            requiredActions = user.requiredActions.map { it.code },
            user = LoginUserSummary(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                employeeNo = user.profile.employeeNo
            )
        )
    }

    fun refresh(command: RefreshTokenCommand): TokenSet {
        val sessionId = refreshTokenIndex.remove(command.refreshToken)
            ?: businessError(ErrorCode.TOKEN_INVALID, "Refresh Token 无效或已使用")
        val session = sessionRepository.findSessionById(sessionId)
            ?: businessError(ErrorCode.SESSION_REVOKED)
        if (session.status.code != "active") {
            businessError(ErrorCode.SESSION_REVOKED)
        }
        val app = appRepository.findByClientId(command.clientId)
            ?: businessError(ErrorCode.NOT_FOUND, "接入应用不存在: ${command.clientId}")
        session.touch()
        sessionRepository.save(session)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "token",
                eventType = "TOKEN_REFRESHED",
                actorId = session.userId,
                targetId = session.id,
                clientId = session.clientId,
                traceId = TraceIds.current()
            )
        )
        return issueTokens(session.userId, app.clientId, session.id, app.tokenPolicy)
    }

    fun logout(refreshToken: String): Map<String, Any> {
        val sessionId = refreshTokenIndex.remove(refreshToken)
            ?: businessError(ErrorCode.TOKEN_INVALID, "Refresh Token 无效")
        val session = sessionRepository.findSessionById(sessionId)
            ?: businessError(ErrorCode.SESSION_REVOKED)
        session.revoke("用户主动退出")
        sessionRepository.save(session)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGOUT",
                actorId = session.userId,
                targetId = session.id,
                clientId = session.clientId,
                traceId = TraceIds.current()
            )
        )
        return mapOf("sessionId" to session.id, "revoked" to true)
    }

    fun logoutAll(userId: String, reason: String?): Map<String, Int> {
        val sessions = sessionRepository.findActiveByUserId(userId)
        sessions.forEach {
            it.revoke(reason)
            sessionRepository.save(it)
        }
        val revokedSsoSessions = revokeActiveSsoSessions(userId)
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGOUT_ALL",
                actorId = userId,
                targetId = userId,
                clientId = null,
                reason = reason,
                traceId = TraceIds.current()
            )
        )
        return mapOf("revokedSessions" to sessions.size, "revokedSsoSessions" to revokedSsoSessions)
    }

    fun currentUser(userId: String): CurrentUserInfo {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "当前用户不存在")
        return CurrentUserInfo(
            id = user.id,
            username = user.username,
            displayName = user.displayName,
            employeeNo = user.profile.employeeNo,
            email = user.email,
            phone = user.phone,
            factoryCode = user.profile.factoryCode,
            departmentCode = user.profile.departmentCode,
            status = user.status.code,
            requiredActions = user.requiredActions.map { it.code }
        )
    }

    fun ensureUserCanAccessApp(userId: String, appId: String) {
        appRepository.findByClientId(appId) ?: businessError(ErrorCode.NOT_FOUND, "接入应用不存在: $appId")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        ensureUserCanAccessApp(user, appId)
    }

    fun authorize(
        ssoSessionId: String?,
        clientId: String,
        redirectUri: String,
        responseType: String,
        scope: String?,
    ): AuthorizationCode {
        require(responseType == "code") { "首版仅支持 response_type=code" }
        val app = appRepository.findByClientId(clientId) ?: businessError(ErrorCode.NOT_FOUND, "接入应用不存在: $clientId")
        require(app.status == AppStatus.ACTIVE) { "接入应用已禁用" }
        require(app.redirectUris.contains(redirectUri)) { "redirect_uri 未在接入应用白名单中" }
        val ssoSession = ssoSessionId
            ?.let { ssoSessionRepository.findSsoSessionById(it) }
            ?: businessError(ErrorCode.UNAUTHORIZED, "缺少有效 SSO 登录态")
        if (!ssoSession.isActive()) {
            businessError(ErrorCode.UNAUTHORIZED, "SSO 登录态已失效")
        }
        ssoSession.touch()
        ssoSessionRepository.save(ssoSession)
        ensureUserCanAccessApp(ssoSession.userId, app.clientId)
        return authorizationCodeRepository.save(
            AuthorizationCode.create(
                userId = ssoSession.userId,
                clientId = app.clientId,
                redirectUri = redirectUri,
                scope = scope
            )
        )
    }

    fun exchangeAuthorizationCode(codeValue: String, clientId: String, redirectUri: String): TokenSet {
        val code = authorizationCodeRepository.findAuthorizationCode(codeValue)
            ?: businessError(ErrorCode.BAD_REQUEST, "授权码不存在")
        code.consume(clientId, redirectUri)
        authorizationCodeRepository.save(code)
        val app = appRepository.findByClientId(clientId) ?: businessError(ErrorCode.NOT_FOUND, "接入应用不存在: $clientId")
        val user = userRepository.findUserById(code.userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        ensureLoginAllowed(user)
        ensureUserCanAccessApp(user, app.clientId)
        val session = LoginSession.create(
            userId = user.id,
            clientId = app.clientId,
            deviceId = null,
            ip = null,
            userAgent = null,
            ttlSeconds = app.tokenPolicy.ssoSessionMaxSeconds
        )
        sessionRepository.save(session)
        return issueTokens(user.id, app.clientId, session.id, app.tokenPolicy)
    }

    fun jwks(): JwkSet = JwkSet(tokenIssuer.jwks())

    fun changePassword(command: ChangePasswordCommand): Map<String, Boolean> {
        require(command.newPassword == command.confirmPassword) { "两次输入的新密码不一致" }
        val user = userRepository.findUserById(command.userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        if (!passwordHasher.matches(command.oldPassword, user.passwordHash)) {
            businessError(ErrorCode.INVALID_CREDENTIALS)
        }
        user.changePassword(passwordHasher.hash(command.newPassword))
        userRepository.save(user)
        logoutAll(user.id, "修改密码后退出其他会话")
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "user",
                eventType = "PASSWORD_CHANGED",
                actorId = user.id,
                targetId = user.id,
                clientId = null,
                traceId = TraceIds.current()
            )
        )
        return mapOf("changed" to true, "logoutOtherSessions" to true)
    }

    fun requestPasswordReset(): Map<String, Boolean> = mapOf("accepted" to true)

    fun confirmPasswordReset(resetToken: String, newPassword: String, confirmPassword: String): Map<String, Boolean> {
        require(resetToken.isNotBlank()) { "resetToken 不能为空" }
        require(newPassword == confirmPassword) { "两次输入的新密码不一致" }
        return mapOf("changed" to true)
    }

    private fun issueTokens(userId: String, clientId: String, sessionId: String, tokenPolicy: TokenPolicy): TokenSet {
        val refreshToken = tokenIssuer.issueRefreshToken()
        refreshTokenIndex[refreshToken] = sessionId
        return TokenSet(
            accessToken = tokenIssuer.issueAccessToken(
                userId = userId,
                audience = clientId,
                authorizedParty = clientId,
                sessionId = sessionId,
                ttlSeconds = tokenPolicy.accessTokenTtlSeconds
            ),
            expiresIn = tokenPolicy.accessTokenTtlSeconds,
            refreshToken = refreshToken,
            refreshExpiresIn = tokenPolicy.refreshTokenMaxSeconds,
            sessionId = sessionId
        )
    }

    private fun ensureLoginAllowed(user: User) {
        when (user.status) {
            UserStatus.DISABLED -> businessError(ErrorCode.USER_DISABLED)
            UserStatus.FROZEN -> businessError(ErrorCode.USER_FROZEN)
            UserStatus.ARCHIVED -> businessError(ErrorCode.USER_ARCHIVED)
            UserStatus.PENDING, UserStatus.ACTIVE -> Unit
        }
        user.ensureCanLogin()
    }

    private fun ensureUserCanAccessApp(user: User, appId: String) {
        val now = OffsetDateTime.now()
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(now) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(now) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        val canAccess = (directRoleIds + inheritedRoleIds)
            .mapNotNull { roleRepository.findRoleById(it) }
            .any { it.enabled && it.appId == appId }
        if (!canAccess) {
            businessError(ErrorCode.FORBIDDEN, "用户没有访问应用 $appId 的权限")
        }
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }

    private fun revokeActiveSsoSessions(userId: String): Int {
        val ssoSessions = ssoSessionRepository.findActiveSsoByUserId(userId)
        ssoSessions.forEach {
            it.revoke()
            ssoSessionRepository.save(it)
        }
        return ssoSessions.size
    }

    private fun invalidCredentials(clientId: String, username: String, ip: String?, userAgent: String?): Nothing {
        auditRepository.save(
            AuditEvent.record(
                eventCategory = "login",
                eventType = "LOGIN_FAILURE",
                actorId = null,
                targetId = username,
                clientId = clientId,
                result = "failure",
                reason = ErrorCode.INVALID_CREDENTIALS.name,
                traceId = TraceIds.current(),
                ip = ip,
                userAgent = userAgent
            )
        )
        businessError(ErrorCode.INVALID_CREDENTIALS)
    }
}

@Service
class UserApplicationService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val auditRepository: AuditRepository,
    private val passwordHasher: PasswordHasher,
) {
    fun page(keyword: String?, status: String?, orgUnitId: String?, page: PageQuery): PageResponse<User> {
        return userRepository.search(UserSearchQuery(keyword, status, orgUnitId, page))
    }

    fun create(command: CreateUserCommand): User {
        if (userRepository.existsByUsername(command.username)) businessError(ErrorCode.USERNAME_EXISTS)
        command.employeeNo?.let { if (userRepository.existsByEmployeeNo(it)) businessError(ErrorCode.EMPLOYEE_NO_EXISTS) }
        command.email?.let { if (userRepository.existsByEmail(it)) businessError(ErrorCode.EMAIL_EXISTS) }
        command.phone?.let { if (userRepository.existsByPhone(it)) businessError(ErrorCode.PHONE_EXISTS) }
        val user = User.create(
            username = command.username,
            displayName = command.displayName,
            employeeNo = command.employeeNo,
            email = command.email,
            phone = command.phone,
            accountType = parseAccountType(command.accountType),
            factoryCode = command.factoryCode,
            departmentCode = command.departmentCode,
            orgUnitIds = command.orgUnitIds,
            passwordHash = passwordHasher.hash(command.initialPassword),
            forceChangePassword = command.forceChangePassword,
            attributes = command.attributes
        )
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_CREATED", null, user.id, null, traceId = TraceIds.current()))
        return user
    }

    fun detail(id: String): User = userRepository.findUserById(id) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")

    fun update(id: String, command: UpdateUserCommand): User {
        val user = detail(id)
        user.updateProfile(
            displayName = command.displayName,
            email = command.email,
            phone = command.phone,
            factoryCode = command.factoryCode,
            departmentCode = command.departmentCode,
            orgUnitIds = command.orgUnitIds,
            attributes = command.attributes
        )
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_UPDATED", null, user.id, null, traceId = TraceIds.current()))
        return user
    }

    fun enable(id: String, reason: String): User {
        val user = detail(id)
        user.enable(reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_ENABLED", null, user.id, null, reason = reason, traceId = TraceIds.current()))
        return user
    }

    fun disable(id: String, command: RevokeUserCommand): Map<String, Any> {
        val user = detail(id)
        user.disable(command.reason)
        val revoked = revokeSessionsIfNeeded(user.id, command.revokeSessions, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_DISABLED", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return mapOf("id" to user.id, "status" to user.status.code, "revokedSessions" to revoked)
    }

    fun freeze(id: String, command: FreezeUserCommand): User {
        val user = detail(id)
        user.freeze(command.freezeUntil, command.freezeReason)
        revokeSessionsIfNeeded(user.id, command.revokeSessions, command.freezeReason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_FROZEN", null, user.id, null, reason = command.freezeReason, traceId = TraceIds.current()))
        return user
    }

    fun unfreeze(id: String, reason: String): User {
        val user = detail(id)
        user.unfreeze(reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_UNFROZEN", null, user.id, null, reason = reason, traceId = TraceIds.current()))
        return user
    }

    fun archive(id: String, command: RevokeUserCommand): User {
        val user = detail(id)
        user.archive(command.reason)
        revokeSessionsIfNeeded(user.id, command.revokeSessions, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "USER_ARCHIVED", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return user
    }

    fun resetPassword(id: String, command: ResetPasswordCommand): Map<String, Any> {
        val user = detail(id)
        val rawPassword = when (command.mode) {
            "temporary_password" -> command.temporaryPassword ?: throw IllegalArgumentException("临时密码不能为空")
            "reset_link" -> "ResetLink@${newId("pwd").takeLast(12)}"
            else -> throw IllegalArgumentException("mode 只能是 temporary_password 或 reset_link")
        }
        user.resetPassword(passwordHasher.hash(rawPassword), command.forceChangePassword, command.reason)
        userRepository.save(user)
        auditRepository.save(AuditEvent.record("user", "PASSWORD_RESET", null, user.id, null, reason = command.reason, traceId = TraceIds.current()))
        return mapOf("id" to user.id, "changed" to true, "forceChangePassword" to command.forceChangePassword)
    }

    fun sessions(id: String): List<LoginSession> {
        detail(id)
        return sessionRepository.search(SessionSearchQuery(userId = id, page = PageQuery(pageSize = 200))).items
    }

    fun terminateUserSession(userId: String, sessionId: String) {
        detail(userId)
        val session = sessionRepository.findSessionById(sessionId) ?: businessError(ErrorCode.NOT_FOUND, "会话不存在")
        require(session.userId == userId) { "会话不属于该用户" }
        session.revoke("管理员终止用户会话")
        sessionRepository.save(session)
    }

    private fun revokeSessionsIfNeeded(userId: String, revoke: Boolean, reason: String): Int {
        if (!revoke) return 0
        val sessions = sessionRepository.findActiveByUserId(userId)
        sessions.forEach {
            it.revoke(reason)
            sessionRepository.save(it)
        }
        return sessions.size
    }
}

@Service
class OrganizationApplicationService(
    private val orgUnitRepository: OrgUnitRepository,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
) {
    fun tree(rootId: String?, includeDisabled: Boolean): List<OrgUnitTreeNode> {
        val nodes = orgUnitRepository.findAll()
            .filter { includeDisabled || it.enabled }
            .filter { rootId == null || it.id == rootId || isDescendantOf(it, rootId) }
        return buildTree(nodes, rootId)
    }

    fun create(command: CreateOrgUnitCommand): OrgUnit {
        if (orgUnitRepository.existsOrgCode(command.code)) businessError(ErrorCode.CONFLICT, "组织编码已存在")
        command.parentId?.let {
            orgUnitRepository.findOrgUnitById(it) ?: businessError(ErrorCode.NOT_FOUND, "父组织不存在")
        }
        return orgUnitRepository.save(
            OrgUnit.create(
                parentId = command.parentId,
                code = command.code,
                name = command.name,
                type = command.type,
                sortOrder = command.sortOrder,
                attributes = command.attributes
            )
        )
    }

    fun update(id: String, command: UpdateOrgUnitCommand): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        orgUnit.update(command.parentId, command.code, command.name, command.type, command.sortOrder, command.attributes)
        when (command.enabled) {
            true -> orgUnit.enable()
            false -> orgUnit.disable()
            null -> Unit
        }
        return orgUnitRepository.save(orgUnit)
    }

    fun delete(id: String) {
        if (orgUnitRepository.hasChildren(id)) businessError(ErrorCode.ORG_UNIT_HAS_CHILDREN)
        val memberCount = userRepository.search(UserSearchQuery(orgUnitId = id, page = PageQuery(pageSize = 1))).total
        if (memberCount > 0) businessError(ErrorCode.CONFLICT, "组织节点下存在成员")
        orgUnitRepository.deleteOrgUnit(id)
    }

    private fun isDescendantOf(orgUnit: OrgUnit, rootId: String): Boolean {
        var parentId = orgUnit.parentId
        while (parentId != null) {
            if (parentId == rootId) return true
            parentId = orgUnitRepository.findOrgUnitById(parentId)?.parentId
        }
        return false
    }

    private fun buildTree(nodes: List<OrgUnit>, rootId: String?): List<OrgUnitTreeNode> {
        fun childrenOf(parentId: String?): List<OrgUnitTreeNode> {
            return nodes
                .filter { it.parentId == parentId }
                .sortedWith(compareBy<OrgUnit> { it.sortOrder }.thenBy { it.name })
                .map {
                    OrgUnitTreeNode(
                        id = it.id,
                        parentId = it.parentId,
                        code = it.code,
                        name = it.name,
                        type = it.type,
                        enabled = it.enabled,
                        roleIds = it.roleIds,
                        children = childrenOf(it.id)
                    )
                }
        }
        return if (rootId == null) childrenOf(null) else nodes.filter { it.id == rootId }.map {
            OrgUnitTreeNode(it.id, it.parentId, it.code, it.name, it.type, it.enabled, it.roleIds, childrenOf(it.id))
        }
    }

    fun bindRoles(id: String, command: BindOrgUnitRolesCommand): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        val roles = command.roleIds.map { roleRepository.findRoleById(it) ?: businessError(ErrorCode.NOT_FOUND, "角色不存在: $it") }
        require(roles.all { it.enabled }) { "存在已禁用角色" }
        orgUnit.bindRoles(command.roleIds, command.mode)
        return orgUnitRepository.save(orgUnit)
    }

    fun roles(id: String): List<Role> {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        return orgUnit.roleIds.map { roleId ->
            roleRepository.findRoleById(roleId) ?: businessError(ErrorCode.NOT_FOUND, "角色不存在: $roleId")
        }
    }

    fun revokeRole(id: String, roleId: String): OrgUnit {
        val orgUnit = orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        orgUnit.revokeRole(roleId)
        return orgUnitRepository.save(orgUnit)
    }

    fun addUser(id: String, userId: String): User {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        user.updateProfile(
            displayName = null,
            email = null,
            phone = null,
            factoryCode = null,
            departmentCode = null,
            orgUnitIds = user.profile.orgUnitIds + id,
            attributes = null
        )
        return userRepository.save(user)
    }

    fun addUsers(id: String, userIds: Set<String>): List<User> {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        val users = userIds.map { userId ->
            userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在: $userId")
        }
        return users.map { user ->
            user.updateProfile(
                displayName = null,
                email = null,
                phone = null,
                factoryCode = null,
                departmentCode = null,
                orgUnitIds = user.profile.orgUnitIds + id,
                attributes = null
            )
            userRepository.save(user)
        }
    }

    fun removeUser(id: String, userId: String): User {
        orgUnitRepository.findOrgUnitById(id) ?: businessError(ErrorCode.NOT_FOUND, "组织不存在")
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        user.updateProfile(
            displayName = null,
            email = null,
            phone = null,
            factoryCode = null,
            departmentCode = null,
            orgUnitIds = user.profile.orgUnitIds - id,
            attributes = null
        )
        return userRepository.save(user)
    }
}

@Service
class AuthorizationApplicationService(
    private val roleRepository: RoleRepository,
    private val resourceRepository: ResourceRepository,
    private val permissionRepository: PermissionRepository,
    private val userRepository: UserRepository,
    private val orgUnitRepository: OrgUnitRepository,
) {
    fun roles(appId: String?, roleType: String?, keyword: String?, page: PageQuery): PageResponse<Role> {
        return roleRepository.search(RoleSearchQuery(appId, roleType, keyword, page))
    }

    fun createRole(command: CreateRoleCommand): Role {
        if (roleRepository.existsRoleCode(command.appId, command.roleCode)) businessError(ErrorCode.ROLE_CODE_EXISTS)
        return roleRepository.save(
            Role.create(
                appId = command.appId,
                roleCode = command.roleCode,
                roleName = command.roleName,
                roleType = parseRoleType(command.roleType),
                description = command.description,
                enabled = command.enabled
            )
        )
    }

    fun updateRole(id: String, command: UpdateRoleCommand): Role {
        val role = roleRepository.findRoleById(id) ?: businessError(ErrorCode.NOT_FOUND, "角色不存在")
        role.update(command.roleName, command.description, command.enabled)
        return roleRepository.save(role)
    }

    fun deleteRole(id: String) = roleRepository.deleteRole(id)

    fun bindRolePermissions(id: String, command: BindRolePermissionsCommand): Role {
        val role = roleRepository.findRoleById(id) ?: businessError(ErrorCode.NOT_FOUND, "角色不存在")
        val permissions = permissionRepository.findPermissionsByIds(command.permissionIds)
        require(permissions.size == command.permissionIds.size) { "存在无效权限点" }
        role.bindPermissions(command.permissionIds, command.mode, command.reason)
        return roleRepository.save(role)
    }

    fun grantUserRoles(userId: String, command: GrantUserRolesCommand): User {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        val roles = command.roleIds.map { roleRepository.findRoleById(it) ?: businessError(ErrorCode.NOT_FOUND, "角色不存在: $it") }
        require(roles.all { it.enabled }) { "存在已禁用角色" }
        user.grantRoles(command.roleIds, command.effectiveFrom, command.effectiveTo, command.reason)
        return userRepository.save(user)
    }

    fun revokeUserRole(userId: String, roleId: String) {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        user.revokeRole(roleId)
        userRepository.save(user)
    }

    fun resources(appId: String?, resourceType: String?, keyword: String?): List<Resource> {
        return resourceRepository.search(ResourceSearchQuery(appId, resourceType, keyword))
    }

    fun createResource(command: CreateResourceCommand): Resource {
        if (resourceRepository.existsResourceCode(command.appId, command.resourceCode)) businessError(ErrorCode.CONFLICT, "资源编码已存在")
        return resourceRepository.save(
            Resource.create(
                appId = command.appId,
                parentId = command.parentId,
                resourceCode = command.resourceCode,
                resourceName = command.resourceName,
                resourceType = parseResourceType(command.resourceType),
                path = command.path,
                method = command.method,
                sortOrder = command.sortOrder,
                attributes = command.attributes
            )
        )
    }

    fun updateResource(id: String, command: UpdateResourceCommand): Resource {
        val resource = resourceRepository.findResourceById(id) ?: businessError(ErrorCode.NOT_FOUND, "资源不存在")
        resource.update(command.resourceName, command.path, command.method, command.sortOrder, command.enabled, command.attributes)
        return resourceRepository.save(resource)
    }

    fun permissions(appId: String?, resourceId: String?, keyword: String?): List<Permission> {
        return permissionRepository.search(PermissionSearchQuery(appId, resourceId, keyword))
    }

    fun createPermission(command: CreatePermissionCommand): Permission {
        if (permissionRepository.existsPermissionCode(command.appId, command.permissionCode)) businessError(ErrorCode.PERMISSION_CODE_EXISTS)
        command.resourceId?.let {
            resourceRepository.findResourceById(it) ?: businessError(ErrorCode.NOT_FOUND, "资源不存在")
        }
        return permissionRepository.save(
            Permission.create(
                appId = command.appId,
                resourceId = command.resourceId,
                permissionCode = command.permissionCode,
                permissionName = command.permissionName,
                action = command.action,
                description = command.description
            )
        )
    }

    fun effectivePermissions(userId: String, appId: String): EffectivePermission {
        val user = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")
        val directRoleIds = user.roleGrants
            .filter { it.effectiveFrom == null || !it.effectiveFrom.isAfter(OffsetDateTime.now()) }
            .filter { it.effectiveTo == null || it.effectiveTo.isAfter(OffsetDateTime.now()) }
            .map { it.roleId }
            .toSet()
        val inheritedRoleIds = user.profile.orgUnitIds
            .flatMap { collectOrgUnitRoleIds(it) }
            .toSet()
        val activeRoleIds = directRoleIds + inheritedRoleIds
        val roles = activeRoleIds.mapNotNull { roleRepository.findRoleById(it) }
            .filter { it.enabled && (it.appId == null || it.appId == appId) }
        val permissionIds = roles.flatMap { it.permissionIds }.toSet()
        val permissions = permissionRepository.findPermissionsByIds(permissionIds)
            .filter { it.enabled && it.appId == appId }
        val resources = resourceRepository.search(ResourceSearchQuery(appId = appId))
        val permissionResourceIds = permissions.mapNotNull { it.resourceId }.toSet()
        val menuNodes = resources
            .filter { it.resourceType == ResourceType.MENU && permissionResourceIds.contains(it.id) }
            .map { MenuNode(code = it.resourceCode, name = it.resourceName, path = it.path) }
        val buttons = resources
            .filter { it.resourceType == ResourceType.BUTTON && permissionResourceIds.contains(it.id) }
            .associate { resource ->
                resource.resourceCode to permissions.filter { it.resourceId == resource.id }.map { it.action }
            }
        return EffectivePermission(
            appId = appId,
            userId = user.id,
            permissionVersion = OffsetDateTime.now().toEpochSecond(),
            roles = roles.map { it.roleCode },
            permissions = permissions.map { it.permissionCode },
            menus = menuNodes,
            buttons = buttons,
            dataScopes = listOfNotNull(
                user.profile.factoryCode?.let { DataScope("factory", setOf(it)) },
                user.profile.departmentCode?.let { DataScope("department", setOf(it)) }
            )
        )
    }

    private fun collectOrgUnitRoleIds(orgUnitId: String): Set<String> {
        val result = linkedSetOf<String>()
        val visited = mutableSetOf<String>()
        var currentId: String? = orgUnitId
        while (currentId != null && visited.add(currentId)) {
            val orgUnit = orgUnitRepository.findOrgUnitById(currentId) ?: break
            if (orgUnit.enabled) {
                result.addAll(orgUnit.roleIds)
            }
            currentId = orgUnit.parentId
        }
        return result
    }
}

@Service
class ClientAppApplicationService(
    private val appRepository: ClientAppRepository,
) {
    fun page(keyword: String?, status: String?, page: PageQuery): PageResponse<ClientApp> {
        return appRepository.search(AppSearchQuery(keyword, status, page))
    }

    fun create(command: CreateClientAppCommand): CreateClientAppResult {
        if (appRepository.existsByClientId(command.clientId)) businessError(ErrorCode.APP_CODE_EXISTS)
        val (app, secret) = ClientApp.create(
            clientId = command.clientId,
            name = command.name,
            appType = command.appType,
            ownerDept = command.ownerDept,
            redirectUris = command.redirectUris,
            logoutUris = command.logoutUris,
            tokenPolicy = command.tokenPolicy.toDomain()
        )
        appRepository.save(app)
        return CreateClientAppResult(app, secret)
    }

    fun update(id: String, command: UpdateClientAppCommand): ClientApp {
        val app = appRepository.findAppById(id) ?: businessError(ErrorCode.NOT_FOUND, "应用不存在")
        app.update(
            name = command.name,
            appType = command.appType,
            ownerDept = command.ownerDept,
            redirectUris = command.redirectUris,
            logoutUris = command.logoutUris,
            tokenPolicy = command.tokenPolicy?.toDomain(),
            status = command.status?.let { parseAppStatus(it) }
        )
        return appRepository.save(app)
    }

    fun rotateSecret(id: String, command: RotateSecretCommand): SecretRotation {
        val app = appRepository.findAppById(id) ?: businessError(ErrorCode.NOT_FOUND, "应用不存在")
        val rotation = app.rotateSecret(command.reason, command.gracePeriodMinutes)
        appRepository.save(app)
        return rotation
    }
}

@Service
class SessionApplicationService(
    private val sessionRepository: SessionRepository,
) {
    fun page(userId: String?, clientId: String?, status: String?, page: PageQuery): PageResponse<LoginSession> {
        return sessionRepository.search(SessionSearchQuery(userId, clientId, status, page))
    }

    fun terminate(id: String) {
        val session = sessionRepository.findSessionById(id) ?: businessError(ErrorCode.NOT_FOUND, "会话不存在")
        session.revoke("管理员终止会话")
        sessionRepository.save(session)
    }

    fun currentUserSessions(userId: String): List<LoginSession> {
        return sessionRepository.search(SessionSearchQuery(userId = userId, page = PageQuery(pageSize = 200))).items
    }

    fun terminateCurrentUserSession(userId: String, sessionId: String) {
        val session = sessionRepository.findSessionById(sessionId) ?: businessError(ErrorCode.NOT_FOUND, "会话不存在")
        require(session.userId == userId) { "只能终止自己的会话" }
        session.revoke("用户自助终止会话")
        sessionRepository.save(session)
    }
}

@Service
class AccountApplicationService(
    private val userRepository: UserRepository,
    private val auditRepository: AuditRepository,
) {
    fun profile(userId: String): User = userRepository.findUserById(userId) ?: businessError(ErrorCode.NOT_FOUND, "用户不存在")

    fun updateProfile(userId: String, displayName: String?, email: String?, phone: String?): User {
        val user = profile(userId)
        user.updateProfile(displayName, email, phone, null, null, null, null)
        userRepository.save(user)
        return user
    }

    fun loginHistory(userId: String, page: PageQuery): PageResponse<AuditEvent> {
        return auditRepository.search(AuditSearchQuery(eventCategory = "login", userId = userId, page = page))
    }
}

@Service
class AuditApplicationService(
    private val auditRepository: AuditRepository,
) {
    fun events(query: AuditSearchQuery): PageResponse<AuditEvent> = auditRepository.search(query)
}

@Service
class ImportExportApplicationService(
    private val userApplicationService: UserApplicationService,
) {
    fun importUsers(command: ImportUsersCommand): ImportResult {
        val errors = mutableListOf<ImportError>()
        if (command.mode !in setOf("validate_only", "import")) {
            throw IllegalArgumentException("mode 只能是 validate_only 或 import")
        }
        command.items.forEachIndexed { index, item ->
            runCatching {
                if (command.mode == "import") {
                    userApplicationService.create(
                        CreateUserCommand(
                            username = item.username,
                            displayName = item.displayName,
                            employeeNo = item.employeeNo,
                            email = null,
                            phone = null,
                            accountType = "employee",
                            factoryCode = item.factoryCode,
                            departmentCode = item.departmentCode,
                            orgUnitIds = emptySet(),
                            initialPassword = "TempPassword@123",
                            forceChangePassword = true,
                            attributes = emptyMap()
                        )
                    )
                }
            }.onFailure {
                errors += ImportError(row = index + 1, username = item.username, code = "IMPORT_ERROR", message = it.message ?: "导入失败")
            }
        }
        return ImportResult(
            total = command.items.size,
            success = command.items.size - errors.size,
            failed = errors.size,
            errors = errors
        )
    }

    fun exportAuditEvents(command: ExportAuditCommand): ExportTaskResult {
        require(command.reason.isNotBlank()) { "导出审计日志必须填写原因" }
        require(command.format in setOf("xlsx", "csv")) { "format 只能是 xlsx 或 csv" }
        return ExportTaskResult(taskId = newId("export"), status = "pending")
    }
}

data class OrgUnitTreeNode(
    val id: String,
    val parentId: String?,
    val code: String,
    val name: String,
    val type: String,
    val enabled: Boolean,
    val roleIds: Set<String>,
    val children: List<OrgUnitTreeNode>,
)

data class CreateClientAppResult(
    val app: ClientApp,
    val clientSecret: String,
)

data class ImportResult(
    val total: Int,
    val success: Int,
    val failed: Int,
    val errors: List<ImportError>,
)

data class ImportError(
    val row: Int,
    val username: String,
    val code: String,
    val message: String,
)

data class ExportTaskResult(
    val taskId: String,
    val status: String,
)

private fun parseAccountType(value: String): AccountType = when (value) {
    "employee" -> AccountType.EMPLOYEE
    "supplier" -> AccountType.SUPPLIER
    "customer" -> AccountType.CUSTOMER
    "system" -> AccountType.SYSTEM
    else -> throw IllegalArgumentException("accountType 不合法: $value")
}

private fun parseRoleType(value: String): RoleType = when (value) {
    "platform" -> RoleType.PLATFORM
    "app" -> RoleType.APP
    else -> throw IllegalArgumentException("roleType 不合法: $value")
}

private fun parseResourceType(value: String): ResourceType = when (value) {
    "menu" -> ResourceType.MENU
    "page" -> ResourceType.PAGE
    "button" -> ResourceType.BUTTON
    "api" -> ResourceType.API
    "report" -> ResourceType.REPORT
    "data" -> ResourceType.DATA
    else -> throw IllegalArgumentException("resourceType 不合法: $value")
}

private fun parseAppStatus(value: String): AppStatus = when (value) {
    "active" -> AppStatus.ACTIVE
    "disabled" -> AppStatus.DISABLED
    else -> throw IllegalArgumentException("status 不合法: $value")
}

private fun TokenPolicyCommand.toDomain(): TokenPolicy {
    return TokenPolicy(
        accessTokenTtlSeconds = accessTokenTtlSeconds,
        refreshTokenIdleSeconds = refreshTokenIdleSeconds,
        refreshTokenMaxSeconds = refreshTokenMaxSeconds,
        ssoSessionIdleSeconds = ssoSessionIdleSeconds,
        ssoSessionMaxSeconds = ssoSessionMaxSeconds
    )
}
