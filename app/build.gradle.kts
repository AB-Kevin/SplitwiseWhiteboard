plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.kevin.splitwisewhiteboard"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kevin.splitwisewhiteboard"
        minSdk = 26
        targetSdk = 37
        versionCode = 10002
        versionName = "1.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    // Networking (talks to Splitwise's website endpoints directly).
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    // Encrypted storage for the Splitwise session cookie.
    implementation("androidx.security:security-crypto:1.1.0")

    // Background refresh of the widget.
    implementation("androidx.work:work-runtime:2.11.2")

    // Coroutines, used from activities, the widget provider, and WorkManager.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}