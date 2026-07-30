import SwiftUI
import ComposeApp

/// Hosts the shared Compose UI (exported from the Kotlin `ComposeApp` framework).
struct ComposeHostView: UIViewControllerRepresentable {
    let onReady: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(onReady: onReady)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Covers the Compose view until it has a map to show.
///
/// iOS releases the real launch screen as soon as the first frame renders, so it cannot be held
/// back the way Android holds its splash. This stands in for it instead: `UILaunchScreen` in
/// project.yml is an empty dictionary, which the system draws as `systemBackground`, so using the
/// same colour here makes the hand-off seamless. Without it, the app cuts straight to an empty
/// surface while the view model is still being built.
struct ContentView: View {
    /// Mirrors MainActivity.MAX_SPLASH_HOLD_MS. A failed init never calls back, and the cover must
    /// not outlive the app's ability to show *something*.
    private static let maxCoverDuration: TimeInterval = 1.2

    @State private var isReady = false

    var body: some View {
        ZStack {
            ComposeHostView {
                // Hop to the next runloop pass: the callback arrives from inside a Compose frame,
                // and SwiftUI complains about state mutated in the middle of a view update.
                DispatchQueue.main.async { isReady = true }
            }

            if !isReady {
                Color(UIColor.systemBackground)
                    .ignoresSafeArea()
            }
        }
        .animation(.easeOut(duration: 0.15), value: isReady)
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.maxCoverDuration) {
                isReady = true
            }
        }
    }
}
