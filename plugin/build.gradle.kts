import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml

plugins {
    id("java-library")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory.paper)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
}

paperPluginYaml {
    main = "de.itsjxsper.advancedreports.plugin.AdvancedReportsPlugin"
    bootstrapper = "de.itsjxsper.advancedreports.plugin.AdvancedReportsPluginBootstrap"
    loader = "de.itsjxsper.advancedreports.plugin.AdvancedReportsPluginLoader"
    apiVersion = "26.2"

    load = BukkitPluginYaml.PluginLoadOrder.STARTUP
    authors.addAll("ItsJxsper")
    website = "https://github.com/ItsJxsper/AdvancedReports"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
    }
}
