package cn.scysn.iam.infrastructure.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["cn.scysn.iam.infrastructure.persistence.jpa.repository"])
@EntityScan(basePackages = ["cn.scysn.iam.infrastructure.persistence.jpa.entity"])
class IamPersistenceConfiguration
