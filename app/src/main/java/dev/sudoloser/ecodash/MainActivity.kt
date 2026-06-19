package dev.sudoloser.ecodash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sudoloser.ecodash.ui.DashboardGrid
import dev.sudoloser.ecodash.ui.DashboardViewModel
import dev.sudoloser.ecodash.ui.PluginManagerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcoDashTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("dashboard") }
                    val viewModel: DashboardViewModel = viewModel()

                    if (currentScreen == "plugins") {
                        PluginManagerScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "dashboard" }
                        )
                    } else {
                        MainDashboardScreen(
                            viewModel = viewModel,
                            onNavigateToPlugins = { currentScreen = "plugins" }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToPlugins: () -> Unit
) {
    val isEditMode by viewModel.isEditMode.collectAsState()
    val refreshInterval by viewModel.refreshInterval.collectAsState()
    var showIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("EcoDash", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Always-On Tablet Kiosk Dashboard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAllWidgets() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Manual Refresh")
                    }

                    IconButton(onClick = { viewModel.isEditMode.value = !isEditMode }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Layout",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = { showIntervalDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }

                    IconButton(onClick = onNavigateToPlugins) {
                        Icon(imageVector = Icons.Default.Extension, contentDescription = "Plugins")
                    }
                }
            )
        }
    ) { innerPadding ->
        DashboardGrid(
            viewModel = viewModel,
            onNavigateToPlugins = onNavigateToPlugins,
            modifier = Modifier.padding(innerPadding)
        )

        if (showIntervalDialog) {
            AutoRefreshIntervalDialog(
                currentInterval = refreshInterval,
                onDismiss = { showIntervalDialog = false },
                onSelect = { intervalSec ->
                    viewModel.setRefreshIntervalSec(intervalSec)
                    showIntervalDialog = false
                }
            )
        }
    }
}

@Composable
fun AutoRefreshIntervalDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto Refresh Interval") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 30, 60, 300).forEach { seconds ->
                    val label = when (seconds) {
                        10 -> "10 seconds"
                        30 -> "30 seconds"
                        60 -> "1 minute"
                        300 -> "5 minutes"
                        else -> "$seconds seconds"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentInterval == seconds,
                            onClick = { onSelect(seconds) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun EcoDashTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF64B5F6),
        secondary = Color(0xFF81C784),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onPrimary = Color(0xFF0D47A1),
        onSecondary = Color(0xFF1B5E20),
        onBackground = Color(0xFFE0E0E0),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF2C2C2C),
        onSurfaceVariant = Color(0xFFB0BEC5)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
