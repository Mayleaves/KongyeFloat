# Third-party notices

## PaddleOCR, ONNX Runtime and OpenCV

The local OCR integration includes the PaddleOCR Android SDK (Apache-2.0), pinned to commit `dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf`, and the official PP-OCRv5_mobile detection/recognition ONNX models. Source, model URLs, checksums and local adaptations are recorded in `paddle-ocr/UPSTREAM.md`, `paddle-ocr/MODELS.json` and `docs/OCR-ENGINES.md`. Upstream copyright headers and `paddle-ocr/LICENSE` are retained.

Inference dependencies include ONNX Runtime Android 1.21.1 (MIT) and official OpenCV Android 4.12.0 (Apache-2.0). Complete upstream license texts are packaged in the APK at `assets/licenses/ONNXRuntime-LICENSE`, `assets/licenses/OpenCV-LICENSE` and `assets/licenses/PaddleOCR-LICENSE`.

## GitHub mark / Octicons

The GitHub mark in `app/src/main/res/drawable/ic_github_mark.xml` uses the Octicons silhouette for the project link. Source: https://github.com/primer/octicons. GitHub trademarks belong to their respective owner.

MIT License

Copyright (c) 2026 GitHub Inc.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

## Lucide sun-moon and sliders-horizontal

The theme-system and home tool-management symbols use Lucide's original SVG paths, converted to Android vectors with theme tinting. Sources: https://github.com/lucide-icons/lucide/blob/main/icons/sun-moon.svg and https://github.com/lucide-icons/lucide/blob/main/icons/sliders-horizontal.svg.

Original SVG files are included in `app/src/main/assets/icons/`. The complete upstream license is included in `app/src/main/assets/licenses/lucide-LICENSE` and packaged in the APK.
