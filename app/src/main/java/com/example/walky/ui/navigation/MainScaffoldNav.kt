package com.example.walky.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.walky.ui.screen.chat.ChatScreen
import com.example.walky.ui.screen.home.HomeScreen
import com.example.walky.ui.screen.map.MapScreen
import com.example.walky.ui.screen.profile.ProfileScreen
import com.example.walky.ui.screen.walk.WalkHistoryScreen

@Composable
fun MainScaffoldNav() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                WalkyScreen.items.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = WalkyScreen.Home.route,
            modifier = Modifier.padding(inner)
        ) {
            composable(WalkyScreen.Home.route) { HomeScreen() }
            composable(WalkyScreen.Map.route) { MapScreen() }
            composable(WalkyScreen.Walks.route) { WalkHistoryScreen() }
            composable(WalkyScreen.Community.route) { ChatScreen() }
            composable(WalkyScreen.Profile.route) { ProfileScreen() }
        }
    }
}
