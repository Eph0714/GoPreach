package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Small round "quick action" button — a colorless circular outline (no
 * container fill, spec request: "the button doesnt have color but the icon
 * has") with a tinted icon inside, and its label placed below the circle
 * rather than alongside it. Used by [com.emfitsolutions.gopreach.ui.screens
 * .home.PublisherHomeScreen]'s "My Bible Study" / "My Return Visit" /
 * "Preaching Hours" actions.
 */
@Composable
fun RoundIconActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedIconButton(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.5.dp, iconTint.copy(alpha = 0.4f)),
        ) {
            Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(26.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
