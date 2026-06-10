package cn.scysn.usercenter.infrastructure.persistence.jpa.entity

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import java.time.Instant

@MappedSuperclass
abstract class BaseJpaEntity {
    @Column(name = "create_time", nullable = false)
    var createTime: Instant = Instant.now()

    @Column(name = "update_time", nullable = false)
    var updateTime: Instant = Instant.now()

    fun touch() {
        updateTime = Instant.now()
    }
}
