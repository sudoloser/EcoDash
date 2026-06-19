package dev.sudoloser.ecodash.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sudoloser.ecodash.plugin.PluginExecutor
import dev.sudoloser.ecodash.plugin.PluginMetadata
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginManagerScreen(
    viewModel: DashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pluginManager = viewModel.pluginManager
    var pluginsList by remember { mutableStateOf(pluginManager.getInstalledPlugins()) }
    var selectedPluginForConfig by remember { mutableStateOf<PluginMetadata?>(null) }

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val result = pluginManager.importPluginFromZip(uri)
            if (result.isSuccess) {
                Toast.makeText(context, "Plugin imported successfully!", Toast.LENGTH_SHORT).show()
                pluginsList = pluginManager.getInstalledPlugins()
                viewModel.refreshPlugins()
            } else {
                Toast.makeText(context, "Failed to import plugin: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugins Manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickZipLauncher.launch("application/zip") }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Import Plugin Zip")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pluginsList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No plugins installed. Import a plugin zip below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(pluginsList) { plugin ->
                    PluginItemCard(
                        plugin = plugin,
                        onToggleEnabled = { enabled ->
                            pluginManager.setPluginEnabled(plugin.id, enabled)
                            pluginsList = pluginManager.getInstalledPlugins()
                            viewModel.refreshPlugins()
                        },
                        onConfigure = {
                            selectedPluginForConfig = plugin
                        }
                    )
                }
            }
        }

        selectedPluginForConfig?.let { plugin ->
            PluginConfigDialog(
                plugin = plugin,
                viewModel = viewModel,
                onDismiss = { selectedPluginForConfig = null },
                onSave = { updatedSettings ->
                    pluginManager.savePluginSettings(plugin.id, updatedSettings)
                    pluginsList = pluginManager.getInstalledPlugins()
                    viewModel.refreshPlugins()
                    selectedPluginForConfig = null
                }
            )
        }
    }
}

@Composable
fun PluginItemCard(
    plugin: PluginMetadata,
    onToggleEnabled: (Boolean) -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Author: ${plugin.author}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(plugin.description, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onConfigure) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Configure Settings")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = plugin.isEnabled,
                    onValueChange = onToggleEnabled
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginConfigDialog(
    plugin: PluginMetadata,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val context = LocalContext.current
    val pluginDir = viewModel.pluginManager.getPluginDirectory(plugin.id)
    val settingsFile = File(pluginDir, plugin.settingsScript)

    val settingsMap = remember(settingsFile) {
        try {
            val executor = PluginExecutor(context, okhttp3.OkHttpClient())
            executor.executeScript(settingsFile, emptyMap())
        } catch (e: Exception) {
            emptyMap<String, Any>()
        }
    }

    val fields = remember(settingsMap) {
        settingsMap["fields"] as? List<Map<String, Any>> ?: emptyList()
    }

    val inputValues = remember(plugin) {
        mutableStateMapOf<String, String>().apply {
            putAll(plugin.settingsValues)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${plugin.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (fields.isEmpty()) {
                    Text("No configuration settings needed for this plugin.", fontSize = 14.sp)
                } else {
                    fields.forEach { field ->
                        val key = field["key"] as? String ?: return@forEach
                        val type = field["type"] as? String ?: "text"
                        val label = field["label"] as? String ?: key
                        val defaultVal = field["defaultValue"] as? String ?: ""

                        if (!inputValues.containsKey(key)) {
                            inputValues[key] = defaultVal
                        }

                        val currentValue = inputValues[key] ?: ""

                        OutlinedTextField(
                            value = currentValue,
                            onValueChange = { inputValues[key] = it },
                            label = { Text(label) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (type == "password") PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(inputValues.toMap()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
