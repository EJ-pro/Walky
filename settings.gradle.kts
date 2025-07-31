pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
    plugins {
        id("com.android.application") version "8.4.0" apply false
        id("org.jetbrains.kotlin.android") version "2.1.0" apply false
        id("com.google.gms.google-services") version "4.4.3"   // 🔑
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // ✅ 카카오 로그인 SDK (v2-* 용)
        maven { url = uri("https://devrepo.kakao.com/nexus/content/groups/public/") }

        // ✅ 벡터맵 SDK
        maven("https://devrepo.kakao.com/nexus/repository/kakaomap-releases/")
    }
}

rootProject.name = "Walky"
include(":app")
