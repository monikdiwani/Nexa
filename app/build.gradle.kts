import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.frienddebt"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.fairshare.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }
        val geminiApiKey = properties.getProperty("GEMINI_API_KEY") ?: ""
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ✅ Firebase BoM (handles versions automatically)
    implementation(platform("com.google.firebase:firebase-bom:33.6.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")


    // ✅ WorkManager for scheduled background tasks (daily/night summaries)
    implementation("androidx.work:work-runtime:2.10.1")
    implementation("com.google.guava:guava:33.2.1-android")

    // ✅ ViewPager2 for tab swiping
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // ✅ Spring Physics dynamic animations
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")

    // ✅ Biometric library for fingerprint and pattern lock
    implementation("androidx.biometric:biometric:1.1.0")

    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    
    // ✅ ML Kit Text Recognition for Receipt Scanner
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // ✅ Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")



    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

