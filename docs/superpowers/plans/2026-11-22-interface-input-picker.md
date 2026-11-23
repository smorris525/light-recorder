# Interface Input Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Mic/Line track picks which input of a USB interface it records from (Input 1 … Input N), saved per track,
falling back to Input 1 or the phone's mic, and saying so, when that input isn't there.

**Architecture:** The mic opens every channel of a multi-input interface (channel index mask) and the reader keeps the
armed track's channel before the ring, so everything downstream stays mono. `InterfaceRoute` learns the input count from
the routed device; pure functions in `tape/InputChoice.kt` turn (track, count) into the Input page's rows and labels.

**Tech Stack:** Kotlin, Jetpack Compose, Light SDK, `android.media.AudioRecord`, kotlinx.serialization, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-11-22-interface-input-picker-design.md` (and `tool/docs/spec.md` §1, §4.2, §9, §19)

## Global Constraints

- No `Context`, `AudioManager` or `getSystemService()` (the SDK plugin rejects it). Learn everything from `AudioRecord`.
- Without a `TYPE_USB_DEVICE` input, behaviour is exactly as today: mono, `Mic/Line`, no picker.
- `Track.channel` is 1-based, defaults to 1, saved as `"channel"`; a song without it reads as 1.
- An interface reporting an empty `channelCounts` counts as 2 inputs.
- Labels, verbatim: `Input 1` … `Input N`, `Mic/Line`, `Drums`, fallbacks `Input 4 → Input 1` and `Input 2 → Mic/Line`.
- 4-space indentation; commit messages are plain sentences like the
  existing log ("Song and Track screens: a jack and a monitor hint while an interface is in use"), with the
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` trailer.
- Tests: `./gradlew :tool:testDebugUnitTest`; build: `./gradlew :tool:assembleDebug`.

## Review Focus

- **Unplugging the interface while a multi-channel track is armed:** the mic must reopen mono on the phone's mic, not
  stay dead or crash. Pinned in Task 4 (`aMicThatDiesWhileArmedReopens`) and by Task 6's device check.
- **A 4-channel mask Android refuses:** must fall back to mono and report 1 input, still recording. Task 3's open logic,
  checked on device in Task 6.
- **Old songs and iOS songs** without `"channel"`: open on Input 1, and saving a channel-1 track writes no key. Task 1.
- **Probe while a take is under way, or with permission refused:** must do nothing (no reopen, no "microphone not
  allowed"). Task 4 tests.
- **Picking Input 2 on an armed track:** stays armed, and the next take records channel 2. Task 4 tests.

---

### Task 1: The track's channel, saved

**Files:**
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/tape/Track.kt`
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/tape/SongCoding.kt` (`TrackDto`, `Track.dto()`, `JsonObject.track()`)
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/tape/SongCodingTest.kt`

**Interfaces:**
- Produces: `Track.channel: Int = 1` (doc: "Which of an interface's inputs a Mic/Line track records from, from 1.").

- [ ] **Step 1: Write the failing tests** in `SongCodingTest`:
  - `aTracksChannelComesBack`: a song whose Track 2 has `channel = 3` round-trips through `SongCoding.encode`/`decode`
    with `channel == 3`.
  - `aTrackSavedWithoutAChannelIsOnInput1`: `decodeOne` of a track JSON with no `"channel"` → `channel == 1`; with
    `"channel": 0` or `-2` → 1.
  - `channel1IsNotWritten`: encoding a new song's tracks produces JSON with no `"channel"` key.
- [ ] **Step 2: Run** `./gradlew :tool:testDebugUnitTest --tests '*SongCodingTest'` → FAIL (no `channel`).
- [ ] **Step 3: Implement.** Add `channel` to `Track`; `TrackDto.channel: Int? = null`, written as
  `channel.takeIf { it != 1 }`; read as `whole("channel")?.takeIf { it >= 1 } ?: 1`.
- [ ] **Step 4: Run** the same command → PASS; then the whole suite → PASS.
- [ ] **Step 5: Commit** "A track remembers which interface input it records from".

### Task 2: Input count, choices and labels

**Files:**
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/engine/InterfaceRoute.kt`
- Create: `tool/src/main/kotlin/com/stephenmorris/recorder/tape/InputChoice.kt`
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/engine/InterfaceRouteTest.kt`
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/tape/InputChoiceTest.kt`

**Interfaces:**
- Consumes: `Track.channel` (Task 1).
- Produces:
  - `InterfaceRoute.inputRouted(kind: RouteKind?, channels: Int = 1)` (existing calls keep compiling).
  - `InterfaceRoute.inputCount: StateFlow<Int>`: 1 without an interface; with one, the last `channels` reported for a
    `USB_DEVICE` input this session, or 2 if none yet. Remembered after the input closes, while the output still says
    interface.
  - `data class InputChoice(val input: TrackInput, val channel: Int = 1)`.
  - `fun inputChoices(inputCount: Int): List<InputChoice>` and `fun InputChoice.label(inputCount: Int): String`.
  - `fun recordedChannel(channel: Int, inputCount: Int): Int` → `channel` if `channel <= inputCount`, else 1.
  - `fun Track.inputLabel(inputCount: Int): String`.
  - `fun Track.isChoice(choice: InputChoice, inputCount: Int): Boolean` (the underline).

- [ ] **Step 1: Write the failing tests.**
  - `InterfaceRouteTest.inputCountIs1WithoutAnInterface`: fresh route → 1; `inputRouted(BUILT_IN, 1)` → 1.
  - `InterfaceRouteTest.anInterfaceInputReportsItsChannels`: `inputRouted(USB_DEVICE, 4)` → 4; then
    `inputRouted(null)` with `outputRouted(USB_DEVICE)` → still 4; `outputRouted(BUILT_IN)` → 1.
  - `InterfaceRouteTest.anInterfaceNotYetOpenedCountsAs2`: `outputRouted(USB_DEVICE)` only → 2.
  - `InputChoiceTest.oneInputOrNoneIsMicLineAndDrums`: `inputChoices(1)` labels `["Mic/Line", "Drums"]`.
  - `InputChoiceTest.fourInputsAreListed`: `inputChoices(4)` labels `["Input 1", "Input 2", "Input 3", "Input 4", "Drums"]`.
  - `InputChoiceTest.labels`: Mic track channel 2, count 2 → `"Input 2"`; channel 1, count 1 → `"Mic/Line"`; channel 4,
    count 2 → `"Input 4 → Input 1"`; channel 2, count 1 → `"Input 2 → Mic/Line"`; Drums track → `"Drums"`.
  - `InputChoiceTest.recordedChannelFallsBackTo1`: (2, 4) → 2; (4, 2) → 1; (3, 1) → 1.
  - `InputChoiceTest.underlineIsWhatRecords`: channel 4, count 2 → only `Input 1` underlined; channel 2, count 1 →
    `Mic/Line` underlined; Drums track → only `Drums`.
- [ ] **Step 2: Run** `./gradlew :tool:testDebugUnitTest --tests '*InterfaceRouteTest' --tests '*InputChoiceTest'` → FAIL.
- [ ] **Step 3: Implement.** `inputCount` is updated in `InterfaceRoute.update()` alongside `hasInterface`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** "Interface inputs: a count from the route, and the Input page's choices and labels".

### Task 3: The mic opens every input and keeps one

**Files:**
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/engine/AudioPorts.kt` (`AudioInput`)
- Create: `tool/src/main/kotlin/com/stephenmorris/recorder/engine/Deinterleave.kt`
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/platform/MicInput.kt`
- Modify: `tool/src/test/kotlin/com/stephenmorris/recorder/engine/Fakes.kt` (`FakeInput`)
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/engine/DeinterleaveTest.kt`

**Interfaces:**
- Consumes: `InterfaceRoute.inputRouted(kind, channels)`, `recordedChannel` (Task 2).
- Produces:
  - `AudioInput.channel: Int` (`var`, 1-based, "Which input to keep, on an interface with more than one; set before or
    while open."). `FakeInput` just stores it.
  - `fun deinterleave(source: ShortArray, frames: Int, channels: Int, channel: Int, into: FloatArray)`: writes
    `frames` samples of 1-based `channel`, scaled by `1 / 32_768f`.

- [ ] **Step 1: Write the failing tests** in `DeinterleaveTest`:
  - `monoIsCopiedScaled`: `[16384, -32768]`, channels 1 → `[0.5f, -1f]`.
  - `theChosenChannelIsKept`: 4 channels, 2 frames `[1,2,3,4, 5,6,7,8]` (×1000), channel 3 → `[3000, 7000] / 32768f`.
- [ ] **Step 2: Run** `./gradlew :tool:testDebugUnitTest --tests '*DeinterleaveTest'` → FAIL.
- [ ] **Step 3: Implement `deinterleave`**, and `AudioInput.channel` with `FakeInput` storing it.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Change `MicInput`.**
  - `@Volatile override var channel = 1`; a private `channels` (1 while mono).
  - `open()`: build mono as today and start it. If `routedDevice?.type == TYPE_USB_DEVICE`, take
    `n = routedDevice.channelCounts.maxOrNull() ?: 2`. If `n > 1`, discard the mono recorder and build with
    `AudioRecord.Builder().setAudioSource(MIC).setAudioFormat(AudioFormat.Builder().setEncoding(PCM_16BIT)
    .setSampleRate(sampleRate).setChannelIndexMask((1 shl n) - 1).build())`, buffer
    `maxOf(minimum * n, READ_FRAMES * 4 * n * 2)`, then `setPreferredDevice(device)`. If it won't initialise or start,
    log it and reopen mono with `channels = 1`. Log the outcome (`"input: n channels from <productName>"`).
  - `reportRoute`: `route.inputRouted(kind, channels)`. When the route changes so that the open shape is wrong
    (multi-channel but no longer `USB_DEVICE`, or mono but now a `USB_DEVICE` with more than one input), set
    `failed = true`, so the transport closes and reopens it (`updateAudio` already does).
  - `readLoop`: read `READ_FRAMES * channels` shorts, then `deinterleave(..., recordedChannel(channel, channels), ...)`
    into `floats` before `meter.record` and `ring.write`. Size the buffers once per open.
- [ ] **Step 6: Run** the whole suite and `./gradlew :tool:assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** "Mic opens every input of an interface and keeps the armed track's".

### Task 4: Transport and editing use the channel; the Input page probe

**Files:**
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/engine/Transport.kt` (`updateAudio`, new `countInputs`)
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/engine/SongEditing.kt` (`setInput`)
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/engine/TransportPlayTest.kt`
- Test: `tool/src/test/kotlin/com/stephenmorris/recorder/engine/SongEditingTest.kt`

**Interfaces:**
- Consumes: `AudioInput.channel` (Task 3), `Track.channel` (Task 1).
- Produces:
  - `SongEditing.setInput(input: TrackInput, channel: Int = 1, on: UUID)`. Disarms only when `input` changes; the
    channel is saved only for `MIC`.
  - `Transport.countInputs()`: opens and closes the mic once to learn the count, only when the input is closed, no take
    or pattern take is under way, the transport isn't paused and a song is open. It never touches `isMicRefused`.

- [ ] **Step 1: Write the failing tests.**
  - `SongEditingTest.pickingAnotherInputKeepsATrackArmed`: arm a Mic/Line track, `setInput(MIC, 2, on)` → still armed,
    `channel == 2`.
  - `SongEditingTest.pickingDrumsStillDisarms`: armed Mic/Line track, `setInput(DRUMS, on = id)` → disarmed.
  - `TransportPlayTest.theArmedTracksChannelReachesTheMic`: Track 2 on `channel = 3`, armed, `prepare()` →
    `input.channel == 3`.
  - `TransportPlayTest.aMicThatDiesWhileArmedReopens`: armed and open, then the fake reads as closed without
    `close()` (as `MicInput` does after `failed = true`), `prepare()` → `opens` up by one and `input.isOpen`.
  - `TransportPlayTest.countingInputsOpensAndClosesAClosedMic`: nothing armed, `countInputs()` → `opens == 1`,
    `closes == 1`, `!input.isOpen`.
  - `TransportPlayTest.countingInputsLeavesAnOpenMicAlone`: armed (open), `countInputs()` → `opens` unchanged.
  - `TransportPlayTest.countingInputsWithoutPermissionChangesNothing`: `granted = false`, `countInputs()` →
    `isMicRefused` false.
  - `TransportPlayTest.countingInputsDuringATakeDoesNothing`: take under way → `opens` unchanged.
- [ ] **Step 2: Run** `./gradlew :tool:testDebugUnitTest --tests '*SongEditingTest' --tests '*TransportPlayTest'` → FAIL.
- [ ] **Step 3: Implement.** In `updateAudio`, set `input.channel` from the armed audio track's `channel` (1 when
  none) before opening. `countInputs` as in Interfaces.
- [ ] **Step 4: Run** the whole suite → PASS.
- [ ] **Step 5: Commit** "Armed track's input reaches the mic; the Input page can count an interface's inputs".

### Task 5: Input page, Track screen and the spec

**Files:**
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/track/InputScreen.kt`
- Modify: `tool/src/main/kotlin/com/stephenmorris/recorder/track/TrackScreen.kt`
- Modify: `tool/docs/spec.md` (§1 "Left out", §4.2 "Which mic", §9 Track and Input, §19)

**Interfaces:**
- Consumes: `inputChoices`, `InputChoice.label`, `Track.inputLabel`, `Track.isChoice`, `InterfaceRoute.inputCount`
  (Task 2); `SongEditing.setInput(input, channel, on)`, `Transport.countInputs()` (Task 4).

- [ ] **Step 1: Input page.** Collect `ready.route.inputCount`; options are `inputChoices(count)` labelled and
  underlined with `isChoice`; a pick calls `setInput(choice.input, choice.channel, on = trackId)`. In a
  `LaunchedEffect(Unit)`, call `transport.countInputs()` when `route.hasInterface.value`.
- [ ] **Step 2: Track screen.** The Input row's value is `track.inputLabel(inputCount)` unless `microphone not allowed`
  applies, as now.
- [ ] **Step 3: Spec.** §1: "Choosing the input" becomes choosing the *device* only; the channel is chosen per track.
  §4.2: the multi-channel open, the fallback to mono, the reopen on route change, `inputCount`. §9: the Input page's
  rows (both forms), the labels and fallbacks verbatim from Global Constraints, the probe, and that switching inputs
  within Mic/Line doesn't disarm. §19: replace "which channel a mono `AudioRecord` gets" with whether LightOS opens a
  multi-channel index mask, and what `channelCounts` interfaces report.
- [ ] **Step 4: Run** the whole suite and `./gradlew :tool:assembleDebug` → PASS / BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** "Input page lists an interface's inputs; Track screen names the one recording".

### Task 6: Verify on the emulator and the device

- [ ] **Step 1: Emulator** (start the AVD with `-no-audio`): install, open a song. Track 2's Input shows `Mic/Line`,
  `Drums`; arming and recording a take still work. No interface, so no picker: this is the regression check.
- [ ] **Step 2: Device with the user's 2-input interface** (the user runs it): Input page shows `Input 1`, `Input 2`,
  `Drums`. Record on Input 2 with a signal only on input 2 and hear it back. Check `adb logcat -s RecorderAudio` for
  the channel count. Unplug while armed: the row reads `Input 2 → Mic/Line` and the meter follows the phone's mic.
- [ ] **Step 3:** Report what the device logged and did. Note in §19 anything learned (e.g. if the index mask was
  refused).
