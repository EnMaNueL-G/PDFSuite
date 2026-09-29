import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.optisuite.pdf"
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.optisuite.pdf"
        minSdk         = 26
        targetSdk      = 34
        versionCode    = 10
        versionName    = "1.6.0"

        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Firma de publicación desde keystore.properties (NO se sube al repositorio).
        val kp = rootProject.file("keystore.properties")
        if (kp.exists()) {
            val props = Properties().apply { kp.inputStream().use { load(it) } }
            create("release") {
                storeFile     = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias      = props.getProperty("keyAlias")
                keyPassword   = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.findByName("release")
            // Solo para verificar el APK optimizado con el autotest: ./gradlew assembleRelease -PselftestRelease
            if (project.hasProperty("selftestRelease")) isDebuggable = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable  = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Conservar (no borrar) los avisos de licencia de las librerías (Apache-2.0 lo exige)
            pickFirsts += "/META-INF/NOTICE"
            pickFirsts += "/META-INF/LICENSE"
            excludes += "/META-INF/*.RSA"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.prefs)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.kotlinx.coroutines.android)
    implementation("androidx.appcompat:appcompat:1.6.1")

    // PDF manipulation — iTextG (iText 5 for Android, AGPL)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")   // Apache-2.0 (antes iTextG, AGPL)

    // ML Kit Document Scanner via GMS (camera → PDF, auto perspective correction)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Coil — async image loading for InsertImageScreen preview
    implementation("io.coil-kt:coil-compose:2.5.0")

    debugImplementation(libs.compose.ui.tooling)
}
