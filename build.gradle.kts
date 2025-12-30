import org.jetbrains.intellij.tasks.PatchPluginXmlTask

plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "ru.fixprice.tools"
version = "0.1.5"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

intellij {
    version.set("2024.1.7")
    type.set("IC")
}

tasks.withType<PatchPluginXmlTask>().configureEach {
    sinceBuild.set("241")
    untilBuild.set("999.*")
}
