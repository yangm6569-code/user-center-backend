package cn.scysn

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "User Center Backend API",
        version = "0.1.0",
        description = "User center authentication, identity, authorization, application access, session, and audit APIs."
    )
)
class OpenApiConfig
