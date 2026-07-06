package eu.dotshell.massilia.generic.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import eu.dotshell.massilia.generic.ui.theme.isAppInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.massilia.platform.DrawableProvider
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    versionName: String,
    onBackClick: () -> Unit,
    onItineraryClick: () -> Unit,
    onLegalClick: () -> Unit,
    onCreditsClick: () -> Unit,
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOfflineClick: () -> Unit = {},
    onApiHealthClick: () -> Unit = {},
    onTelemetryClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    isAboutMenu: Boolean = false
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var isEasterEggActive by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val drawableProvider = DrawableProvider(LocalPlatformContext.current)
    val strings = StringProvider(LocalPlatformContext.current)


    // Reset click count after 2 seconds
    LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            delay(2000)
            // The tap handler already resets to 0 on the 3rd tap, so by here clickCount is 1 or 2.
            clickCount = 0
        }
    }

    // Auto-disable easter egg after 10 seconds
    LaunchedEffect(isEasterEggActive) {
        if (isEasterEggActive) {
            delay(10000)
            isEasterEggActive = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 40.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            val rotation by animateFloatAsState(
                targetValue = if (isEasterEggActive) 3600f else 0f,
                animationSpec = tween(10000),
                label = "logo_rotation"
            )

            // In light mode the logo is shown in negative so it reads on the light background.
            val invertColorFilter = remember {
                ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            -1f, 0f, 0f, 0f, 255f,
                            0f, -1f, 0f, 0f, 255f,
                            0f, 0f, -1f, 0f, 255f,
                            0f, 0f, 0f, 1f, 0f,
                        )
                    )
                )
            }
            val logoColorFilter = if (isAppInDarkTheme()) null else invertColorFilter

            Image(
                painter = drawableProvider.getPainter("ic_launcher_foreground"),
                contentDescription = strings["logo_massilia"],
                colorFilter = logoColorFilter,
                modifier = Modifier
                    .size(200.dp)
                    .padding(bottom = 48.dp)
                    .rotate(rotation)
                    .clickable {
                        clickCount++
                        if (clickCount >= 3) {
                            clickCount = 0
                            isEasterEggActive = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
            )

            if (isAboutMenu) {
                SettingsMenuRow(
                    title = strings["app_version_title"],
                    subtitle = versionName,
                    onClick = null,
                    showChevron = false
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["legal_title"],
                    onClick = onLegalClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["credits_title"],
                    onClick = onCreditsClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["contact_title"],
                    onClick = onContactClick
                )
            } else {
                SettingsMenuRow(
                    title = strings["itinerary"],
                    onClick = onItineraryClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["offline_mode"],
                    onClick = onOfflineClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["theme_settings_title"],
                    onClick = onThemeClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["privacy_title"],
                    onClick = onTelemetryClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsMenuRow(
                    title = strings["about_title"],
                    onClick = onAboutClick
                )

            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings["back"],
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)?,
    showChevron: Boolean = true
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedBackgroundColor by animateColorAsState(
        targetValue = if (onClick != null && isPressed) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
        animationSpec = tween(durationMillis = 120),
        label = "settings_menu_press"
    )

    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = pressedBackgroundColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (showChevron && onClick != null) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = strings["next_arrow"],
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
