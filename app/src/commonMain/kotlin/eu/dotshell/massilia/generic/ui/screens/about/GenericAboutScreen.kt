package eu.dotshell.massilia.generic.ui.screens.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.dotshell.massilia.generic.data.config.AboutData
import androidx.compose.material3.MaterialTheme
import eu.dotshell.massilia.generic.ui.theme.TransportThemeProvider

class GenericAboutScreen(private val data: AboutData) : AboutScreenContract {

    override val screenTitle: String = data.screenTitle

    private val linkColor: Color
        get() = TransportThemeProvider.getTheme().linkColor

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun AboutScreenContent(
        onBackClick: () -> Unit,
        onCreditsClick: () -> Unit,
        onLegalClick: () -> Unit,
        onContactClick: () -> Unit
    ) {
        Scaffold(
            topBar = { AboutTopBar(screenTitle, onBackClick) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                AboutMenuItem(data.labels.creditsTitle, onCreditsClick)
                AboutMenuItem(data.labels.legalTitle, onLegalClick)
                AboutMenuItem(data.labels.contactTitle, onContactClick)
            }
        }
    }

    @Composable
    private fun AboutMenuItem(text: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(16.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                data.labels.openContentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun CreditsScreenContent(
        onBackClick: () -> Unit,
        onDataSourceClick: () -> Unit,
        onApiSourceClick: () -> Unit
    ) {
        val sections = getCreditSections()

        Scaffold(
            topBar = { AboutTopBar(data.labels.creditsTitle, onBackClick) },
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
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = section.content,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    section.links.forEach { link ->
                        ClickableLink(link.label, link.url, uriHandler)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    @Composable
    private fun ClickableLink(label: String, url: String, uriHandler: UriHandler) {
        val color = linkColor
        Row(
            modifier = Modifier
                .clickable { uriHandler.openUri(url) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = color, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                data.labels.openContentDescription,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun LegalScreenContent(onBackClick: () -> Unit) {
        val sections = getLegalSections()

        Scaffold(
            topBar = { AboutTopBar(data.labels.legalTitle, onBackClick) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sections.forEach { section ->
                    Text(
                        text = section.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = section.content,
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun ContactScreenContent(onBackClick: () -> Unit) {
        val contactInfo = getContactInfo()

        Scaffold(
            topBar = { AboutTopBar(data.labels.contactTitle, onBackClick) },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                contactInfo.email?.let { email ->
                    ContactItem(data.labels.emailLabel, email, "mailto:$email")
                }

                contactInfo.website?.let { website ->
                    ContactItem(data.labels.websiteLabel, website, website)
                }

                if (contactInfo.socialMedia.isNotEmpty()) {
                    Text(
                        text = data.labels.socialMediaSectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )

                    contactInfo.socialMedia.forEach { social ->
                        ContactItem(social.platform, "@${social.username}", social.url)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AboutTopBar(title: String, onBackClick: () -> Unit) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        data.labels.backContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }

    @Composable
    private fun ContactItem(label: String, value: String, url: String) {
        val uriHandler = LocalUriHandler.current
        val color = linkColor

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .clickable { uriHandler.openUri(url) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    data.labels.openContentDescription,
                    tint = color
                )
            }
        }
    }

    override fun getCreditSections(): List<AboutScreenContract.CreditSection> {
        return data.sections.map { section ->
            AboutScreenContract.CreditSection(
                title = section.title,
                content = section.content,
                links = section.links.map { AboutScreenContract.CreditLink(it.label, it.url) }
            )
        }
    }

    override fun getLegalSections(): List<AboutScreenContract.LegalSection> {
        return data.legalSections.map { section ->
            AboutScreenContract.LegalSection(
                title = section.title,
                content = section.content
            )
        }
    }

    override fun getContactInfo(): AboutScreenContract.ContactInfo {
        return AboutScreenContract.ContactInfo(
            email = data.contact.email,
            website = data.contact.website,
            socialMedia = data.contact.socialMedia.map {
                AboutScreenContract.SocialMediaLink(it.platform, it.url, it.username)
            }
        )
    }
}
