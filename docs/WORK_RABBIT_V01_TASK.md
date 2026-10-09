# Work task — Black Rabbit 2D v0.1 A/B comparison

Work only on branch `experiment/work-rabbit-v01`.

## Read first
- `docs/RABBIT_V01_AB_SPEC.md`

## Shared visual source
Use the generated project/conversation image named:
- `黒ウサギvtuber表情パーツシート.png`

It contains the common rabbit design and the candidate body/head/eye/eyelid/mouth pieces.

## Independence rule
- DO NOT inspect `experiment/chat-rabbit-v01`
- DO NOT copy its commits, renderer, asset processing, coordinates, or implementation choices
- design your own rig from the shared spec and source image
- the comparison is specifically intended to measure whether Work chooses a better structure independently

## Goal
Implement a Galaxy-installable Android APK that meets the spec, including:
- front camera / MediaPipe
- BODY independent from HEAD
- yaw/pitch/roll head movement
- independent left/right blink
- eyelids matching the rabbit's actual eye contour
- full eye closure without iris/sclera leakage
- fixed upper muzzle/nose + mouth interior + moving lower jaw
- no whole-face expression swapping
- stream UI hide mode
- GitHub Actions APK build success

The user only has a phone. Do not require PC operation. Ask only for Galaxy installation/testing when needed.

Use a distinct applicationId and app label so the Work APK can be installed side-by-side with the Chat version.
