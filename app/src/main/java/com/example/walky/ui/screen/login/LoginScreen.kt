package com.example.walky.ui.login

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walky.R
import com.example.walky.data.AuthRepository
import com.example.walky.data.LoginPrefs
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    // repository & viewmodel
    val prefs = remember { LoginPrefs(ctx) }
    val repo = AuthRepository(ctx, activity)

    // 뷰모델 생성 시 AuthRepository에 activity까지 전달
    val vm: LoginViewModel = viewModel {
        LoginViewModel(AuthRepository(ctx, activity), prefs)
    }
    val state by vm.state.collectAsState()

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val accTask = GoogleSignIn.getSignedInAccountFromIntent(res.data)
        val idToken = accTask.result?.idToken
        vm.signInGoogle(idToken)
    }

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onSuccess()
        }
    }
    val pretendard = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_bold, FontWeight.Bold),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_light, FontWeight.Light)
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(100.dp))
        Text(
            "당신의 건강한",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = pretendard,
            color = Color.Black
        )
        Text(
            "한 걸음을 응원해요!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = pretendard,
            color = Color.Black
        )
        Spacer(Modifier.height(25.dp))
        Text(
            "WalkMate",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = pretendard,
            color = Color(0xFF767676)
        )
        Spacer(Modifier.height(150.dp))
        Button(
            onClick = { vm.signInKakao() },
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                contentColor = Color.Black
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_kakao),
                contentDescription = null,
                tint = Color.Unspecified, // 원본 색 유지
                modifier = Modifier.size(28.dp) // ← 아이콘 크기 조절
            )
            Spacer(Modifier.width(8.dp))
            Text("Login With Kakao",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = pretendard,
                color = Color.Black)
        }

        Spacer(Modifier.height(16.dp))

        // Google 버튼
        OutlinedButton(
            onClick = {
                Log.d("LoginScreen","Google 버튼 클릭")
                googleLauncher.launch(repo.googleIntent())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                tint = Color.Unspecified, // 원본 색 유지
                modifier = Modifier.size(28.dp) // ← 아이콘 크기 조절
            )
            Spacer(Modifier.width(8.dp))
            Text("Login with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = pretendard,
                color = Color.Black)
        }

        if (state is LoginState.Loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        if (state is LoginState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((state as LoginState.Error).msg, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(100.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)){
                    append("계정을 생성하시면 ")
                }

                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("이용약관")
                }

                withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)){
                    append("과 ")
                }

                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("개인정보처리방침")
                }

                withStyle(style = SpanStyle(fontWeight = FontWeight.Medium)){
                    append("에 동이하는 것으로 간주됩니다.")
                }
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            fontFamily = pretendard,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth() // 중앙 정렬 위해 필요
        )
    }
}
