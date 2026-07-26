import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.5")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    pluginConfiguration {
        // Compatibility range that patches into the built plugin.xml.
        // 233 (2023.3) matches the platform APIs this plugin uses - all long-stable.
        // untilBuild is left open so new IDE releases are not locked out; run
        // `verifyPlugin` before publishing to confirm nothing newer than 233 slipped in.
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }

    // Marketplace upload. Reads the same secrets you used for glb-viewer from the
    // environment; the providers are lazy, so a normal build works without them set.
    signing {
        certificateChainFile = file(System.getenv("CERTIFICATE_CHAIN") ?: "chain.crt")
        privateKeyFile = file(System.getenv("PRIVATE_KEY") ?: "private.pem")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}
