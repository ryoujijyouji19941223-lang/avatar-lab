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
No microphone, network, storage or overlay permission is requested.

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
