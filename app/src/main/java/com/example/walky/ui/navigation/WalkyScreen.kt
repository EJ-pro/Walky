package com.example.walky.ui.navigation

import androidx.annotation.DrawableRes
import com.example.walky.R

sealed class WalkyScreen(
    val route: String,
    val label: String,
    @DrawableRes val iconRes: Int
) {
    object Home      : WalkyScreen("home",      "HOME",       R.drawable.ic_home)
    object Map       : WalkyScreen("map",       "MAP",     R.drawable.ic_map)
    object Walks     : WalkyScreen("walks",     "HISTORY",     R.drawable.ic_history)
    object Community : WalkyScreen("community", "CHAT", R.drawable.ic_chat)
    object Profile   : WalkyScreen("profile",   "MY",   R.drawable.ic_my)

    companion object {
        val items = listOf(Home, Map, Walks, Community, Profile)
    }
}
