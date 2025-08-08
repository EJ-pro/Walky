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
        printAppSha1()
        // Firebase/Kakao 초기화
        FirebaseApp.initializeApp(this)
        KakaoSdk.init(this, getString(R.string.kakao_native_app_key))
        lifecycleScope.launch {
            prefs.isLoggedIn.collect { loggedIn ->
                setContent {
                    WalkyTheme {
                        WalkyNav()
                    }
                }
            }
        }
    }
    private fun printAppSha1() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            val md = MessageDigest.getInstance("SHA1")
            signatures?.forEach { sig ->
                md.update(sig.toByteArray())
                val sha1 = md.digest().joinToString(separator = ":") { byte ->
                    String.format("%02X", byte)
                }
                Log.d("AppSHA1", sha1)
            }
        } catch (e: Exception) {
            Log.e("AppSHA1", "SHA1 계산 실패", e)
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
