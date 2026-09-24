import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The code Android and iOS share: pure Kotlin and Compose, no Android and no Java in commonMain. The
// app keeps its packages — a class moved here is imported exactly as before.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    android {
        namespace = "com.violinjourney.app.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // The common tests run on the JVM too, beside the app's unit tests.
        withHostTest {}
        // Strings and the font of composeResources reach the Android app through its assets.
        androidResources {
            enable = true
        }
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // The iOS app (iosApp) links this framework; Xcode builds it through Gradle.
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.animation)
            implementation(libs.compose.mp.material3)
            implementation(libs.compose.mp.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":shared-testing"))
        }
    }
}

// Res.string / Res.font of the shared strings and font, in the package of the shared UI.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.violinjourney.app.shared.resources"
}
