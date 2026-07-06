# Android App Roadmap — BGRemover Mobile Client

Native Android client (Kotlin + Jetpack Compose) for the existing Spring Boot
server. Epic: [#5](https://github.com/vaibhavparashar8-a11y/bgremover-web/issues/5).
New fourth top-level component `android/`. **Backend unchanged** — it already
binds 0.0.0.0:8080, responses are host-agnostic (raw PNG bytes / plain JSON),
and it proxies all inference calls, so the phone never touches :8000. All
traffic goes over Tailscale (from anywhere) or LAN to `http://<host>:8080/api`.
No Firebase, no cloud.

Development starts ~2026-07-09. Each phase below = one GitHub issue +
`feature/<n>-<name>` branch + PR, per the repo workflow in CLAUDE.md.

## 1. Architecture (decided)

### Module structure: single Gradle module

One module (`android/app`), package-by-layer. Multi-module buys nothing at
this scale (4 endpoints, ~6 screens, one developer):

```
com.bgremover.android
├── api/        BgRemoverApi (interface) + OkHttpBgRemoverApi + DTOs — mirrors frontend/src/api.ts
├── data/       RemoveRepository, ModelsRepository, SettingsRepository (DataStore)
├── editor/     MaskEditor, coordinate transforms, compositing (pure logic, no Compose)
├── ui/         Compose screens + ViewModels (settings/, remove/, select/, refine/)
└── di/         AppContainer (manual DI)
```

### Stack decisions

- **MVVM + Repository, StateFlow ViewModels** — each screen exposes one
  sealed `UiState`.
- **API client: hand-written OkHttp + kotlinx.serialization behind a
  `BgRemoverApi` interface — NOT Retrofit.** The base URL is user-configured
  at runtime (Retrofit's is awkwardly immutable), the main endpoint returns
  raw PNG bytes not JSON, and a thin client mirrors `frontend/src/api.ts`
  1:1 (same function names, `RemoveOptions`/prompt types, `{"detail": ...}`
  error extraction into a typed `ApiException`). The interface makes a
  `FakeBgRemoverApi` trivial for tests.
- **Never call `PUT /api/models/active`** — it mutates global server state
  shared with the web UI; always pass `model` per `/api/remove` request.
- **OkHttp timeouts:** connect 10s, write 120s (25MB upload), **read 600s**
  (cold-model weight download up to ~928MB; matches backend's read timeout).
- **Coil 3** for image display; **DataStore (Preferences)** for settings
  (server base URL, default model, alpha-matting default); **manual DI**
  (`AppContainer` on `Application`) — no Hilt; graph is ~6 objects, skipping
  KSP keeps builds fast.
- **Cleartext HTTP:** `network-security-config.xml` with app-wide
  `cleartextTrafficPermitted="true"` — the host is user-entered (MagicDNS
  name or 100.x/LAN IP), unknown at build time. Acceptable: traffic rides
  Tailscale's WireGuard tunnel or trusted LAN.
- **SDK levels:** minSdk 29 (scoped storage + `MediaStore.IS_PENDING`),
  target/compileSdk 36 (android-36.1 already at `E:\Android\Sdk`). AGP 8.x
  on JDK 21 (`E:\Java\jdk-21.0.11+10`).
- **E:-drive pinning:** checked-in `android/build.cmd` sets
  `GRADLE_USER_HOME=E:\gradle-cache` and `JAVA_HOME` before invoking the
  checked-in Gradle wrapper (no global Gradle install); git-ignored
  `local.properties` → `sdk.dir=E:\Android\Sdk`; Android Studio's
  "Gradle user home" IDE setting must also point at `E:\gradle-cache`
  (IDE ignores the cmd script — document in README).
- **Memory rule (critical):** a 48MP photo ≈ 190MB per ARGB bitmap and the
  editor holds three. Cap working resolution at **4096px long edge on
  import** (also keeps uploads under the 25MB server limit); edit, composite,
  and export at that resolution; full-resolution round-trip (replay mask
  upscaled) is future work, not in scope.

### Canvas approach: SAM selection + brush refine (the hard part)

- **Coordinate mapping:** image shown with `ContentScale.Fit`; pure functions
  `viewToImage`/`imageToView` handle scale + letterbox offset. Prompts are
  sent in image-pixel coordinates, exactly like the web frontend's
  `{x, y, label 0|1}` points / `{x1,y1,x2,y2}` boxes serialized into the
  `points` form field. Lives in `editor/` — fully unit-testable.
- **SAM interaction:** `Box { Image(original); Canvas(pointerInput) }` — tap
  adds a fg/bg point, drag draws a box. Each prompt change calls
  `/api/remove` with `model=sam`; the returned cutout is drawn tinted
  (`ColorFilter.tint(semi-transparent blue), BlendMode.SrcAtop`) over the
  original as the mask preview, matching the web UI. Debounce re-requests
  and cancel in-flight calls on new input.
- **Brush refine (client-side, no server call — same as web):** keep
  `original`, `serverCutout`, and a mutable `ALPHA_8` mask (seeded from the
  cutout's alpha). Restore = opaque round paint (soft edge via
  `BlurMaskFilter`) on the mask; erase = same paint with
  `PorterDuff.Mode.CLEAR`. Final composite: draw original, then mask with
  `DST_IN` — original RGB, mask alpha, identical to the web's canvas
  compositing. **Undo:** stroke-op list replayed from checkpointed snapshots
  (bitmap snapshot every N ops) — cheaper than per-stroke full snapshots.
  All in `editor/MaskEditor`, no Compose dependency → Robolectric-testable.
- **Before/after slider:** result on checkerboard, original clipped with
  `clipRect(right = sliderX)` from a horizontal drag. Trivial.

## 2. Phases (one issue + branch + PR each)

### Phase 0 — Scaffold + buildable on E: (small)
- **Goal:** `android/` exists, builds, launches a hello-Compose screen; all
  caches on E:.
- **Key pieces:** Gradle wrapper (checked in), `settings.gradle.kts`, version
  catalog, `app/build.gradle.kts` (minSdk 29 / target 36, Compose BOM),
  manifest + `network-security-config.xml`, `android/build.cmd`, `.gitignore`
  entries, one trivial JUnit test proving `gradlew test` works.
- **Same PR:** CLAUDE.md architecture section → four components + new
  build-command rows; README Android section (Gradle-home IDE setting,
  Tailscale setup, emulator loopback).
- **Verify:** `build.cmd assembleDebug` + run on emulator. Check E: free
  space first (first build downloads ~1–2GB into `E:\gradle-cache`).

### Phase 1 — Settings, connection, models list (small/medium)
- **Goal:** configure server URL; prove connectivity; list models.
- **User-visible:** Settings screen (base URL field + "Test connection"
  hitting `/api/health`, showing status/active model); home screen model
  dropdown from `/api/models` (labels + quality/speed/size badges); graceful
  offline state.
- **Key pieces:** `BgRemoverApi` + OkHttp impl + DTOs (`ModelInfo`,
  `ModelsResponse`, `ApiException`), `SettingsRepository`,
  `ModelsRepository`, ViewModels, `FakeBgRemoverApi`.
- **Tests:** URL normalization/validation, `{"detail"}` parsing, ViewModel
  loading/success/offline states against the fake.
- **User step:** install Tailscale on laptop + phone (see Risks — installer
  lands on C:).

### Phase 2 — Auto remove happy path + save/share (medium)
- **Goal:** pick photo → remove background → view → save/share PNG.
- **User-visible:** Photo Picker (`PickVisualMedia`), model dropdown +
  alpha-matting toggle, processing state, result on checkerboard, Save
  (MediaStore `Pictures/BGRemover` with `IS_PENDING`) and Share
  (FileProvider + `ACTION_SEND`).
- **Key pieces:** multipart `removeBackground()` (fields exactly matching
  api.ts: `file`, `model`, `alphaMatting`, `invert`), `RemoveRepository`,
  `RemoveViewModel` state machine (Idle→Uploading→Processing→Result/Error),
  capped-resolution import (content URI → bitmap → temp file in app cache),
  `ImageSaver`.
- **Tests:** repository against fake API (success + 413/415/503/detail
  mapping), ViewModel transitions, multipart field construction. MediaStore
  save documented as manually verified (untestable in JVM).

### Phase 3 — SAM interactive selection (medium/large)
- **Goal:** tap points / drag box → mask overlay preview → apply, with
  keep/remove invert.
- **User-visible:** "Select subject" mode when `sam` is chosen:
  foreground/background point toggle, box drag, tinted mask overlay updating
  per prompt, undo-last-point/clear, invert toggle, Apply → result screen.
- **Key pieces:** `editor/CoordinateMapper`, prompt sealed types + JSON
  serialization identical to the frontend `points` payload, `SelectViewModel`
  (debounce + cancellation), overlay Canvas composable.
- **Tests:** coordinate mapping (fit-scale + letterbox cases), golden prompt
  JSON tests against known frontend payloads, debounce/cancel with fake API.

### Phase 4 — Brush refine + before/after (medium/large)
- **Goal:** client-side restore/erase on the result, undo, before/after
  slider; export the edited result.
- **Key pieces:** `editor/MaskEditor` (mask bitmap, stroke ops, checkpointed
  undo, DST_IN composite), refine Canvas composable (reuses Phase 3 mapper),
  `BeforeAfterSlider` composable.
- **Tests:** Robolectric `MaskEditor` tests on small bitmaps (erase clears
  alpha, restore recovers original pixels, undo restores prior state,
  composite preserves RGB), stroke coordinate mapping. (Robolectric enters
  the project here — `android.graphics.Bitmap` needs it on the JVM.)

### Phase 5 — Polish: limits, errors, cold-model UX (medium)
- **Goal:** production-feel robustness.
- **User-visible:** automatic downscale notice for oversize images (also
  enforce ≤25MB encoded); human-readable error sheet per status
  (413/415/503/timeout/unreachable with "check Tailscale/server" hints +
  retry); cold-model progress — elapsed timer + "first use of <model>
  downloads ~<size_mb>MB, this can take minutes" sourced from `/api/models`;
  rotation/process-death survival (`SavedStateHandle`); app icon, dark theme.
- **Tests:** downscale/size-budget logic, error-mapper table test,
  cold-vs-warm messaging logic.

## 3. Risks & flagged decisions

| Item | Position |
|---|---|
| Cleartext HTTP | App-wide `cleartextTrafficPermitted` (host unknown at build time). Safe over Tailscale/LAN; noted in README. |
| Bitmap memory | Cap working resolution at 4096px long edge on import; edit/export at that size. Full-res round-trip = future work. |
| MagicDNS vs IP | Settings stores a full base-URL string; both work. Recommend MagicDNS (survives IP churn); "Test connection" surfaces resolution failures. |
| Tailscale installer on C: | System service/network driver — practically must live on C:; small footprint. Accepted deviation from the E:-only rule (flagged per CLAUDE.md environment rules); record in CLAUDE.md's tool table when installed. |
| minSdk | 29. Re-check against the actual phone before Phase 0 merges. |
| Emulator vs device | Emulator can't join the tailnet → dev via `http://10.0.2.2:8080` (host loopback); real device over Tailscale for E2E. Both offered as preset hints in Settings. |
| Global model switching | Never `PUT /api/models/active` from mobile; per-request `model` only. |
| Cold model | 600s read timeout + Phase 5 patience UX. Leaving the screen cancels the call (server keeps warming; retry is fast). |
| First Gradle build | Downloads ~1–2GB into `E:\gradle-cache`; needs internet once; check E: free space. |
| Device-only behavior | Photo Picker, MediaStore, rendering fidelity: manually verified, documented per repo testing rules in PR descriptions. |

## 4. Reference files for implementation

- `frontend/src/api.ts` — the contract the Android `api/` package mirrors
  (types, field names, error handling)
- `frontend/src/App.tsx` — parity reference for SAM interaction, brush
  refine, before/after slider behavior
- `backend/src/main/java/com/bgremover/web/` controllers — endpoint truth
  (also live at `/swagger-ui.html` on a running server)
- `inference/config.yaml` — model registry (names, sizes shown in UI)
