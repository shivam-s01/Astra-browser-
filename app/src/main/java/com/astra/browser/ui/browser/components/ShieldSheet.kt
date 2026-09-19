package com.astra.browser.ui.browser.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astra.browser.privacy.blocker.ContentBlocker
import com.astra.browser.theme.LocalAstraColors

/**
 * Real Shield popup (opened from the shield icon in the address bar).
 *
 * Everything here is live and functional:
 *  - big "blocked" number + Ads / Trackers / Allowed breakdown for THIS page
 *  - master Ad-blocker switch (global)
 *  - "Shield for this site" switch (per site, persisted, reloads the page)
 *  - Tracker protection + Popup blocking switches (global)
 *  - lifetime total of everything Astra has blocked
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSheet(
    host: String?,
    isHome: Boolean,
    stats: ContentBlocker.TabStats,
    lifetimeBlocked: Int,
    adBlockingOn: Boolean,
    trackingOn: Boolean,
    popupsOn: Boolean,
    siteShieldOn: Boolean,
    onAdBlocking: (Boolean) -> Unit,
    onTracking: (Boolean) -> Unit,
    onPopups: (Boolean) -> Unit,
    onSiteShield: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalAstraColors.current
    val protecting = (adBlockingOn || trackingOn) && siteShieldOn
    val accent by animateColorAsState(
        if (protecting) colors.accent else colors.onSurface.copy(alpha = 0.35f),
        tween(250), label = "shieldAccent"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.onSurface.copy(alpha = 0.25f)) }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // ─── Header: status + big blocked number ───────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (protecting) "Shield is ON" else "Shield is OFF",
                        fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
                    )
                    Text(
                        when {
                            isHome -> "Protecting every site you open"
                            host != null -> host
                            else -> "This page"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Big number card
            Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${stats.blocked}",
                        fontSize = 54.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (protecting) colors.accent else colors.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        if (isHome) "blocked on this page (open a site to start)" else "ads & trackers blocked on this page",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatCell("Ads", stats.adsBlocked, Icons.Filled.Block, colors.accent)
                        StatCell("Trackers", stats.trackersBlocked, Icons.Filled.Visibility, colors.accent)
                        StatCell("Allowed", stats.allowed, Icons.Filled.Language, colors.onSurface.copy(alpha = 0.6f))
                    }
                    if (stats.total > 0) {
                        Spacer(Modifier.height(14.dp))
                        val frac by animateFloatAsState(
                            stats.blocked.toFloat() / stats.total.toFloat(), tween(400), label = "blockedFrac"
                        )
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = colors.accent,
                            trackColor = colors.onSurface.copy(alpha = 0.12f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${(frac * 100).toInt()}% of ${stats.total} requests blocked",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ─── Controls ──────────────────────────────────────────────────
            SectionLabel("Controls")

            // The main one: complete ad-blocker control
            ControlRow(
                icon = Icons.Filled.Block,
                title = "Ad blocker",
                subtitle = if (adBlockingOn) "Blocking ads on all sites" else "Ads are allowed everywhere",
                checked = adBlockingOn,
                onChange = onAdBlocking,
                prominent = true
            )
            if (!isHome && host != null) {
                ControlRow(
                    icon = Icons.Filled.Shield,
                    title = "Shield for this site",
                    subtitle = if (siteShieldOn) "Protecting $host" else "Turned off for $host",
                    checked = siteShieldOn,
                    onChange = onSiteShield
                )
            }
            ControlRow(
                icon = Icons.Filled.Visibility,
                title = "Tracker protection",
                subtitle = "Analytics, pixels & beacons",
                checked = trackingOn,
                onChange = onTracking
            )
            ControlRow(
                icon = Icons.Filled.PhotoSizeSelectLarge,
                title = "Block pop-ups",
                subtitle = "Stops pop-ups & pop-unders opening",
                checked = popupsOn,
                onChange = onPopups
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = colors.border)
            Spacer(Modifier.height(14.dp))

            // ─── Lifetime ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total blocked by Astra", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text(
                        "Since you started using it",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.55f)
                    )
                }
                Text(
                    formatCount(lifetimeBlocked),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.accent
                )
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onOpenPrivacy, modifier = Modifier.align(Alignment.End)) {
                Text("Privacy dashboard", color = colors.accent)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalAstraColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = colors.accent,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun StatCell(label: String, value: Int, icon: ImageVector, tint: Color) {
    val colors = LocalAstraColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 72.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text("$value", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun ControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    prominent: Boolean = false
) {
    val colors = LocalAstraColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (prominent) colors.accent.copy(alpha = 0.10f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (prominent) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (checked) colors.accent else colors.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (prominent) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal,
                    color = colors.onSurface
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurface.copy(alpha = 0.6f))
            }
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.accent)
            )
        }
    }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 10_000 -> String.format("%.1fk", n / 1000f)
    else -> n.toString()
}
