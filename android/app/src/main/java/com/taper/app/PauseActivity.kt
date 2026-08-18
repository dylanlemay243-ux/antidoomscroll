package com.taper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * counts down, and only then offers a way through (unless hard block is on).
 */
class PauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val delay = intent.getIntExtra(EXTRA_DELAY, 15)
        val allow = intent.getBooleanExtra(EXTRA_ALLOW, true)
        val reason = intent.getStringExtra(EXTRA_REASON) ?: ""
        val used = intent.getIntExtra(EXTRA_USED, 0)

        setContent {
            TaperTheme {
                PauseScreen(
                    appName = Catalog.label(pkg),
                    reason = reason,
                    used = used,
                    target = TaperPlan.targetMinutes(this),
                    seconds = delay,
                    allowContinue = allow,
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
        const val EXTRA_ALLOW = "allow"
        const val EXTRA_REASON = "reason"
        const val EXTRA_USED = "used"
    }
}

@Composable
private fun PauseScreen(
    appName: String,
    reason: String,
    used: Int,
    target: Int,
    seconds: Int,
    allowContinue: Boolean,
    onContinue: () -> Unit,
    onLeave: () -> Unit,
) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1000)
            left -= 1
        }
    }

    Box(
        Modifier.fillMaxSize().background(NightSage).padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                appName.uppercase(),
                color = Color(0xFFAEBF92),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (target > 0) "$used of $target minutes today" else "$used minutes today",
                color = Color(0xFFF0FAE1),
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge
            )
            Spacer(Modifier.height(12.dp))
            Text(
                reason,
                color = Color(0xFFCCDBB2),
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onLeave,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF0FAE1), contentColor = NightSage
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Put the phone down") }
            Spacer(Modifier.height(12.dp))
            if (allowContinue) {
                OutlinedButton(
                    onClick = onContinue,
                    enabled = left == 0,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF0FAE1)),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(if (left > 0) "Wait ${left}s" else "Go in anyway")
                }
            } else {
                Text(
                    "Hard block is on. This one is closed until your next window.",
                    color = Color(0xFFAEBF92),
                    textAlign = TextAlign.Center,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
