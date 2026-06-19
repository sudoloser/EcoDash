package dev.sudoloser.ecodash.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.sudoloser.ecodash.data.LayoutStore
import dev.sudoloser.ecodash.network.*
import dev.sudoloser.ecodash.plugin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class GridItem(
    val id: String,
    val col: Int = 0,
    val row: Int = 0,
    val colSpan: Int = 1,
    val rowSpan: Int = 1
)

enum class GridPattern(val columns: Int, val label: String) {
    DENSE_2(2, "2 Columns"),
    DENSE_3(3, "3 Columns"),
    DENSE_4(4, "4 Columns"),
    FOCUS_LEFT(3, "Focus Left"),
    FOCUS_TOP(2, "Focus Top"),
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val layoutStore = LayoutStore(context)
    private val httpClient = OkHttpClient.Builder().build()
    private val gson = Gson()

    val pluginManager = PluginManager(context)
    private val pluginExecutor = PluginExecutor(context, httpClient)

    val widgetLayout = layoutStore.widgetLayoutFlow.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("minecraft", "network", "media_server"))
    val refreshInterval = layoutStore.refreshIntervalFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    var isEditMode = MutableStateFlow(false)
    var gridPattern = MutableStateFlow(GridPattern.DENSE_3)
    var gridItems = MutableStateFlow<List<GridItem>>(emptyList())

    val mcIp = MutableStateFlow("127.0.0.1")
    val mcPort = MutableStateFlow("25565")
    val mcIsBedrock = MutableStateFlow(false)
    val mcStatus = MutableStateFlow<MinecraftStatus?>(null)
    val mcIsLoading = MutableStateFlow(false)

    val speedTestServerUrl = MutableStateFlow("http://127.0.0.1:7867")
    val speedTestState = MutableStateFlow<SpeedTestResult>(SpeedTestResult("IDLE"))
    val speedTestHistory = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    val connectionState = MutableStateFlow("Offline")
    val networkGateway = MutableStateFlow("N/A")

    val mediaServerType = MutableStateFlow("Jellyfin")
    val mediaServerHost = MutableStateFlow("http://127.0.0.1:7867")
    val mediaServerToken = MutableStateFlow("")
    val mediaPlaybackState = MutableStateFlow<MediaPlaybackStatus?>(null)
    val mediaIsLoading = MutableStateFlow(false)

    val activePluginsLayouts = MutableStateFlow<Map<String, Map<String, Any>>>(emptyMap())
    val pluginsVersion = MutableStateFlow(0)

    private var refreshJob: Job? = null

    init {
        loadWidgetConfigurations()
        loadGridItems()
        startAutoRefresh()
        updateNetworkInfo()
    }

    private fun loadWidgetConfigurations() {
        val prefs = context.getSharedPreferences("widget_configs", Application.MODE_PRIVATE)
        mcIp.value = prefs.getString("mc_ip", "127.0.0.1") ?: "127.0.0.1"
        mcPort.value = prefs.getString("mc_port", "25565") ?: "25565"
        mcIsBedrock.value = prefs.getBoolean("mc_is_bedrock", false)
        
        speedTestServerUrl.value = prefs.getString("speedtest_url", "http://127.0.0.1:7867") ?: "http://127.0.0.1:7867"
        
        val historyJson = prefs.getString("speedtest_history", "[]")
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<Pair<Long, String>>>() {}.type
            speedTestHistory.value = com.google.gson.Gson().fromJson(historyJson, type) ?: emptyList()
        } catch (e: Exception) {
            speedTestHistory.value = emptyList()
        }

        mediaServerType.value = prefs.getString("media_type", "Jellyfin") ?: "Jellyfin"
        mediaServerHost.value = prefs.getString("media_host", "http://127.0.0.1:7867") ?: "http://127.0.0.1:7867"
        mediaServerToken.value = prefs.getString("media_token", "") ?: ""
    }

    fun saveWidgetConfigurations() {
        val prefs = context.getSharedPreferences("widget_configs", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("mc_ip", mcIp.value)
            .putString("mc_port", mcPort.value)
            .putBoolean("mc_is_bedrock", mcIsBedrock.value)
            .putString("speedtest_url", speedTestServerUrl.value)
            .putString("media_type", mediaServerType.value)
            .putString("media_host", mediaServerHost.value)
            .putString("media_token", mediaServerToken.value)
            .apply()
    }

    private fun loadGridItems() {
        val prefs = context.getSharedPreferences("widget_grid", Application.MODE_PRIVATE)
        val patternName = prefs.getString("pattern", GridPattern.DENSE_3.name) ?: GridPattern.DENSE_3.name
        gridPattern.value = try { GridPattern.valueOf(patternName) } catch (_: Exception) { GridPattern.DENSE_3 }
        val json = prefs.getString("items", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<GridItem>>() {}.type
                gridItems.value = gson.fromJson(json, type)
            } catch (_: Exception) {
                gridItems.value = generateDefaultItems()
            }
        } else {
            gridItems.value = generateDefaultItems()
        }
    }

    private fun saveGridItems() {
        val prefs = context.getSharedPreferences("widget_grid", Application.MODE_PRIVATE)
        prefs.edit()
            .putString("pattern", gridPattern.value.name)
            .putString("items", gson.toJson(gridItems.value))
            .apply()
    }

    private fun generateDefaultItems(): List<GridItem> {
        val ids = widgetLayout.value
        val cols = gridPattern.value.columns
        return ids.mapIndexed { i, id ->
            GridItem(id = id, col = i % cols, row = i / cols)
        }
    }

    fun moveGridItem(id: String, newCol: Int, newRow: Int) {
        gridItems.value = gridItems.value.map {
            if (it.id == id) it.copy(col = newCol, row = newRow) else it
        }
        saveGridItems()
    }

    fun resizeGridItem(id: String, colSpan: Int, rowSpan: Int) {
        gridItems.value = gridItems.value.map {
            if (it.id == id) it.copy(colSpan = colSpan.coerceIn(1, 4), rowSpan = rowSpan.coerceIn(1, 4)) else it
        }
        saveGridItems()
    }

    fun applyGridPattern(pattern: GridPattern) {
        gridPattern.value = pattern
        gridItems.value = generateDefaultItems()
        saveGridItems()
    }

    fun setRefreshIntervalSec(seconds: Int) {
        viewModelScope.launch {
            layoutStore.saveRefreshInterval(seconds)
            startAutoRefresh()
        }
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshAllWidgets()
                delay(refreshInterval.value * 1000L)
            }
        }
    }

    fun refreshAllWidgets() {
        updateNetworkInfo()
        refreshMinecraft()
        refreshMediaServer()
        refreshPlugins()
    }

    fun refreshMinecraft() {
        viewModelScope.launch {
            mcIsLoading.value = true
            val ip = mcIp.value
            val port = mcPort.value.toIntOrNull() ?: 25565
            mcStatus.value = if (mcIsBedrock.value) {
                MinecraftPing.pingBedrock(ip, port)
            } else {
                MinecraftPing.pingJava(ip, port)
            }
            mcIsLoading.value = false
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch {
            val url = speedTestServerUrl.value
            SpeedTestRunner.runSpeedTest(httpClient, url).collect { result ->
                speedTestState.value = result
                if (result.state == "COMPLETE") {
                    val formattedRates = String.format(
                        "Down: %s | Up: %s",
                        formatBitrate(result.downloadSpeed),
                        formatBitrate(result.uploadSpeed)
                    )
                    val newHistory = speedTestHistory.value.toMutableList()
                    newHistory.add(0, System.currentTimeMillis() to formattedRates)
                    if (newHistory.size > 5) {
                        newHistory.removeAt(newHistory.size - 1)
                    }
                    speedTestHistory.value = newHistory
                    
                    val prefs = context.getSharedPreferences("widget_configs", Application.MODE_PRIVATE)
                    prefs.edit().putString("speedtest_history", com.google.gson.Gson().toJson(newHistory)).apply()
                }
            }
        }
    }

    private fun formatBitrate(bitsPerSecond: Double): String {
        return when {
            bitsPerSecond >= 1_000_000_000.0 -> String.format("%.2f Gbps", bitsPerSecond / 1_000_000_000.0)
            bitsPerSecond >= 1_000_000.0 -> String.format("%.2f Mbps", bitsPerSecond / 1_000_000.0)
            bitsPerSecond >= 1_000.0 -> String.format("%.2f Kbps", bitsPerSecond / 1_000.0)
            else -> String.format("%.2f bps", bitsPerSecond)
        }
    }

    fun updateNetworkInfo() {
        val (state, gateway) = SpeedTestRunner.getNetworkInfo(context)
        connectionState.value = state
        networkGateway.value = gateway
    }

    fun refreshMediaServer() {
        viewModelScope.launch {
            mediaIsLoading.value = true
            val host = mediaServerHost.value
            val token = mediaServerToken.value
            mediaPlaybackState.value = fetchMediaServerStatus(host, token)
            mediaIsLoading.value = false
        }
    }

    private suspend fun fetchMediaServerStatus(host: String, token: String): MediaPlaybackStatus = withContext(Dispatchers.IO) {
        try {
            val targetUrl = if (host.endsWith("/")) "${host}api/v1/dashboard/status" else "$host/api/v1/dashboard/status"
            
            val request = okhttp3.Request.Builder()
                .url(targetUrl)
                .header("Authorization", "Bearer $token")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = org.json.JSONObject(bodyString)
                    val status = json.getString("server_status")
                    val uptime = json.getLong("uptime_seconds")
                    val activeStreams = json.getInt("active_streams")
                    
                    val storage = json.getJSONObject("storage")
                    val used = storage.getDouble("used_gb")
                    val total = storage.getDouble("total_gb")
                    val percent = storage.getDouble("percent_used")
                    
                    MediaPlaybackStatus(
                        isOnline = true,
                        serverName = mediaServerType.value,
                        statusString = "Status: $status | Uptime: ${uptime / 3600}h ${(uptime % 3600) / 60}m",
                        activeStreamsCount = activeStreams,
                        storageUsedGb = used,
                        storageTotalGb = total,
                        storagePercent = percent
                    )
                } else {
                    MediaPlaybackStatus(
                        isOnline = false,
                        error = "API Error: ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            MediaPlaybackStatus(
                isOnline = false,
                error = e.message ?: "Connection timed out"
            )
        }
    }

    fun refreshPlugins() {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.IO) { pluginManager.getInstalledPlugins() }
            val results = mutableMapOf<String, Map<String, Any>>()
            
            coroutineScope {
                installed.filter { it.isEnabled }.forEach { plugin ->
                    launch(Dispatchers.IO) {
                        try {
                            val pluginDir = pluginManager.getPluginDirectory(plugin.id)
                            val logicFile = File(pluginDir, plugin.logicScript)
                            val mainFile = File(pluginDir, plugin.mainScript)
                            
                            if (logicFile.exists() && mainFile.exists()) {
                                val variables = mutableMapOf<String, Any>()
                                variables.putAll(plugin.settingsValues)
                                variables["httpClient"] = httpClient
                                variables["context"] = context
                                variables["pluginId"] = plugin.id

                                val evaluationResult = pluginExecutor.executeScript(logicFile, variables)

                                val uiVariables = mapOf<String, Any>(
                                    "data" to evaluationResult,
                                    "context" to context,
                                    "pluginId" to plugin.id
                                )
                                val uiLayout = pluginExecutor.executeScript(mainFile, uiVariables)
                                synchronized(results) {
                                    results[plugin.id] = uiLayout
                                }
                            }
                        } catch (e: Exception) {
                            synchronized(results) {
                                results[plugin.id] = mapOf(
                                    "type" to "Card",
                                    "backgroundColor" to "#FFCDD2",
                                    "children" to listOf(
                                        mapOf("type" to "Text", "text" to plugin.name, "style" to "Title"),
                                        mapOf("type" to "Text", "text" to "Execution Error: ${e.message}", "style" to "Body")
                                    )
                                )
                            }
                        }
                    }
                }
            }
            activePluginsLayouts.value = results
            pluginsVersion.value++
        }
    }

    fun reorderWidget(fromIndex: Int, toIndex: Int) {
        val current = widgetLayout.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val element = current.removeAt(fromIndex)
            current.add(toIndex, element)
            viewModelScope.launch {
                layoutStore.saveWidgetLayout(current)
            }
        }
    }

    fun reorderWidgetFull(newLayout: List<String>) {
        viewModelScope.launch {
            layoutStore.saveWidgetLayout(newLayout)
        }
    }

    fun createBackupTo(destFile: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileOutputStream(destFile).use { fos ->
                    ZipOutputStream(fos).use { zos ->
                        val manifest = JSONObject()

                        // --- Widget layout + refresh interval ---
                        val layoutJson = JSONObject()
                        layoutJson.put("widgetLayout", Gson().toJson(widgetLayout.value))
                        layoutJson.put("refreshInterval", refreshInterval.value)
                        manifest.put("dashboard", layoutJson)

                        // --- Widget configs SharedPrefs ---
                        val widgetPrefs = context.getSharedPreferences("widget_configs", Application.MODE_PRIVATE)
                        val wc = JSONObject()
                        for (key in widgetPrefs.all.keys) {
                            wc.put(key, widgetPrefs.all[key].toString())
                        }
                        manifest.put("widgetConfigs", wc)

                        // --- Plugin prefs ---
                        val pluginPrefs = context.getSharedPreferences("plugins_pref", Application.MODE_PRIVATE)
                        val pp = JSONObject()
                        for (key in pluginPrefs.all.keys) {
                            pp.put(key, pluginPrefs.all[key].toString())
                        }
                        manifest.put("pluginPrefs", pp)

                        // --- Grid items + pattern ---
                        val grid = JSONObject()
                        grid.put("pattern", gridPattern.value.name)
                        grid.put("items", gson.toJson(gridItems.value))
                        manifest.put("gridLayout", grid)

                        // Write manifest
                        zos.putNextEntry(ZipEntry("ecodash.json"))
                        zos.write(manifest.toString(2).toByteArray())
                        zos.closeEntry()

                        // --- Plugin files ---
                        val pluginsDir = File(context.filesDir, "plugins")
                        if (pluginsDir.exists()) {
                            for (pluginDir in pluginsDir.listFiles() ?: emptyArray()) {
                                addDirToZip(zos, pluginDir, "plugins/${pluginDir.name}")
                            }
                        }

                        // Write backup marker
                        zos.putNextEntry(ZipEntry("BACKUP"))
                        zos.write("ecodash-v1".toByteArray())
                        zos.closeEntry()
                    }
                }
                onResult(true, "Backup saved to ${destFile.name}")
            } catch (e: Exception) {
                onResult(false, "Backup failed: ${e.message}")
            }
        }
    }

    fun restoreFromBackup(sourceFile: File, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var manifestStr: String? = null
                val pluginFiles = mutableMapOf<String, ByteArray>()

                ZipInputStream(sourceFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val data = zis.readBytes()
                        when {
                            name == "ecodash.json" -> manifestStr = String(data)
                            name.startsWith("plugins/") && !name.endsWith("/") -> {
                                val relPath = name.removePrefix("plugins/")
                                pluginFiles[relPath] = data
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                val manifest = manifestStr?.let { JSONObject(it) } ?: throw Exception("ecodash.json not found in backup")

                // --- Restore widget configs ---
                val wc = manifest.optJSONObject("widgetConfigs")
                if (wc != null) {
                    val prefs = context.getSharedPreferences("widget_configs", Application.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    for (key in wc.keys()) {
                        prefs.edit().putString(key, wc.getString(key)).apply()
                    }
                }

                // --- Restore plugin prefs ---
                val pp = manifest.optJSONObject("pluginPrefs")
                if (pp != null) {
                    val prefs = context.getSharedPreferences("plugins_pref", Application.MODE_PRIVATE)
                    prefs.edit().clear().apply()
                    for (key in pp.keys()) {
                        prefs.edit().putString(key, pp.getString(key)).apply()
                    }
                }

                // --- Restore DataStore (widget layout + refresh interval) ---
                val dash = manifest.optJSONObject("dashboard")
                if (dash != null) {
                    if (dash.has("widgetLayout")) {
                        val type = object : TypeToken<List<String>>() {}.type
                        val layout: List<String> = Gson().fromJson(dash.getString("widgetLayout"), type)
                        layoutStore.saveWidgetLayout(layout)
                    }
                    if (dash.has("refreshInterval")) {
                        layoutStore.saveRefreshInterval(dash.getInt("refreshInterval"))
                    }
                }

                // --- Restore plugin files ---
                val pluginsDir = File(context.filesDir, "plugins")
                if (pluginFiles.isNotEmpty()) {
                    if (pluginsDir.exists()) pluginsDir.deleteRecursively()
                    pluginsDir.mkdirs()
                    for ((relPath, data) in pluginFiles) {
                        val outFile = File(pluginsDir, relPath)
                        outFile.parentFile?.mkdirs()
                        outFile.writeBytes(data)
                    }
                }

                // --- Restore grid layout ---
                val grid = manifest.optJSONObject("gridLayout")
                if (grid != null) {
                    try {
                        gridPattern.value = GridPattern.valueOf(grid.optString("pattern", GridPattern.DENSE_3.name))
                    } catch (_: Exception) {}
                    val itemsJson = grid.optString("items", null)
                    if (itemsJson != null) {
                        try {
                            val type = object : TypeToken<List<GridItem>>() {}.type
                            gridItems.value = gson.fromJson(itemsJson, type)
                        } catch (_: Exception) {}
                    }
                    saveGridItems()
                }

                // --- Reload everything ---
                loadWidgetConfigurations()
                loadGridItems()
                startAutoRefresh()
                onResult(true, "Backup restored successfully. Dashboard will refresh.")
            } catch (e: Exception) {
                onResult(false, "Restore failed: ${e.message}")
            }
        }
    }

    private fun addDirToZip(zos: ZipOutputStream, dir: File, prefix: String) {
        for (file in dir.listFiles() ?: emptyArray()) {
            val entryName = "$prefix/${file.name}"
            if (file.isDirectory) {
                addDirToZip(zos, file, entryName)
            } else {
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }
}

data class MediaPlaybackStatus(
    val isOnline: Boolean,
    val serverName: String = "",
    val statusString: String = "",
    val activeStreamsCount: Int = 0,
    val storageUsedGb: Double = 0.0,
    val storageTotalGb: Double = 0.0,
    val storagePercent: Double = 0.0,
    val error: String? = null
)
