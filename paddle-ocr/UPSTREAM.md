PaddleOCR Android SDK

Source: https://github.com/PaddlePaddle/PaddleOCR/tree/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf/deploy/ppocr-android/ppocr-sdk

Apache-2.0, see LICENSE. Gradle configuration adapted for the application. Native libraries are compressed for APK distribution. Models: official PP-OCRv5 mobile ONNX export.

Local adaptations: Gradle uses AGP 9 built-in Kotlin and OpenCV's official 4.12.0 artifact (the upstream sample's older QuickBird artifact did not load on the test device). Kongye's synchronous worker-thread bridge sets mobile-v5 detection limits/thresholds. OCREngine accepts a cancellation predicate checked between stages/batches; upstream notices and other source are retained.
