@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.walky.ui.screen.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.walky.R
import com.example.walky.ui.home.HomeViewModel
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(onStartWalk: () -> Unit = {}) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    // 위치 권한 요청 런처
    val locationLauncher =
        rememberLauncherForActivityResult(RequestPermission()) { granted ->
            if (granted) vm.fetchLocation(context)
        }

    LaunchedEffect(Unit) {
        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("산책 메이트", color = Color.White) },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Notifications, contentDescription = "알림", tint = Color.White)
                    }
                    Image(
                        painter = rememberAsyncImagePainter("https://via.placeholder.com/150"),
                        contentDescription = "프로필",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { /* TODO */ },
                        contentScale = ContentScale.Crop
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(Color(0xFF4A90E2), Color(0xFF9013FE)))
                )
            )
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@Box
            }
            state.error?.let { err ->
                Text(
                    text = err,
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Red
                )
                return@Box
            }

            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // ─ 오늘의 날씨 카드 ─
                state.weather?.let { w ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A90E2)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("오늘의 날씨", color = Color.White)
                                Text(w.city, color = Color.White)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${w.tempC}°C",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(w.description, color = Color.White)
                            }
                            Icon(
                                painter = painterResource(R.drawable.ic_sun),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ─ 내 위치 표시 ─
                state.location
                    ?.let { (lat, lon) ->
                        Text(
                            text = "내 위치: %.5f, %.5f".format(lat, lon),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    ?: Text(
                        text = "위치 정보 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                Spacer(Modifier.height(16.dp))

                // ─ 반려견 카드 ─
                state.dog?.let { d ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(d.avatarUrl),
                                contentDescription = "강아지 사진",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text("${d.breed} · ${d.age}살", color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    repeat(5) { idx ->
                                        val filled = idx < d.activityLevel
                                        Icon(
                                            painterResource(
                                                if (filled) R.drawable.ic_dot_filled else R.drawable.ic_dot_outline
                                            ),
                                            contentDescription = null,
                                            tint = if (filled) Color.Green else Color.LightGray,
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "활동량 보통",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ─ 산책 시작 버튼 ─
                Button(
                    onClick = onStartWalk,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("산책 시작하기", color = Color.White)
                }

                Spacer(Modifier.height(24.dp))

                // ─ 최근 산책 기록 ─
                Text("최근 산책 기록", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(state.recentWalks) { walk ->
                        // … 생략 …
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ─ 이번 주 통계 카드 ─
                state.weeklySummary?.let { ws ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF8E24AA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("이번 주 총 산책", color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${ws.count}회",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "총 ${"%.1f".format(ws.totalDistanceKm)}km · ${ws.totalTimeMin/60}시간 ${ws.totalTimeMin%60}분",
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
