package com.example.walky

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.example.walky.data.LoginPrefs
import com.example.walky.ui.navigation.WalkyNav
import com.example.walky.ui.theme.WalkyTheme
import com.google.firebase.FirebaseApp
import com.kakao.sdk.common.KakaoSdk
import kotlinx.coroutines.launch
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = LoginPrefs(this)
        printKeyHash()
        // Firebase/Kakao 초기화
        FirebaseApp.initializeApp(this)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        lifecycleScope.launch {
            prefs.isLoggedIn.collect { loggedIn ->
                setContent {
                    WalkyTheme {
                        if (loggedIn) {
                            WalkyNav(startDestination = if (loggedIn) "home" else "login")
                            // 자동 로그인 시 바로 홈
                        } else {
                            WalkyNav(startDestination = if (loggedIn) "home" else "login")
                        }
                    }
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.P)
    private fun printKeyHash() {
        try {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            // Android 9 이상
            val signers = info.signingInfo?.apkContentsSigners
            // Android 8 이하 호환
                ?: info.signatures
            if (signers != null) {
                for (sig in signers) {
                    val md = MessageDigest.getInstance("SHA-1")
                    md.update(sig.toByteArray())
                    val hash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                    Log.d("KeyHash", hash)
                }
            }
        } catch (e: Exception) {
            Log.e("KeyHash", "키 해시 생성 실패", e)
        }
    }

}
