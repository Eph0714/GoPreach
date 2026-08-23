package com.emfitsolutions.gopreach.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Reuses [BarSlice] — same "label + value + color" shape works for a
 * donut/pie slice too (spec §3's "pie/donut... charts"). */
@Composable
fun DonutChart(slices: List<BarSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(120.dp)) {
                var startAngle = -90f
                val strokeWidth = size.minDimension * 0.28f
                slices.forEach { slice ->
                    val sweep = 360f * (slice.value / total)
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth),
                    )
                    startAngle += sweep
                }
            }
            Text(total.toInt().toString(), style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(slice.color))
                    Text(
                        "${slice.label}: ${slice.value.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
