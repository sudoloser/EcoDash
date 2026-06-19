package dev.sudoloser.ecodash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
    val pluginsVersion by viewModel.pluginsVersion.collectAsState()
    val gridPattern by viewModel.gridPattern.collectAsState()
    val gridItems by viewModel.gridItems.collectAsState()

    val installedPlugins = remember(pluginsVersion) { viewModel.pluginManager.getInstalledPlugins() }
    val enabledPluginIds = installedPlugins.filter { it.isEnabled }.map { it.id }

    val allWidgetKeys = remember(widgetLayout, enabledPluginIds) {
        val list = widgetLayout.toMutableList()
        enabledPluginIds.forEach { id ->
            if (!list.contains(id)) {
                list.add(id)
            }
        }
        list.filter { it == "network" || enabledPluginIds.contains(it) }
    }

    // Ensure all widget keys have a GridItem
    LaunchedEffect(allWidgetKeys) {
        val existing = gridItems.toMutableList()
        var changed = false
        val ids = existing.map { it.id }.toSet()
        for (key in allWidgetKeys) {
            if (key !in ids) {
                val cols = gridPattern.columns
                val row = existing.size / cols
                val col = existing.size % cols
                existing.add(GridItem(id = key, col = col, row = row))
                changed = true
            }
        }
        // Remove items for keys no longer present
        existing.removeAll { it.id !in allWidgetKeys }
        if (changed || existing.size != gridItems.size) {
            viewModel.gridItems.value = existing
        }
    }

    val columns = gridPattern.columns
    var containerWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gapPx = with(density) { 12.dp.toPx() }.toInt()
    val cellWidth = if (containerWidthPx > 0) (containerWidthPx - gapPx * (columns - 1)) / columns else 200

    // Scroll state for overflow
    val scrollState = rememberScrollState()

    var dragTargetId by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val headerHeightPx = with(density) { 40.dp.toPx() }.toInt()
    val cellHeight = (cellWidth * 0.55f).toInt()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Column {
            // Pattern selector in edit mode
            if (isEditMode) {
                PatternSelectorBar(
                    current = gridPattern,
                    onSelect = { viewModel.applyGridPattern(it) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onSizeChanged { containerWidthPx = it.width }
            ) {
                // Render only visible widgets at their grid position
                val visibleItems = gridItems.filter { it.id == "network" || it.id in enabledPluginIds }
                visibleItems.forEach { item ->
                    val xPx = item.col * (cellWidth + gapPx)
                    val yPx = item.row * (cellHeight + gapPx) + (if (isEditMode) 0 else 0)
                    val wPx = item.colSpan * cellWidth + (item.colSpan - 1) * gapPx
                    val hPx = item.rowSpan * cellHeight + (item.rowSpan - 1) * gapPx

                    val isDragged = dragTargetId == item.id
                    val offsetYExtra = if (isDragged) dragOffsetY else 0f
                    val offsetXExtra = if (isDragged) dragOffsetX else 0f

                    val itemModifier = Modifier
                        .offset { IntOffset(xPx + offsetXExtra.roundToInt(), yPx + offsetYExtra.roundToInt()) }
                        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })

                    WidgetContainerGrid(
                        key = item.id,
                        isEditMode = isEditMode,
                        viewModel = viewModel,
                        activePlugins = activePlugins,
                        modifier = itemModifier,
                        onDragStart = {
                            dragTargetId = item.id
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDrag = { dx, dy ->
                            dragOffsetX += dx
                            dragOffsetY += dy
                            val threshold = cellWidth * 0.35f
                            val rowThreshold = cellHeight * 0.35f
                            val items = viewModel.gridItems.value
                            val currentItem = items.find { it.id == item.id }
                            if (currentItem != null) {
                                val colDelta = (dragOffsetX / threshold).roundToInt()
                                val rowDelta = (dragOffsetY / rowThreshold).roundToInt()
                                if (colDelta != 0 || rowDelta != 0) {
                                    val newCol = (currentItem.col + colDelta).coerceIn(0, columns - currentItem.colSpan)
                                    val newRow = (currentItem.row + rowDelta).coerceIn(0, 10)
                                    viewModel.moveGridItem(item.id, newCol, newRow)
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                }
                            }
                        },
                        onDragEnd = {
                            dragTargetId = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PatternSelectorBar(
    current: GridPattern,
    onSelect: (GridPattern) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.ViewModule, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Layout:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            AssistChip(
                onClick = { expanded = true },
                label = { Text(current.label, fontSize = 12.sp) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GridPattern.entries.forEach { pattern ->
                    DropdownMenuItem(
                        text = { Text(pattern.label) },
                        onClick = { onSelect(pattern); expanded = false }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("Long-press drag handle to move", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun WidgetContainerGrid(
    key: String,
    isEditMode: Boolean,
    viewModel: DashboardViewModel,
    activePlugins: Map<String, Map<String, Any>>,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
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

    val editBorderMod = if (isEditMode) {
        Modifier
            .drawBehind {
                val stroke = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
                drawRoundRect(color = Color.Gray, style = stroke)
            }
    } else Modifier

    Box(
        modifier = modifier.then(editBorderMod)
    ) {
        Column {
            // Edit mode header bar
            if (isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                        .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to move",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = key.replace("_", " "),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Widget content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (isEditMode && rotation != 0f) rotation else 0f
                    }
            ) {
                when (key) {
                    "minecraft" -> MinecraftWidgetCard(viewModel, isEditMode)
                    "network" -> NetworkWidgetCard(viewModel, isEditMode)
                    "media_server" -> MediaServerWidgetCard(viewModel, isEditMode)
                    else -> {
                        val uiLayout = activePlugins[key]
                        if (uiLayout != null) {
                            PluginUiRenderer.Render(uiLayout)
                        } else {
                            Card(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Executing $key...", fontSize = 10.sp)
                                    }
                                }
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
    val fs by viewModel.minecraftFontScale.collectAsState()

    Card(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (mcStatus?.isOnline == true) Color(0xFF1B3D1B) else Color(0xFF4E1F1F))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Minecraft", fontSize = (15 * fs).sp, fontWeight = FontWeight.Bold)
                if (mcIsLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                else {
                    val c = if (mcStatus?.isOnline == true) Color(0xFF81C784) else Color(0xFFEF5350)
                    Text(if (mcStatus?.isOnline == true) "Online" else "Offline", color = c, fontWeight = FontWeight.Bold, fontSize = (12 * fs).sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(0.8f to "S", 1.0f to "M", 1.3f to "L").forEach { (scale, label) ->
                        Button(onClick = { viewModel.minecraftFontScale.value = scale; viewModel.saveWidgetConfigurations() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (fs == scale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 11.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = mcIp, onValueChange = { viewModel.mcIp.value = it; viewModel.saveWidgetConfigurations() }, label = { Text("IP") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                OutlinedTextField(value = mcPort, onValueChange = { viewModel.mcPort.value = it; viewModel.saveWidgetConfigurations() }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bedrock", fontSize = (11 * fs).sp)
                    Switch(checked = mcIsBedrock, onCheckedChange = { viewModel.mcIsBedrock.value = it; viewModel.saveWidgetConfigurations(); viewModel.refreshMinecraft() })
                }
            } else if (mcStatus?.isOnline == true) {
                Text("${mcStatus?.onlinePlayers}/${mcStatus?.maxPlayers}", fontSize = (18 * fs).sp, fontWeight = FontWeight.Bold)
                Text("MOTD: ${mcStatus?.motd}", fontSize = (10 * fs).sp, color = Color.Gray, maxLines = 2)
            } else {
                Text("Server is unreachable: ${mcStatus?.error ?: "Cannot resolve connection parameters"}", fontSize = (11 * fs).sp, color = Color(0xFFEF5350))
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
    val fs by viewModel.networkFontScale.collectAsState()

    Card(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Network", fontSize = (15 * fs).sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(0.8f to "S", 1.0f to "M", 1.3f to "L").forEach { (scale, label) ->
                        Button(onClick = { viewModel.networkFontScale.value = scale; viewModel.saveWidgetConfigurations() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (fs == scale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 11.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = speedTestUrl, onValueChange = { viewModel.speedTestServerUrl.value = it; viewModel.saveWidgetConfigurations() }, label = { Text("Speed Test URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            } else {
                Text("$connectionState | $networkGateway", fontSize = (11 * fs).sp)
                Spacer(modifier = Modifier.height(6.dp))
                if (speedTestState.state != "IDLE" && speedTestState.state != "COMPLETE" && speedTestState.state != "FAILED") {
                    LinearProgressIndicator(progress = speedTestState.progress, modifier = Modifier.fillMaxWidth().height(4.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Down: ${formatBps(speedTestState.downloadSpeed)}  Up: ${formatBps(speedTestState.uploadSpeed)}", fontSize = (10 * fs).sp)
                } else {
                    Button(onClick = { viewModel.runSpeedTest() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) { Text("Run", fontSize = (11 * fs).sp) }
                }
                if (speedTestHistory.isNotEmpty()) {
                    speedTestHistory.take(2).forEach { log ->
                        Text(log.second, fontSize = (9 * fs).sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    val fs by viewModel.mediaFontScale.collectAsState()

    Card(
        modifier = Modifier.fillMaxSize().padding(4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (mediaStatus?.isOnline == true) Color(0xFF1B3D1B) else Color(0xFF4E1F1F))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Media: $mediaType", fontSize = (15 * fs).sp, fontWeight = FontWeight.Bold)
                if (mediaIsLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Size", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(0.8f to "S", 1.0f to "M", 1.3f to "L").forEach { (scale, label) ->
                        Button(onClick = { viewModel.mediaFontScale.value = scale; viewModel.saveWidgetConfigurations() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (fs == scale) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) { Text(label, fontSize = 11.sp) }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    listOf("Jellyfin", "Plex", "Emby").forEach { type ->
                        Button(onClick = { viewModel.mediaServerType.value = type; viewModel.saveWidgetConfigurations(); viewModel.refreshMediaServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (mediaType == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(2.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text(type, fontSize = 9.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(value = mediaHost, onValueChange = { viewModel.mediaServerHost.value = it; viewModel.saveWidgetConfigurations() }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
                OutlinedTextField(value = mediaToken, onValueChange = { viewModel.mediaServerToken.value = it; viewModel.saveWidgetConfigurations() }, label = { Text("Token") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 12.sp))
            } else if (mediaStatus?.isOnline == true) {
                Text(mediaStatus?.statusString ?: "Healthy", fontSize = (11 * fs).sp)
                Text("Streams: ${mediaStatus?.activeStreamsCount}", fontSize = (13 * fs).sp, fontWeight = FontWeight.Bold)
                val used = mediaStatus?.storageUsedGb ?: 0.0
                val total = mediaStatus?.storageTotalGb ?: 0.0
                val percent = mediaStatus?.storagePercent ?: 0.0
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(progress = (percent / 100.0).toFloat().coerceIn(0.0f, 1.0f), modifier = Modifier.fillMaxWidth().height(6.dp))
                Text("${used.toInt()} / ${total.toInt()} GB", fontSize = (9 * fs).sp, color = Color.Gray)
            } else {
                Text("Offline: ${mediaStatus?.error ?: "Connection failed"}", fontSize = (11 * fs).sp, color = Color(0xFFEF5350))
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
