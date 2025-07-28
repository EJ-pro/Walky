package com.example.walky.ui.navigation

import com.example.walky.R

sealed class BottomNavItem(
    val label: String,
    val route: String,
    val iconRes: Int
) {
    object Walks : BottomNavItem("산책기록", "walks", R.drawable.ic_walks)
    object Map : BottomNavItem("지도", "map", R.drawable.ic_map)
    object Home : BottomNavItem("홈", "home", R.drawable.ic_home)
    object Community : BottomNavItem("커뮤니티", "community", R.drawable.ic_walks)
    object Profile : BottomNavItem("프로필", "profile", R.drawable.ic_profile)

    companion object {
        val items = listOf(Walks, Map, Home, Community, Profile)
    }
}
