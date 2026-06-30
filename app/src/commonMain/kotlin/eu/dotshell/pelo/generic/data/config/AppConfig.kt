package eu.dotshell.pelo.generic.data.config
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName

@Serializable
data class AppConfig(
    @SerialName("transport") val transport: TransportConfigData,
    @SerialName("theme") val theme: ThemeData,
    @SerialName("about") val about: AboutData,
    @SerialName("rules") val rules: RulesData,
    @SerialName("mapStyles") val mapStyles: MapStylesData,
    @SerialName("lineColors") val lineColors: LineColorsData,
    @SerialName("cache") val cache: CacheConfigData,
    @SerialName("itinerarySettings") val itinerarySettings: ItinerarySettingsData,
    @SerialName("telemetry") val telemetry: TelemetryConfigData? = null,
    @SerialName("consent") val consent: ConsentConfigData
)

@Serializable
data class TransportConfigData(
    val baseUrl: String,
    val networkName: String,
    val region: String,
    val organizingAuthority: String,
    val dataSource: String,
    val dataSourceUrl: String,
    val dataLicense: String,
    val regionBounds: List<Double>,
    val offlineMapZoomRange: IntRangeData,
    val schoolHolidaysFile: String,
    val primaryColor: String,
    val secondaryColor: String,
    val trafficAlertsBaseUrl: String,
    val vehiclePositionsStreamUrl: String
)

@Serializable
data class IntRangeData(
    val start: Int,
    val end: Int
)

@Serializable
data class ThemeData(
    val metroLineColor: String,
    val tramLineColor: String,
    val busLineColor: String,
    val errorColor: String,
    val successColor: String,
    val warningColor: String,
    val disruptionColor: String,
    val linkColor: String = "#4285F4"
)

@Serializable
data class AboutData(
    val screenTitle: String,
    val sections: List<AboutSectionData>,
    val legalSections: List<AboutSectionData>,
    val contact: ContactData,
    val labels: AboutLabelsData = AboutLabelsData()
)

@Serializable
data class AboutLabelsData(
    val creditsTitle: String = "Crédits",
    val legalTitle: String = "Mentions légales / CGU",
    val contactTitle: String = "Contact",
    val backContentDescription: String = "Retour",
    val openContentDescription: String = "Ouvrir",
    val emailLabel: String = "Email",
    val websiteLabel: String = "Site web",
    val socialMediaSectionTitle: String = "Réseaux sociaux"
)

@Serializable
data class AboutSectionData(
    val title: String,
    val content: String,
    val links: List<AboutLinkData> = emptyList()
)

@Serializable
data class AboutLinkData(
    val label: String,
    val url: String
)

@Serializable
data class ContactData(
    val email: String?,
    val website: String?,
    val socialMedia: List<SocialMediaData>
)

@Serializable
data class SocialMediaData(
    val platform: String,
    val url: String,
    val username: String
)

@Serializable
data class RulesData(
    val strongLines: List<String>,
    val strongLineRegexes: List<String> = emptyList(),
    val lineNameRegexes: List<String>,
    val transportTypes: List<TransportTypeData> = emptyList(),
    val aliases: List<LineAliasData> = emptyList(),
    val excludedLines: List<String> = emptyList(),
    val vehicleMarkers: List<VehicleMarkerRuleData> = emptyList(),
    val defaultVehicleMarker: String = "BUS"
)

@Serializable
data class TransportTypeData(
    val name: String,
    val regex: String,
    val icon: String? = null
)

@Serializable
data class LineAliasData(
    val from: String,
    val to: String,
    val matchType: String = "exact",
    val equivalents: List<String> = emptyList(),
    val displayAs: String? = null
)

@Serializable
data class VehicleMarkerRuleData(
    val prefix: String,
    val marker: String
)

@Serializable
data class MapStylesData(
    val defaultKey: String,
    val standard: List<MapStyleEntryData>,
    val satellite: MapStyleEntryData
)

@Serializable
data class MapStyleEntryData(
    val key: String,
    val displayName: String,
    val styleUrl: String
)

@Serializable
data class LineColorsData(
    val rules: List<LineColorRuleData>,
    val fallback: String
)

@Serializable
data class LineColorRuleData(
    val match: String,
    val type: String = "exact",
    val color: String
)

@Serializable
data class CacheConfigData(
    val validityHours: Long = 24,
    val cacheBusLines: Boolean = false
)

@Serializable
data class ItinerarySettingsData(
    val screenTitle: String,
    val sectionTitle: String,
    val options: List<ItineraryOptionData>
)

@Serializable
data class ItineraryOptionData(
    val key: String,
    val title: String,
    val subtitle: String,
    val defaultEnabled: Boolean = true
)

@Serializable
data class TelemetryConfigData(
    val enabled: Boolean = true,
    val endpointUrl: String,
    val schemaVersion: Int = 1,
    val networkCode: String,
    val closeDebounceSeconds: Long = 60,
    val tripSamplingSeconds: Long = 30,
    val tripSnapRadiusMeters: Int = 100,
    val profileWindowDays: Int = 30,
    val disclosure: TelemetryDisclosureData = TelemetryDisclosureData()
)

@Serializable
data class TelemetryDisclosureData(
    val title: String = "Aidez-nous à améliorer le réseau",
    val body: String = "",
    val items: List<String> = emptyList(),
    val localOnly: List<String> = emptyList(),
    val privacyNote: String = "",
    val faq: List<TelemetryFaqEntryData> = emptyList()
)

@Serializable
data class TelemetryFaqEntryData(
    val question: String,
    val answer: String
)

@Serializable
data class ConsentConfigData(
    val version: Int,
    val title: String,
    val intro: String,
    val summary: List<String> = emptyList(),
    val acceptLabel: String = "J'accepte",
    val declineLabel: String = "Je refuse",
    val declineNote: String = "",
    val acknowledgementLabel: String = "J'ai lu et approuvé les conditions d'utilisation",
    val acknowledgementLinkText: String = "conditions d'utilisation",
    val privacyAcknowledgementLabel: String = "J'ai lu et approuvé la politique de confidentialite",
    val privacyAcknowledgementLinkText: String = "politique de confidentialite",
    val privacyDetailsTitle: String = "Politique de confidentialité",
    val privacySectionTitle: String = "Traitement des données et confidentialité",
    val detailsTitle: String = "Conditions d'utilisation",
    val detailsButtonLabel: String = "Lire les conditions compl\u00e8tes"
)
