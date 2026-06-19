package dev.sudoloser.ecodash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sudoloser.ecodash.plugin.PluginUiRenderer

@Composable
fun DashboardGrid(
    viewModel: DashboardViewModel,
    onNavigateToPlugins: () -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetLayout by viewModel.widgetLayout.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val activePlugins by viewModel.activePluginsLayouts.collectAsState()

    // Combined list of widgets: built-ins ("minecraft", "network", "media_server") and enabled plugins
    val installedPlugins = remember { viewModel.pluginManager.getInstalledPlugins() }
    val enabledPluginIds = installedPlugins.filter { it.isEnabled }.map { it.id }
    
    // We display all widgets in the layout store order, followed by any newly added plugins not yet in layout
    val allWidgetKeys = remember(widgetLayout, enabledPluginIds) {
        val list = widgetLayout.toMutableList()
        // Add plugins that are enabled but not in layout
        enabledPluginIds.forEach { id ->
            if (!list.contains(id)) {
                list.add(id)
            }
        }
        // Remove widgets from layout that are not enabled plugins and not built-in keys
        list.filter { it == "minecraft" || it == "network" || it == "media_server" || enabledPluginIds.contains(it) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 350.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(allWidgetKeys) { index, key ->
            WidgetContainer(
                key = key,
                index = index,
                totalCount = allWidgetKeys.size,
                isEditMode = isEditMode,
                viewModel = viewModel,
                activePlugins = activePlugins,
                onMoveUp = { viewModel.reorderWidget(index, index - 1) },
                onMoveDown = { viewModel.reorderWidget(index, index + 1) }
            )
        }
    }
}

@Composable
fun WidgetContainer(
    key: String,
    index: Int,
    totalCount: Int,
    isEditMode: Boolean,
    viewModel: DashboardViewModel,
    activePlugins: Map<String, Map<String, Any>>,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    // Shake animation setup for Edit Mode
    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val rotation by infiniteTransition.animateFloat(
        initialValue = -1.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shake_rotation"
    )

    val containerModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isEditMode) {
                Modifier
                    .graphicsLayer(rotationZ = rotation)
                    .drawBehind {
                        val stroke = Stroke(
                            width = 2f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                        drawRoundRect(color = Color.Gray, style = stroke)
                    }
            } else {
                Modifier
            }
        )

    Box(modifier = containerModifier) {
        Column {
            if (isEditMode) {
                // Drag & Reorder header control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Widget #$index",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Move Up",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = index < totalCount - 1,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Move Down",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Render specific widget cards
            when (key) {
                "minecraft" -> MinecraftWidgetCard(viewModel, isEditMode)
                "network" -> NetworkWidgetCard(viewModel, isEditMode)
                "media_server" -> MediaServerWidgetCard(viewModel, isEditMode)
                else -> {
                    // Custom loaded plugin widget
                    val uiLayout = activePlugins[key]
                    if (uiLayout != null) {
                        PluginUiRenderer.Render(uiLayout)
                    } else {
                        // Empty/Loading fallback for loaded plugin
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Executing Plugin Script: $key...", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinecraftWidgetCard(viewModel: DashboardViewModel, isEditMode: Boolean) {
    val mcIp by viewModel.mcIp.collectAsState()
    val mcPort by viewModel.mcPort.collectAsState()
    val mcIsBedrock by viewModel.mcIsBedrock.collectAsState()
    val mcStatus by viewModel.mcStatus.collectAsState()
    val mcIsLoading by viewModel.mcIsLoading.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mcStatus?.isOnline == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Minecraft Status", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (mcIsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    val statusText = if (mcStatus?.isOnline == true) "Online" else "Offline"
                    val statusColor = if (mcStatus?.isOnline == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditMode) {
                // Configuration panel
                OutlinedTextField(
                    value = mcIp,
                    onValueChange = {
                        viewModel.mcIp.value = it
                        viewModel.saveWidgetConfigurations()
                    },
                    label = { Text("Server IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mcPort,
                    onValueChange = {
                        viewModel.mcPort.value = it
                        viewModel.saveWidgetConfigurations()
                    },
                    label = { Text("Server Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bedrock Edition (UDP)")
                    Switch(
                        checked = mcIsBedrock,
                        onCheckedChange = {
                            viewModel.mcIsBedrock.value = it
                            viewModel.saveWidgetConfigurations()
                            viewModel.refreshMinecraft()
                        }
                    )
                }
            } else {
                // Standard UI
                if (mcStatus?.isOnline == true) {
                    Text("MOTD: ${mcStatus?.motd}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Players: ${mcStatus?.onlinePlayers} / ${mcStatus?.maxPlayers}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Version: ${mcStatus?.version}", fontSize = 12.sp, color = Color.Gray)
                } else {
                    val errMsg = mcStatus?.error ?: "Cannot resolve connection parameters"
                    Text("Server is unreachable: $errMsg", fontSize = 14.sp, color = Color(0xFFC62828))
                }
            }
        }
    }
}

@Composable
fun NetworkWidgetCard(viewModel: DashboardViewModel, isEditMode: Boolean) {
    val speedTestUrl by viewModel.speedTestServerUrl.collectAsState()
    val speedTestState by viewModel.speedTestState.collectAsState()
    val speedTestHistory by viewModel.speedTestHistory.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val networkGateway by viewModel.networkGateway.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Network Speed Test", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (isEditMode) {
                OutlinedTextField(
                    value = speedTestUrl,
                    onValueChange = {
                        viewModel.speedTestServerUrl.value = it
                        viewModel.saveWidgetConfigurations()
                    },
                    label = { Text("Speed Test companion URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Text("Connection: $connectionState", fontSize = 14.sp)
                Text("Gateway: $networkGateway", fontSize = 14.sp)

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Indicator during active speed test
                if (speedTestState.state != "IDLE" && speedTestState.state != "COMPLETE" && speedTestState.state != "FAILED") {
                    Column {
                        Text("Running speed test: ${speedTestState.state}...", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = speedTestState.progress,
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Down: ${formatBps(speedTestState.downloadSpeed)}", fontSize = 12.sp)
                            Text("Up: ${formatBps(speedTestState.uploadSpeed)}", fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.runSpeedTest() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run Speed Test")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (speedTestHistory.isNotEmpty()) {
                    Text("History Logs", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    speedTestHistory.forEach { log ->
                        Text("• ${log.second}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaServerWidgetCard(viewModel: DashboardViewModel, isEditMode: Boolean) {
    val mediaType by viewModel.mediaServerType.collectAsState()
    val mediaHost by viewModel.mediaServerHost.collectAsState()
    val mediaToken by viewModel.mediaServerToken.collectAsState()
    val mediaStatus by viewModel.mediaPlaybackState.collectAsState()
    val mediaIsLoading by viewModel.mediaIsLoading.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (mediaStatus?.isOnline == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Media Server: $mediaType", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (mediaIsLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Platform Type")
                    // Segmented selection
                    Row {
                        listOf("Jellyfin", "Plex", "Emby").forEach { type ->
                            Button(
                                onClick = {
                                    viewModel.mediaServerType.value = type
                                    viewModel.saveWidgetConfigurations()
                                    viewModel.refreshMediaServer()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mediaType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.padding(2.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(type, fontSize = 10.sp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mediaHost,
                    onValueChange = {
                        viewModel.mediaServerHost.value = it
                        viewModel.saveWidgetConfigurations()
                    },
                    label = { Text("Server Host URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = mediaToken,
                    onValueChange = {
                        viewModel.mediaServerToken.value = it
                        viewModel.saveWidgetConfigurations()
                    },
                    label = { Text("Auth Token / Api Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                if (mediaStatus?.isOnline == true) {
                    Text(mediaStatus?.statusString ?: "Uptime: healthy", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Active Streams: ${mediaStatus?.activeStreamsCount}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    
                    val used = mediaStatus?.storageUsedGb ?: 0.0
                    val total = mediaStatus?.storageTotalGb ?: 0.0
                    val percent = mediaStatus?.storagePercent ?: 0.0
                    
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Disk Space Info", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(String.format("%.1f GB / %.1f GB (%.1f%%)", used, total, percent), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = (percent / 100.0).toFloat().coerceIn(0.0f, 1.0f),
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            strokeCap = StrokeCap.Round
                        )
                    }
                } else {
                    val errMsg = mediaStatus?.error ?: "Authentication error or container offline"
                    Text("Connection failed: $errMsg", fontSize = 14.sp, color = Color(0xFFC62828))
                }
            }
        }
    }
}

private fun formatBps(bps: Double): String {
    return when {
        bps >= 1_000_000_000.0 -> String.format("%.1f Gbps", bps / 1_000_000_000.0)
        bps >= 1_000_000.0 -> String.format("%.1f Mbps", bps / 1_000_000.0)
        bps >= 1_000.0 -> String.format("%.1f Kbps", bps / 1_000.0)
        else -> String.format("%.0f bps", bps)
    }
}
