pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.github.com/ItsJxsper/advancedreports") {
            name = "githubPackages"
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

rootProject.name = "AdvancedReports"
include("common")
include("api")
include("proxy")
include("plugin")
include("backend")
include("discord-bot")