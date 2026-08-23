package com.emfitsolutions.gopreach.ui.components.charts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One KPI tile (spec §7's "KPI cards" row) — a label + a big number, nothing
 * fancier, so a horizontally-scrolling row of these reads at a glance. */
@Composable
fun KpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.width(140.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}
