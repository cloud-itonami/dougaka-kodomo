# Phase C — cadence 定期投稿(ADR-2607164500 / 2607162200)

こどもチャンネル(kodomo.aozora.app)に、未投稿の知育ソングを1日1本 headless で
制作→安全ゲート→投稿する仕組み。

## 4 層(ADR-2607162200 に準拠)
- **cadence tick**: `tools/cadence.cljk` が getAuthorFeed で既投稿を確認し、
  `resources/songs.edn` の未投稿トピックを :priority 降順で1つ選ぶ。
- **production**: `tools/produce.cljk <topic> [--publish]` が
  compose→audio(ローカル VOICEVOX)→video(ffmpeg)→gate→publish を一気通貫。
- **generation**: VOICEVOX(歌唱: sing_frame_audio_query→frame_synthesis、
  話し: audio_query→synthesis)+ nbb 純正伴奏シンセ + ffmpeg 合成。
- **publish gate**: `tools/run_gate.cljk` = kids-safety HARD gate
  (loudness/尺/フラッシュ/語彙/COPPA metadata/IP)。green で auto-publish、
  hold で produce ごと非ゼロ終了(escalate、公開しない)。

## 手動実行
```
cd orgs/cloud-itonami/dougaka-kodomo
nbb --classpath src:resources tools/produce.cljk doubutsu-koe          # ドライラン(制作+gate、投稿しない)
nbb --classpath src:resources tools/produce.cljk doubutsu-koe --publish # 投稿まで
nbb --classpath src:resources tools/cadence.cljk --publish              # 次の未投稿を自動選択して投稿
```

## 実行環境 — murakumo fleet 分散(ADR-2607164500 addendum、2026-07-18)

**main-2(開発端末)は司令塔のみ。実際の produce/cadence 実行は murakumo fleet の
Mac mini 上で行う**(gad は ComfyUI GPU compute で既に高負荷のため対象外)。

| ノード | tailscale IP | 役割 | VOICEVOX | cadence |
|---|---|---|---|---|
| asher | 100.96.122.69 | active worker | 常駐(nohup、`~/.murakumo/voicevox-engine`) | cron 準備済み(下記、opt-in) |
| naphtali | 100.101.27.85 | standby worker | 常駐(nohup) | 未設定(failover 用の予備能力) |
| main-2 | — | 司令塔(SSH で fleet に指令のみ、自身では compute しない) | — | — |

- 各ノードへの `orgs/gftdcojp/ai-gftd-dougaka-kodomo` + `orgs/kotoba-lang/kotobase-client`
  は main-2 から `rsync`(GitHub 認証なしの code push、compute ではない)。GitHub 直
  clone にするなら fleet ノードに `gh auth login` が要る(未設定)。
  更新時は同じ rsync を再実行する。
- **headless SSH セッションには `launchctl bootstrap`(gui/user domain)が使えない**
  (GUI ログインセッションが存在しないため `Domain does not support specified action`
  / `Input/output error` で失敗— 実測 2026-07-18、asher)。VOICEVOX Engine は
  `nohup ./run --host 127.0.0.1 --port 50021 &` でデタッチ起動する(launchd 常駐の
  代替。再起動で消えるが fleet ノードは連続稼働 47 日超の実績があり許容)。
  `tools/cadence.cljk` は起動前に `ensure-voicevox!` で自己ヘルスチェック
  → 未応答なら自動起動するので、cron 実行前の手動起動は不要。
- **同じ理由で macOS Keychain も headless SSH からは書き込めない**
  (`security add-generic-password` が `Write permissions error`)。
  `tools/publish_aozora.cljk` の actor seed 解決は
  env `AOZORA_ACTOR_SEED_HEX` → Keychain → `~/.murakumo/secrets/<service>.hex`
  (chmod 600)の順にフォールバックする。fleet ノードでは全ノード**同一の
  kodomo actor seed**(main-2 の Keychain 値)をファイルへ配布済み — 別 seed を
  生成すると別 DID になり account continuity が壊れるため、ノードごとに
  新規生成させない。
- `tools/produce.cljk` の kotobase-client パスは
  (main-2 の絶対パスをハードコードしていたバグを修正し)
  `<repo-root>/../../kotoba-lang/kotobase-client/src` 相対解決に統一
  (`KOTOBASE_CLIENT_SRC` env で上書き可)— `orgs/<org>/<repo>` の
  sibling レイアウトを fleet ノードでも再現しているので機種を問わず動く。

## 定期実行(cron、fleet ノード上、opt-in)
main-2 の `deploy/com.gftd.dougaka-kodomo-cadence.plist`(launchd)は GUI セッション
前提のため fleet の headless ノードには使えない。かわりに `crontab` を使う
(ready 状態、まだ asher に投入していない — 投入する場合の手順):
```
ssh asher@100.96.122.69 '(crontab -l 2>/dev/null; echo "0 10 * * * cd \$HOME/orgs/gftdcojp/ai-gftd-dougaka-kodomo && PATH=/opt/homebrew/bin:/usr/bin:/bin:/usr/local/bin /opt/homebrew/bin/nbb --classpath src:resources tools/cadence.cljk --publish >> /tmp/dougaka-kodomo-cadence.log 2>&1") | crontab -'
# 停止: ssh asher@100.96.122.69 'crontab -l | grep -v dougaka-kodomo-cadence | crontab -'
```
naphtali は standby(同じセットアップ済みだが cadence cron は入れない — 二重投稿
race を避けるため active は常に1ノードのみ。asher 障害時は上記コマンドの
ホストを naphtali に変えて手動フェイルオーバー)。

## 前提ツール
nbb / ffmpeg / rsvg-convert / ローカル VOICEVOX(127.0.0.1:50021)— fleet ノードには
Homebrew で `p7zip`(VOICEVOX Engine 配布物が 7z)/ `librsvg` を追加インストール済み。
歌唱の公開 API(api.murakumo.cloud/v1/audio/song、Phase B)はリモート消費者向けで、
10 スコアのバッチ合成では tunnel 帯域制約があるため、cadence はローカル VOICEVOX を既定にする。

## 曲を増やす
`compose_<x>.cljs` + `render_<x>_video.cljs` を書き、`resources/songs.edn` に
1 エントリ追加。curriculum の :lexicon が歌詞を被覆すること(gate が確認)。
