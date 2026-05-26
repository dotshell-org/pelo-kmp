package com.pelotcl.app.generic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.local_history.LocalHistoryStorage
import com.pelotcl.app.generic.data.telemetry.TelemetryEmitter
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Settings entry-point for telemetry. Lets the user:
 *  - flip the opt-in toggle (effective immediately for future events),
 *  - inspect the raw payload of the day (transparency, "see exactly what we collect"),
 *  - wipe the local history (favorites audit + trip history).
 *
 * Designed to mirror the visual style of the rest of [SettingsScreen]: dark background,
 * card rows, chevrons for navigation. The CGU link is a navigation hop to the existing
 * legal screen and is exposed by the caller through [onLegalClick].
 */
@Composable
fun TelemetrySettingsScreen(
    onBackClick: () -> Unit,
    onSystemBack: () -> Unit,
    onShowCollectedData: () -> Unit,
    onLegalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val optInManager = TelemetryEmitter.optInManager()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler { onSystemBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Confidentialité",
                color = SecondaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (optInManager != null) {
                val state by optInManager.state.collectAsState()
                TelemetryToggleRow(
                    enabled = state.optedIn,
                    onToggle = { newValue ->
                        if (newValue) {
                            val schemaVersion = TelemetryEmitter.config()?.schemaVersion ?: 1
                            optInManager.acceptCurrentSchema(schemaVersion)
                        } else {
                            optInManager.decline()
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFF3A3A3C))
            }

            TelemetryMenuRow(
                title = "Voir les données collectées aujourd'hui",
                subtitle = "Transparence : ce que partage l'application",
                onClick = onShowCollectedData
            )
            HorizontalDivider(color = Color(0xFF3A3A3C))

            TelemetryMenuRow(
                title = "Supprimer mon historique local",
                subtitle = "Trajets et historique des favoris (non envoyés au serveur)",
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { LocalHistoryStorage(context).wipeAll() }
                    }
                }
            )
            HorizontalDivider(color = Color(0xFF3A3A3C))

            TelemetryMenuRow(
                title = "Mentions légales / CGU",
                onClick = onLegalClick
            )

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
                contentDescription = "Retour",
                tint = SecondaryColor
            )
        }
    }
}

@Composable
private fun TelemetryToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Partage de données anonymes",
                    color = SecondaryColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (enabled) "Activé — merci pour votre soutien" else "Désactivé",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = SecondaryColor,
                    checkedTrackColor = Color(0xFF34C759),
                    uncheckedThumbColor = SecondaryColor,
                    uncheckedTrackColor = Color(0xFF3A3A3C)
                )
            )
        }
    }
}

@Composable
private fun TelemetryMenuRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = PrimaryColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = SecondaryColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
