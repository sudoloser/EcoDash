package dev.sudoloser.ecodash.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
    val pluginsVersion by viewModel.pluginsVersion.collectAsState()

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

    var items by remember { mutableStateOf(allWidgetKeys) }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(allWidgetKeys) {
        if (draggedIndex == -1) {
            items = allWidgetKeys
        }
    }

    val onDragStart: (Int) -> Unit = { index ->
        draggedIndex = index
        items = allWidgetKeys
    }

    val onDrag: (Float) -> Unit = { deltaY ->
        dragOffsetY += deltaY
        val threshold = itemHeightPx * 0.4f
        val currentIdx = draggedIndex
        if (currentIdx == -1) return@Unit

        if (dragOffsetY > threshold && currentIdx < items.size - 1) {
            items = items.toMutableList().also { list ->
                val temp = list[currentIdx]
                list[currentIdx] = list[currentIdx + 1]
                list[currentIdx + 1] = temp
            }
            draggedIndex = currentIdx + 1
            dragOffsetY -= threshold
        } else if (dragOffsetY < -threshold && currentIdx > 0) {
            items = items.toMutableList().also { list ->
                val temp = list[currentIdx]
                list[currentIdx] = list[currentIdx - 1]
                list[currentIdx - 1] = temp
            }
            draggedIndex = currentIdx - 1
            dragOffsetY += threshold
        }
    }

    val onDragEnd: () -> Unit = {
        val newLayout = items.filter { it in widgetLayout }
        if (newLayout != widgetLayout) {
            viewModel.reorderWidgetFull(newLayout)
        }
        draggedIndex = -1
        dragOffsetY = 0f
        items = allWidgetKeys
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
        itemsIndexed(items, key = { _, key -> key }) { index, key ->
            WidgetContainer(
                key = key,
                index = index,
                totalCount = items.size,
                isEditMode = isEditMode,
                isDragged = index == draggedIndex,
                dragOffsetY = if (index == draggedIndex) dragOffsetY else 0f,
                viewModel = viewModel,
                activePlugins = activePlugins,
                onDragStart = { onDragStart(index) },
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onItemHeightKnown = { px -> if (itemHeightPx == 0f) itemHeightPx = px.toFloat() }
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
    isDragged: Boolean,
    dragOffsetY: Float,
    viewModel: DashboardViewModel,
    activePlugins: Map<String, Map<String, Any>>,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onItemHeightKnown: (Int) -> Unit
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

    val containerModifier = Modifier
        .fillMaxWidth()
        .then(
            if (isEditMode) {
                Modifier
                    .graphicsLayer(rotationZ = if (isDragged) 0f else rotation)
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
        .animateItem()
        .onSizeChanged { size -> onItemHeightKnown(size.height) }
        .then(
            if (isDragged) {
                Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationY = dragOffsetY
                        scaleX = 1.05f
                        scaleY = 1.05f
                        alpha = 0.9f
                    }
            } else {
                Modifier
            }
        )

    Box(modifier = containerModifier) {
        Column {
            if (isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.y)
                                    },
                                    onDragEnd = onDragEnd,
                                    onDragCancel = onDragEnd
                                )
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = key.replace("_", " "),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

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
