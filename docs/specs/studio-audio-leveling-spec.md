# Specification: JumpCut AI Studio Speech Leveling & Room-Tone Smoothing

**Date:** 2026-09-08  
**Status:** Approved  
**Target Version:** JumpCut AI v1.5.3  

---

## 1. Overview & Objective
When jump-cutting silence from video footage, creators frequently face two acoustic issues:
1. **Dialogue Inconsistency:** Speakers alternate between quiet whispering/mumbling and loud vocal bursts. Without normalization, the viewer has to constantly adjust volume.
2. **Acoustic "Vacuum Dropout" & Boundary Clicks:** Hard cuts in audio create audible clicks and drop room tone abruptly to digital zero silence, causing an unnatural "vacuum" feeling between sentences.

This specification introduces:
- **Studio Speech Auto-Leveler:** An intelligent two-stage compressor/limiter normalizing speech segments to standard -14 LUFS target RMS with a hard -1.0 dB true peak safety ceiling.
- **S-Curve Dual-Ended Micro-Crossfade & Room-Tone Comfort Bed:** Smooth 15ms half-cosine fade-in and fade-out at clip boundaries, paired with subtle room-tone floor continuity at the video's measured ambient floor.
- **Interactive UI Controls:** Toggles in the Editor's Tuning panel and Export Options sheet, fully integrated into Creator Presets.

---

## 2. Technical Architecture & Data Flow

### A. Data Layer (`MediaModels.kt`)
Extend `CutSettings` with:
```kotlin
val studioAudioLeveling: Boolean = true,
val roomToneSmoothing: Boolean = true,
val targetLoudnessLufs: Float = -14.0f
```
Add serialization/deserialization support in `PresetRepository.kt`.

### B. Audio Processing Engine

#### 1. `StudioAudioProcessor.kt` (Media3 `AudioProcessor`)
An optimized PCM-16 stereo/mono `BaseAudioProcessor` implementing:
1. **Dynamic Leveling (Slow-attack RMS AGC):**
   - Measures speech window RMS energy.
   - Applies smooth gain compensation toward -14 LUFS target (~0.20 normalized amplitude RMS).
   - Maximum boost cap: +12 dB (prevents over-amplifying low background noise).
   - Maximum cut: -18 dB (prevents aggressive loud shout distortion).
2. **True-Peak Soft Limiter:**
   - Soft-knee tanh or cubic limiter clamping peaks above -1.0 dBFS (30,000 / 32,768 short amplitude) to ensure zero digital clipping.
3. **Dual-Ended S-Curve Seam Fades:**
   - Smooth 15ms half-cosine fade-in on clip start.
   - Smooth 15ms half-cosine fade-out on clip end.
4. **Room-Tone Comfort Bed:**
   - Injects a continuous low-level pink/shaped noise floor matching the room's detected ambient floor (scaled to ~-48 dBFS) to prevent silence vacuum sensation between cut boundaries.

#### 2. Pipeline Integration (`VideoSplicer.kt`)
In `VideoSplicer.kt`:
Pass `StudioAudioProcessor` into `EditedMediaItem.Builder.setEffects(Effects(listOf(studioProcessor), videoEffects))` for each segment.

### C. UI & User Controls

#### 1. `EditorScreen.kt`
- Under **Tuning** tab:
  - Add **Studio Voice Leveler** toggle card with description: *"Normalizes speech to radio-quality -14 LUFS with peak limiter"*.
  - Add **Room Tone Smoothing** toggle card with description: *"15ms S-curve crossfades & ambient floor continuity"*.
- In `ExportBottomSheet`:
  - Show quick switches for **Studio Voice Leveler** and **Room Tone Smoothing** right alongside Auto-Zoom Jumpcuts.

#### 2. `PresetRepository.kt`
- Presets updated:
  - **Viral Shorts:** Leveling = ON, Smoothing = ON
  - **Podcast Studio:** Leveling = ON, Smoothing = ON
  - **Fast Lecture:** Leveling = ON, Smoothing = ON
  - **Vlog Natural:** Leveling = ON, Smoothing = ON
- Custom presets JSON serialization updated.

---

## 3. Implementation Plan

| Step | Component | Description |
|------|-----------|-------------|
| 1 | `MediaModels.kt` | Add `studioAudioLeveling`, `roomToneSmoothing` to `CutSettings`. |
| 2 | `StudioAudioProcessor.kt` | Implement Media3 `BaseAudioProcessor` with AGC, peak limiter, S-curve fades, and room-tone comfort bed. |
| 3 | `VideoSplicer.kt` | Wire `StudioAudioProcessor` into Media3 Transformer `EditedMediaItemSequence`. |
| 4 | `PresetRepository.kt` | Update preset defaults and JSON persistence. |
| 5 | `EditorScreen.kt` | Add Studio Voice Leveler & Room Tone Smoothing toggles to Tuning panel and Export Bottom Sheet. |
| 6 | Verification | Compile locally with `./gradlew assembleDebug`, install on ReDroid, and verify UI & export. |
