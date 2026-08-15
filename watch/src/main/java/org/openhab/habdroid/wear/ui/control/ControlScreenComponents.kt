package org.openhab.habdroid.wear.ui.control

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/**
 * Shared styling constants and composables for control activities
 * (Toggle, Rotary, ColorPicker, RollerShutter, ChoicePicker).
 *
 * Ensures consistent look when launched from complication tap or tile tap.
 */
object ControlStyle {
    val LogoSize = 32.dp
    val LogoTopOffset = 12.dp
    val LabelFontSize = 14.sp
    val ValueFontSize = 32.sp

    /** Default theme color (amber) used before the actual theme loads from ThemeStore. */
    const val DEFAULT_THEME_COLOR = 0xFFFFB300L

    /** Dark gray used for arc/progress track backgrounds. */
    const val ARC_TRACK_COLOR = 0xFF333333L
}

/**
 * App logo positioned at the top center of the screen.
 * Used as a branding element across all control activities.
 */
@Composable
fun ControlLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logoPainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/app_logo_gray.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    )
    Image(
        painter = logoPainter,
        contentDescription = null,
        modifier = modifier.size(ControlStyle.LogoSize)
    )
}

/**
 * Item label text with consistent styling across control activities.
 */
@Composable
fun ControlLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = ControlStyle.LabelFontSize,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Item value text with consistent styling across control activities.
 * @param highlighted whether the value should use the highlight color
 * @param highlightColor color to use when highlighted (defaults to primary)
 */
@Composable
fun ControlValue(
    text: String,
    highlighted: Boolean = false,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = ControlStyle.ValueFontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (highlighted) highlightColor else Color.White,
        modifier = modifier
    )
}


/** Built-in display labels for common raw state values. */
private val DISPLAY_LABELS = mapOf(
    "ON" to "On",
    "OFF" to "Off",
    "OPEN" to "Open",
    "CLOSED" to "Closed"
)

/**
 * Convert a raw state value to a human-friendly display string.
 * Checks stateDescription options, commandDescription options, then built-in mappings.
 */
fun formatStateDisplay(state: String): String {
    return DISPLAY_LABELS[state] ?: state
}
