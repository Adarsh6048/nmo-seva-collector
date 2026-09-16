import Foundation
import Combine

final class SettingsStore: ObservableObject {
    @Published var collectorName: String { didSet { save() } }
    @Published var collectorCode: String { didSet { save() } }
    @Published var collectorToken: String { didSet { save() } }
    @Published var backendURL: String { didSet { save() } }
    @Published var ledgerConsent: Bool { didSet { save() } }

    private let defaults = UserDefaults.standard

    init() {
        collectorName = defaults.string(forKey: "collectorName") ?? ""
        collectorCode = defaults.string(forKey: "collectorCode") ?? ""
        collectorToken = defaults.string(forKey: "collectorToken") ?? ""
        backendURL = defaults.string(forKey: "backendURL") ?? ""
        ledgerConsent = defaults.bool(forKey: "ledgerConsent")
    }

    var isConfigured: Bool {
        !collectorCode.trimmingCharacters(in: .whitespaces).isEmpty &&
        !collectorToken.trimmingCharacters(in: .whitespaces).isEmpty &&
        backendURL.lowercased().hasPrefix("https://") && ledgerConsent
    }

    private func save() {
        defaults.set(collectorName, forKey: "collectorName")
        defaults.set(collectorCode.uppercased(), forKey: "collectorCode")
        defaults.set(collectorToken, forKey: "collectorToken")
        defaults.set(backendURL, forKey: "backendURL")
        defaults.set(ledgerConsent, forKey: "ledgerConsent")
    }
}
