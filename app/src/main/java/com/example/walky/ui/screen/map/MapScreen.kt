@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.walky.ui.screen.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Toast
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

/**
 * Drawable을 Bitmap으로 변환
 */
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

@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsState()

    // 위치 가져오기 (HomeUiState에서)
    val (lat, lon) = uiState.location ?: (37.402111 to 127.108678)

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
                latitude   = lat,
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
                        mapViewModel.recordStartLocation(lat, lon)
                        mapViewModel.startStopwatch()
                    } else {
                        mapViewModel.togglePause()
                    }
                },
                onEndWalk = {
                    mapViewModel.resetStopwatch()
                    Toast.makeText(context, "산책 종료", Toast.LENGTH_SHORT).show()
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

    // 내 위치 마커 (한 개만 갱신)
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
                polyline.value = layer.addPolyline(PolylineOptions.from(mp, 5f, android.graphics.Color.RED))
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
