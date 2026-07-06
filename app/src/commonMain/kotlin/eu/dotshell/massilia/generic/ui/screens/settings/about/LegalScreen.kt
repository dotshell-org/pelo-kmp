package eu.dotshell.massilia.generic.ui.screens.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.massilia.generic.data.config.AboutSectionData
import androidx.compose.material3.MaterialTheme
import eu.dotshell.massilia.platform.LocalPlatformContext
import eu.dotshell.massilia.platform.StringProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    legalSections: List<AboutSectionData>,
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
                        text = strings["legal_title"],
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            legalSections.forEach { section ->
                val resolvedTitle = when (section.title) {
                    "Éditeur" -> strings["legal_editor_title"]
                    "Objet" -> strings["legal_object_title"]
                    "Description de l'application" -> strings["legal_desc_title"]
                    "Permissions" -> strings["legal_permissions_title"]
                    "Traitement des données et confidentialité" -> strings["legal_privacy_title"]
                    "Responsabilité" -> strings["legal_liability_title"]
                    "Propriété intellectuelle" -> strings["legal_ip_title"]
                    "Mises à jour" -> strings["legal_updates_title"]
                    else -> section.title
                }
                val resolvedContent = when (section.title) {
                    "Éditeur" -> strings["legal_editor_content"]
                    "Objet" -> strings["legal_object_content"]
                    "Description de l'application" -> strings["legal_desc_content"]
                    "Permissions" -> strings["legal_permissions_content"]
                    "Traitement des données et confidentialité" -> strings["legal_privacy_content"]
                    "Responsabilité" -> strings["legal_liability_content"]
                    "Propriété intellectuelle" -> strings["legal_ip_content"]
                    "Mises à jour" -> strings["legal_updates_content"]
                    else -> section.content
                }
                Text(
                    text = resolvedTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = resolvedContent,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
