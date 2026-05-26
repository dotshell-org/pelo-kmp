package com.pelotcl.app.generic.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.telemetry.DailyReportState
import com.pelotcl.app.generic.data.telemetry.TelemetryEmitter
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import kotlinx.serialization.json.Json

/**
 * Read-only dump of the current [DailyReportState] as pretty-printed JSON. Provides radical
 * transparency: the user can see, byte-for-byte, what is queued for upload at any moment.
 *
 * No network calls happen here — we only read the in-memory state from the
 * [com.pelotcl.app.generic.data.telemetry.DailyReportRepository].
 */
@Composable
fun TelemetryPreviewScreen(
    onBackClick: () -> Unit,
    onSystemBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onSystemBack() }

    var snapshot by remember { mutableStateOf<DailyReportState?>(null) }
    var loading by remember { mutableStateOf(true) }

    val prettyJson = remember {
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
            encodeDefaults = true
        }
    }

    LaunchedEffect(Unit) {
        val repo = TelemetryEmitter.repository()
        snapshot = repo?.state?.value
        loading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PrimaryColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 80.dp, bottom = 24.dp)
        ) {
            Text(
                text = "Données collectées aujourd'hui",
                color = SecondaryColor,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                text = "Voici l'état exact en mémoire, tel qu'il sera envoyé à la prochaine fermeture de l'app.",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    when {
                        loading -> CircularProgressIndicator(color = SecondaryColor)
                        snapshot == null -> Text(
                            text = "Aucune donnée pour le moment.",
                            color = SecondaryColor,
                            fontSize = 14.sp
                        )
                        else -> {
                            val text = prettyJson.encodeToString(DailyReportState.serializer(), snapshot!!)
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.Top
                            ) {
                                Text(
                                    text = text,
                                    color = Color(0xFFD0D0D5),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
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
