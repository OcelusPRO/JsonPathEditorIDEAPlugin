plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.2"
    kotlin("jvm")
}

group = "fr.ftnl"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugins("com.intellij.modules.json", "com.intellij.jsonpath", "com.intellij.java")
    }
    implementation("com.jayway.jsonpath:json-path:3.0.0")
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        
        ideaVersion {
            sinceBuild = "252.25557"
        }
        
        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks { // Set the JVM compatibility versions
    withType<JavaCompile> {}
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
    jvmToolchain(21)
}


buildscript {
    repositories {
        mavenCentral()
    }
}

tasks {
    withType<JavaCompile> {
        options.release.set(21)
    }
    
    buildPlugin {
    }
}