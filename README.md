# MetroGaia + LOOM Android | first playable foundation

This repository builds **two independently installable Android applications**:

- MetroGaia Android (`com.domucode.metrogaia`)
- LOOM Android (`com.domucode.loom`)

Both use Android's native AudioTrack/MIDI APIs and a responsive touchscreen layout with real PocketTracker R15 **5x5 bitmap glyph patterns** (source: `PocketTracker3/native/ui/font5x5.h`).

## Get the APKs

Go to [Android build runs](https://github.com/domu-code/metrogaia_android/actions/workflows/android-apk.yml), open the latest **successful** run, and download the `MetroGaia-Android-APK` or `LOOM-Android-APK` artifact ZIP. Extract the APK and install it on Android 8+.

Build locally with JDK 17, Android SDK platform 35, and Gradle 8.9:

`gradle :metro:assembleDebug :loom:assembleDebug`

These are Android debug-signed APKs for sideloading, not Play Store releases.

## Playable prototype features

- Stereo native audio synthesis through AudioTrack, using the device's output sample rate
- 12 voices and four independently switchable oscillator slots (sine, saw, triangle, pulse, wavetable-like, DMG-like, FM, noise, and resonant **prototype** algorithms)
- Oscillator mix levels and semitone-cent detuning, lowpass filtering, ADSR, drive, stereo echo
- Scaled/auto-rearranging touchscreen controls and a multitouch piano keyboard
- Native Android MIDI device discovery, including compatible USB MIDI and virtual device connections
- Android MIDI IN service discoverable to other apps; note on/off, pitch bend, sustain, CC 1/7/71/72/73/74/91
- Eight patch slots per app, saved privately on the device

## Important limitations and project integrity

**This is the first port foundation, not feature parity with the desktop synthesizers.** The full original Processing versions, MetroGaia **v7.2.2** and LOOM **M7.5.4**, use components that aren't directly Android-compatible (desktop JavaSound, Beads, ControlP5, TheMidiBus, desktop windowing). This build does not yet migrate the desktop synthesis engine implementations, sampling/granular DSP, full Plaits/Braids/Rings/AIR/BORE models, patch-bank formats, original effects, modulation matrix, sequencers or XY recorders. The prototype wave/model names do not assert compatibility or DSP equivalence with those models.

The original source archives were inspected separately and are **not** included in this repository. They must be brought into a later DSP port with licensing and reference-audio validation. The PocketTracker font is stored here as glyph-bit arrays rather than an unlicensed font binary. The 5x5 patterns are derived from the user's existing PocketTracker R15 source.

Keep the app foregrounded during live performance. Background playback, native-device hotplug auto-reconnect, sample file import, preset transfer, MIDI learn, Bluetooth-specific latency testing, and signed release packaging are future work.

## Android modules

- `core/`: pure Java synth DSP, native audio renderer, Android MIDI bridge, MIDI service, PocketTracker bitmap font, responsive Canvas UI.
- `metro/`: MetroGaia application identity and patch defaults.
- `loom/`: LOOM application identity and patch defaults.
- `.github/workflows/android-apk.yml`: CI builds both APKs, downloadable as run artifacts.
