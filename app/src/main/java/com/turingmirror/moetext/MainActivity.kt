package com.turingmirror.moetext

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.turingmirror.moetext.data.ConfigStore
import com.turingmirror.moetext.engine.AppConfig
import com.turingmirror.moetext.service.MoeAccessibilityService
import com.turingmirror.moetext.ui.RulesTab
import com.turingmirror.moetext.ui.StatusTab
import com.turingmirror.moetext.ui.TestTab
import com.turingmirror.moetext.ui.UnicodeTab
import com.turingmirror.moetext.ui.theme.GlassBottomBar
import com.turingmirror.moetext.ui.theme.MoeTheme
import com.turingmirror.moetext.ui.theme.glassBackdrop
import dev.chrisbanes.haze.rememberHazeState

class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)
    private val serviceConnected = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshServiceState()
        setContent {
            MoeTheme {
                Root(
                    enabled = serviceEnabled.value,
                    connected = serviceConnected.value,
                    onOpenAccessibility = { openAccessibilitySettings() },
                    onRequestBattery = { requestIgnoreBatteryOptimization() },
                    initialConfig = ConfigStore.load(applicationContext),
                    onPersist = { ConfigStore.save(applicationContext, it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshServiceState()
    }

    private fun refreshServiceState() {
        serviceEnabled.value = try {
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains("$packageName/") == true
        } catch (e: Exception) {
            false
        }
        serviceConnected.value = MoeAccessibilityService.connected
    }

    private fun requestIgnoreBatteryOptimization() {
        val intents = listOf(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }
        }
        Toast.makeText(this, "无法打开电池设置", Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Root(
    enabled: Boolean,
    connected: Boolean,
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
    initialConfig: AppConfig,
    onPersist: (AppConfig) -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var config by remember { mutableStateOf(initialConfig) }
    val tabStates = rememberSaveableStateHolder()

    val hazeState = rememberHazeState()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(Modifier.fillMaxSize().glassBackdrop(hazeState)) {
            tabStates.SaveableStateProvider(tab) {
            when (tab) {
                0 -> StatusTab(
                    enabled,
                    connected,
                    config,
                    onConfig = {
                        config = it
                        onPersist(it)
                    },
                    onOpenAccessibility,
                    onRequestBattery
                )
                1 -> RulesTab(config, onConfig = { config = it }, onPersist = { onPersist(it) })
                2 -> TestTab(config)
                3 -> UnicodeTab()
            }
            }
        }
        GlassBottomBar(
            selected = tab,
            onSelect = { tab = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        )
    }
}
