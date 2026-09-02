plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    jacoco
}

group = "de.itsjxsper"
version = "0.0.1-SNAPSHOT"
description = "backend"

// The toolchain comes from the root subprojects block, which reads it from the version catalog.

jacoco {
    toolVersion = libs.versions.jacoco.get()
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

    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.data.jpa)
    //implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.spring.boot.starter.webmvc)
    // Bean Validation used to come in only by accident, through springdoc-openapi.
    // Without an explicit declaration every @Valid silently disappears as soon as
    // the documentation dependency changes.
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.aws.sdk.s3)
    implementation(libs.mapstruct)
    implementation(libs.bucket4j.redis)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.actuator)
    // The application talks to PostgreSQL but shipped without a JDBC driver, so the
    // datasource could never be created. Required at runtime, not just in tests.
    runtimeOnly(libs.postgresql)
    compileOnly(libs.lombok)
    developmentOnly(libs.spring.boot.docker.compose)
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)
    testImplementation(libs.bundles.spring.boot.test)
    //testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly(libs.junit.platform.launcher)
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
