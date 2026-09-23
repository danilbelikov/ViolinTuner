import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// `-PfakePitch=true` builds the app on the scripted FakePitchSource(DEMO) instead of the
// microphone: for emulators and for checking every Live state without an instrument.
val fakePitch = providers.gradleProperty("fakePitch").map(String::toBoolean).getOrElse(false)

// `-PplainLive=true` builds Live without the room and the halls behind it (spec 3.27): the plain dark
// field of before, to compare the two from a music stand until one of them is chosen.
val plainLive = providers.gradleProperty("plainLive").map(String::toBoolean).getOrElse(false)

// `-PanalyticsDebug=true` lets a debug build send statistics (spec 5.27). Normally it does not:
// checks on the emulator must not mix into the numbers the real phones send.
val analyticsDebug = providers.gradleProperty("analyticsDebug").map(String::toBoolean).getOrElse(false)

// The AppMetrica key comes from local.properties (git-ignored) or from `-PappMetricaKey=…`; the
// repository never holds it. Without a key the app builds and runs on NoOpAnalytics — a fresh
// clone, another machine and CI need no secret to work (spec 5.27).
val appMetricaKey = providers.gradleProperty("appMetricaKey").orNull
    ?: Properties().apply {
        rootProject.file("local.properties").takeIf(File::exists)?.inputStream()?.use(::load)
    }.getProperty("appMetricaKey", "")

android {
    namespace = "com.violinjourney.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Kept from the template on purpose: a new id is a new app for Android, and the owner's phone
        // holds real data under this one. Set it to "com.violinjourney.app" before publishing
        // (Play refuses com.example.*); data moves over through «Копия данных».
        applicationId = "com.example.violintuner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FAKE_PITCH_SOURCE", fakePitch.toString())
        buildConfigField("boolean", "PLAIN_LIVE", plainLive.toString())
        buildConfigField("String", "APPMETRICA_KEY", "\"$appMetricaKey\"")
        buildConfigField("boolean", "ANALYTICS_IN_DEBUG", analyticsDebug.toString())
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

// Room writes the schema of every database version here; the files are committed so that
// migrations can be written and tested against them.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    // Anonymous statistics (spec 3.34). The modules that collect a location, screenshots, purchases
    // and ad revenue are dropped: nothing here needs them, and what is absent cannot start.
    implementation(libs.appmetrica.analytics) {
        exclude(group = "io.appmetrica.analytics", module = "analytics-location")
        exclude(group = "io.appmetrica.analytics", module = "analytics-screenshot")
        exclude(group = "io.appmetrica.analytics", module = "analytics-billing")
        exclude(group = "io.appmetrica.analytics", module = "analytics-ad-revenue")
    }
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}