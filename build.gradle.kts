plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.vyibc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    // HTTP client for Google Translate API
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    version.set("2023.2.5")
    type.set("IC")
    plugins.set(listOf("java"))
    
    pluginName.set("Code Assistant")
    updateSinceUntilBuild.set(false)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("252.*")
    }
}

kotlin {
    jvmToolchain(17)
}