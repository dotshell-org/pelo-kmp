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
        onMain {
            guard #available(iOS 16.1, *) else { return }
            self.requestActivity(state: state)
        }
    }

    func update(state: NavigationLiveActivityState) {
        onMain {
            guard #available(iOS 16.1, *) else { return }
            guard let running = self.currentActivity else { return }
            Task { await running.update(using: state.asContentState) }
        }
    }

    func end() {
        onMain {
            guard #available(iOS 16.1, *) else { return }
            guard let running = self.currentActivity else { return }
            self.activity = nil
            Task { await running.end(dismissalPolicy: .immediate) }
        }
    }

    @available(iOS 16.1, *)
    private func requestActivity(state: NavigationLiveActivityState) {
        // Already running: treat a second start as an update, so a reroute — which restarts the
        // session — carries on in the same activity instead of stacking a new one.
        guard currentActivity == nil else {
            update(state: state)
            return
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        let attributes = PeloNavigationAttributes(destination: state.destination)
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

    /// The shared controller drives this from its own coroutine scope — a background dispatcher —
    /// where it used to be a Compose effect on the main thread. Requesting and mutating an
    /// activity is a UI operation; getting back on the main thread costs nothing and removes the
    /// question.
    private func onMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
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
            isArrived: isArrived,
            segments: segments.map { PeloNavigationAttributes.Segment(from: $0) },
            transferOffsetsSeconds: transferOffsetsSeconds.map { Int(truncating: $0) },
            progressSeconds: Int(progressSeconds),
            totalSeconds: Int(totalSeconds)
        )
    }
}

@available(iOS 16.1, *)
private extension PeloNavigationAttributes.Segment {
    init(from segment: NavigationRouteSegment) {
        self.init(
            seconds: Int(segment.seconds),
            kind: Kind(kotlinName: segment.kind.name),
            colorArgb: segment.colorArgb.map { Int(truncating: $0) }
        )
    }
}

@available(iOS 16.1, *)
private extension PeloNavigationAttributes.Segment.Kind {
    /// Matched on the Kotlin enum's own `name` rather than on a bridged case. How Kotlin/Native
    /// spells an exported enum entry in Swift is the compiler's business and can change; the name
    /// written in the Kotlin source is part of the contract.
    init(kotlinName: String) {
        switch kotlinName {
        case "WALK": self = .walk
        case "WAIT": self = .wait
        default: self = .ride
        }
    }
}
