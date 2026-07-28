pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.github.com/ItsJxsper/advancedreports") {
            name = "githubPackages"
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("actor")
                password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("token")
            }
        }
    }
}

rootProject.name = "AdvancedReports"
include("common")
include("api")
include("proxy")
include("plugin")
include("backend")
include("discord-bot")