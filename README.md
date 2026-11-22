# MetroGaia + LOOM Android

Two independent Android APKs built by GitHub Actions from one shared native Java/Android foundation.

**Important: LOOM is now a feature-rich Android adaptation, not a complete sound-identical migration of desktop LOOM M7.5.4.** The underlying synthesizer source algorithms were reviewed and the feature families reimplemented for Android. The specialised mobile models must not be mistaken for the entire original Processing desktop DSP. See [PORT_STATUS.md](PORT_STATUS.md).

## Install

Open [GitHub Actions](https://github.com/domu-code/metrogaia_android/actions/workflows/android-apk.yml), select the newest **successful** build, and download the `LOOM-Android-APK` artifact. Extract its ZIP and install the `loom-debug.apk` file.

Requires Android 8.0+ (API 26). APK is debug-signed for sideloading. `MetroGaia-Android-APK` is maintained separately, without LOOM's advanced UI and engine additions.

## LOOM Android expanded prototype

Eight concurrently mixable source families, each with source level, selected model, harmonic/timbre/morph controls:
- Plaits-inspired family, 24 selected variations
- Braids-inspired family, 48 model variations grouped into eight mobile algorithms
- DMG, four pulse/triangle/noise modes
- WAV sampler, with PCM16 or float32 mono/stereo import, reverse/loop/retrigger, grain-style scanning
- Procedural speech/formant synthesis
- Karplus-inspired resonant Rings
- Eight-material modal Handpan
- Wind/reed Air/Bore delay-line model

More features:
- Responsive ten-page touch editor, one-column phones and two-column tablets, portrait/landscape resizing, multi-touch piano keyboard with octave controls
- Exact PocketTracker 3 R15 5×5 bitmap glyph patterns, source-rendered throughout
- 24-slot modulation matrix: LFO 1/2, amp/mod envelopes, velocity, mod wheel, pitch bend, XY axes, step, random and aftertouch sources
- MIDI learn for UI parameter destinations; MIDI mappings persist with local patches
- Four LOOM STATES snapshot slots, capture/recall, A/B selection and morph fader
- Per-note parameter locks and local patch storage
- Five reorderable effects: drive, bitcrush, chorus, delay and reverb
- Arpeggiator, 16-step note sequencer and live performance input
- Eight banks of eight MIDI recording loop slots, record, playback, overdub and save/load of loop banks
- Native Android USB and virtual MIDI input, channel pressure, pitch bend and useful conventional CC defaults
- Local eight-slot patch memory; new Android patch format, *not* yet desktop .json cartridge compatibility

### Touch equivalents of desktop controls

| Desktop action | LOOM Android equivalent |
|---|---|
| Ctrl-click a parameter, choose a modulation source | Tap **MOD**, tap parameter, choose source; edit signed depth in MOD page |
| MIDI learn/CC assignment | Tap **LEARN**, tap parameter, send a CC from controller |
| Shift-drag precision | Tap **FINE** then drag the parameter |
| Double-click reset | Long-press parameter → **RESET** |
| Alt-click clear note lock | Tap **LOCK**, play/select note, long-press parameter → **CLEAR LOCK** |
| Parameter contextual menu | Long-press parameter |
| XY mouse movement | Touch the XY page pad |
| Keyboard octave | MIX page, **OCT -** and **OCT +** |
| Patch save/load | PATCH page |
| Looper keys/shortcuts | LOOPS page pads and transport controls |

**MOD**, **LEARN**, **FINE**, and **LOCK** are persistent, clearly highlighted edit modes. **EDIT** resumes normal operation.

## Building

Requires JDK 17, Android SDK 35, Gradle 8.9. GitHub builds and uploads both debug-signed APKs automatically:

```sh
gradle --no-daemon :metro:assembleDebug :loom:assembleDebug
```

Source modules:
- `core/`: native AudioTrack synths, MIDI adapters, UI, font and rendering layers
- `loom/`: LOOM app
- `metro/`: original basic MetroGaia Android prototype

## Honest limitations

This is **not** LOOM M7.5.4's complete DSP in Android packaging. Detailed Plaits/Braids algorithms, source Beads grains and reverb, allophone sample assets, full Rings/Handpan/AIR fidelity, 256-bar/free looper quantization, Frames/Branches, original WAV wavetable import, desktop cartridge compatibility and full MIDI router still require additional engineering. The sampler currently loads WAV from storage, not the phone microphone. New Android patch/loop formats are different from desktop LOOM patch files.

Physical-device stability, touch hit targets on every device, USB MIDI hardware compatibility, latency, battery use, and full sustained-load performance remain **unverified until device testing**.
