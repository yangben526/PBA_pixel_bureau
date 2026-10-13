package app.pixelbureau.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF161410)
val Panel = Color(0xFF2A241C)
val PanelRaised = Color(0xFF3A3228)
val Cream = Color(0xFFF3E6C8)
val CreamDim = Color(0xFFC8B89A)
val Amber = Color(0xFFE2A23A)
val Moss = Color(0xFF8FBF78)
val Brick = Color(0xFFD4654A)
val FloorVoid = Color(0xFF1A2428)

/** Pixel chat panel — charcoal field inside a dark-brown frame. */
val ChatPanel = Color(0xFF2C2824)
val ChatPanelDeep = Color(0xFF1E1B18)
val ChatBorderOuter = Color(0xFF4A3A28)
val ChatBorderInner = Color(0xFF1A1510)
val ChatBubbleAgent = Color(0xFF3A3836)
val ChatBubbleAgentBorder = Color(0xFF6A6660)
val ChatBubbleUser = Color(0xFFE8B84A)
val ChatInputWell = Color(0xFF1A1816)
val ChatBtnGray = Color(0xFF5A5650)
val ChatBtnGrayHi = Color(0xFF7A746C)
val ChatBtnGrayLo = Color(0xFF3A3632)
val ChatBtnAmber = Color(0xFFE2A23A)
val ChatBtnAmberHi = Color(0xFFF0C060)
val ChatBtnAmberLo = Color(0xFFB87A20)
val ChipTile = Color(0xFF3A342C)
val ChipTileSelected = Color(0xFF2A241C)
val AmberGlow = Color(0x66E2A23A)

// ── Light Kairosoft roster tokens ──────────────────────────────────────────
/** Soft parchment page wash behind Company / Roster. */
val KaiPage = Color(0xFFF7EEDC)
/** Warm cream card face. */
val KaiCard = Color(0xFFFFF8EC)
/** Slightly deeper card nest (agent / task tiles). */
val KaiCardNest = Color(0xFFFFF1DC)
val KaiInk = Color(0xFF3A2E22)
val KaiInkMuted = Color(0xFF7A6A56)
val KaiBorder = Color(0xFFC9B090)
val KaiBorderDeep = Color(0xFF8B7355)
val KaiMint = Color(0xFFD8F0DA)
val KaiMintDeep = Color(0xFF8FBF78)
val KaiPeach = Color(0xFFFFE2CC)
val KaiSky = Color(0xFFD6E8F5)
val KaiButter = Color(0xFFFFF0C2)
val KaiRose = Color(0xFFF8D8D4)
val KaiAccent = Color(0xFFE2A23A)
val KaiAccentHi = Color(0xFFF0C060)
val KaiAccentLo = Color(0xFFB87A20)

private val Scheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Moss,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Panel,
    onSurface = Cream,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = CreamDim,
    error = Brick,
    onError = Ink,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelBureauTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        // No Material grey ripple / state-layer scrub anywhere in the app.
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            content()
        }
    }
}
