package eu.dotshell.pelo.generic.data.models.navigation

import eu.dotshell.pelo.generic.data.models.realtime.alerts.community.CommunityAlert

/**
 * Why we are interrupting a traveller to ask about an alert.
 *
 * The two cases read the same to the app but not to the person: one asks whether something
 * somebody just reported is real, the other whether something everyone agreed on an hour ago is
 * still going on. Without the second, a confirmed alert would live out its whole TTL unchallenged.
 */
enum class NavigationAlertPromptKind {
    /** One person reported it; nobody has vouched for it yet. */
    LOW_KARMA_CONFIRM,

    /** The crowd confirmed it a while ago and nothing has refreshed it since. */
    HIGH_KARMA_STILL_THERE
}

/**
 * An alert put to a traveller whose itinerary runs through it, with the question to ask.
 *
 * The whole design rests on asking the one person who can actually see the answer, at the moment
 * they can see it — which is why this is built from the journey being planned rather than pushed
 * at whoever happens to have the app open.
 */
data class NavigationAlertPrompt(
    val alert: CommunityAlert,
    val kind: NavigationAlertPromptKind
)
