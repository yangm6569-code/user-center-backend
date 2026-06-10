package cn.scysn.usercenter.application

import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.*
import cn.scysn.usercenter.interfaces.*

fun UserAccountEntity.toVO(): UserAccountVO =
    UserAccountVO(
        id = id ?: 0,
        username = username,
        email = email,
        phone = phone,
        displayName = displayName,
        status = status,
        accountType = accountType,
        employeeNo = employeeNo,
        factoryCode = factoryCode,
        departmentCode = departmentCode,
        positionCode = positionCode,
        emailVerified = emailVerified,
        lockedUntil = lockedUntil,
        freezeUntil = freezeUntil,
        freezeReason = freezeReason,
        lastLoginAt = lastLoginAt,
        createTime = createTime,
        updateTime = updateTime,
    )

fun LoginSessionEntity.toVO(): LoginSessionVO =
    LoginSessionVO(
        id = id ?: 0,
        clientId = application?.clientId,
        status = status,
        ip = ip,
        userAgent = userAgent,
        expiresAt = expiresAt,
        revokedAt = revokedAt,
        createTime = createTime,
    )

fun AccessApplicationEntity.toVO(): AccessApplicationVO =
    AccessApplicationVO(
        id = id ?: 0,
        clientId = clientId,
        name = name,
        appType = appType,
        ownerDept = ownerDept,
        status = status,
        secretVersion = secretVersion,
        redirectUris = redirectUris,
        logoutUris = logoutUris,
        allowedScopes = allowedScopes,
        accessTokenTtlSeconds = accessTokenTtlSeconds,
        refreshTokenTtlSeconds = refreshTokenTtlSeconds,
    )

fun GroupEntity.toVO(): GroupVO =
    GroupVO(
        id = id ?: 0,
        code = code,
        name = name,
        groupType = groupType,
        parentId = parent?.id,
        path = path,
    )

fun GroupEntity.toTreeVO(children: List<GroupTreeVO>): GroupTreeVO =
    GroupTreeVO(
        id = id ?: 0,
        code = code,
        name = name,
        groupType = groupType,
        parentId = parent?.id,
        path = path,
        children = children,
    )

fun RoleEntity.toVO(): RoleVO =
    RoleVO(
        id = id ?: 0,
        code = code,
        name = name,
        description = description,
        scope = scope,
        applicationId = application?.id,
        clientId = application?.clientId,
    )

fun PermissionEntity.toVO(): PermissionVO =
    PermissionVO(
        id = id ?: 0,
        roleId = role.id ?: 0,
        roleCode = role.code,
        resourceId = resource.id ?: 0,
        resourceCode = resource.resourceCode,
        clientId = resource.application.clientId,
        scope = scope,
        policyId = policy?.id,
        effect = effect,
    )

fun AuditEventEntity.toVO(): AuditEventVO =
    AuditEventVO(
        id = id ?: 0,
        category = category,
        eventType = eventType,
        actorId = actorId,
        targetId = targetId,
        clientId = clientId,
        ip = ip,
        userAgent = userAgent,
        result = result,
        reason = reason,
        traceId = traceId,
        payload = payload,
        createTime = createTime,
    )
