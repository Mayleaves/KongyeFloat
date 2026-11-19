# 3.4.8 本地验证

版本 3.4.8，versionCode 112。

- Debug、五种架构 Release 与 Release Lint 构建通过；Lint 为 0 错误、140 警告。
- 五种 Release 安装包的包名、版本与签名校验通过，签名与 3.4.7 一致。
- 在独占 Android 模拟器 emulator-5590 中核对本地与 WebDAV 页面布局、双层圆角方框加号图标，并保存界面截图。
- 通过原生触摸左右滑动切换两个页签；填写生成的 WebDAV 地址后，切换页签仍保留尚未保存的输入。
- 纵向滚动不改变当前页签，点击页签往返后保留 WebDAV 页面滚动位置；回到顶部后输入仍在。
- 本地导出入口正常打开系统文件选择器，默认文件名包含 KongyeFloat。
- 本次调整界面与翻页交互。本地文件读写、WebDAV 协议、恢复与定时任务的既有验证见 3.4.7 记录。
- 本次未在真实手机上测试；连接设备中没有此前指定的一加手机，未安装到其他手机。

截图：`docs/screenshots/3.4.8/backup-local.png`、`backup-webdav.png`、`backup-webdav-files.png`、`backup-export-picker.png`。
