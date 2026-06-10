package cn.scysn.usercenter.application

import cn.scysn.common.base.domain.ConflictException
import cn.scysn.common.base.domain.NotFoundException
import cn.scysn.usercenter.domain.model.AuditCategory
import cn.scysn.usercenter.infrastructure.persistence.jpa.entity.AccessApplicationEntity
import cn.scysn.usercenter.infrastructure.persistence.jpa.repository.AccessApplicationJpaRepository
import cn.scysn.usercenter.infrastructure.tool.TokenHashing
import cn.scysn.usercenter.interfaces.AccessApplicationCreateDTO
import cn.scysn.usercenter.interfaces.AccessApplicationUpdateDTO
import cn.scysn.usercenter.interfaces.AccessApplicationVO
import cn.scysn.usercenter.interfaces.RotatedSecretVO
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccessApplicationApplicationService(
    private val accessApplicationJpaRepository: AccessApplicationJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenHashing: TokenHashing,
    private val auditApplicationService: AuditApplicationService,
) {
    @Transactional(readOnly = true)
    fun list(): List<AccessApplicationVO> =
        accessApplicationJpaRepository.findAll().map { it.toVO() }.sortedBy { it.clientId }

    @Transactional
    fun create(create: AccessApplicationCreateDTO, actorId: Long?, metadata: RequestMetadata?): Long {
        if (accessApplicationJpaRepository.existsByClientId(create.clientId)) {
            throw ConflictException("clientId已存在: ${create.clientId}")
        }
        val app = accessApplicationJpaRepository.save(
            AccessApplicationEntity().apply {
                clientId = create.clientId
                name = create.name
                appType = create.appType
                ownerDept = create.ownerDept
                redirectUris = create.redirectUris.toMutableList()
                logoutUris = create.logoutUris.toMutableList()
                allowedScopes = create.allowedScopes.toMutableList()
                accessTokenTtlSeconds = create.accessTokenTtlSeconds
                refreshTokenTtlSeconds = create.refreshTokenTtlSeconds
            },
        )
        auditApplicationService.record(AuditCategory.ADMIN, "APPLICATION_CREATED", actorId, app.id, app.clientId, metadata)
        return app.id ?: 0
    }

    @Transactional
    fun update(update: AccessApplicationUpdateDTO, actorId: Long?, metadata: RequestMetadata?) {
        val app = accessApplicationJpaRepository.findById(update.id).orElseThrow { NotFoundException("应用不存在: ${update.id}") }
        update.name?.let { app.name = it }
        update.appType?.let { app.appType = it }
        update.ownerDept?.let { app.ownerDept = it }
        update.status?.let { app.status = it }
        update.redirectUris?.let { app.redirectUris = it.toMutableList() }
        update.logoutUris?.let { app.logoutUris = it.toMutableList() }
        update.allowedScopes?.let { app.allowedScopes = it.toMutableList() }
        update.accessTokenTtlSeconds?.let { app.accessTokenTtlSeconds = it }
        update.refreshTokenTtlSeconds?.let { app.refreshTokenTtlSeconds = it }
        app.touch()
        auditApplicationService.record(AuditCategory.ADMIN, "APPLICATION_UPDATED", actorId, app.id, app.clientId, metadata)
    }

    @Transactional
    fun rotateSecret(id: Long, actorId: Long?, metadata: RequestMetadata?): RotatedSecretVO {
        val app = accessApplicationJpaRepository.findById(id).orElseThrow { NotFoundException("应用不存在: $id") }
        val secret = "uc_${tokenHashing.newOpaqueToken()}"
        app.clientSecretHash = passwordEncoder.encode(secret)
        app.secretVersion += 1
        app.touch()
        auditApplicationService.record(
            AuditCategory.ADMIN,
            "APPLICATION_SECRET_ROTATED",
            actorId,
            app.id,
            app.clientId,
            metadata,
            payload = mapOf("secretVersion" to app.secretVersion),
        )
        return RotatedSecretVO(app.id ?: 0, app.clientId, app.secretVersion, secret)
    }
}
