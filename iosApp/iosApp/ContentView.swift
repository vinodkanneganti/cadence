import SwiftUI
import Shared

/// Hosts the shared Compose Multiplatform UI (Kotlin `MainViewController`).
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
