plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val minSdkVersion: Int by rootProject.extra

android {
    namespace = "com.protonvpn.android.ui_automator_test_util"
    compileSdk = 36

    defaultConfig {
        minSdk = minSdkVersion
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.okhttp3)
    implementation(libs.proton.test.fusion)
    implementation(libs.proton.core.test.performance)
    implementation(libs.androidx.test.rules)
}
