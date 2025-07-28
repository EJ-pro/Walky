package com.example.walky.data

import com.example.walky.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

class AuthRepository(
    private val ctx: Context,
    private val activity: Activity,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val db = FirebaseFirestore.getInstance()

    /** Google Sign‑In Intent 생성 */
    fun googleIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(ctx.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(activity, gso).signInIntent
    }

    /** Google ID 토큰 → Firebase */
    suspend fun firebaseWithGoogle(idToken: String) =
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    /** 카카오 로그인 (콜백 → 코루틴 래핑) */
    suspend fun loginWithKakao(): Pair<String, String>? =
        suspendCancellableCoroutine { cont ->
            val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
                Log.d("AuthRepo", "카카오 콜백 token=$token, error=$error")
                if (token != null && error == null) {
                    // idToken 또는 accessToken이 null이면 null 반환
                    val id = token.idToken
                    val access = token.accessToken
                    if (id.isNullOrBlank() || access.isNullOrBlank()) {
                        cont.resume(null)           // 그냥 cont.resume 호출
                    } else {
                        cont.resume(id to access)   // 정상 토큰 반환
                    }
                } else {
                    cont.resume(null)             // 에러나 null 토큰일 때
                }
            }

            if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
                UserApiClient.instance.loginWithKakaoTalk(
                    context = activity,
                    callback = callback
                )
            } else {
                UserApiClient.instance.loginWithKakaoAccount(
                    context = activity,
                    callback = callback
                )
            }
        }


    /** 카카오 토큰 → Firebase Auth */
    suspend fun firebaseWithKakao(idToken: String, accessToken: String) =
        auth.signInWithCredential(
            OAuthProvider.newCredentialBuilder("oidc.kakao.com")
                .setIdToken(idToken)
                .setAccessToken(accessToken)
                .build()
        ).await()

    /** 신규 유저 Firestore에 저장 */
    suspend fun registerUserIfNew() {
        auth.currentUser?.let { user ->
            db.collection("users")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
        }
    }
}
