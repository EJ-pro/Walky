package com.example.walky.ui.screen.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walky.ui.home.HomeViewModel
import com.kakao.vectormap.MapView
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolylineOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context    = LocalContext.current

    // 센서 매니저 & 스텝 카운터 센서
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor    = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }

    // 센서 이벤트 리스너 등록
    DisposableEffect(sensorManager, stepSensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.getOrNull(0)?.let { viewModel.onStepChanged(it) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        stepSensor?.also { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    // 스톱워치·센서·거리·칼로리 상태
    val duration   by viewModel.durationText.collectAsState()
    val isPaused   by viewModel.isPaused.collectAsState()
    val isStarted  by viewModel.isStarted.collectAsState()
    val stepCount  by viewModel.stepCount.collectAsState()
    val distanceKm by viewModel.distanceKm.collectAsState()
    val calorie    by viewModel.calorie.collectAsState()

    // HomeViewModel 에서 가져온 초깃값(권한 동의 후 위치)
    val uiState    by homeViewModel.uiState.collectAsState()

    // 산책 경로 점 모음
    val pathPoints by viewModel.pathPoints.collectAsState()

    // 현재 획득된 위치(Flow). null 이면 uiState.location, 그것도 없으면 기본 좌표
    val currentLoc = viewModel.currentLocation.collectAsState().value
        ?: uiState.location
        ?: (37.402111 to 127.108678)
    val (lat, lon) = currentLoc

    // 맵 진입 즉시: 권한 요청 → 내 위치로 추적 시작
    LaunchedEffect(Unit) {
        homeViewModel.fetchLocation(context)           // 위치 권한 요청
        viewModel.startLocationTracking(context)      // FusedLocation 구동
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LectureKakaoMap(
                modifier  = Modifier
                    .fillMaxWidth()
                    .weight(0.6f),
                latitude  = lat,
                longitude = lon,
                path      = pathPoints
            )
            Box(Modifier.fillMaxWidth().weight(0.4f)) {
                WalkTrackingBottomBar(
                    stepCount     = stepCount,
                    distanceKm    = distanceKm,
                    durationText  = duration,
                    calorie       = calorie,
                    isPaused      = isPaused,
                    isStarted     = isStarted,
                    onPauseToggle = {
                        if (!isStarted) viewModel.startStopwatch()
                        else viewModel.togglePause()
                    },
                    onEndWalk     = {
                        viewModel.resetStopwatch()
                        Toast.makeText(context, "산책 종료", Toast.LENGTH_SHORT).show()
                    },
                    isSafeZone    = true
                )
            }
        }
    }
}

@Composable
fun LectureKakaoMap(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    path: List<Pair<Double, Double>>
) {
    val context   = LocalContext.current
    val mapView   = remember { MapView(context) }
    var kakaoMap  by remember { mutableStateOf<KakaoMap?>(null) }
    val polyline  = remember { mutableStateOf<Polyline?>(null) }

    // 1) 맵 초기화는 최초 한 번만
    LaunchedEffect(Unit) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(e: Exception?) {
                    Toast.makeText(context, "지도 오류: $e", Toast.LENGTH_LONG).show()
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) {
                    kakaoMap = map
                }
                override fun getPosition() =
                    com.kakao.vectormap.LatLng.from(latitude, longitude)
            }
        )
    }

    // 2) 위치(latitude, longitude)가 바뀔 때마다 카메라 이동
    LaunchedEffect(latitude, longitude) {
        kakaoMap?.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                com.kakao.vectormap.LatLng.from(latitude, longitude)
            )
        )
    }

    // 3) 경로가 2점 이상 모였을 때만 한 번 Polyline 생성 → 이후 changeMapPoints
    LaunchedEffect(path) {
        val map   = kakaoMap ?: return@LaunchedEffect
        if (path.size < 2) return@LaunchedEffect

        val layer = map.getShapeManager()?.getLayer() ?: return@LaunchedEffect
        val pts   = path.map { (la, lo) ->
            com.kakao.vectormap.LatLng.from(la, lo)
        }
        val mapPts = MapPoints.fromLatLng(pts)

        if (polyline.value == null) {
            polyline.value = layer.addPolyline(
                PolylineOptions.from(mapPts, 5f, AndroidColor.RED)
            )
        } else {
            polyline.value?.changeMapPoints(listOf(mapPts))
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}


@Composable
fun WalkTrackingBottomBar(
    stepCount: Int,
    distanceKm: Double,
    durationText: String,
    calorie: Int,
    isPaused: Boolean,
    isStarted: Boolean,
    onPauseToggle: () -> Unit,
    onEndWalk: () -> Unit,
    isSafeZone: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            WalkStatItem("$stepCount", "걸음")
            WalkStatItem(String.format("%.2f", distanceKm), "km")
            WalkStatItem(durationText, "시간")
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            StatCard(Icons.Default.Star, "$calorie", "칼로리", Color(0xFFFF6F00))
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onPauseToggle, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))) {
                Icon(
                    imageVector = when {
                        !isStarted -> Icons.Default.PlayArrow
                        isPaused -> Icons.Default.PlayArrow
                        else -> Icons.Default.Home
                    },
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        !isStarted -> "시작"
                        isPaused -> "재시작"
                        else -> "일시정지"
                    }
                )
            }
            Button(onClick = onEndWalk, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("종료")
            }
        }
    }
}

@Composable
fun WalkStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun StatCard(icon: ImageVector, value: String, label: String, iconColor: Color) {
    Card(Modifier.width(80.dp).height(80.dp), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium)
                Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
