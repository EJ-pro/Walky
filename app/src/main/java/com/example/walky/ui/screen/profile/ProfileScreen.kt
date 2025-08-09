package com.example.walky.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.walky.data.DogProfile
import com.example.walky.gamification.rankBadgeResId
import kotlin.math.floor
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    vm: ProfileViewModel = viewModel()
) {
    val state by vm.ui.collectAsState()

    // ✅ 등급 전용 VM
    val rankVm: RankViewModel = viewModel()
    val rank by rankVm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("마이페이지") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { rankVm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* 앱 설정 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { inner ->
        if (state.isLoading || rank.loading) {
            Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 프로필 카드 ---
            item {
                ProfileCard(
                    name = state.user?.displayName.orEmpty(),
                    email = state.user?.email.orEmpty(),
                    location = state.user?.locationText.orEmpty(),
                    photoUrl = state.user?.photoUrl.orEmpty(),
                    onEdit = vm::openEdit
                )
            }

            // --- ✅ 등급 / 오늘 점수 / 14일 히스토리 ---
            item {
                RankCard(
                    badgeRes = rankBadgeResId(rank.rankTier),
                    tierName = rank.rankTier.display,
                    points14d = rank.points14d,
                    nextTierName = rank.nextTierName,
                    progress = rank.fractionToNext,
                    toNext = rank.toNext,
                    streakDays = rank.streakDays
                )
            }
            item {
                TodayScoreCard(score = rank.todayScore)
            }
            item {
                History14dCard(items = rank.dailyBreakdown)
            }

            // --- 내 강아지 ---
            item {
                SectionHeader(title = "내 강아지", actionText = "+ 추가", onAction = vm::openAddDog)
            }
            items(state.dogs, key = { it.id }) { dog ->
                DogRow(
                    dog = dog,
                    onClick = { /* 상세 편집 이동 예정 */ },
                    onLongPressDelete = { vm.deleteDog(dog.id) }
                )
            }

            // --- 산책 통계 ---
            item {
                SectionHeader(title = "산책 통계", actionText = "더보기") {
                    // navController.navigate("stats")
                }
            }
            item {
                StatsRow(
                    count = state.weekly?.walkCount ?: 0,
                    totalKm = state.weekly?.totalDistanceKm ?: 0.0,
                    totalMinutes = state.weekly?.totalMinutes ?: 0
                )
            }

            // --- 설정 리스트 ---
            item { SettingsItem("알림 설정", Icons.Default.Notifications) { /* nav */ } }
            item { SettingsItem("차단 유저 관리", Icons.Default.Notifications) { /* nav */ } }
            item { SettingsItem("개인정보 보호", Icons.Default.Notifications) { /* nav */ } }
            item { SettingsItem("고객 지원", Icons.Default.Notifications) { /* nav */ } }
            item { SettingsItem("앱 정보", Icons.Default.Info) { /* nav */ } }

            // --- 로그아웃 ---
            item {
                OutlinedButton(
                    onClick = {
                        vm.signOut()
                        navController.navigate("login") {
                            popUpTo("main") { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("로그아웃")
                }
            }
        }
    }

    if (state.showEditDialog) EditProfileDialog(
        initialName = state.user?.displayName.orEmpty(),
        initialLocation = state.user?.locationText.orEmpty(),
        initialEmail = state.user?.email.orEmpty(),
        onDismiss = vm::closeEdit,
        onSave = { n, l, e -> vm.saveProfile(n, l, e, photoUrl = null) }
    )

    if (state.showAddDogDialog) AddDogDialog(
        onDismiss = vm::closeAddDog, // ← 콜론(:) 말고 이퀄(=)
        onSave = { name, breed, age, neutered -> vm.addDog(name, breed, age, neutered) }
    )
}

@Composable
private fun SettingsItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
    }
    Divider(thickness = 0.5.dp, color = Color(0x14000000))
}

@Composable
private fun ProfileCard(
    name: String,
    email: String,
    location: String,
    photoUrl: String,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = photoUrl.ifBlank { "https://via.placeholder.com/96" },
                contentDescription = "avatar",
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = .3f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name.ifBlank { "사용자" }, style = MaterialTheme.typography.titleMedium)
                Text(email, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                if (location.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF4A90E2),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(location, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            FilledTonalButton(onClick = onEdit, shape = RoundedCornerShape(10.dp)) {
                Text("편집")
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
private fun DogRow(
    dog: DogProfile,
    onClick: () -> Unit,
    onLongPressDelete: () -> Unit
) {
    val bg = try { Color(android.graphics.Color.parseColor(dog.colorHex)) }
    catch (_: Exception) { Color(0xFFFF8A65) }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bg.copy(alpha = .25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = bg)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(dog.name, style = MaterialTheme.typography.titleMedium)
                Text("${dog.breed} • ${dog.age}살 • ${if (dog.neutered) "중성" else "미중성"}",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onLongPressDelete) {
                Icon(Icons.Default.Delete, contentDescription = "삭제")
            }
        }
    }
}

@Composable
private fun StatsRow(count: Int, totalKm: Double, totalMinutes: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Icons.Default.Notifications, "이번 주", "${count}회", Color(0xFF4F83CC))
        StatCard(Icons.Default.Notifications, "총 거리", String.format("%.1fkm", totalKm), Color(0xFF2E7D32))
        val h = floor(totalMinutes / 60f).toInt()
        val m = totalMinutes % 60
        StatCard(Icons.Default.Notifications, "총 시간", "${h}시간 ${m}분", Color(0xFF8E24AA))
    }
}

@Composable
private fun StatCard(icon: ImageVector, label: String, value: String, tint: Color) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.08f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column {
                Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text(value, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------- 등급 UI 추가 컴포저블 ----------------

@Composable
private fun RankCard(
    badgeRes: Int,
    tierName: String,
    points14d: Int,
    nextTierName: String?,
    progress: Float,
    toNext: Int,
    streakDays: Int
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = badgeRes),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("펫 등급", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text(tierName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f,1f),
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    trackColor = Color(0xFFEDEDED)
                )
                Spacer(Modifier.height(6.dp))
                val nextText = nextTierName?.let { "다음 $it 까지 $toNext 점" } ?: "최고 등급!"
                Text("$nextText · 최근 14일 ${points14d}점 · 연속 ${streakDays}일",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun TodayScoreCard(score: Int) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("오늘 점수", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                Text("$score 점", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun History14dCard(items: List<Pair<java.time.LocalDate, Int>>) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("최근 14일", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            if (items.isEmpty()) {
                Text("기록이 없어요.", color = Color.Gray)
            } else {
                val max = (items.maxOf { it.second }.coerceAtLeast(1)).toFloat()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { (date, score) ->
                        val h = (70 * (score / max)).coerceAtLeast(6f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF68CA8F))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                date.format(DateTimeFormatter.ofPattern("MM/dd")),
                                style = MaterialTheme.typography.labelSmall, color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

/* -------- Dialogs -------- */

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialLocation: String,
    initialEmail: String,
    onDismiss: () -> Unit,
    onSave: (name: String, location: String, email: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var loc by remember { mutableStateOf(initialLocation) }
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), loc.trim(), email.trim()) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("프로필 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name,  onValueChange = { name = it },  label = { Text("이름") })
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("이메일") })
                OutlinedTextField(value = loc,   onValueChange = { loc = it },   label = { Text("지역") })
            }
        }
    )
}


@Composable
private fun AddDogDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, breed: String, age: Int, neutered: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("2") }
    var neutered by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val age = ageText.toIntOrNull() ?: 0
                onSave(name.trim(), breed.trim(), age, neutered)
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("강아지 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") })
                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text("견종") })
                OutlinedTextField(value = ageText, onValueChange = { ageText = it }, label = { Text("나이") })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = neutered, onCheckedChange = { neutered = it })
                    Text("중성화 여부")
                }
            }
        }
    )
}
