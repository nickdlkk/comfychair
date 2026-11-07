package sh.hnet.comfychair.util

import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

/** Only explicit, value-free diagnostic events are transmitted. Never forwards arbitrary app logs. */
class RealtimeDiagnostics(private val journal: File, autoStart: Boolean = true) {
    private val queue = ArrayDeque<String>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "comfychair-diagnostics").apply { isDaemon = true }
    }
    private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    private var target: String? = null
    private var enabled = false
    private var foreground = false
    private var generation = 0L
    private var nextAttempt = 0L
    private var failures = 0
    private var dropped = 0L
    @Volatile private var activeCall: okhttp3.Call? = null
    private val pendingJournal = ArrayDeque<String>()

    init { if (autoStart) executor.scheduleWithFixedDelay({ runCatching { tick() } }, 1, 1, TimeUnit.SECONDS) }

    @Synchronized fun configure(capture: Boolean, upload: Boolean, url: String) {
        val next = if (upload && capture && validTarget(url)) url.trim() else null
        if (enabled == capture && target == next) return
        generation++
        activeCall?.cancel()
        enabled = capture
        target = next
        queue.clear() // Never send an old target's events to a new target.
        pendingJournal.clear()
        nextAttempt = 0; failures = 0
    }

    @Synchronized fun foreground(value: Boolean) {
        foreground = value
        if (!value) activeCall?.cancel()
    }

    @Synchronized fun record(event: String) {
        if (!enabled) return
        if (queue.size == 256) { queue.removeFirst(); dropped++ }
        val line = "${System.currentTimeMillis()} ${event.take(1024)}"
        queue.addLast(line)
        if (pendingJournal.size == 256) pendingJournal.removeFirst()
        pendingJournal.addLast(line)
    }

    /** Bounded synchronous local write only on fatal exception; delegates to previous handler afterward. */
    @Synchronized fun fatal(error: Throwable) {
        if (!enabled) return
        val text = buildString {
            append("fatal "); append(error.javaClass.simpleName)
            error.stackTrace.take(16).forEach { append("\n at "); append(it.className); append('.'); append(it.methodName); append(':'); append(it.lineNumber) }
        }
        runCatching {
            while (pendingJournal.isNotEmpty()) appendJournal(pendingJournal.removeFirst())
            appendJournal(text)
        }
    }

    @Synchronized fun pendingCount() = queue.size

    private fun appendJournal(text: String) {
        journal.parentFile?.mkdirs()
        if (journal.length() + text.toByteArray().size > 128 * 1024) {
            val previous = File(journal.parentFile, journal.name + ".previous")
            previous.delete()
            journal.renameTo(previous)
        }
        journal.appendText(text + "\n")
    }

    internal fun close() { activeCall?.cancel(); executor.shutdownNow() }

    internal fun tick() {
        val snapshot: List<String>
        val url: String?
        val token: Long
        val lost: Long
        synchronized(this) {
            if (!enabled || queue.isEmpty()) return
            snapshot = queue.take(32)
            token = generation; url = target; lost = dropped
            // Persist on the IO worker before attempting network, including offline events.
            while (pendingJournal.isNotEmpty()) appendJournal(pendingJournal.removeFirst())
            if (url == null) { repeat(snapshot.size) { queue.removeFirst() }; return }
            if (!foreground || System.nanoTime() < nextAttempt) return
        }
        val body = JSONObject().put("app", "ComfyChair").put("schema", 1)
            .put("dropped", lost).put("events", JSONArray(snapshot)).toString()
        val call = client.newCall(Request.Builder().url(url!!)
            .post(body.toRequestBody("application/json".toMediaType())).build())
        synchronized(this) {
            if (token != generation || !enabled || !foreground) return
            activeCall = call
        }
        val success = runCatching { call.execute().use { it.isSuccessful } }.getOrDefault(false)
        synchronized(this) {
            activeCall = null
            if (token != generation) return
            if (success) {
                // Queue may have dropped its oldest events while network was pending.
                snapshot.forEach { queue.remove(it) }
                failures = 0; nextAttempt = 0
            } else {
                failures = (failures + 1).coerceAtMost(6)
                nextAttempt = System.nanoTime() + TimeUnit.SECONDS.toNanos((1L shl failures).coerceAtMost(60))
            }
        }
    }

    companion object {
        fun validTarget(value: String): Boolean {
            val url = value.trim().toHttpUrlOrNull() ?: return false
            return url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
                url.encodedPath == "/events"
        }
    }
}
