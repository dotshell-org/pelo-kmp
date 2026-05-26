package com.pelotcl.app.generic.ui.screens.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pelotcl.app.generic.data.config.TelemetryDisclosureData
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor

/**
 * One-shot consent screen presented on the first launch (and again if the schema version
 * advances in a breaking way).
 *
 * Content is fully driven by [TelemetryDisclosureData] sourced from `config.yml`, so the
 * same screen renders correctly for any white-labeled network without code changes.
 */
@Composable
fun TelemetryOptInScreen(
    disclosure: TelemetryDisclosureData,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(containerColor = PrimaryColor) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = SecondaryColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = disclosure.title,
                    color = SecondaryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            if (disclosure.body.isNotBlank()) {
                Text(
                    text = disclosure.body,
                    color = SecondaryColor,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(16.dp))
            }

            DisclosureGroup(
                heading = "Données partagées (anonymes)",
                items = disclosure.items
            )

            Spacer(Modifier.height(16.dp))

            HorizontalDivider(color = SecondaryColor.copy(alpha = 0.3f))

            Spacer(Modifier.height(16.dp))

            DisclosureGroup(
                heading = "Toujours conservé sur votre téléphone",
                items = disclosure.localOnly
            )

            if (disclosure.privacyNote.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = disclosure.privacyNote,
                        color = SecondaryColor.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryColor,
                    contentColor = PrimaryColor
                )
            ) {
                Text("Activer le partage", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryColor)
            ) {
                Text("Plus tard")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DisclosureGroup(heading: String, items: List<String>) {
    if (items.isEmpty()) return
    Text(
        text = heading,
        color = SecondaryColor,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SecondaryColor.copy(alpha = 0.85f),
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 3.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = item,
                    color = SecondaryColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
