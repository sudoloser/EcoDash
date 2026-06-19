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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sudoloser.ecodash.plugin.PluginExecutor
import dev.sudoloser.ecodash.plugin.PluginMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class HubPlugin(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val downloadUrl: String,
    val version: String
)

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
    var selectedTab by remember { mutableIntStateOf(0) }

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
            Column {
                TopAppBar(
                    title = { Text("Plugins") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Installed") },
                        icon = { Icon(Icons.Default.Extension, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Plugin Hub") },
                        icon = { Icon(Icons.Default.Store, contentDescription = null) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { pickZipLauncher.launch("application/zip") }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Import Plugin Zip")
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> InstalledPluginsTab(
                pluginsList = pluginsList,
                pluginManager = pluginManager,
                viewModel = viewModel,
                onPluginsChanged = { pluginsList = pluginManager.getInstalledPlugins() },
                onConfigure = { selectedPluginForConfig = it },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> PluginHubTab(
                viewModel = viewModel,
                installedIds = pluginsList.map { it.id }.toSet(),
                onInstalled = {
                    pluginsList = pluginManager.getInstalledPlugins()
                    viewModel.refreshPlugins()
                },
                modifier = Modifier.padding(innerPadding)
            )
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
fun InstalledPluginsTab(
    pluginsList: List<PluginMetadata>,
    pluginManager: dev.sudoloser.ecodash.plugin.PluginManager,
    viewModel: DashboardViewModel,
    onPluginsChanged: () -> Unit,
    onConfigure: (PluginMetadata) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (pluginsList.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No plugins installed",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Import a .zip file or browse the Plugin Hub.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else {
            items(pluginsList) { plugin ->
                PluginItemCard(
                    plugin = plugin,
                    onToggleEnabled = { enabled ->
                        pluginManager.setPluginEnabled(plugin.id, enabled)
                        onPluginsChanged()
                        viewModel.refreshPlugins()
                    },
                    onConfigure = { onConfigure(plugin) }
                )
            }
        }
    }
}

@Composable
fun PluginHubTab(
    viewModel: DashboardViewModel,
    installedIds: Set<String>,
    onInstalled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hubPlugins by remember { mutableStateOf<List<HubPlugin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var installingId by remember { mutableStateOf<String?>(null) }

    val hubUrl = "https://raw.githubusercontent.com/sudoloser/EcoDash/main/docs/plugin-hub.json"

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(hubUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "{}"
                        val json = JSONObject(body)
                        val arr = json.getJSONArray("plugins")
                        val list = mutableListOf<HubPlugin>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                HubPlugin(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    author = obj.optString("author", "Unknown"),
                                    description = obj.optString("description", ""),
                                    downloadUrl = obj.getString("download_url"),
                                    version = obj.optString("version", "1.0.0")
                                )
                            )
                        }
                        hubPlugins = list
                    } else {
                        errorMsg = "Failed to fetch hub: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                errorMsg = e.message ?: "Network error"
            }
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Loading Plugin Hub...", fontSize = 14.sp)
                    }
                }
            }
        } else if (errorMsg != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Failed to load Plugin Hub", fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(errorMsg ?: "", fontSize = 13.sp, color = Color(0xFFC62828))
                    }
                }
            }
        } else if (hubPlugins.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No plugins available in the hub yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(hubPlugins) { hubPlugin ->
                val alreadyInstalled = installedIds.contains(hubPlugin.id)
                val isInstalling = installingId == hubPlugin.id

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(hubPlugin.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "v${hubPlugin.version}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text("by ${hubPlugin.author}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(hubPlugin.description, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        if (alreadyInstalled) {
                            OutlinedButton(onClick = {}, enabled = false) {
                                Text("Installed", fontSize = 12.sp)
                            }
                        } else if (isInstalling) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Button(
                                onClick = {
                                    installingId = hubPlugin.id
                                    scope.launch {
                                        val success = downloadAndInstallPlugin(
                                            viewModel = viewModel,
                                            downloadUrl = hubPlugin.downloadUrl
                                        )
                                        installingId = null
                                        if (success) {
                                            Toast.makeText(context, "${hubPlugin.name} installed!", Toast.LENGTH_SHORT).show()
                                            onInstalled()
                                        } else {
                                            Toast.makeText(context, "Failed to install ${hubPlugin.name}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Install", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun downloadAndInstallPlugin(
    viewModel: DashboardViewModel,
    downloadUrl: String
): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder().url(downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val stream = response.body?.byteStream() ?: return@withContext false
            val result = viewModel.pluginManager.importPluginFromStream(stream)
            result.isSuccess
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
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
                    onCheckedChange = onToggleEnabled
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
