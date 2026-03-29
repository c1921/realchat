package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.model.ProactiveSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class ProactiveTriggerResult {
    SENT,
    PAUSE_UNTIL_USER_REPLY,
    RETRY_LATER
}

class ProactiveMessagingController(
    private val scope: CoroutineScope,
    private val onTrigger: suspend (elapsedMs: Long) -> ProactiveTriggerResult
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
        if (sentCount >= settings.maxCount) {
            nextTriggerMs = Long.MAX_VALUE
            return
        }
        scheduleNext(baseTimestampMs = this.lastMessageTimestampMs, settings = settings)
        timerJob = scope.launch {
            // 立即检查一次，处理应用关闭期间已到期的情况
            val nowAtStart = System.currentTimeMillis()
            if (nowAtStart >= nextTriggerMs && sentCount < settings.maxCount) {
                handleTrigger(nowMs = nowAtStart, settings = settings)
            }
            while (true) {
                delay(CHECK_INTERVAL_MS)
                if (sentCount >= settings.maxCount) break
                val now = System.currentTimeMillis()
                if (now >= nextTriggerMs) {
                    handleTrigger(nowMs = now, settings = settings)
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
        scheduleNext(baseTimestampMs = lastMessageTimestampMs, settings = currentSettings)
    }

    fun updateLastMessageTimestamp(ms: Long) {
        lastMessageTimestampMs = ms
    }

    fun getNextTriggerMs(): Long = nextTriggerMs

    fun getSentCount(): Int = sentCount

    fun isRunning(): Boolean = timerJob?.isActive == true

    private suspend fun handleTrigger(nowMs: Long, settings: ProactiveSettings) {
        val elapsed = nowMs - lastMessageTimestampMs
        when (onTrigger(elapsed)) {
            ProactiveTriggerResult.SENT -> {
                lastMessageTimestampMs = nowMs
                sentCount++
                if (sentCount < settings.maxCount) {
                    scheduleNext(baseTimestampMs = nowMs, settings = settings)
                } else {
                    nextTriggerMs = Long.MAX_VALUE
                }
            }

            ProactiveTriggerResult.PAUSE_UNTIL_USER_REPLY -> {
                sentCount = settings.maxCount
                nextTriggerMs = Long.MAX_VALUE
            }

            ProactiveTriggerResult.RETRY_LATER -> {
                scheduleNext(baseTimestampMs = nowMs, settings = settings)
            }
        }
    }

    private fun scheduleNext(baseTimestampMs: Long, settings: ProactiveSettings) {
        val minMs = settings.minIntervalMs
        val maxMs = maxOf(settings.maxIntervalMs, minMs)
        val intervalMs = if (maxMs > minMs) Random.nextLong(minMs, maxMs + 1) else minMs
        nextTriggerMs = baseTimestampMs + intervalMs
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L
    }
}
