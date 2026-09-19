package com.astra.browser.ui.newtab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.astra.browser.theme.LocalAstraColors
import com.astra.browser.ui.prefs.LocalUiPrefs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Brave-style new tab page: full-bleed wallpaper with a live clock, a real
 * search bar, a Shield stats card and the shortcut strip on top. The browser
 * chrome (URL bar on top, bottom navigation) is owned by BrowserScreen, so this
 * composable only renders the content area.
 */
@Composable
fun NewTabPage(
    onNavigate: (String) -> Unit,
    onOpenShield: () -> Unit = {},
    viewModel: NewTabViewModel = hiltViewModel()
) {
    val prefs = LocalUiPrefs.current
    val recentSites by viewModel.recentSites.collectAsState()
    val engineName by viewModel.searchEngineName.collectAsState()
    val lifetimeBlocked by viewModel.totalTrackersBlocked.collectAsState()
    val wallpaperMode by viewModel.wallpaperMode.collectAsState()
    val wallpaperDim by viewModel.wallpaperDim.collectAsState()
    val customWallpaper by viewModel.customWallpaper.collectAsState()

    val cardAlpha = prefs.cardOpacity
    val center = prefs.homeAlign == "CENTER"

    Box(modifier = Modifier.fillMaxSize()) {
        HomeWallpaper(
            mode = wallpaperMode,
            custom = customWallpaper,
            dim = wallpaperDim,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (center) Arrangement.Center else Arrangement.Top
        ) {
            if (prefs.showGreeting) {
                HomeGreeting(
                    name = prefs.userName,
                    modifier = Modifier.padding(top = if (center) 0.dp else 24.dp, bottom = 4.dp)
                )
            }

            if (prefs.showClock) {
                HomeClock(
                    is24h = prefs.clock24h,
                    sizeSp = prefs.clockSize,
                    showDate = prefs.showDate,
                    modifier = Modifier.padding(top = if (prefs.showGreeting || center) 4.dp else 28.dp)
                )
            } else if (!prefs.showGreeting) {
                Spacer(Modifier.height(20.dp))
            }

            if (prefs.showSearchBar) {
                HomeSearchBar(
                    engineName = engineName,
                    onSubmit = onNavigate,
                    cardAlpha = cardAlpha,
                    modifier = Modifier
                        .padding(top = if (prefs.showClock || prefs.showGreeting) 22.dp else 6.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (prefs.showShieldCard) {
                ShieldStatsCard(
                    lifetimeBlocked = lifetimeBlocked,
                    onClick = onOpenShield,
                    cardAlpha = cardAlpha,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                )
            }

            if (prefs.showShortcuts) {
                ShortcutsCard(
                    sites = recentSites,
                    onNavigate = onNavigate,
                    style = prefs.tileStyle,
                    count = prefs.tileCount,
                    showLabels = prefs.tileLabels,
                    cardAlpha = cardAlpha,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                )
            }
        }

        if (wallpaperMode == WallpaperModes.NIGHT_SKY) {
            Text(
                text = "Astra  ·  Milky Way over the ridge",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun HomeGreeting(name: String, modifier: Modifier = Modifier) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val base = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..20 -> "Good evening"
        else -> "Good night"
    }
    val text = if (name.isBlank()) base else "$base, ${name.trim()}"
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.95f),
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Live clock + date
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeClock(
    is24h: Boolean,
    sizeSp: Int,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    // Ticks ONCE A MINUTE (aligned to the minute boundary), not every second:
    // the display has no seconds, so a per-second tick was 59 useless
    // recompositions per minute. Cancelled automatically when the page is hidden.
    var now by remember { mutableStateOf(java.util.Date()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            now = java.util.Date()
            val ms = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000L - (ms % 60_000L) + 50L)
        }
    }
    val locale = java.util.Locale.getDefault()
    val timeText = java.text.SimpleDateFormat(if (is24h) "HH:mm" else "h:mm", locale).format(now)
    val ampm = if (is24h) "" else java.text.SimpleDateFormat("a", locale).format(now).lowercase()
    val dateText = java.text.SimpleDateFormat("EEEE, d MMMM", locale).format(now)

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                timeText,
                color = Color.White,
                fontSize = sizeSp.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp
            )
            if (ampm.isNotEmpty()) {
                Text(
                    ampm,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = (sizeSp * 0.31f).sp,
                    modifier = Modifier.padding(start = 6.dp, bottom = (sizeSp * 0.19f).dp)
                )
            }
        }
        if (showDate) {
            Text(dateText, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Real search bar (type + Go / Enter -> real search or URL)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSearchBar(
    engineName: String,
    onSubmit: (String) -> Unit,
    cardAlpha: Float,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun submit() {
        val q = text.trim()
        if (q.isNotEmpty()) {
            focusManager.clearFocus()
            onSubmit(q)
            text = ""
        }
    }

    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = Color.Black.copy(alpha = (cardAlpha + 0.06f).coerceIn(0f, 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    autoCorrectEnabled = false
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                "Search $engineName or type a URL",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            )
            if (text.isNotEmpty()) {
                IconButton(onClick = { text = "" }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { submit() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Go",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Shield stats card (tap -> opens the Shield popup)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ShieldStatsCard(
    lifetimeBlocked: Int,
    onClick: () -> Unit,
    cardAlpha: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = cardAlpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = Color(0xFF4FE3C1),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatBlocked(lifetimeBlocked),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ads & trackers blocked",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatBlocked(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 10_000 -> String.format("%.1fk", n / 1000f)
    else -> n.toString()
}

// ─────────────────────────────────────────────────────────────────────────
// Shortcuts row (Brave "Top Sites" strip)
// ─────────────────────────────────────────────────────────────────────────

private data class DefaultSite(val label: String, val url: String, val tint: Color)

private val defaultSites = listOf(
    DefaultSite("YouTube", "https://m.youtube.com", Color(0xFFFF3B30)),
    DefaultSite("Google", "https://www.google.com", Color(0xFF4285F4)),
    DefaultSite("Wikipedia", "https://en.m.wikipedia.org", Color(0xFF9AA0A6)),
    DefaultSite("GitHub", "https://github.com", Color(0xFF8B949E)),
    DefaultSite("Reddit", "https://www.reddit.com", Color(0xFFFF4500))
)

private data class ShortcutItem(val label: String, val url: String, val tint: Color)

@Composable
private fun ShortcutsCard(
    sites: List<String>,
    onNavigate: (String) -> Unit,
    style: String,
    count: Int,
    showLabels: Boolean,
    cardAlpha: Float,
    modifier: Modifier = Modifier
) {
    val items = remember(sites, count) {
        val fromHistory = sites.mapNotNull { url ->
            val host = runCatching { java.net.URI(url).host }.getOrNull()
                ?.removePrefix("www.")?.removePrefix("m.")
            if (host.isNullOrBlank()) null
            else ShortcutItem(host.substringBeforeLast('.').replaceFirstChar { it.uppercase() }, url, tintFor(host))
        }.distinctBy { it.label }

        val fillers = defaultSites
            .filter { d -> fromHistory.none { it.label.equals(d.label, ignoreCase = true) } }
            .map { ShortcutItem(it.label, it.url, it.tint) }

        (fromHistory + fillers).take(count)
    }

    Surface(
        modifier = modifier.padding(horizontal = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = cardAlpha)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items, key = { it.url }) { item ->
                ShortcutTile(item = item, style = style, showLabel = showLabels, onClick = { onNavigate(item.url) })
            }
        }
    }
}

private fun tintFor(host: String): Color {
    val palette = listOf(
        Color(0xFF2E7D6B), Color(0xFF3F51B5), Color(0xFF8E4A9E),
        Color(0xFFB5651D), Color(0xFF1E88A8), Color(0xFF6D7B3A)
    )
    return palette[(host.hashCode() and 0x7fffffff) % palette.size]
}

@Composable
private fun ShortcutTile(item: ShortcutItem, style: String, showLabel: Boolean, onClick: () -> Unit) {
    val outer = when (style) {
        "SQUARE" -> RoundedCornerShape(6.dp)
        "ROUNDED" -> RoundedCornerShape(18.dp)
        else -> CircleShape
    }
    val inner = when (style) {
        "SQUARE" -> RoundedCornerShape(4.dp)
        "ROUNDED" -> RoundedCornerShape(11.dp)
        else -> CircleShape
    }
    Column(
        modifier = Modifier
            .width(86.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = outer,
            color = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(shape = inner, color = item.tint, modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = item.label.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.label,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Night-sky wallpaper (pure Canvas, no bitmap assets)
// Deep blue-black sky, Milky Way arch with nebula colour, dense star field,
// a horizon glow behind a rocky ridge and stone stairway, fog bank on the left.
// ─────────────────────────────────────────────────────────────────────────

@Composable
internal fun NightSkyWallpaper(modifier: Modifier = Modifier) {
    val stars = remember { generateStars(900, seed = 7) }
    val dust = remember { generateStars(1400, seed = 21) }

    Canvas(modifier = modifier.background(Color(0xFF0A0F14))) {
        val w = size.width
        val h = size.height

        // Sky base
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF070B10),
                0.35f to Color(0xFF0D141B),
                0.62f to Color(0xFF1A2229),
                0.80f to Color(0xFF2B3138),
                1f to Color(0xFF15181C)
            )
        )

        drawMilkyWay(w, h)

        // Fine dust stars (tiny, dim)
        dust.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = s.a * 0.45f),
                radius = 0.5f + s.r * 0.35f,
                center = Offset(s.x * w, s.y * h * 0.68f)
            )
        }
        // Bright stars, denser inside the galactic band
        stars.forEach { s ->
            drawCircle(
                color = Color.White.copy(alpha = s.a),
                radius = 0.7f + s.r * 1.3f,
                center = Offset(s.x * w, s.y * h * 0.68f)
            )
        }
        // A few hero stars with soft glow
        listOf(
            Offset(0.73f, 0.325f), Offset(0.31f, 0.14f), Offset(0.09f, 0.50f), Offset(0.52f, 0.22f)
        ).forEach { p ->
            val c = Offset(p.x * w, p.y * h)
            drawCircle(Color.White.copy(alpha = 0.12f), radius = 9f, center = c)
            drawCircle(Color.White.copy(alpha = 0.95f), radius = 2.4f, center = c)
        }

        drawHorizonGlow(w, h)
        drawFogBank(w, h)
        drawRidge(w, h)
        drawStairway(w, h)
        drawFoliage(w, h)
        drawFenceRopes(w, h)
    }
}

private data class Star(val x: Float, val y: Float, val r: Float, val a: Float)

private fun generateStars(count: Int, seed: Int): List<Star> {
    val rnd = Random(seed)
    return List(count) {
        val x = rnd.nextFloat()
        // Bias density toward the arch band so it reads as the galactic core
        val bandY = 0.30f + 0.20f * sin(x * PI.toFloat()) * -1f + 0.20f
        val y = if (rnd.nextFloat() < 0.55f) {
            (bandY + (rnd.nextFloat() - 0.5f) * 0.18f).coerceIn(0f, 1f)
        } else rnd.nextFloat()
        Star(x, y, rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.75f)
    }
}

private fun DrawScope.drawMilkyWay(w: Float, h: Float) {
    // The galactic band: a diagonal arch from left-mid to right-top, built
    // from overlapping soft radial blobs along a curve.
    val blobs = 34
    for (i in 0..blobs) {
        val t = i / blobs.toFloat()
        val x = w * (-0.05f + 1.10f * t)
        val y = h * (0.30f - 0.10f * sin(t * PI.toFloat()) + 0.10f * (t - 0.5f))
        val warm = i % 3 != 0
        val core = if (warm) Color(0xFFB79A7C) else Color(0xFF7C8AA8)
        val radius = w * (0.30f + 0.05f * sin(t * 9f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(core.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(x, y),
                radius = radius
            ),
            radius = radius,
            center = Offset(x, y)
        )
    }
    // Bright dense core toward the upper right
    val coreC = Offset(w * 0.62f, h * 0.20f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFD9C3A5).copy(alpha = 0.28f), Color.Transparent),
            center = coreC, radius = w * 0.42f
        ),
        radius = w * 0.42f, center = coreC
    )
    // Pink/magenta emission nebula on the left (like the Cygnus region)
    val nebulaPts = listOf(
        Offset(0.12f, 0.27f) to 0.20f, Offset(0.20f, 0.24f) to 0.15f,
        Offset(0.06f, 0.32f) to 0.16f, Offset(0.30f, 0.22f) to 0.10f
    )
    nebulaPts.forEach { (p, r) ->
        val c = Offset(p.x * w, p.y * h)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFC2416B).copy(alpha = 0.34f), Color(0xFF7A2A5E).copy(alpha = 0.12f), Color.Transparent),
                center = c, radius = w * r
            ),
            radius = w * r, center = c
        )
    }
    // Dark dust lanes carving the band
    val lane = Path().apply {
        moveTo(w * 0.05f, h * 0.30f)
        cubicTo(w * 0.30f, h * 0.20f, w * 0.55f, h * 0.24f, w * 0.98f, h * 0.17f)
    }
    drawPath(lane, Color(0xFF05080C).copy(alpha = 0.55f), style = Stroke(width = h * 0.018f))
    val lane2 = Path().apply {
        moveTo(w * 0.15f, h * 0.36f)
        cubicTo(w * 0.40f, h * 0.29f, w * 0.65f, h * 0.31f, w * 1.00f, h * 0.24f)
    }
    drawPath(lane2, Color(0xFF05080C).copy(alpha = 0.4f), style = Stroke(width = h * 0.012f))
}

private fun DrawScope.drawHorizonGlow(w: Float, h: Float) {
    // Cool teal airglow near the horizon
    drawRect(
        brush = Brush.verticalGradient(
            0.42f to Color.Transparent,
            0.62f to Color(0xFF2E4A4F).copy(alpha = 0.32f),
            0.72f to Color(0xFF4B5B55).copy(alpha = 0.22f),
            0.80f to Color.Transparent
        )
    )
}

private fun DrawScope.drawFogBank(w: Float, h: Float) {
    // Low cloud/valley lights on the left, like the sea of clouds in the photo
    val c = Offset(w * 0.08f, h * 0.63f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFE6D9A4).copy(alpha = 0.55f), Color(0xFF9DB0B4).copy(alpha = 0.25f), Color.Transparent),
            center = c, radius = w * 0.42f
        ),
        radius = w * 0.42f, center = c
    )
    val c2 = Offset(w * 0.20f, h * 0.68f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFF1F3F5).copy(alpha = 0.38f), Color.Transparent),
            center = c2, radius = w * 0.34f
        ),
        radius = w * 0.34f, center = c2
    )
    // Tiny valley lights
    val rnd = Random(3)
    repeat(26) {
        val x = w * (0.02f + rnd.nextFloat() * 0.26f)
        val y = h * (0.615f + rnd.nextFloat() * 0.04f)
        drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.55f), radius = 1.4f, center = Offset(x, y))
    }
}

private fun DrawScope.drawRidge(w: Float, h: Float) {
    // Main rocky peak silhouette on the right-of-center, with a lit face
    val peak = Path().apply {
        moveTo(w * 0.30f, h * 0.66f)
        lineTo(w * 0.36f, h * 0.60f)
        lineTo(w * 0.42f, h * 0.55f)
        lineTo(w * 0.47f, h * 0.505f)
        lineTo(w * 0.52f, h * 0.455f)
        lineTo(w * 0.56f, h * 0.447f)
        lineTo(w * 0.60f, h * 0.465f)
        lineTo(w * 0.66f, h * 0.50f)
        lineTo(w * 0.72f, h * 0.535f)
        lineTo(w * 0.79f, h * 0.575f)
        lineTo(w * 0.86f, h * 0.60f)
        lineTo(w * 1.00f, h * 0.62f)
        lineTo(w * 1.00f, h * 1.00f)
        lineTo(w * 0.20f, h * 1.00f)
        close()
    }
    drawPath(
        peak,
        brush = Brush.horizontalGradient(
            0.30f to Color(0xFF3A3236),
            0.52f to Color(0xFF5A484C),
            0.78f to Color(0xFF4A3F44),
            1.00f to Color(0xFF2B2A2C)
        )
    )
    // Lit rock face highlight
    val face = Path().apply {
        moveTo(w * 0.60f, h * 0.465f)
        lineTo(w * 0.66f, h * 0.50f)
        lineTo(w * 0.72f, h * 0.535f)
        lineTo(w * 0.79f, h * 0.575f)
        lineTo(w * 0.78f, h * 0.66f)
        lineTo(w * 0.66f, h * 0.60f)
        close()
    }
    drawPath(face, Color(0xFF7A6167).copy(alpha = 0.38f))

    // Right grass slope
    val slope = Path().apply {
        moveTo(w * 0.80f, h * 0.575f)
        lineTo(w * 1.00f, h * 0.535f)
        lineTo(w * 1.00f, h * 0.90f)
        lineTo(w * 0.86f, h * 0.80f)
        close()
    }
    drawPath(slope, Color(0xFF3D4530).copy(alpha = 0.75f))

    // Left rock shoulder
    val left = Path().apply {
        moveTo(w * 0.00f, h * 0.68f)
        lineTo(w * 0.14f, h * 0.66f)
        lineTo(w * 0.28f, h * 0.62f)
        lineTo(w * 0.36f, h * 0.66f)
        lineTo(w * 0.38f, h * 1.00f)
        lineTo(w * 0.00f, h * 1.00f)
        close()
    }
    drawPath(left, Color(0xFF2A2B2E))
}

private fun DrawScope.drawStairway(w: Float, h: Float) {
    // Perspective stone stairway running from bottom-center up the ridge
    val topL = Offset(w * 0.525f, h * 0.505f)
    val topR = Offset(w * 0.545f, h * 0.505f)
    val botL = Offset(w * 0.20f, h * 1.00f)
    val botR = Offset(w * 0.80f, h * 1.00f)

    val path = Path().apply {
        moveTo(topL.x, topL.y)
        lineTo(topR.x, topR.y)
        lineTo(botR.x, botR.y)
        lineTo(botL.x, botL.y)
        close()
    }
    drawPath(
        path,
        brush = Brush.verticalGradient(
            0.505f to Color(0xFF6F6270),
            0.70f to Color(0xFF5B5461),
            1.00f to Color(0xFF423F46)
        )
    )

    // Steps: horizontal bands that compress with distance
    val rnd = Random(11)
    var t = 0f
    var step = 0.0075f
    while (t < 1f) {
        val y = topL.y + (botL.y - topL.y) * t
        val lx = topL.x + (botL.x - topL.x) * t
        val rx = topR.x + (botR.x - topR.x) * t
        drawLine(Color(0xFF2B272E).copy(alpha = 0.55f), Offset(lx, y), Offset(rx, y), strokeWidth = 1f + t * 2.2f)
        drawLine(Color(0xFF9A8FA0).copy(alpha = 0.18f), Offset(lx, y + 1.5f + t * 2f), Offset(rx, y + 1.5f + t * 2f), strokeWidth = 1f + t * 1.4f)
        t += step
        step *= 1.075f
    }
    // Cobblestone speckle on the foreground
    repeat(420) {
        val ty = 0.35f + rnd.nextFloat() * 0.65f
        val y = topL.y + (botL.y - topL.y) * ty
        val lx = topL.x + (botL.x - topL.x) * ty
        val rx = topR.x + (botR.x - topR.x) * ty
        val x = lx + (rx - lx) * rnd.nextFloat()
        val r = 1.5f + ty * 5.5f * rnd.nextFloat()
        val shade = if (rnd.nextBoolean()) Color(0xFF8B8092) else Color(0xFF302D34)
        drawCircle(shade.copy(alpha = 0.32f), radius = r, center = Offset(x, y))
    }
    // Soft vignette at the very bottom so the bottom bar blends in
    drawRect(
        brush = Brush.verticalGradient(
            0.88f to Color.Transparent,
            1.00f to Color(0xFF0B0D10).copy(alpha = 0.75f)
        )
    )
}

private fun DrawScope.drawFoliage(w: Float, h: Float) {
    val rnd = Random(5)
    val greens = listOf(Color(0xFF3F5233), Color(0xFF4C5E3A), Color(0xFF2E3A26), Color(0xFF6B5A3A))
    // Left bank shrubs
    repeat(70) {
        val x = w * (0.04f + rnd.nextFloat() * 0.30f)
        val y = h * (0.68f + rnd.nextFloat() * 0.26f)
        val r = 5f + rnd.nextFloat() * 20f
        drawCircle(greens[rnd.nextInt(greens.size)].copy(alpha = 0.72f), radius = r, center = Offset(x, y))
    }
    // Right bank shrubs
    repeat(60) {
        val x = w * (0.70f + rnd.nextFloat() * 0.30f)
        val y = h * (0.68f + rnd.nextFloat() * 0.26f)
        val r = 5f + rnd.nextFloat() * 20f
        drawCircle(greens[rnd.nextInt(greens.size)].copy(alpha = 0.68f), radius = r, center = Offset(x, y))
    }
}

private fun DrawScope.drawFenceRopes(w: Float, h: Float) {
    val rope = Color(0xFFB9A88B).copy(alpha = 0.75f)
    val post = Color(0xFF6A4B3D)

    // Right railing: thick posts angled with perspective, rope catenary between
    val rightPosts = listOf(
        Offset(w * 0.575f, h * 0.60f) to 4f,
        Offset(w * 0.60f, h * 0.68f) to 6f,
        Offset(w * 0.65f, h * 0.78f) to 9f,
        Offset(w * 0.77f, h * 0.90f) to 16f
    )
    for (i in 0 until rightPosts.size - 1) {
        val (a, _) = rightPosts[i]
        val (b, _) = rightPosts[i + 1]
        val sag = (b.y - a.y) * 0.10f
        val p = Path().apply {
            moveTo(a.x, a.y)
            quadraticTo((a.x + b.x) / 2f, (a.y + b.y) / 2f + sag, b.x, b.y)
        }
        drawPath(p, rope, style = Stroke(width = 1.6f + i * 0.9f))
    }
    rightPosts.forEach { (p, sw) ->
        drawLine(post, Offset(p.x, p.y - sw * 2.6f), Offset(p.x + sw * 0.15f, p.y + sw * 4.4f), strokeWidth = sw)
    }

    // Left railing
    val leftPosts = listOf(
        Offset(w * 0.49f, h * 0.60f) to 3.5f,
        Offset(w * 0.40f, h * 0.70f) to 6f,
        Offset(w * 0.27f, h * 0.80f) to 10f,
        Offset(w * 0.05f, h * 0.90f) to 15f
    )
    for (i in 0 until leftPosts.size - 1) {
        val (a, _) = leftPosts[i]
        val (b, _) = leftPosts[i + 1]
        val sag = (b.y - a.y) * 0.08f
        val p = Path().apply {
            moveTo(a.x, a.y)
            quadraticTo((a.x + b.x) / 2f, (a.y + b.y) / 2f + sag, b.x, b.y)
        }
        drawPath(p, rope, style = Stroke(width = 1.4f + i * 0.8f))
    }
    leftPosts.forEach { (p, sw) ->
        drawLine(post, Offset(p.x, p.y - sw * 2.4f), Offset(p.x - sw * 0.1f, p.y + sw * 4.2f), strokeWidth = sw)
    }
}
