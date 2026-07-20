import java.io.File

// Auto-recreate .env.example if deleted to prevent Secrets plugin crash
val envExampleFile = rootProject.file(".env.example")
if (!envExampleFile.exists()) {
    envExampleFile.writeText("""
        # SYSTEM REQUIRED FILE - DO NOT DELETE
        # This is a system-required placeholder file for the Android Gradle build process.
        # IT DOES NOT CONTAIN ANY SENSITIVE DATA OR REAL API KEYS.
        # Deleting this file will break the compiler and prevent the app from building.

        GEMINI_API_KEY=placeholder_do_not_delete
        CHATGPT_API_KEY=placeholder_do_not_delete
        CLAUDE_API_KEY=placeholder_do_not_delete
    """.trimIndent() + "\n")
}

// Synchronously write .env from system environment variables during Gradle config
val envFile = rootProject.file(".env")
val keysToImport = listOf("GEMINI_API_KEY", "CHATGPT_API_KEY", "CLAUDE_API_KEY")
val envMap = mutableMapOf<String, String>()

if (envFile.exists()) {
    envFile.forEachLine { line ->
        if (line.isNotBlank() && !line.startsWith("#")) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                envMap[parts[0].trim()] = parts[1].trim()
            }
        }
    }
}

var envChanged = false
for (key in keysToImport) {
    val systemValue = System.getenv(key) ?: System.getenv(key.lowercase()) ?: ""
    if (systemValue.isNotBlank() && !systemValue.contains("your_") && !systemValue.contains("placeholder")) {
        if (envMap[key] != systemValue) {
            envMap[key] = systemValue
            envChanged = true
        }
    }
}

if (envChanged || !envFile.exists()) {
    val sb = StringBuilder()
    sb.append("# Generated automatically from system environment variables during Gradle sync\n")
    for ((key, value) in envMap) {
        sb.append("$key=$value\n")
    }
    for (key in keysToImport) {
        if (!envMap.containsKey(key)) {
            sb.append("$key=your_${key.lowercase()}_here\n")
        }
    }
    envFile.writeText(sb.toString())
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.secrets)
}

android {
    namespace = "com.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aistudio.rabiyasaheli.v2.umqkzx"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "4.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_18
        targetCompatibility = JavaVersion.VERSION_18
    }
    kotlinOptions {
        jvmTarget = "18"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.ads)
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}
