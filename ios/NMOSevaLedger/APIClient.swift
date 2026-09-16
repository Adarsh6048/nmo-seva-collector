import Foundation

enum APIError: Error { case invalidURL, badResponse }

struct APIClient {
    static func upload(events: [LedgerEvent], settings: SettingsStore) async throws {
        guard !events.isEmpty else { return }
        guard let url = URL(string: settings.backendURL) else { throw APIError.invalidURL }

        let eventObjects: [[String: Any?]] = events.map { event in
            [
                "eventId": event.eventId,
                "amount": event.amount,
                "donorName": event.donorName,
                "expenseNote": event.expenseNote,
                "senderHint": event.senderHint,
                "transactionRef": event.transactionRef,
                "receivedAt": event.receivedAt,
                "sourceApp": event.sourceApp,
                "direction": event.direction.rawValue,
                "donationStatus": event.donationStatus.rawValue,
                "expenseStatus": event.expenseStatus.rawValue,
                "reconciled": event.reconciled
            ]
        }

        let cleanEvents = eventObjects.map { dict in
            dict.reduce(into: [String: Any]()) { result, pair in
                result[pair.key] = pair.value ?? NSNull()
            }
        }
        let body: [String: Any] = [
            "collectorCode": settings.collectorCode.uppercased(),
            "token": settings.collectorToken,
            "events": cleanEvents
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { throw APIError.badResponse }
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        guard json?["ok"] as? Bool == true else { throw APIError.badResponse }
    }
}
