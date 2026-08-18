package com.taper.app

import android.app.TimePickerDialog
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
import com.taper.app.Prefs.autoAdjust
import com.taper.app.Prefs.windDownMinute
import com.taper.app.Prefs.startWeek
import com.taper.app.Prefs.tracked
import com.taper.app.Prefs.windows
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
        TaperPlan.applyAutoAdjust(this)
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
    val streak = remember(refreshKey) { History.streak(ctx) }

    Text(
        if (measuring) "Measuring your baseline" else "Week $week of your taper",
        style = MaterialTheme.typography.headlineMedium
    )
    Spacer(Modifier.height(4.dp))
    Text(
        when {
            measuring -> "${TaperPlan.measureDaysLeft(ctx)} days to go · nothing is blocked yet"
            TaperPlan.insideWindow(ctx) -> TaperPlan.stageLabel(stage) + " · window open now"
            else -> {
                val mins = TaperPlan.minutesToNextWindow(ctx)
                TaperPlan.stageLabel(stage) + if (mins != null) " · next window in ${fmtGap(mins)}" else ""
            }
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
                val frac = if (target > 0) (used.toFloat() / target).coerceIn(0f, 1f) else 0f
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
                        if (target > 0) max(0, target - used).toString() else used.toString(),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        if (target > 0) "minutes left today" else "minutes today",
                        color = Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                measuring -> "Just watching. Your target starts after the measurement."
                target > 0 -> "Used $used of $target · baseline ${ctx.baselineMinutes}"
                else -> "Set a baseline in Settings to get a target"
            },
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        if (target > 0) {
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
    ctx.tracked.sortedByDescending { byApp[it] ?: 0 }.forEach { pkg ->
        val mins = byApp[pkg] ?: 0
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Catalog.label(pkg), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("$mins min", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            val frac = if (target > 0) (mins.toFloat() / target).coerceIn(0.01f, 1f) else 0.01f
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(Rail)) {
                Box(
                    Modifier.fillMaxWidth(frac).fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp)).background(Terracotta)
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
    val days = remember(refreshKey) { UsageRepo.lastSevenDays(ctx) }
    val target = TaperPlan.targetMinutes(ctx)
    val peak = max(1, max(days.maxOrNull() ?: 1, target))
    val recorded = remember(refreshKey) { History.all(ctx) }

    Text("Insights", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Last 7 days")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(150.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.reversed().forEachIndexed { i, m ->
                val h = (m.toFloat() / peak * 120f).coerceAtLeast(6f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$m", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (target in 1 until m) TerracottaDeep else Terracotta)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LocalDate.now().minusDays((6 - i).toLong()).dayOfWeek.name.take(1),
                        color = Muted, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (target > 0) "Darker bars went past your $target-minute target."
            else "Set a baseline to see your target here.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    val avg = if (days.any { it > 0 }) days.filter { it > 0 }.average().roundToInt() else 0
    Card(SageTint) {
        Kicker("Average", SageDeep)
        Spacer(Modifier.height(6.dp))
        Text("$avg minutes a day", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ctx.baselineMinutes > 0)
                "Baseline was ${ctx.baselineMinutes}, so that's ${pctDown(ctx.baselineMinutes, avg)}% down. " +
                    "Today so far: ${days.firstOrNull() ?: 0}."
            else "Today so far: ${days.firstOrNull() ?: 0}.",
            color = SageDeep, style = MaterialTheme.typography.bodyMedium
        )
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
                    val over = t > 0 && mins > t
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
private fun LadderTab(activity: MainActivity, refreshKey: Int) {
    val ctx = activity
    val week = TaperPlan.weekNumber(ctx)
    val streak = remember(refreshKey) { History.streak(ctx) }
    val over = remember(refreshKey) { History.overDaysThisWeek(ctx) }

    Text("Your ladder", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Each week the target steps down and the intervention firms up.",
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
                    Text(TaperPlan.stageLabel(s), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("$t min", color = Muted, style = MaterialTheme.typography.bodyMedium)
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
    var wins by remember { mutableStateOf(ctx.windows) }
    var floor by remember { mutableIntStateOf(ctx.floorMinutes) }
    var startW by remember { mutableIntStateOf(ctx.startWeek) }
    var auto by remember { mutableStateOf(ctx.autoAdjust) }
    var windDown by remember { mutableIntStateOf(ctx.windDownMinute) }
    val suggested = remember { TaperPlan.suggestedWindDown(ctx) }

    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))

    Card {
        Kicker("Where the ladder starts")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Ease in", 3 to "Pause", 6 to "Windows").forEach { (w, label) ->
                val on = startW == w
                Button(
                    onClick = { startW = w; ctx.startWeek = w },
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
            "Right now: " + TaperPlan.stageLabel(TaperPlan.stage(TaperPlan.weekNumber(ctx))) +
                ", week " + TaperPlan.weekNumber(ctx) + " of the plan.",
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
            "−${(paceV * 100).roundToInt()}% a week, never below $floor minutes.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60, 90, 120).forEach { f ->
                val on = floor == f
                OutlinedButton(
                    onClick = { floor = f; ctx.floorMinutes = f },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (on) TerracottaTint else Color.Transparent,
                        contentColor = if (on) TerracottaDeep else Muted
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("floor $f") }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Wind-down")
        Spacer(Modifier.height(6.dp))
        Text(
            if (windDown >= 0)
                "Tracked apps close down after ${TaperPlan.hhmm(windDown)}."
            else
                "No cut-off yet. Taper sets one once it can see when your scrolling runs long.",
            color = Ink, style = MaterialTheme.typography.bodyMedium
        )
        if (suggested != null && suggested != windDown) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Your own pattern points at ${TaperPlan.hhmm(suggested)} — that's where the " +
                    "late runs start.",
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { windDown = suggested; ctx.windDownMinute = suggested },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use ${TaperPlan.hhmm(suggested)}") }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Let Taper adjust it", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Moves the cut-off as your habits move, without asking.",
                    color = Muted, style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = auto,
                onCheckedChange = {
                    auto = it
                    ctx.autoAdjust = it
                    if (it) {
                        TaperPlan.applyAutoAdjust(ctx)
                        windDown = ctx.windDownMinute
                    }
                }
            )
        }
        if (windDown >= 0) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { windDown = -1; ctx.windDownMinute = -1; auto = false; ctx.autoAdjust = false },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Clear the cut-off") }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Scroll windows")
        Spacer(Modifier.height(4.dp))
        wins.forEachIndexed { i, w ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeButton(hhmm(w.first)) {
                    pickTime(activity, w.first) { picked ->
                        val next = wins.toMutableList()
                        next[i] = picked..maxOf(picked + 5, w.last)
                        wins = next; ctx.windows = next
                    }
                }
                Text(" – ", color = Muted)
                TimeButton(hhmm(w.last)) {
                    pickTime(activity, w.last) { picked ->
                        val next = wins.toMutableList()
                        next[i] = minOf(w.first, picked - 5)..picked
                        wins = next; ctx.windows = next
                    }
                }
                Spacer(Modifier.weight(1f))
                if (wins.size > 1) {
                    OutlinedButton(
                        onClick = {
                            val next = wins.toMutableList().also { it.removeAt(i) }
                            wins = next; ctx.windows = next
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)
                    ) { Text("Remove") }
                }
            }
        }
        if (wins.size < 4) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = {
                    // listOf() matters: `wins + (1200..1230)` resolves to the
                    // plus(Iterable) overload and flattens the range into ints.
                    val next = wins + listOf(1200..1230)
                    wins = next; ctx.windows = next
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep)
            ) { Text("Add a window") }
        }
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("How firmly to intervene")
        Spacer(Modifier.height(4.dp))
        ToggleRow("Reminders near target", reminders) { reminders = it; ctx.remindersOn = it }
        ToggleRow("Hard block outside windows", hard) { hard = it; ctx.hardBlock = it }
        Text(
            "Hard block removes the way through. Stage 4 of the ladder turns it on anyway.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Apps to track")
        Spacer(Modifier.height(4.dp))
        Catalog.all.forEach { (pkg, label) ->
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                ctx.baselineMinutes = 0
                Prefs.run { ctx.baselinePendingUntil = LocalDate.now().toEpochDay() + 7 }
            },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep)
        ) { Text("Measure a fresh week") }
    }
    Spacer(Modifier.height(20.dp))
}

private fun hhmm(minsOfDay: Int): String = "%02d:%02d".format(minsOfDay / 60, minsOfDay % 60)

private fun pickTime(activity: MainActivity, initial: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        activity,
        { _, h, m -> onPicked(h * 60 + m) },
        initial / 60, initial % 60, false
    ).show()
}

@Composable
private fun TimeButton(label: String, onClick: () -> Unit) =
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink)
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }

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
