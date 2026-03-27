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

    @Volatile
    private var sentCount: Int = 0

    @Volatile
    private var currentSettings: ProactiveSettings = ProactiveSettings()

    fun start(settings: ProactiveSettings, lastMessageTimestampMs: Long) {
        this.lastMessageTimestampMs = lastMessageTimestampMs
        this.currentSettings = settings
        stop()
        scheduleNext(settings)
        timerJob = scope.launch {
            // 立即检查一次，处理应用关闭期间已到期的情况
            val nowAtStart = System.currentTimeMillis()
            if (nowAtStart >= nextTriggerMs && sentCount < settings.maxCount) {
                val elapsed = nowAtStart - this@ProactiveMessagingController.lastMessageTimestampMs
                this@ProactiveMessagingController.lastMessageTimestampMs = nowAtStart
                sentCount++
                if (sentCount < settings.maxCount) {
                    scheduleNext(settings)
                } else {
                    nextTriggerMs = Long.MAX_VALUE
                }
                onTrigger(elapsed)
            }
            while (true) {
                delay(CHECK_INTERVAL_MS)
                if (sentCount >= settings.maxCount) break
                val now = System.currentTimeMillis()
                if (now >= nextTriggerMs) {
                    val elapsed = now - this@ProactiveMessagingController.lastMessageTimestampMs
                    this@ProactiveMessagingController.lastMessageTimestampMs = now
                    sentCount++
                    if (sentCount < settings.maxCount) {
                        scheduleNext(settings)
                    } else {
                        nextTriggerMs = Long.MAX_VALUE
                    }
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

    fun resetCount() {
        sentCount = 0
        scheduleNext(currentSettings)
    }

    fun updateLastMessageTimestamp(ms: Long) {
        lastMessageTimestampMs = ms
    }

    fun getNextTriggerMs(): Long = nextTriggerMs

    fun getSentCount(): Int = sentCount

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
