# Crow Avatar Work v0.4 — implementation and verification

## Experiment boundary

Work started at `cea91249bc55c7264eb9979bc0b757904730b91e`, on
`experiment/work-v04`. Only this branch was fetched and inspected. No competing
branch code, commits, diffs or implementation approach were consulted.
`CROW_V04_AB_SPEC.md` is the acceptance specification; it has not been changed.
The browser experiment in `2d/crow/` is unchanged; the deliverable is Android.

## Design

`AvatarAssets` builds transparent, independent layers once from the original
480×480 neutral portrait. Expression portraits are removed from the Android
asset bundle. `CrowRenderer` composes layers; `AvatarView` smooths six inputs
independently with time-based filters (faster blink closure than reopening).

| Group | Contents | Input / transform |
| --- | --- | --- |
| BODY | Shoulders, jacket, chest and reconstructed feather backing at neck | 0.3% vertical breathing; no face input |
| HEAD | Hair, head feathers, face outline and small feather overlap | yaw ±17°, pitch ±10.1°, roll ±8.6° |
| LEFT_EYE | Original eye region, feather-textured upper and lower lids | `eyeBlinkLeft`; anatomical left = viewer right |
| RIGHT_EYE | Original eye region, feather-textured upper and lower lids | `eyeBlinkRight`; anatomical right = viewer left |
| mouth_inside | Opaque dark interior, drawn behind both beaks | Geometry joins the mouth corners to moving lower tip |
| lower_beak | Original lower beak cutout | Projected X-axis rotation about mouth-corner hinge (251,244) |
| upper_beak | Original upper beak cutout, frontmost | Fixed in HEAD space; never transformed by jaw |

Both lids trace the original eye opening, including its lashes. The original
eyeball is clipped between independently advancing upper/lower boundaries.
At full closure it is not drawn at all. The lids meet in a fine feather crease;
there is no added black band and no generated closed-face substitution. Lids
use nearby neutral cheek feather texture, so no new art style is introduced.
Blink scores 0.08–0.72 map continuously to closure, saturating at full closure.

The head mask contains no shoulders. The body excludes the moving head mask;
a stationary neck backing and overlapping feather edge keep the neck covered.
Yaw gain is 17/13 = 1.31× the starting implementation; pitch is 10.1/8.4 =
1.20×, roll 8.6/8.2 = 1.05×. Neck pivot/translation are deliberately restrained.

Lower beak rest projection is inverted, then composed with a changing projection
(42° to 12°) about the same horizontal hinge. This preserves source alignment
at rest and exact horizontal centre throughout opening. Lower beak receives at
most 2px downward hinge translation; upper beak never receives that transform.
No talk frame, face swap, or inverse-triangle cut through the upper beak is used.

## Tracking and Android lifecycle

MediaPipe Face Landmarker supplies its column-major 4×4 facial transformation
matrix and the three separate blendshapes. Head rotation uses Euler angles
from that matrix, centred on the first detection; 「正面を合わせる」 recentres
only head pose, not expressions. Camera pixels are rotated to portrait without
horizontal mirroring, preserving anatomical left/right blendshape semantics.

Single-worker VIDEO inference processes at most 25 input frames per second;
CameraX keeps only the latest frame. Padded RGBA rows are handled explicitly.
Every ImageProxy, MPImage and temporary bitmap is released in the same serial
inference call. Tracker close is synchronized; pending starts are invalidated
by a generation token. Camera releases on backgrounding and restarts if it
was active. No-face results and a 550ms stale-input timeout return to neutral.
Screen stays awake. UI work is on the main thread; model initialization is off it.

「配信画面」 hides controls and system bars. Tap the avatar or use Back to return.
Front camera remains this app's camera; streaming uses the phone's screen capture.
Camera is the only runtime permission requested. No microphone, storage or
overlay permission is requested. MediaPipe dependencies merge the normal
INTERNET / ACCESS_NETWORK_STATE permissions into the APK; app code itself makes
no network requests and the face model is packaged locally.

## Automated evidence

CI runs `testDebugUnitTest lintDebug assembleDebug`, packages the Face Landmarker
model, signs with the existing fixed test key and uploads the APK and reports.
The package remains `com.totomarujapan.crowavatar.workv04` / `Crow Avatar Work`.
It can coexist with other experiment apps and update the existing Work install.

`CrowRigTest` uses native Android graphics under Robolectric, not a substitute
Python drawing. Tests check independent eyes, zero bright iris/white pixels
under closed lids, invariant hair/eyes/upper beak under jaw motion, invariant
shoulders under each head axis, moving lower beak, fixed horizontal mouth centre,
column-major pose extraction, and 720 frames at 24fps (30 seconds of rig time).
Native images cover neutral, partial/full blink, each individual eye, partial/full
jaw, each axis endpoint and a combined extreme. The checks artifact includes
these PNGs and separate head/body masks for inspection.

First CI iteration compiled successfully but found one remaining bright pixel
at (210,197) at the edge of the right eye. The eyelid masks were expanded to
follow the full original lash contour rather than just the white opening. The
zero-leak requirement was kept and the inspected eye regions were enlarged.
A duplicate stationary head fragment in the body backing was also removed.

## Galaxy acceptance test — still required

The buttons under the main controls scroll horizontally. They exercise rendering
without a camera, to distinguish artwork from input/calibration problems.

1. Open `Crow Avatar Work`. Use 「左目」「右目」「両目」. Closed eyes must show no
   white or gold iris; lids must follow the original eye shapes. 「通常」 resets.
2. Use 「口半開き」「口全開」. Only lower beak opens, upper stays fixed, interior
   stays behind, centre does not shift, and eyes/hair/face outline stay unchanged.
3. Use the direction buttons and then 「追従開始」 (allow Camera); face the camera
   and optionally 「正面を合わせる」. Turn left/right, look up/down and tilt. Neither
   shoulder should follow; neck should remain joined without a visible tear.
4. Wink each anatomical eye separately and open/close mouth. Confirm input-side
   mapping and tracking sensitivity on the Galaxy rather than only preset output.
5. Keep tracking at least 30 seconds. Watch for freeze, jumps, overheating or crash.
6. Enter 「配信画面」: controls/bars hide and tracking continues; tap returns.
   Confirm the intended screen-capture app records the avatar on this Galaxy.

These human visual, camera, thermal and screen-capture conditions are **pending**.
Automated rendering endurance is not a claim of 30-second Galaxy camera stability.
The existing 480px source limits close-up detail. Lid feather texture and the
neck overlap need subjective device review. Camera tracking while this activity
is not visible is deliberately stopped; screen capture must keep it visible.


## Successful build receipt (2026-10-09 JST)

- Build commit: `0452f5342819b08cbbfdb437d81590afcb97bd3c`.
- [Successful Actions run](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37821369542)
  finished `testDebugUnitTest`, `lintDebug`, `assembleDebug` and both artifact uploads.
- Five native-graphics tests pass. Lint has no errors; remaining warnings are
  recorded in the checks artifact. The second iteration's manifest lint error
  was fixed by explicit optional rear-camera / autofocus feature declarations.
- [APK artifact](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37821369542/artifacts/11570110175).
- [Checks and render images](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37821369542/artifacts/11570015267).
- Extracted APK: 55,774,255 bytes, Android 8+ (minSdk 26), targetSdk 35,
  version `0.4-work` (40). Includes `arm64-v8a` for Galaxy, plus three other ABIs.
- SHA-256: `111be89533abeda1a5d5d972f87eb712c0c0ebca1fc6143effcbb492924f6caa`.
- `apksigner verify --verbose --print-certs`: signature verified (v2).
  Existing test certificate SHA-256:
  `4df18e204b454053c3474ee42864732eb58358055c295482dc681674430d6046`.
- `aapt dump badging`: Work package and label confirmed; packaged Face Landmarker
  model is 3,758,596 bytes. The provided APK is extracted from this Actions
  artifact, not rebuilt locally. Local build attempts could not finish dependency
  downloads in the workspace network environment; GitHub Actions is the successful
  build and verification environment.
- Two correction rounds after initial implementation: eyelid/neck rendering,
  then manifest hardware declarations. No code changed after the successful build;
  this receipt is a documentation-only follow-up.
- Galaxy acceptance conditions remain pending. In particular, eyelid appearance,
  live wink side/sensitivity, neck continuity at extremes, actual 30+ second camera
  stability, and screen-capture compatibility must be checked on the phone.

## Follow-up v0.4.1 — mouth visibility and false partial blinks

User feedback: the open mouth interior was too dark, and head yaw/pitch produced
unintended partly closed eyes on Galaxy. This is not a structural requirement of
2D rendering: HEAD pose and eyelid inputs are separate. The old renderer admitted
blink scores above 0.08 without corroborating actual lid opening. That makes a
pose-related tracking score change visible. We have not received a recording of
this particular symptom, so that is the code-based diagnosis rather than a
measured explanation of every affected frame.

The interior now has a muted purple backing (48,29,53), a brighter shaded mouth
floor and a muted pink tongue behind the lower beak. All changing shading stays
below the fixed upper tip (y=292), preserving upper beak, eyes and hair. Mouth
centre, hinge, BODY and HEAD masks are unchanged.

`EyeInputGeometry` computes the average of three perpendicular upper/lower-lid
3D separations divided by the corner-to-corner 3D eye width. Normalized y is
converted to image-width units using the rotated image aspect; x and z already
use width units. True 3D rigid yaw/pitch/roll preserve that ratio; the MediaPipe
points themselves are estimates, so this does not promise perfect pose invariance
for real camera inference.

Two separate `EyeBlinkInput` states calibrate left/right open-eye aperture on
start and 「正面を合わせる」. The phone user should keep eyes open for calibration.
Normal wide-open geometry suppresses a false blink even if the blendshape rises.
A 16% opening tolerance absorbs moderate landmark noise; partial closure is
admitted continuously and an independently corroborated strong blink saturates
at full closure. Held winks do not adapt into the open reference. Slow reference
adaptation accepts only low-blink frontal frames within 10% of the current open
reference, avoiding maximum-value drift or pose outliers.

At extreme estimated yaw (>54°) or pitch (>43°), or with degenerate eye geometry,
blink output returns open rather than keeping a guessed partial closure. A real
wink at those extreme poses will therefore not be reproduced reliably. This is
an explicitly documented camera visibility limit, not a claim that all 2D rigs
must half-close. Normal head turns retain independent wink control.

New deterministic tests verify ratio invariance over all combinations of 3D
rotations/scale/translation, inflated scores with open geometry, held independent
winks, continuous partial closure, calibration outliers and invalid/extreme inputs.
Native mouth tests additionally check visible floor/tongue pixels and fixed upper
beak/eyes/hair/torso. Device confirmation remains necessary: first calibrate with
eyes open, turn head without blinking, then wink each eye normally and open mouth.

The official topology/coordinate references used for this independent correction:
- https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/java/com/google/mediapipe/tasks/vision/facelandmarker/FaceLandmarksConnections.java
- https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker

## Successful v0.4.1 build receipt (2026-10-09 JST)

- Code commit: `6c3a92158d6247fb38f8a120aed8f6f0e49199ce`.
- [Successful Actions run](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37906570531)
  completed `testDebugUnitTest lintDebug assembleDebug` and both artifact uploads.
- Eleven tests pass: six eye-input tests and five native-graphics rig tests;
  zero failures/errors. Lint has zero errors and 23 warnings in its report.
- [APK artifact](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37906570531/artifacts/11604627996).
- [Checks and native render images](https://github.com/ryoujijyouji19941223-lang/avatar-lab/actions/runs/37906570531/artifacts/11604294048).
- The extracted APK is 55,774,251 bytes, version `0.4.1-work` (41), same Work
  package, minSdk 26 / targetSdk 35, with Galaxy's arm64-v8a ABI and the bundled
  3,758,596-byte Face Landmarker model.
- SHA-256: `5b7389aa87b2b11607b02b89b8679654bca7f2786d4c7dbdd57935bbffc1b868`.
- `apksigner verify --verbose --print-certs` verifies v2 signature; certificate
  SHA-256 matches the original Work APK:
  `4df18e204b454053c3474ee42864732eb58358055c295482dc681674430d6046`.
  This is an update install, with no need to uninstall the old Work app.
- Native full/partial-mouth PNGs were visually inspected: the purple cavity and
  tongue are visible behind the beak. Real camera suppression of false blinks
  remains pending Galaxy feedback; synthetic input tests cannot establish that
  the reported live symptom is fully resolved. Calibrate with eyes open, then
  check head turns and independent winks on the phone.
- This receipt is documentation only; code is unchanged after the successful run.
