package com.example.walky.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.walky.ui.screen.home.HomeScreen

@Composable
fun WalkyNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "login") {
        composable("login") {
            com.example.walky.ui.login.LoginScreen {
                nav.navigate("home") { popUpTo("login") { inclusive = true } }
            }
        }
        composable("home") {
            HomeScreen(
                onStartWalk = {
                    // TODO: 산책 시작 화면으로 이동할 네비게이션 로직
                }
            )
        }
    }
}
