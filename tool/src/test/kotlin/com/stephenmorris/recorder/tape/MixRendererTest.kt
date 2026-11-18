package com.stephenmorris.recorder.tape

import com.stephenmorris.recorder.audio.Voice
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MixRendererTest {
    private val frames = 256

    /** One render cycle. */
    private fun render(mix: MixRenderer): Pair<FloatArray, FloatArray> {
        val left = FloatArray(frames)
        val right = FloatArray(frames)
        mix.render(left, right, frames)
        return left to right
    }

    private fun mixer(voices: List<Voice> = emptyList()) = MixRenderer(sampleRate = 48_000.0, voices = voices, maxFrames = 1_024)

    @Test
    fun drumsSoundAtTheirFrame() {
        val mix = mixer(listOf(Voice(FloatArray(64) { 0.25f })))

        mix.trigger(bus = 0, voice = 0, frame = 10, gain = 1f)
        val (left, right) = render(mix)

        assertEquals(0f, left[9])
        assertTrue(left[10] > 0)
        assertEquals(left[10], right[10])
    }

    @Test
    fun clipsPlayAtTheirFrames() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(100) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 0, startFrame = 20, frameCount = 100))
        val (left, _) = render(mix)

        assertEquals(0f, left[19])
        assertEquals(0.5f, left[20])
        assertEquals(0.5f, left[119])
        assertEquals(0f, left[120])
    }

    @Test
    fun aClipCarriesOnIntoTheNextCycle() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(1_000) { it / 2_000f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 100, startFrame = 0, frameCount = 500))
        render(mix)
        val (second, _) = render(mix)

        assertEquals((100 + 256) / 2_000f, second[0])
    }

    @Test
    fun panSplitsChannels() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(100) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 0f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 100))
        val (left, right) = render(mix)

        assertEquals(0.5f, left[0])
        assertEquals(0f, right[0])
    }

    @Test
    fun stopClipsSilencesThem() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(10_000) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 10_000))
        render(mix)
        mix.send(MixEvent.StopClips)
        val (left, _) = render(mix)

        assertTrue(left.all { it == 0f })
    }

    @Test
    fun limiterHoldsTheCeiling() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(200) { 1f }))

        for (slot in 0 until Song.TRACK_COUNT) {
            mix.send(MixEvent.SetTrack(slot = slot, gainLeft = 1f, gainRight = 1f))
            mix.send(MixEvent.PlayClip(slot = slot, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 200))
        }
        val (left, _) = render(mix)

        assertTrue(left.all { abs(it) <= 0.99f })
    }

    @Test
    fun clipsAreSilentWhileBeingRecordedOver() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(256) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 256))
        mix.send(MixEvent.SilenceTrack(slot = 0, fromFrame = 100, toFrame = 200))
        val (left, _) = render(mix)

        assertEquals(0.5f, left[99])
        assertEquals(0f, left[100])
        assertEquals(0f, left[199])
        assertEquals(0.5f, left[200])
    }

    @Test
    fun drumsFollowTheirPan() {
        val voice = Voice(FloatArray(64) { 0.25f })
        val right = mixer(listOf(voice))
        right.send(MixEvent.SetDrums(bus = 0, gainLeft = 0f, gainRight = 1f))
        right.trigger(bus = 0, voice = 0, frame = 0, gain = 1f)
        val panned = render(right)

        assertTrue(panned.first.all { it == 0f })
        assertTrue(panned.second.any { it > 0 })

        val centre = mixer(listOf(voice))
        centre.send(MixEvent.SetDrums(bus = 0, gainLeft = 0.7f, gainRight = 0.7f))
        centre.trigger(bus = 0, voice = 0, frame = 0, gain = 1f)
        val centred = render(centre)

        assertTrue(centred.first.any { it > 0 })
        assertContentEquals(centred.first, centred.second)
    }

    @Test
    fun drumBusesHaveTheirOwnGains() {
        val voice = Voice(floatArrayOf(1f))
        val first = MixRenderer(sampleRate = 48_000.0, voices = listOf(voice), maxFrames = 1_024, limits = false)
        val second = MixRenderer(sampleRate = 48_000.0, voices = listOf(voice), maxFrames = 1_024, limits = false)
        for (mix in listOf(first, second)) {
            mix.send(MixEvent.SetDrums(bus = 1, gainLeft = 0.5f, gainRight = 0f))
            mix.send(MixEvent.SetDrums(bus = 2, gainLeft = 0f, gainRight = 1f))
        }

        first.trigger(bus = 1, voice = 0, frame = 0, gain = 1f)
        second.trigger(bus = 2, voice = 0, frame = 0, gain = 1f)
        val onFirst = render(first)
        val onSecond = render(second)

        assertEquals(0.5f, onFirst.first[0])
        assertEquals(0f, onFirst.second[0])
        assertEquals(0f, onSecond.first[0])
        assertEquals(1f, onSecond.second[0])
    }

    @Test
    fun aTrackReportsWhatItPlaysAtItsGain() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(100) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 1, gainLeft = 0.25f, gainRight = 0.5f))
        mix.send(MixEvent.PlayClip(slot = 1, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 100))
        render(mix)

        assertEquals(0.25f, mix.takeTrackPeak(1))
        assertEquals(0f, mix.takeTrackPeak(1))
        assertEquals(0f, mix.takeTrackPeak(0))
    }

    @Test
    fun aTrackReportsItsHitsAtTheirBusGain() {
        val mix = mixer(listOf(Voice(FloatArray(64) { 0.5f })))

        mix.send(MixEvent.SetDrums(bus = 2, gainLeft = 0.5f, gainRight = 0.5f))
        mix.trigger(bus = 2, voice = 0, frame = 0, gain = 1f)
        render(mix)

        assertTrue(mix.takeTrackPeak(2) > 0)
        assertEquals(0f, mix.takeTrackPeak(3))
    }

    @Test
    fun theUnityBusIsNoTracks() {
        val mix = mixer(listOf(Voice(FloatArray(64) { 0.5f })))

        mix.trigger(bus = MixRenderer.UNITY_BUS, voice = 0, frame = 0, gain = 1f)
        render(mix)

        for (slot in 0 until MixRenderer.SLOTS) {
            assertEquals(0f, mix.takeTrackPeak(slot))
        }
    }

    // New: Review Focus 5.
    @Test
    fun aClipShorterThanItsSpanPlaysSilenceAfterItsEnd() {
        val mix = mixer()
        val clip = assertNotNull(mix.clips.add(FloatArray(50) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 100))
        val (left, _) = render(mix)

        assertEquals(0.5f, left[49])
        assertTrue((50 until frames).all { left[it] == 0f })
    }

    @Test
    fun aClipThatIsNotInTheStorePlaysSilence() {
        val mix = mixer()

        mix.send(MixEvent.SetTrack(slot = 0, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 0, clip = 7, sourceFrame = 0, startFrame = 0, frameCount = 100))
        mix.send(MixEvent.PlayClip(slot = 0, clip = ClipStore.CAPACITY + 1, sourceFrame = 0, startFrame = 0, frameCount = 100))
        mix.send(MixEvent.PlayClip(slot = 0, clip = -1, sourceFrame = 0, startFrame = 0, frameCount = 100))
        val (left, _) = render(mix)

        assertTrue(left.all { it == 0f })
    }

    @Test
    fun aFullStoreTakesNoMoreClips() {
        val store = ClipStore()
        repeat(ClipStore.CAPACITY) { assertEquals(it, store.add(FloatArray(1))) }

        assertNull(store.add(FloatArray(1)))
    }

    @Test
    fun anEventForASlotOrBusThatIsNotThereIsIgnored() {
        val mix = mixer(listOf(Voice(FloatArray(64) { 0.5f })))
        val clip = assertNotNull(mix.clips.add(FloatArray(100) { 0.5f }))

        mix.send(MixEvent.SetTrack(slot = 9, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.PlayClip(slot = 9, clip = clip, sourceFrame = 0, startFrame = 0, frameCount = 100))
        mix.send(MixEvent.SetDrums(bus = 9, gainLeft = 1f, gainRight = 1f))
        mix.send(MixEvent.SilenceTrack(slot = 9, fromFrame = 0, toFrame = 10))
        mix.trigger(bus = 9, voice = 0, frame = 0, gain = 1f)
        val (left, _) = render(mix)

        assertTrue(left.all { it == 0f })
        assertEquals(0f, mix.takeTrackPeak(9))
    }

    @Test
    fun theClockCountsRenderedFrames() {
        val mix = mixer()
        render(mix)
        render(mix)

        assertEquals(512L, mix.renderedFrames)
    }
}
