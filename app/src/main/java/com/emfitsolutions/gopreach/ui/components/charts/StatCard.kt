package com.emfitsolutions.gopreach.ui.components.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A single metric card in a 2-column grid — a plain white card with a
 * circular icon badge, a label, and a bold value underneath, matching the
 * "Accounts" card layout from the reference design (icon top-left in a
 * light circle, label, then the number) rather than the earlier compact
 * KPI-strip look. [color] tints the icon badge only — the card itself stays
 * a neutral surface, keeping the "simple, organized" reference's flat white-
 * card-on-light-background feel instead of a rainbow of card backgrounds.
 */
@Composable
fun StatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * The "Income vs Expense"-style comparison card from the reference: two big
 * numbers side by side with their own accent color, plus a horizontal
 * proportion bar underneath showing the split between them at a glance.
 */
@Composable
fun ComparisonCard(
    leftLabel: String,
    leftValue: String,
    leftFraction: Float,
    leftColor: Color,
    rightLabel: String,
    rightValue: String,
    rightColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(leftLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(leftValue, style = MaterialTheme.typography.headlineMedium, color = leftColor)
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(rightLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rightValue, style = MaterialTheme.typography.headlineMedium, color = rightColor)
                }
            }
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .size(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(rightColor.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(leftFraction.coerceIn(0f, 1f))
                        .size(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(leftColor),
                )
            }
        }
    }
}
