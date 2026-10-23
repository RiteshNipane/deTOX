import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.detox"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.detox"
        minSdk = 30 // Android 11+ (takeScreenshot needs API 30)
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // Release signing comes from env vars (CI) or keystore.properties (local, git-ignored).
    // With neither present the release build is simply unsigned.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun secret(env: String, prop: String): String? = System.getenv(env) ?: keystoreProps.getProperty(prop)
    val storePath = secret("DETOX_KEYSTORE_PATH", "storeFile")
    signingConfigs {
        if (storePath != null) {
            create("release") {
                storeFile = file(storePath)
                storePassword = secret("DETOX_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = secret("DETOX_KEY_ALIAS", "keyAlias")
                keyPassword = secret("DETOX_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (storePath != null) signingConfig = signingConfigs.getByName("release")
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // The model is memory-mapped straight from the APK, so it must not be compressed.
    androidResources { noCompress += "tflite" }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
