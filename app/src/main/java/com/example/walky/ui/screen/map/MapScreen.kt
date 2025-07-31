package com.example.walky.ui.screen.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.walky.R
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    locationX: Double = 127.108678,
    locationY: Double = 37.402111
) {
    val context = LocalContext.current
    var isPaused by remember { mutableStateOf(false) }

    Scaffold{ padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 🗺️ 지도 (상단)
            LectureKakaoMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f),
                locationX = locationX,
                locationY = locationY
            )

            // 🏃 하단: 걷기 트래킹 UI (30%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.3f)
            ) {
                WalkTrackingBottomBar(
                    stepCount = 2847,
                    distanceKm = 1.8,
                    durationText = "23:45",
                    calorie = 142,
                    isPaused = isPaused,
                    onPauseToggle = { isPaused = !isPaused },
                    onEndWalk = {
                        Toast.makeText(context, "산책 종료", Toast.LENGTH_SHORT).show()
                    },
                    isSafeZone = true
                )
            }
        }
    }
}

@Composable
fun LectureKakaoMap(
    modifier: Modifier = Modifier,
    locationX: Double,
    locationY: Double,
) {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(mapView) {
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() {}
                override fun onMapError(exception: Exception?) {
                    Toast.makeText(context, "지도 오류: $exception", Toast.LENGTH_LONG).show()
                }
            },
            object : KakaoMapReadyCallback() {
                override fun onMapReady(kakaoMap: KakaoMap) {
                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(
                        LatLng.from(locationY, locationX)
                    )
                    kakaoMap.moveCamera(cameraUpdate)

                    val drawable = AppCompatResources.getDrawable(context, R.drawable.ic_kakao)
                    if (drawable != null) {
                        val bitmap = drawableToBitmap(drawable)
                        val labelStyle = LabelStyle.from(bitmap)
                        val styles = kakaoMap.labelManager?.addLabelStyles(LabelStyles.from(labelStyle))
                        val options = LabelOptions.from(LatLng.from(locationY, locationX)).setStyles(styles)
                        kakaoMap.labelManager?.layer?.addLabel(options)
                    }
                }

                override fun getPosition(): LatLng {
                    return LatLng.from(locationY, locationX)
                }
            }
        )
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 64
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 64
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// ✅ 걷기 트래킹 UI (하단)
@Composable
fun WalkTrackingBottomBar(
    stepCount: Int,
    distanceKm: Double,
    durationText: String,
    calorie: Int,
    isPaused: Boolean,
    onPauseToggle: () -> Unit,
    onEndWalk: () -> Unit,
    isSafeZone: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            WalkStatItem(value = "$stepCount", label = "걸음")
            WalkStatItem(value = String.format("%.1f", distanceKm), label = "km")
            WalkStatItem(value = durationText, label = "시간")
        }

        Spacer(modifier = Modifier.height(11.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            StatCard(
                icon = Icons.Default.Home,
                value = "$calorie",
                label = "칼로리",
                iconColor = Color(0xFFFF6F00)
            )
        }

        Spacer(modifier = Modifier.height(1.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onPauseToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFC107),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else    Icons.Default.Home,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isPaused) "재시작" else "일시정지")
            }

            Button(
                onClick = onEndWalk,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector =
                    Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("종료")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = if (isSafeZone) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        )
    }
}

@Composable
fun WalkStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    iconColor: Color
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .height(80.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = value, style = MaterialTheme.typography.titleLarge)
                Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
