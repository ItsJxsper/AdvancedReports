plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "de.itsjxsper"
version = "0.0.1-SNAPSHOT"
description = "backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
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

    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    //implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("software.amazon.awssdk:s3:2.42.35")
    implementation("org.mapstruct:mapstruct:1.6.3")
    implementation("com.bucket4j:bucket4j-redis:8.10.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // The application talks to PostgreSQL but shipped without a JDBC driver, so the
    // datasource could never be created. Required at runtime, not just in tests.
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    //testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-minio")
    testImplementation("com.redis:testcontainers-redis")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()

    val mockitoAgent = configurations.testRuntimeClasspath.get()
        .files
        .find { it.name.contains("mockito-core") }

    if (mockitoAgent != null) {
        jvmArgs("-javaagent:${mockitoAgent.absolutePath}")
    }
}

// Runs everything that does not need a Docker daemon: pure Mockito unit tests
// and @WebMvcTest slices. Integration (*IT) and end-to-end (*E2ETest) tests are
// excluded because they start Testcontainers.
tasks.register<Test>("unitTest") {
    description = "Runs only the tests that do not require Docker."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    filter {
        includeTestsMatching("*Test")
        excludeTestsMatching("*IT")
        excludeTestsMatching("*E2ETest")
    }
}
