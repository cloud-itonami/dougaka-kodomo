# ai-gftd-dougaka-kodomo — 幼児向けオリジナルアニメ・知育ソング生成パイプライン

Cocomelon / ChuChu TV / Pinkfong / Infobells / El Reino Infantil 型の
**幼児向け（0〜4歳）オリジナル知育ソング動画**を 1 curriculum topic / 1 song spec
から自動生成する。`ai-gftd-yukkuri` の pure-planner アーキテクチャ
（no-IO `.cljc` core + `:exec` 実行分離 + dougaka/renderer-mac ffmpeg 合成）を
ベースに、掛け合い解説フォーマットを **歌 + ループアニメ** フォーマットに
置き換えたもの。設計の正本: superproject ADR-2607164500。

## yukkuri との差分（このリポジトリが所有するもの）

| 層 | yukkuri | dougaka-kodomo |
|---|---|---|
| 台本 | L/R 掛け合い解説 (`generate_script`) | **song spec**（歌詞 + メロディ EDN + 絵コンテ、反復・call-and-response 構造） |
| 声 | VOICEVOX 話し声 | **歌唱合成**（VOICEVOX song 経路が第一候補、話しパートは speech） |
| 画 | 紙芝居（静止 BG + 立ち絵） | **拍同期ループアニメ**（cutout 2D、車輪回転・バウンス等の loop plan） |
| QA | 尺/ラウドネス advisory | **kids-safety HARD gate**（COPPA / 語彙 / フラッシュ / ラウドネス / IP 類似） |
| 公開 | YouTube 手動 1/day | madeForKids=true + cadence 定期投稿（aozora ADR-2607162200 パターン） |

## 構成

- `src/kodomo/song.cljk` — song spec（歌詞・メロディ EDN）の生成 planner と検証
  （音域・反復率・セクション構造）。純データ、外部 IO なし。
- `src/kodomo/safety.cljk` — kids-safety gate。決定論チェックのみ
  （loudness / 尺 / フラッシュ頻度 / 語彙年齢 / metadata / クレジット）。
- `src/kodomo/pipeline.cljk` — produce stage-order と advance reducer
  （yukkuri `graphs/produce.cljc` と同型。`:score-safety` が hard gate）。
- `resources/characters.edn` — オリジナルキャラクターファミリー（機械可読 SSoT。
  yukkuri `content/channels.edn` と同型）。
- `resources/curriculum.edn` — 知育カリキュラム topic カタログ
  （ABC / かず / いろ / どうぶつ / 生活習慣 …）。

## 実行

```bash
# テスト（nbb が第一経路。JVM は互換）
kbb --backend sci --classpath src:test test/run.cljk
kbb -M:test
```

実 IO（VOICEVOX / ComfyUI / seedance / YouTube / D1）はこのリポジトリに置かない。
yukkuri と同じく実行系は `:exec` 側（murakumo engines / renderer-mac）が担う。

## License / credits

- 歌唱・話し声に VOICEVOX を使う場合、公開 description に
  `VOICEVOX:<話者名>` クレジット必須（yukkuri と同一の不変条件）。
- メロディは (a) オリジナル、または (b) パブリックドメイン童謡
  （Wheels on the Bus / ABC song 等）の**オリジナル編曲**のみ。
  既存の商用幼児チャンネルのキャラクター・楽曲・映像に類似させない
  （safety gate の IP チェック対象）。
