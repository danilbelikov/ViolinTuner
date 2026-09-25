import AppMetricaCore
import AppMetricaCrashes
import Shared

/// The one place of the iOS app that knows AppMetrica (spec 5.27): the shared code speaks `AnalyticsService` only.
final class AppMetricaService: AnalyticsService {
    func activate(apiKey: String, logs: Bool) {
        guard let configuration = AppMetricaConfiguration(apiKey: apiKey) else { return }
        // muted until the stored consent says otherwise: nothing leaves before it, no first session is lost to waiting
        configuration.dataSendingEnabled = false
        configuration.areLogsEnabled = logs
        AppMetrica.activate(with: configuration)
    }

    func setDataSendingEnabled(enabled: Bool) {
        AppMetrica.setDataSendingEnabled(enabled)
    }

    func reportEvent(name: String, params: [String: Any]) {
        if params.isEmpty {
            AppMetrica.reportEvent(name: name, onFailure: nil)
        } else {
            AppMetrica.reportEvent(name: name, parameters: params, onFailure: nil)
        }
    }

    func reportError(group: String, message: String, cause: String?) {
        let error = AppMetricaError(identifier: group, message: message, parameters: cause.map { ["cause": $0] })
        AppMetricaCrashes.crashes().report(error: error, onFailure: nil)
    }
}
