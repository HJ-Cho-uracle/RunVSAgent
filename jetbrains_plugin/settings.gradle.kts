// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm") {
                val gradleProperties = java.util.Properties()
                // In settings.gradle.kts, rootDir refers to the directory containing this settings file.
                val propertiesFile = rootDir.resolve("gradle.properties")
                if (propertiesFile.exists()) {
                    propertiesFile.inputStream().use { gradleProperties.load(it) }
                }
                val platformVersionProperty = gradleProperties.getProperty("platformVersion")

                val kotlinVersion = if (platformVersionProperty?.startsWith("2025.2") == true) {
                    "2.2.0"
                } else {
                    "1.9.22" // Default for 2024.1 and others
                }
                useVersion(kotlinVersion)
            }
        }
    }
    plugins {
        // The version is specified dynamically in the resolutionStrategy block above.
        // We still need to declare the plugin ID here, but without a version.
        id("org.jetbrains.kotlin.jvm")
    }
}

rootProject.name = "RunVSAgent"
