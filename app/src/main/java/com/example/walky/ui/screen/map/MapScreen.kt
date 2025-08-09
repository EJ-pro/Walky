@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.walky.ui.screen.map

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.walky.R
import com.example.walky.ui.home.HomeViewModel
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.shape.Polyline
import com.kakao.vectormap.shape.PolylineOptions
import com.kakao.vectormap.shape.MapPoints
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Drawable → Bitmap 유틸 */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
        Canvas(bmp).apply {
            drawable.setBounds(0, 0, width, height)
            drawable.draw(this)
        }
    }
}

/** 현재 위치 1회 취득 (권한 가정) + lastLocation 폴백 */
private fun fetchCurrentLocationOnce(
    context: Context,
    onResult: (Double, Double) -> Unit
) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    try {
        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    onResult(loc.latitude, loc.longitude)
                } else {
                    fused.lastLocation.addOnSuccessListener { last ->
                        if (last != null) onResult(last.latitude, last.longitude)
                    }
                }
            }
    } catch (_: SecurityException) {
        // 권한 없는 경우 조용히 무시
    }
}

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsState()
    val actRecLauncher = rememberLauncherForActivityResult(RequestPermission()) { /* result ignored */ }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            actRecLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }
    // 홈에서 받은 좌표(있으면 사용)
    val homePos: Pair<Double, Double>? = uiState.location

    // 현재 좌표 상태 (맵 진입 시 갱신)
    var currentPos by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    // 위치 권한 요청 런처 (단일 인스턴스)
    val locLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            fetchCurrentLocationOnce(context) { la, lo -> currentPos = la to lo }
        } else {
            // 거부 시엔 homePos 또는 기본값 사용
            currentPos = null
        }
    }

    // 진입 시 현재 위치 먼저 시도 → 실패하면 권한 요청
    LaunchedEffect(Unit) {
        fetchCurrentLocationOnce(context) { la, lo -> currentPos = la to lo }
        if (currentPos == null) {
            locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // 최종 사용 좌표: 현재위치 > 홈좌표 > 기본값
    val (lat, lon) = currentPos ?: homePos ?: (37.402111 to 127.108678)

    // 워크 트래킹 상태
    val isStarted    by mapViewModel.isStarted.collectAsState()
    val isPaused     by mapViewModel.isPaused.collectAsState()
    val stepCount    by mapViewModel.stepCount.collectAsState()
    val distanceKm   by mapViewModel.distanceKm.collectAsState()
    val durationText by mapViewModel.durationText.collectAsState()
    val calorie      by mapViewModel.calorie.collectAsState()
    val pathPoints   by mapViewModel.pathPoints.collectAsState()
    val startPt      by mapViewModel.startPoint.collectAsState()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LectureKakaoMap(
                modifier   = Modifier
                    .fillMaxWidth()
                    .weight(0.6f),
                latitude   = lat,   // ✅ 현재/홈/기본 순서로 결정된 좌표 사용
                longitude  = lon,
                path       = pathPoints,
                startPoint = startPt
            )
            WalkTrackingBottomBar(
                stepCount     = stepCount,
                distanceKm    = distanceKm,
                durationText  = durationText,
                calorie       = calorie,
                isPaused      = isPaused,
                isStarted     = isStarted,
                onPauseToggle = {
                    if (!isStarted) {
                        mapViewModel.recordStartLocation(lat, lon) // ✅ 시작 지점 기록
                        mapViewModel.startStopwatch()
                        mapViewModel.startLocationTracking(context) // ✅ 위치 추적 시작
                    } else {
                        mapViewModel.togglePause()
                    }
                },
                onEndWalk = {
                    // ✅ 순서 중요: 위치 중지 → 저장 → 성공 시 리셋/클리어
                    mapViewModel.stopLocationTracking()
                    mapViewModel.endWalkAndSave(context, mode = "MY") { ok ->
                        Toast.makeText(context, if (ok) "산책 저장 완료" else "저장 실패", Toast.LENGTH_SHORT).show()
                        if (ok) {
                            mapViewModel.resetStopwatch()
                            mapViewModel.clearWalk()
                        }
                    }
                },
                isSafeZone = true
            )
        }
    }
}

@Composable
fun LectureKakaoMap(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    path: List<Pair<Double, Double>>,
    startPoint: Pair<Double, Double>?
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    // 맵 초기화 (한 번)
    LaunchedEffect(Unit) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(e: Exception?) {
                    Toast.makeText(context, "지도 오류: $e", Toast.LENGTH_LONG).show()
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) { kakaoMap = map }
                override fun getPosition() = LatLng.from(latitude, longitude)
            }
        )
    }

    // 내 위치 마커 (1개만 유지하며 위치/카메라 갱신)
    val followLabel = remember { mutableStateOf<Label?>(null) }
    val followStyles = remember {
        val drw = AppCompatResources.getDrawable(context, R.drawable.ic_my_location_marker)!!
        LabelStyles.from(LabelStyle.from(drawableToBitmap(drw)))
    }
    LaunchedEffect(latitude, longitude) {
        kakaoMap?.let { map ->
            val layer = map.labelManager?.layer ?: return@let
            val pos = LatLng.from(latitude, longitude)
            if (followLabel.value == null) {
                val styles = map.labelManager?.addLabelStyles(followStyles)!!
                followLabel.value = layer.addLabel(LabelOptions.from(pos).setStyles(styles))
            } else {
                followLabel.value?.moveTo(pos)
            }
            map.moveCamera(CameraUpdateFactory.newCenterPosition(pos))
        }
    }

    // 경로 폴리라인
    val polyline = remember { mutableStateOf<Polyline?>(null) }
    LaunchedEffect(path) {
        kakaoMap?.let { map ->
            if (path.size < 2) return@let
            val layer = map.getShapeManager()?.getLayer() ?: return@let
            val pts = path.map { LatLng.from(it.first, it.second) }
            val mp  = MapPoints.fromLatLng(pts)
            if (polyline.value == null) {
                polyline.value = layer.addPolyline(
                    PolylineOptions.from(mp, 5f, android.graphics.Color.RED)
                )
            } else {
                polyline.value?.changeMapPoints(listOf(mp))
            }
        }
    }

    // 시작 지점 마커
    val startLabel = remember { mutableStateOf<Label?>(null) }
    val startStyles = remember {
        val drw = AppCompatResources.getDrawable(context, R.drawable.ic_my_location_marker)!!
        LabelStyles.from(LabelStyle.from(drawableToBitmap(drw)))
    }
    LaunchedEffect(startPoint) {
        startPoint?.let { sp ->
            kakaoMap?.let { map ->
                val layer = map.labelManager?.layer ?: return@let
                startLabel.value?.hide()
                val styles = map.labelManager?.addLabelStyles(startStyles)!!
                startLabel.value = layer.addLabel(
                    LabelOptions.from(LatLng.from(sp.first, sp.second)).setStyles(styles)
                )
            }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = onPauseToggle,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
            ) {
                Icon(
                    imageVector = if (!isStarted || isPaused) Icons.Default.PlayArrow else Icons.Default.Home,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !isStarted -> "시작"
                        isPaused   -> "재시작"
                        else       -> "일시정지"
                    }
                )
            }
            Button(
                onClick = onEndWalk,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("종료")
            }
        }
    }
}

@Composable
fun WalkStatItem(
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
