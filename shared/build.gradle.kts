import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The code Android and iOS share: pure Kotlin, no Android and no Java in commonMain. The app keeps
// its packages — a class moved here is imported exactly as before.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
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
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":shared-testing"))
        }
    }
}
