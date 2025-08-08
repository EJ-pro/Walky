package com.example.walky.ui.screen.map

import com.example.walky.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
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
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolylineOptions

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width  = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context    = LocalContext.current

    // 1) 센서 세팅
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val stepSensor    = remember { sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    DisposableEffect(sensorManager, stepSensor) {
        val listener = object: SensorEventListener {
            override fun onSensorChanged(e: SensorEvent?) {
                e?.values?.getOrNull(0)?.let { viewModel.onStepChanged(it) }
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {}
        }
        stepSensor?.also { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // 2) StateFlows
    val duration   by viewModel.durationText.collectAsState()
    val isPaused   by viewModel.isPaused.collectAsState()
    val isStarted  by viewModel.isStarted.collectAsState()
    val stepCount  by viewModel.stepCount.collectAsState()
    val distanceKm by viewModel.distanceKm.collectAsState()
    val calorie    by viewModel.calorie.collectAsState()
    val uiState    by homeViewModel.uiState.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()
    val startPt    by viewModel.startPoint.collectAsState()

    // 3) 현재 위치 결정 (Flow → 권한 이후 실제 위치 → 기본)
    val curLoc = viewModel.currentLocation.collectAsState().value
        ?: uiState.location
        ?: (37.402111 to 127.108678)
    val (lat, lon) = curLoc

    // 4) 권한 요청 & FusedLocation 시작 (한 번만)
    LaunchedEffect(Unit) {
        homeViewModel.fetchLocation(context)
        viewModel.startLocationTracking(context)
    }

    Scaffold{ padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
        ) {
            LectureKakaoMap(
                modifier  = Modifier.fillMaxWidth().weight(0.6f),
                latitude  = lat,
                longitude = lon,
                path      = pathPoints,
                startPoint= startPt
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
                        if (!isStarted) {
                            // “시작” 버튼을 누르는 순간 위치 기록
                            viewModel.recordStartLocation(lat, lon)
                            viewModel.startStopwatch()
                        } else {
                            viewModel.togglePause()
                        }
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
    path: List<Pair<Double, Double>>,
    startPoint: Pair<Double, Double>?    // 새로 추가
) {
    val context  = LocalContext.current
    val mapView  = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    // ─ 실시간 경로 폴리라인 (변경 없슴) ─
    val polyline = remember { mutableStateOf<Polyline?>(null) }
    LaunchedEffect(path) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (path.size < 2) return@LaunchedEffect
        val layer = map.getShapeManager()?.getLayer() ?: return@LaunchedEffect
        val pts = path.map { LatLng.from(it.first, it.second) }
        val mp  = MapPoints.fromLatLng(pts)
        if (polyline.value == null) {
            polyline.value = layer.addPolyline(
                PolylineOptions.from(mp, 5f, AndroidColor.RED)
            )
        } else {
            polyline.value?.changeMapPoints(listOf(mp))
        }
    }

    // ─ 맵 초기화는 최초 1회만 ─
    LaunchedEffect(Unit) {
        mapView.start(
            object: MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(e: Exception?) {
                    Toast.makeText(context, "지도 오류: $e", Toast.LENGTH_LONG).show()
                }
            },
            object: KakaoMapReadyCallback() {
                override fun onMapReady(m: KakaoMap) {
                    kakaoMap = m
                }
                override fun getPosition() = LatLng.from(latitude, longitude)
            }
        )
    }

    // ─ 내 위치 따라다니는 마커 ─
    val followLabel = remember { mutableStateOf<Label?>(null) }
    // 스타일 한 번만 생성
    val followStyles = remember {
        val drw = AppCompatResources.getDrawable(context, R.drawable.ic_my_location_marker)!!
        val bmp = drawableToBitmap(drw)
        LabelStyles.from(LabelStyle.from(bmp))
    }
    LaunchedEffect(latitude, longitude) {
        val map = kakaoMap ?: return@LaunchedEffect
        val layer = map.labelManager?.layer ?: return@LaunchedEffect

        val pos = LatLng.from(latitude, longitude)
        if (followLabel.value == null) {
            // 최초에만 라벨 추가
            val styleSet = map.labelManager?.addLabelStyles(followStyles)!!
            val opts = LabelOptions.from(pos).setStyles(styleSet)
            followLabel.value = layer.addLabel(opts)
        } else {
            // Label.moveTo 로 부드럽게 이동 :contentReference[oaicite:0]{index=0}
            followLabel.value?.moveTo(pos)
        }
    }

    // ─ 시작 지점에 찍히는 마커 ─
    val startLabel = remember { mutableStateOf<Label?>(null) }
    // 시작용 아이콘 스타일
    val startStyles = remember {
        val drw = AppCompatResources.getDrawable(context, R.drawable.ic_my_location_marker)!!
        val bmp = drawableToBitmap(drw)
        LabelStyles.from(LabelStyle.from(bmp))
    }
    LaunchedEffect(startPoint) {
        val sp = startPoint ?: return@LaunchedEffect
        val map = kakaoMap ?: return@LaunchedEffect
        val layer = map.labelManager?.layer ?: return@LaunchedEffect
        // 기존 시작 마커는 숨기기
        startLabel.value?.hide()
        // 새 라벨 생성
        val styleSet = map.labelManager?.addLabelStyles(startStyles)!!
        val opts = LabelOptions.from(LatLng.from(sp.first, sp.second))
            .setStyles(styleSet)
        startLabel.value = layer.addLabel(opts)
    }

    AndroidView<MapView>(
        factory = fun(ctx: Context): MapView {
            return mapView
        },
        modifier = modifier
    )
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
