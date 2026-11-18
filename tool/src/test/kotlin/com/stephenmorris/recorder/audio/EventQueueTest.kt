package com.stephenmorris.recorder.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventQueueTest {
    private val slot = VoiceEventQueue.Slot()

    /** The next trigger's voice, or null when empty. */
    private fun VoiceEventQueue.popVoice(): Int? = if (popInto(slot)) slot.voice else null

    @Test
    fun popsInPushOrder() {
        val queue = VoiceEventQueue(8)
        queue.pushTrigger(1, 0, 1f)
        queue.pushTrigger(2, 0, 1f)
        queue.pushTrigger(3, 0, 1f)
        assertEquals(1, queue.popVoice())
        assertEquals(2, queue.popVoice())
        assertEquals(3, queue.popVoice())
        assertEquals(null, queue.popVoice())
    }

    @Test
    fun refusesWhenFull() {
        val queue = VoiceEventQueue(4)
        assertTrue(queue.pushTrigger(1, 0, 1f))
        assertTrue(queue.pushTrigger(2, 0, 1f))
        assertTrue(queue.pushTrigger(3, 0, 1f))
        assertFalse(queue.pushTrigger(4, 0, 1f))
        assertEquals(1, queue.popVoice())
        assertTrue(queue.pushTrigger(4, 0, 1f))
    }

    @Test
    fun wrapsAround() {
        val queue = VoiceEventQueue(4)
        for (value in 0 until 20) {
            queue.pushTrigger(value, value.toLong(), 0.5f)
            assertTrue(queue.popInto(slot))
            assertEquals(VoiceEventQueue.TRIGGER, slot.kind)
            assertEquals(value, slot.voice)
            assertEquals(value.toLong(), slot.frame)
            assertEquals(0.5f, slot.gain)
        }
    }

    @Test
    fun carriesStopAll() {
        val queue = VoiceEventQueue(4)
        queue.pushStopAll()
        assertTrue(queue.popInto(slot))
        assertEquals(VoiceEventQueue.STOP_ALL, slot.kind)
    }

    @Test
    fun handsValuesBetweenThreadsInOrder() {
        val queue = VoiceEventQueue(64)
        val count = 100_000
        val producer = Thread {
            var next = 0
            while (next < count) {
                if (queue.pushTrigger(next, 0, 1f)) {
                    next += 1
                }
            }
        }
        val received = IntArray(count)
        val consumer = Thread {
            val slot = VoiceEventQueue.Slot()
            var index = 0
            while (index < count) {
                if (queue.popInto(slot)) {
                    received[index++] = slot.voice
                }
            }
        }
        producer.start()
        consumer.start()
        producer.join()
        consumer.join()
        assertEquals((0 until count).toList(), received.toList())
    }
}
