import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // The map is drawn by the Mapbox SDK, which only Swift can call (FaunaMapbox.swift).
        NativeMaps.shared.factory = FaunaMapFactory()
        // Background tasks and the notification delegate must be registered before launch finishes.
        MainViewControllerKt.onAppLaunch()
    }

    var body: some Scene {
        WindowGroup {
            // Compose handles insets itself (safeDrawingPadding), so it gets the full screen.
            ContentView().ignoresSafeArea()
        }
    }
}
