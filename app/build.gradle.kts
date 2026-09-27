import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

android {
    namespace = "com.faunary.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.faunary.app"
        minSdk = 24
        targetSdk = 37
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")

        // Mapbox SDK reads this string resource automatically.
        resValue("string", "mapbox_access_token", localProps.getProperty("MAPBOX_ACCESS_TOKEN", ""))
        // Online features are disabled (app stays local-only) when these are blank.
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\"")
    }

    signingConfigs {
        // Updates only install over an app signed with the same key, so releases reuse the key
        // the first builds were signed with. Keep a backup of this keystore: losing it means
        // every user has to uninstall before they can update.
        create("shared") {
            storeFile = file(localProps.getProperty("SIGNING_STORE_FILE", "${System.getProperty("user.home")}/.android/debug.keystore"))
            storePassword = localProps.getProperty("SIGNING_STORE_PASSWORD", "android")
            keyAlias = localProps.getProperty("SIGNING_KEY_ALIAS", "androiddebugkey")
            keyPassword = localProps.getProperty("SIGNING_KEY_PASSWORD", "android")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("shared")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // One small APK per CPU family instead of a 190 MB universal one; the updater picks the right one.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        // Compressed native libs keep each APK under the 50 MB storage limit; unchanged libs
        // compress to identical bytes, so update patches stay small.
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.mlkit.objects)
    implementation(libs.mlkit.image.labeling)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mapbox)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.work.runtime)
    implementation(libs.lifecycle.process)
    implementation(libs.jbsdiff)
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
