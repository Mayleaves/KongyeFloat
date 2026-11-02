# v1.30.6 翻译图标替换

日期：2026-11-02。版本：1.30.6，versionCode 56。

使用用户提供的“操作-翻译.svg”原始 pathData，转换为 Android VectorDrawable，原 SVG 保存在 references/v136。通过 drawable tint 使用调用方的主题颜色；翻译和复制继续使用同一个 Ui.action 组件，图标容器均为 18dp，颜色均为 Ui.GREEN。按复制图标的可见边界调整等比缩放，不改变 SVG 的字形和结构。所有使用 translate 图标名的入口统一替换，翻译逻辑保留 1.30.5。

Release 构建、Android Lint 与签名验证通过。正式包覆盖安装到 Ace2（PHK110），包管理器版本核验为 1.30.6 / 56。没有导出手机截图、视频或 GIF，没有更改手机设置。

APK：dist/空叶悬浮球-1.30.6.apk；项目根目录保留同名发送用副本。

SHA-256：`59383d1be01256e40af3eff7804ba43fc3b26aa324aee7a810787ee34f95dfeb`

已包含于 GitHub v1.30.6 正式发布：https://github.com/Mayleaves/KongyeFloat/releases/tag/v1.30.6 。
