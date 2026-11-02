# 本机 OCR 引擎与第三方来源

区域截图保留完整选区内的无障碍原文，同时使用 ML Kit 中文文字识别与 PaddleOCR PP-OCRv5_mobile 本机模型。原文按位置优先；图片文字按检测区域融合。两引擎的置信度并非同一量表，不直接比较分数高低；主要使用 PaddleOCR 的中文结果，保留 ML Kit 独有区域与一致且可靠的拉丁文字排版，低置信度有回退。

PaddleOCR SDK 来源固定于提交 dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf，见 paddle-ocr/UPSTREAM.md，许可证为 Apache-2.0。模型为官方 PP-OCRv5_mobile ONNX，来源、大小、SHA-256 见 paddle-ocr/MODELS.json；模型均打包在 APK 中，不运行下载器，不将选区上传服务器。

推理依赖：ONNX Runtime Android 1.21.1（MIT）、OpenCV 官方 Android 4.12.0（Apache-2.0）。相关许可证已放入 APK assets/licenses，PaddleOCR 源文件的上游版权头保留。

为控制安装包传输大小，原生库压缩打包并由 Android 安装时解压，仍提供各依赖包含的架构。双引擎与模型增加 APK 和安装空间占用；模型会在识别工作线程创建，结束时释放，不常驻占用推理会话。

“中文标点优化”只处理 OCR 文字，原生提取文字保留原样；根据中文邻接调整英文逗号/冒号/问号等，保留小数、千分位、网址及英文语句。该选项可关闭。不会凭空补出未识别到的引号或标点；模型识别依然可能出错。
