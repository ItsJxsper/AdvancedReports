plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
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
            username = providers.gradleProperty("gpr.user")
                .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                .orNull
            password = providers.gradleProperty("gpr.token")
                .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                .orNull
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    //implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    // Bean Validation used to come in only by accident, through springdoc-openapi.
    // Without an explicit declaration every @Valid silently disappears as soon as
    // the documentation dependency changes.
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2")
    implementation("software.amazon.awssdk:s3:2.42.35")
    implementation("org.mapstruct:mapstruct:1.6.3")
    implementation("com.bucket4j:bucket4j-redis:8.10.1")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // From JDK 21 on, Mockito needs the agent declared explicitly. Resolution has to stay lazy:
    // a direct configurations.testRuntimeClasspath.get().files resolves the configuration
    // during the configuration phase already - on EVERY invocation, including 'build -x test'
    // and 'bootBuildImage' - and it also breaks the configuration cache.
    val testRuntimeClasspath: FileCollection = configurations.testRuntimeClasspath.get()
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        val agent = testRuntimeClasspath.files.find { it.name.contains("mockito-core") }
        if (agent != null) listOf("-javaagent:${agent.absolutePath}") else emptyList()
    })
}

// Runs everything that does not need a Docker daemon: pure Mockito unit tests
// and @WebMvcTest slices. End-to-end (*E2ETest) tests match "*Test" too, so they
// have to be excluded explicitly; "*IT" never matches "*Test" in the first place.
tasks.register<Test>("unitTest") {
    description = "Runs only the tests that do not require Docker."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    filter {
        includeTestsMatching("*Test")
        excludeTestsMatching("*E2ETest")
    }
}

// Counterpart to unitTest: only the tests that need a Docker daemon, so CI does not
// re-run the unit tests in the integration stage.
tasks.register<Test>("integrationTest") {
    description = "Runs only the tests that require Docker (Testcontainers)."
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    filter {
        includeTestsMatching("*IT")
        includeTestsMatching("*E2ETest")
    }
}

// The standard 'test' task would run EVERYTHING, including the Testcontainers tests -
// so 'gradlew build' silently required a Docker daemon.
// Instead 'check' depends on unitTest only; integrationTest is invoked explicitly.
tasks.named<Test>("test") {
    enabled = false
}

tasks.named("check") {
    dependsOn(tasks.named("unitTest"))
}

// unitTest and integrationTest write separate .exec files - the report merges both, so
// that coverage does not fall apart per stage.
// Important: register only build/jacoco as input, not the whole build directory -
// otherwise Gradle treats every unitTest output as an undeclared dependency.
tasks.jacocoTestReport {
    // unitTest provides the baseline coverage and runs without Docker - hence dependsOn.
    dependsOn(tasks.named("unitTest"))
    // integrationTest is optional (it needs Docker): only enforce ordering, so that
    // 'integrationTest jacocoTestReport' works in a single invocation.
    mustRunAfter(tasks.named("integrationTest"))

    executionData.setFrom(
        layout.buildDirectory.dir("jacoco").map { dir -> fileTree(dir) { include("*.exec") } }
    )

    reports {
        xml.required = true
        html.required = true
    }
}
