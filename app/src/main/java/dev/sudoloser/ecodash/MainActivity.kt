package dev.sudoloser.ecodash

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sudoloser.ecodash.ui.DashboardGrid
import dev.sudoloser.ecodash.ui.DashboardViewModel
import dev.sudoloser.ecodash.ui.PluginManagerScreen
import java.io.File

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

                    when (currentScreen) {
                        "plugins" -> PluginManagerScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "dashboard" }
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "dashboard" }
                        )
                        else -> MainDashboardScreen(
                            viewModel = viewModel,
                            onNavigateToPlugins = { currentScreen = "plugins" },
                            onNavigateToSettings = { currentScreen = "settings" }
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
    onNavigateToPlugins: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val isEditMode by viewModel.isEditMode.collectAsState()

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

                    IconButton(onClick = onNavigateToSettings) {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val refreshInterval by viewModel.refreshInterval.collectAsState()
    var backupResult by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            val tempFile = File(context.cacheDir, "ecodash_backup_temp.zip")
            viewModel.createBackupTo(tempFile) { ok, msg ->
                if (ok) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            tempFile.inputStream().use { it.copyTo(out) }
                        }
                        tempFile.delete()
                        backupResult = "Backup saved successfully!"
                    } catch (e: Exception) {
                        backupResult = "Failed to save backup: ${e.message}"
                    }
                } else {
                    backupResult = msg
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            restoring = true
            val tempFile = File(context.cacheDir, "ecodash_restore_temp.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                viewModel.restoreFromBackup(tempFile) { ok, msg ->
                    tempFile.delete()
                    restoring = false
                    if (ok) {
                        backupResult = "Restore successful! Dashboard refreshed."
                    } else {
                        backupResult = msg
                    }
                }
            } catch (e: Exception) {
                restoring = false
                backupResult = "Restore failed: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text("Auto-Refresh Interval", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            .clickable { viewModel.setRefreshIntervalSec(seconds) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = refreshInterval == seconds,
                            onClick = { viewModel.setRefreshIntervalSec(seconds) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 16.sp)
                    }
                }
            }

            HorizontalDivider()

            Text("Backup & Restore", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Button(
                onClick = { saveBackupLauncher.launch("ecodash.backup") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Backup")
            }

            Button(
                onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !restoring,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (restoring) "Restoring..." else "Restore from Backup")
            }

            backupResult?.let {
                Text(
                    text = it,
                    color = if (it.startsWith("Backup saved") || it.startsWith("Restore successful"))
                        Color(0xFF81C784) else Color(0xFFEF5350),
                    fontSize = 14.sp
                )
            }

            HorizontalDivider()

            Text(
                "Backup saves all dashboard settings, widget configs, plugin settings, and plugin files.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
