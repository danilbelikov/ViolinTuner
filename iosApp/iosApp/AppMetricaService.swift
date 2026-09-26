import AppMetricaCore
import AppMetricaCrashes
import Shared

/// The one place of the iOS app that knows AppMetrica (spec 5.27): the shared code speaks `AnalyticsService` only.
final class AppMetricaService: AnalyticsService {
    private var apiKey: String?

    func activate(apiKey: String, logs: Bool) {
        guard let configuration = AppMetricaConfiguration(apiKey: apiKey) else { return }
        // muted until the stored consent says otherwise: nothing leaves before it, no first session is lost to waiting
        configuration.dataSendingEnabled = false
        configuration.areLogsEnabled = logs
        AppMetrica.activate(with: configuration)
        self.apiKey = apiKey
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

    /// A Kotlin crash of the last run, as the plugins of Unity and Flutter tell theirs: a crash named by its class, with frames.
    func reportUnhandledException(type: String, message: String?, frames: [KotlinCrashFrame], environment: [String: String]) {
        let backtrace = frames.map {
            StackTraceElement(className: $0.className, fileName: $0.fileName, line: $0.line > 0 ? NSNumber(value: $0.line) : nil, column: nil, methodName: $0.methodName)
        }
        let details = PluginErrorDetails(
            exceptionClass: type, message: message, backtrace: backtrace, platform: "kotlin",
            virtualMachineVersion: environment["kotlin"], pluginEnvironment: environment
        )
        // The reporter of the key, not the module's `pluginExtension()`: the module sets itself up on the library's own queue
        // after `activate`, and a crash told at the start finds it «not configured»; the reporter of the key is there at once.
        guard let apiKey, let reporter = AppMetricaCrashes.crashes().reporter(for: apiKey) else { return }
        // the library calls this block without looking when it cannot make the report: it must not be nil
        reporter.pluginExtension().reportUnhandledException(exception: details) { error in
            NSLog("AppMetrica did not take the Kotlin crash: %@", error.localizedDescription)
        }
    }
}
