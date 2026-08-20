package com.taper.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.floorMinutes
import com.taper.app.Prefs.hardBlock
import com.taper.app.Prefs.onboarded
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.remindersOn
import com.taper.app.Prefs.tracked
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        Reminders.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11
            )
        }
        setContent { TaperTheme { AppRoot(this, refresh) } }
    }

    override fun onResume() {
        super.onResume()
        // Keep the local record current, and close out a finished measurement.
        if (UsageRepo.hasPermission(this)) {
            History.record(this, LocalDate.now().toEpochDay(), UsageRepo.totalMinutes(this))
            History.backfill(this)
        }
        TaperPlan.finishMeasurementIfDue(this)
        // Warm one ad while the app is open, so the pause screen has one ready when it
        // fires rather than starting a load at the moment it needs to show something.
        Ads.preload(this)
        refresh++
    }
}

private val tabs = listOf("Today", "Insights", "Ladder", "Settings")

@Composable
private fun AppRoot(activity: MainActivity, refreshKey: Int) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Cream)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp, 28.dp, 20.dp, 12.dp)
            ) {
                when (tab) {
                    0 -> TodayTab(activity, refreshKey)
                    1 -> InsightsTab(activity, refreshKey)
                    2 -> LadderTab(activity, refreshKey)
                    else -> SettingsTab(activity)
                }
            }
        }
        BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Sand).padding(6.dp, 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { i, label ->
            val active = i == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(999.dp)).clickable { onSelect(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.width(44.dp).height(6.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (active) Terracotta else Color.Transparent)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    color = if (active) TerracottaDeep else Muted,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun Card(bg: Color = Sand, content: @Composable () -> Unit) {
    Surface(color = bg, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) { content() }
    }
}

@Composable
private fun Kicker(text: String, color: Color = Terracotta) =
    Text(text.uppercase(), color = color, style = MaterialTheme.typography.labelSmall)

@Composable
private fun TodayTab(activity: MainActivity, refreshKey: Int) {
    val ctx = activity
    val hasUsage = remember(refreshKey) { UsageRepo.hasPermission(ctx) }
    val blockerOn = remember(refreshKey) { BlockerService.isEnabled(ctx) }
    val byApp = remember(refreshKey) { UsageRepo.minutesByApp(ctx) }
    val used = byApp.values.sum()
    val measuring = TaperPlan.measuring(ctx)
    val week = TaperPlan.weekNumber(ctx)
    val target = TaperPlan.targetMinutes(ctx)
    val stage = TaperPlan.stage(week)
    val hasTarget = TaperPlan.hasTarget(ctx)
    val streak = remember(refreshKey) { History.streak(ctx) }

    Text(
        if (measuring) "Measuring your baseline" else "Week $week of your taper",
        style = MaterialTheme.typography.headlineMedium
    )
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            measuring -> "${TaperPlan.measureDaysLeft(ctx)} days to go · nothing is blocked yet"
            hasTarget -> TaperPlan.stageLabel(stage) + " · target $target min"
            else -> "No baseline yet"
        },
        color = Muted, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(18.dp))

    if (!hasUsage || !blockerOn) {
        Card(TerracottaTint) {
            Kicker("Not fully on", TerracottaDeep)
            Spacer(Modifier.height(6.dp))
            if (!hasUsage) {
                SetupRow("Screen time access is off", "Taper can't count minutes without it.") {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            if (!blockerOn) {
                SetupRow("The pause screen is off", "You'll get reminders only.") {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    Card {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                val frac = when {
                    !hasTarget -> 0f
                    target <= 0 -> if (used > 0) 1f else 0f
                    else -> (used.toFloat() / target).coerceIn(0f, 1f)
                }
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 18.dp.toPx()
                    val inset = stroke / 2
                    val s = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        color = Rail, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                        topLeft = Offset(inset, inset), size = s,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (frac > 0f) {
                        drawArc(
                            color = if (frac >= 1f) TerracottaDeep else Terracotta,
                            startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                            topLeft = Offset(inset, inset), size = s,
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hasTarget) max(0, target - used).toString() else used.toString(),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        if (hasTarget) "minutes left today" else "minutes today",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                measuring -> "Just watching. Your target starts after the measurement."
                hasTarget -> "Used $used of $target · baseline ${ctx.baselineMinutes}"
                else -> "Set a baseline in Settings to get a target"
            },
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        if (hasTarget) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Next week: ${TaperPlan.targetMinutes(ctx, week + 1)} minutes",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    if (streak > 0) {
        Spacer(Modifier.height(14.dp))
        Card(SageTint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("Streak", SageDeep)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$streak day${if (streak == 1) "" else "s"} under target",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "${History.minutesSaved(ctx) / 60}h saved",
                    color = SageDeep, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Kicker("Today's apps")
    Spacer(Modifier.height(10.dp))
    // Bars are scaled against the biggest app of the day, not the target — otherwise
    // everything past target pins at full width and the comparison between apps,
    // which is the actual point of this list, disappears.
    val topApp = max(1, byApp.values.maxOrNull() ?: 1)
    ctx.tracked.sortedByDescending { byApp[it] ?: 0 }.forEach { pkg ->
        val mins = byApp[pkg] ?: 0
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Catalog.label(ctx, pkg), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(fmtGap(mins), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            val frac = (mins.toFloat() / topApp).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Rail)) {
                if (frac > 0f) {
                    Box(
                        Modifier.fillMaxWidth(frac.coerceAtLeast(0.02f)).fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (hasTarget && mins > target) TerracottaDeep else Terracotta)
                    )
                }
            }
            if (hasTarget && mins > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${(mins * 100 / max(1, used))}% of today" +
                        if (mins > target) " · past the whole day's target on its own" else "",
                    color = Muted, style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun fmtGap(mins: Int): String =
    if (mins < 60) "${mins}m" else "${mins / 60}h ${mins % 60}m"

@Composable
private fun SetupRow(title: String, body: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Cream)
        ) { Text("Open") }
    }
}

@Composable
private fun InsightsTab(activity: MainActivity, refreshKey: Int) {
    val ctx = activity
    var range by remember { mutableIntStateOf(7) }
    var mode by remember { mutableIntStateOf(0) } // 0 = by day, 1 = by hour, 2 = by app
    val target = TaperPlan.targetMinutes(ctx)
    val hasTarget = TaperPlan.hasTarget(ctx)
    val days = remember(refreshKey, range) { UsageRepo.lastDays(ctx, range) }
    val recorded = remember(refreshKey) { History.all(ctx) }

    Text("Insights", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(7 to "7 days", 14 to "14 days", 28 to "28 days").forEach { (n, label) ->
            val on = range == n
            Button(
                onClick = { range = n },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) Terracotta else Sand,
                    contentColor = if (on) Cream else Ink
                ),
                modifier = Modifier.weight(1f)
            ) { Text(label, style = MaterialTheme.typography.bodySmall) }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(0 to "By day", 1 to "By hour", 2 to "By app").forEach { (m, label) ->
            val on = mode == m
            Button(
                onClick = { mode = m },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) Sage else Sand,
                    contentColor = if (on) Cream else Ink
                ),
                modifier = Modifier.weight(1f)
            ) { Text(label, style = MaterialTheme.typography.bodySmall) }
        }
    }
    Spacer(Modifier.height(16.dp))

    when (mode) {
        0 -> DayChart(ctx, days, range, target)
        1 -> HourChart(ctx, refreshKey, range)
        else -> AppBreakdown(ctx, refreshKey, range)
    }

    Spacer(Modifier.height(16.dp))
    // Averages come from complete days only. Today is still running, so including it
    // would read low all morning and high all night.
    val complete = remember(refreshKey, range) { History.completeDays(ctx, range) }
    val avg = if (complete.isEmpty()) 0 else complete.average().roundToInt()
    val best = complete.minOrNull() ?: 0
    val worst = complete.maxOrNull() ?: 0
    val underTarget = if (hasTarget) complete.count { it <= target } else 0

    Card(SageTint) {
        Kicker("Complete days in the last $range", SageDeep)
        Spacer(Modifier.height(8.dp))
        Text("$avg minutes a day on average", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Across ${complete.size} finished day${if (complete.size == 1) "" else "s"}. " +
                "Today is counted separately — it isn't over yet.",
            color = SageDeep, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        StatRow("Best day", fmtGap(best))
        StatRow("Worst day", fmtGap(worst))
        if (hasTarget) StatRow("Days under target", "$underTarget of ${complete.size}")
        if (ctx.baselineMinutes > 0) {
            StatRow("Against baseline", "${pctDown(ctx.baselineMinutes, avg)}% down")
        }
        StatRow("Today so far", fmtGap(days.firstOrNull() ?: 0))
    }

    if (avg > 0) {
        Spacer(Modifier.height(16.dp))
        ProjectionCard(avg, ctx.baselineMinutes)
    }

    if (recorded.size > 7) {
        Spacer(Modifier.height(16.dp))
        Card {
            Kicker("Every day Taper has recorded")
            Spacer(Modifier.height(12.dp))
            // One bead per day, oldest first, sized by minutes.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                recorded.take(28).reversed().forEach { (day, mins) ->
                    val t = TaperPlan.targetForDay(ctx, day)
                    val over = t != TaperPlan.NO_TARGET && mins > t
                    Box(
                        Modifier.weight(1f).height(if (mins > 0) (10 + (mins / 12).coerceAtMost(20)).dp else 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (over) Terracotta else Sage)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${recorded.size} days recorded. Sage means under that day's target.",
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun pctDown(from: Int, to: Int): Int =
    if (from <= 0) 0 else (((from - to).toFloat() / from) * 100).roundToInt()

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = SageDeep, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = SageDeep, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProjectionCard(avg: Int, baseline: Int) {
    val p = Facts.project(avg)
    val hoursYear = avg * 365 / 60
    val daysDecade = p.daysPerYear * 10
    Card(TerracottaTint) {
        Kicker("If you keep this up", TerracottaDeep)
        Spacer(Modifier.height(10.dp))
        Text("$hoursYear hours a year", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(2.dp))
        Text("$daysDecade days a decade", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "At $avg minutes a day, that's ${p.hoursPerWeek} hours a week and " +
                "${p.daysPerYear} round-the-clock days a year. Counting only the hours " +
                "you're awake, ${Facts.wakingDaysPerYear(avg)} waking days a year — about " +
                "${Facts.wakingMonthsPerDecade(avg)} months of waking life every decade, spent scrolling.",
            color = TerracottaDeep, style = MaterialTheme.typography.bodySmall
        )
        if (baseline > 0 && baseline > avg) {
            val savedYear = (baseline - avg) * 365 / 60
            Spacer(Modifier.height(12.dp))
            Text(
                "You started at $baseline minutes a day, which was ${baseline * 365 / 60} " +
                    "hours a year. At today's rate you've bought back about $savedYear " +
                    "hours a year.",
                color = TerracottaDeep, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DayChart(ctx: MainActivity, days: List<Int>, range: Int, target: Int) {
    val peak = max(1, max(days.maxOrNull() ?: 1, target))
    Card {
        Kicker("Minutes a day")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (range > 14) 2.dp else 6.dp)
        ) {
            days.reversed().forEachIndexed { i, m ->
                val h = (m.toFloat() / peak * 128f).coerceAtLeast(3f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (range <= 7) {
                        Text("$m", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                    }
                    Box(
                        Modifier.fillMaxWidth().height(h.dp)
                            .clip(RoundedCornerShape(if (range > 14) 3.dp else 8.dp))
                            .background(if (target != TaperPlan.NO_TARGET && m > target) TerracottaDeep else Terracotta)
                    )
                    if (range <= 14) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            LocalDate.now().minusDays((days.size - 1 - i).toLong())
                                .dayOfWeek.name.take(1),
                            color = Muted, style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (target != TaperPlan.NO_TARGET) "Darker bars went past your $target-minute target."
            else "Set a baseline to see your target here.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HourChart(ctx: MainActivity, refreshKey: Int, range: Int) {
    val profile = remember(refreshKey, range) { UsageRepo.hourlyProfile(ctx, range.toLong()) }
    val peak = max(0.1f, profile.maxOrNull() ?: 0.1f)
    val worstHour = profile.indices.maxByOrNull { profile[it] } ?: 0
    Card {
        Kicker("When you scroll")
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(130.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            profile.forEachIndexed { h, mins ->
                val bh = (mins / peak * 120f).coerceAtLeast(2f)
                Box(
                    Modifier.weight(1f).height(bh.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (h == worstHour) TerracottaDeep else Sage)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("12a", "6a", "12p", "6p", "11p").forEach {
                Text(it, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Your heaviest hour is ${TaperPlan.hhmm(worstHour * 60)} — about " +
                "${profile[worstHour].roundToInt()} minutes a day, averaged over $range days.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun AppBreakdown(ctx: MainActivity, refreshKey: Int, range: Int) {
    val totals = remember(refreshKey, range) {
        val acc = HashMap<String, Int>()
        (0L until range.toLong()).forEach { back ->
            UsageRepo.minutesByApp(ctx, back).forEach { (pkg, m) ->
                acc[pkg] = (acc[pkg] ?: 0) + m
            }
        }
        acc
    }
    val grand = max(1, totals.values.sum())
    val topApp = max(1, totals.values.maxOrNull() ?: 1)
    Card {
        Kicker("Where the time went")
        Spacer(Modifier.height(4.dp))
        Text(
            "${fmtGap(grand)} across $range days",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(14.dp))
        if (totals.isEmpty()) {
            Text(
                "Nothing recorded yet for this range.",
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
        }
        totals.entries.sortedByDescending { it.value }.forEach { (pkg, mins) ->
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Catalog.label(ctx, pkg), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${mins * 100 / grand}% · ${fmtGap(mins)}",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp))
                    .background(Rail).padding(bottom = 0.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth((mins.toFloat() / topApp).coerceIn(0.02f, 1f))
                        .fillMaxHeight().clip(RoundedCornerShape(999.dp)).background(Terracotta)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "Averages ${fmtGap(grand / max(1, range))} a day.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LadderTab(activity: MainActivity, refreshKey: Int) {
    val ctx = activity
    val week = TaperPlan.weekNumber(ctx)
    val streak = remember(refreshKey) { History.streak(ctx) }
    val over = remember(refreshKey) { History.overDaysThisWeek(ctx) }

    Text("Your ladder", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Each week the target steps down and the pause screen gets harder to skip.",
        color = Muted, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(16.dp))

    if (ctx.baselineMinutes == 0) {
        Card {
            Text(
                if (TaperPlan.measuring(ctx))
                    "Your ladder appears once the baseline week finishes — ${TaperPlan.measureDaysLeft(ctx)} days to go."
                else "Set a baseline in Settings and your ladder appears here."
            )
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            Card {
                Text("$streak", style = MaterialTheme.typography.headlineMedium)
                Text("day streak", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Box(Modifier.weight(1f)) {
            Card {
                Text("${History.minutesSaved(ctx) / 60}h", style = MaterialTheme.typography.headlineMedium)
                Text("saved vs baseline", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (over > 0) {
        Spacer(Modifier.height(10.dp))
        Text(
            "$over day${if (over == 1) "" else "s"} over target in the last week.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    val zeroWeek = TaperPlan.weeksToZero(ctx)
    if (zeroWeek != null) {
        Text(
            "At this pace the target reaches zero in week $zeroWeek.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
    }
    Card {
        TaperPlan.ladder(ctx).forEach { (w, t, s) ->
            val now = w == week
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (now) Terracotta else if (w < week) Sage else Rail)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Week $w" + if (now) " · now" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (now) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(TaperPlan.stageDetail(s), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    if (t <= 0) "none" else "$t min",
                    color = Muted, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun SettingsTab(activity: MainActivity) {
    val ctx = activity
    var paceV by remember { mutableStateOf(ctx.pace) }
    var hard by remember { mutableStateOf(ctx.hardBlock) }
    var reminders by remember { mutableStateOf(ctx.remindersOn) }
    var trackedSet by remember { mutableStateOf(ctx.tracked) }
    var floor by remember { mutableIntStateOf(ctx.floorMinutes) }
    var showAllApps by remember { mutableStateOf(false) }
    var appQuery by remember { mutableStateOf("") }
    val allApps = remember(showAllApps) { if (showAllApps) Catalog.installed(ctx) else emptyList() }

    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))

    Card {
        Kicker("Where you are")
        Spacer(Modifier.height(8.dp))
        Text(
            "Week ${TaperPlan.weekNumber(ctx)} · " +
                TaperPlan.stageLabel(TaperPlan.stage(TaperPlan.weekNumber(ctx))),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Week 1 lets you skip the screen the moment it appears. From there the skip " +
                "button arrives a few seconds later each stage, and past your target it " +
                "stops appearing at all.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Taper pace")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.05f to "Gentle", 0.08f to "Steady", 0.12f to "Firm").forEach { (v, label) ->
                val on = paceV == v
                Button(
                    onClick = { paceV = v; ctx.pace = v },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Terracotta else Cream,
                        contentColor = if (on) Cream else Ink
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (floor == 0)
                "−${(paceV * 100).roundToInt()}% a week, all the way down to zero."
            else
                "−${(paceV * 100).roundToInt()}% a week, stopping at $floor minutes.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        Text("Floor", style = MaterialTheme.typography.labelSmall, color = Muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "zero", 30 to "30", 60 to "60", 90 to "90").forEach { (f, label) ->
                val on = floor == f
                OutlinedButton(
                    onClick = { floor = f; ctx.floorMinutes = f },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (on) TerracottaTint else Color.Transparent,
                        contentColor = if (on) TerracottaDeep else Muted
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp, 8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(label, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Below twenty minutes the percentage stops meaning much, so the last stretch " +
                "steps down five minutes a week. A floor of zero means it keeps going " +
                "until there's nothing left of the allowance.",
            color = Muted, style = MaterialTheme.typography.labelSmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("How firmly to intervene")
        Spacer(Modifier.height(4.dp))
        ToggleRow("Reminders near target", reminders) { reminders = it; ctx.remindersOn = it }
        ToggleRow("Lock me out at the target now", hard) { hard = it; ctx.hardBlock = it }
        Text(
            "On, there's no skip button once you pass the day's target — from this week " +
                "rather than week 7. The ladder turns it on by itself eventually.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Apps to track")
        Spacer(Modifier.height(4.dp))
        Catalog.suggested.forEach { (pkg, label) ->
            val installed = UsageRepo.isInstalled(ctx, pkg)
            ToggleRow(
                if (installed) label else "$label (not installed)",
                pkg in trackedSet
            ) { on ->
                val next = trackedSet.toMutableSet()
                if (on) next.add(pkg) else next.remove(pkg)
                trackedSet = next
                ctx.tracked = next
            }
        }
        // Anything already tracked that isn't in the suggested seven, so a hand-picked
        // app stays visible without opening the full list again.
        trackedSet.filter { p -> Catalog.suggested.none { it.first == p } }.sorted().forEach { pkg ->
            ToggleRow(Catalog.label(ctx, pkg), true) {
                val next = trackedSet.toMutableSet().also { it.remove(pkg) }
                trackedSet = next
                ctx.tracked = next
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { showAllApps = !showAllApps },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (showAllApps) "Hide the full list" else "Add any app on this phone") }

        if (showAllApps) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = appQuery,
                onValueChange = { appQuery = it },
                singleLine = true,
                shape = RoundedCornerShape(999.dp),
                placeholder = { Text("Search apps", color = Muted) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val q = appQuery.trim().lowercase()
            val shown = allApps.filter { q.isEmpty() || it.second.lowercase().contains(q) }
            if (allApps.isEmpty()) {
                Text(
                    "Couldn't read the app list on this device.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
            shown.take(120).forEach { (pkg, label) ->
                ToggleRow(label, pkg in trackedSet) { on ->
                    val next = trackedSet.toMutableSet()
                    if (on) next.add(pkg) else next.remove(pkg)
                    trackedSet = next
                    ctx.tracked = next
                }
            }
            if (shown.size > 120) {
                Text(
                    "${shown.size - 120} more — narrow it with the search box.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Anything you add is counted and paused exactly like the rest. Games, news, " +
                "shopping, a browser — whatever the actual problem is.",
            color = Muted, style = MaterialTheme.typography.labelSmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Baseline")
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                ctx.baselineMinutes > 0 -> "${ctx.baselineMinutes} minutes a day"
                TaperPlan.measuring(ctx) -> "Measuring — ${TaperPlan.measureDaysLeft(ctx)} days to go"
                else -> "Not measured yet"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Two ways to set it, and only complete days count either way — today is still " +
                "running, so counting it would read low all morning and high all night. " +
                "Android only keeps about ten days of detail, so the history read looks at " +
                "the last seven complete days.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                History.backfill(ctx)
                val avg = TaperPlan.historyAverage(ctx, 3, 7)
                if (avg > 0) {
                    ctx.baselineMinutes = avg
                    Prefs.run {
                        ctx.baselinePendingUntil = -1L
                        ctx.startEpochDay = LocalDate.now().toEpochDay()
                    }
                }
            },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Use the last 7 complete days") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                ctx.baselineMinutes = 0
                Prefs.run { ctx.baselinePendingUntil = LocalDate.now().toEpochDay() + 7 }
            },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Measure a fresh week") }
        Spacer(Modifier.height(8.dp))
        Text(
            "A fresh measurement blocks nothing for seven days, then starts week 1 from " +
                "what it saw. Restarting either one resets the ladder to week 1.",
            color = Muted, style = MaterialTheme.typography.labelSmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card(SageTint) {
        Kicker("Try the pause screen", SageDeep)
        Spacer(Modifier.height(6.dp))
        Text(
            "Fires the real screen on demand, so you can see each gate and check the ad " +
                "panel without waiting to go past your target. The ad shows on every " +
                "pause now, whichever gate is in play.",
            color = SageDeep, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))
        var adStatus by remember { mutableStateOf(Ads.status()) }
        listOf(
            Triple("Skip straight through", Gate.FREE, 0),
            Triple("Ten-second wait", Gate.WAIT, 10),
            Triple("Locked out", Gate.LOCKED, 15)
        ).forEach { (label, gate, secs) ->
            OutlinedButton(
                onClick = {
                    Ads.preload(ctx)
                    adStatus = Ads.status()
                    ctx.startActivity(
                        Intent(ctx, PauseActivity::class.java).apply {
                            putExtra(PauseActivity.EXTRA_PKG, ctx.tracked.firstOrNull() ?: "")
                            putExtra(PauseActivity.EXTRA_DELAY, secs)
                            putExtra(PauseActivity.EXTRA_GATE, gate.name)
                            putExtra(PauseActivity.EXTRA_REASON, "Preview — nothing is blocked.")
                            putExtra(PauseActivity.EXTRA_USED, UsageRepo.totalMinutes(ctx))
                            putExtra(PauseActivity.EXTRA_COOLDOWN, gate == Gate.LOCKED)
                        }
                    )
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SageDeep),
                modifier = Modifier.fillMaxWidth()
            ) { Text(label) }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ad status", color = SageDeep, style = MaterialTheme.typography.labelSmall)
                Text(adStatus, color = SageDeep, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { Ads.preload(ctx); adStatus = Ads.status() },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SageDeep)
            ) { Text("Check") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (AdConfig.USE_TEST)
                "Test ads are on, so the panel always fills and is safe to tap. Set " +
                    "AdConfig.USE_TEST = false for release."
            else
                "Live ads are on — don't tap the ad itself.",
            color = SageDeep, style = MaterialTheme.typography.labelSmall
        )
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Cream,
                checkedTrackColor = Terracotta,
                uncheckedThumbColor = Cream,
                uncheckedTrackColor = Rail,
            )
        )
    }
}
