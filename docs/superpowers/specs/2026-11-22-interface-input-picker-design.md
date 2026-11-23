# Interface input picker

## Intent

With a USB audio interface plugged in, a Mic/Line track records whatever Android's mono default gives it, and there's
no way to say "this track is Input 2". The player wants to pick, per track, which of the interface's inputs it records
from (guitar on Input 1 into Track 2, vocal on Input 2 into Track 3), for an interface with any number of inputs.

## Goals

- Per track, a choice of the interface's inputs, Input 1 … Input N, saved in the song.
- Works for interfaces with 1, 2, 4 or more inputs; with 1 input nothing changes.
- When a track's saved input isn't there (a smaller interface, or none), it records from Input 1 or the phone's mic,
  and its Input row says so.

## Non-goals

- Choosing the phone's own mic while an interface is plugged in (needs `AudioManager`, which tools can't reach).
- Recording two inputs onto two tracks in one take (one Mic/Line track arms at a time, as now).
- Stereo tracks; input gain; monitoring.
- The iOS app (Light Recorder Tape), unless asked separately.

## Anti-goals

- Changing anything about the built-in mic or a headset: without an interface, Recorder behaves exactly as today.
- A take that silently records the wrong input: whenever the input recorded isn't the one saved, the screen shows it.

## Constraints

- No `Context`/`AudioManager` (`getSystemService()` is rejected by the SDK plugin). Everything is learned from the
  `AudioRecord` itself: `routedDevice.type` and `routedDevice.channelCounts`.
- The input count is only known once an `AudioRecord` is open and routed.
- Untested hardware: whether LightOS opens more than 2 channels, and what `channelCounts` a given interface reports.

## Approaches

1. **Open every input, keep the track's one** (picked). With an interface, the mic opens all N channels with a channel
   index mask; the reader keeps the armed track's channel before the ring, so takes, meter and alignment stay mono.
   Switching input reopens nothing. The Input page briefly opens the mic to count inputs, else uses the last count.
2. **Open only the chosen input** (single-bit index mask, reopen on change). Rejected: devices are pickier about
   single-channel masks and may downmix, which can't be tested ahead of time; reopening adds gaps and route churn.
3. **Like 1, without the probe.** Rejected: a new 4-input interface would show the wrong choices until a track arms.

The user picked 1.

## Design

Decided with the user:

- The choice is per track and saved in the song.
- Fallback: a missing input records from Input 1 (or the phone's mic with no interface), and the Input row shows it.
- No phone-mic option while an interface is in.

Claude's calls (correct any of these):

- **Model.** `Track.channel: Int = 1`, 1-based, meaningful only on Mic/Line. Stored in the song as `"channel"`;
  missing reads as 1, so old songs are unchanged.
- **Input count.** `InterfaceRoute` gains `inputCount: StateFlow<Int>`: the channel count of the routed USB interface,
  remembered for the session after the mic closes (not saved). 1 for the built-in mic, a headset, or no interface.
  An empty `channelCounts` counts as 2.
- **Opening.** The mic opens mono as today. If the routed device is `TYPE_USB_DEVICE` with more than one input, it
  reopens with `setChannelIndexMask((1 shl n) - 1)`. If that won't open, it falls back to mono and reports 1 input
  (logged). If the route changes away from the interface while open, it reopens mono.
- **Choosing the channel.** `AudioInput` gains a `channel` the transport sets when a Mic/Line track arms (and when its
  input changes while armed). The reader de-interleaves `channel` if the interface has it, else channel 1; mono
  ignores it. That is the fallback.
- **Input page.** With an interface of N ≥ 2 inputs: `Input 1` … `Input N`, then `Drums`. Otherwise: `Mic/Line`,
  `Drums`, as now. Picking `Mic/Line` sets channel 1. When the page opens and the mic isn't open, Recorder isn't
  recording and the permission is granted, it opens the mic for a moment to learn the count. Without the permission,
  it uses the last count.
- **Labels** (Track screen's Input row, wherever the input's name shows):
  - Saved input present: `Input 2`, or `Mic/Line` when there's no picker (N ≤ 1, channel 1).
  - Saved input missing: `Input 4 → Input 1` with a smaller interface, `Input 2 → Mic/Line` with a 1-input interface
    or none.
- **Disarming.** Switching between inputs on a Mic/Line track doesn't disarm it (the input is locked while recording
  anyway). Switching between Mic/Line and Drums still disarms, as now.
- **Spec.** Update `tool/docs/spec.md`: §1 "Left out" (choosing the device stays out, the channel comes in), §4.2,
  §9's Input page, and §19 (what a multi-channel open does on LightOS).
- **Tests.** Song coding round trip and old-song default; the label function for every case; de-interleaving and the
  `min` fallback; `inputCount` from route reports.

Left to the builder: where the label function lives, how the probe shares `MicInput` with the transport, and log
wording.
