package org.openhab.habdroid.wear.phone.ui.tiledesign.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Theme colors matching the watch tile accent themes.
 */
enum class TileThemeColor(val displayName: String, val color: Color) {
    AMBER("Amber", Color(0xFFFFB950)),
    BLUE("Blue", Color(0xFFA8C8FF)),
    GREEN("Green", Color(0xFF8AD88E)),
    PURPLE("Purple", Color(0xFFD4BBFF)),
    RED("Red", Color(0xFFFFB4AB));

    companion object {
        fun fromName(name: String): TileThemeColor =
            entries.find { it.name.equals(name, ignoreCase = true) } ?: AMBER
    }
}

/**
 * Row of colored circles for selecting the tile accent theme.
 */
@Composable
fun ThemeSelector(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tile Theme Preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TileThemeColor.entries.forEach { theme ->
                val isSelected = theme.name.equals(selectedTheme, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(theme.color)
                        .then(
                            if (isSelected) Modifier.border(
                                width = 3.dp,
                                color = MaterialTheme.colorScheme.onBackground,
                                shape = CircleShape
                            ) else Modifier
                        )
                        .clickable { onThemeSelected(theme.name) }
                )
            }
        }
    }
}
