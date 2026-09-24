import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Test signals shared by the tests of :shared and of :app — a test source set cannot be
// depended on across modules. Never a dependency of production code.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.violinjourney.app.testing"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    iosArm64()
    iosSimulatorArm64()
}
