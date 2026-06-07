package com.pelotcl.app.generic.ui.screens.plan

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pelotcl.app.generic.data.network.mapstyle.MapStyleData
import com.pelotcl.app.platform.provideMapStyleConfig
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.utils.map.MapStyleUtils.mapStyleLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapStyleSelectionSheet(
    isOffline: Boolean,
    downloadedMapStyles: Set<String>,
    selectedMapStyle: MapStyleData,
    onDismiss: () -> Unit,
    onStyleSelected: (MapStyleData) -> Unit
) {
    val mapStyleConfig = provideMapStyleConfig()
    val standardStyles = remember { mapStyleConfig.getStandardMapStyles() }
    val satelliteStyle = remember { mapStyleConfig.getSatelliteMapStyle() }
    val allStyles = remember { standardStyles + satelliteStyle }
    val firstRowStyles = remember { allStyles.take(4) }
    val secondRowStyles = remember { allStyles.drop(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SecondaryColor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Thème",
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                firstRowStyles.forEach { style ->
                    val enabled = !isOffline || style.key in downloadedMapStyles
                    val isSelected = style.key == selectedMapStyle.key

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    2.dp,
                                    if (isSelected) Color(0xFF3B82F6) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(2.dp)
                        ) {
                            MapStylePreviewTile(
                                style = style,
                                isEnabled = enabled,
                                onClick = { onStyleSelected(style) }
                            )
                        }

                        Text(
                            text = mapStyleLabel(style),
                            color = if (enabled) PrimaryColor else Color(0xFF9CA3AF)
                        )
                    }
                }
            }

            if (secondRowStyles.isNotEmpty()) {
                Spacer(modifier = Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                    verticalAlignment = Alignment.Top
                ) {
                    secondRowStyles.forEach { style ->
                        val enabled = !isOffline || style.key in downloadedMapStyles
                        val isSelected = style.key == selectedMapStyle.key

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) Color(0xFF3B82F6) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(2.dp)
                            ) {
                                MapStylePreviewTile(
                                    style = style,
                                    isEnabled = enabled,
                                    onClick = { onStyleSelected(style) }
                                )
                            }

                            Text(
                                text = mapStyleLabel(style),
                                color = if (enabled) PrimaryColor else Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
            }
        }
    }
}
