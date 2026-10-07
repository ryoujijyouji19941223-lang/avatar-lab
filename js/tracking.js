/*
 * tracking.js — Webカメラで顔の向き・まばたき・口の開きを取る（実験的）
 *
 * MediaPipe Face Landmarker を CDN から読み込む。初回はモデル(約4MB)の
 * ダウンロードがあるのでネット接続が必要。
 */
(function (global) {
  'use strict';

  const VERSION = '0.10.14';
  const CDN = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${VERSION}`;
  const MODEL =
    'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';

  let landmarker = null;
  let stream = null;
  let video = null;
  let running = false;
  let lastVideoTime = -1;

  const deg = (r) => (r * 180) / Math.PI;

  // 4x4 (列優先) の変換行列から yaw / pitch / roll（度）を取り出す
  function eulerFromMatrix(m) {
    const r00 = m[0], r10 = m[1], r20 = m[2];
    const r21 = m[6], r22 = m[10];
    return {
      yaw: deg(Math.asin(Math.max(-1, Math.min(1, -r20)))),
      pitch: deg(Math.atan2(r21, r22)),
      roll: deg(Math.atan2(r10, r00))
    };
  }

  async function createLandmarker(vision, fileset, delegate) {
    return vision.FaceLandmarker.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: MODEL, delegate },
      runningMode: 'VIDEO',
      numFaces: 1,
      outputFaceBlendshapes: true,
      outputFacialTransformationMatrixes: true
    });
  }

  async function start(onData, onStatus) {
    if (running) return;
    onStatus('カメラを起動中…');
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 640, height: 480, facingMode: 'user' },
      audio: false
    });
    video = document.createElement('video');
    video.srcObject = stream;
    video.muted = true;
    video.playsInline = true;
    await video.play();

    if (!landmarker) {
      onStatus('顔認識モデルを読み込み中…');
      const vision = await import(`${CDN}/vision_bundle.mjs`);
      const fileset = await vision.FilesetResolver.forVisionTasks(`${CDN}/wasm`);
      try {
        landmarker = await createLandmarker(vision, fileset, 'GPU');
      } catch (err) {
        landmarker = await createLandmarker(vision, fileset, 'CPU');
      }
    }

    running = true;
    onStatus('トラッキング中');
    const loop = () => {
      if (!running) return;
      if (video.readyState >= 2 && video.currentTime !== lastVideoTime) {
        lastVideoTime = video.currentTime;
        const res = landmarker.detectForVideo(video, performance.now());
        if (res.faceBlendshapes && res.faceBlendshapes.length) {
          const bs = {};
          for (const c of res.faceBlendshapes[0].categories) bs[c.categoryName] = c.score;
          const pose = res.facialTransformationMatrixes && res.facialTransformationMatrixes.length
            ? eulerFromMatrix(res.facialTransformationMatrixes[0].data)
            : { yaw: 0, pitch: 0, roll: 0 };
          onData({
            found: true,
            yaw: pose.yaw,
            pitch: pose.pitch,
            roll: pose.roll,
            blinkL: bs.eyeBlinkLeft || 0,
            blinkR: bs.eyeBlinkRight || 0,
            jaw: bs.jawOpen || 0,
            smile: ((bs.mouthSmileLeft || 0) + (bs.mouthSmileRight || 0)) / 2,
            browUp: bs.browInnerUp || 0,
            browDown: ((bs.browDownLeft || 0) + (bs.browDownRight || 0)) / 2
          });
        } else {
          onData({ found: false });
        }
      }
      requestAnimationFrame(loop);
    };
    loop();
  }

  function stop() {
    running = false;
    if (stream) stream.getTracks().forEach((t) => t.stop());
    stream = null;
    video = null;
  }

  global.FaceTracker = { start, stop, isRunning: () => running };
})(window);
