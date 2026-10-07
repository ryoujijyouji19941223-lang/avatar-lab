# ケモノコーチ — 獣人 2D アバター

タンクトップにホイッスルを下げた「コーチ」系の獣人アバターです。ブラウザだけで動き、インストールは要りません。

- キャラクター：**クマ / クロネコ / ゴールデン**（参考画像の3人をもとにしたオリジナルデザイン）
- 表情 7 種：真顔・怒り・笑顔・大笑い・驚き・しょんぼり・ドヤ顔
- 自動まばたき、呼吸、耳ピク、ホイッスルの揺れ
- マウスの方を向く（顔・耳・鼻先をずらした疑似立体）
- **マイク口パク**（声の大きさで口が開く）
- **Webカメラ追跡**（実験的）：顔の向き・まばたき・口・笑顔・眉を反映
- 色替え（毛・明るい毛・瞳・タンクトップ）
- 背景：ジム / グリーンバック / ブルーバック / 透過
- **PNG 書き出し**：透過 1024px。PNGTuber ソフト用の「目×口」4枚セットも出せます

## 使い方

1. このフォルダをダウンロードし、`index.html` を Chrome か Edge で開きます。
2. 右のパネルでキャラクター・表情・色を選びます。
3. 「マイクで口パク」を押してマイクを許可すると、しゃべるのに合わせて口が動きます。

| キー | 動作 |
| --- | --- |
| `1`〜`7` | 表情の切り替え |
| `Space`（押している間） | 口を開く |
| `B` | まばたき |
| `H` | 操作パネルを隠す / 出す |

### URL パラメータ

`index.html?species=cat&bg=transparent&ui=0` のように指定できます。

| 名前 | 値 |
| --- | --- |
| `species` | `bear` / `cat` / `dog` |
| `bg` | `studio` / `green` / `blue` / `transparent` |
| `ui` | `0` で操作パネルを隠した状態で開く |

## 配信で使う

### A. OBS のブラウザソース（いちばん手軽）

1. OBS で「ソース → ブラウザ」を追加します。
2. URL に `file:///C:/パス/avatar-lab/index.html?bg=transparent&ui=0` を入れ、幅・高さは `1080 × 1080` にします。
3. 背景が透過になるので、ゲーム画面などの上にそのまま重ねられます。

ブラウザソースでマイクやカメラを使う場合は、OBS を起動オプション `--enable-media-stream` 付きで起動してください。うまくいかないときは B の方法を使います。

### B. Chrome のウィンドウ＋クロマキー

1. Chrome で `index.html?bg=green&ui=0` を開き、マイク口パクを有効にします。
2. OBS で「ウィンドウキャプチャ」→ フィルタ「クロマキー（緑）」を掛けます。

### C. PNGTuber ソフト（veadotube mini など）

1. 表情を選んで「PNGTuber 用 4枚」を押します。
2. 次の 4 枚の透過 PNG が保存されます。
   - `01_eyes-open_mouth-closed`（通常）
   - `02_eyes-open_mouth-open`（しゃべり）
   - `03_eyes-closed_mouth-closed`（まばたき）
   - `04_eyes-closed_mouth-open`（まばたき＋しゃべり）
3. [veadotube mini](https://veado.tube/) の各状態に画像を割り当てます。

## 調べたこと：2D と 3D の選択肢

| 方式 | 主なツール | 難しさ | 向いている用途 |
| --- | --- | --- | --- |
| **PNGTuber**（差分画像の切り替え） | veadotube mini、PNGTuber Plus | 低 | 配信をすぐ始めたい |
| **ブラウザ 2D リグ**（このリポジトリ） | SVG + JavaScript | 低〜中 | 改造しながら育てたい |
| **Live2D** | Live2D Cubism（作成）＋ VTube Studio（動かす） | 中〜高 | 2D で本格的な VTuber |
| **3D（VRM）** | VRoid Studio、Blender＋VRM アドオン、Warudo / VSeeFace / VNyan | 高 | 3D の VTuber、全身の動き |
| **VRChat アバター** | Blender ＋ Unity（VRChat SDK、FBX） | 高 | VRChat で使う |

- VRoid Studio は無料で VRM を出力できますが、人型専用です。獣人のマズル・耳・しっぽは Blender で作り足すのが一般的です。
- 3D 獣人は、Blender で作り、VRM 形式で書き出して、Warudo などのトラッキングソフトで動かす流れがよく使われています。
- 2D の場合は PNGTuber がいちばん手軽で、マイクの音で「口閉じ / 口開き」の画像を切り替えるだけで動きます。

お使いの PC（Core i7-14650HX、RAM 64GB、RTX 5050 Laptop 8GB）なら、Blender・Unity・Warudo・Live2D Cubism はどれも快適に動きます。今回のブラウザ版はさらに軽く、配信と同時に動かしても負荷はほぼかかりません。

参考
- [Best AI Tools for VRChat and VTuber Avatars in 2026](https://www.3daistudio.com/blog/best-ai-tools-for-vrchat-vtuber-avatars-2026)
- [veado.tube ドキュメント](https://veado.tube/docs/intro/)
- [Veadotube の使い方と OBS 連携](https://techtactician.com/veadotube-png-vtuber-avatar-software-tutorial)
- [PNGTuber Plus の代替ソフト一覧](https://alternativeto.net/software/pngtuber-plus)

## デザインについて

参考画像のうち、最後のハイエナは既存のゲームのキャラクターに見えるため、そのまま再現するのは避けました。クマ・ネコ・イヌの「タンクトップ＋ホイッスルのコーチ」という共通の雰囲気をもとに、オリジナルのキャラクターとして描いています。

## ファイル構成

```
index.html         画面と操作パネル
css/style.css      見た目
js/character.js    キャラクターの描画とリグ（種族・表情・目・口の計算）
js/app.js          アニメーション、マイク、マウス、キーボード、書き出し
js/tracking.js     Webカメラの顔追跡（MediaPipe Face Landmarker を CDN から読み込み）
```

### 新しい種族を足すには

`js/character.js` の `SPECIES` に 1 項目を追加します。耳・頭の輪郭・マズル・目の位置・眉・口の位置を座標で書けば、まばたきや口パク、表情はそのまま使えます。

### カメラ追跡について

- 初回は顔認識モデル（約 4MB）をダウンロードするため、ネット接続が必要です。
- 首の向きが逆に動く場合は「カメラの左右を反転」を切り替えてください。
- 正面を向いた状態で「正面をリセット」を押すと、そこが基準になります。
