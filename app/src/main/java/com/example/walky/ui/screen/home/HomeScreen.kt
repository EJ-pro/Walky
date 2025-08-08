@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.walky.ui.screen.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.walky.R
import com.example.walky.ui.home.HomeViewModel
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    userName: String = "재희",
    onStartWalk: () -> Unit = {}
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    // 위치 권한 요청
    val locLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) vm.fetchLocationAndWeather(context)
    }
    LaunchedEffect(Unit) {
        locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var expanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // ── SECTION 1: 검은 배경 (인사말 + 토글) ─────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Hello, $userName",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "${userName}님, 오늘도 걸어볼까요?",
                            color = Color(0xFFBEE7A5),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    IconButton(onClick = { /* 알림 */ }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kakao),
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Image(
                        painter = rememberAsyncImagePainter(state.dog?.avatarUrl ?: ""),
                        contentDescription = "프로필",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .clickable { expanded = !expanded }
                        .padding(top = 8.dp)
                )
            }
        }

        // ── SECTION 2: F5F5F5 배경 (Today 카드 + 챌린지 + 모드 토글) ────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF000000))
                    .padding(16.dp)
            ) {
                // Today 카드 (펼쳐졌을 때만)
                if (expanded) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))

                            state.weather?.let { w ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_sun),
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = Color.Unspecified
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(w.city, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${w.tempC}°C  ${w.description}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("맑음", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "습도 ${w.humidity}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text("오늘의 걸음 통계", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${state.todaySteps}걸음", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${"%.1f".format(state.todayDistanceKm)}km",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${state.todayDurationMin}분",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Slider(
                                value = (state.todaySteps / state.stepGoal.toFloat()).coerceIn(
                                    0f,
                                    1f
                                ),
                                onValueChange = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onStartWalk,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                            ) {
                                Text("Start", color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp)
            ) {
                // Today Challenge 카드
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAF3C0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Today Challenge",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Text(
                                    "• 반려동물 산책 인증!\n강아지나 고양이와 산책 포토를 찍어보세요!\n귀여움과 건강을 동시에.",
                                    Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Image(
                            painter = painterResource(R.drawable.ic_kakao),
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 모드 토글
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf("MY", "PET")
                    var selected by remember { mutableStateOf("MY") }
                    modes.forEach { mode ->
                        OutlinedButton(
                            onClick = { selected = mode },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .height(32.dp)
                                .width(60.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected == mode) Color.Black else Color.White,
                                contentColor = if (selected == mode) Color.White else Color.Gray
                            )
                        ) {
                            Text(mode)
                        }
                    }
                }
            }
        }

        // ── SECTION 3: 흰 배경 (최근 산책 기록) ────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp)
            ) {
                Text(
                    "최근 산책 기록",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }

        items(state.recentWalks) { walk ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(walk.title, color = Color.Black)
                    Text(
                        walk.date.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // 마지막 여백
        item {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .background(Color.White)
            )
        }
    }
}
