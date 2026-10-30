# 空叶悬浮球

一款 Android 悬浮球工具，让常用应用、截图、录屏和快捷操作随手可用。项目采用原生 Android 开发，界面交互参考 FV 悬浮球，项目独立实现，与其无隶属关系。

[下载最新 APK](https://github.com/Mayleaves/KongyeFloat/releases/latest) · [1.19.2 发布页](https://github.com/Mayleaves/KongyeFloat/releases/tag/v1.19.2) · [详细使用说明](docs/USER_GUIDE.md)

## 功能

- **悬浮球与手势**：自定义点击、双击、长按和上下划动作；长按展开环形快捷，快捷位置可配置应用或功能。
- **截图**：整屏、区域截图和长截图；区域截图支持本机中英文文字识别、编辑和复制。长图支持手动或自动滚动拼接、预览、拖动排序和删除。
- **图片编辑**：裁剪、旋转、镜像、文字、画笔、画框、直线、箭头、序号、高亮和马赛克；支持颜色、粗细和双指缩放。
- **录屏与 GIF**：选择录制区域、分辨率和声音来源，可添加水印；录制完成后预览、保存、分享或将视频转换为 GIF。
- **个性化**：导入图片或透明 GIF 作为悬浮球，调整尺寸、透明度和边距；支持深色、浅色、跟随系统和定时切换。
- **文字翻译**：区域截图识别的文字可选择语言翻译，首次使用通过 Wi-Fi 下载语言模型，下载后在设备上翻译。
- **更新与项目主页**：在「设置 → 关于与更新」手动检查 GitHub 最新正式版本，或打开项目主页。检测到更新时在应用内下载 APK、显示进度并校验，然后由系统确认安装。
- **其他工具**：常用应用、最近应用、便签、悬浮浏览器，以及针对特定应用的显示规则。首页工具可自选和拖动排序。

## 安装与权限

1. 从 [Releases](https://github.com/Mayleaves/KongyeFloat/releases/latest) 下载 APK 并安装。应用支持 Android 8.0 及以上；区域截图和长截图需要 Android 11 及以上。
2. 开启「显示在其他应用上层」权限，返回应用后开启悬浮球。
3. 使用导航、截图或自动滚动长截图时，按应用提示开启空叶无障碍服务。侧载安装遇到受限设置时，可在系统应用信息中允许受限设置。
4. 最近打开的应用需要使用记录访问权限。录屏每次需要系统屏幕捕获授权；麦克风录音需要声音权限。

「设置 → 增强悬浮层」可在无障碍服务连接时改善悬浮球的显示范围，无需 Root。Android 或目标应用仍可能限制安全页面上的悬浮窗、截图和录音。

## 构建

需要 Android SDK、JDK 17 或更新版本。当前项目使用 Gradle 9.8.2、Android Gradle Plugin 9.4.1、compile SDK 37、target SDK 36，源码版本为 1.19.2。

在 Android Studio 中打开项目，配置本机 Android SDK；也可以使用 Gradle Wrapper 构建调试版：

```bash
./gradlew assembleDebug
```

Windows 上可运行 `gradlew.bat assembleDebug`。项目里的 `scripts/build.ps1` 还会执行 Lint 和本地签名校验；其中本机 JDK、SDK 工具路径可按环境调整。

发布签名密钥与 `signing.properties` 由构建者自行管理，不包含在仓库内。使用不同签名构建的 APK 不能直接覆盖已安装的官方签名版本。

## 数据与保存

截图识别使用随应用打包的 Google ML Kit 中文模型，在手机上处理；ML Kit SDK 可能发送性能和使用统计，详见 [ML Kit 条款](https://developers.google.com/ml-kit/terms)。便签和配置保存在设备本地。浏览器访问用户打开的网站，网站按其自身政策处理请求和 Cookie。点击检查更新时，应用会请求本项目的 GitHub Releases API。翻译使用 Google ML Kit，首次使用时通过 Wi-Fi 下载语言模型，识别文字在设备上翻译。

区域截图、整屏截图、手动长图和录制先进入预览，点击保存后写入相册；独立的自动滚动长截图通过“完成保存”写入相册。分享使用应用私有的临时文件和系统选择器。使用细节、权限说明与功能限制见[详细使用说明](docs/USER_GUIDE.md)。

## 项目结构

- `app/src/main`：Android 应用、悬浮球、截图、录制和界面源码。
- `app/src/debug`：仅调试版本使用的验收组件。
- `tests`：手势、截图几何、拼接、文字选择等逻辑检查。
- `scripts`：构建和真机验证脚本。
- `docs`：详细使用说明与历史验证记录。
- `prototype`：界面布局、图标及不同视觉方案的纯前端演示。

`prototype` 中的 HTML/CSS/JS 可通过本地 HTTP 服务查看。

当前仓库不包含签名密钥、本机 SDK 配置、构建缓存或设备测试数据。第三方标志说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
