plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

}

dependencies {
    implementation(libs.common)

    implementation(libs.jetbrains.annotations)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    implementation(libs.okhttp)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.paper.api)
}



