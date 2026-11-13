"""Only inspect this app's home page; never capture screenshots or other apps' content."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r"D:\Program\Android\Sdk\platform-tools\adb.exe"
serial = sys.argv[1]


def adb(*args):
    return subprocess.run([ADB, "-s", serial, *args], check=True, capture_output=True).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/kongye-save-home.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/data/local/tmp/kongye-save-home.xml"))
    return [n for n in root.iter("node") if n.get("package") == "com.kongye.floatball"]


def tap(node):
    a, b, c, d = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((a + c) // 2), str((b + d) // 2))


adb("shell", "am", "start", "-W", "-n", "com.kongye.floatball/.MainActivity")
time.sleep(2)
home = next(n for n in nodes() if n.get("content-desc") == "首页")
tap(home)
time.sleep(0.5)
page = nodes()
descriptions = [n.get("content-desc", "") for n in page]
assert any(t.startswith("悬浮窗，") for t in descriptions), "missing overlay status"
assert any(t.startswith("无障碍，") for t in descriptions), "missing accessibility status"
assert not any(t.startswith("通知，") for t in descriptions), "notification permission still in home"
if "启动悬浮球" in descriptions:
    tap(next(n for n in page if n.get("content-desc") == "启动悬浮球"))
    time.sleep(1)
assert any(n.get("content-desc") == "关闭悬浮球" for n in nodes()), "ball failed to start without notification permission"
print("PASS: two permission entries, no notification request; floating ball running")
