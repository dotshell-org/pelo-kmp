package eu.dotshell.pelo.generic.service

/**
 * Implemented in Swift, because ActivityKit is a Swift-only framework with no Objective-C surface
 * and therefore nothing for Kotlin/Native cinterop to bind against. The Kotlin side owns *when*
 * the activity should change; Swift owns *how*.
 *
 * Register an implementation once at launch via [NavigationLiveActivity.handler].
 */
interface NavigationLiveActivityHandler {
    fun start(state: NavigationLiveActivityState)
    fun update(state: NavigationLiveActivityState)
    fun end()
}

actual object NavigationLiveActivity {

    /**
     * Set from Swift at app start. Null until then — and on iOS below 16.1, where the Swift side
     * declines to register at all, so every call here is a silent no-op rather than a crash.
     */
    var handler: NavigationLiveActivityHandler? = null

    actual fun start(state: NavigationLiveActivityState) {
        handler?.start(state)
    }

    actual fun update(state: NavigationLiveActivityState) {
        handler?.update(state)
    }

    actual fun end() {
        handler?.end()
    }
}
