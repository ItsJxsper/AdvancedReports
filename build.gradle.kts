plugins {
    id("java")
    id("maven-publish")
}

allprojects {
    group = "de.itsjxsper"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
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
                credentials(PasswordCredentials::class)
            }
        }
    }
}