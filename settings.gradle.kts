pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.github.com/ItsJxsper/advancedreports") {
            name = "githubPackages"
            credentials(PasswordCredentials::class)
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