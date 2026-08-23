package com.emfitsolutions.gopreach.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

data class BarSlice(val label: String, val value: Float, val color: Color)

/**
 * Lightweight, dependency-free bar chart (spec §3's "bar, horizontal bar...
 * charts") built on plain Compose [Canvas] drawing — this project has no
 * charting library today, and pulling one in was judged out of scope for
 * this pass (see BUILD_PLAN.md); this covers the same visual requirement
 * (compare a value across congregations at a glance) without a new
 * dependency. [onBarTap] is the drill-down hook (spec §8) — pass null to
 * render a non-interactive chart.
 */
@Composable
fun SimpleBarChart(
    slices: List<BarSlice>,
    modifier: Modifier = Modifier,
    onBarTap: ((BarSlice) -> Unit)? = null,
) {
    val maxValue = (slices.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
    Column(modifier = modifier) {
        slices.forEach { slice ->
            val rowModifier = if (onBarTap != null) {
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onBarTap(slice) }
            } else {
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
            }
            Row(modifier = rowModifier) {
                Text(
                    slice.label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                )
                Canvas(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                    val barWidth = size.width * (slice.value / maxValue)
                    drawRoundRect(
                        color = slice.color,
                        topLeft = Offset.Zero,
                        size = Size(barWidth.coerceAtLeast(2f), size.height),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Fill,
                    )
                }
                Text(
                    slice.value.toInt().toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
