package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.ApplicationStatus
import cn.scysn.usercenter.domain.model.ApplicationType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "uc_access_application")
class AccessApplicationEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "client_id", nullable = false, unique = true, length = 120)
    var clientId: String = ""

    @Column(name = "name", nullable = false, length = 160)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "app_type", nullable = false, length = 24)
    var appType: ApplicationType = ApplicationType.WEB

    @Column(name = "owner_dept", length = 120)
    var ownerDept: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    var status: ApplicationStatus = ApplicationStatus.ACTIVE

    @Column(name = "client_secret_hash", length = 120)
    var clientSecretHash: String? = null

    @Column(name = "secret_version", nullable = false)
    var secretVersion: Int = 1

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "redirect_uris", nullable = false, columnDefinition = "jsonb")
    var redirectUris: MutableList<String> = mutableListOf()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "logout_uris", nullable = false, columnDefinition = "jsonb")
    var logoutUris: MutableList<String> = mutableListOf()

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_scopes", nullable = false, columnDefinition = "jsonb")
    var allowedScopes: MutableList<String> = mutableListOf()

    @Column(name = "access_token_ttl_seconds", nullable = false)
    var accessTokenTtlSeconds: Long = 900

    @Column(name = "refresh_token_ttl_seconds", nullable = false)
    var refreshTokenTtlSeconds: Long = 604800
}
