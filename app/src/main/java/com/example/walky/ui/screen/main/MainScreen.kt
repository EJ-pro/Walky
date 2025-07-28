package com.example.walky.ui.screen.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.example.walky.R
import com.example.walky.ui.navigation.BottomNavItem
import com.example.walky.ui.screen.home.HomeScreen

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute by navController.currentBackStackEntryAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate("home")
            }) {
                Icon(painter = painterResource(id = R.drawable.ic_home), contentDescription = "홈")
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                BottomNavItem.items.forEachIndexed { index, item ->
                    if (index == 2) {
                        Spacer(modifier = Modifier.weight(1f, true)) // 홈 공간 확보
                    } else {
                        NavigationBarItem(
                            selected = currentRoute?.destination?.route == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    launchSingleTop = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = item.label,
                                    tint = if (currentRoute?.destination?.route == item.route)
                                        Color(0xFF4A90E2) else Color.Gray
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(BottomNavItem.Home.route) { HomeScreen() }
            composable(BottomNavItem.Walks.route) { Text("산책 기록") }
            composable(BottomNavItem.Map.route) { Text("지도") }
            composable(BottomNavItem.Community.route) { Text("커뮤니티") }
            composable(BottomNavItem.Profile.route) { Text("프로필") }
        }
    }
}
