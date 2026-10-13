package app.pixelbureau.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Content height of the compact docked bar. */
private val CompactBarHeight = 52.dp
private val CompactIconSize = 18.dp
private val ActiveChipShape = RoundedCornerShape(14.dp)

/** Near-black charcoal for the full-bleed bottom bar (matches themes.xml nav bar). */
private val PillCharcoal = Color(0xFF1C1A17)
private val PillDivider = Color(0xFF6A6358)

/** Brighter inactive label than CreamDim — readable on charcoal, not near-black. */
private val TabLabelIdle = Color(0xFFF3E6C8)
private val TabLabelActive = Color(0xFFE2A23A)

@Composable
fun BureauApp(model: BureauViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openBureau by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Do not use WindowInsets.isImeVisible. On targetSdk 35 it stays true on some
    // tablets even with the keyboard closed, and this was the only tab switcher.
    // Hide only when the IME actually extends past the nav / task bar.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val keyboardOpen = imeBottom > navBottom
    Scaffold(
        containerColor = Ink,
        // Status bar + side cutout / side nav. Bottom inset is owned by the bar
        // so a wide screen does not drop primary nav. Union takes the max per side.
        contentWindowInsets = WindowInsets.statusBars
            .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .union(
                WindowInsets.displayCutout.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
        bottomBar = {
            // Hide the bar while the IME is up so chat composer can rise cleanly
            // without a stacked bottom-bar + imePadding black gap.
            if (!keyboardOpen) {
                CompactBottomBar(
                    selected = tab,
                    onSelect = { tab = it },
                )
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
        when (tab) {
            0 -> OfficeScreen(model, contentModifier, onOpenBureau = { openBureau = true; tab = 1 })
            1 -> RosterScreen(model, contentModifier, onOpenOffice = { tab = 0 }, startOnBureau = openBureau.also { openBureau = false })
            else -> SettingsScreen(model, contentModifier)
        }
    }
}

/**
 * Bottom inset for the docked bar, capped so a bogus full-window inset (seen on
 * some tablets before edge-to-edge) cannot shove the tabs off the top of the screen.
 * Taskbar / 3-button nav on large screens is well under this.
 */
private val MaxBottomBarInset = 128.dp

@Composable
private fun CompactBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val bars = WindowInsets.navigationBars.union(WindowInsets.displayCutout)
    val capPx = with(density) { MaxBottomBarInset.roundToPx() }
    fun side(px: Int) = with(density) { px.coerceIn(0, capPx).toDp() }
    val startInset = side(bars.getLeft(density, layoutDirection))
    val endInset = side(bars.getRight(density, layoutDirection))
    val bottomInset = side(bars.getBottom(density))

    // Flat full-bleed charcoal — no rounded-top shape. Rounded DockedPillShape left
    // transparent corner wedges where Scaffold Ink (#161410) showed through next to
    // the Office / Roster / Settings labels. RectangleShape + explicit charcoal
    // fill (and matching window nav bar) means nothing black can peek around tabs.
    // Insets pad OUTSIDE the tab row. A fixed height outside the padding collapses
    // to 0 when the tablet taskbar inset is taller than the bar, which is how the
    // Office / Roster / Settings row vanished on large screens.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PillCharcoal,
        contentColor = TabLabelIdle,
        shape = RectangleShape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PillCharcoal)
                .padding(start = startInset, end = endInset, bottom = bottomInset)
                .heightIn(min = CompactBarHeight)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                "Office",
                Icons.Filled.Home,
                selected = selected == 0,
                modifier = Modifier.weight(1f),
            ) { onSelect(0) }
            NavDivider()
            NavItem(
                "Company",
                Icons.Filled.Person,
                selected = selected == 1,
                modifier = Modifier.weight(1f),
            ) { onSelect(1) }
            NavDivider()
            NavItem(
                "Settings",
                Icons.Filled.Settings,
                selected = selected == 2,
                modifier = Modifier.weight(1f),
            ) { onSelect(2) }
        }
    }
}

@Composable
private fun NavDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(22.dp)
            .background(PillDivider.copy(alpha = 0.85f)),
    )
}

@Composable
private fun NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // Explicit cream / amber — never rely on LocalContentColor (Surface contentColorFor
    // returns Unspecified for custom PillCharcoal and can fall back toward dark ink).
    val contentColor = if (selected) TabLabelActive else TabLabelIdle
    val chipModifier = if (selected) {
        Modifier
            .background(TabLabelActive.copy(alpha = 0.14f), ActiveChipShape)
            .border(1.25.dp, TabLabelActive.copy(alpha = 0.90f), ActiveChipShape)
    } else {
        // Transparent over charcoal — do NOT paint Ink behind idle tabs.
        Modifier.background(Color.Transparent, ActiveChipShape)
    }
    Column(
        modifier = modifier
            .then(chipModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(CompactIconSize),
            tint = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Short amber underline under the active label (reserve space so layout stays stable).
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .background(
                    if (selected) TabLabelActive else Color.Transparent,
                    RoundedCornerShape(1.dp),
                ),
        )
    }
}
