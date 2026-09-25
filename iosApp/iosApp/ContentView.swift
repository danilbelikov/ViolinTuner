import Shared
import SwiftUI

/** The Compose screen of the shared module, over the whole window: Compose keeps its content out of the notch itself. */
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(analytics: AppMetricaService())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
