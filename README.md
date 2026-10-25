# HazardMusicPlayer

面向 FiiO M6 的轻量第三方网易云音乐客户端。纯 Java + framework View，播放使用 ExoPlayer 2.18.7 + 内置 libFLAC。

## 功能

- 扫码登录（新版桌面端接口）+ Cookie 登录兜底
- 两页：首页（每日推荐、推荐歌单、搜索）/ 我的（资料、播放历史、创建/收藏歌单、收藏专辑、本地音乐、设置）
- 每日推荐、云搜索、推荐歌单、创建/收藏歌单、收藏专辑、歌单/专辑详情、喜欢列表/红心
- 本地播放历史（SQLite，重启保留，可清空）
- 在线播放：优先无损 FLAC，自动回退 320 kbps/标准
- 底部导航（首页/我的）+ Material 填充图标；详情页全屏返回栈
- mini player（封面/标题/播放/下一首）、播放页、进度/seek、通知控制、媒体键
- 下载：队列、暂停/继续、Range 续传、失败重试、空间预检、前台服务
- 离线库：本地 FLAC/MP3 播放、删除、断网可用
- 缓存：每日推荐、推荐歌单、账号、歌单、收藏专辑、喜欢 ID，启动先读缓存再刷新
- 设置：音质、下载目录探测、清播放历史/缓存、退出登录、崩溃记录

## 设备前置

- FiiO M6，Android 7.0 / API 24，armeabi-v7a
- 解锁安装白名单：设置 → 通用 → 关于设备 → 连点“内核版本”7 次 → 重启
- 开启 USB 调试；首次连接允许 RSA 指纹
- 建议插 microSD

## 构建

工具链见 `ENVIRONMENT.md`：JDK 21、Gradle 8.9、AGP 8.7.3、SDK 34、NDK 21.4.7075529。

```powershell
# debug
.\gradlew.bat :app:assembleDebug

# release（当前 proguard 已包含 libflacJNI 所需的 keep 规则）
.\gradlew.bat :app:assembleRelease
```

release 产物默认未签名。仓库内验证使用的是 `~/.android/debug.keystore`：

```powershell
$bt = "D:\Devtools\android-sdk\build-tools\34.0.0"
& "$bt\zipalign.exe" -f 4 app\build\outputs\apk\release\app-release-unsigned.apk app\build\outputs\apk\release\app-release-aligned.apk
& "$bt\apksigner.bat" sign --ks "$env:USERPROFILE\.android\debug.keystore" `
  --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey `
  --out app\build\outputs\apk\release\app-release-signed.apk `
  app\build\outputs\apk\release\app-release-aligned.apk
```

正式发布请生成自己的 keystore 并替换签名参数；debug 证书只适合个人测试，且换证书会导致应用数据不兼容。

## 安装

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
# 或 release
adb install -r app\build\outputs\apk\release\app-release-signed.apk
```

## 使用

1. “我的” → “登录”，用网易云 App 扫码；若返回 8821/-462，改用“Cookie 登录”粘贴 `MUSIC_U`。
2. 底部“首页/我的”切换；首页有搜索入口、推荐歌单和每日推荐；“我的”有播放历史、创建/收藏歌单、收藏专辑。
3. 点歌曲行播放；底部 mini player 进入播放页。
4. 歌曲行右侧下载图标加入下载；“我的 → 本地音乐”查看进度、暂停/继续、删除。
5. 下载完成的歌曲在“本地音乐”页点击即本地播放，断网可用。

## 存储与文件

- 默认下载根目录：
  `/storage/external_sd/Android/data/com.hazard.music/files/Music`
- 设置 → “选择 SAF 公共目录”可改用系统文件选择器授权的目录；标准 Android 上可用。下载先写到 SD 临时区，完成后拷贝到所选目录，播放使用 `content://` URI。
- **M6 固件限制**：自带 DocumentsUI 把“选择”按钮布局成 0×0（竖屏/横屏/虚拟分辨率均复现），无法确认 SAF 目录。M6 上请使用默认 SD 专属目录。
- 完成后会触发 `MediaScanner`。FiiO Music 是否能扫描 `Android/data` 取决于固件；本应用自身可离线播放。
- 内置存储仅剩约 1 GB，请勿把下载目录改到内置存储。

## 音质与音频限制

- 无损需要黑胶 VIP；非会员会被服务端降级为 320 kbps。
- 平台没有 FLAC 解码器，客户端内置 libFLAC；24-bit FLAC 会输出为 16-bit PCM。
- 不支持 Hi-Res/母带档位；第三方应用走系统混音器，不是 bit-perfect。
- 系统 `MediaPlayer` 在该固件会令 `mediaserver` 崩溃，已完全改用 ExoPlayer。

## 已知限制

- Release 的 R8 必须保留 `FlacStreamMetadata` 与 `metadata.flac.PictureFrame`，否则 libflacJNI 会 `JNI DETECTED ERROR: mid == null`。规则已写入 `app/proguard-rules.pro`。
- 100 首连续下载的长时间 soak 未做；实测 7 首约 354 MB。
- 公共 SD 目录需要 SAF；代码已实现（含临时文件+SAF 拷贝、content:// 播放），但 M6 自带 DocumentsUI 无法确认目录（“选择”按钮 0×0），实际仍用 SD 专属目录。
- SAF 路径未在 M6 上完成运行时验证；默认 SD 路径已充分验证。
- 非官方接口，可能因网易风控/协议变更失效；账号风险自担。
- 无歌词、评论、歌单编辑、Hi-Res。

## 目录

```
app/src/main/java/com/hazard/music/
  api/        weapi/eapi、CookieJar、NetEaseClient
  crypto/     （Crypto 位于 api/）
  model/      Song / SongUrl / Playlist / Account / DownloadItem
  player/     PlayerController / PlaybackService
  download/   DownloadManager / DownloadService
  store/      AppDb（SQLite）
  ui/         MainActivity / PlayerActivity / LoginActivity / SettingsActivity
  util/       Settings / ImageLoader
```

## 免责声明

仅供个人学习与自用。接口来自非官方网易云 API，可能随时变化；请遵守网易云音乐用户协议，不要用于商业分发。

## 图标

界面图标来自 Google Material Icons（Apache 2.0），明细见 `NOTICE.md`。
