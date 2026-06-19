package dev.sudoloser.ecodash.plugin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PluginUiRenderer {

    @Composable
    fun Render(layout: Map<String, Any>?) {
        if (layout == null) {
            Text("No layout data available", color = MaterialTheme.colorScheme.error)
            return
        }
        RenderElement(layout)
    }

    @Composable
    private fun RenderElement(element: Map<String, Any>) {
        val type = element["type"] as? String ?: return
        
        when (type) {
            "Card" -> {
                val bgColorStr = element["backgroundColor"] as? String ?: "#FFFFFF"
                val bgColor = parseColor(bgColorStr)
                val children = element["children"] as? List<Map<String, Any>> ?: emptyList()
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        children.forEach { child ->
                            RenderElement(child)
                        }
                    }
                }
            }
            "Text" -> {
                val text = element["text"] as? String ?: ""
                val style = element["style"] as? String ?: "Body"
                val colorStr = element["color"] as? String
                val textColor = colorStr?.let { parseColor(it) } ?: MaterialTheme.colorScheme.onSurface

                val (fontSize, fontWeight) = when (style) {
                    "Title" -> 20.sp to FontWeight.Bold
                    "Subtitle" -> 16.sp to FontWeight.SemiBold
                    "Body" -> 14.sp to FontWeight.Normal
                    else -> 14.sp to FontWeight.Normal
                }

                Text(
                    text = text,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    color = textColor,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            "Row" -> {
                val children = element["children"] as? List<Map<String, Any>> ?: emptyList()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    children.forEach { child ->
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            RenderElement(child)
                        }
                    }
                }
            }
            "Column" -> {
                val children = element["children"] as? List<Map<String, Any>> ?: emptyList()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    children.forEach { child ->
                        RenderElement(child)
                    }
                }
            }
            "Spacer" -> {
                Spacer(modifier = Modifier.width(8.dp).height(8.dp))
            }
            "StorageBar" -> {
                val used = (element["used"] as? Number)?.toDouble() ?: 0.0
                val total = (element["total"] as? Number)?.toDouble() ?: 0.0
                val percent = (element["percent"] as? Number)?.toDouble() ?: 0.0
                
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Storage Space", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(String.format("%.1f GB / %.1f GB (%.1f%%)", used, total, percent), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = (percent / 100.0).toFloat().coerceIn(0.0f, 1.0f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }

    private fun parseColor(colorStr: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorStr))
        } catch (e: Exception) {
            Color.White
        }
    }
}
