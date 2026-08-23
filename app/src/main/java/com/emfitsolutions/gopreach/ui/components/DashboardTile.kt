package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * One action button in the Main Form (Coordinator Elder/Regular Elder/
 * Publisher dashboards) — per explicit request, a plain text button: no
 * icon, and no per-item accent/color coding (a single neutral outlined
 * style for every function alike, not the earlier purple icon-badge grid).
 * [icon] is accepted but intentionally unused, kept only so every existing
 * call site (there are several, across AdminHomeScreen/PublisherHomeScreen)
 * didn't need to change just because the visual design did.
 */
@Composable
fun DashboardTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text = label, modifier = Modifier.padding(vertical = 4.dp))
    }
}
