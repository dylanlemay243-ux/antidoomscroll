package com.taper.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The interception moment. Sits in front of the tracked app: states the number,
 * makes you wait, and then charges whatever the week charges to continue.
 *
 * The gate is the whole idea. Early on it's FREE or a short WAIT — the screen only
 * has to be seen. Later the way through is an ad you choose to watch. At the top of
 * the ladder there is no way through until the cooldown is served.
 */
class PauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val delay = intent.getIntExtra(EXTRA_DELAY, 15)
        val gate = runCatching {
            Gate.valueOf(intent.getStringExtra(EXTRA_GATE) ?: "WAIT")
        }.getOrDefault(Gate.WAIT)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val used = intent.getIntExtra(EXTRA_USED, 0)
        val cooldown = intent.getBooleanExtra(EXTRA_COOLDOWN, false)

        if (gate == Gate.AD) Ads.preload(this)

        setContent {
            TaperTheme {
                PauseScreen(
                    activity = this,
                    appName = Catalog.label(pkg),
                    reason = reason,
                    used = used,
                    target = TaperPlan.targetMinutes(this),
                    seconds = delay,
                    gate = gate,
                    cooldown = cooldown,
                    onContinue = { finish() },
                    onLeave = {
                        startActivity(
                            Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_DELAY = "delay"
        const val EXTRA_GATE = "gate"
        const val EXTRA_REASON = "reason"
        const val EXTRA_USED = "used"
        const val EXTRA_COOLDOWN = "cooldown"
    }
}

private val Paper = Color(0xFFF0FAE1)
private val Faint = Color(0xFFAEBF92)
private val Soft = Color(0xFFCCDBB2)

@Composable
private fun PauseScreen(
    activity: Activity,
    appName: String,
    reason: String,
    used: Int,
    target: Int,
    seconds: Int,
    gate: Gate,
    cooldown: Boolean,
    onContinue: () -> Unit,
    onLeave: () -> Unit,
) {
    var left by remember { mutableIntStateOf(seconds) }

    // One ad is taken for this screen and released with it. Taken only for the AD
    // gate — the earlier weeks never see one.
    val ad = remember { if (gate == Gate.AD) Ads.take(activity) else null }
    DisposableEffect(Unit) { onDispose { Ads.release(ad) } }

    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    Box(
        Modifier.fillMaxSize().background(NightSage)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (cooldown) "COOLDOWN — ${appName.uppercase()}" else appName.uppercase(),
                color = Faint,
                style = MaterialTheme.typography.labelSmall
            )
            Gap(12)
            Text(
                if (target > 0) "$used of $target minutes today" else "$used minutes today",
                color = Paper,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge
            )
            Gap(12)
            Text(
                reason,
                color = Soft,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            if (gate == Gate.AD && ad != null) {
                Gap(20)
                NativeAdPanel(ad)
                Gap(10)
                Text(
                    "A small timeout, paid for by the ad above. It's what keeps Taper free " +
                        "— no subscription, no account — and it supports the person who built it.",
                    color = Faint,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Gap(32)

            Button(
                onClick = onLeave,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Paper, contentColor = NightSage
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Put the phone down") }

            Gap(12)

            when (gate) {
                Gate.LOCKED -> {
                    if (left > 0) {
                        Text(
                            "Closed for ${left}s.",
                            color = Paper,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Gap(6)
                    }
                    Text(
                        if (cooldown)
                            "The gap isn't skippable at this stage. Nothing to do but wait it out."
                        else
                            "Hard block is on. This one is closed until your next window.",
                        color = Faint,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (cooldown && left == 0) {
                        Gap(12)
                        OutlinedButton(
                            onClick = onContinue,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) { Text("Go back in") }
                    }
                }

                Gate.FREE -> OutlinedButton(
                    onClick = onContinue,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Go in") }

                Gate.WAIT -> OutlinedButton(
                    onClick = onContinue,
                    enabled = left == 0,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (left > 0) "Wait ${left}s" else "Go in anyway") }

                Gate.AD -> {
                    OutlinedButton(
                        onClick = onContinue,
                        enabled = left == 0,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text(if (left > 0) "Wait ${left}s" else "Go in anyway") }
                    if (ad == null) {
                        Gap(8)
                        Text(
                            "Just the timeout this time — " + Ads.status(),
                            color = Faint,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Gap(dp: Int) = Box(Modifier.height(dp.dp))
