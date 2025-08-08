package com.example.walky.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.walky.R
import com.example.walky.ui.screen.chat.ChatScreen
import com.example.walky.ui.screen.home.HomeScreen
import com.example.walky.ui.screen.map.MapScreen
import com.example.walky.ui.screen.profile.ProfileScreen
import com.example.walky.ui.screen.walk.WalkHistoryScreen

@Composable
fun MainScaffoldNav(rootNavController: NavHostController) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    val pretendard = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_bold,    FontWeight.Bold),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_light,   FontWeight.Light)
    )
    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(32.dp),
                        clip = false
                    )
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp)),      // ← 여기서 shape 적용
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                WalkyScreen.items.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = screen.iconRes),
                                contentDescription = screen.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = screen.label,
                                fontFamily = pretendard,                    // pretendard 적용
                                fontSize = 10.sp,                           // 원하는 크기로 조정
                                fontWeight = FontWeight.Bold,               // 항상 Bold
                                color = if (selected) Color.Black
                                else Color(0xFFC4C4C4),
                                textAlign = TextAlign.Center
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Color.Black,
                            unselectedIconColor = Color.Gray,
                            indicatorColor      = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WalkyScreen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(WalkyScreen.Home.route)      { HomeScreen() }
            composable(WalkyScreen.Map.route)       { MapScreen() }
            composable(WalkyScreen.Walks.route)     { WalkHistoryScreen() }
            composable(WalkyScreen.Community.route) { ChatScreen() }
            composable(WalkyScreen.Profile.route)   { ProfileScreen(navController = rootNavController) }
        }
    }
}
