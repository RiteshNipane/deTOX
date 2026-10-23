package com.detox.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.detox.DetoxApp
import com.detox.data.Settings
import com.detox.service.ReelTrackerService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as DetoxApp
    val scope = rememberCoroutineScope()

    var a11yOn by remember { mutableStateOf(isAccessibilityEnabled(ctx)) }
    var notifOn by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(isBatteryExempt(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    var showDisclosure by remember { mutableStateOf(false) }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text("Accessibility permission") },
            text = {
                Text(
                    "deTOX uses Android's accessibility service only while Instagram or YouTube is open, to:\n\n" +
                        "• detect when you are in Reels / Shorts and count them\n" +
                        "• read on-screen labels such as \"AI info\" so it can hide AI-labelled reels (optional)\n" +
                        "• take screenshots for the on-device AI detector (optional, off by default)\n\n" +
                        "It does not read messages, passwords or other apps. Counts and screenshots are processed and " +
                        "stored on this phone only. Nothing is sent anywhere, and there are no ads or analytics.\n\n" +
                        "You can turn this off any time in Android settings."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDisclosure = false
                    ctx.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("I agree") }
            },
            dismissButton = { TextButton(onClick = { showDisclosure = false }) { Text("Not now") } },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        a11yOn = isAccessibilityEnabled(ctx)
        batteryOk = isBatteryExempt(ctx)
        refresh++
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOn = it }
    LaunchedEffect(Unit) { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

    val settings by app.settings.settings.collectAsState(initial = Settings())
    val count by app.todayCount.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("deTOX", fontSize = 32.sp, style = MaterialTheme.typography.headlineLarge)
        Text("Reels / Shorts today: $count", style = MaterialTheme.typography.titleMedium)
        Dashboard(app, settings.dailyLimit) { scope.launch { app.settings.setDailyLimit(it) } }

        if (!a11yOn || !batteryOk) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
        }
        if (!a11yOn) {
            StepCard(
                title = "1. Enable accessibility",
                body = "deTOX needs the accessibility service to count reels. Tap to see exactly what it does.",
                action = "Continue",
            ) { showDisclosure = true }
        }
        if (!batteryOk) {
            StepCard(
                title = "2. Allow background running",
                body = "Exempt deTOX from battery optimization so counting isn't killed.",
                action = "Allow",
            ) {
                ctx.startActivity(
                    Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                )
            }
        }

        Text("Settings", style = MaterialTheme.typography.titleMedium)
        ToggleRow("Floating counter", settings.overlayEnabled) { scope.launch { app.settings.setOverlayEnabled(it) } }
        ToggleRow("deSLOP: hide AI-labelled reels", settings.deSlopEnabled) { scope.launch { app.settings.setDeSlopEnabled(it) } }
        ToggleRow("Instagram Reels",settings.instagramEnabled) { scope.launch { app.settings.setAppEnabled("instagram", it) } }
        ToggleRow("YouTube Shorts", settings.youtubeEnabled) { scope.launch { app.settings.setAppEnabled("youtube_shorts", it) } }

        Text("Developer", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Save training frames: ${settings.collectLabel}", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = {
                val next = when (settings.collectLabel) { "off" -> "real"; "real" -> "ai"; else -> "off" }
                scope.launch { app.settings.setCollectLabel(next) }
            }) { Text("Change") }
        }
        Text(
            "Collect only while scrolling reels you KNOW are all real, or all AI. " +
                "Frames stay on this phone (Android/data/com.detox/files/frames).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StepCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun isAccessibilityEnabled(ctx: android.content.Context): Boolean {
    val am = ctx.getSystemService(AccessibilityManager::class.java)
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.name == ReelTrackerService::class.java.name }
}

private fun isBatteryExempt(ctx: android.content.Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
