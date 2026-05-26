package com.pelotcl.app.generic.data.config

import com.google.gson.annotations.SerializedName

data class AppConfig(
    @SerializedName("transport") val transport: TransportConfigData,
    @SerializedName("theme") val theme: ThemeData,
    @SerializedName("about") val about: AboutData,
    @SerializedName("rules") val rules: RulesData,
    @SerializedName("mapStyles") val mapStyles: MapStylesData,
    @SerializedName("lineColors") val lineColors: LineColorsData,
    @SerializedName("cache") val cache: CacheConfigData,
    @SerializedName("itinerarySettings") val itinerarySettings: ItinerarySettingsData,
    @SerializedName("telemetry") val telemetry: TelemetryConfigData? = null
)

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
    val vehiclePositionsStreamUrl: String,
    val vehiclePositionsLineRefPattern: String = "(?<=::)[^:]+"
)

data class IntRangeData(
    val start: Int,
    val end: Int
)

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

data class AboutData(
    val screenTitle: String,
    val sections: List<AboutSectionData>,
    val legalSections: List<AboutSectionData>,
    val contact: ContactData,
    val labels: AboutLabelsData = AboutLabelsData()
)

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

data class AboutSectionData(
    val title: String,
    val content: String,
    val links: List<AboutLinkData> = emptyList()
)

data class AboutLinkData(
    val label: String,
    val url: String
)

data class ContactData(
    val email: String?,
    val website: String?,
    val socialMedia: List<SocialMediaData>
)

data class SocialMediaData(
    val platform: String,
    val url: String,
    val username: String
)

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

data class TransportTypeData(
    val name: String,
    val regex: String,
    val icon: String? = null
)

data class LineAliasData(
    val from: String,
    val to: String,
    val matchType: String = "exact",
    val equivalents: List<String> = emptyList(),
    val displayAs: String? = null
)

data class VehicleMarkerRuleData(
    val prefix: String,
    val marker: String
)

data class MapStylesData(
    val defaultKey: String,
    val standard: List<MapStyleEntryData>,
    val satellite: MapStyleEntryData
)

data class MapStyleEntryData(
    val key: String,
    val displayName: String,
    val styleUrl: String
)

data class LineColorsData(
    val rules: List<LineColorRuleData>,
    val fallback: String
)

data class LineColorRuleData(
    val match: String,
    val type: String = "exact",
    val color: String
)

data class CacheConfigData(
    val validityHours: Long = 24,
    val cacheBusLines: Boolean = false
)

data class ItinerarySettingsData(
    val screenTitle: String,
    val sectionTitle: String,
    val options: List<ItineraryOptionData>
)

data class ItineraryOptionData(
    val key: String,
    val title: String,
    val subtitle: String,
    val defaultEnabled: Boolean = true
)

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

data class TelemetryDisclosureData(
    val title: String = "Aidez-nous à améliorer le réseau",
    val body: String = "",
    val items: List<String> = emptyList(),
    val localOnly: List<String> = emptyList(),
    val privacyNote: String = ""
)
