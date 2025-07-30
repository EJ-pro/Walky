package com.example.walky.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.walky.ui.login.LoginScreen
import com.example.walky.ui.screen.splash.SplashScreen

@Composable
fun WalkyNav() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(navController)
        }
        composable("login") {
            LoginScreen(onSuccess = {
                navController.navigate("main") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("main") {
            MainScaffoldNav()
        }
    }
}
