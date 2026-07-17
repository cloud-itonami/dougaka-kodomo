# Phase C — cadence 定期投稿(ADR-2607164500 / 2607162200)

こどもチャンネル(kodomo.aozora.app)に、未投稿の知育ソングを1日1本 headless で
制作→安全ゲート→投稿する仕組み。

## 4 層(ADR-2607162200 に準拠)
- **cadence tick**: `tools/cadence.cljs` が getAuthorFeed で既投稿を確認し、
  `resources/songs.edn` の未投稿トピックを :priority 降順で1つ選ぶ。
- **production**: `tools/produce.cljs <topic> [--publish]` が
  compose→audio(ローカル VOICEVOX)→video(ffmpeg)→gate→publish を一気通貫。
- **generation**: VOICEVOX(歌唱: sing_frame_audio_query→frame_synthesis、
  話し: audio_query→synthesis)+ nbb 純正伴奏シンセ + ffmpeg 合成。
- **publish gate**: `tools/run_gate.cljs` = kids-safety HARD gate
  (loudness/尺/フラッシュ/語彙/COPPA metadata/IP)。green で auto-publish、
  hold で produce ごと非ゼロ終了(escalate、公開しない)。

## 手動実行
```
cd orgs/gftdcojp/ai-gftd-dougaka-kodomo
nbb --classpath src:resources tools/produce.cljs doubutsu-koe          # ドライラン(制作+gate、投稿しない)
nbb --classpath src:resources tools/produce.cljs doubutsu-koe --publish # 投稿まで
nbb --classpath src:resources tools/cadence.cljs --publish              # 次の未投稿を自動選択して投稿
```

## 定期実行(launchd、opt-in)
`deploy/com.gftd.dougaka-kodomo-cadence.plist` を用意済み(即ロードしない)。
VOICEVOX 常駐 & マシン負荷が落ち着いている環境で opt-in 有効化する:
```
cp deploy/com.gftd.dougaka-kodomo-cadence.plist ~/Library/LaunchAgents/
launchctl load ~/Library/LaunchAgents/com.gftd.dougaka-kodomo-cadence.plist   # 毎日 10:00
launchctl unload ~/Library/LaunchAgents/com.gftd.dougaka-kodomo-cadence.plist # 停止
```
本命は murakumo fleet の Mac mini 常駐(ffmpeg/rsvg/nbb/VOICEVOX が要る)。
cloud-only の routine では ffmpeg が動かないため、レンダは VOICEVOX を持つ
実機で回す(ADR-2607162200 の「実行環境」に準拠)。

## 前提ツール
nbb / ffmpeg / rsvg-convert / ローカル VOICEVOX(127.0.0.1:50021)。
歌唱の公開 API(api.murakumo.cloud/v1/audio/song、Phase B)はリモート消費者向けで、
10 スコアのバッチ合成では tunnel 帯域制約があるため、cadence はローカル VOICEVOX を既定にする。

## 曲を増やす
`compose_<x>.cljs` + `render_<x>_video.cljs` を書き、`resources/songs.edn` に
1 エントリ追加。curriculum の :lexicon が歌詞を被覆すること(gate が確認)。
