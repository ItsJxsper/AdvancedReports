plugins {
    id("java")
}

group = "de.itsjxsper"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    implementation("com.squareup.okhttp3:okhttp:5.3.0")

    implementation("net.dv8tion:JDA:6.3.1")
}

tasks.test {
    useJUnitPlatform()
}