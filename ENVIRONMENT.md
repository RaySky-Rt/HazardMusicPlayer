# 开发环境清单与清理指南

> 安装日期：2026-10-25
> 主机：Windows 10 19045 x64
> 目的：FiiO M6 第三方网易云客户端（Java / AGP 8.7.3 / Gradle 8.9 / ExoPlayer 2.18.7）
> 安装位置统一在 `D:\Devtools`，不写系统目录；无需管理员权限。

## 1. 本次新增内容（共约 4.2 GB）

| 组件 | 版本 | 路径 | 占用 | 用途 |
|---|---|---|---|---|
| Android SDK cmdline-tools | 19.0（build 13114758） | `D:\Devtools\android-sdk\cmdline-tools\latest` | 158 MB | `sdkmanager` / `apksigner` / `d8` 等 |
| platform-tools | 37.0.1 | `D:\Devtools\android-sdk\platform-tools` | 17 MB | adb / fastboot |
| platforms;android-34 | rev 3 | `D:\Devtools\android-sdk\platforms\android-34` | 95 MB | `compileSdk 34` |
| build-tools;34.0.0 | 34.0.0 | `D:\Devtools\android-sdk\build-tools\34.0.0` | 137 MB | aapt2 / d8 / zipalign / apksigner |
| **NDK** | **21.4.7075529** | `D:\Devtools\android-sdk\ndk\21.4.7075529` | **3.6 GB** | 编译 `libflacJNI.so` |
| SDK licenses | — | `D:\Devtools\android-sdk\licenses` | <1 MB | 许可记录 |
| Gradle | 8.9 | `D:\Devtools\gradle-8.9` | 145 MB | 构建 |
| ExoPlayer FLAC 产物 | r2.18.7 + libFLAC 1.3.2 | `D:\Devtools\exoplayer-flac-2.18.7` | 316 KB | P1 直接接入；含 Java 源码、`libflacJNI.so`、许可证 |
| adb 主机密钥缓存 | — | `C:\Users\RaySky\.android\cache` | <1 MB | adb 自动生成 |

安装包临时文件（267 MB）、libFLAC 构建目录（187 MB，`%TEMP%\flacbuild`）和播放探针工程（`%TEMP%\probeapp`）已在 P0 验证后删除。

## 2. 环境变量变更

只改了 **当前用户** 的环境变量，未改系统变量：

| 变量 | 值 |
|---|---|
| `ANDROID_HOME` | `D:\Devtools\android-sdk` |
| `ANDROID_SDK_ROOT` | `D:\Devtools\android-sdk` |
| `Path`（用户级，前 3 项） | `D:\Devtools\android-sdk\platform-tools`<br>`D:\Devtools\android-sdk\cmdline-tools\latest\bin`<br>`D:\Devtools\gradle-8.9\bin` |

`JAVA_HOME` 未改动，仍为 `C:\Users\RaySky\AppData\Local\Programs\Microsoft\jdk-21.0.8.9-hotspot`。

注意：已经打开的终端不会自动获得新变量，需要新开终端（或注销/重登）。

## 3. 没有安装 / 没有改动

- 未安装 Android Studio、Android Emulator、任何系统镜像、CMake。NDK 已安装（用于 libFLAC）。
- 未安装 Google USB Driver（设备已能正常识别 ADB 接口，不需要）。
- 未安装 Node.js / Kotlin / Python 包，未改动系统级 PATH。
- 未安装任何服务、驱动、开机启动项。
- 未修改 `D:\Devtools\adb`（原有 adb，仍在磁盘上，只是被 SDK 的 platform-tools 在 PATH 中优先）。

## 4. 真机实测信息（FiiO M6）

| 项 | 值 |
|---|---|
| 序列号 | `000006b5767627d4` |
| 型号 / 设备 | `FiiO M6` / `smdk7270` |
| Android | **7.0（API 24）**，不是 6.0 |
| 构建号 | `eng.FiiO.20200519.165027` |
| ABI | `armeabi-v7a`（32 位，无 64 位） |
| RAM | `MemTotal 712072 kB`（标称 768 MB） |
| 屏幕 | 480×800，密度 240 dpi → **320×533 dp** |
| 内置存储 | 2.0 GB，剩余 1.0 GB |
| microSD | 58 GB，剩余 28 GB，挂载于 `/storage/external_sd`，vfat |
| 时区 | Asia/Shanghai |

对方案的直接影响：

1. `minSdk 23` 仍满足"适配 Android 6"，但真机是 API 24，按 24 调试即可。
2. 32 位设备：不能引入 arm64-only 的 native 库；本方案纯 Java，无需 `abiFilters`。
3. 320 dp 宽度比预想宽，原计划按 320 dp 布局可直接用。
4. 内置仅剩 1 GB：下载默认必须落 `/storage/external_sd`。
5. SD 是 FAT32：单文件上限 4 GB，无 Unix 权限/符号链接；文件名用 UTF-8 安全字符。
6. Android 7.0：需要运行时权限（存储）；平台 FLAC 解码仍按 44.1 kHz/16 bit 使用，无损档位安全。

## 5. 验收结果（已执行）

| 检查 | 结果 |
|---|---|
| `sdkmanager --version` | 19.0 |
| `sdkmanager --list_installed` | platform-tools 37.0.1 / platforms;android-34 / build-tools;34.0.0 |
| `gradle -v` | Gradle 8.9，Launcher JVM 21.0.8 |
| `adb version` | 1.0.41（37.0.1-15733141） |
| `adb devices -l` | `000006b5767627d4 device product:m6 model:FiiO_M6` |
| `adb shell` 读写内置存储 | 通过 |
| `adb shell` 读写 SD 卡 | 通过（`/storage/external_sd`） |

## 6. 日常清理（保守，保留环境）

随时可执行，不影响下次构建：

```powershell
adb kill-server
# 项目构建产物
Remove-Item -Recurse -Force "D:\Documents\Code\Github\HazardMusicPlayer\app\build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "D:\Documents\Code\Github\HazardMusicPlayer\.gradle" -ErrorAction SilentlyContinue
# Gradle 全局缓存（下次构建会重新下载，可能较慢）
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\daemon" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$env:USERPROFILE\.android\cache" -ErrorAction SilentlyContinue
# libFLAC 构建临时目录（需要时可重建）
Remove-Item -Recurse -Force "$env:TEMP\flacbuild" -ErrorAction SilentlyContinue
# 播放探针临时工程
Remove-Item -Recurse -Force "$env:TEMP\probeapp" -ErrorAction SilentlyContinue
```

不建议随手删除 `%USERPROFILE%\.gradle\wrapper\dists`，除非确认没有其他 Gradle 项目；删除后所有项目首次构建都会重新下载 Gradle。

## 7. 完全卸载

### 7.1 先移除 PATH 与变量

在 PowerShell 中执行（只删精确匹配项，不动其他 PATH）：

```powershell
$sdk    = 'D:\Devtools\android-sdk'
$gradle = 'D:\Devtools\gradle-8.9'

# 移除用户 PATH 中的 3 个条目
$p = [Environment]::GetEnvironmentVariable('Path','User')
$drop = @("$sdk\platform-tools", "$sdk\cmdline-tools\latest\bin", "$gradle\bin")
$p = (($p -split ';') | Where-Object { $_ -and ($drop -notcontains $_) }) -join ';'
[Environment]::SetEnvironmentVariable('Path', $p, 'User')

# 移除 SDK 变量
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $null, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $null, 'User')
```

### 7.2 删除目录

```powershell
adb kill-server
# 含 NDK 3.6 GB
Remove-Item -Recurse -Force 'D:\Devtools\android-sdk'
Remove-Item -Recurse -Force 'D:\Devtools\gradle-8.9'
Remove-Item -Recurse -Force 'D:\Devtools\exoplayer-flac-2.18.7'
```

不要在 `D:\Devtools` 上执行通配删除；该目录下还有你自己的 `adb`、`scrcpy`、`iperf` 等工具。

### 7.3 可选：删除用户级缓存

```powershell
# adb 主机密钥（下次连接会重新生成并再次弹授权框）
Remove-Item -Recurse -Force "$env:USERPROFILE\.android"

# Gradle 缓存：仅当你没有其他 Gradle 项目时删除
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle"
```

`%USERPROFILE%\.gradle` 在本机安装前就存在（含 gradle 7.5.1），删除会影响其他项目，按需处理。

### 7.4 设备侧（可选）

- 设置 → 通用 → 开发者选项 → **撤销 USB 调试授权**。
- 已解锁的安装白名单不会因为 PC 清理而恢复；它属于设备设置，与本次环境无关。

### 7.5 卸载后验证

新开一个 PowerShell：

```powershell
Test-Path 'D:\Devtools\android-sdk'          # False
Test-Path 'D:\Devtools\gradle-8.9'           # False
[Environment]::GetEnvironmentVariable('ANDROID_HOME','User')      # 空
[Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT','User')  # 空
Get-Command sdkmanager -ErrorAction SilentlyContinue              # 无输出
Get-Command gradle     -ErrorAction SilentlyContinue              # 无输出
```

确认 PATH 里不再有：

```powershell
[Environment]::GetEnvironmentVariable('Path','User') -split ';' |
    Select-String 'android-sdk|gradle-8.9'
```

应无输出。

## 8. 空间账目

| 项 | 大小 |
|---|---|
| SDK（不含 NDK） | 405 MB |
| NDK 21.4.7075529 | 3.6 GB |
| Gradle 8.9 | 145 MB |
| ExoPlayer FLAC 产物 | 316 KB |
| 合计新增（保留） | **约 4.2 GB**（SDK 405 MB + NDK 3.6 GB + Gradle 145 MB + 316 KB） |
| 已删除的安装包临时文件 | 267 MB |
| 已删除的 libFLAC 构建目录 / 探针工程 | 187 MB + 探针工程 |
| 后续构建缓存（预计） | 200–500 MB，位于 `%USERPROFILE%\.gradle` |

## 9. 复原（重新安装）

```powershell
# 1) SDK
$root = 'D:\Devtools\android-sdk'
New-Item -ItemType Directory -Force "$root\cmdline-tools" | Out-Null
$zip = "$env:TEMP\clt.zip"
Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip' -OutFile $zip
New-Item -ItemType Directory -Force "$env:TEMP\clt" | Out-Null
tar -xf $zip -C "$env:TEMP\clt"
Move-Item "$env:TEMP\clt\cmdline-tools" "$root\cmdline-tools\latest"

# 2) 包与许可
& "$root\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$root --licenses
& "$root\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$root --install "platform-tools" "platforms;android-34" "build-tools;34.0.0" "ndk;21.4.7075529"

# 3) Gradle
Invoke-WebRequest 'https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip' -OutFile "$env:TEMP\gradle.zip"
tar -xf "$env:TEMP\gradle.zip" -C 'D:\Devtools'

# 4) 变量（同 §2）
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $root, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $root, 'User')

$gradle = 'D:\Devtools\gradle-8.9'
$p = [Environment]::GetEnvironmentVariable('Path','User')
$want = @("$root\platform-tools", "$root\cmdline-tools\latest\bin", "$gradle\bin")
$missing = @($want | Where-Object { $p -notlike "*$_*" })
if ($missing.Count -gt 0) {
    [Environment]::SetEnvironmentVariable('Path', (($missing -join ';') + ';' + $p), 'User')
}
```

许可步骤是交互式的，需要按提示输入 `y`。

## 10. 重新生成 `libflacJNI.so`

平台没有 FLAC 解码器，客户端需要 ExoPlayer 的 FLAC 扩展与 libFLAC。产物为 `armeabi-v7a/libflacJNI.so`（约 278 KB）：

```powershell
$ndk = 'D:\Devtools\android-sdk\ndk\21.4.7075529'
$work = "$env:TEMP\flacbuild"
New-Item -ItemType Directory -Force $work | Out-Null
Set-Location $work

Invoke-WebRequest 'https://codeload.github.com/google/ExoPlayer/tar.gz/refs/tags/r2.18.7' -OutFile exo.tar.gz
Invoke-WebRequest 'https://codeload.github.com/xiph/flac/tar.gz/refs/tags/1.3.2' -OutFile flac.tar.gz
tar -xzf exo.tar.gz
tar -xzf flac.tar.gz

$jni = "$work\ExoPlayer-r2.18.7\extensions\flac\src\main\jni"
Copy-Item -Recurse "$work\flac-1.3.2" "$jni\flac"
Set-Location $jni
& "$ndk\ndk-build.cmd" APP_ABI=armeabi-v7a -j4
```

输出在 `ExoPlayer-r2.18.7\extensions\flac\src\main\libs\armeabi-v7a\libflacJNI.so`。  
Java 侧使用同目录 `src/main/java/com/google/android/exoplayer2/ext/flac/` 下的 `FlacDecoderJni`、`LibflacAudioRenderer` 等；许可证为 ExoPlayer Apache-2.0 + libFLAC（Xiph BSD）。
