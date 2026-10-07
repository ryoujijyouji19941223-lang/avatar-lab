# Crow Avatar Android prototype

スマホ単体で使うことを優先した、カラス簡易2DアバターのAndroid試作です。

## 今回の狙い

Web版で詰まった「CDNからMediaPipeを読み込む」構成をやめています。

APKの中に以下を入れる構成です。

- MediaPipe Tasks Vision（Gradle依存）
- Face Landmarkerモデル（GitHub Actionsのビルド時に取得しAPKへ同梱）
- カラスの通常 / まばたき / 口開き画像

起動後はインターネットから顔認識SDKを読み込む必要がありません。

## 動き

- 前面カメラで顔を解析
- 左右 / 上下 / 傾き → 頭だけ
- まばたき → まばたき画像
- 口を開く → 口開き画像
- 胴体 → ごく小さい呼吸だけ
- 舌出し → 削除

## 配信を想定した設計

### YouTube

まずは画面配信（Screencast）方式を想定します。

1. このアプリで顔トラッキングを開始
2. 「配信画面にする」でUIを隠す
3. YouTube側でスマホ画面のライブ配信を開始
4. このアプリへ戻る
5. 視聴者にはカラスの画面が配信される

重要なのは、YouTube側に前面カメラを使わせず、カメラはこのアプリだけが使うことです。
配信側は画面をキャプチャします。

### Instagram

Instagramも考え方は同じです。

- このアプリ → 前面カメラ + 顔認識 + アバター描画
- 配信エンコーダ → 画面キャプチャ
- Instagram → RTMP/対応配信経路

Instagramアプリ自身とこのアプリが同時に前面カメラを取り合う構成にはしません。

将来の本命は、このAndroidアプリ自身にRTMP送信を持たせ、
「アバター生成 → 720x1280でエンコード → YouTube / Instagramへ送信」
まで1アプリで完結させる方式です。

## APKの作り方

GitHub Actions の **Build Crow Avatar APK** が自動でdebug APKを作ります。
PCは不要です。

Actions → Build Crow Avatar APK → 最新run → Artifacts からAPKを取得できます。
