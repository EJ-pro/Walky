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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walky.R
import com.example.walky.data.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn

@Composable
fun LoginScreen(onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    // repository & viewmodel
    val repo = AuthRepository(ctx, activity)

    // 뷰모델 생성 시 AuthRepository에 activity까지 전달
    val vm: LoginViewModel = viewModel {
        LoginViewModel(AuthRepository(ctx, activity))
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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("WalkMate", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))
        // Google 버튼
        OutlinedButton(
            onClick = {
                Log.d("LoginScreen","Google 버튼 클릭")
                googleLauncher.launch(repo.googleIntent())
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(painterResource(R.drawable.ic_google), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Google로 계속하기")
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.signInKakao() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_kakao),
                contentDescription = null,
                tint = LocalContentColor.current
            )
            Spacer(Modifier.width(8.dp))
            Text("카카오로 계속하기")
        }

        if (state is LoginState.Loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
        }
        if (state is LoginState.Error) {
            Spacer(Modifier.height(8.dp))
            Text((state as LoginState.Error).msg, color = MaterialTheme.colorScheme.error)
        }
    }
}
