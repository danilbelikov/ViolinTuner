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
            // api: the app reads the shared strings too (Res.string), so their types are part of this module's face
            api(libs.compose.mp.resources)
            // the one database and the settings of the app (Room and DataStore are multiplatform)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.datastore.preferences)
        }
        iosMain.dependencies {
            // Android keeps its system SQLite (the database is opened as before); iOS brings its own
            implementation(libs.androidx.sqlite.bundled)
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
// unescape only \n, \t and \u, and would show \' as it is). Never edit the copy.
val composeStrings = tasks.register<Sync>("syncComposeStrings") {
    from(rootProject.layout.projectDirectory.dir("app/src/main/res")) {
        include("values*/strings.xml", "values*/strings_home.xml", "values*/home_catalog.xml")
        filter { line -> line.replace("\\'", "'").replace("\\\"", "\"").replace("\\?", "?").replace("\\@", "@") }
    }
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
