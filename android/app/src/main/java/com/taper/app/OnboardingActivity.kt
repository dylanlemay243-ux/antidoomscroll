package com.taper.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.baselinePendingUntil
import com.taper.app.Prefs.onboarded
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.startEpochDay
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * First run: what it does, screen-time access, a baseline (history or a measured
 * week), a pace, then the pause screen. Also carries the prominent disclosure
 * Play requires before an accessibility service is enabled.
 */
class OnboardingActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaperTheme { Flow(refresh) } }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    @Composable
    private fun Flow(refreshKey: Int) {
        var step by remember { mutableIntStateOf(0) }
        val hasUsage = remember(refreshKey) { UsageRepo.hasPermission(this) }
        val blockerOn = remember(refreshKey) { BlockerService.isEnabled(this) }

        Column(
            Modifier.fillMaxSize().background(Cream)
                .verticalScroll(rememberScrollState())
                .padding(26.dp, 40.dp, 26.dp, 28.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(if (i == step) 26.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (i <= step) Terracotta else Rail)
                    )
                }
            }
            Spacer(Modifier.height(28.dp))

            when (step) {
                0 -> Intro { step = 1 }
                1 -> UsageStep(hasUsage) { step = 2 }
                2 -> BaselineStep(refreshKey) { step = 3 }
                else -> BlockerStep(blockerOn)
            }
        }
    }

    @Composable
    private fun Intro(next: () -> Unit) {
        Text("Taper doesn't ask you to quit", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))
        Body(
            "It measures what you actually do now, then lowers the ceiling a little each " +
                "week. The first weeks only tell you where you stand. Blocking comes later, " +
                "once the number is already moving."
        )
        Spacer(Modifier.height(10.dp))
        Body("Everything stays on your phone. Taper has no account and sends nothing anywhere.")
        Spacer(Modifier.height(28.dp))
        Primary("Start", next)
    }

    @Composable
    private fun UsageStep(granted: Boolean, next: () -> Unit) {
        Text("Let Taper count minutes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Body(
            "Android keeps per-app screen time behind a permission you grant by hand. " +
                "Taper reads it to know how long you spent in the apps you choose."
        )
        Spacer(Modifier.height(24.dp))
        if (granted) {
            Note("Granted.")
            Spacer(Modifier.height(16.dp))
            Primary("Next", next)
        } else {
            Primary("Open the setting") {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Spacer(Modifier.height(10.dp))
            Note("Find Taper in the list and turn it on, then come back here.")
        }
    }

    @Composable
    private fun BaselineStep(refreshKey: Int, next: () -> Unit) {
        val avg = remember(refreshKey) {
            History.backfill(this)
            TaperPlan.historyAverage(this, 3)
        }
        val dayCount = remember(refreshKey) { TaperPlan.historyDayCount(this) }
        var chosenPace by remember { mutableStateOf(pace) }
        val projected = if (avg > 0) avg else 180

        Text("Your baseline", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Body(
            "Everything downstream is a percentage of this number, so it's worth getting " +
                "right. Seven clean days with nothing blocked is the honest way to measure it."
        )
        if (avg > 0) {
            Spacer(Modifier.height(12.dp))
            Note(
                "Android's own records suggest about $avg minutes a day across $dayCount " +
                    "complete days. You can start from that instead and skip the wait."
            )
        }
        Spacer(Modifier.height(22.dp))

        Text("Pace", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(0.05f to "Gentle", 0.08f to "Steady", 0.12f to "Firm").forEach { (v, label) ->
                val on = chosenPace == v
                Button(
                    onClick = { chosenPace = v; pace = v },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (on) Terracotta else Sand,
                        contentColor = if (on) Cream else Ink
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Note(
            "−${(chosenPace * 100).roundToInt()}% a week. From $projected " +
                "minutes that's about ${weeksToFloor(projected, chosenPace)} weeks to 90."
        )
        Spacer(Modifier.height(24.dp))

        Primary("Measure my baseline for 7 days") {
            baselineMinutes = 0
            baselinePendingUntil = LocalDate.now().toEpochDay() + 7
            next()
        }
        if (avg > 0) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    baselineMinutes = avg
                    startEpochDay = LocalDate.now().toEpochDay()
                    baselinePendingUntil = -1L
                    next()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TerracottaDeep),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Start now from $avg minutes") }
        }
        Spacer(Modifier.height(10.dp))
        Note("Nothing is blocked while it measures.")
    }

    @Composable
    private fun BlockerStep(enabled: Boolean) {
        Text("The pause screen", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(14.dp))
        Body(
            "To put a pause in front of a tracked app, Taper needs to know when that app " +
                "comes to the front. Android exposes that through an accessibility service."
        )
        Spacer(Modifier.height(12.dp))
        Surface(color = SageTint, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "What Taper does with it",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It reads the package name of the app in front — nothing else. Screen " +
                        "contents, text and passwords are never requested or read. Nothing " +
                        "leaves your phone.",
                    color = SageDeep,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        if (enabled) {
            Note("On. You're set up.")
            Spacer(Modifier.height(16.dp))
            Primary("Go to Taper") { finishOnboarding() }
        } else {
            Primary("Open accessibility settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Spacer(Modifier.height(10.dp))
            Note("Installed apps → Taper → on. Then come back.")
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { finishOnboarding() },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Skip for now — reminders only") }
        }
    }

    private fun finishOnboarding() {
        onboarded = true
        History.backfill(this)
        Reminders.schedule(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    @Composable
    private fun Body(text: String) =
        Text(text, color = Ink, style = MaterialTheme.typography.bodyMedium)

    @Composable
    private fun Note(text: String) =
        Text(text, color = Muted, style = MaterialTheme.typography.bodySmall)

    @Composable
    private fun Primary(label: String, onClick: () -> Unit) =
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Terracotta, contentColor = Cream),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(label, fontWeight = FontWeight.SemiBold) }

    private fun weeksToFloor(from: Int, pace: Float, floor: Int = 90): Int {
        var m = from.toFloat()
        var w = 0
        while (m > floor && w < 60) {
            m *= (1f - pace)
            w++
        }
        return w
    }
}
