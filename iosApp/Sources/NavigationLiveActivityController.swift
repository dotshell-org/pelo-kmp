import ActivityKit
import ComposeApp
import Foundation

/// Bridges the shared navigation session to ActivityKit.
///
/// The Kotlin side decides *when* the activity should start, change or end; this decides *how*.
/// ActivityKit is Swift-only — there is no Objective-C surface for Kotlin/Native to bind — so the
/// shared code calls through the `NavigationLiveActivityHandler` protocol it exports, and this
/// registers itself as the implementation at launch.
final class NavigationLiveActivityController: NavigationLiveActivityHandler {

    /// Installs the handler, unless the OS is too old for Live Activities. Leaving the handler
    /// null on older systems is what makes every call from the shared code a silent no-op.
    static func register() {
        guard #available(iOS 16.1, *) else { return }
        NavigationLiveActivity.shared.handler = NavigationLiveActivityController()
    }

    // Held as Any because the concrete Activity type is only available from iOS 16.1, and stored
    // properties cannot carry an availability annotation.
    private var activity: Any?

    func start(state: NavigationLiveActivityState) {
        guard #available(iOS 16.1, *) else { return }
        // Already running: treat a second start as an update, so a reroute — which restarts the
        // session — carries on in the same activity instead of stacking a new one.
        guard currentActivity == nil else {
            update(state: state)
            return
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        let attributes = PeloNavigationAttributes(destination: state.arrivalTimeText)
        do {
            activity = try Activity.request(
                attributes: attributes,
                contentState: state.asContentState,
                pushType: nil
            )
        } catch {
            // Denied, budget exhausted, or the system declined: navigation itself is unaffected.
            activity = nil
        }
    }

    func update(state: NavigationLiveActivityState) {
        guard #available(iOS 16.1, *), let running = currentActivity else { return }
        Task { await running.update(using: state.asContentState) }
    }

    func end() {
        guard #available(iOS 16.1, *), let running = currentActivity else { return }
        activity = nil
        Task { await running.end(dismissalPolicy: .immediate) }
    }

    @available(iOS 16.1, *)
    private var currentActivity: Activity<PeloNavigationAttributes>? {
        activity as? Activity<PeloNavigationAttributes>
    }
}

@available(iOS 16.1, *)
private extension NavigationLiveActivityState {
    var asContentState: PeloNavigationAttributes.ContentState {
        PeloNavigationAttributes.ContentState(
            instruction: instruction,
            lineName: lineName,
            remainingMinutes: Int(remainingMinutes),
            arrivalTime: arrivalTimeText,
            isArrived: isArrived
        )
    }
}
