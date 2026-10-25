# HazardMusicPlayer — 可行性与实施计划

> 目标设备：FiiO M6（实测 Android 7.0 / API 24，定制 FiiO OS）
> 需求来源：`draft.md`
> 本文只做评估与计划，不含实现代码。

## 1. 结论

**可行，但有三个明确的边界条件。**

1. **协议侧可行**：2026-10-25 用桌面 JVM 探针实测，搜索、每日推荐、歌单详情、二维码登录、歌曲播放地址均可直接取到，不需要自建服务端。
2. **"在线无损"取决于账号，且必须走 eapi**：已用实际黑胶账号验证，`eapi` 的 `song/url/v1` 返回 `type=flac`、`level=lossless`；plaintext 同名接口即使带 `MUSIC_U` 也只返回 320 kbps MP3。平台没有 FLAC 解码器，客户端内置 libFLAC 后输出 16-bit PCM，因此**只承诺 44.1/48 kHz、16-bit 无损，不承诺 Hi-Res**。
3. **设备资源比 draft 更宽裕但仍有硬约束**：RAM 标称 768 MB（实测 `MemTotal` 712 MB），内置存储 2 GB 可用户使用、实测仅剩 1 GB，microSD 58 GB、剩 28 GB，屏幕 480×800/240 dpi（320×533 dp）。因此**下载必须落 microSD**，并且要按"低内存 + 无 GMS + 32 位 ART"来设计。

最大的不确定性不在客户端，而在**网易云接口的持续演进**（2026 年已上线 xeapi/AegisSDK 与 checkToken 风控）。缓解方式是协议层隔离 + 桌面探针回归，而不是把协议散落在 UI 里。

## 2. 已核实的事实

### 2.1 设备（官方参数 + 真机实测）

| 项 | 值 |
|---|---|
| RAM | 标称 768 MB；`MemTotal` 712 MB |
| ROM | 4 GB，用户可用 2 GB；实测剩 1 GB |
| 屏幕 | 3.2" IPS，480×800，240 dpi → 320×533 dp |
| SoC / ABI | Exynos 7270，双核 A53 1 GHz；`armeabi-v7a`（仅 32 位） |
| 系统 | **Android 7.0（API 24）**，构建 `eng.FiiO.20200519.165027` |
| 网络 | Wi-Fi 2.4 GHz，BT 4.2；无 GMS |
| 存储扩展 | microSD 58 GB（剩 28 GB），vfat，挂载 `/storage/external_sd` |
| 第三方应用 | 默认白名单；FW ≥ 1.0.3 可解锁；本机已解锁 |

### 2.2 接口实测（2026-10-25，桌面 JVM + weapi/eapi 实现，无登录/登录态）

| 能力 | 接口 | 结果 |
|---|---|---|
| 搜索 | `POST /weapi/search/get` | 200，返回歌曲 |
| 搜索（更全，带 privilege） | `POST /weapi/cloudsearch/pc` | 200，含 `maxBrLevel`/`fee` |
| 每日推荐 | `POST /weapi/v3/discovery/recommend/songs` | 200，返回 `dailySongs`（无登录也为访客推荐） |
| 二维码取 key | `POST /weapi/login/qrcode/unikey` | 200，`unikey` |
| 二维码轮询 | `POST /weapi/login/qrcode/client/login` | 无效 key 返回 800（预期） |
| 账号信息 | `POST /weapi/nuser/account/get` | 200，`account:null` |
| 用户歌单 | `POST /weapi/user/playlist` | 200（uid=1 公开歌单） |
| 歌单详情 | `POST /weapi/v6/playlist/detail` | 200（热歌榜，200 首） |
| 批量歌曲详情 | `POST /api/v3/song/detail` | 200 |
| 播放地址（旧） | `POST /api/song/enhance/player/url` | 200，320 kbps MP3 |
| 播放地址（v1/eapi，已登录） | `POST /eapi/song/enhance/player/url/v1` | 200；`type=flac`、`level=lossless`，文件头 `fLaC` |
| 播放地址（v1/plaintext，已登录） | `POST /api/song/enhance/player/url/v1` | 200；仍只有 320 kbps MP3，不可用于无损 |
| 喜欢列表 / 喜欢操作 | `POST /weapi/likelist`、`/weapi/song/like` | 无登录返回 301（需登录，符合预期） |
| 图片 CDN | `http://p*.music.126.net/...` | 200，可直接 HTTP 加载 |

补充：
- **已登录实测（2026-10-25）**：eapi 返回的 FLAC 文件头均为 `fLaC`，两首样本分别是 44.1 kHz/16 bit/23.7 MB 和 48 kHz/16 bit/29.0 MB；同曲 `hires` 为 48 kHz/24 bit/53.6 MB。单曲体积按 24–54 MB 预估。
- **点播 URL 必须走 eapi**：plaintext `/api/song/enhance/player/url/v1` 带登录 cookie 仍返回 320 kbps MP3。plaintext 只用于调试/无登录兜底。
- 播放地址是 **http**，有效期 `expi=1200` 秒；下载需在取到 URL 后尽快开始。
- `cloudsearch` 返回 `privilege.maxBrLevel`，可据此在点播前判断可用音质，避免"点了才失败"。
- TLS：`*.music.163.com` 证书链 `GeoTrust G2 TLS CN RSA4096 SHA256 2022 CA1` → `DigiCert Global Root G2`（交叉签至 `DigiCert Global Root CA`）。预期 Android 7 的根证书库可校验（根 CA 在 Android 7 系统信任列表中），但仍需在应用中实测。

### 2.3 媒体栈实测（2026-10-25，真机）

| 项 | 结果 |
|---|---|
| 系统 `MediaPlayer` | **不可用**。`MediaPlayer.create` 也会让 `mediaserver` 在 `MediaPlayerService::Client::setDataSource_pre+217` 空指针崩溃（SIGSEGV），调用方随后收到 `error (100,0)` / `prepareAsync called in state 0` |
| 平台解码器 | MP3/AAC/Opus/Vorbis 有解码器；**没有 `audio/flac` 解码器**（只有 `OMX.google.flac.encoder`） |
| ExoPlayer 2.18.7 + MediaCodec | MP3 正常（`OMX.google.mp3.decoder`，AudioTrack 活跃） |
| ExoPlayer + vendored libFLAC | 48 kHz/24-bit FLAC 可解码输出；AudioTrack 为 **48 kHz / PCM 16-bit**，libFLAC 内部把 24-bit 降到 16-bit |
| 物理按键 | `gpio_keys` 暴露 `KEY_PLAYPAUSE`/`KEY_NEXTSONG`/`KEY_PREVIOUSSONG`；`media dispatch` 能触发我们的 `MediaSession` 回调 |
| 媒体键默认接收者 | 系统恢复项是 `com.fiio.music/...MediaButtonReceiver`，但我们的 `MediaSession` 激活后能拿到按键 |
| 内存 | 测试 Activity + ExoPlayer + libFLAC：PSS 约 40.5 MB；前台 Service 连续播放 10 分钟后 PSS 42.9 MB |
| 10 分钟息屏网络播放 | 通过：单曲循环 3 次、AudioTrack 持续活跃、WiFi 保持连接、`mWakefulness=Asleep`、电量 82%→82%、温度 25.1°C，Service 未被杀 |
| APK | debug 12 MB（含 9.1 MB 测试音频）；去掉测试资源并 R8 后 **732 KB**，已含 ExoPlayer + 278 KB `libflacJNI.so`；产品目标 ≤ 1.5 MB |

后果：**播放层必须用 ExoPlayer，不能用系统 MediaPlayer；FLAC 必须自带 libFLAC**。MediaPlayer 崩溃后需要重启设备才能恢复媒体服务。

已编译好的扩展 Java 源码、`libflacJNI.so`（armeabi-v7a）、proguard 规则和许可证保存在 `D:\Devtools\exoplayer-flac-2.18.7`，P1 直接复制进工程；重建方法见 `ENVIRONMENT.md` §10。

### 2.4 协议演进现状（风险证据）

- 原 `Binaryify/NeteaseCloudMusicApi` 已归档（最后提交 2024-02）。
- 维护中的 `api-enhanced` 已引入 **xeapi**：X25519 密钥交换 + AES-GCM + 动态会话密钥 + 改过的 SHA-256，并依赖 yidun `X-antiCheatToken`；`song/url/v1` 在该项目中已改用 xeapi。
- 本次实测说明**这些接口目前仍接受 eapi/weapi**，但这是"当前可用"，不是长期承诺。
- 影响：客户端必须把加密/请求封装成独立层（`api` + `crypto`），一旦某接口切 xeapi，改动限制在一处；必要时可切换到"自建网关"兜底（见 §3.4）。

## 3. 技术方案

### 3.1 技术栈选择

| 方案 | 结论 |
|---|---|
| **纯 Java + Framework View + ExoPlayer 2.18.7** | **采用**。系统 MediaPlayer 在该固件上必崩；ExoPlayer 已验证 MP3/FLAC 可用。实测 release 732 KB（无业务代码），产品目标 ≤ 1.5 MB；PSS 目标 ≤ 120 MB |
| 系统 MediaPlayer | 否。`setDataSource` 触发 `mediaserver` SIGSEGV，真机不可用 |
| Kotlin + AndroidX + Media3 | 否。Media3 没有 FLAC 软件解码器产物，且体积/常驻内存更大 |
| Fork ponymusic | 否。Compose + Hilt + Room + Media3 + multidex，与"轻量"目标冲突；仅作接口参考 |
| Flutter / RN | 否。运行时内存与包体不可接受 |

约束：
- `minSdk 23`、`targetSdk 28`、`compileSdk 34`；不启用 core library desugaring。
- 不使用 GMS / Firebase / Crashlytics。
- 不引入图片库、网络库、JSON 库（用 `org.json`）、QR 库（自带最小实现）。
- `android.useAndroidX=true`（ExoPlayer 传递依赖 `androidx.annotation`），但不用 AndroidX UI/Compose。
- release 需关掉 lintVital：`lint { checkReleaseBuilds false; abortOnError false }`（`targetSdk 28` 会触发 `ExpiredTargetSdkVersion`，本项目不上 Play）。
- `jniLibs` 只放 `armeabi-v7a/libflacJNI.so`；不引入其他 ABI 的 native 库。

### 3.2 模块划分

```
app/
  api/        NetEaseClient（weapi/eapi）、CookieJar、错误码、限速
  crypto/     AES-CBC/ECB、RSA-NoPadding、MD5
  model/      Track / Playlist / Account / Privilege
  store/      SQLite（收藏、歌单、下载、最近播放、kv）
  player/     PlayerService、MediaSession、队列、音质回退
  download/   DownloadService、续传、路径探测
  ui/         主界面、搜索、歌单、播放页、设置、登录（QR）
  util/       BitmapLoader、LruCache、线程池、格式化
```

请求/数据流：UI → Repository → (API | DB | Download | Player)，网络回调统一回主线程；所有磁盘/网络操作在固定线程池，禁止在 UI 线程。

### 3.3 关键设计点

**登录**：二维码为主，走**新版桌面端 plaintext 接口**：
`POST https://interface.music.163.com/api/login/qrcode/unikey`、`/api/login/qrcode/client/login`（表单 `type=3`）。旧 weapi 接口会触发 `-462/8821` 风控。轮询处理 800/801/802/803，并把 `-462/8810/8821` 明确提示为"安全验证失败"。
`MUSIC_U` 存 `SharedPreferences(MODE_PRIVATE)`。**Cookie 登录兜底**：粘贴浏览器里的 `MUSIC_U`（可带 `__csrf`），写入后调用 `account` 校验；不做手机号密码登录。

**红心写入**：`POST /api/song/like`（eapi，`trackId` + `userid` + `like`）。旧 `/api/radio/like` 在当前网络返回 `-460 网络环境风险`，已弃用。已实测喜欢/取消均 200，重启后 likelist 与 UI 状态一致。

**播放**：
- `ExoPlayer 2.18.7` + 前台 Service + framework `MediaSession`（API 21 可用），接收 M6 物理键（`KEYCODE_MEDIA_*`）。
- **必须内置 libFLAC**：平台没有 `audio/flac` 解码器。使用 vendored `com.google.android.exoplayer2.ext.flac`（`FlacDecoderJni`/`LibflacAudioRenderer`）+ `libflacJNI.so`（armeabi-v7a，NDK r21 + flac 1.3.2），`DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER`。
- **R8 keep 规则（release 必须）**：`libflacJNI` 通过 JNI 反射 `com.google.android.exoplayer2.extractor.FlacStreamMetadata` 与 `com.google.android.exoplayer2.metadata.flac.PictureFrame` 的构造器。必须 `-keep` 这两个包，否则 release 会在播放线程 `JNI DETECTED ERROR: mid == null` 崩溃（debug 不复现）。
- 音频焦点与耳机拔出由 ExoPlayer 处理（`setAudioAttributes(..., true)`、`setHandleAudioBecomingNoisy(true)`）；`setWakeMode(C.WAKE_MODE_NETWORK)` 在播放期间持有 CPU/WiFi 锁。
- 音质顺序：`lossless` → `exhigh` → `standard`；**URL 请求固定走 eapi**（plaintext 最高只有 320 kbps）；URL 失败或 ExoPlayer 解码失败会自动降级；`freeTrialInfo` 非空标记“试听”。
- 队列与自动切歌：`PlayerController` 持有内存队列，`STATE_ENDED` 自动下一首；mini player 与播放页共用该单例。
- 通知：4 个动作（上一首/播放暂停/下一首/停止），前台服务 ID 1001；`MediaSession` 同步播放状态与元数据。
- 队列持久化到 SQLite（重启恢复）尚未实现，留到 P3/P4。
- 预期限制：第三方应用走系统混音器，**不是 bit-perfect**；libFLAC 把 24-bit 解成 16-bit 输出，实测 48 kHz/16-bit，Hi-Res 档位不纳入承诺。

**下载**：取 URL → 临时文件 `.part` → Range 续传 → 完成后改名并入库；单任务并发，剩余空间预检；默认目录 `microSD/Music/Hazard/`，启动时探测可写卷；完成后 `MediaScannerConnection` 扫描，便于 FiiO Music 也能看到。

**UI**：480×800 / 240 dpi → **320×533 dp**（真机已确认）。布局按 320 dp 宽设计，不做向右扩展的多栏；列表用 `ListView` + ViewHolder，分页 30；图片 `BitmapFactory` + `inSampleSize` + `RGB_565` + LRU（≤8 MB）。

**数据**：SQLite 表 `favorites`/`playlists`/`playlist_tracks`/`downloads`/`recent`/`kv`；启动先读本地，再后台刷新；禁止每次进页面都打接口。

### 3.4 协议失效的兜底

保留接口抽象，若核心接口被 xeapi 收口：
1. 优先在 `api` 层补 xeapi（X25519 可用 JDK/Android 纯 Java 实现，AES-GCM API 21+ 可用）；
2. 若风控（checkToken）无法在端上稳定解决，增加"可选网关"设置项，指向自建 `api-enhanced` 服务，客户端只换 BaseURL，不动 UI/业务层。

这一项不在 v1 范围内，只保证架构上不堵死。

## 4. 资源预算（目标值，不是实测）

| 指标 | 目标 |
|---|---|
| APK | ≤ 1.5 MB（R8 后，含 libFLAC） |
| 方法数 | < 64 K（不 multidex） |
| 冷启动 | ≤ 2 s |
| PSS：空闲 / 播放 | ≤ 60 MB / ≤ 120 MB |
| 图片缓存 | ≤ 8 MB |
| 列表分页 | 30 条 |
| 下载并发 | 1（可配置 2） |
| 数据库 | ≤ 2 MB（不含音频） |

## 5. 分阶段计划

### P0 设备与协议验证（止损点，约 1 天）

- **已完成**：白名单解锁、USB 调试、`adb` 连接；记录 API 24 / `armeabi-v7a` / 240 dpi / `MemTotal` 712 MB / SD `/storage/external_sd`（见 `ENVIRONMENT.md`）。
- **已完成**：探针 APK 安装、启动、前台 Service、通知。
- **已完成**：登录态与无损验证。eapi `song/url/v1` 返回 `type=flac`、`level=lossless`；样本为 44.1 kHz/16 bit、48 kHz/16 bit，`hires` 为 48 kHz/24 bit，文件头均为 `fLaC`。plaintext 接口仅 320 kbps，已确认必须走 eapi。
- **已完成**：媒体栈验证。系统 `MediaPlayer` 必崩（mediaserver SIGSEGV），平台无 FLAC 解码器；ExoPlayer 2.18.7 + vendored libFLAC 已实测 320 kbps MP3 与 48 kHz/24-bit FLAC，AudioTrack 为 48 kHz/PCM16；`media dispatch` 的 play/pause/next/prev 均到达我们的 MediaSession。
- **已完成**：10 分钟息屏 HTTP 播放（循环 3 次不中断，PSS 42.9 MB）；R8 release 探针 732 KB。
- 待完成：`likelist`/`like` 读写；下载 URL 落盘；正式工程的 R8 体积。
- **验收**：以上全部有结论；若后台被系统杀，先解决再进入 P1。

### P1 协议层 + 登录 + 搜索/推荐/收藏（2–3 天）

- Gradle/AGP 工程（仅 ExoPlayer + 内嵌 libFLAC）、HTTP/Cookie/JSON/错误码/限速。
- weapi/eapi；二维码登录（含最小 QR 生成器）；搜索；每日推荐；歌单详情；收藏列表与喜欢/取消。
- **验收**：真机完成"登录 → 搜索 → 喜欢 → 杀进程重开仍登录且列表一致"；弱网/超时有明确提示。

**P1 状态：完成（2026-10-25）**

- 工程：Gradle 8.9 / AGP 8.7.3，wrapper 已生成；debug 3.2 MB，R8 release **574 KB**。
- 已实现：`Crypto`（weapi/eapi）、`CookieJar`、`NetEaseClient`、二维码登录 + Cookie 兜底、每日推荐、搜索、歌单、喜欢列表与红心、framework View UI。
- 已真机验证：
  - 扫码登录：新桌面端接口出码 200 → 801 → 802 → 803，cookies 落盘。
  - `account`、每日推荐 30 首、云搜索 30 首、用户歌单、likelist、歌单详情全部 200。
  - 喜欢的歌单 1355 首、普通歌单 3 首均能打开；红心喜欢/取消各 200。
  - 杀进程重启：登录态、歌单、likelist 与红心状态保持。
  - 首帧 512 ms，无崩溃。
- 已知环境限制：旧 `/api/radio/like` 返回 `-460`，已改用 `/api/song/like`；旧 weapi 扫码接口会 `8821`，已改新接口并保留提示。
- 未专门注入测试：弱网/超时提示（代码已处理）。

### P2 播放器 + 主界面（2–3 天）

- 前台服务、MediaSession、通知、队列、上一首/下一首、进度、音质回退、试听提示。
- mini player + 播放页 + 歌单详情；图片加载器。
- **验收**：息屏连续播放 1 小时不中断；物理键全可用；PSS ≤ 120 MB；列表滑动无明显卡顿。

**P2 状态：完成（2026-10-26）**

- 已实现：`PlayerController`（进程内单例）+ `PlaybackService` 前台服务；ExoPlayer + libFLAC；`MediaSession`、通知（上一首/播放暂停/下一首/停止）、队列与自动切歌、seek、音质回退（lossless → exhigh → standard）、试听标记。
- UI：MainActivity 底部 mini player；`PlayerActivity` 封面/标题/歌手/音质/进度/时间/控制；`ImageLoader`（LRU + 采样 + RGB_565）。
- 已真机验证：
  - 在线无损：`type=flac level=lossless`，AudioTrack 48 kHz/PCM16 活跃。
  - 10 分钟息屏连续播放：自动切歌 3 次，始终 FLAC，前台服务存活，PSS 61.7 MB，电量 82%→82%，温度 25.1°C。
  - `media dispatch` 的暂停/播放/下一首均生效；播放页 Seek 生效；通知含 4 个动作。
  - 首帧 / 播放页启动无崩溃。
- release（R8）967 KB，已用 debug keystore 签名安装；登录态与歌单数据保留。
- release 首次运行时崩溃：R8 改写了 `FlacStreamMetadata` / `PictureFrame` 的构造器与类名，libflacJNI 的 `NewObject` 找不到 method ID（`JNI DETECTED ERROR: mid == null`）。已补 keep 规则，重建后 release 在线无损播放通过。
- 1 小时息屏 soak（release 包）通过：21 次自动切歌，21/21 为 FLAC，0 次 MP3、0 次试听；前台服务存活、AudioTrack 持续活跃、无崩溃；PSS 44.3 MB，温度 25.1°C（USB 供电，电量保持 82%）。
- 真机物理按压未逐颗测试；`gpio_keys` 映射与 `media dispatch` 通路已验证。

### P3 下载与离线（2–3 天）

- 下载队列、暂停/续传、失败重试、空间预检、TF 卡路径、MediaScanner。
- 离线库：已下载列表、播放本地文件、删除；收藏/歌单本地缓存 + 手动刷新。
- **验收**：断网可播放已下载；续传正确；连续下载 100 首无内存增长；重启后任务状态正确。

**P3 状态：完成（2026-10-26，100 首长时间 soak 未做）**

- 已实现：
  - `AppDb`：`downloads` 表 + `kv` 缓存表；`DownloadManager` 单并发队列、eapi 取 URL、Range 续传、失败重试、空间预检、暂停/继续/删除、下载前台服务与通知。
  - 下载落盘：优先 `/storage/external_sd/Music/Hazard`；在 FiiO 固件上 app 对该公共目录 `create` 失败，自动降级到 **SD 专属目录** `/storage/external_sd/Android/data/com.hazard.music/files/Music`（28 GB 可用），完成后 `MediaScanner` 扫描。
  - UI：第 4 个“下载”Tab、歌曲行 `↓/✓` 按钮、下载状态/进度/暂停/继续/删除、全部暂停/继续、存储剩余。
  - 离线播放：`Song.localPath` + `PlayerController` 本地分支（不走网络，质量显示“本地”）。
  - 缓存：每日推荐、账号、用户歌单、喜欢 ID 写入 `kv`；启动先读缓存再刷新；断网时 Mine/推荐仍可用。
- 已真机验证：
  - 7 首 FLAC 下载完成（13–71 MB），总约 354 MB，均落 SD 专属目录。
  - 中途暂停（8.2 MB / 26.7 MB）→ 继续：HTTP **206**、`existing=8678805`，Range 续传完成。
  - 暂停任务 → 杀进程重启 → 仍显示“已暂停” → 继续后完成。
  - 关闭 WiFi（`Wi-Fi is disabled`）后：每日推荐/账号/歌单来自缓存；已下载歌曲 `play local`，AudioTrack 活跃。
  - 下载前台服务/通知、删除、存储剩余显示正常；无崩溃。
- 仅未覆盖：100 首连续下载的长时间内存测试（实测 7 首、约 354 MB）。公共 SD 目录的 SAF 代码已实现，但 M6 DocumentsUI 无法确认目录，当前仍用 SD 专属目录。

### P4 打磨与发布（1–2 天）

- 空态/错误态/VIP 提示；设置（音质、目录、清缓存、退出）；启动优化；崩溃兜底。
- **验收**：冷启动 ≤ 2 s、无 ANR、APK ≤ 1.5 MB、release 签名可安装；附一页使用说明。

**P4 状态：完成（2026-10-26）**

- 已实现：
  - `SettingsActivity`：无损/320k/标准音质切换、下载目录重探测、SAF 目录选择/恢复默认、清列表/封面缓存、退出登录、崩溃记录、关于。
  - `Settings`：音质偏好驱动播放与下载的候选档位。
  - 崩溃兜底：`MusicApp` 写 `files/crash_last.txt`，设置页可查看/清除。
  - 启动优化：PlayerController 懒加载（无播放时不再创建 ExoPlayer/MediaSession）。
  - UI：底部导航 + 顶栏随 Tab 切换；详情页隐藏主栏并显示返回；Mine 改为头像卡片、导航行、带分隔线的歌单列表；mini player 增加封面；适配 FiiO 底部系统导航栏遮挡。
  - 收藏：`user/playlist` 的 `subscribed` 字段分创建/收藏；`/api/album/sublist`（`data` 数组）收藏专辑；`/api/v1/album/{id}` 专辑详情；收藏页与专辑行点击、返回栈、缓存。
  - `README.md`：构建、安装、使用、存储路径、音质限制、已知问题。
- 已真机验证：
  - 音质切到 320k 后请求 `level=exhigh`、播放 320 kbps；切回无损后 `level=lossless`/FLAC。
  - 冷启动：release 稳定 **0.9–1.8 s**（多次实测，≤2 s）；全新安装后首启 5.1 s（dexopt，属系统一次性开销）。
  - release 972 KB，debug keystore 签名安装成功，在线 FLAC 播放/AudioTrack 正常，无崩溃/ANR。
  - 设置页各分区渲染正常。
  - UI 真机：底栏 653–725px 可点击；详情页全屏、返回恢复主栏；收藏/下载/Tab 切换正常；release 冷启动 1291 ms。
  - 收藏页：真机显示收藏歌单（含作者）与 27 张收藏专辑；点击专辑加载曲目，返回仍回收藏页。
- SAF：已实现选择器、持久化权限、临时文件+SAF 拷贝、content:// 播放与删除；M6 自带 DocumentsUI 的“选择”按钮为 0×0（竖屏/横屏/虚拟分辨率复现），无法在 M6 上完成授权，运行时未验证。
- 未做：正式发布 keystore、100 首下载 soak。

合计约 **8–12 个开发日**（含真机调试；不含等待设备/固件的时间）。

## P5 UI v2（2026-10-26，完成）

- 两 Tab：**首页 / 我的**；搜索、歌单/专辑、历史、本地音乐均为全屏子页。
- 首页：搜索入口、推荐歌单横条（`/api/personalized/playlist`）、每日推荐。
- 我的：真实头像/昵称/VIP、播放历史横条、创建歌单（喜欢置顶）、收藏歌单/专辑横条、本地音乐、设置。
- 本地音乐：复用原下载/离线库；下载 Tab 已移除。
- 图标：Material 填充风 VectorDrawable（见 `NOTICE.md`）。
- 播放历史：SQLite `history` 表，`AppDb` v2；`onUpgrade` 只增量建表，不删除 downloads/kv。
- 图片缓存 key 加入目标尺寸，避免 mini/播放页互相复用低清图。
- 云端 `/api/play-record/song/list` 尚未验证合并，先本地优先。
- 真机：首页推荐 12 个歌单；两 Tab、搜索、历史、本地音乐、设置、播放页正常；DB v1→v2 后 7 个下载保留；release 981 KB，冷启动 1.1–1.8 s，无崩溃。

## 6. 风险清单

| 风险 | 概率 | 影响 | 处理 |
|---|---|---|---|
| 网易切 xeapi / 加风控 | 中 | 高 | 协议层隔离；桌面探针回归；必要时切自建网关 |
| 无损需 VIP；plaintext 接口只给 320 kbps | 高 | 中 | 点播 URL 固定走 eapi；按 privilege 预判；UI 标注音质 |
| 系统 MediaPlayer 崩溃（已确认） | 高 | 高 | 已改用 ExoPlayer；禁止任何 MediaPlayer 调用 |
| 平台无 FLAC 解码器（已确认） | 高 | 高 | 内置 libFLAC（`libflacJNI.so` + `LibflacAudioRenderer`） |
| ExoPlayer/Guava/libFLAC 体积与内存 | 中 | 中 | 仅保留 core；R8；禁用未用扩展；PSS 预算 120 MB |
| 后台被 FiiO OS 杀 | 中 | 高 | 已验证 10 分钟息屏不杀；P2 再验证 1 小时 |
| 2 GB 内置存储被写满 | 高 | 中 | 强制默认 microSD，写前检查空间 |
| 账号被风控/封禁 | 低 | 高 | 低请求频率、缓存、只做基础功能；用户自担 |
| TLS 证书在 Android 7 校验失败 | 低 | 高 | 真机验证；不改全局 TrustManager，必要时内置 CA 校验链 |
| 非 bit-perfect 输出 | 高 | 中 | 预期管理：无损≠源码率直出 |

## 7. 不做（v1 范围外）

歌词滚动、评论、云盘、MV、电台、歌单编辑/订阅、桌面歌词、均衡器、多账号、蓝牙编码控制、开机自启、每日推荐自动离线缓存、Hi-Res/母带档位。

## 8. 开工前需要确认的默认项

1. 登录：**QR 为主 + Cookie 粘贴兜底**（不做手机号密码）。
2. 音质：**优先 lossless，回退 320 kbps**，不请求 hires。
3. 下载目录：**自动选 microSD 的 `Music/Hazard/`**，可在设置中改。
4. 账号风险：接受使用非官方接口（个人使用）。
5. 设备已插 microSD 且能把固件升到支持解锁白名单的版本。

无异议则按上述默认值实施。
