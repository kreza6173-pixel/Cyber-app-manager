package io.github.kreza6173pixel.cyberappmanager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.cyberappmanager.exec.ConnectionState
import io.github.kreza6173pixel.cyberappmanager.exec.ExecBridge
import io.github.kreza6173pixel.cyberappmanager.inventory.InventoryRepository
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuRuntime
import io.github.kreza6173pixel.cyberappmanager.shizuku.ShizukuState
import io.github.kreza6173pixel.cyberappmanager.ui.apps.AppDetailScreen
import io.github.kreza6173pixel.cyberappmanager.ui.apps.AppsScreen
import io.github.kreza6173pixel.cyberappmanager.ui.console.ConsoleScreen
import io.github.kreza6173pixel.cyberappmanager.ui.home.HomeScreen
import io.github.kreza6173pixel.cyberappmanager.ui.theme.CyberAppManagerTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val runtime by lazy { ShizukuRuntime(applicationContext) }
    private val bridge by lazy { ExecBridge(applicationContext) }
    private val inventory by lazy { InventoryRepository(applicationContext, bridge) }

    /**
     * The app ships English only. Pin en-US so digits, layout direction and formatting stay
     * consistent on devices set to an RTL or non-Latin-digit locale.
     */
    override fun attachBaseContext(newBase: Context) {
        Locale.setDefault(Locale.US)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.US)
        config.setLayoutDirection(Locale.US)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CyberAppManagerTheme {
                DisposableEffect(Unit) {
                    runtime.start()
                    bridge.start()
                    onDispose {
                        bridge.stop()
                        runtime.stop()
                    }
                }

                AppRoot(runtime = runtime, bridge = bridge, inventory = inventory)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The manager may have been started or stopped while we were backgrounded.
        runtime.refresh()
    }
}

private enum class Screen { HOME, CONSOLE, APPS, APP_DETAIL }

// TopAppBar is still @ExperimentalMaterial3Api in material3 1.4.0.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(runtime: ShizukuRuntime, bridge: ExecBridge, inventory: InventoryRepository) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var selectedPkg by remember { mutableStateOf("") }

    // Bind the user service only while Shizuku is usable; this also recovers after a restart.
    val ready = runtime.state == ShizukuState.READY
    DisposableEffect(ready) {
        if (ready) bridge.connect() else bridge.disconnect()
        onDispose { bridge.disconnect() }
    }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    // Feature screens need the user service and fall back to home when not READY.
    val shown = if (ready) screen else Screen.HOME

    BackHandler(enabled = shown != Screen.HOME) {
        screen = if (shown == Screen.APP_DETAIL) Screen.APPS else Screen.HOME
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when (shown) {
                                Screen.HOME -> R.string.app_name
                                Screen.CONSOLE -> R.string.console_title
                                Screen.APPS -> R.string.apps_title
                                Screen.APP_DETAIL -> R.string.detail_title
                            }
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (shown) {
            Screen.HOME -> HomeScreen(
                runtime = runtime,
                modifier = contentModifier,
                onOpenApps = { screen = Screen.APPS },
                onOpenConsole = { screen = Screen.CONSOLE },
            )
            Screen.CONSOLE -> ConsoleScreen(bridge = bridge, modifier = contentModifier)
            Screen.APPS -> AppsScreen(
                repository = inventory,
                connected = connected,
                modifier = contentModifier,
                onOpenApp = { pkg ->
                    selectedPkg = pkg
                    screen = Screen.APP_DETAIL
                },
            )
            Screen.APP_DETAIL -> AppDetailScreen(
                pkg = selectedPkg,
                repository = inventory,
                connected = connected,
                modifier = contentModifier,
            )
        }
    }
}
