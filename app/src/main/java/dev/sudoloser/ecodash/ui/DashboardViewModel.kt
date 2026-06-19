package dev.sudoloser.ecodash.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sudoloser.ecodash.data.LayoutStore
import dev.sudoloser.ecodash.network.*
import dev.sudoloser.ecodash.plugin.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import java.io.File

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val layoutStore = LayoutStore(context)
    private val httpClient = OkHttpClient.Builder().build()
    
    val pluginManager = PluginManager(context)
    private val pluginExecutor = PluginExecutor(context, httpClient)

    val widgetLayout = layoutStore.widgetLayoutFlow.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("minecraft", "network", "media_server"))
    val refreshInterval = layoutStore.refreshIntervalFlow.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    var isEditMode = MutableStateFlow(false)

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

    private var refreshJob: Job? = null

    init {
        loadWidgetConfigurations()
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
                                
                                val evaluationResult = pluginExecutor.executeScript(logicFile, variables)
                                
                                val uiVariables = mapOf<String, Any>(
                                    "data" to evaluationResult,
                                    "context" to context
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
