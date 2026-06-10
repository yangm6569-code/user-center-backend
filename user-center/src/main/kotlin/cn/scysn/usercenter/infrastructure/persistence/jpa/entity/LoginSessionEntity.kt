package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.SessionStatus
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "uc_login_session")
class LoginSessionEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccountEntity = UserAccountEntity()

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    var application: AccessApplicationEntity? = null

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 128)
    var refreshTokenHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    var status: SessionStatus = SessionStatus.ACTIVE

    @Column(name = "ip", length = 80)
    var ip: String? = null

    @Column(name = "user_agent", length = 500)
    var userAgent: String? = null

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now()

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
}
