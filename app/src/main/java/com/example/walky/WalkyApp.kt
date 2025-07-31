// app/src/main/java/com/example/walky/WalkyApp.kt
package com.example.walky

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class WalkyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase 초기화
        FirebaseApp.initializeApp(this)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        KakaoMapSdk.init(this, getString(R.string.kakao_map_key))
        Log.d("WalkyApp", "KakaoSdk initialized with key=${getString(R.string.kakao_native_app_key)}")
    }
}
