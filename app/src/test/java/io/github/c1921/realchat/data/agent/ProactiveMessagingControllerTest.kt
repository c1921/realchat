package io.github.c1921.realchat.data.agent

import io.github.c1921.realchat.model.ProactiveSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProactiveMessagingControllerTest {
    @Test
    fun start_whenTriggerSendsMessage_incrementsCountAndSchedulesNextAttempt() = runTest {
        var calls = 0
        val controller = ProactiveMessagingController(
            scope = backgroundScope,
            onTrigger = {
                calls++
                ProactiveTriggerResult.SENT
            }
        )

        controller.start(
            settings = ProactiveSettings(
                enabled = true,
                minIntervalMinutes = 1,
                maxIntervalMinutes = 1,
                maxCount = 3
            ),
            lastMessageTimestampMs = System.currentTimeMillis() - 60_000L
        )
        runCurrent()

        assertEquals(1, calls)
        assertEquals(1, controller.getSentCount())
        assertTrue(controller.getNextTriggerMs() > System.currentTimeMillis())
    }

    @Test
    fun start_whenDirectorPauses_setsCountToMaxAndStopsScheduling() = runTest {
        var calls = 0
        val controller = ProactiveMessagingController(
            scope = backgroundScope,
            onTrigger = {
                calls++
                ProactiveTriggerResult.PAUSE_UNTIL_USER_REPLY
            }
        )

        controller.start(
            settings = ProactiveSettings(
                enabled = true,
                minIntervalMinutes = 0,
                maxIntervalMinutes = 0,
                maxCount = 3
            ),
            lastMessageTimestampMs = 0L
        )
        runCurrent()

        assertEquals(1, calls)
        assertEquals(3, controller.getSentCount())
        assertEquals(Long.MAX_VALUE, controller.getNextTriggerMs())
    }

    @Test
    fun start_whenTriggerNeedsRetry_keepsCountAndReschedulesFromNow() = runTest {
        var calls = 0
        val controller = ProactiveMessagingController(
            scope = backgroundScope,
            onTrigger = {
                calls++
                ProactiveTriggerResult.RETRY_LATER
            }
        )

        controller.start(
            settings = ProactiveSettings(
                enabled = true,
                minIntervalMinutes = 1,
                maxIntervalMinutes = 1,
                maxCount = 3
            ),
            lastMessageTimestampMs = System.currentTimeMillis() - 60_000L
        )
        runCurrent()

        assertEquals(1, calls)
        assertEquals(0, controller.getSentCount())
        assertTrue(controller.getNextTriggerMs() > System.currentTimeMillis())
    }
}
