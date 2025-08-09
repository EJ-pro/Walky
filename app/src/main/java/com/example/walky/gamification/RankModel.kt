package com.example.walky.gamification

import kotlin.math.floor

// 14일 합계 기준 시즌 랭크
enum class RankTier(val display: String, val minPoints14d: Int) {
    BRONZE("브론즈", 0),
    SILVER("실버", 700),
    GOLD("골드", 1400),
    PLATINUM("플래티넘", 2100),
    DIAMOND("다이아", 3200),
    MASTER("마스터", 4700);

    companion object {
        fun from(points14d: Int): RankTier =
            values().sortedBy { it.minPoints14d }.last { points14d >= it.minPoints14d }
    }
}

data class DailyInputs(
    val totalSteps: Int,
    val totalMinutes: Int,
    val sessionCount20min: Int // 20분 이상 세션 수
)

/** 오늘 점수 계산 규칙 */
fun calcDailyScore(input: DailyInputs): Int {
    val stepsBase  = floor(input.totalSteps / 1000.0).toInt() * 10         // 1,000보당 10점
    val timeBase   = (input.totalMinutes * 0.5).toInt()                    // 분당 0.5점
    val sessionBase= input.sessionCount20min * 15                          // 세션당 15점
    val multiSess  = if (input.sessionCount20min > 1) (input.sessionCount20min - 1) * 5 else 0
    val bonus10k   = if (input.totalSteps >= 10_000) 50 else 0
    val bonusGoal  = if (input.totalSteps >= 8_000 && input.totalMinutes >= 40) 30 else 0
    val raw = stepsBase + timeBase + sessionBase + multiSess + bonus10k + bonusGoal
    return raw.coerceAtMost(2000) // 일일 상한
}

/** 스트릭 승수 (7/14/30일) */
fun streakMultiplier(streakDays: Int): Double = when {
    streakDays >= 30 -> 1.20
    streakDays >= 14 -> 1.10
    streakDays >= 7  -> 1.05
    else -> 1.0
}

/** 펫 모드 세션이 하루에 1회라도 있으면 소폭 승수 */
fun petMultiplier(hasPetSession: Boolean): Double = if (hasPetSession) 1.05 else 1.0

/** 시즌 랭크 프로그레스 계산 */
data class RankProgress(
    val tier: RankTier,
    val points14d: Int,
    val nextTier: RankTier?,
    val toNext: Int,                // 다음 티어까지 남은 점수
    val fractionToNext: Float       // 0f~1f
)

fun calcRankProgress(points14d: Int): RankProgress {
    val tier = RankTier.from(points14d)
    val tiers = RankTier.values().sortedBy { it.minPoints14d }
    val idx = tiers.indexOf(tier)
    val next = tiers.getOrNull(idx + 1)
    return if (next == null) {
        RankProgress(tier, points14d, null, 0, 1f)
    } else {
        val span = (next.minPoints14d - tier.minPoints14d).coerceAtLeast(1)
        val curInSpan = (points14d - tier.minPoints14d).coerceAtLeast(0)
        RankProgress(
            tier = tier,
            points14d = points14d,
            nextTier = next,
            toNext = (next.minPoints14d - points14d).coerceAtLeast(0),
            fractionToNext = (curInSpan.toFloat() / span.toFloat()).coerceIn(0f, 1f)
        )
    }
}

/** 티어별 펫 배지 리소스 */
fun rankBadgeResId(tier: RankTier): Int = when (tier) {
    RankTier.BRONZE   -> com.example.walky.R.drawable.ic_sun
    RankTier.SILVER   -> com.example.walky.R.drawable.ic_sun
    RankTier.GOLD     -> com.example.walky.R.drawable.ic_sun
    RankTier.PLATINUM -> com.example.walky.R.drawable.ic_sun
    RankTier.DIAMOND  -> com.example.walky.R.drawable.ic_sun
    RankTier.MASTER   -> com.example.walky.R.drawable.ic_sun
}
