# Recorder

A four-track recorder with a drum machine, for the Light Phone III.

Recorder is a Light Phone tool. You record a song on four tracks. Each track
takes either a mic (the phone's own Built-In Mic, a USB-C headset's, or one of a USB audio interface's inputs, chosen
per track) or Drums (patterns played from the built-in drum machine, which comes from the Drums tool). There's no network and no account, and nothing leaves the
phone until you export it.

## What it does

- **Library:** your songs, each listed with its length.
- **Song:** four channel strips with input, pan, meter, fader and arm controls, above a transport panel that has a
  ruler, a waveform lane, in and out points, record and play.
- **Tracks:** a mic or Drums input on each track. Arm one mic track and one Drums track at a time. A take
  starts with a 3 s lead-in and punches in and out at the points you set.
- **Drum machine:** each song has its own patterns, with a step grid, pads with count-in overdub, tempo, time signature,
  length and swing. Pattern takes are queued while recording.
- **Bounce:** mix any tracks down onto one.
- **Export:** saves the song as a 320 kbps AAC `.m4a`. You can download it from Tool Manager, under Recorder ›
  Exports.
- **Notes:** a notes page for each song.
- **USB audio interfaces:** while one is in use, a jack icon replaces the mic icon. The Track screen tells you to listen
  through the interface's direct monitoring, since the app has no monitoring of its own.

Some things are left out of this version: live monitoring, input gain, choosing the input device and guitar tracks
with an amp. The [spec](tool/docs/spec.md#left-out) says why for each.

## Repository layout

This repo is a clone of [`lightphone/light-sdk`](https://github.com/lightphone/light-sdk). The tool lives in
[`tool/`](tool) under the Kotlin package `com.stephenmorris.recorder`:

| Package | What's in it |
| --- | --- |
| `core` | The drum machine (ported from Drums) |
| `audio` | DSP: the synthesised kits, compressor and limiter |
| `tape` | The song model and its rules |
| everything else | Engines, storage and Compose screens |

- [`tool/docs/spec.md`](tool/docs/spec.md): the agreed design. It's the source of truth for behaviour.
- [`tool/lighttool.toml`](tool/lighttool.toml): the tool's id, version and permissions (`RECORD_AUDIO`).

The other modules (`sdk`, `plugin`, `lint-rules`, `builder`, …) come from the Light SDK unchanged. For those, see
[the SDK docs](docs) and [`docs/repo`](docs/repo/README.md).

## Building and running

Open the project in Android Studio, or use Gradle:

```sh
./gradlew :tool:testDebugUnitTest   # JVM tests: core logic, DSP, engines behind fakes
./gradlew :tool:assembleDebug       # build the APK
```

**On the LightOS emulator:** set up the emulator by following [`docs/system_app`](docs/system_app). Then, in
`tool/lighttool.toml`, swap `serverPackage` to `com.thelightphone.sdk.emulator` and build with
`-DlightSdk.allowAltServerPackage=true`. The emulator AVD has no Light server to grant permissions, so grant the mic by
hand:

```sh
adb shell pm grant com.stephenmorris.recorder android.permission.RECORD_AUDIO
```

Audio diagnostics (latency, take alignment, interface routing) are logged under the `RecorderAudio` tag:

```sh
adb logcat -s RecorderAudio
```

## Status

All of v1 is built: screens, engines, export and Tool Manager. It has been checked on the emulator only. Several things
can't be confirmed without a Light Phone III: take alignment, latency, CPU load, touch feel and how USB interfaces are
routed. The list is in the [spec's open questions](tool/docs/spec.md#19-open-questions).

## License

See [LICENSE](LICENSE). Light requires community tools to be open source.
