# LOOM Android implementation audit

Reference desktop project: `Loom_M7_5_4_LiveLooper_Colourways.zip` (24 Processing tabs), inspected for engine, parameter, UI, sequencer and porting constraints.

**Build stage:** GitHub Actions compiles debug APKs. No physical phone/tablet audio, MIDI, CPU or UI test has been executed here.

## Feature coverage

| Desktop feature | Android state | Notes |
|---|---|---|
| Eight synth source families | Mobile adaptations | All eight voices available concurrently; algorithms not sample-accurate to desktop source |
| Plaits | Partial | 24 selectable variation IDs, simplified DSP per family |
| Braids | Partial | 48 model positions mapped to eight broad algorithm families |
| DMG | Partial | Four pulse/tri/noise options; not full DMG envelope/register simulator |
| Sampler | Partial | Android WAV import, PCM16/float32, reverse/loop/grain retrigger; no microphone recorder |
| Speech | Partial | Procedural formant synthesis; no source allophone WAV asset playback |
| Rings | Partial | Plucked/resonant delay lines; not original Rings modal algorithm |
| Handpan | Partial | Multimode resonator and material selection; not original 2-ring model |
| AIR/BORE | Partial | Reed/wind waveguide variants, breath control; not full nine-model implementation |
| Wavetable | Not migrated | No external multi-frame wavetable sample import or anti-aliased wavetable engine |
| Tides/LFOs | Partial | Two global LFOs; advanced Tides shapes not migrated |
| Modulation matrix | Working mobile implementation | 24 signed routes with 12 source types and touchscreen assignment |
| MIDI learn | Working mobile implementation | Learn mode arms destination and captures first received CC; local patch persistence |
| MIDI input | Basic Android implementation | Native Android MIDI devices and virtual MIDI service, OMNI channels |
| MIDI CC defaults | Working | CC1/7/71/72/73/74/91/93, note on/off, pitch bend and channel pressure |
| Per-note parameter locks | Working | Select note in LOCK mode, edit parameter and clear via long-press; saved with patch |
| LOOM STATES | Working mobile implementation | Four snapshots, recall and A/B morph, persisted with patch |
| Effects | Partial | Five reorderable effect stages, not source Beads/desktop FX chain |
| MIDI performance looper | Partial | 64 slots, record/overdub/play, saved independently; lacks quantization and 256-bar capture |
| Step sequencer | Basic | 16 enable/disable steps, note transpose, tempo and gate |
| Arpeggiator | Basic | Simple octave cycling, not the full source pattern set |
| XY modulation | Working basic | Touch surface and XY sources for mod routes; motion recorder not migrated |
| Frames/Branches | Not migrated | Future performance feature |
| Cartridge import | Not migrated | Android-local patch JSON is not compatible with LOOM cartridges |
| Preset banks | Basic | Eight local presets, separate 64-slot MIDI loop banks |
| Android responsive UI | Implemented but untested | Actual View dimensions, portrait/landscape, one/two columns and vertically scrolling parameter pages |
| Touch replacements for keyboard modifiers | Implemented but untested | MOD, LEARN, FINE, LOCK and long-press context menu |
| PocketTracker font | Implemented | Bitmap codepoints transcribed from PocketTracker 3 R15 `native/ui/font5x5.h` |

## Most important next work

1. Test both portrait and landscape UI on small phones and large tablets, including edge touch targets, overlay contexts, and multi-touch note-release behaviour.
2. Compare source desktop rendered audio for each model and replace the mobile prototype DSP with faithful, block-based Java ports of `JavaPlaitsVoice`, `JavaBraidsVoice`, `LoomAirBoreVoice`, `LoomHandpanVoice`, speech/allophones and `BeadsTexture`, using preallocated buffers and reference-audio tests.
3. Restore desktop cartridge format and factory sound banks with compatibility adapters.
4. Implement audio microphone capture, full granular processing, advanced wavetable import, MIDI quantization, Frames/Branches, step pattern varieties, XY motion recording and per-note modulation.
5. Add background audio foreground service and measure end-to-end USB/virtual MIDI latency, dropouts and thermal/battery behaviour.

All source-fidelity claims should be measured, not inferred from superficial model names. The APK is meant for iterative device tests before a musical production release.
