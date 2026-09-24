import SwiftUI

@main
struct iOSApp: App {
    init() {
        // Live is read from a music stand: the screen must not go dark while one plays (spec 3.6).
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
