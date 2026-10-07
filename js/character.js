/*
 * character.js — 獣人コーチ アバターの描画とリグ
 *
 * キャラクターは 800x800 の SVG として描き、パーツごとに <g> を分けて
 * JS から transform / path を毎フレーム書き換えて動かす（簡易 Live2D 方式）。
 * 色は SVG ルートの CSS 変数（--fur など）で持つので、色替えは再描画不要。
 */
(function (global) {
  'use strict';

  const CX = 400; // 顔の中心線
  const SVGNS = 'http://www.w3.org/2000/svg';

  const r1 = (n) => Math.round(n * 10) / 10;
  const pt = (p) => r1(p[0]) + ' ' + r1(p[1]);
  const mir = (p) => [2 * CX - p[0], p[1]];
  const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
  const smooth = (a, b, v) => {
    const t = clamp((v - a) / (b - a));
    return t * t * (3 - 2 * t);
  };

  // 右半分（上端 x=400 → 下端 x=400）のセグメントから左右対称の閉じたパスを作る
  function symPath(start, segs) {
    const pts = [start];
    let d = 'M ' + pt(start);
    for (const [type, ...ps] of segs) {
      d += ' ' + type + ' ' + ps.map(pt).join(' ');
      pts.push(ps[ps.length - 1]);
    }
    for (let i = segs.length - 1; i >= 0; i--) {
      const [type, ...ps] = segs[i];
      const to = mir(pts[i]);
      if (type === 'C') d += ' C ' + pt(mir(ps[1])) + ' ' + pt(mir(ps[0])) + ' ' + pt(to);
      else if (type === 'Q') d += ' Q ' + pt(mir(ps[0])) + ' ' + pt(to);
      else d += ' L ' + pt(to);
    }
    return d + ' Z';
  }

  // 右側用に描いたマークアップを左右反転して両側に置く
  const FLIP = 'matrix(-1 0 0 1 800 0)';
  const both = (markup) => markup + `<g transform="${FLIP}">${markup}</g>`;

  // 塗り＋外周の線（閉じる辺には線を引かない）。毛のトゲなどに使う
  const tuft = (d, cls = 'fur') => `<path class="${cls}" d="${d}"/><path class="line" d="${d}"/>`;

  /* ------------------------------------------------------------------ */
  /* 種族ごとの定義                                                        */
  /* ------------------------------------------------------------------ */

  const SPECIES = {
    bear: {
      label: 'クマ',
      colors: { fur: '#7b5233', furL: '#a9805a', iris: '#6a3c1a', tank: '#1f1f24' },
      fixed: { nose: '#2a1b15', inner: null, whisker: '#000' },
      chestOpacity: 0.55,
      defaultExpr: 'neutral',
      ears: {
        layer: 'back',
        pivot: [505, 222],
        twist: 9,
        markup:
          '<circle class="fur ol" cx="512" cy="188" r="46"/>' +
          '<circle class="inner" cx="514" cy="194" r="25"/>' +
          '<path class="line thin" d="M 500 196 q 8 -6 18 -2 M 506 208 q 8 -4 16 0"/>'
      },
      head: symPath([400, 166], [
        ['C', [468, 164], [522, 186], [540, 236]],
        ['C', [550, 262], [553, 288], [556, 306]],
        ['Q', [582, 316], [562, 334]],
        ['Q', [586, 350], [556, 362]],
        ['Q', [574, 386], [532, 386]],
        ['Q', [540, 410], [496, 406]],
        ['C', [470, 424], [432, 432], [400, 432]]
      ]),
      headExtras:
        both('<path class="line thin" d="M 520 296 q 12 8 16 22 M 506 330 q 14 6 20 18 M 440 196 q 10 2 18 10"/>') +
        both('<path class="furD" opacity=".35" d="M 462 238 Q 500 232 520 262 Q 494 252 462 256 Z"/>'),
      muzzle:
        `<path class="furL ol" d="${symPath([400, 298], [
          ['C', [442, 298], [470, 322], [468, 352]],
          ['C', [466, 384], [434, 398], [400, 398]]
        ])}"/>` +
        `<path class="nose ol mid" d="${symPath([400, 312], [
          ['C', [426, 311], [438, 318], [436, 330]],
          ['C', [432, 344], [413, 352], [400, 354]]
        ])}"/>` +
        '<ellipse cx="391" cy="320" rx="10" ry="4" fill="#fff" opacity=".35"/>' +
        '<path class="line" d="M 400 354 L 400 366"/>',
      eyes: { L: [346, 268], R: [454, 268], ew: 24, eh: 13, round: 0.55, iris: 10, pupil: 'round' },
      brow: { len: 26, gap: 9, tIn: 11, tOut: 5, arch: 3, cls: 'furD' },
      mouth: { x: 400, y: 375, w: 21, fangs: true },
      blush: [[318, 318], [482, 318]]
    },

    cat: {
      label: 'クロネコ',
      colors: { fur: '#2c2c33', furL: '#4b4b56', iris: '#86c8e6', tank: '#1f1f24' },
      fixed: { nose: '#c98b82', inner: '#c27a78', whisker: '#dcdce2' },
      chestOpacity: 0.7,
      defaultExpr: 'neutral',
      ears: {
        layer: 'back',
        pivot: [494, 214],
        twist: 12,
        markup:
          '<path class="fur ol" d="M 446 204 Q 486 128 528 88 Q 546 78 548 100 Q 556 170 542 242 Z"/>' +
          '<path class="inner" d="M 468 200 Q 498 146 527 114 Q 536 162 530 222 Z"/>' +
          '<path class="furStroke" d="M 488 214 l 16 -26 M 500 216 l 16 -30 M 512 222 l 12 -24"/>'
      },
      head: symPath([400, 176], [
        ['C', [462, 174], [516, 198], [532, 246]],
        ['C', [540, 270], [542, 292], [546, 308]],
        ['Q', [582, 312], [556, 334]],
        ['Q', [588, 344], [550, 360]],
        ['Q', [566, 382], [522, 380]],
        ['C', [490, 408], [440, 416], [400, 416]]
      ]),
      headExtras:
        tuft('M 364 198 Q 372 172 386 158 Q 386 178 394 184 Q 400 156 416 144 Q 412 172 420 184 Q 432 168 448 166 Q 436 186 440 204') +
        both('<path class="line thin" d="M 512 312 q 12 6 16 18 M 498 342 q 12 4 18 14"/>'),
      muzzle:
        both('<circle class="furL" cx="416" cy="352" r="19"/>') +
        '<ellipse class="furL" cx="400" cy="378" rx="15" ry="8"/>' +
        '<path class="nose ol mid" d="M 386 334 Q 400 328 414 334 Q 410 345 400 351 Q 390 345 386 334 Z"/>' +
        '<path class="line" d="M 400 351 L 400 359"/>' +
        both('<path class="whisker" d="M 432 346 Q 482 332 530 330 M 434 355 Q 486 352 536 360 M 430 364 Q 478 372 522 388"/>'),
      eyes: { L: [352, 286], R: [448, 286], ew: 24, eh: 13, round: 0.5, iris: 10.5, pupil: 'slit' },
      brow: { len: 20, gap: 8, tIn: 7, tOut: 3, arch: 3, cls: 'furL' },
      mouth: { x: 400, y: 365, w: 13, fangs: true },
      blush: [[322, 330], [478, 330]]
    },

    dog: {
      label: 'ゴールデン',
      colors: { fur: '#d8a660', furL: '#f0d39e', iris: '#5b3a20', tank: '#1f1f24' },
      fixed: { nose: '#3b2820', inner: null, whisker: '#000' },
      chestOpacity: 1,
      defaultExpr: 'smile',
      ears: {
        layer: 'front',
        pivot: [494, 196],
        twist: 6,
        markup:
          '<path class="furD ol" d="M 486 186 C 540 178 578 222 580 294 C 582 348 566 392 540 398 C 516 402 506 372 503 340 C 499 292 484 240 486 186 Z"/>' +
          '<path class="line thin" d="M 530 240 q 18 40 14 90 M 550 260 q 12 40 8 90"/>'
      },
      head: symPath([400, 174], [
        ['C', [466, 172], [514, 200], [522, 252]],
        ['C', [530, 302], [522, 352], [500, 386]],
        ['C', [472, 424], [434, 432], [400, 432]]
      ]),
      headExtras:
        tuft('M 382 182 Q 390 160 402 154 Q 400 170 408 174 Q 416 158 430 154 Q 422 172 426 186') +
        both('<path class="line thin" d="M 486 392 q 6 14 2 26 M 470 404 q 4 12 0 22"/>'),
      muzzle:
        `<path class="furL ol" d="${symPath([400, 298], [
          ['C', [446, 298], [478, 324], [476, 358]],
          ['C', [474, 392], [440, 408], [400, 408]]
        ])}"/>` +
        `<path class="nose ol mid" d="${symPath([400, 312], [
          ['C', [430, 310], [446, 318], [443, 332]],
          ['C', [439, 350], [415, 356], [400, 358]]
        ])}"/>` +
        '<ellipse cx="390" cy="321" rx="12" ry="5" fill="#fff" opacity=".35"/>' +
        '<path class="line" d="M 400 358 L 400 374"/>',
      eyes: { L: [354, 272], R: [446, 272], ew: 17, eh: 13, round: 0.9, iris: 10, pupil: 'round' },
      brow: { len: 18, gap: 10, tIn: 8, tOut: 5, arch: 4, cls: 'furD' },
      mouth: { x: 400, y: 384, w: 30, fangs: false },
      blush: [[326, 322], [474, 322]]
    }
  };

  /* ------------------------------------------------------------------ */
  /* 表情プリセット                                                        */
  /*  lidIn/lidOut: 上まぶたが目頭/目尻をどれだけ覆うか (0..1)              */
  /*  low: 下まぶた, browAng: 眉の角度(+で怒り), browY: 眉の上下            */
  /*  mouth: 口角(-1への字〜+1笑顔), open: 口の開き, pupil: 瞳の大きさ      */
  /*  happy: にっこり閉じ目(^^), blush: 頬の赤み                            */
  /* ------------------------------------------------------------------ */

  const EXPRESSIONS = {
    neutral:   { label: '真顔',   lidIn: 0.22, lidOut: 0.08, low: 0,    browAng: 12,  browY: 0,   mouth: -0.3, open: 0,    pupil: 1,    happy: 0, blush: 0 },
    angry:     { label: '怒り',   lidIn: 0.42, lidOut: 0.12, low: 0.1,  browAng: 26,  browY: 5,   mouth: -0.8, open: 0,    pupil: 0.8,  happy: 0, blush: 0 },
    smile:     { label: '笑顔',   lidIn: 0.08, lidOut: 0.08, low: 0.28, browAng: -4,  browY: -4,  mouth: 0.9,  open: 0,    pupil: 1.05, happy: 0, blush: 0.45 },
    laugh:     { label: '大笑い', lidIn: 0.08, lidOut: 0.08, low: 0,    browAng: -8,  browY: -8,  mouth: 0.8,  open: 0.55, pupil: 1,    happy: 1, blush: 0.7 },
    surprised: { label: '驚き',   lidIn: 0,    lidOut: 0,    low: 0,    browAng: -10, browY: -14, mouth: -0.1, open: 0.45, pupil: 0.65, happy: 0, blush: 0 },
    sad:       { label: 'しょんぼり', lidIn: 0, lidOut: 0.34, low: 0.05, browAng: -22, browY: -4, mouth: -0.7, open: 0,  pupil: 1.1,  happy: 0, blush: 0 },
    smug:      { label: 'ドヤ顔', lidIn: 0.38, lidOut: 0.38, low: 0.12, browAng: 4,   browY: -2,  mouth: 0.55, open: 0,    pupil: 0.9,  happy: 0, blush: 0.2 }
  };

  /* ------------------------------------------------------------------ */
  /* 色ユーティリティ                                                      */
  /* ------------------------------------------------------------------ */

  function hexToRgb(hex) {
    const h = hex.replace('#', '');
    const n = parseInt(h.length === 3 ? h.split('').map((c) => c + c).join('') : h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function rgbToHex(rgb) {
    return '#' + rgb.map((v) => Math.round(clamp(v, 0, 255)).toString(16).padStart(2, '0')).join('');
  }
  function mix(a, b, t) {
    const A = hexToRgb(a);
    const B = hexToRgb(b);
    return rgbToHex(A.map((v, i) => v + (B[i] - v) * t));
  }

  /* ------------------------------------------------------------------ */
  /* 共通の体（タンクトップ＋ホイッスル）                                    */
  /* ------------------------------------------------------------------ */

  function bodyMarkup(sp) {
    const arm =
      '<path class="fur ol" d="M 532 455 C 615 445 688 505 696 610 C 702 690 692 760 684 812 L 470 812 L 470 470 Z"/>' +
      '<path class="furD" opacity=".4" d="M 572 600 C 618 610 646 660 652 730 C 655 768 654 795 652 812 L 604 812 C 604 760 596 690 572 640 Z"/>' +
      '<path class="line" d="M 692 662 Q 650 640 606 664"/>' +
      tuft('M 676 520 L 700 526 L 686 536 L 708 548 L 690 552') +
      tuft('M 694 600 L 714 612 L 698 616 L 712 632 L 696 630');

    // 首〜僧帽筋。塗りは腕の内側まで伸ばし、線は肩のラインだけ引く
    const torso =
      '<path class="fur" d="M 334 372 C 326 418 296 444 254 462 L 254 812 L 546 812 L 546 462 C 504 444 474 418 466 372 Z"/>' +
      both('<path class="line" d="M 466 372 C 474 418 504 444 546 462"/>');

    const chest =
      `<path class="furL" opacity="${sp.chestOpacity}" d="${symPath([400, 432], [
        ['Q', [430, 440], [448, 458]],
        ['Q', [436, 470], [452, 486]],
        ['Q', [432, 490], [430, 508]],
        ['Q', [414, 504], [400, 526]]
      ])}"/>` +
      '<path class="line thin" d="M 392 474 q 8 10 4 22 M 410 488 q 6 8 2 18"/>';

    const tank =
      `<path class="tank ol" d="${symPath([400, 522], [
        ['C', [432, 522], [468, 470], [479, 408]],
        ['L', [504, 434]],
        ['C', [508, 520], [532, 600], [566, 640]],
        ['L', [580, 812]],
        ['L', [400, 812]]
      ])}"/>` +
      both('<path class="tankHi" d="M 412 560 C 440 546 492 550 522 580 C 502 612 450 620 412 606 Z"/>') +
      both('<path class="tankLine" d="M 404 614 Q 462 630 524 596 M 412 680 q 30 6 58 0 M 414 730 q 28 6 52 0"/>') +
      '<path class="tankLine" d="M 400 548 L 400 612"/>' +
      both('<path class="tankHi" opacity=".6" d="M 482 416 L 498 432 C 500 470 506 500 514 520 C 500 490 490 450 482 416 Z"/>');

    const whistle =
      '<g id="whistle">' +
      '<path class="cord" d="M 352 418 C 352 520 372 600 394 646"/>' +
      '<path class="cord" d="M 448 418 C 448 520 428 600 406 646"/>' +
      '<circle cx="400" cy="650" r="6" fill="none" stroke="#9aa1ab" stroke-width="3"/>' +
      '<rect class="metal ol mid" x="391" y="686" width="18" height="20" rx="4"/>' +
      '<rect class="metal ol mid" x="383" y="654" width="34" height="40" rx="14"/>' +
      '<rect x="392" y="664" width="16" height="7" rx="2" fill="#3a3f47"/>' +
      '<rect x="388" y="676" width="5" height="12" rx="2.5" fill="#fff" opacity=".7"/>' +
      '</g>';

    return `<g id="body">${both(arm)}${torso}${chest}${tank}${whistle}</g>`;
  }

  /* ------------------------------------------------------------------ */
  /* 目・眉・口のマークアップ                                               */
  /* ------------------------------------------------------------------ */

  function eyeShape(cx, cy, ew, eh, round) {
    const k = eh / 0.75; // 3次ベジェの山の高さ = 0.75k
    return (
      `M ${pt([cx - ew, cy])} C ${pt([cx - ew * round, cy - k])} ${pt([cx + ew * round, cy - k])} ${pt([cx + ew, cy])} ` +
      `C ${pt([cx + ew * round, cy + k])} ${pt([cx - ew * round, cy + k])} ${pt([cx - ew, cy])} Z`
    );
  }

  function eyeMarkup(side, sp) {
    const E = sp.eyes;
    const [cx, cy] = E[side];
    const shape = eyeShape(cx, cy, E.ew, E.eh, E.round);
    const pupil =
      E.pupil === 'slit'
        ? `<ellipse class="pupil" cx="${cx}" cy="${cy}" rx="2.8" ry="${E.iris * 0.85}"/>`
        : `<circle class="pupil" cx="${cx}" cy="${cy}" r="${E.iris * 0.48}"/>`;
    return (
      `<g id="eye${side}">` +
      `<clipPath id="clipEye${side}"><path d="${shape}"/></clipPath>` +
      `<path id="white${side}" class="white" d="${shape}"/>` +
      `<g clip-path="url(#clipEye${side})">` +
      `<g id="iris${side}">` +
      `<circle class="iris" cx="${cx}" cy="${cy}" r="${E.iris}"/>` +
      `<circle cx="${cx}" cy="${cy}" r="${E.iris}" fill="none" stroke="#000" stroke-opacity=".35" stroke-width="2"/>` +
      `<g id="pupil${side}">${pupil}</g>` +
      `<circle cx="${cx + E.iris * 0.35}" cy="${cy - E.iris * 0.4}" r="${Math.max(2.2, E.iris * 0.28)}" fill="#fff"/>` +
      '</g>' +
      `<path id="low${side}" class="fur"/>` +
      `<path id="lid${side}" class="fur"/>` +
      `<path id="lidLine${side}" class="lidline"/>` +
      '</g>' +
      `<path id="eyeOl${side}" d="${shape}" class="eyeol"/>` +
      `<path id="closed${side}" class="line" opacity="0"/>` +
      '</g>'
    );
  }

  /* ------------------------------------------------------------------ */
  /* Avatar クラス                                                        */
  /* ------------------------------------------------------------------ */

  const STYLE = `
    .fur{fill:var(--fur)} .furL{fill:var(--furL)} .furD{fill:var(--furD)}
    .inner{fill:var(--inner)} .nose{fill:var(--nose)} .iris{fill:var(--iris)}
    .white{fill:#f6f2ea} .pupil{fill:#0c0a09}
    .tank{fill:var(--tank)} .tankHi{fill:var(--tankHi)}
    .tankLine{fill:none;stroke:var(--tankLine);stroke-width:3;stroke-linecap:round}
    .ol{stroke:var(--ol);stroke-width:4.5;stroke-linejoin:round;stroke-linecap:round}
    .ol.mid{stroke-width:3}
    .line{fill:none;stroke:var(--ol);stroke-width:4;stroke-linecap:round;stroke-linejoin:round}
    .line.thin{stroke-width:2.5;opacity:.7}
    .furStroke{fill:none;stroke:var(--fur);stroke-width:3;stroke-linecap:round}
    .whisker{fill:none;stroke:var(--whisker);stroke-width:2;stroke-linecap:round}
    .eyeol{fill:none;stroke:var(--ol);stroke-width:2.5}
    .lidline{fill:none;stroke:var(--ol);stroke-width:8;stroke-linecap:round}
    .cord{fill:none;stroke:#c3c8cf;stroke-width:3.2;stroke-linecap:round}
    .metal{fill:url(#metalGrad)}
    .mouthIn{fill:#3a1216} .tongue{fill:#d8646e} .fang{fill:#fbf8f0}
    .blush{fill:#ff6f86}
  `;

  class Avatar {
    constructor(svg) {
      this.svg = svg;
      this.speciesKey = null;
      this.sp = null;
      this.el = {};
    }

    setSpecies(key) {
      const sp = SPECIES[key] || SPECIES.bear;
      this.speciesKey = SPECIES[key] ? key : 'bear';
      this.sp = sp;

      const earGroup = (side) => {
        const [px, py] = sp.ears.pivot;
        const pivot = side === 'R' ? [px, py] : [2 * CX - px, py];
        const inner = side === 'R' ? sp.ears.markup : `<g transform="${FLIP}">${sp.ears.markup}</g>`;
        return { markup: `<g id="ear${side}">${inner}</g>`, pivot };
      };
      const earL = earGroup('L');
      const earR = earGroup('R');
      const ears = earL.markup + earR.markup;
      this.earPivots = { L: earL.pivot, R: earR.pivot };

      const brows = `<path id="browL" class="${sp.brow.cls} ol mid"/><path id="browR" class="${sp.brow.cls} ol mid"/>`;
      const blush = sp.blush
        .map(([x, y]) => `<ellipse class="blush" cx="${x}" cy="${y}" rx="22" ry="9"/>`)
        .join('');
      const M = sp.mouth;
      const mouth =
        '<clipPath id="clipMouth"><path id="mouthClip"/></clipPath>' +
        '<path id="mouthFill" class="mouthIn"/>' +
        '<g clip-path="url(#clipMouth)">' +
        `<ellipse id="tongue" class="tongue" cx="${M.x}" cy="${M.y}" rx="${M.w * 0.6}" ry="10"/>` +
        (M.fangs ? '<path id="fangs" class="fang"/>' : '') +
        '</g>' +
        '<path id="mouthLine" class="line"/>';

      this.svg.innerHTML =
        `<style>${STYLE}</style>` +
        '<defs><linearGradient id="metalGrad" x1="0" x2="1" y1="0" y2="1">' +
        '<stop offset="0" stop-color="#f3f5f8"/><stop offset=".55" stop-color="#b9bfc8"/><stop offset="1" stop-color="#7d848f"/>' +
        '</linearGradient></defs>' +
        '<g id="rig">' +
        bodyMarkup(sp) +
        '<g id="head">' +
        `<g id="earsBack">${sp.ears.layer === 'back' ? ears : ''}</g>` +
        `<path class="fur ol" d="${sp.head}"/>` +
        sp.headExtras +
        '<g id="face">' +
        eyeMarkup('L', sp) +
        eyeMarkup('R', sp) +
        brows +
        `<g id="blush" opacity="0">${blush}</g>` +
        '</g>' +
        `<g id="muzzle">${sp.muzzle}${mouth}</g>` +
        `<g id="earsFront">${sp.ears.layer === 'front' ? ears : ''}</g>` +
        '</g>' +
        '</g>';

      const ids = [
        'rig', 'body', 'whistle', 'head', 'earsBack', 'earsFront', 'face', 'muzzle', 'earL', 'earR',
        'browL', 'browR', 'blush', 'mouthClip', 'mouthFill', 'mouthLine', 'tongue', 'fangs'
      ];
      for (const s of ['L', 'R']) {
        for (const part of ['white', 'iris', 'pupil', 'low', 'lid', 'lidLine', 'eyeOl', 'closed']) ids.push(part + s);
      }
      this.el = {};
      for (const id of ids) this.el[id] = this.svg.querySelector('#' + id);
    }

    // colors: { fur, furL, iris, tank }
    setColors(colors) {
      const sp = this.sp;
      const fur = colors.fur;
      const vars = {
        '--fur': fur,
        '--furL': colors.furL,
        '--furD': mix(fur, '#000000', 0.3),
        '--ol': mix(fur, '#000000', 0.8),
        '--inner': sp.fixed.inner || mix(fur, '#000000', 0.42),
        '--nose': sp.fixed.nose,
        '--iris': colors.iris,
        '--whisker': sp.fixed.whisker,
        '--tank': colors.tank,
        '--tankHi': mix(colors.tank, '#ffffff', 0.09),
        '--tankLine': mix(colors.tank, '#000000', 0.45)
      };
      for (const [k, v] of Object.entries(vars)) this.svg.style.setProperty(k, v);
    }

    /*
     * s: {
     *   hx, hy      顔の向き (-1..1)
     *   roll        首のかしげ (deg)
     *   gx, gy      視線 (-1..1)
     *   breath      呼吸 (-1..1)
     *   blink       まばたき (0..1)
     *   talk        口パク (0..1)
     *   earL, earR  耳のピクつき (-1..1)
     *   sway        ホイッスルの揺れ (deg)
     *   expr        表情パラメータ（EXPRESSIONS と同じキー）
     * }
     */
    render(s) {
      const e = this.el;
      const sp = this.sp;
      const x = s.hx;
      const y = s.hy;
      const bounce = s.talk * 3;

      e.body.setAttribute('transform', `translate(${r1(x * 3)} ${r1(s.breath * 2.2)})`);
      e.whistle.setAttribute('transform', `rotate(${r1(s.sway - x * 3)} 400 420)`);
      e.head.setAttribute(
        'transform',
        `translate(${r1(x * 16)} ${r1(y * 11 + s.breath * 1.4 - bounce)}) rotate(${r1(s.roll + x * 2.5)} 400 430)`
      );
      e.earsBack.setAttribute('transform', `translate(${r1(-x * 6)} ${r1(-y * 5)})`);
      e.earsFront.setAttribute('transform', `translate(${r1(x * 2)} ${r1(y * 1)})`);
      e.face.setAttribute('transform', `translate(${r1(x * 9)} ${r1(y * 7)})`);
      e.muzzle.setAttribute('transform', `translate(${r1(x * 15)} ${r1(y * 10)})`);

      for (const side of ['L', 'R']) {
        const [px, py] = this.earPivots[side];
        const dir = side === 'L' ? -1 : 1;
        e['ear' + side].setAttribute('transform', `rotate(${r1(s['ear' + side] * sp.ears.twist * dir)} ${px} ${py})`);
      }

      const p = Object.assign({}, s.expr, { blink: s.blink });
      this.renderEye('L', p, s.gx, s.gy);
      this.renderEye('R', p, s.gx, s.gy);
      e.browL.setAttribute('d', this.browPath('L', p));
      e.browR.setAttribute('d', this.browPath('R', p));
      e.blush.setAttribute('opacity', r1(clamp(p.blush) * 0.55 * 100) / 100);
      this.renderMouth(p.mouth, clamp(Math.max(p.open, s.talk)));
    }

    renderEye(side, p, gx, gy) {
      const e = this.el;
      const E = this.sp.eyes;
      const [cx, cy] = E[side];
      const { ew, eh } = E;
      const inner = side === 'L' ? 1 : -1; // 目頭の向き

      const cIn = Math.max(p.lidIn + (1 - p.lidIn) * p.blink, p.happy);
      const cOut = Math.max(p.lidOut + (1 - p.lidOut) * p.blink, p.happy);
      const top = cy - eh;
      const span = eh * 2.3;
      const yIn = top + cIn * span;
      const yOut = top + cOut * span;
      const xIn = cx + inner * (ew + 4);
      const xOut = cx - inner * (ew + 4);
      const ctrl = (yIn + yOut) / 2 - eh * 0.25;
      e['lid' + side].setAttribute(
        'd',
        `M ${pt([xIn, top - 30])} L ${pt([xOut, top - 30])} L ${pt([xOut, yOut])} Q ${pt([cx, ctrl])} ${pt([xIn, yIn])} Z`
      );
      e['lidLine' + side].setAttribute('d', `M ${pt([xOut, yOut])} Q ${pt([cx, ctrl])} ${pt([xIn, yIn])}`);

      const lowY = cy + eh * 1.05 - p.low * eh * 1.6;
      e['low' + side].setAttribute(
        'd',
        `M ${pt([cx - ew - 4, cy + eh + 30])} L ${pt([cx + ew + 4, cy + eh + 30])} L ${pt([cx + ew + 4, lowY + eh * 0.3])} ` +
          `Q ${pt([cx, lowY - eh * 0.3])} ${pt([cx - ew - 4, lowY + eh * 0.3])} Z`
      );

      const closed = Math.min(cIn, cOut);
      const cl = smooth(0.82, 1, closed);
      e['eyeOl' + side].setAttribute('opacity', r1((1 - cl) * 100) / 100);
      e['white' + side].setAttribute('opacity', r1((1 - cl) * 100) / 100);
      e['lidLine' + side].setAttribute('opacity', r1((1 - cl) * 100) / 100);
      e['closed' + side].setAttribute('opacity', r1(cl * 100) / 100);
      const archY = cy + eh * (0.9 + (-1.9 - 0.9) * p.happy);
      e['closed' + side].setAttribute(
        'd',
        `M ${pt([cx - ew, cy + 2])} Q ${pt([cx, archY])} ${pt([cx + ew, cy + 2])}`
      );

      e['iris' + side].setAttribute('transform', `translate(${r1(gx * ew * 0.38)} ${r1(gy * eh * 0.35)})`);
      const ps = p.pupil;
      e['pupil' + side].setAttribute('transform', `translate(${cx} ${cy}) scale(${r1(ps * 100) / 100}) translate(${-cx} ${-cy})`);
    }

    browPath(side, p) {
      const E = this.sp.eyes;
      const B = this.sp.brow;
      const [cx, cy] = E[side];
      const s = side === 'L' ? 1 : -1;
      const by = cy - E.eh - B.gap + p.browY;
      const a = (p.browAng * Math.PI) / 180;
      const dx = B.len * Math.cos(a);
      const dy = B.len * Math.sin(a);
      const ix = cx + s * dx;
      const iy = by + dy;
      const ox = cx - s * dx;
      const oy = by - dy;
      const mx = (ix + ox) / 2;
      const my = (iy + oy) / 2 - B.arch;
      const t = (B.tIn + B.tOut) / 4;
      return (
        `M ${pt([ix, iy - B.tIn / 2])} Q ${pt([mx, my - t])} ${pt([ox, oy - B.tOut / 2])} ` +
        `Q ${pt([ox - s * B.tOut, oy])} ${pt([ox, oy + B.tOut / 2])} ` +
        `Q ${pt([mx, my + t])} ${pt([ix, iy + B.tIn / 2])} Z`
      );
    }

    renderMouth(curve, open) {
      const e = this.el;
      const M = this.sp.mouth;
      const w = M.w * (1 - open * 0.15);
      const lift = curve * 7;
      const L = [M.x - w, M.y - lift];
      const R = [M.x + w, M.y - lift];
      if (open < 0.04) {
        const d = `M ${pt(L)} Q ${pt([M.x, M.y + curve * 9])} ${pt(R)}`;
        e.mouthLine.setAttribute('d', d);
        e.mouthFill.setAttribute('d', '');
        e.mouthClip.setAttribute('d', '');
        if (e.fangs) e.fangs.setAttribute('d', '');
        return;
      }
      const upY = M.y + curve * 5 - open * 6;
      const lowY = M.y + curve * 5 + open * 66;
      const d = `M ${pt(L)} Q ${pt([M.x, upY])} ${pt(R)} Q ${pt([M.x, lowY])} ${pt(L)} Z`;
      e.mouthLine.setAttribute('d', d);
      e.mouthFill.setAttribute('d', d);
      e.mouthClip.setAttribute('d', d);
      const bottom = (L[1] + lowY) / 2; // 下唇のいちばん低いところ
      e.tongue.setAttribute('cy', r1(bottom + 2));
      e.tongue.setAttribute('ry', r1(5 + open * 10));
      e.tongue.setAttribute('rx', r1(w * 0.62));
      if (e.fangs) {
        const lipY = 0.625 * L[1] + 0.375 * upY;
        const len = 7 + open * 5;
        const f = (fx) => `M ${pt([fx - 4, lipY - 4])} L ${pt([fx + 4, lipY - 4])} L ${pt([fx, lipY + len])} Z`;
        e.fangs.setAttribute('d', f(M.x - w * 0.5) + ' ' + f(M.x + w * 0.5));
      }
    }
  }

  global.BeastAvatar = { Avatar, SPECIES, EXPRESSIONS, SVGNS };
})(window);
