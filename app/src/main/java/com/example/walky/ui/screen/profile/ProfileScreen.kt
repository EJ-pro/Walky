package com.example.walky.ui.screen.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth

@Composable
fun ProfileScreen(
    navController: NavController
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {
                // Firebase 로그아웃 처리
                FirebaseAuth.getInstance().signOut()
                // 로그인 화면으로 이동 (메인 스택 초기화)
                navController.navigate("login") {
                    popUpTo("main") { inclusive = true }
                }
            },
            modifier = Modifier
                .width(200.dp)
                .height(50.dp)
        ) {
            Text("로그아웃", style = MaterialTheme.typography.titleMedium)
        }
    }
}
