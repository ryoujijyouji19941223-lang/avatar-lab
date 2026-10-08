# Chat版 v0.4 実装メモ

A/B比較のChat側。Work側は参照しない。

## 実装方針
既存 neutral / blink / talk の3枚を「完成顔差分」ではなく座標が揃ったテクスチャアトラスとして使用。

- BODY: neutralの肩以下のみ、呼吸だけ
- HEAD: neutralを頭形状で描画
- EYE: blink画像の左右の閉眼部分を独立クリップ。上/下に分割して閉じる方向へ移動
- MOUTH:
  - talk画像の口内だけを最背面
  - neutral画像のlower-jaw領域だけを可動レイヤー
  - neutralのHEADからlower-jaw領域をPath差分で除外
  - upper beakはHEAD側に残り固定
- jawOpenは正面絵なので面内回転ではなく、口角付近を支点に縦伸長＋微下移動で疑似回転
- yaw/pitch/rollはv0.3より少し拡大

## 判定
実機で目形状、完全閉眼、上嘴固定、下嘴開閉、口中心、首境界を確認する。
