package sh.hnet.comfychair

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.launch
import sh.hnet.comfychair.storage.AppSettings
import sh.hnet.comfychair.util.DebugLogger
import sh.hnet.comfychair.util.RealtimeDiagnostics
import java.io.File

class DiagnosticsApplication : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        private var instance: DiagnosticsApplication? = null
        fun refresh() { instance?.configure() }
        // Only value-free event names supplied by instrumentation, never exception messages/URLs.
        fun event(name: String) { instance?.reporter?.record(name) }
    }
    private lateinit var reporter: RealtimeDiagnostics
    private var started = 0
    override fun onCreate() {
        super.onCreate()
        instance = this
        reporter = RealtimeDiagnostics(File(filesDir, "diagnostics/recent.log"))
        configure()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            reporter.fatal(error)
            previous?.uncaughtException(thread, error)
        }
        registerActivityLifecycleCallbacks(this)
        event("process_start")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            sh.hnet.comfychair.connection.ConnectionManager.connectionState.collect { state ->
                event("connection_state " + state.javaClass.simpleName)
            }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launch {
            sh.hnet.comfychair.connection.ConnectionManager.webSocketState.collect { state ->
                event("websocket_state " + state.javaClass.simpleName)
            }
        }
    }
    private fun configure() {
        val capture = AppSettings.isDebugLoggingEnabled(this)
        DebugLogger.setEnabled(capture)
        reporter.configure(capture, AppSettings.isRealtimeLoggingEnabled(this), AppSettings.getRealtimeLogUrl(this))
    }
    override fun onActivityStarted(activity: Activity) { started++; reporter.foreground(true); event("activity_start " + activity.javaClass.simpleName) }
    override fun onActivityStopped(activity: Activity) { event("activity_stop " + activity.javaClass.simpleName); started = (started - 1).coerceAtLeast(0); reporter.foreground(started > 0) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) { event("activity_create " + activity.javaClass.simpleName + " restored=" + (state != null)) }
    override fun onActivityResumed(activity: Activity) { event("activity_resume " + activity.javaClass.simpleName) }
    override fun onActivityPaused(activity: Activity) { event("activity_pause " + activity.javaClass.simpleName) }
    override fun onActivityDestroyed(activity: Activity) { event("activity_destroy " + activity.javaClass.simpleName + " finishing=" + activity.isFinishing) }
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
}
