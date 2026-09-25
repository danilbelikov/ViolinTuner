import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The code Android and iOS share: pure Kotlin and Compose, no Android and no Java in commonMain. The
// app keeps its packages — a class moved here is imported exactly as before.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
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
    compilerOptions {
        // expect/actual classes (PlatformFile) are Beta in Kotlin 2.2.
        freeCompilerArgs.add("-Xexpect-actual-classes")
        // kotlin.time.Instant and Clock, which kotlinx-datetime is built on, are still marked experimental in Kotlin 2.2.
        optIn.add("kotlin.time.ExperimentalTime")
        // the multiplatform BackHandler of the shared screens
        optIn.add("androidx.compose.ui.ExperimentalComposeUiApi")
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
            // api: dates are in the types the app reads from shared (LocalDate of a practice day, …)
            api(libs.kotlinx.datetime)
            implementation(libs.compose.mp.runtime)
            implementation(libs.compose.mp.foundation)
            implementation(libs.compose.mp.ui)
            implementation(libs.compose.mp.animation)
            implementation(libs.compose.mp.material3)
            // the system «back» in shared screens (androidx.activity on Android)
            implementation(libs.compose.mp.ui.backhandler)
            // api: the app reads the shared strings too (Res.string), so their types are part of this module's face
            api(libs.compose.mp.resources)
            // the one database and the settings of the app (Room and DataStore are multiplatform)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences)
            // view models of the screens and lifecycle-aware collection; api: the app's Hilt view models extend them
            api(libs.jb.lifecycle.viewmodel)
            api(libs.jb.lifecycle.viewmodel.savedstate)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
        }
        androidMain.dependencies {
            // the system pickers (a photo for the profile) behind the shared calls
            implementation(libs.androidx.activity.compose)
            // the app's own camera of «Снять под минусовку» (spec 3.32), behind the shared screen
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.compose)
            implementation(libs.androidx.camera.core)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.video)
        }
        iosMain.dependencies {
            // Android keeps its system SQLite (the database is opened as before); iOS brings its own
            implementation(libs.androidx.sqlite.bundled)
            // the app's graph of screens; Android keeps its androidx navigation in app
            implementation(libs.jb.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":shared-testing"))
        }
    }
}

// The words of the app live in app/src/main/res — Russian the source, values/ the English fallback, eight more
// (spec 3.26) — where Android reads them and LocalizationTest checks them. The shared code and iOS read the same
// files as compose resources: copied here at build time, with the escapes Android needs undone (compose resources
// unescape only \n, \t and \u, and would show \' and the %% of a formatted string as they are). Never edit the copy.
val composeStrings = tasks.register<Sync>("syncComposeStrings") {
    val androidEscapes = listOf("\\'" to "'", "\\\"" to "\"", "\\?" to "?", "\\@" to "@", "%%" to "%")
    // a new escape must make the copy again: the list is an input, the lambda below is not
    inputs.property("androidEscapes", androidEscapes.toString())
    from(rootProject.layout.projectDirectory.dir("app/src/main/res")) {
        include("values*/strings.xml", "values*/strings_home.xml", "values*/home_catalog.xml")
        filter { line -> androidEscapes.fold(line) { text, (escaped, plain) -> text.replace(escaped, plain) } }
    }
    // the rest of the shared resources — the pictures of the journey and the home — lie in the usual place; the
    // custom directory below replaces it, so they are copied along
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    into(layout.buildDirectory.dir("generated/composeStrings"))
}

// Res.string / Res.font of the shared strings and font, in the package of the shared UI.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.violinjourney.app.shared.resources"
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = layout.dir(composeStrings.map { it.destinationDir }),
    )
}

// Room writes the database code for each platform; the schemas of all versions stay in app/schemas, where the
// migration tests read them.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}

ksp {
    arg("room.schemaLocation", "${rootDir}/app/schemas")
}
