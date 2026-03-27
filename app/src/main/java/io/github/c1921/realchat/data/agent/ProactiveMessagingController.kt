package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.model.ProactiveSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class ProactiveMessagingController(
    private val scope: CoroutineScope,
    private val onTrigger: suspend (elapsedMs: Long) -> Unit
) {
    private var timerJob: Job? = null

    @Volatile
    private var lastMessageTimestampMs: Long = System.currentTimeMillis()

    @Volatile
    private var nextTriggerMs: Long = Long.MAX_VALUE

    fun start(settings: ProactiveSettings, lastMessageTimestampMs: Long) {
        this.lastMessageTimestampMs = lastMessageTimestampMs
        stop()
        scheduleNext(settings)
        timerJob = scope.launch {
            while (true) {
                delay(CHECK_INTERVAL_MS)
                val now = System.currentTimeMillis()
                if (now >= nextTriggerMs) {
                    val elapsed = now - this@ProactiveMessagingController.lastMessageTimestampMs
                    this@ProactiveMessagingController.lastMessageTimestampMs = now
                    scheduleNext(settings)
                    onTrigger(elapsed)
                }
            }
        }
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        nextTriggerMs = Long.MAX_VALUE
    }

    fun updateLastMessageTimestamp(ms: Long) {
        lastMessageTimestampMs = ms
    }

    fun getNextTriggerMs(): Long = nextTriggerMs

    private fun scheduleNext(settings: ProactiveSettings) {
        val minMs = settings.minIntervalMs
        val maxMs = maxOf(settings.maxIntervalMs, minMs)
        val intervalMs = if (maxMs > minMs) Random.nextLong(minMs, maxMs + 1) else minMs
        nextTriggerMs = lastMessageTimestampMs + intervalMs
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L
    }
}
