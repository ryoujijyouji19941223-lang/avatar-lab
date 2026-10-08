# Crow Avatar Work v0.4

Android/Galaxy face-following 2D Crow rig. Package:
`com.totomarujapan.crowavatar.workv04`, label `Crow Avatar Work`.

Development and build only on `experiment/work-v04`. Requirements:
[`../../docs/CROW_V04_AB_SPEC.md`](../../docs/CROW_V04_AB_SPEC.md).
Design, evidence and phone-only acceptance steps:
[`../../docs/WORK_V04_IMPLEMENTATION.md`](../../docs/WORK_V04_IMPLEMENTATION.md).

GitHub Actions builds and uploads `crow-avatar-work-v04-apk` and
`crow-avatar-work-v04-checks` (native PNG previews and test/lint reports).
Download the APK artifact on the phone, extract it, then open `app-debug.apk`.
The fixed existing test signing key permits updates to the Work app.
Camera permission is requested only when tracking starts; render test buttons
work without it. First detected face sets the head's neutral pose.
