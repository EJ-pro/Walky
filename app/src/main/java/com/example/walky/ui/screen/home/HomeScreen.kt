@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.walky.ui.screen.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.walky.R
import com.example.walky.ui.home.HomeViewModel
import java.time.format.DateTimeFormatter
// 🔹 Health Connect imports
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord

@Composable
fun HomeScreen(
    onStartWalk: () -> Unit = {}
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    val hcPermissions = remember {
        setOf(
            HealthPermission.getReadPermission(StepsRecord::class)
        )
    }
    val requestHcPermissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (granted.containsAll(hcPermissions)) {
            vm.forceRefreshStepsFromHealthConnect(context)
        }
    }

    LaunchedEffect(Unit) {
        when (HealthConnectClient.getSdkStatus(context, "com.google.android.apps.healthdata")) {
            HealthConnectClient.SDK_AVAILABLE -> {
                val client = HealthConnectClient.getOrCreate(context)
                val granted: Set<String> = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(hcPermissions)) {
                    requestHcPermissions.launch(hcPermissions)
                } else {
                    vm.forceRefreshStepsFromHealthConnect(context)
                }
            }
            else -> { /* 설치/업데이트 유도 or 미지원 처리 */ }
        }
    }

    // 초기 로드
    LaunchedEffect(Unit) {
        vm.loadProfileImage(context)
        vm.startTodayStatsListener() // 거리/시간 합산 리스너
    }

    // 화면 복귀 시 새로고침 (Health Connect도 함께 시도)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refreshAll(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stopTodayStatsListener()
        }
    }

    // Pretendard
    val pretendard = FontFamily(
        Font(R.font.pretendard_light, FontWeight.Light),
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
        Font(R.font.pretendard_bold, FontWeight.Bold)
    )

    // 위치 권한
    val locLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) vm.fetchLocationAndWeather(context)
    }
    LaunchedEffect(Unit) {
        locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun WeatherIcon(descriptionSimplified: String, sizeDp: Int = 40) {
        val iconRes = when (descriptionSimplified) {
            "맑음" -> R.drawable.ic_sun
            "흐림" -> R.drawable.ic_sun
            "비"   -> R.drawable.ic_sun
            "눈"   -> R.drawable.ic_sun
            "폭풍" -> R.drawable.ic_sun
            "안개" -> R.drawable.ic_sun
            else   -> R.drawable.ic_sun
        }
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = descriptionSimplified,
            modifier = Modifier.size(sizeDp.dp),
            tint = Color.Unspecified
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .verticalScroll(rememberScrollState())
    ) {
        // 상단 카드/Today 섹션 (너가 쓰던 UI 유지)
        Card(
            Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Spacer(Modifier.height(32.dp))
                        Text("Hello, ${state.userName}", fontFamily = pretendard, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("${state.userName}님, 오늘도 걸어볼까요?", fontFamily = pretendard, fontSize = 14.sp, color = Color(0xFFBEE7A5))
                    }
                    IconButton(onClick = { /* 알림 */ }) {
                        Icon(painter = painterResource(R.drawable.ic_alarm), contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.profileImageUrl != null) {
                        Image(
                            painter = rememberAsyncImagePainter(state.profileImageUrl),
                            contentDescription = "프로필",
                            modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(painter = painterResource(R.drawable.ic_default_avatar), contentDescription = null, tint = Color.White,
                            modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, Color.White, CircleShape))
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (expanded) {
                    Spacer(Modifier.height(16.dp))
                    Text("Today", fontFamily = pretendard, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(12.dp))

                    Card(
                        Modifier.fillMaxWidth().animateContentSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                WeatherIcon(descriptionSimplified = state.description, sizeDp = 40)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(state.weatherCity, fontFamily = pretendard, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF68CA8F))
                                    Text("${state.tempC}°C", fontFamily = pretendard, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                                }
                                Spacer(Modifier.weight(1f))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(state.description, fontFamily = pretendard, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111111))
                                    Text("습도 ${state.humidity}%", fontFamily = pretendard, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF767676))
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                            Divider(color = Color(0xFFEDEDED), thickness = 1.dp)
                            Spacer(Modifier.height(18.dp))

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("오늘의 걸음 통계", fontFamily = pretendard, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111111))
                                Spacer(Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("${state.todaySteps}걸음", fontFamily = pretendard, fontSize = 14.sp, color = Color(0xFF767676))
                                    Text("${"%.1f".format(state.todayDistanceKm)}km", fontFamily = pretendard, fontSize = 14.sp, color = Color.DarkGray)
                                    Text("${state.todayDurationMin}분", fontFamily = pretendard, fontSize = 14.sp, color = Color.DarkGray)
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = (state.todaySteps / state.stepGoal.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth().height(15.dp),
                                color = Color(0xFF68CA8F),
                                trackColor = Color(0xFFE0E0E0)
                            )
                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = onStartWalk,
                                Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                            ) {
                                Text("Start", fontFamily = pretendard, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
            Icon(
                painter = painterResource(id = if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down),
                contentDescription = null, tint = Color.White,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).clickable { expanded = !expanded }.size(30.dp)
            )
            Spacer(Modifier.height(16.dp))
        }


        // 최근 산책 기록
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                "최근 산책 기록",
                fontFamily = pretendard,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        state.recentWalks.forEach { walk ->
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
                    Text(
                        walk.title,
                        fontFamily = pretendard,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                    Text(
                        walk.date.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                        fontFamily = pretendard,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(Color.White)
        )
    }
}
