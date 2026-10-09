# Black Rabbit 2D v0.1 — Chat / Work A-B comparison

## Goal
Build a lightweight Android 2D face-tracked avatar from the supplied black-rabbit asset sheet and compare two independent implementations.

## Shared source
The source asset is the black rabbit character sheet generated in this project:
- black fur
- purple eyes
- white hooded jacket
- lavender shirt
- black cargo pants
- front-facing rabbit head / body / eyes / eyelids / mouth pieces

The build reconstructs the shared atlas from:
- android/crow-avatar/app/src/main/assets/rabbit_atlas.b64.1
- rabbit_atlas.b64.2
- rabbit_atlas.b64.3

Both implementations must start from the same source image and requirements.

## Required behavior
- front camera + MediaPipe Face Landmarker
- head yaw / pitch / roll
- body does not follow head; subtle breathing only
- independent left/right blinking
- eyelids must follow the rabbit's actual eye shape
- full blink must completely hide iris/sclera
- no black-bar blink cheats
- mouth/jaw opens without replacing the whole face
- upper muzzle/nose stays visually fixed; lower mouth/jaw is the moving part
- UI can be hidden for screen-capture streaming
- Galaxy-installable APK via GitHub Actions

## Rig structure target
BODY
HEAD
LEFT_EYE
  - eye
  - upper lid
  - lower lid / closed state
RIGHT_EYE
  - eye
  - upper lid
  - lower lid / closed state
MOUTH
  - fixed upper muzzle/nose
  - inner mouth
  - movable lower jaw/mouth

## Test criteria
1. shoulders do not move with head
2. left wink works independently
3. right wink works independently
4. both eyes fully close with no iris leak
5. mouth opens around its center without face drift
6. nose / upper muzzle stays fixed during jaw motion
7. head tracking is stable for 30 seconds
8. UI hide / stream mode works
9. app does not crash during camera tracking

## Fair comparison
- Chat branch: experiment/chat-rabbit-v01
- Work branch: experiment/work-rabbit-v01
- Work must not inspect or copy Chat branch
- Chat must be completed before inspecting Work's implementation
- compare naturalness, rig structure, stability, implementation iterations, and corrections required
