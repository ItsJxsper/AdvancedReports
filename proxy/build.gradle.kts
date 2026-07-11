plugins {
    id("java-library")
    alias(libs.plugins.run.velocity)
    id("eclipse")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.velocity.api)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
  runVelocity {
    velocityVersion(libs.versions.velocity.api.get())
  }

    processResources {
        val props = mapOf("version" to version )
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }

afterEvaluate {
    try {
        project.eclipse.synchronizationTasks(generateTemplates)
    } catch (e: Exception) {
        // Eclipse extension not available
    }
}
