package eu.dotshell.pelo.generic.data.network

/**
 * Identifying User-Agent sent to public third-party services (Photon geocoder, OSRM foot
 * router): their fair-use policies require clients to be identifiable, and anonymous traffic
 * risks silent throttling or bans.
 */
internal const val PUBLIC_SERVICES_USER_AGENT = "Pelo/1.0 (+https://dotshell.eu)"
