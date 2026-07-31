plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation(libs.jetbrains.annotations)

    implementation(libs.okhttp)

    implementation(libs.jakarta.validation.api)
    implementation(libs.jackson.databind)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.paper.api)
}



