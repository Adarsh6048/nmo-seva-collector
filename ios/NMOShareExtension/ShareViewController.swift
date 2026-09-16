import UIKit
import UniformTypeIdentifiers

final class ShareViewController: UIViewController {
    private let groupId = "group.org.nmo.seva.ledger"

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        collectSharedText()
    }

    private func collectSharedText() {
        guard let items = extensionContext?.inputItems as? [NSExtensionItem] else {
            finish(); return
        }
        let providers = items.compactMap(\.attachments).flatMap { $0 }
        guard let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }) else {
            finish(); return
        }
        provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] item, _ in
            guard let self else { return }
            let text = (item as? String) ?? ((item as? NSAttributedString)?.string ?? "")
            guard let parsed = self.parsePaymentText(text) else { self.finish(); return }
            self.save(parsed)
            self.finish()
        }
    }

    private func parsePaymentText(_ text: String) -> [String: Any]? {
        let normalized = text.replacingOccurrences(of: "\n", with: " ")
        let amountPattern = #"(?:₹|Rs\.?|INR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"#
        guard let regex = try? NSRegularExpression(pattern: amountPattern, options: [.caseInsensitive]),
              let match = regex.firstMatch(in: normalized, range: NSRange(normalized.startIndex..., in: normalized)),
              let range = Range(match.range(at: 1), in: normalized),
              let amount = Double(normalized[range].replacingOccurrences(of: ",", with: "")) else { return nil }

        let lower = normalized.lowercased()
        let outgoing = ["you sent", "you paid", "paid to", "sent to", "debited"].contains { lower.contains($0) }
        let incoming = ["you received", "received", "credited", "sent you", "paid you", "to you"].contains { lower.contains($0) }
        guard outgoing || incoming else { return nil }

        let direction = outgoing ? "OUTGOING" : "INCOMING"
        return [
            "eventId": UUID().uuidString,
            "amount": amount,
            "receivedAt": Int64(Date().timeIntervalSince1970 * 1000),
            "sourceApp": "iOS Share",
            "direction": direction,
            "donationStatus": direction == "INCOMING" ? "PENDING" : "NOT_DONATION",
            "expenseStatus": direction == "OUTGOING" ? "PENDING" : "NOT_APPLICABLE"
        ]
    }

    private func save(_ object: [String: Any]) {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: groupId),
              let data = try? JSONSerialization.data(withJSONObject: object) else { return }
        let file = container.appendingPathComponent("pending-import-\(UUID().uuidString).json")
        try? data.write(to: file, options: .atomic)
    }

    private func finish() {
        DispatchQueue.main.async { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }
    }
}
