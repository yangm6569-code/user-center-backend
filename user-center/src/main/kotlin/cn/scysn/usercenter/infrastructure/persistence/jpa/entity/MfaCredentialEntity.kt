package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import cn.scysn.usercenter.domain.model.MfaType
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "uc_mfa_credential")
class MfaCredentialEntity : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: UserAccountEntity = UserAccountEntity()

    @Enumerated(EnumType.STRING)
    @Column(name = "mfa_type", nullable = false, length = 24)
    var mfaType: MfaType = MfaType.TOTP

    @Column(name = "label", nullable = false, length = 120)
    var label: String = ""

    @Column(name = "secret_ref", nullable = false, length = 300)
    var secretRef: String = ""

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
}
