package eu.dotshell.pelo.generic.ui.screens.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import eu.dotshell.pelo.generic.ui.theme.TransportThemeProvider
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = StringProvider(LocalPlatformContext.current)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings["credits_title"],
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings["back"],
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = strings["credits_transport_data_title"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = strings["credits_transport_data_content"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "data.grandlyon.com",
                    url = "https://data.grandlyon.com",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "api.dotshell.eu/pelo/v1/",
                    url = "https://api.dotshell.eu/pelo/v1/",
                    uriHandler = uriHandler,
                    strings = strings
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = strings["credits_cartography_title"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = strings["credits_cartography_content"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "maplibre.org",
                    url = "https://maplibre.org",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "openstreetmap.org",
                    url = "https://www.openstreetmap.org",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "openmaptiles.org",
                    url = "https://openmaptiles.org",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "maptiler.com",
                    url = "https://www.maptiler.com",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "esri.com",
                    url = "https://www.esri.com",
                    uriHandler = uriHandler,
                    strings = strings
                )
            }

            Text(
                text = strings["credits_development_title"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )

            Text(
                text = strings["credits_development_content"],
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                ClickableLink(
                    label = "github.com/dotshell-org",
                    url = "https://github.com/dotshell-org",
                    uriHandler = uriHandler,
                    strings = strings
                )
                Spacer(modifier = Modifier.height(4.dp))
                ClickableLink(
                    label = "dotshell.eu",
                    url = "https://www.dotshell.eu",
                    uriHandler = uriHandler,
                    strings = strings
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ClickableLink(
    label: String,
    url: String,
    uriHandler: UriHandler,
    strings: StringProvider,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { uriHandler.openUri(url) }
    ) {
        Text(
            text = label,
            color = TransportThemeProvider.getTheme().linkColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = strings["credits_open_link"],
            tint = TransportThemeProvider.getTheme().linkColor,
            modifier = Modifier
                .padding(top = 2.dp)
                .width(14.dp)
                .height(14.dp)
        )
    }
}
