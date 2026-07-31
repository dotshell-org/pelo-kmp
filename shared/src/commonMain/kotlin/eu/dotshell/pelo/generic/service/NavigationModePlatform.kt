package eu.dotshell.pelo.generic.service

expect object NavigationModePlatform {
    /** The platform's own navigation service records trip telemetry; the controller must not. */
    val handlesTripTelemetry: Boolean

    /**
     * The platform runs its own background location stream during navigation and publishes to
     * [NavigationLocationBus]. When true the UI can leave its stream at browsing cadence instead
     * of running a second high-accuracy one alongside it.
     */
    val ownsLocationStream: Boolean
}
