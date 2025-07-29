package com.example.walky.ui.login

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Range
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
import com.example.walky.data.LocationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity

    val prefs = remember { LoginPrefs(ctx) }
    val repo = AuthRepository(ctx, activity)

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
    val context = LocalContext.current
    val fused = remember { LocationRepository() }

    var userLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 위치 가져오기
            CoroutineScope(Dispatchers.Main).launch {
                userLocation = runCatching { fused.getCurrentLocation(context) }.getOrNull()
                Log.d("LoginScreen", "위치: $userLocation")
            }
        }
    }

    // 위치 권한 요청 실행
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val pretendard = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_bold, FontWeight.Bold),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_light, FontWeight.Light)
    )
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(100.dp))

        Text(
            text = "당신의 건강한",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = pretendard,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "한 걸음을 응원해요!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = pretendard,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(25.dp))

        Text(
            text = "WalkMate",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = pretendard,
            color = Color(0xFF767676)
        )

        Spacer(Modifier.height(150.dp))

        // 카카오 로그인 버튼
        Button(
            onClick = { vm.signInKakao() },
            shape = RoundedCornerShape(40.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                contentColor = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_kakao),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Login With Kakao",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = pretendard,
                color = Color.Black
            )
        }

        Spacer(Modifier.height(16.dp))

        // 구글 로그인 버튼
        OutlinedButton(
            onClick = {
                Log.d("LoginScreen", "Google 버튼 클릭")
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
                tint = Color.Unspecified,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Login with Google",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = pretendard,
                color = Color.Black
            )
        }

        if (state is LoginState.Loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        if (state is LoginState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = (state as LoginState.Error).msg,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(100.dp))

        // 하단 약관 안내 텍스트 (ClickableText)
        val annotatedText = buildAnnotatedString {
            append("계정을 생성하시면 ")

            pushStringAnnotation(tag = "TERMS", annotation = "terms")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                append("이용약관")
            }
            pop()

            append("과 ")

            pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                append("개인정보처리방침")
            }
            pop()

            append("에 동의하는 것으로 간주됩니다.")
        }

        ClickableText(
            text = annotatedText,
            modifier = Modifier.fillMaxWidth(),
            style = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                fontFamily = pretendard,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            ),
            onClick = { offset ->
                annotatedText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                    .firstOrNull()?.let {
                        // "이용약관" 클릭 시
                        Log.d("LoginScreen", "이용약관 클릭됨")
                        // TODO: Dialog 띄우거나 WebView 이동
                        showTermsDialog = true
                    }

                annotatedText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
                    .firstOrNull()?.let {
                        // "개인정보처리방침" 클릭 시
                        Log.d("LoginScreen", "개인정보처리방침 클릭됨")
                        // TODO: Dialog 띄우거나 WebView 이동
                        showPrivacyDialog = true
                    }
            }
        )
        if (showTermsDialog) {
            AlertDialog(
                onDismissRequest = { showTermsDialog = false },
                confirmButton = {
                    TextButton(onClick = { showTermsDialog = false }) {
                        Text("닫기", fontFamily = pretendard)
                    }
                },
                title = { Text("이용약관", fontWeight = FontWeight.Bold, fontFamily = pretendard) },
                text = {
                    Text(
                        text = """
                    본 애플리케이션 'WalkMate'는 사용자의 건강한 산책 습관 형성을 위한 앱입니다.
                    
                    사용자는 아래와 같은 서비스 제공에 동의합니다:
                    - 산책 거리 및 기록 저장
                    - 날씨 정보 제공
                    - 위치 기반 기능 활용

                    사용자는 앱 사용 시 타인의 권리를 침해하거나 서비스를 방해하지 않아야 하며,
                    본 약관은 언제든지 변경될 수 있습니다. 변경 시 앱 내 공지사항을 통해 고지됩니다.
                """.trimIndent(),
                        fontSize = 14.sp,
                        fontFamily = pretendard
                    )
                }
            )
        }
        if (showPrivacyDialog) {
            AlertDialog(
                onDismissRequest = { showPrivacyDialog = false },
                confirmButton = {
                    TextButton(onClick = { showPrivacyDialog = false }) {
                        Text("닫기", fontFamily = pretendard)
                    }
                },
                title = { Text("개인정보처리방침", fontWeight = FontWeight.Bold, fontFamily = pretendard) },
                text = {
                    Text(
                        text = """
                    'WalkMate'는 사용자 인증 및 산책 기록 기능을 위해 최소한의 개인정보를 수집합니다.

                    수집 항목:
                    - 이메일 주소 (Google/Kakao 로그인 시)
                    - 기기 위치 정보 (산책 경로 및 날씨 제공 목적)

                    수집된 정보는 외부에 공개되지 않으며, 보안적으로 안전하게 관리됩니다.
                    사용자는 언제든지 정보 삭제 요청이 가능하며, 관련 문의는 앱 설정 > 문의하기를 통해 가능합니다.
                """.trimIndent(),
                        fontSize = 14.sp,
                        fontFamily = pretendard
                    )
                }
            )
        }

    }
}
