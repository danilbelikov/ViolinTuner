import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// `-PfakePitch=true` builds the app on the scripted FakePitchSource(DEMO) instead of the
// microphone: for emulators and for checking every Live state without an instrument. Debug builds
// only, as are the three switches below: a release pins them off.
val fakePitch = providers.gradleProperty("fakePitch").map(String::toBoolean).getOrElse(false)

// `-PfakeScenario=IN_TUNE` (with `-PfakePitch=true`) plays one steady scenario of FakeScenario instead of
// the looping DEMO: a screen that stands still, to compare pixel by pixel before and after a change.
val fakeScenario = providers.gradleProperty("fakeScenario").getOrElse("DEMO")

// `-PplainLive=true` builds Live without the room and the halls behind it (spec 3.27): the plain dark
// field of before, to compare the two from a music stand until one of them is chosen.
val plainLive = providers.gradleProperty("plainLive").map(String::toBoolean).getOrElse(false)

// `-PanalyticsDebug=true` lets a debug build send statistics (spec 5.27). Normally it does not:
// checks on the emulator must not mix into the numbers the real phones send.
val analyticsDebug = providers.gradleProperty("analyticsDebug").map(String::toBoolean).getOrElse(false)

// The AppMetrica key comes from local.properties (git-ignored) or from `-PappMetricaKey=…`; the
// repository never holds it. Without a key the app builds and runs on NoOpAnalytics — a fresh
// clone, another machine and CI need no secret to work (spec 5.27).
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::exists)?.inputStream()?.use(::load)
}
val appMetricaKey = providers.gradleProperty("appMetricaKey").orNull
    ?: localProperties.getProperty("appMetricaKey", "")

// The upload key of the stores lives outside the repository, and so do its passwords: local.properties
// names the file. Without them a release still builds — unsigned, not for a store — so a fresh clone
// and CI need no secret. RuStore and Google Play get the same key: then either store's update installs
// over the other's app (Play App Signing is set up with this key, not with one Google makes).
val releaseStoreFile = localProperties.getProperty("releaseStoreFile")?.let(::file)?.takeIf(File::exists)

android {
    namespace = "com.violinjourney.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The id of the stores; never changes again. Until 1.0 it was the template's
        // com.example.violintuner, and the owner's data moved over through «Копия данных».
        applicationId = "com.violinjourney.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FAKE_PITCH_SOURCE", fakePitch.toString())
        buildConfigField("String", "FAKE_SCENARIO", "\"$fakeScenario\"")
        buildConfigField("boolean", "PLAIN_LIVE", plainLive.toString())
        buildConfigField("String", "APPMETRICA_KEY", "\"$appMetricaKey\"")
        buildConfigField("boolean", "ANALYTICS_IN_DEBUG", analyticsDebug.toString())
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = localProperties.getProperty("releaseStorePassword")
                keyAlias = localProperties.getProperty("releaseKeyAlias")
                keyPassword = localProperties.getProperty("releaseKeyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build is another app beside the one from the store: a check on a real phone
            // never touches its data, and the two keys never meet on one id.
            applicationIdSuffix = ".debug"
        }
        release {
            // The switches above are for the emulator and the checks of a debug build. A store build
            // never takes them, whatever `-P` or `ORG_GRADLE_PROJECT_…` says: a release on the fake
            // source would play DEMO instead of listening (docs/release.md).
            buildConfigField("boolean", "FAKE_PITCH_SOURCE", "false")
            buildConfigField("String", "FAKE_SCENARIO", "\"DEMO\"")
            buildConfigField("boolean", "PLAIN_LIVE", "false")
            buildConfigField("boolean", "ANALYTICS_IN_DEBUG", "false")
            signingConfig = signingConfigs.findByName("release")
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

kotlin {
    compilerOptions {
        // kotlin.time.Instant and Clock, which kotlinx-datetime is built on, are still marked experimental in Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(project(":shared"))
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
    implementation(libs.androidx.media3.common)
    // Lottie (and the AppCompat it brings) serves only LottieOverlay of media3-effect, which «Видео с нотами» does not use
    implementation(libs.androidx.media3.effect) {
        exclude(group = "com.airbnb.android", module = "lottie")
    }
    implementation(libs.androidx.media3.transformer) {
        exclude(group = "com.airbnb.android", module = "lottie")
    }
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
    ksp(libs.hilt.compiler)
    testImplementation(project(":shared-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// XcodeProjectTest reads the configurations of the iOS app: the unit tests run again when those files change, not only
// when the code does.
tasks.withType<Test>().configureEach {
    inputs.files(
        rootProject.file("iosApp/iosApp.xcodeproj/project.pbxproj"),
        rootProject.file("iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme"),
    ).withPropertyName("xcodeProject").withPathSensitivity(PathSensitivity.RELATIVE)
}
