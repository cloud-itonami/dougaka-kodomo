# operator quickstart — dougaka-kodomo を手元で最後まで回す

**この repo で今日実際にできることを、踏める形で上から書く。** §1〜§5 は
**VOICEVOX も YouTube も aozora のアカウントも要らない** —— 歌声だけが外部依存なので、
そこに立ち入り用の代役を1本置くと、compose → video → kids-safety gate までが
手元で最後まで通る。踏めなかったものは §6 に理由付きで分けてある。

**出力はすべて 2026-09-08 に実際に walk した結果で、手打ちの値は無い。** 秒数と
バイト数はこの walk の値であって定数ではない。walk した機械: macOS 15.3、
load average 10.29（walk 中は 14〜19）、他セッション並走中。

## 0. 前提

| 要るもの | 確認 | この walk で使った版 | どこで要る |
|---|---|---|---|
| nbb | `nbb --version` | v1.5.212 | 全部（第一経路） |
| Node.js | `node --version` | v26.7.0 | nbb の host |
| ffmpeg | `ffmpeg -version` | 8.0 | §3 映像合成 / §4 計測 |
| ffprobe | `ffprobe -version` | 8.0 | §4 尺の計測 |
| rsvg-convert | `rsvg-convert --version` | 2.62.1（Homebrew `librsvg`） | §3 SVG→PNG |
| JDK | `java -version` | openjdk 24.0.2 | §1 の JVM 側だけ（任意） |
| Clojure CLI | `clojure --version` | 1.12.5.1654 | §1 の JVM 側だけ（任意） |
| VOICEVOX | `curl 127.0.0.1:50021/version` | **無し**（§6） | 本番の歌声のみ |

この repo は **npm パッケージを 1 つも要求しない**。`package.json` に
`@ipld/dag-cbor` と `@noble/curves` が宣言されているが、これは §6 の publish 経路
（`tools/publish_aozora.cljs`）だけが使う。§1〜§5 に `npm install` は要らない
（この walk では一度も走らせていない）。

```bash
git clone git@github.com:cloud-itonami/dougaka-kodomo.git
cd dougaka-kodomo
```

## 1. テストが通ることを見る

**nbb が第一経路**（CLAUDE.md の runtime 優先順位）。JVM は互換確認用。

```bash
nbb --classpath src:test test/run.cljs
```

実出力:

```
Testing kodomo.song-test

Testing kodomo.safety-test

Ran 17 tests containing 28 assertions.
0 failures, 0 errors.
```

JVM 側も同じ数を返す（初回は test-runner の取得でネットワークが要る）:

```bash
clojure -M:test
```

```
Running tests in #{"test"}

Testing kodomo.safety-test

Testing kodomo.song-test

Ran 17 tests containing 28 assertions.
0 failures, 0 errors.
```

⚠ `clojure -M:test` は `.cpcache/` を作る。`.gitignore` に入れてあるので
`git status` は汚れない。

この 17 本は **`kodomo.safety` の拒否方向を純関数の水準で既に固定している**
（loudness / flash / made-for-kids / 外部リンク / VOICEVOX クレジット / 未知語）。
§5 が足すのはその上の層 —— **実際の mp4 を ffmpeg で計測した値**で同じ拒否が
出るか、である。

## 2. compose —— 歌を組む（外部 IO なし）

composer は純データ。VOICEVOX も ffmpeg も呼ばない。

```bash
nbb --classpath src:resources tools/compose_kazu.cljs     /tmp/kodomo-build-kazu
nbb --classpath src:resources tools/compose_iro.cljs      /tmp/kodomo-build-iro
nbb --classpath src:resources tools/compose_doubutsu.cljs /tmp/kodomo-build-doubutsu
```

実出力（3 本とも exit 0）:

```
song spec valid. total 164 beats = 98.39999999999999 sec; 10 scores, 10 spoken lines
iro song spec valid. total 156 beats = 97.5 sec; 10 scores, 8 spoken lines
doubutsu song spec valid. total 144 beats = 86.39999999999999 sec; 10 scores, 5 spoken lines
```

build-dir に 4 つの実行プランが出る（`scores.json` が VOICEVOX 歌唱スコア、
`spoken.json` が話しパート、`accomp.json` が伴奏、`timeline.json` が映像用）:

```
accomp.json    4263
scores.json   16576
spoken.json     711
timeline.json  1474
```

**composer は `content/<曲>.edn` も書き戻す。これは追跡されているファイルだが、
決定論なので内容は変わらない。** walk で確認した:

```bash
shasum -a 256 content/*.edn   # compose の前後で一致する
```

```
2b30529c8ca69f66a6b0de2f412cb0fd9d066618410ead076c4a0fd3ab5251e0  content/doubutsu-no-uta.edn
a24a42ed234232e9a1892e802ea9a3c5f3568cff00a10d3d3468348ec437877b  content/iro-no-uta.edn
5f12e6e0f07c320196d974b43c9e7dfb1db50809c46d452ea4dc26730c28e599  content/kazu-no-uta.edn
```

3 本走らせたあとの `git status --porcelain` は空。**差分が出たら composer を
変えたということなので、その差分をレビューしてから commit する。**

## 3. 映像を作る —— 歌声の代役を 1 本置く

`tools/render_kazu_video.cljs` は最後に `audio-final.wav` と多重化する。
本番ではそれが VOICEVOX の歌声だが、**手元に無くても代役を置けば映像経路は
最後まで通る**。尺は §2 が出した `total-sec`（かずのうたは 98.4 秒）に合わせる。

```bash
cd /tmp/kodomo-build-kazu
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i "sine=frequency=440:duration=98.4:sample_rate=48000" \
  -af "loudnorm=I=-14:TP=-2.0,volume=0.9" -ac 2 -y audio-final.wav
```

`-14 LUFS` を狙うのは §4 のゲートが `-20..-12 LUFS` を要求するため。
**代役は「ゲートを通す音」であって「正しい歌」ではない** —— §5 で、この同じ
仕掛けを使って**ゲートが落ちる**ことも見る。

```bash
cd <repo>
nbb tools/render_kazu_video.cljs /tmp/kodomo-build-kazu
```

⚠ **この workspace では高負荷 build を resource governor 経由で回す**
（CLAUDE.md の repo-wide mandatory）。walk では実際に 1 度弾かれた ——
別 repo の build が lock を持っていて `REFUSED build-lock held by pid=...`
が返り、governor が正しく仕事をした。

```bash
node <superproject>/scripts/resource-guard.mjs run build -- \
  nbb tools/render_kazu_video.cljs /tmp/kodomo-build-kazu
```

実出力:

```
assets rendered
sec0.mp4 rendered
sec1.mp4 rendered
sec2.mp4 rendered
sec3.mp4 rendered
sec4.mp4 rendered
sec5.mp4 rendered
kazu-no-uta.mp4 done
```

`ffprobe` で確かめた成果物:

```
codec_name=h264
width=1280
height=720
codec_name=aac
duration=98.400000
size=4364502
```

## 4. kids-safety gate を通す（green）

ゲートは **実測した facts** を `kodomo.safety/gate` に通す。数字を手で渡す口は無い
—— loudness は ffmpeg `loudnorm`、フラッシュは `signalstats` の YDIF、尺は
`ffprobe` から採る。

```bash
# 公開文（VOICEVOX クレジットが要る。resources/songs.edn の :post-text がそれ）
printf 'かずのうた 🍎 いち・に・さん!\nメロとポポとミミといっしょに、1から10まで かぞえよう!\nこどもむけ知育ソング(0〜4さい向け)\n#こどもむけ #知育ソング #かずのうた\nVOICEVOX:ずんだもん / VOICEVOX:四国めたん\n' \
  > /tmp/kodomo-build-kazu/post-text.txt

nbb --classpath src:resources tools/run_gate.cljs \
  /tmp/kodomo-build-kazu \
  /tmp/kodomo-build-kazu/kazu-no-uta.mp4 \
  /tmp/kodomo-build-kazu/post-text.txt \
  content/kazu-no-uta.edn kazu-1-10
```

実出力（exit 0）:

```
facts: {:made-for-kids? true, :kind :single, :integrated-lufs -14.95, :ip-flags [], :uses-voicevox? true, :max-flashes-per-sec 1, :duration-sec 98.4, :true-peak-dbtp -13.9}
tokens: 75 lexicon: 16
 OK  :loudness {:integrated-lufs -14.95, :true-peak-dbtp -13.9}
 OK  :duration {:duration-sec 98.4, :kind :single, :allowed [60 300]}
 OK  :flash {:max-flashes-per-sec 1}
 OK  :vocab {:unknown-ratio 0, :unknown-sample []}
 OK  :metadata {:errors []}
 OK  :ip-similarity {:flags []}
DECISION: :publish
```

`build-dir/gate-result.edn` に判定が残る。

## 5. gate が**落ちる**ことを見る（ここまでやって初めて gate）

**通るところしか見ていないゲートは、通すことしかしていないゲートと区別が付かない。**
2 本の control を、それぞれ**別の理由で**落として確かめる。**落ちた理由が
名指ししたものと一致していること**まで見る（別の原因で赤くなった実行を
「拒否できた」と数えないため）。

### control A —— 音が大きすぎる（`:loudness` だけが落ちること）

映像は §3 と同一。音だけ差し替える。

```bash
cd /tmp/kodomo-build-kazu
ffmpeg -hide_banner -loglevel error -f lavfi \
  -i "sine=frequency=440:duration=98.4:sample_rate=48000" \
  -af "loudnorm=I=-5:TP=-0.2" -ac 2 -y audio-loud.wav
ffmpeg -hide_banner -loglevel error -i video-noaudio.mp4 -i audio-loud.wav \
  -c:v copy -c:a aac -b:a 192k -movflags +faststart -shortest -y kazu-LOUD.mp4
```

同じ引数で gate を回すと **exit 1**:

```
facts: {... :integrated-lufs -5.05, ... :true-peak-dbtp -4}
 NG  :loudness {:integrated-lufs -5.05, :true-peak-dbtp -4}
 OK  :duration {:duration-sec 98.4, :kind :single, :allowed [60 300]}
 OK  :flash {:max-flashes-per-sec 1}
 OK  :vocab {:unknown-ratio 0, :unknown-sample []}
 OK  :metadata {:errors []}
 OK  :ip-similarity {:flags []}
DECISION: :hold
```

**落ちたのは `:loudness` の 1 本だけで、残り 5 本は OK のまま。**
`-5.05 LUFS` は許容帯 `-20..-12` の外なので、落ちた理由は名指ししたものと一致する。

### control B —— VOICEVOX クレジットが無い（`:metadata` だけが落ちること）

mp4 は §4 の green と同一。**公開文だけ**を差し替える。

```bash
printf 'かずのうた 🍎 いち・に・さん!\nこどもむけ知育ソング(0〜4さい向け)\n' \
  > /tmp/kodomo-build-kazu/post-nocredit.txt
```

exit 1:

```
 OK  :loudness {:integrated-lufs -14.95, :true-peak-dbtp -13.9}
 ...
 NG  :metadata {:errors [:voicevox-credit-missing]}
 OK  :ip-similarity {:flags []}
DECISION: :hold
```

**同じ mp4 が §4 では `:publish`、ここでは `:hold`。** 変えたのは公開文 1 個だけで、
落ちた理由も `:voicevox-credit-missing` の 1 個だけ。

ゲートは `tools/produce.cljs` から HARD gate として呼ばれるので、この exit 1 は
**produce ごと止めて publish させない**（ADR-2607162200 の escalate）。

## 6. 踏めなかったもの（この機械では。理由付き）

| 何を | 何が起きたか | 何が要るか |
|---|---|---|
| **VOICEVOX 歌唱・話し声** | `curl -m 3 http://127.0.0.1:50021/version` が接続できない（`000`）。`~/.murakumo/voicevox-engine` もこの機械には無い | ローカル VOICEVOX Engine（fleet ノードには常駐。`docs/PHASE-C-CADENCE.md`） |
| **`tools/produce.cljs` の一気通貫** | compose は通り、伴奏 `accomp.wav`（4,339,440 サンプル）まで**純 nbb で書けた**あと、audio 段で `TypeError: fetch failed` → `✘ audio (public API) failed (exit 1)` | 同上。伴奏シンセは外部依存が無いのでオフラインで動く |
| **`tools/cadence.cljs`（未投稿の自動選択）** | `https://appview.aozora.app/xrpc/app.bsky.feed.getAuthorFeed` が 20 秒で timeout（`curl` exit 28）。DNS は引ける（`104.21.87.101` / `172.67.169.53`、Cloudflare）ので**名前ではなく到達性** | appview への到達性。到達できないのは投稿の有無ではないので、「未投稿」と読まない |
| **publish（`tools/publish_aozora.cljs`）** | 実行していない。actor seed（env / Keychain / `~/.murakumo/secrets/`）と appview 到達性の両方が要り、**外向きの投稿**なので walk では踏まない | seed + 到達性。踏むなら `--publish` を明示 |
| **fleet ノードの cron** | `asher` / `naphtali` の crontab はこの機械からは観測していない | ノード上で `crontab -l` |

⚠ **「到達できなかった」を「無い」と読まない。** 上の appview は timeout であって
空のフィードではない。`cadence.cljs` は `posted-rkeys` が空集合だと
**全曲を未投稿とみなして先頭を投稿しにいく** ので、到達性が落ちている状態で
`--publish` を回さないこと。

## 7. 曲を増やす

`tools/compose_<x>.cljs` と `tools/render_<x>_video.cljs` を書き、
`resources/songs.edn` に 1 エントリ足す。**歌詞の語彙が
`resources/curriculum.edn` の `:curriculum/lexicon` に被覆されること**を
gate の `:vocab` が確かめる（未知語率 20% 以下）。かずのうたの実測は
`tokens: 75 lexicon: 16` で未知語率 0。

`tools/run_gate.cljs` は曲固有の `tokenize` map を持つが、**無くても動く** ——
空白区切りなら split、それも無ければ行そのものを 1 トークンとして扱う。
新しい曲で `:vocab` が落ちたら、まず lexicon を見る（tokenize を足すのはその後）。

## 8. 既知のずれ

- `docs/PHASE-C-CADENCE.md` は fleet ノード側の配置を
  `orgs/gftdcojp/ai-gftd-dougaka-kodomo` と書いている。**この superproject 上の
  path は `orgs/cloud-itonami/dougaka-kodomo`**（`manifest/west.yml`、remote も
  `cloud-itonami/dougaka-kodomo`）。ローカル手順の `cd` は修正したが、
  **ノード側（rsync 先と crontab）の実配置はこの機械から観測できないので変えていない**
  —— 投入前に `ssh <node> 'ls ~/orgs'` で確かめること。
- `README.md` と `package.json` の名乗りは旧名 `ai-gftd-dougaka-kodomo` のまま。
  GitHub のリダイレクトが効くので壊れてはいない。
