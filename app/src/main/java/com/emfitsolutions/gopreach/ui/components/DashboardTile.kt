package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.ui.theme.IconAccentGreen

/**
 * One action tile in the Main Form's icon grid — "Professional Green
 * Icon-Based Main Form Redesign" spec: a large, consistently-green circular
 * icon badge with a short label underneath, not a shrunk button with a tiny
 * icon inside it. Every dimension here (badge size, icon size, tap target,
 * spacing) is a deliberate, fixed system rather than "whatever fits" — see
 * the inline comments below for the reasoning behind each number, so a
 * future tweak changes the system on purpose instead of by accident.
 */
@Composable
fun DashboardTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // 96dp: wide enough for a two-line label under the 64dp badge without
        // wrapping awkwardly, narrow enough that 3 fit per row on a standard
        // ~360dp-wide phone with the grid's own spacing (spec §3/§9: fits
        // small phones through tablets without the tile itself changing shape
        // — DashboardSection's FlowRow is what reflows the *count* per row).
        modifier = modifier
            .width(96.dp)
            .clip(RoundedCornerShape(16.dp))
            // The clickable area is the *entire* tile (badge + label + this
            // padding), not just the visible icon circle (spec §7: "the
            // clickable area must be large enough... do not make the actual
            // clickable area too small") — comfortably exceeds Android's
            // 48dp minimum touch target in both dimensions once the label
            // and padding are included.
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            // 64dp badge, 28dp glyph: large enough to read as the visually
            // dominant element on the tile (spec §6: "icon should be visually
            // dominant") while the label stays legible text, not a caption —
            // the same badge size is used everywhere a DashboardTile appears,
            // so every function in the grid reads as one consistent system
            // (spec §8) rather than some icons looking more important than others.
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(IconAccentGreen.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IconAccentGreen,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
