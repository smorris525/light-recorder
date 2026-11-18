package com.stephenmorris.recorder.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VoiceRendererTest {
    private val ones = FloatArray(100) { 1f }
    private val zeros = FloatArray(100)

    private fun render(renderer: VoiceRenderer, frameCount: Int): List<Float> {
        val output = FloatArray(frameCount) { Float.NaN }
        renderer.render(frameCount, output)
        return output.toList()
    }

    private fun renderer(vararg voices: Voice) = VoiceRenderer(voices.toList(), fadeFrames = 4, gain = 1f)

    private fun trigger(voice: Int, frame: Long, gain: Float = 1f) = VoiceEvent.Trigger(voice, frame, gain)

    private fun floats(vararg values: Number) = values.map { it.toFloat() }

    @Test
    fun silentWhenIdleAndAdvancesTheClock() {
        val renderer = renderer(Voice(ones))
        assertEquals(List(8) { 0f }, render(renderer, 8))
        assertEquals(8L, renderer.renderedFrames)
    }

    @Test
    fun startsAVoiceAtItsFrame() {
        val renderer = renderer(Voice(floatArrayOf(1f, 2f, 3f)))
        renderer.send(trigger(0, 3))
        assertEquals(floats(0, 0, 0, 1, 2, 3, 0, 0), render(renderer, 8))
    }

    @Test
    fun continuesAcrossRenders() {
        val renderer = renderer(Voice(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f)))
        renderer.send(trigger(0, 6))
        assertEquals(floats(0, 0, 0, 0, 0, 0, 1, 2), render(renderer, 8))
        assertEquals(floats(3, 4, 5, 6, 0, 0, 0, 0), render(renderer, 8))
    }

    @Test
    fun waitsForAFutureFrame() {
        val renderer = renderer(Voice(floatArrayOf(1f)))
        renderer.send(trigger(0, 10))
        assertEquals(List(8) { 0f }, render(renderer, 8))
        assertEquals(floats(0, 0, 1, 0, 0, 0, 0, 0), render(renderer, 8))
    }

    @Test
    fun playsALateTriggerAtOnce() {
        val renderer = renderer(Voice(floatArrayOf(1f, 2f, 3f)))
        render(renderer, 8)
        renderer.send(trigger(0, 3))
        assertEquals(floats(1, 2, 3, 0, 0, 0, 0, 0), render(renderer, 8))
    }

    @Test
    fun sortsTriggersSentOutOfOrder() {
        val renderer = renderer(Voice(floatArrayOf(1f)), Voice(floatArrayOf(2f)))
        renderer.send(trigger(1, 5))
        renderer.send(trigger(0, 2))
        assertEquals(floats(0, 0, 1, 0, 0, 2, 0, 0), render(renderer, 8))
    }

    @Test
    fun retriggerFadesThePreviousSound() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0))
        renderer.send(trigger(0, 2))
        assertEquals(floats(1, 1, 2, 1.75, 1.5, 1.25, 1, 1), render(renderer, 8))
    }

    @Test
    fun chokeFadesTheChokedVoice() {
        val renderer = renderer(Voice(ones), Voice(zeros, intArrayOf(0)))
        renderer.send(trigger(0, 0))
        renderer.send(trigger(1, 2))
        assertEquals(floats(1, 1, 1, 0.75, 0.5, 0.25, 0, 0), render(renderer, 8))
    }

    @Test
    fun stopAllFadesVoicesAndDropsEarlierTriggers() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0))
        renderer.send(trigger(0, 50))
        assertEquals(floats(1, 1), render(renderer, 2))
        renderer.send(VoiceEvent.StopAll)
        assertEquals(floats(1, 0.75, 0.5, 0.25, 0, 0, 0, 0), render(renderer, 8))
        assertEquals(List(64) { 0f }, render(renderer, 64))
    }

    // Stopping one pattern and playing another in the same turn keeps the new hits.
    @Test
    fun triggersSentAfterStopAllStillPlay() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0))
        render(renderer, 4)
        renderer.send(VoiceEvent.StopAll)
        renderer.send(trigger(0, 6))
        assertEquals(floats(1, 0.75, 1.5, 1.25, 1, 1, 1, 1), render(renderer, 8))
    }

    @Test
    fun appliesGain() {
        val renderer = VoiceRenderer(listOf(Voice(floatArrayOf(1f, 1f))), fadeFrames = 4, gain = 0.5f)
        renderer.send(trigger(0, 0))
        assertEquals(floats(0.5, 0.5, 0), render(renderer, 3))
    }

    @Test
    fun ignoresUnknownVoices() {
        val renderer = renderer(Voice(floatArrayOf(1f)))
        renderer.send(trigger(7, 0))
        assertEquals(floats(0, 0), render(renderer, 2))
    }

    // A drum roll restarts a sound faster than its fade. The earlier fade must keep going, or the output steps down.
    @Test
    fun aRetriggerDuringAFadeKeepsTheEarlierFade() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0))
        renderer.send(trigger(0, 2))
        renderer.send(trigger(0, 3))
        // At frame 3: the new sound (1), the first sound's fade (3 of 4 frames left) and the second's (4 of 4).
        assertEquals(1f + 0.75f + 1f, render(renderer, 6)[3])
    }

    @Test
    fun playsATriggerAtItsGain() {
        val renderer = renderer(Voice(floatArrayOf(1f, 1f, 1f)))
        renderer.send(trigger(0, 1, 0.5f))
        assertEquals(floats(0, 0.5, 0.5, 0.5, 0), render(renderer, 5))
    }

    @Test
    fun gainDefaultsToFull() {
        val renderer = VoiceRenderer(listOf(Voice(floatArrayOf(1f))), fadeFrames = 4, gain = 0.5f)
        renderer.send(VoiceEvent.Trigger(0, 0))
        assertEquals(floats(0.5, 0), render(renderer, 2))
    }

    // A retrigger at a quieter level doesn't drop the fading tail to that level.
    @Test
    fun aFadingSoundKeepsItsOwnGain() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0, 1f))
        renderer.send(trigger(0, 2, 0.5f))
        assertEquals(floats(1, 1, 1.5, 1.25, 1, 0.75, 0.5, 0.5), render(renderer, 8))
    }

    @Test
    fun runsTheMixThroughTheCompressor() {
        val renderer = VoiceRenderer(
            listOf(Voice(FloatArray(44_100) { 1f })),
            gain = 1f,
            compressor = Compressor(44_100.0),
        )
        renderer.send(trigger(0, 0))
        assertTrue(abs(render(renderer, 22_050).last() - 0.813f) < 0.01f)
    }

    // Two full-scale voices together would reach 2; the limiter, last in line, holds the mix to the ceiling.
    @Test
    fun runsTheMixThroughTheLimiterLast() {
        val renderer = VoiceRenderer(
            listOf(Voice(FloatArray(100) { 1f }), Voice(FloatArray(100) { 1f })),
            gain = 1f,
            compressor = Compressor(44_100.0),
            limiter = Limiter(sampleRate = 44_100.0),
        )
        renderer.send(trigger(0, 0))
        renderer.send(trigger(1, 0))
        val output = render(renderer, 100)
        assertTrue(output.all { abs(it) <= KitMix.CEILING })
        assertTrue(output.any { it > 0.9f })
    }

    // A stop sent when the queue is full must still stop everything sent before it.
    @Test
    fun stopAllIsNeverLostWhenTheQueueIsFull() {
        val renderer = renderer(Voice(ones))
        renderer.send(trigger(0, 0))
        render(renderer, 2)
        repeat(VoiceRenderer.PENDING_CAPACITY - 1) { renderer.send(trigger(0, 10L + it)) }
        renderer.send(VoiceEvent.StopAll)
        assertEquals(floats(1, 0.75, 0.5, 0.25, 0, 0, 0, 0), render(renderer, 8))
        assertEquals(List(2_000) { 0f }, render(renderer, 2_000))
    }

    // Catches gross per-block allocation on the audio thread.
    @Test
    fun renderAllocatesNothing() {
        val renderer = VoiceRenderer(
            DrumKit.voices(48_000.0),
            compressor = Compressor(48_000.0),
            limiter = Limiter(sampleRate = 48_000.0),
        )
        val output = FloatArray(256)
        val events = List(8) { VoiceEvent.Trigger(it, 0, 0.6f) }
        repeat(100) { renderer.render(256, output) }
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        repeat(10_000) { block ->
            if (block % 10 == 0) {
                renderer.send(events[block % 8])
            }
            renderer.render(256, output)
        }
        val after = runtime.totalMemory() - runtime.freeMemory()
        assertTrue(after - before < 1_000_000, "grew ${after - before} bytes")
    }
}
