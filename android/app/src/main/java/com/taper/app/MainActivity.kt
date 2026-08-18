package com.taper.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.ensureStarted
import com.taper.app.Prefs.floorMinutes
import com.taper.app.Prefs.hardBlock
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.remindersOn
import com.taper.app.Prefs.tracked
import com.taper.app.Prefs.windows
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    2 -> LadderTab(activity)
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
    val week = TaperPlan.weekNumber(ctx)
    val target = TaperPlan.targetMinutes(ctx)
    val stage = TaperPlan.stage(week)

    Text("Week $week of your taper", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        TaperPlan.stageLabel(stage) + if (TaperPlan.insideWindow(ctx)) " · window open now" else "",
        color = Muted, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(18.dp))

    if (!hasUsage || !blockerOn || ctx.baselineMinutes == 0) {
        Card(TerracottaTint) {
            Kicker("Set up", TerracottaDeep)
            Spacer(Modifier.height(6.dp))
            Text("Taper needs three things", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            if (!hasUsage) {
                SetupRow("Screen time access", "So Taper can count real minutes.") {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            if (!blockerOn) {
                SetupRow("The pause screen", "Turn on Taper under Installed apps.") {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            if (hasUsage && ctx.baselineMinutes == 0) {
                val avg = remember(refreshKey) {
                    val d = UsageRepo.lastSevenDays(ctx).filter { it > 0 }
                    if (d.isEmpty()) 0 else d.average().roundToInt()
                }
                SetupRow(
                    "A baseline",
                    if (avg > 0) "Your last 7 days average $avg min/day. Use that."
                    else "No history yet — leave it a day and come back."
                ) {
                    if (avg > 0) {
                        ctx.baselineMinutes = avg
                        ctx.ensureStarted()
                    }
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
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = s,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (frac >= 1f) TerracottaDeep else Terracotta,
                        startAngle = -90f, sweepAngle = 360f * frac, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = s,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
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
            if (target > 0) "Used $used of $target · baseline ${ctx.baselineMinutes}"
            else "Set a baseline to get a target",
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

    Spacer(Modifier.height(18.dp))
    Kicker("Today's apps")
    Spacer(Modifier.height(10.dp))
    val list = ctx.tracked.sortedByDescending { byApp[it] ?: 0 }
    list.forEach { pkg ->
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

    Text("Insights", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Last 7 days")
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.reversed().forEach { m ->
                val h = (m.toFloat() / peak * 130f).coerceAtLeast(6f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$m", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (target in 1 until m) TerracottaDeep else Terracotta)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (target > 0) "Bars past your $target-minute target are darker. Left is a week ago."
            else "Left is a week ago.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(Modifier.height(16.dp))
    val today = days.firstOrNull() ?: 0
    val avg = if (days.any { it > 0 }) days.filter { it > 0 }.average().roundToInt() else 0
    Card(SageTint) {
        Kicker("Average", SageDeep)
        Spacer(Modifier.height(6.dp))
        Text("$avg minutes a day", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            if (ctx.baselineMinutes > 0)
                "Baseline was ${ctx.baselineMinutes}. Today so far: $today."
            else "Today so far: $today.",
            color = SageDeep, style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LadderTab(activity: MainActivity) {
    val ctx = activity
    val week = TaperPlan.weekNumber(ctx)
    Text("Your ladder", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Each week the target steps down and the intervention firms up.",
        color = Muted, style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(16.dp))
    if (ctx.baselineMinutes == 0) {
        Card { Text("Set a baseline on the Today tab and your ladder appears here.") }
        return
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
    var pace by remember { mutableStateOf(ctx.pace) }
    var hard by remember { mutableStateOf(ctx.hardBlock) }
    var reminders by remember { mutableStateOf(ctx.remindersOn) }
    var trackedSet by remember { mutableStateOf(ctx.tracked) }

    Text("Settings", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))

    Card {
        Kicker("Taper pace")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.05f to "Gentle", 0.08f to "Steady", 0.12f to "Firm").forEach { (v, label) ->
                val on = pace == v
                Button(
                    onClick = { pace = v; ctx.pace = v },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Terracotta else Color.Transparent,
                        contentColor = if (on) Cream else Ink
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "−${(pace * 100).roundToInt()}% a week, never below ${ctx.floorMinutes} minutes.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("Scroll windows")
        Spacer(Modifier.height(8.dp))
        ctx.windows.forEach { w ->
            Text(
                "%02d:%02d – %02d:%02d".format(w.first / 60, w.first % 60, w.last / 60, w.last % 60),
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Editing windows in-app comes next; for now these are the defaults.",
            color = Muted, style = MaterialTheme.typography.bodySmall
        )
    }

    Spacer(Modifier.height(16.dp))
    Card {
        Kicker("How firmly to intervene")
        Spacer(Modifier.height(4.dp))
        ToggleRow("Reminders near target", reminders) { reminders = it; ctx.remindersOn = it }
        ToggleRow("Hard block outside windows", hard) { hard = it; ctx.hardBlock = it }
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
            if (ctx.baselineMinutes > 0) "${ctx.baselineMinutes} minutes a day"
            else "Not measured yet",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { ctx.baselineMinutes = 0 },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep)
        ) { Text("Re-measure baseline") }
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
