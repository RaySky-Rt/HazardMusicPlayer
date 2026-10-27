# Hazard Music Player

面向低版本、低配置 Android 音乐播放器的轻量第三方网易云音乐客户端。

## 功能

### 首页

- 每日推荐歌曲
- 个性化推荐歌单
- 推荐歌单横向浏览
- 歌曲与歌单搜索

### 歌单与歌曲列表

- 同步“我喜欢的音乐”、创建的歌单、收藏的歌单、收藏的专辑
- 支持收藏/取消收藏歌曲

### 播放

- 支持在线无损播放与本地离线播放
- 支持列表循环、单曲循环、随机播放
- 支持上一首、下一首和播放队列查看
- 支持定时停止播放
- 支持最高 96kHz 24bit HiRes 音质在线播放/本地下载

### 其他

- 支持下载管理与断点续传
- 支持本地播放历史

## 最低运行配置

- Android 6.0（API 23）及以上
- armeabi-v7a 设备
- 建议至少 50MB 可用存储空间
- 建议至少 30MB 可用运行内存

## 构建

项目使用 Gradle Wrapper，需要 JDK 11 和 Android SDK 34。

```bash
./gradlew :app:assembleDebug
```

Release 构建：

```bash
./gradlew :app:assembleRelease
```

配置：

- `compileSdk 34`
- `minSdk 23`
- `targetSdk 28`
- `abiFilters armeabi-v7a`

### Release 签名

项目根目录的 `keystore.properties` 保存本地正式签名配置，文件已被 `.gitignore` 忽略，请勿提交。

```properties
storeFile=C:/path/to/release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

`keystore.properties` 和 keystore 文件需要自行备份；丢失后无法继续用同一签名发布更新。`app/build.gradle` 会在文件存在时自动为 release 构建签名，缺失时生成未签名包。

## 开源引用

本项目使用或内嵌了以下开源项目与库：

| 项目 / 库 | 用途 | 许可证 |
| --- | --- | --- |
| [ExoPlayer](https://github.com/google/ExoPlayer) | 音频播放引擎 | Apache License 2.0 |
| [ExoPlayer FLAC Extension](https://github.com/google/ExoPlayer/tree/release-v2/extensions/flac) | FLAC 解码扩展 | Apache License 2.0 |
| [libFLAC](https://xiph.org/flac/) | FLAC 原生解码 | BSD 3-Clause |
| [ZXing](https://github.com/zxing/zxing) | 二维码生成/扫码登录 | Apache License 2.0 |
| [AndroidX Annotation](https://developer.android.com/jetpack/androidx/releases/annotation) | Android 注解支持 | Apache License 2.0 |
| [Google Material Icons](https://github.com/google/material-design-icons) | 界面图标 |  Apache License 2.0 |
| [网易云音乐 Node.js API Services (Half Refactor & Enhanced) ](https://github.com/google/material-design-icons) | 网易云api | MIT |


## 免责声明

仅供个人学习使用。接口来自非官方网易云 API，禁止用于商业分发。
