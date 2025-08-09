plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") version "4.4.3"
}

android {
    namespace = "com.example.walky"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.walky"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}


dependencies {
    // ── Compose ─────────────────────────
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── Firebase BoM & Auth ────────────────
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-auth")

    // ── Firestore‑KTX: 반드시 버전 명시 or BOM 없이 단독 선언 ──
    implementation("com.google.firebase:firebase-firestore-ktx:24.9.1")

    // ── 로그인 SDKs ───────────────────────
    implementation("com.google.android.gms:play-services-auth:21.1.0")
    implementation("com.kakao.sdk:v2-user:2.18.0")
    implementation("com.kakao.sdk:v2-auth:2.18.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0")

    implementation("io.coil-kt:coil-compose:2.4.0")

    // 위치 정보 (Fused Location Provider)
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // (선택) 권한 요청을 돕는 Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("com.kakao.maps.open:android:2.11.9")
    implementation("androidx.health.connect:connect-client:1.2.0-alpha01")

}
