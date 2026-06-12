plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.3.5")
    implementation("org.springframework.boot:spring-boot-starter-validation:3.3.5")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.3.5")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
    implementation("org.springframework.security:spring-security-crypto:6.3.4")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.22")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.3.5")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
