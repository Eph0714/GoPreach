package com.emfitsolutions.gopreach.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.emfitsolutions.gopreach.ui.theme.AccentGold
import com.emfitsolutions.gopreach.ui.theme.PrimaryBlue
import com.emfitsolutions.gopreach.ui.theme.PrimaryBlueDark

/**
 * The gradient banner shown on entry/dashboard screens (login, home).
 *
 * The app logo shown here (or a fallback wordmark when no custom logo has been
 * uploaded) is customizable at runtime by the Super-Admin via the Control Panel
 * (Phase 3) — this composable takes a [logoContent] slot so that swap-in is a
 * drop-in replacement, no layout change needed.
 */
@Composable
fun AppBanner(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    logoContent: (@Composable () -> Unit)? = null,
    /** Optional corner action — e.g. a settings gear on a dashboard banner.
     * Left off the login banner, which has nothing to act on yet. */
    topEndAction: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(PrimaryBlueDark, PrimaryBlue),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            logoContent?.invoke()
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AccentGold,
                )
            }
        }
        if (topEndAction != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                topEndAction()
            }
        }
    }
}
