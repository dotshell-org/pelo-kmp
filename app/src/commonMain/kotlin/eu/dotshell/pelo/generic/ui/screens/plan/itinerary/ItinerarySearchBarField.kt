package eu.dotshell.pelo.generic.ui.screens.plan.itinerary

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import eu.dotshell.pelo.generic.data.models.itinerary.SelectedStop
import androidx.compose.material3.MaterialTheme
import eu.dotshell.pelo.generic.ui.theme.AccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItinerarySearchBarField(
    modifier: Modifier = Modifier,
    selectedStop: SelectedStop?,
    onClick: () -> Unit,
    icon: ImageVector,
    placeholder: String
) {
    val displayedValue = selectedStop?.name ?: ""

    SearchBar(
        modifier = modifier
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                query = displayedValue,
                onQueryChange = { onClick() },
                onSearch = { onClick() },
                expanded = false,
                onExpandedChange = { if (it) onClick() },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AccentColor
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        },
        expanded = false,
        onExpandedChange = { if (it) onClick() },
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            dividerColor = Color.Transparent
        )
    ) {}
}
