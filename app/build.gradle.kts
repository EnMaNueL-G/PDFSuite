plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace  = "com.enmanuelgil.pdfsuite"
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.enmanuelgil.pdfsuite"
        minSdk         = 26
        targetSdk      = 34
        versionCode    = 7
        versionName    = "1.5.0"

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
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
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/LICENSE"
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
    implementation("com.itextpdf:itextg:5.5.10")

    // ML Kit Document Scanner via GMS (camera → PDF, auto perspective correction)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    // Coil — async image loading for InsertImageScreen preview
    implementation("io.coil-kt:coil-compose:2.5.0")

    debugImplementation(libs.compose.ui.tooling)
}
