plugins {
    id("buildsrc.convention.kotlin-jvm")

    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.spring)
}

dependencies {
    implementation(kotlin("reflect"))

    implementation(libs.bundles.kotlinx.ecosystem)
    implementation(libs.bundles.spring.boot)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.kotlin.logging)

    testImplementation(libs.bundles.testing)
}
