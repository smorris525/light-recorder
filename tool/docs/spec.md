# Recorder — Light Phone Tool Spec

Status: agreed design, v1, 2026-11-17. A four-track recorder with a drum machine. There's no cycle, the song screen is
four upright channel strips, and nothing is dimmed outside the in and out points. Where this spec is silent, the code and
its tests hold.

The drum machine's screens are the Drums tool's (`~/Documents/Apps/Light Phone/Drums/light-sdk`, its `tool/docs/spec.md`
is "Drums §n"), copied into this tool.

**Words:** the app says **song**, **track** (`Track 1`…`Track 4`), **pattern**, **take**, **bounce**, **in point** and
**out point**. Units are uppercase (`92 BPM`). Buttons are uppercase; everything else is sentence case. Sound names are
lowercase, as in Drums.

## 1. Scope

- **Device:** Light Phone III only. One portrait screen, 1080 × 1240 px (about 360 × 413 dp). No other layouts.
- **Look:** Light's own building blocks wherever they exist: `LightTheme`, the LightOS font and `LightTextVariant`s,
  `LightTopBar`, `LightBottomBar`, `LightBarButton`, `LightScrollView`, `LightTextInputEditor`,
  `LightEmbeddedLp3Keyboard`, `LightIcons`, `lightClickable`. Custom drawing only where the SDK has nothing for the job:
  the channel strips (fader, pan, meter), the ruler, the lane, a few icons (the drum, the in and out brackets, and
  Drums' metronome, step grid and count-in), the choice underline, and Drums' musical parts (step dots, pads, keypad).
  LightOS conventions throughout.
- **Animation:** none. Every change is an instant swap.
- **Local only.** No network, no accounts, no sync, no analytics. Files leave the phone only when the user downloads an
  export through Tool Manager.
- **Foreground only.** Audio stops when the tool leaves the screen.
- **Typography:** the SDK's variants (`copy`, `detail`, `paragraph`,
  `superfine`, `button`, …). Sizes are adjusted on the emulator afterwards, not up front.

### Left out

- **Monitor** (hearing the mic live). Kotlin-only audio can't get the round trip low enough to be playable, and the
  Recorder anti-goal is "hears noticeable delay". A Microphone track's screen has only Input. "plug in headphones to
  monitor" goes with it.
- **Input gain**, the handle on the armed Microphone track's meter. Android gives tools no mic gain control. The meter
  still shows the input level.
- **Always the built-in mic.** Tools can't choose an input device (that needs `AudioManager`, which needs a `Context`).
  The mic is Android's default: the phone's own, or a plugged-in USB-C headset's (§4.2). Bluetooth is listen-only, since
  only call mode opens a Bluetooth mic.
- **A share sheet.** Export saves to Tool Manager instead (§13).
- **Renaming in place.** Drums' Rename screen instead (§11).
- **Alerts and sheets.** Every confirmation is full screen, as in Drums (§14).
- **Edge-swipe back.** The SDK owns back.
- **Accessibility labels and screen-reader adjustments.** Deferred (§17).

## 2. Tool identity

`tool/lighttool.toml`:

```toml
[tool]
id = "com.stephenmorris.recorder"   # permanent once registered
label = "Recorder"
versionCode = 1
versionName = "1.0.0"
permissions = ["android.permission.RECORD_AUDIO"]
capabilities = ["tool-manager-provider"]
serverPackage = "com.lightos"
```

The Kotlin package is `com.stephenmorris.recorder`. The repo is a clone of `lightphone/light-sdk` (remote `upstream`)
published as `smorris525/light-recorder` (remote `origin`). The source is public, as Light requires.

## 3. Data & persistence

In the tool's private `filesDir`. No Room.

- **`Songs/songs.json`:** every song, as kotlinx.serialization JSON. It's written 0.5 s after the songs stop changing, and
  on `onAppPause`. It reads leniently: a setting missing from an older save takes its default, and an unknown value reads
  as the default.
- **`Songs/Clips/<id>.clip`:** one recording: its sample rate, then its mono float samples. It's written once its take
  finishes, read when a song using it opens, and resampled if the output now runs at another rate.
- **`Songs/Mix/<song id>.mix`:** the song's last measured lane, so its waveform shows the moment it opens.
- **Cleanup:** at launch and when a song is deleted, recordings no song plays any more and deleted songs' mixes are
  removed.
- **Click** (on Pads) is one setting for the whole tool, in **DataStore**, on by default.
- **No backups in v1.** LightOS backs up only Tool Manager folders, append-only and flattened; clips are about 35 MB per
  3-minute track. Export is how a song is kept (§13).

### Model

In the `tape` package:

- **Song:** id, name, notes, in point and out point (seconds, each optional), the view (`TapeView`), exactly four tracks,
  the patterns (at least one) and the selected pattern.
- **Track:** id, number (1…4), input (`mic` | `drums`), volume (−48…+6 dB, and silent below), pan (L50…C…R50 in steps of
  5), spans (audio) and hits (drums). There's no mute or solo. The name is always `Track n`.
- **Pattern:** Drums' pattern (name, BPM, meter, bars, swing, steps) belonging to the song.
- **Not saved:** which tracks are armed. A song opens with nothing armed.

**A new song:** Track 1 on Drums, Tracks 2–4 on Microphone, and one pattern, "A": the plain rock beat at 100
BPM, 4/4, 1 bar, no swing, selected. Nothing is recorded. There's no sample song: the first launch opens an empty
library.

## 4. Audio engine

The transport plays, punches in and out, counts the lead-in, records pattern takes and mixes. Android forces this
implementation:

### 4.1 Output

- **Kotlin only.** Light's build rejects native code, so there's no Oboe or AAudio.
- **One stereo float `AudioTrack`** (`PERFORMANCE_MODE_LOW_LATENCY`, the device's native rate, 48 kHz expected) fed by
  the tool's own mixer thread, as Drums does. Stereo, unlike Drums, because tracks pan.
- **The mix:** each track's audio and drum hits at its volume and pan, then a `Limiter` per channel.
- **The kit:** Drums' synthesised kit, rendered once at launch, with Recorder's headroom: 7 dB under Drums, so the default pattern peaks just under the hot zone.
- **Scheduling:** sample-accurate, ahead of the audio clock, for tape hits, live pattern steps, clip starts and the out
  point's stop. The playhead derives from the audio clock.

### 4.2 Input

- **Plain `AudioRecord`** (mic source, 16-bit mono, the output's rate) on its own thread. It's open only while a
  Microphone track is armed. It feeds the meter and, during a take, a lock-free ring to a background
  writer. The SDK's `LightAudioCapture` isn't used, since its flow drops buffers when the collector falls behind.
- **Permission:** asked through the SDK (`checkPermission` / `rememberPermissionRequestLauncher`) the first time a
  Microphone track is armed. If refused, nothing arms, and that track's Input reads `microphone not allowed`.
- **Which mic:** Android's default input (§1, "Not carried over").

### 4.3 Alignment

- A take is placed by timestamps: `AudioTrack.getTimestamp` and `AudioRecord.getTimestamp` both map frames to
  `System.nanoTime`, so each input frame is placed against what was playing when it was heard. Reported latencies
  aren't used.
- A fixed correction constant (0 to start) is kept for tuning on hardware.
- The measured output latency, input latency and offset are logged (`adb logcat -s RecorderAudio`) so a tester with a
  phone can report them.
- Pattern takes need no compensation: hits are written where they were scheduled.

### 4.4 Lifecycle and limits

- **Audio focus:** not requested. The SDK doesn't give tools access to it.
- **`onAppPause`** (home, screen off, a call) is delivered to the top screen only, so every view model shares a base
  class that handles it. It stops playback, ends a take keeping what it has, and closes the mic.
- **Screen:** kept awake while playing or recording, with Compose's `Modifier.keepScreenOn()`.
- **Unverified without hardware:** latency, alignment, CPU and touch feel (§19).

## 5. Song model and rules

In `tape`, each rule covered by tests: four fixed tracks, inputs and arming (one Microphone track and one
Drums track at once), recording clearing both kinds under a take, punch points, the 3 s lead-in and its count, pattern
takes and queueing (a take stops at once on ■), song length (the last recording plus 30 s of blank
tape), the lane's measurement, bounce (§15) and the mixdown.

## 6. Navigation & lifecycle

The SDK owns the back stack and the back button.

```
Library ──► Song ──► Track n ──► Input
              │         └──► Patterns ──► Pattern ──► Sound (per sound)
              │                              │  ├──► Pads
              │                              │  ├──► Settings ──► Tempo / Time Signature / Length / Swing
              │                              │  ├──► Rename
              │                              │  └──► Actions ──► Delete confirmation
              │                              └── (Pads also opens Settings, Rename, Actions)
              ├──► Rename
              └──► Actions ──► Notes ──► Notes editor
                       ├──► Bounce ──► From / To
                       ├──► Export
                       └──► Delete confirmation
```

- **The transport panel** (§8) shows on Song, Track, Input and Patterns, the same panel on each. It isn't on the pattern
  screens, Notes, Bounce, Export or Actions.
- **Back from Song to the Library** stops playback and ends any take.
- **Back from Pattern or Pads to Patterns** stops the pattern playing and ends a Pads take.
- **Within the panel's screens** (Song, Track, Input, Patterns), and on Rename, playback and takes carry on.
- **Leaving the tool** stops everything (§4.4). Returning while the tool is still in memory shows the same screen,
  stopped. **A fresh launch opens the Library.**
- One-time setup (rendering the kit, loading the songs) goes in view model `init`, not `onScreenShow`.

## 7. Library

As Drums §7, with songs:

- **No header.** A `LightScrollView` of songs, oldest first. Each row shows the name (`Copy`) over its length (`Detail`,
  `2:48`), both white. Tapping a row opens the song with the playhead at 0:00, the view at the start, and nothing armed.
- **Bottom bar:** `EDIT` at the left and ⊕ (`LightIcons.ADD`) at the right. With no songs, only ⊕, centred.
- **Edit mode:** `EDIT` becomes `DONE`. Each row gets a `LightIcons.DELETE` at its left, with the names indented, which
  opens the delete confirmation (§14). You stay in edit mode afterwards; deleting the last song leaves it. Tapping a row
  does nothing in edit mode.
- **⊕** creates "New Song" (then "New Song 2", …, `uniqueName`), leaves edit mode and opens it.
- **Empty state:** "tap below to start a song", centred on the whole screen, `Paragraph`, not tappable.

## 8. Song screen and the transport panel

### Header (`LightTopBar`)

- **Left:** `BACK`, to the Library.
- **Centre:** the song's name, `LightTopBarCenter.Text`. Tap it to rename (§11).
- **Right:** `LightIcons.ELLIPSES`, opening the song's Actions (§10).

### Channel strips (custom)

Four strips side by side: a 16 dp gap between strips, 36 dp
button rows with 44 dp targets, and a 38 dp pan row. Every control is on screen without scrolling. Top to bottom:

- **The number and input icon:** `Detail` text, then `LightIcons.MICROPHONE` or the hand-drawn drum
  at 18 dp. A tap opens the track's screen (§9).
- **Pan:** a short line across with the handle alone, catching at C.
- **The meter and the fader,** upright and side by side, as `LevelMeter` and `TrackSliders.volume`:
  - **Meter:** the armed Microphone track's input, otherwise the track's playback level, with a 4 dp gap at the hot zone,
    level with the fader's 0 dB. CLIP shows on the hot zone for 2 s after full scale.
  - **Fader:** thick (5 dp) up to its handle (3 × 14 dp), with a 4 dp gap marking 0 dB while the handle is away. It catches
    within 8 dp of home.
- **Arm:** `LightIcons.CIRCLE`, underlined while armed. Disabled during a take.

Taps and drags follow `LevelSlider`.

### Transport panel (custom, except the bar)

18 dp above the ruler, a 28 dp
ruler, a 30 dp lane 5 dp under it whose touches reach 5 dp above and 9 dp below, 2 dp line spacing, a 4 dp clip gap,
brackets with 6 dp feet, and 24 dp margins showing the tape either side. The ruler and lane are white throughout: nothing
dims outside the points. The text is `superfine` (the playhead's time medium, `0:12.73`).

- **Ruler:** a tap moves the playhead; a drag scrubs it by hundredths, and holding near either edge scrolls half a screen
  a second.
- **Lane:** a tap moves the playhead; a sideways drag scrolls the tape. Touches are ignored during a take.
- **Bottom bar (`LightBottomBar`, 4 icons):**
  - **[** and **]**, hand-drawn brackets: set the point at the playhead, or clear it if it's set.
    Underlined while set. Disabled where they can't be set, and during a take.
  - **●** (`LightIcons.CIRCLE`): record. Underlined while recording; disabled with nothing armed. During the lead-in, the
    count (3, 2, 1) drawn in its place, as Drums' count-in icon.
  - **▶ / ■** (`PLAY` / `STOP`).

  Disabled items use the SDK's own `enabled = false` look.

## 9. Track, Input and Patterns

All three show the transport panel underneath.

- **Track screen:** `LightTopBar` with `BACK` and "Track 2", and no right icon. Label-over-value rows, as Drums' Settings
  main page:
  - **Input** (`Microphone` / `Drums` / `microphone not allowed`) opens Input. It's locked (dimmed, doesn't open) while
    that track records.
  - On Drums, **Pattern** (the selected pattern's name) opens Patterns.
- **Input:** Drums' choice page (`ChoiceScreen`), titled "Input": Microphone, Drums. The current option is underlined
  (2 dp, about 3 dp under the text, as wide as it). Picking one moves the underline and the page stays open. Changing
  input disarms the track and never changes what's recorded.
- **Patterns:** `LightTopBar` with `BACK` and "Patterns". A `LightScrollView` of the song's patterns:
  - Each row is the name (`Paragraph`) over `92 BPM · 4/4 · 2 bars` (`Detail`). A tap selects the row, underlined, and
    the page stays open.
  - During a take, a queued pattern has an 8 dp dot after its name.
  - Only the selected row has `LightIcons.PENCIL` at its right, which opens the pattern (§10).
  - **ADD PATTERN** (`Button` text) after the last row adds the next letter with the selected pattern's tempo, meter and
    length, and no hits. It doesn't open it. It's a row, not the bottom bar's ⊕, because the bar is the transport's.

## 10. Pattern screens and Actions

**Pattern, Sound, Pads, Settings (Tempo, Time Signature, Length, Swing), Rename, Actions and Delete confirmation are
Drums' screens** (Drums §§8–14), with these differences:

- **Opened** from a pattern's ✎ on Patterns; back from Pattern or Pads returns there (§6).
- **▶ loops the pattern alone**, without the tape. ● on Pads overdubs into the pattern, quantised to its steps, as Drums.
  Changing a pattern never changes hits already on tape.
- **Click** on Pads: on by default (§3).
- **Pattern Actions:** the pattern's name as the title, with back. **Duplicate** copies it as `A 2` (the lowest free
  suffix) and opens the copy. **Delete**, shown only while the song has more than one pattern, asks first (§14), then
  returns to Patterns, with the selection moving to the pattern after it, or the last.
- **No sample patterns** and no pattern library: patterns live in the song.

**Song Actions** (`ELLIPSES` on Song): a full-screen menu of text rows titled with the song's name, with back:
**Notes**, then after a gap **Bounce…**, **Export**, **Duplicate** and **Delete**:

- Bounce… is dimmed until something is recorded, while playing and during a take.
- Export is dimmed until something is recorded, and during a take.
- Duplicate and Delete are hidden during a take.
- Duplicate copies the song as `uniqueName` names it and opens the copy. Delete asks first (§14), then returns to the
  Library.

## 11. Rename

As Drums §11, for songs and patterns: tapping the name in the top bar (Song, Pattern, Pads) opens
`LightTextInputEditor`, titled "Rename", single line, with the current name and the cursor at the end. **DONE** saves the
trimmed name (an empty name keeps the old one). **Back cancels.** Playback and takes carry on. Tracks can't be renamed.

## 12. Notes

- **Notes screen:** `LightTopBar` with `BACK` and "Notes", and no right icon. The song's notes as plain text
  (`Paragraph`) in a `LightScrollView`. **Tapping the text opens the editor.** With no notes, it reads "tap to write
  notes", which opens the editor the same way.
- **Notes editor:** a close copy of `LightTextInputEditor`'s layout (its top bar with `BACK` and "Notes", the text, and
  `LightEmbeddedLp3Keyboard`), multi-line, with **only the text area scrolling**, so the cursor is always in view. The
  SDK's editor clips long text, which is why this is a copy. It opens with the cursor at the end.
  - **No DONE:** the keyboard's bottom bar is empty.
  - **Back saves** the notes, with trailing whitespace trimmed, and returns to the Notes screen.
  - The keyboard's haptics follow the system setting.

## 13. Export

- **Export** (song Actions) renders the mixdown (0:00 to the last span or hit, not the blank tape after it)
  to a **320 kbps AAC `.m4a`**, using Android's `MediaCodec` and `MediaMuxer`.
- **While rendering:** `EXPORTING` and a percentage, centred, with `LightIcons.CLOSE` in the bottom bar to cancel, which
  discards the file and goes back.
- **The file:** `exports/<song name>.m4a` in the shared folder (`LightFileShare`). Exporting the same song again replaces
  its file. Characters a file name can't hold are replaced with `-`.
- **Done:** a full screen in `LightFullscreenModal`'s style: "Exported. Find it in Tool Manager, under Recorder ›
  Exports." Back returns to the song.
- **Tool Manager:** `getToolManagerManifest()` lists one leaf, `FileBrowserSpec(label = "Exports", path = "exports")`, for
  download and delete, with `canBeBackedUp = false`.

## 14. Deleting

A song (from the Library's edit mode or the song's Actions) or a pattern (from its Actions) is deleted after Drums' full
screen confirmation (Drums §12): **"Delete ‹name›?"** centred (sentence case, no quotes, wrapping), with a
`LightBottomBar` of `LightIcons.DENY` at the left (cancel) and `LightIcons.ACCEPT` at the right (delete). No undo.
Deleting the open song stops playback.

## 15. Bounce

Bounce mixes the From tracks onto To, in `tape`'s `Bounce`. Every page has `LightTopBar` with `BACK` and a title.

- **Bounce:** "Bounce". Two label-over-value rows:
  - **From:** `None`, `Track 1`, `Tracks 1 and 2`, … (`Bounce.summary`).
  - **To:** `Track 1`.

  Under them, "replaces track 1's recording" (`Paragraph`) when the bounce would replace a recording on To that isn't a
  source. Opening defaults: From is None, To is Track 1. The `LightBottomBar` holds **`BOUNCE`** alone, centred, disabled
  until `canBounce` (From has a track other than just To). Tapping it bounces and returns to the song.
- **From:** Track 1…Track 4, each with `LightIcons.SELECT_ON` / `SELECT_OFF` at its left. A tap toggles a track, and the
  page stays open. Empty tracks are dimmed and can't be picked.
- **To:** a choice page, Track 1…Track 4, the current one underlined. **It stays open on a pick,** to match
  every other choice page. All four can be picked.

## 16. Haptics

These follow the system setting (`LocalHapticsEnabled`, `LocalHapticFeedback`):

- **Ruler scrub:** a light tap on each tick, a firmer one on each labelled tick.
- **Fader and pan:** a light tap on arriving at home (0 dB, C).
- **Drums' own** (Drums §14): the accent hold, the tempo keypad, the keyboards.
- **`lightClickable`'s** touch-down buzz on buttons and rows. It's off for the faders, pans, ruler, lane, step taps and
  pads.

Nothing else buzzes.

## 17. Accessibility

Deferred until it's clear what screen-reader support LightOS offers tools.

## 18. Testing & build order

1. **Core logic** (`com.stephenmorris.recorder.core` for the drum machine, `.audio` for its DSP, `.tape` for
   the song model; no `android.*`), test-first.
   Drums' core and DSP are copied in with their tests.
2. **Engines and storage:** mixer and output, capture and the take writer, the transport, mix measurement, bounce
   rendering, the `.m4a` encoder and the song store, behind interfaces with fakes so they stay JVM-testable. Verified on
   the emulator, including a mic take from the host's mic.
3. **Screens:**
   - 3a: Library, Song with the panel, Track, Input, Patterns, Rename and Notes.
   - 3b: the pattern screens, Actions, Bounce, Export and Tool Manager.

   Each is screenshotted on the emulator.

## 19. Open questions

These need hardware; there's no Light Phone III to test on:

- Take alignment: the timestamp offsets and the correction constant (§4.3).
- Output latency, and the pads' feel.
- What a USB-C headset's mic does to a take (§4.2).
- CPU: four tracks, the kit and a mic take, with the mix measured in the background.
- The faders, pans and ruler scrub on the real touchscreen, and the haptics' feel.
- The font (Akkurat) and how tight the channel strips are.
- A Tool Manager download of an export over Wi-Fi.
- The SDK's permission request on a real Light server. The emulator AVD has no Light server, so its mic was granted
  with `adb shell pm grant`.

The emulator's numbers (API 34 AVD, 2026-11-17), for comparison with hardware:
- The output runs at 48 kHz with a 4,360-frame buffer and 0 underruns. The latency read 91 ms at start and 175 ms
  during a take.
- The input's timestamp was 9 ms old, and the take was aligned by timestamps.
- The host mic reached the take only faintly, peaking at −72 dB.

### Later

Monitoring, if hardware latency proves low enough. Backups. Accessibility.
