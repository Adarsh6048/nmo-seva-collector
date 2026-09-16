import SwiftUI

@main
struct NMOSevaLedgerApp: App {
    @StateObject private var settings = SettingsStore()
    @StateObject private var ledger = LedgerStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(ledger)
                .preferredColorScheme(.dark)
                .onAppear {
                    SharedImportService.consume(into: ledger)
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        if settings.isConfigured {
            ContentView()
        } else {
            SetupView()
        }
    }
}
