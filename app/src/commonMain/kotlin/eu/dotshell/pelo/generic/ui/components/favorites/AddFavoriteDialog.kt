package eu.dotshell.pelo.generic.ui.components.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.dotshell.pelo.generic.data.models.search.StationSearchResult
import eu.dotshell.pelo.generic.ui.components.search.TransportSearchBar
import eu.dotshell.pelo.generic.data.models.search.TransportSearchContent
import eu.dotshell.pelo.generic.ui.theme.Gray700
import eu.dotshell.pelo.generic.ui.theme.PrimaryColor
import eu.dotshell.pelo.generic.ui.theme.SecondaryColor
import eu.dotshell.pelo.generic.ui.viewmodel.TransportViewModelInterface
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

/**
 * Dialog for creating a new favorite from predefined presets.
 * @param onDismiss Callback when dialog is dismissed
 * @param onFavoriteCreated Callback when a new favorite is created
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onFavoriteCreated: (String, String, String) -> Unit,
    viewModel: TransportViewModelInterface,
    initialStopName: String? = null
) {
    val strings = StringProvider(LocalPlatformContext.current)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val homePreset = strings["preset_home"]
    val workPreset = strings["preset_work"]
    val schoolPreset = strings["preset_school"]
    val shoppingPreset = strings["preset_shopping"]
    val busPreset = strings["preset_bus_station"]
    val trainPreset = strings["preset_train_station"]
    val otherPreset = strings["preset_other"]

    val presets = remember(homePreset, workPreset, schoolPreset, shoppingPreset, busPreset, trainPreset, otherPreset) {
        listOf(
            FavoritePreset(homePreset, "home"),
            FavoritePreset(workPreset, "work"),
            FavoritePreset(schoolPreset, "school"),
            FavoritePreset(shoppingPreset, "shopping"),
            FavoritePreset(busPreset, "bus"),
            FavoritePreset(trainPreset, "train"),
            FavoritePreset(otherPreset, "star")
        )
    }

    var selectedPreset by remember { mutableStateOf(presets.firstOrNull()) }
    var customOtherTitle by remember { mutableStateOf("") }
    var selectedStop by remember(initialStopName) {
        mutableStateOf(initialStopName?.let {
            StationSearchResult(
                stopName = it,
                lines = emptyList()
            )
        })
    }
    var stopSearchOverlayQuery by remember { mutableStateOf("") }
    var showStopSearchFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(showStopSearchFullscreen) {
        if (showStopSearchFullscreen) {
            stopSearchOverlayQuery = ""
        }
    }

    val isOtherSelected = selectedPreset?.iconName == "star"
    val finalFavoriteTitle =
        if (isOtherSelected) customOtherTitle.trim() else (selectedPreset?.name ?: "")

    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SecondaryColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings["favorite_new"],
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryColor
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = strings["close"],
                        tint = Gray700
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Preset selection
            Text(
                text = strings["favorite_type"],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    IconSelectionButton(
                        iconName = preset.iconName,
                        label = preset.name,
                        isSelected = preset == selectedPreset,
                        onClick = {
                            selectedPreset = preset
                            if (preset.iconName != "star") {
                                customOtherTitle = ""
                            }
                        }
                    )
                }
            }

            if (isOtherSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = strings["favorite_title"],
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryColor
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = customOtherTitle,
                    onValueChange = { customOtherTitle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFF5F5F5))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaryColor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    decorationBox = { innerTextField ->
                        if (customOtherTitle.isBlank()) {
                            Text(
                                text = strings["favorite_title_placeholder"],
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Stop selection
            Text(
                text = strings["favorite_associated_stop"],
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(PrimaryColor)
                    .clickable { showStopSearchFullscreen = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = SecondaryColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedStop?.stopName ?: strings["search_stop_placeholder"],
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryColor
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Create button
            Button(
                onClick = {
                    val preset = selectedPreset
                    val stop = selectedStop
                    if (preset != null && stop != null && finalFavoriteTitle.isNotBlank()) {
                        onFavoriteCreated(finalFavoriteTitle, preset.iconName, stop.stopName)
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryColor,
                    contentColor = SecondaryColor,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = SecondaryColor
                ),
                shape = RoundedCornerShape(28.dp),
                enabled = selectedPreset != null && selectedStop != null && finalFavoriteTitle.isNotBlank()
            ) {
                Text(
                    text = strings["favorite_create"],
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showStopSearchFullscreen) {
        Dialog(
            onDismissRequest = { showStopSearchFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryColor)
            ) {
                TransportSearchBar(
                    onSearchStops = { query -> viewModel.searchStops(query) },
                    onSearchLines = { emptyList() },
                    modifier = Modifier.fillMaxSize(),
                    content = TransportSearchContent.STOPS_ONLY,
                    showHistory = false,
                    startExpanded = true,
                    showDarkOutline = false,
                    searchPlaceholder = strings["search_stop_placeholder"],
                    query = stopSearchOverlayQuery,
                    onQueryChange = { q ->
                        stopSearchOverlayQuery = q
                    },
                    onExpandedChange = { expanded ->
                        if (!expanded) showStopSearchFullscreen = false
                    },
                    onStopPrimary = { result ->
                        selectedStop = result
                        stopSearchOverlayQuery = ""
                        showStopSearchFullscreen = false
                    }
                )
            }
        }
    }
}

private data class FavoritePreset(
    val name: String,
    val iconName: String
)