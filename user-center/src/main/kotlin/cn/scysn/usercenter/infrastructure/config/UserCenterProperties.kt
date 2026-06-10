package cn.scysn.usercenter.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("user-center.security")
data class UserCenterSecurityProperties(
    var jwtSecretKey: String = "dev-only-change-me-dev-only-change-me-dev-only-change-me-32",
    var issuer: String = "user-center",
    var accessTokenTtlSeconds: Long = 900,
    var refreshTokenTtlSeconds: Long = 604800,
    var loginFailureThreshold: Int = 5,
    var loginLockMinutes: Long = 15,
)

@ConfigurationProperties("user-center.cors")
data class UserCenterCorsProperties(
    var allowedOrigins: List<String> = listOf("http://localhost:3000", "http://localhost:5173"),
)

@ConfigurationProperties("user-center.bootstrap")
data class UserCenterBootstrapProperties(
    var adminUsername: String = "admin",
    var adminPassword: String = "Admin@123456",
    var adminEmail: String = "admin@example.com",
)
