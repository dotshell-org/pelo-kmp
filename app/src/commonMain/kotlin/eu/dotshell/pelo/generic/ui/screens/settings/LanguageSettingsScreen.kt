package eu.dotshell.pelo.generic.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.dotshell.pelo.generic.ui.theme.PrimaryColor
import eu.dotshell.pelo.generic.ui.theme.SecondaryColor
import eu.dotshell.pelo.platform.AppLanguage
import eu.dotshell.pelo.platform.LanguageManager
import eu.dotshell.pelo.platform.LocalPlatformContext
import eu.dotshell.pelo.platform.StringProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
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
                        text = strings["language_title"],
                        color = SecondaryColor,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings["back"],
                            tint = SecondaryColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor
                )
            )
        },
        containerColor = PrimaryColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppLanguage.entries.forEach { lang ->
                val label = if (lang == AppLanguage.SYSTEM) {
                    strings["language_system"]
                } else {
                    lang.nativeName
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            LanguageManager.set(lang)
                        }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = LanguageManager.current == lang,
                        onClick = {
                            LanguageManager.set(lang)
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = SecondaryColor,
                            unselectedColor = SecondaryColor.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = label,
                        color = SecondaryColor,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
