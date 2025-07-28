package com.example.walky.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.example.walky.ui.login.LoginScreen
import com.example.walky.ui.screen.home.HomeScreen

@Composable
fun WalkyNav(startDestination: String = "login")  {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(onSuccess = {
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("home") {
            HomeScreen()
        }
    }
}
