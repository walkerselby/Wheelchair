plugins {
    java
    `maven-publish`
    kotlin("jvm") version "1.9.0"
    id("dev.architectury.loom") version "1.4.362"
}

java {
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    "minecraft"(libs.minecraft)
    "mappings"("net.fabricmc:yarn:${libs.versions.yarn.get()}:v2")

    modImplementation(libs.fabric.kotlin)
    modImplementation(libs.fabric.api)
    modImplementation (libs.fabric.loader)
}