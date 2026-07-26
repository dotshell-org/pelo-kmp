import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.initializeKmpDependencies()
        // Hands the shared navigation session somewhere to render outside the app. A no-op below
        // iOS 16.1, where the handler is simply never installed.
        NavigationLiveActivityController.register()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
