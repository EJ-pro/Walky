package com.example.walky.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.walky.R

@Composable
fun PetModeToggle(
    isPetMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val toggleSize = 40.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (isPetMode) 56.dp else 0.dp,
        label = ""
    )

    Box(
        modifier = Modifier
            .width(96.dp)
            .height(toggleSize)
            .clip(RoundedCornerShape(50))
            .background(Color.LightGray.copy(alpha = 0.3f))
            .clickable { onToggle(!isPetMode) }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(toggleSize)
                .clip(CircleShape)
                .background(Color.White)
                .padding(8.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isPetMode) R.drawable.ic_profile else R.drawable.ic_home),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
