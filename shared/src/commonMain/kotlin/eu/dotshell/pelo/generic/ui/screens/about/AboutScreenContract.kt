package eu.dotshell.pelo.generic.ui.screens.about

import androidx.compose.runtime.Composable

/**
 * Contract for "About" screens
 * Each city must provide its own implementation
 */
interface AboutScreenContract {

    /**
     * Name of the "About" screen
     */
    val screenTitle: String

    /**
     * Component for the main "About" screen
     */
    @Composable
    fun AboutScreenContent(
        onBackClick: () -> Unit,
        onCreditsClick: () -> Unit,
        onLegalClick: () -> Unit,
        onContactClick: () -> Unit
    )

    /**
     * Component for the "Credits" screen
     */
    @Composable
    fun CreditsScreenContent(
        onBackClick: () -> Unit,
        onDataSourceClick: () -> Unit,
        onApiSourceClick: () -> Unit
    )

    /**
     * Component for the "Legal Mentions" screen
     */
    @Composable
    fun LegalScreenContent(
        onBackClick: () -> Unit
    )

    /**
     * Component for the "Contact" screen
     */
    @Composable
    fun ContactScreenContent(
        onBackClick: () -> Unit
    )

    /**
     * Data model for a credits section
     */
    data class CreditSection(
        val title: String,
        val content: String,
        val links: List<CreditLink>
    )

    /**
     * Data model for a link within credits
     */
    data class CreditLink(
        val label: String,
        val url: String
    )

    /**
     * Data model for a legal section
     */
    data class LegalSection(
        val title: String,
        val content: String
    )

    /**
     * Provides city-specific credit sections
     */
    fun getCreditSections(): List<CreditSection>

    /**
     * Provides city-specific legal sections
     */
    fun getLegalSections(): List<LegalSection>

    /**
     * Provides contact information
     */
    fun getContactInfo(): ContactInfo

    /**
     * Data model for contact information
     */
    data class ContactInfo(
        val email: String?,
        val website: String?,
        val socialMedia: List<SocialMediaLink>
    )

    /**
     * Data model for a social media link
     */
    data class SocialMediaLink(
        val platform: String,
        val url: String,
        val username: String
    )
}