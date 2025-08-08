package com.example.walky.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.walky.ui.login.LoginScreen
import com.example.walky.ui.screen.splash.SplashScreen

@Composable
fun WalkyNav() {
    val rootNavController = rememberNavController()

    NavHost(
        navController = rootNavController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(rootNavController)
        }
        composable("login") {
            LoginScreen(onSuccess = {
                rootNavController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("main") {
            // 루트 NavController 를 하위로 전달
            MainScaffoldNav(rootNavController)
        }
    }
}

