plugins {
    id("buildsrc.convention.kotlin-jvm")

    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(project(":common"))
    implementation(project(":user-center-api"))

    implementation(libs.bundles.kotlinx.ecosystem)
    implementation(libs.bundles.spring.modulith)
    implementation(libs.bundles.spring.boot)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.bundles.spring.data.base)
    implementation(libs.bundles.jjwt)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
}
