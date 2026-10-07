/*
 * app.js — アニメーションループ、入力（マウス/マイク/カメラ/キーボード）、UI
 */
(function () {
  'use strict';

  const { Avatar, SPECIES, EXPRESSIONS } = window.BeastAvatar;
  const $ = (id) => document.getElementById(id);
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
  const approach = (cur, target, rate, dt) => cur + (target - cur) * (1 - Math.exp(-rate * dt));
  const smooth = (a, b, v) => {
    const t = clamp((v - a) / (b - a));
    return t * t * (3 - 2 * t);
  };

  const NUM_KEYS = Object.keys(EXPRESSIONS); // 数字キー 1〜7 の順番
  const BACKGROUNDS = [
    ['studio', 'ジム'],
    ['green', 'グリーン'],
    ['blue', 'ブルー'],
    ['transparent', '透過']
  ];

  /* ---------------- 設定の保存と読み込み ---------------- */

  const STORE_KEY = 'kemono-coach-avatar-v1';
  function loadSaved() {
    try {
      return JSON.parse(localStorage.getItem(STORE_KEY)) || {};
    } catch (err) {
      return {};
    }
  }
  const saved = loadSaved();
  const params = new URLSearchParams(location.search);
  const pick = (v, allowed, fallback) => (allowed.includes(v) ? v : fallback);

  const settings = {
    species: pick(params.get('species') || saved.species, Object.keys(SPECIES), 'bear'),
    bg: pick(params.get('bg') || saved.bg, BACKGROUNDS.map((b) => b[0]), 'studio'),
    colors: saved.colors || {},
    follow: saved.follow !== false,
    autoBlink: saved.autoBlink !== false,
    idle: saved.idle !== false,
    sens: Number(saved.sens) || 24
  };
  function save() {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(settings));
    } catch (err) {
      /* 保存できない環境では何もしない */
    }
  }

  /* ---------------- アバター ---------------- */

  const svg = $('avatar');
  const avatar = new Avatar(svg);

  const state = {
    exprKey: 'neutral',
    expr: Object.assign({}, EXPRESSIONS.neutral),
    hx: 0, hy: 0, roll: 0, gx: 0, gy: 0,
    blink: 0, blinkStart: -10, nextBlink: 2,
    talk: 0,
    earT: { L: -10, R: -10 }, nextEar: 4,
    breath: 0, sway: 0
  };

  const input = {
    pointer: { x: 0, y: 0, last: -100 },
    space: false,
    testTalk: false
  };

  function colorsFor(key) {
    return Object.assign({}, SPECIES[key].colors, settings.colors[key] || {});
  }

  function setSpecies(key) {
    settings.species = key;
    avatar.setSpecies(key);
    avatar.setColors(colorsFor(key));
    setExpression(SPECIES[key].defaultExpr, true);
    syncColorInputs();
    document.querySelectorAll('#speciesSeg button').forEach((b) => {
      b.setAttribute('aria-pressed', String(b.dataset.species === key));
    });
    save();
  }

  function setExpression(key, instant) {
    if (!EXPRESSIONS[key]) return;
    state.exprKey = key;
    if (instant) state.expr = Object.assign({}, EXPRESSIONS[key]);
    document.querySelectorAll('#exprGrid button').forEach((b) => {
      b.setAttribute('aria-pressed', String(b.dataset.expr === key));
    });
  }

  /* ---------------- マイク口パク ---------------- */

  const mic = { stream: null, ctx: null, analyser: null, buf: null, rms: 0 };

  async function toggleMic() {
    const btn = $('micBtn');
    if (mic.stream) {
      mic.stream.getTracks().forEach((t) => t.stop());
      if (mic.ctx) mic.ctx.close();
      Object.assign(mic, { stream: null, ctx: null, analyser: null, buf: null, rms: 0 });
      btn.textContent = 'マイクで口パク';
      btn.setAttribute('aria-pressed', 'false');
      return;
    }
    try {
      mic.stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
      });
      const AC = window.AudioContext || window.webkitAudioContext;
      mic.ctx = new AC();
      const src = mic.ctx.createMediaStreamSource(mic.stream);
      mic.analyser = mic.ctx.createAnalyser();
      mic.analyser.fftSize = 1024;
      mic.buf = new Float32Array(mic.analyser.fftSize);
      src.connect(mic.analyser);
      btn.textContent = 'マイクを止める';
      btn.setAttribute('aria-pressed', 'true');
    } catch (err) {
      mic.stream = null;
      toast('マイクを使えませんでした。ブラウザのマイク許可を確認してください。');
    }
  }

  function micOpen() {
    if (!mic.analyser) return 0;
    mic.analyser.getFloatTimeDomainData(mic.buf);
    let sum = 0;
    for (let i = 0; i < mic.buf.length; i++) sum += mic.buf[i] * mic.buf[i];
    mic.rms = Math.sqrt(sum / mic.buf.length);
    return clamp((mic.rms * settings.sens - 0.12) / 0.6);
  }

  // テスト再生：1.8秒しゃべって0.6秒休む
  function testTalkOpen(t) {
    if (t % 2.4 > 1.8) return 0;
    return clamp(Math.abs(Math.sin(t * 9.5)) * (0.6 + 0.4 * Math.sin(t * 3.1)));
  }

  /* ---------------- Webカメラ追跡 ---------------- */

  const cam = { active: false, data: null, zero: { yaw: 0, pitch: 0, roll: 0 } };

  async function toggleCam() {
    const btn = $('camBtn');
    const status = $('camStatus');
    if (cam.active || window.FaceTracker.isRunning()) {
      window.FaceTracker.stop();
      cam.active = false;
      cam.data = null;
      btn.setAttribute('aria-pressed', 'false');
      $('camCalib').hidden = true;
      $('camMirrorWrap').hidden = true;
      status.textContent = '';
      return;
    }
    btn.disabled = true;
    try {
      await window.FaceTracker.start(
        (d) => {
          cam.data = d;
        },
        (s) => {
          status.textContent = s;
        }
      );
      cam.active = true;
      btn.setAttribute('aria-pressed', 'true');
      $('camCalib').hidden = false;
      $('camMirrorWrap').hidden = false;
    } catch (err) {
      window.FaceTracker.stop();
      status.textContent = 'カメラ追跡を開始できませんでした（' + (err && err.message ? err.message : err) + '）';
    } finally {
      btn.disabled = false;
    }
  }

  function calibrateCam() {
    if (cam.data && cam.data.found) {
      cam.zero = { yaw: cam.data.yaw, pitch: cam.data.pitch, roll: cam.data.roll };
      toast('いまの顔の向きを正面にしました');
    }
  }

  /* ---------------- 毎フレームの更新 ---------------- */

  function update(dt, t) {
    const camOn = cam.active && cam.data && cam.data.found;
    const mirror = $('camMirror').checked ? -1 : 1;

    // 顔の向きの目標
    let tx = 0;
    let ty = 0;
    let troll = 0;
    if (camOn) {
      tx = clamp((cam.data.yaw - cam.zero.yaw) / 22, -1, 1) * mirror;
      ty = clamp(-(cam.data.pitch - cam.zero.pitch) / 18, -1, 1);
      troll = clamp((cam.data.roll - cam.zero.roll) * 0.7 * mirror, -14, 14);
    } else if (settings.follow && t - input.pointer.last < 4) {
      tx = input.pointer.x;
      ty = input.pointer.y * 0.8;
    } else if (settings.idle) {
      tx = Math.sin(t * 0.37) * 0.22 + Math.sin(t * 0.13) * 0.12;
      ty = Math.sin(t * 0.29) * 0.1;
      troll = Math.sin(t * 0.21) * 2;
    }
    state.hx = approach(state.hx, tx, 6, dt);
    state.hy = approach(state.hy, ty, 6, dt);
    state.roll = approach(state.roll, troll, 5, dt);
    state.gx = approach(state.gx, clamp(tx * 1.1, -1, 1), 16, dt);
    state.gy = approach(state.gy, clamp(ty * 1.1, -1, 1), 16, dt);

    // まばたき
    if (settings.autoBlink && t > state.nextBlink) {
      state.blinkStart = t;
      state.nextBlink = t + 2 + Math.random() * 4;
      if (Math.random() < 0.18) state.nextBlink = t + 0.28; // ときどき二回まばたき
    }
    const ph = (t - state.blinkStart) / 0.16;
    let blink = ph >= 0 && ph < 1 ? Math.sin(Math.PI * ph) : 0;
    if (camOn) blink = Math.max(blink, smooth(0.35, 0.7, (cam.data.blinkL + cam.data.blinkR) / 2));
    state.blink = blink;

    // 口パク
    let talk = Math.max(micOpen(), input.testTalk ? testTalkOpen(t) : 0, input.space ? 0.75 : 0);
    if (camOn) talk = Math.max(talk, smooth(0.08, 0.6, cam.data.jaw));
    state.talk = approach(state.talk, talk, talk > state.talk ? 30 : 14, dt);
    $('meterBar').style.width = Math.round(clamp(mic.rms * settings.sens) * 100) + '%';

    // 耳ピク
    if (t > state.nextEar) {
      const r = Math.random();
      if (r < 0.4) state.earT.L = t;
      else if (r < 0.8) state.earT.R = t;
      else state.earT.L = state.earT.R = t;
      state.nextEar = t + 3 + Math.random() * 7;
    }

    // 呼吸とホイッスルの揺れ
    state.breath = settings.idle ? Math.sin(t * 1.7) : 0;
    state.sway = settings.idle ? Math.sin(t * 1.3) * 1.5 : 0;

    // 表情の補間
    const target = EXPRESSIONS[state.exprKey];
    for (const k of Object.keys(state.expr)) {
      if (typeof target[k] === 'number') state.expr[k] = approach(state.expr[k], target[k], 10, dt);
    }
  }

  function earValue(t, t0) {
    const d = t - t0;
    if (d < 0 || d > 0.6) return 0;
    return Math.exp(-d * 7) * Math.sin(d * 34);
  }

  function renderState(t) {
    const expr = Object.assign({}, state.expr);
    if (cam.active && cam.data && cam.data.found) {
      expr.mouth = clamp(expr.mouth + cam.data.smile * 1.4, -1, 1);
      expr.browY -= cam.data.browUp * 12;
      expr.browAng += cam.data.browDown * 16 - cam.data.browUp * 14;
    }
    return {
      hx: state.hx,
      hy: state.hy,
      roll: state.roll,
      gx: state.gx,
      gy: state.gy,
      breath: state.breath,
      blink: state.blink,
      talk: state.talk,
      earL: earValue(t, state.earT.L),
      earR: earValue(t, state.earT.R),
      sway: state.sway,
      expr
    };
  }

  let exporting = false;
  let last = performance.now();
  function frame(now) {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    const t = now / 1000;
    if (!exporting) {
      update(dt, t);
      avatar.render(renderState(t));
    }
    requestAnimationFrame(frame);
  }

  /* ---------------- 書き出し ---------------- */

  async function svgToPngBlob(size) {
    const clone = svg.cloneNode(true);
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    clone.setAttribute('width', size);
    clone.setAttribute('height', size);
    const str = new XMLSerializer().serializeToString(clone);
    const url = URL.createObjectURL(new Blob([str], { type: 'image/svg+xml;charset=utf-8' }));
    try {
      const img = new Image();
      await new Promise((resolve, reject) => {
        img.onload = resolve;
        img.onerror = reject;
        img.src = url;
      });
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = size;
      canvas.getContext('2d').drawImage(img, 0, 0, size, size);
      return await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
    } finally {
      URL.revokeObjectURL(url);
    }
  }

  function download(blob, name) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  }

  async function savePng() {
    try {
      const blob = await svgToPngBlob(1024);
      download(blob, `kemono-coach_${settings.species}_${state.exprKey}.png`);
      toast('PNG を保存しました');
    } catch (err) {
      toast('PNG を作れませんでした');
    }
  }

  // PNGTuber ソフト用：目の開閉 × 口の開閉 の4枚を正面ポーズで書き出す
  async function savePngtuberSet() {
    exporting = true;
    const combos = [
      ['01_eyes-open_mouth-closed', 0, 0],
      ['02_eyes-open_mouth-open', 0, 0.65],
      ['03_eyes-closed_mouth-closed', 1, 0],
      ['04_eyes-closed_mouth-open', 1, 0.65]
    ];
    try {
      for (const [name, blink, talk] of combos) {
        avatar.render({
          hx: 0, hy: 0, roll: 0, gx: 0, gy: 0, breath: 0, earL: 0, earR: 0, sway: 0,
          blink, talk,
          // 口を閉じた差分では、表情の「口の開き」も 0 にする
          expr: Object.assign({}, EXPRESSIONS[state.exprKey], talk ? {} : { open: 0 })
        });
        const blob = await svgToPngBlob(1024);
        download(blob, `kemono-coach_${settings.species}_${state.exprKey}_${name}.png`);
        await new Promise((r) => setTimeout(r, 350));
      }
      toast('4枚の PNG を保存しました');
    } catch (err) {
      toast('PNG を作れませんでした');
    } finally {
      exporting = false;
    }
  }

  /* ---------------- UI ---------------- */

  let toastTimer = 0;
  function toast(msg) {
    const el = $('toast');
    el.textContent = msg;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
  }

  function setBg(key) {
    settings.bg = key;
    $('stage').dataset.bg = key;
    document.body.dataset.bg = key;
    document.querySelectorAll('#bgSeg button').forEach((b) => {
      b.setAttribute('aria-pressed', String(b.dataset.bg === key));
    });
    save();
  }

  function setUiHidden(hidden) {
    $('app').classList.toggle('ui-hidden', hidden);
    document.body.classList.toggle('ui-hidden', hidden);
  }

  const COLOR_INPUTS = { cFur: 'fur', cFurL: 'furL', cIris: 'iris', cTank: 'tank' };
  function syncColorInputs() {
    const c = colorsFor(settings.species);
    for (const [id, key] of Object.entries(COLOR_INPUTS)) $(id).value = c[key];
  }

  function buildUi() {
    const speciesSeg = $('speciesSeg');
    for (const [key, sp] of Object.entries(SPECIES)) {
      const b = document.createElement('button');
      b.type = 'button';
      b.dataset.species = key;
      b.textContent = sp.label;
      b.addEventListener('click', () => setSpecies(key));
      speciesSeg.appendChild(b);
    }

    const grid = $('exprGrid');
    NUM_KEYS.forEach((key, i) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.dataset.expr = key;
      b.innerHTML = `<kbd>${i + 1}</kbd><span>${EXPRESSIONS[key].label}</span>`;
      b.addEventListener('click', () => setExpression(key));
      grid.appendChild(b);
    });

    const bgSeg = $('bgSeg');
    for (const [key, label] of BACKGROUNDS) {
      const b = document.createElement('button');
      b.type = 'button';
      b.dataset.bg = key;
      b.textContent = label;
      b.addEventListener('click', () => setBg(key));
      bgSeg.appendChild(b);
    }

    for (const [id, key] of Object.entries(COLOR_INPUTS)) {
      $(id).addEventListener('input', (ev) => {
        const sp = settings.species;
        settings.colors[sp] = Object.assign({}, settings.colors[sp], { [key]: ev.target.value });
        avatar.setColors(colorsFor(sp));
        save();
      });
    }
    $('resetColors').addEventListener('click', () => {
      delete settings.colors[settings.species];
      avatar.setColors(colorsFor(settings.species));
      syncColorInputs();
      save();
    });

    const bindCheck = (id, key) => {
      $(id).checked = settings[key];
      $(id).addEventListener('change', (ev) => {
        settings[key] = ev.target.checked;
        save();
      });
    };
    bindCheck('followMouse', 'follow');
    bindCheck('autoBlink', 'autoBlink');
    bindCheck('idleMotion', 'idle');

    $('sens').value = settings.sens;
    $('sens').addEventListener('input', (ev) => {
      settings.sens = Number(ev.target.value);
      save();
    });

    $('micBtn').addEventListener('click', toggleMic);
    $('testTalkBtn').addEventListener('click', (ev) => {
      input.testTalk = !input.testTalk;
      ev.currentTarget.setAttribute('aria-pressed', String(input.testTalk));
    });
    $('camBtn').addEventListener('click', toggleCam);
    $('camCalib').addEventListener('click', calibrateCam);
    $('savePng').addEventListener('click', savePng);
    $('savePngtuber').addEventListener('click', savePngtuberSet);
    $('hideUi').addEventListener('click', () => setUiHidden(true));
    $('showUi').addEventListener('click', () => setUiHidden(false));

    window.addEventListener('pointermove', (ev) => {
      const r = svg.getBoundingClientRect();
      input.pointer.x = clamp((ev.clientX - (r.left + r.width / 2)) / (r.width / 2), -1, 1);
      input.pointer.y = clamp((ev.clientY - (r.top + r.height * 0.36)) / (r.height / 2), -1, 1);
      input.pointer.last = performance.now() / 1000;
    });

    const typing = (el) => el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable);
    window.addEventListener('keydown', (ev) => {
      if (typing(ev.target) && ev.target.type !== 'range' && ev.target.type !== 'checkbox') return;
      if (ev.code === 'Space') {
        input.space = true;
        ev.preventDefault();
        return;
      }
      const key = ev.key.toLowerCase();
      if (key === 'h') setUiHidden(!$('app').classList.contains('ui-hidden'));
      else if (key === 'b') state.blinkStart = performance.now() / 1000;
      else if (/^[1-9]$/.test(key) && NUM_KEYS[Number(key) - 1]) setExpression(NUM_KEYS[Number(key) - 1]);
    });
    window.addEventListener('keyup', (ev) => {
      if (ev.code === 'Space') input.space = false;
    });
    window.addEventListener('blur', () => {
      input.space = false;
    });
  }

  buildUi();
  setSpecies(settings.species);
  setBg(settings.bg);
  setUiHidden(params.get('ui') === '0');
  requestAnimationFrame(frame);
})();
