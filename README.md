<div align="center">

<!-- ── Project Identity ── -->
<br />

<h1>AuraConv Pro</h1>

<p>
  <strong>500-Channel Abyssal Resonator Matrix</strong><br />
  Real-time parallel moving-average convolution engine for Android
</p>

<p>
  <em>
    A native Android application wrapping a high-performance Web Audio DSP engine<br />
    inside a hardened Chromium WebView shell — 500 logarithmically-distributed<br />
    resonator taps from 25 to 1,000,001 samples, live microphone input,<br />
    feedback resonance, polarity inversion, and real-time FFT visualisation.
  </em>
</p>

<br />

<!-- ── Badges ── -->
<p>
  <img src="https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/min%20SDK-24%20(Nougat)-blue" alt="Min SDK" />
  <img src="https://img.shields.io/badge/target%20SDK-35%20(Android%2015)-blue" alt="Target SDK" />
  <img src="https://img.shields.io/badge/language-Kotlin%202.x-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/audio-Web%20Audio%20API%20%2B%20AudioWorklet-orange" alt="Audio Engine" />
  <img src="https://img.shields.io/badge/webview-Chromium%20(WebKit%201.12)-green" alt="WebView" />
  <img src="https://img.shields.io/badge/license-MIT-yellow" alt="License" />
  <img src="https://img.shields.io/badge/version-1.0.0-red" alt="Version" />
</p>

<br />

</div>

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [DSP Engine](#dsp-engine)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Installation & Build](#installation--build)
- [Project Structure](#project-structure)
- [Permissions](#permissions)
- [Performance Engineering](#performance-engineering)
- [Configuration Reference](#configuration-reference)
- [Troubleshooting](#troubleshooting)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgments](#acknowledgments)

---

## Overview

**AuraConv Pro** is a real-time audio processing application that routes live
microphone input through **500 parallel moving-average resonator channels**,
each tuned to a unique odd-numbered tap length distributed logarithmically
across five orders of magnitude (25 → 1,000,001 samples). The result is a
dense, evolving spectral wash that transforms any sound source into a deep,
modal drone texture.

The application is built as a **native Android WebView shell** (Kotlin) hosting
a self-contained HTML5/Web Audio single-page application. This architecture
delivers sample-accurate DSP via the `AudioWorklet` API while maintaining a
single-codebase UI layer that renders at 60 fps on the Chromium compositor.

```
┌─────────────────────────────────────────────────────────────────┐
│                      AuraConv Pro                               │
│                                                                 │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              Android Native Shell (Kotlin)                │  │
│  │                                                           │  │
│  │  • Runtime microphone permission (Activity Result API)    │  │
│  │  • WebViewAssetLoader (virtual HTTPS for AudioWorklet)    │  │
│  │  • AudioFocus management (call/notification handling)     │  │
│  │  • Navigation guard (local-asset-only policy)             │  │
│  │  • GPU-accelerated WebView layer                          │  │
│  │                                                           │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │         Chromium WebView (M120+)                    │  │  │
│  │  │                                                     │  │  │
│  │  │  ┌───────────────────────────────────────────────┐  │  │  │
│  │  │  │     Web Audio Engine (index.html)             │  │  │  │
│  │  │  │                                               │  │  │  │
│  │  │  │  Mic → 500× AudioWorklet → Master → FFT      │  │  │  │
│  │  │  │        (lazy instantiation)    Gain  Canvas   │  │  │  │
│  │  │  └───────────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Features

### Audio Engine

| Feature | Detail |
|---|---|
| **500 parallel resonator channels** | Logarithmically distributed odd-tap moving-average filters (25 → 1,000,001 samples) |
| **Lazy worklet instantiation** | `AudioWorkletNode` created on activation, destroyed on deactivation — peak RAM scales with active channel count, not total |
| **Feedback resonance** | Recursive wet-signal feedback (0 – 95%) with integrated DC-blocking high-pass filter (~5 Hz) |
| **Polarity inversion** | Continuous crossfade between pass-mode (sustained wash) and block-mode (transient extraction) — click-free switching |
| **Timbre tilt** | Global spectral weighting from Dark (low-tap emphasis) to Bright (high-tap emphasis) |
| **Per-channel control** | Independent gain and dry/wet mix sliders for all 500 channels |
| **Group presets** | One-tap activation of Softener, Warmth, Blur, and Abyss frequency bands |
| **Real-time FFT visualiser** | 128-bin spectrum analyser rendered on a GPU-composited `<canvas>` |
| **Zero-latency monitoring** | `latencyHint: 'interactive'` for minimum audio buffer size |

### Android Platform

| Feature | Detail |
|---|---|
| **Secure asset serving** | `WebViewAssetLoader` provides virtual `https://` origin for `AudioWorklet` secure-context compliance |
| **Runtime permissions** | Modern `ActivityResultContracts` API with rationale dialog and permanent-denial recovery |
| **Audio focus** | `AudioFocusRequest` integration — engine pauses on incoming calls, resumes automatically |
| **Navigation lockdown** | `shouldOverrideUrlLoading` restricts WebView to local assets only |
| **Lifecycle management** | `onPause` / `onResume` / `onDestroy` handlers prevent audio hangs and memory leaks |
| **GPU rendering** | `LAYER_TYPE_HARDWARE` for smooth canvas compositing alongside 500 DOM nodes |
| **Offline-capable** | All assets bundled locally — no network dependency at runtime |

---

## Architecture

### Dual-Layer Permission Model

Android enforces microphone access at **two independent layers**. AuraConv Pro
bridges both:

```
Layer 1 — Android OS                    Layer 2 — Chromium WebView
┌──────────────────────┐               ┌──────────────────────────┐
│  RECORD_AUDIO        │               │  WebChromeClient         │
│  runtime permission  │──── grants ──▶│  .onPermissionRequest()  │
│  (Activity Result    │               │  RESOURCE_AUDIO_CAPTURE  │
│   API)               │               │  → request.grant()       │
└──────────────────────┘               └──────────────────────────┘
         │                                        │
         ▼                                        ▼
  Android grants mic                  JavaScript getUserMedia()
  hardware access                     resolves with MediaStream
```

### Secure Context Pipeline

`AudioWorklet` requires a [secure context](https://developer.mozilla.org/en-US/docs/Web/Security/Secure_Contexts)
(`https://`). Local `file:///` URLs do not qualify. The solution:

```
app/src/main/assets/index.html
        │
        ▼
WebViewAssetLoader.AssetsPathHandler
        │
        ▼
https://appassets.android.com/assets/index.html   ← virtual HTTPS origin
        │
        ▼
AudioWorklet secure-context check: PASS ✓
```

### Lazy Worklet Memory Model

```
BEFORE (eager — all 500 nodes at init):
  500 × avg(2 × 2^ceil(log2(taps)) × 4 bytes) ≈ 509 MB  ← FATAL on ≤6 GB devices

AFTER (lazy — nodes created on toggle):
  active_channels × avg_buffer ≈ 15–40 MB (typical usage)  ← SAFE
```

---

## DSP Engine

### Signal Flow (per channel)

```
                    ┌──────────────────────────────────────────────────┐
                    │          MovingAverageProcessor (AudioWorklet)    │
                    │                                                  │
  input ──┐        │   ┌──────────┐    ┌────────────────────┐         │
          ├──(+)───┼──▶│ DC Block │──▶│  Circular Buffer    │         │
          │   ▲    │   │ (1-pole  │   │  (power-of-2, mask) │         │
          │   │    │   │  HP ~5Hz)│   │                    │         │
          │   │    │   └──────────┘   │  sum += new        │         │
          │   │    │                  │  sum -= old        │         │
          │   │    │                  │  wet = sum/N × gain│         │
          │   │    │                  └────────┬───────────┘         │
          │   │    │                           │                     │
          │   │    │                  ┌────────▼───────────┐         │
          │   └────┼──── feedback ────│  prevWet × fb      │         │
          │        │                  └────────┬───────────┘         │
          │        │                           │                     │
          │        │   dry (delay-compensated) │  wet                │
          │        │        │                  │                     │
          │        │        │    ┌─────────────▼──────────────┐      │
          │        │        │    │ Polarity Crossfade          │      │
          │        │        │    │ out = wet + (dry-wet)×mode  │      │
          │        │        │    └─────────────┬──────────────┘      │
          │        │        │                  │                     │
          │        │        ▼                  ▼                     │
          │        │   ┌────────────────────────────┐                │
          │        │   │  Dry/Wet Mix               │                │
          │        │   │  out = dry + (wet-dry)×mix │                │
          │        │   └────────────┬───────────────┘                │
          │        │                │                                │
          │        └────────────────┼────────────────────────────────┘
          │                         ▼
          │                   trackGain ──▶ masterGain ──▶ analyser ──▶ output
          │
     micSource
```

### Tap Distribution

| Category | Tap Range | Count | Character |
|---|---|---|---|
| **Soften** | 25 – 250 | ~40 | Gentle high-frequency smoothing |
| **Warm** | 251 – 2,000 | ~85 | Mid-range thickening |
| **Blur** | 2,001 – 20,000 | ~130 | Spectral smearing, loss of transients |
| **Wash** | 20,001 – 150,000 | ~145 | Long decaying tonal washes |
| **Abyss** | 150,001 – 1,000,001 | ~100 | Sub-second resonant drones |

---

## Screenshots

<div align="center">

| Master Deck | 500-Channel Grid |
|:---:|:---:|
| ![Master Deck](docs/screenshots/master-deck.png) | ![Channel Grid](docs/screenshots/channel-grid.png) |
| *Global controls, FFT visualiser, presets* | *Ultra-dense parallel processing matrix* |

</div>

> **Note:** Replace the paths above with actual screenshot files placed in
> `docs/screenshots/`.

---

## Requirements

| Requirement | Minimum | Recommended |
|---|---|---|
| **Android version** | 7.0 (API 24) | 12.0+ (API 31) |
| **RAM** | 4 GB | 8 GB+ |
| **CPU** | Quad-core ARM Cortex-A53 | Octa-core (Snapdragon 8 Gen 1 / Dimensity 9000+) |
| **Microphone** | Built-in | External USB / TRRS condenser |
| **Android Studio** | Hedgehog (2023.1) | Ladybug (2024.2)+ |
| **JDK** | 17 | 21 |
| **Kotlin** | 2.0 | 2.1+ |
| **Gradle** | 8.4 | 8.7+ |

---

## Installation & Build

### 1. Clone the Repository

```bash
git clone https://github.com/<your-org>/AuraConvPro.git
cd AuraConvPro
```

### 2. Open in Android Studio

```
File → Open → select the AuraConvPro/ directory
```

Allow Gradle to sync. The IDE will download all AndroidX dependencies
automatically.

### 3. Build & Run

```bash
# Debug build (includes logging)
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or use Android Studio: Run → Run 'app'
```

### 4. Release Build (signed APK)

```bash
# Generate a signing key (one-time)
keytool -genkey -v -keystore auraconv-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias auraconv

# Build release APK
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

> **Important:** Configure signing in `app/build.gradle.kts` under
> `signingConfigs` before building for distribution. See the
> [Android signing documentation](https://developer.android.com/studio/publish/app-signing).

---

## Project Structure

```
AuraConvPro/
│
├── app/
│   ├── build.gradle.kts                  # Module dependencies & SDK config
│   ├── proguard-rules.pro                # Release minification rules
│   │
│   └── src/main/
│       ├── AndroidManifest.xml           # Permissions, features, activity
│       │
│       ├── java/com/auraconv/pro/
│       │   └── MainActivity.kt           # WebView shell, permissions, audio focus
│       │
│       ├── res/
│       │   ├── layout/
│       │   │   └── activity_main.xml     # Fullscreen WebView layout
│       │   ├── values/
│       │   │   ├── strings.xml
│       │   │   ├── themes.xml
│       │   │   └── colors.xml
│       │   └── mipmap-*/                 # Launcher icons
│       │
│       └── assets/
│           ├── index.html                # Self-contained DSP engine + UI
│           └── styles.css                # Pre-compiled utility stylesheet
│
├── build.gradle.kts                      # Project-level Gradle config
├── settings.gradle.kts                   # Module inclusion
├── gradle.properties                     # JVM args, AndroidX flags
│
├── docs/
│   └── screenshots/                      # README images
│
├── LICENSE
└── README.md
```

---

## Permissions

| Permission | Purpose | When Requested |
|---|---|---|
| `RECORD_AUDIO` | Capture live microphone input for the DSP engine | On app launch (runtime dialog) |
| `MODIFY_AUDIO_SETTINGS` | Control speakerphone routing and audio session type | Implicit (granted at install) |
| `INTERNET` | Required by `WebViewAssetLoader` virtual HTTPS domain | Implicit (granted at install) |

### Permission Flow

```
App Launch
    │
    ▼
RECORD_AUDIO already granted? ──Yes──▶ Load WebView
    │
    No
    │
    ▼
Show rationale dialog? ──Yes──▶ Explain → Re-request
    │
    No (first request or "Don't ask again")
    │
    ▼
System permission dialog
    │
    ├── Granted ──▶ Load WebView
    │
    └── Denied
         │
         ├── "Don't ask again" ──▶ Redirect to System Settings
         │
         └── Simple deny ──▶ Toast notification
```

---

## Performance Engineering

### Memory Budget

| Component | Allocation | Notes |
|---|---|---|
| Chromium WebView process | ~200 MB | Baseline for any WebView app |
| Active worklet buffers | ~1–8 MB each | Power-of-2 circular buffers, stereo `Float32Array` |
| 50 active channels (typical) | ~150 MB | Well within 4 GB device budget |
| DOM (500 cards) | ~5 MB | `content-visibility: auto` skips off-screen paint |
| **Total (50 active)** | **~355 MB** | Safe on 4 GB+ devices |

### CPU Budget

| Metric | Value |
|---|---|
| Audio sample rate | 48,000 Hz |
| Render quantum | 128 frames (2.67 ms) |
| Callbacks per second | 375 |
| Samples per callback (50 active channels) | 6,400 |
| Real-time thread budget | ~2.5 ms per callback |

### Key Optimisations

- **Lazy worklet instantiation** — nodes allocated on toggle, freed on deactivation
- **`content-visibility: auto`** — Chromium skips layout/paint for off-screen cards
- **Event delegation** — 2 listeners on the grid container replace 1,500 individual handlers
- **`innerHTML` buffer** — 500 DOM nodes injected in a single reflow (~20 ms vs ~2.5 s)
- **Cached `AudioParam` references** — eliminates `Map.get()` lookups in slider hot paths
- **Power-of-2 circular buffers** — bitwise `& mask` wrap-around replaces modulo division
- **GPU-composited WebView layer** — `LAYER_TYPE_HARDWARE` for canvas rendering

---

## Configuration Reference

### Master Controls

| Control | Range | Default | Effect |
|---|---|---|---|
| **Global Mix** | 0 – 100% | 100% | Dry/wet balance across all channels |
| **Master Volume** | 0 – 100% | 70% | Output gain before the analyser |
| **Resonance (Feedback)** | 0 – 95% | 0% | Recursive wet-signal feedback depth |
| **Timbre Tilt** | Dark ↔ Bright | Flat (50) | Spectral weighting across the tap range |
| **Polarity Mode** | Pass / Block | Pass | Continuous crossfade between wash and transient extraction |

### Presets

| Preset | Active Band | Tap Range |
|---|---|---|
| **Softener** | Soften | 25 – 250 |
| **Warmth** | Warm | 251 – 2,000 |
| **Blur** | Blur + Wash | 2,001 – 150,000 |
| **Abyss** | Abyss | 150,001 – 1,000,001 |

---

## Troubleshooting

<details>
<summary><strong>App crashes immediately on microphone activation</strong></summary>

<br />

**Cause:** Insufficient RAM for the Chromium WebView process + audio engine.

**Solution:**
- Close background applications to free RAM.
- Ensure the device has ≥ 4 GB RAM.
- Verify that `minSdk` is set to `24` in `build.gradle.kts`.
- Check `adb logcat -s AuraConvPro` for Low Memory Killer (LMK) events.

</details>

<details>
<summary><strong>No audio output after granting microphone permission</strong></summary>

<br />

**Cause:** Android WebView routing audio to the earpiece instead of the loudspeaker.

**Solution:**
- The app includes a `suspend()`/`resume()` cycle to force route re-evaluation.
- If the issue persists, toggle the device's speakerphone manually during the first second of audio.
- On some OEM skins (MIUI, OneUI), disable "Separate app sound" or "Dual audio" in system settings.

</details>

<details>
<summary><strong>Audio crackling or dropouts with many channels active</strong></summary>

<br />

**Cause:** CPU saturation on the Web Audio render thread.

**Solution:**
- Reduce the number of simultaneously active channels (≤ 100 on mid-range devices).
- Close other CPU-intensive applications.
- On devices with a "Battery Saver" or "Performance Mode" toggle, enable Performance Mode.
- The `latencyHint: 'interactive'` setting requests the smallest buffer; on some devices this increases underrun risk. This is a Chromium-level limitation.

</details>

<details>
<summary><strong>UI renders as unstyled HTML</strong></summary>

<br />

**Cause:** `styles.css` is missing from the `assets/` directory.

**Solution:**
- Verify that both `index.html` and `styles.css` are present in `app/src/main/assets/`.
- Clean and rebuild: `./gradlew clean assembleDebug`.
- The application does **not** require network access for styling — the Tailwind CDN has been fully replaced.

</details>

<details>
<summary><strong>Microphone permission dialog does not appear</strong></summary>

<br />

**Cause:** The user previously selected "Don't ask again."

**Solution:**
- Navigate to **Settings → Apps → AuraConv Pro → Permissions → Microphone** and enable manually.
- The app will display a dialog with an "Open Settings" shortcut when this state is detected.

</details>

<details>
<summary><strong>Build fails with "match_match" or ConstraintLayout errors</strong></summary>

<br />

**Cause:** Stale build cache from a previous version of the layout file.

**Solution:**
```bash
./gradlew clean
# Then rebuild
./gradlew assembleDebug
```
The current layout uses `FrameLayout` with `match_parent` — no ConstraintLayout dependency is required at runtime.

</details>

---

## Roadmap

- [ ] **MIDI controller mapping** — USB MIDI surface integration via Web MIDI API
- [ ] **Preset save/load** — Persist channel configurations to `localStorage` / `SharedPreferences`
- [ ] **Stereo width control** — Per-channel pan and mid/side processing
- [ ] **Impulse response import** — Load custom `.wav` IRs for convolution hybrid mode
- [ ] **Multi-channel output** — Route individual channel groups to separate `AudioTrack` outputs
- [ ] **Tablet-optimised layout** — Adaptive grid density for 10"+ displays
- [ ] **Unit tests** — Robolectric tests for permission flow and WebView configuration
- [ ] **CI/CD pipeline** — GitHub Actions for automated debug/release builds

---

## Contributing

Contributions are welcome. Please follow the workflow below:

1. **Fork** the repository.
2. **Create** a feature branch:
   ```bash
   git checkout -b feature/<descriptive-name>
   ```
3. **Commit** with clear, imperative-mood messages:
   ```bash
   git commit -m "Add DC blocker to feedback path in worklet processor"
   ```
4. **Push** and open a **Pull Request** against `main`.
5. Ensure all changes pass `./gradlew lint` with zero errors.

### Code Style

| Layer | Standard |
|---|---|
| Kotlin | [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) |
| HTML / JS | 4-space indent, `"use strict"`, no `var`, semicolons required |
| CSS | 2-space indent, BEM-like class naming for custom selectors |
| XML | 4-space indent, attributes on separate lines for > 3 attributes |

---

## License

```
MIT License

Copyright (c) 2026 AuraConv Pro Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Acknowledgments

- **Web Audio API** — W3C specification enabling real-time DSP in the browser
- **AudioWorklet** — W3C specification for sample-accurate processing on a dedicated audio thread
- **AndroidX WebKit** — `WebViewAssetLoader` for secure local-file serving
- **Chromium** — The WebView engine powering the audio and rendering pipeline
- **Tailwind CSS** — Design system reference for the utility-class stylesheet

---

<div align="center">

<br />

**AuraConv Pro** — *500 channels of resonant silence, waiting for sound.*

<br />

<sub>Built with Web Audio API · AudioWorklet · Kotlin · AndroidX WebKit</sub>

</div>
