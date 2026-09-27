import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Supabase and Mapbox settings come from local.properties (or env vars on CI) and are compiled into FaunaryConfig,
// so Android and iOS read the same values. Online features switch off when they are blank.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun configValue(key: String): String = System.getenv(key)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(key, "")

val generateFaunaryConfig = tasks.register("generateFaunaryConfig") {
    val url = configValue("SUPABASE_URL")
    val key = configValue("SUPABASE_ANON_KEY")
    val mapboxToken = configValue("MAPBOX_ACCESS_TOKEN")
    val outDir = layout.buildDirectory.dir("generated/faunaryConfig/kotlin")
    inputs.property("url", url)
    inputs.property("key", key)
    inputs.property("mapboxToken", mapboxToken)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/faunary/app/FaunaryConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.faunary.app
            |
            |object FaunaryConfig {
            |    const val SUPABASE_URL = "$url"
            |    const val SUPABASE_ANON_KEY = "$key"
            |    /** Public token for the map, Directions and Search Box APIs. */
            |    const val MAPBOX_ACCESS_TOKEN = "$mapboxToken"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin {
    android {
        namespace = "com.faunary.shared"
        compileSdk = 37
        minSdk = 24
        // Needed so the fonts in composeResources are packaged into the APK.
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // The iOS app links this as a static framework named "Shared" (see iosApp/project.yml).
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
        target.compilerOptions { optIn.add("kotlinx.cinterop.ExperimentalForeignApi") }
    }

    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
        optIn.add("kotlin.io.encoding.ExperimentalEncodingApi")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateFaunaryConfig)
            dependencies {
                implementation(libs.cmp.runtime)
                implementation(libs.cmp.foundation)
                implementation(libs.cmp.ui)
                implementation(libs.cmp.animation)
                implementation(libs.cmp.material3)
                implementation(libs.cmp.resources)
                api(libs.cmp.material.icons.extended)
                api(libs.coil.compose)

                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.io.core)
                api(libs.kotlinx.datetime)
                api(libs.cmp.lifecycle.viewmodel)
                implementation(libs.cmp.lifecycle.runtime.compose)
                api(libs.multiplatform.settings)
                api(libs.room.runtime)
                api(libs.supabase.auth)
                api(libs.supabase.postgrest)
                api(libs.supabase.storage)
                api(libs.supabase.realtime)
                api(libs.supabase.functions)
                implementation(libs.ktor.client.core)
                api(libs.koin.core)
                api(libs.koin.compose.viewmodel)
            }
        }
        androidMain.dependencies {
            implementation(libs.lifecycle.process)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.mapbox)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqlite.bundled)
            implementation(libs.coil.network.ktor3)
            // iOS-only app shell (the Android app keeps its own NavHost on androidx navigation).
            implementation(libs.cmp.navigation.compose)
        }
    }
}

dependencies {
    listOf("kspAndroid", "kspIosArm64", "kspIosSimulatorArm64").forEach { add(it, libs.room.compiler) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

compose.resources {
    packageOfResClass = "com.faunary.shared.resources"
}
