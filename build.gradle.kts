plugins {
    id("java")
    id("maven-publish")
}

// Read outside the subprojects block on purpose: inside it the receiver is the subproject, which has
// no "libs" extension, so referencing the catalog there fails with
// "Extension with name 'libs' does not exist".
val javaToolchain = libs.versions.java.get().toInt()

allprojects {
    group = "de.itsjxsper"

    repositories {
        mavenCentral()
        gradlePluginPortal()
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
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaToolchain))
        }
        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    val moduleVersion = findProperty("moduleVersion") as String?

    if (moduleVersion != null) {
        version = moduleVersion
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])

                pom {
                    name.set(project.name)
                    description.set("Module ${project.name} of monorepo")
                    url.set("https://github.com/ItsJxsper/advancedreports")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("itsjxsper")
                            name.set("Jesper")
                        }
                    }

                    scm {
                        url.set("https://github.com/ItsJxsper/advancedreports")
                        connection.set("scm:git:https://github.com/ItsJxsper/advancedreports.git")
                        developerConnection.set("scm:git:https://github.com/ItsJxsper/advancedreports.git")
                    }
                }
            }
        }

        repositories {
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
    }
}