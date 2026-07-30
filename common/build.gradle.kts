plugins {
    id("java")
    id("maven-publish")
}

description = "Common module - shared utilities and models"

dependencies {
    implementation(libs.jetbrains.annotations)

    implementation(libs.jakarta.validation.api)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
