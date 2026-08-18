plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven {
        name = "githubPackages"
        url = uri("https://maven.pkg.github.com/ItsJxsper/advancedreports")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("actor")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("token")
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.jetbrains.annotations)

    implementation(libs.okhttp)

    implementation(libs.jakarta.validation.api)
    
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)


    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    compileOnly(libs.paper.api)
}



