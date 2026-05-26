package com.pelotcl.app.generic.ui.screens.settings

import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import android.os.Build
import com.pelotcl.app.R
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onSystemBack: () -> Unit,
    onItineraryClick: () -> Unit,
    onLegalClick: () -> Unit,
    onCreditsClick: () -> Unit,
    onContactClick: () -> Unit,
    modifier: Modifier = Modifier,
    onOfflineClick: () -> Unit = {},
    onApiHealthClick: () -> Unit = {},
    onTelemetryClick: () -> Unit = {}
) {
    var clickCount by remember { mutableIntStateOf(0) }
    var isEasterEggActive by remember { mutableStateOf(false) }
    var isAboutMenu by rememberSaveable { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    // Reset click count after 2 seconds
    LaunchedEffect(clickCount) {
        if (clickCount > 0) {
            delay(2000)
            if (clickCount < 3) clickCount = 0
        }
    }

    // Auto-disable easter egg after 10 seconds
    LaunchedEffect(isEasterEggActive) {
        if (isEasterEggActive) {
            delay(10000)
            isEasterEggActive = false
        }
    }

    BackHandler(enabled = isAboutMenu) {
        isAboutMenu = false
    }

    BackHandler(enabled = !isAboutMenu) {
        onSystemBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(100.dp))
            val rotation by animateFloatAsState(
                targetValue = if (isEasterEggActive) 3600f else 0f,
                animationSpec = tween(10000),
                label = "logo_rotation"
            )

            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo Pelo",
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
                    title = "Version de l'application",
                    subtitle = versionName,
                    onClick = null,
                    showChevron = false
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "Mentions légales / CGU",
                    onClick = onLegalClick
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "Crédits",
                    onClick = onCreditsClick
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "Nous contacter",
                    onClick = onContactClick
                )
            } else {
                SettingsMenuRow(
                    title = "Itinéraire",
                    onClick = onItineraryClick
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "Mode hors ligne",
                    onClick = onOfflineClick
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "Confidentialité",
                    onClick = onTelemetryClick
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
                SettingsMenuRow(
                    title = "À propos",
                    onClick = { isAboutMenu = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        IconButton(
            onClick = {
                if (isAboutMenu) {
                    isAboutMenu = false
                } else {
                    onBackClick()
                }
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = SecondaryColor
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressedBackgroundColor by animateColorAsState(
        targetValue = if (onClick != null && isPressed) Color(0xFF1C1C1E) else PrimaryColor,
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
                    color = SecondaryColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (showChevron && onClick != null) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Next Arrow Icon",
                    tint = SecondaryColor
                )
            }
        }
    }
}
