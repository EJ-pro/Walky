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

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
        Canvas(bmp).apply {
            drawable.setBounds(0, 0, w, h); drawable.draw(this)
        }
    }
}

/** 현재 위치 1회 취득 (권한 필요) + lastLocation 폴백 */
private fun fetchCurrentLocationOnce(
    context: Context,
    onResult: (Double, Double) -> Unit
) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    try {
        val cts = CancellationTokenSource()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) onResult(loc.latitude, loc.longitude)
                else fused.lastLocation.addOnSuccessListener { last ->
                    if (last != null) onResult(last.latitude, last.longitude)
                }
            }
    } catch (_: SecurityException) { }
}

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel() // 지금은 안 쓰지만 시그니처 유지
) {
    val context = LocalContext.current

    // 무조건 내 위치만 사용
    var currentPos by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(true) }

    // 위치 권한 런처
    val locLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            fetchCurrentLocationOnce(context) { la, lo ->
                currentPos = la to lo
                isLoadingLocation = false
            }
        } else {
            permissionDenied = true
            isLoadingLocation = false
        }
    }

    // 진입 시: 권한 요청 → 허용되면 즉시 현재 위치 취득
    LaunchedEffect(Unit) {
        isLoadingLocation = true
        locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

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
            // ----- 상단 지도 영역 -----
            when {
                isLoadingLocation -> {
                    // 로딩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                    }
                }
                permissionDenied -> {
                    // 권한 거부됨 → 재요청 버튼
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("내 위치 권한이 필요합니다.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                isLoadingLocation = true
                                locLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }) {
                                Text("권한 허용")
                            }
                        }
                    }
                }
                currentPos == null -> {
                    // 권한은 OK인데 아직 좌표가 없음 → 한번 더 시도
                    LaunchedEffect("retry-fetch") {
                        fetchCurrentLocationOnce(context) { la, lo ->
                            currentPos = la to lo
                            isLoadingLocation = false
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    // ✅ 좌표 준비 완료 → 이때만 지도 렌더링 (초기 중심 = 내 위치)
                    val (lat, lon) = currentPos!!
                    LectureKakaoMap(
                        modifier   = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                        latitude   = lat,
                        longitude  = lon,
                        path       = pathPoints,
                        startPoint = startPt
                    )
                }
            }

            // ----- 하단 컨트롤 -----
            WalkTrackingBottomBar(
                stepCount     = stepCount,
                distanceKm    = distanceKm,
                durationText  = durationText,
                calorie       = calorie,
                isPaused      = isPaused,
                isStarted     = isStarted,
                onPauseToggle = {
                    if (!isStarted) {
                        // 시작: 좌표 없으면 막기
                        val pos = currentPos
                        if (pos == null) {
                            Toast.makeText(context, "내 위치를 먼저 가져오는 중입니다…", Toast.LENGTH_SHORT).show()
                            return@WalkTrackingBottomBar
                        }
                        val (lat, lon) = pos
                        mapViewModel.recordStartLocation(lat, lon)
                        mapViewModel.startStopwatch()
                        mapViewModel.startLocationTracking(context)
                        mapViewModel.startHealthStepsPolling(context)
                    } else {
                        mapViewModel.togglePause()
                    }
                },
                onEndWalk = {
                    mapViewModel.stopLocationTracking()
                    mapViewModel.stopHealthStepsPolling()
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

    // 맵 첫 초기화: 이 컴포저블은 currentPos 준비 후에만 호출되므로
    // 초기 카메라는 항상 내 위치로 잡힘
    LaunchedEffect(Unit) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(e: Exception?) { }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(map: KakaoMap) { kakaoMap = map }
                override fun getPosition() = LatLng.from(latitude, longitude)
            }
        )
    }

    // 내 위치 마커 & 카메라 이동
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
            } else followLabel.value?.moveTo(pos)
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
fun WalkStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
