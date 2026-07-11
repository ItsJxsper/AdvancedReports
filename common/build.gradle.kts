plugins {
    id("java")
    id("maven-publish")
}

description = "Common module - shared utilities and models"

dependencies {
    implementation("org.jetbrains:annotations:26.1.0")

    implementation("jakarta.validation:jakarta.validation-api:3.1.1")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}
