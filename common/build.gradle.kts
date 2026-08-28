plugins {
    id("java-library")
    id("maven-publish")
}

description = "Common module - shared utilities and models"

dependencies {
    // ObjectMapper appears in the public signature of ApiException.fromHttpResponse ->
    // must be on consumers' compile classpath, not just their runtime classpath.
    api(libs.jackson.databind)
    // jakarta.validation.constraints annotations sit on the public DTO record components.
    api(libs.jakarta.validation.api)
    // Not imported anywhere in common; consumers need it registered to (de)serialize the
    // java.time fields on the DTOs.
    runtimeOnly(libs.jackson.datatype.jsr310)
    // org.jetbrains annotations are CLASS-retention only - no runtime need.
    compileOnly(libs.jetbrains.annotations)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
