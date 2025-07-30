package com.example.walky.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class WalkyScreen(val route: String, val label: String, val icon: ImageVector) {
    object Home : WalkyScreen("home", "홈", Icons.Default.Home)
    object Walks : WalkyScreen("walks", "기록", Icons.Default.List)
    object Map : WalkyScreen("map", "지도", Icons.Default.Home)
    object Community : WalkyScreen("community", "커뮤니티", Icons.Default.Home)
    object Profile : WalkyScreen("profile", "프로필", Icons.Default.Person)

    companion object {
        val items = listOf(Walks, Map, Home, Community, Profile)
    }
}
