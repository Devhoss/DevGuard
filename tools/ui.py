"""Device-test helper for the DevGuard project (not part of the app).

Usage:
  python tools/ui.py dump             # list visible nodes with screen-centre coordinates
  python tools/ui.py tap "<label>"    # tap the first node whose text or description matches
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEVICE = "RZCTB0HMQ5L"
LOCAL_XML = "E:/dev/devguard/tools/ui.xml"


def adb(*args):
    return subprocess.run(["adb", "-s", DEVICE, *args], capture_output=True, text=True)


def dump_tree():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", LOCAL_XML)
    return ET.parse(LOCAL_XML)


def centre(bounds):
    numbers = re.findall(r"\d+", bounds or "")
    if len(numbers) != 4:
        return None
    left, top, right, bottom = (int(v) for v in numbers)
    return ((left + right) // 2, (top + bottom) // 2)


def describe(node):
    text = (node.get("text") or "").strip()
    desc = (node.get("content-desc") or "").strip()
    return text or desc


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else "dump"
    tree = dump_tree()
    if command == "dump":
        for node in tree.iter("node"):
            label = describe(node)
            point = centre(node.get("bounds"))
            if not label or point is None:
                continue
            print(f"{label[:44]!r:46} {node.get('class', '').split('.')[-1]:12}"
                  f" centre={point} clickable={node.get('clickable')}"
                  f" checked={node.get('checked')}")
        return
    if command == "tap":
        needle = sys.argv[2].lower()
        for node in tree.iter("node"):
            if needle in describe(node).lower():
                point = centre(node.get("bounds"))
                if point:
                    adb("shell", "input", "tap", str(point[0]), str(point[1]))
                    print(f"tapped {describe(node)!r} at {point}")
                    return
        print(f"not found: {needle}")
        sys.exit(1)


if __name__ == "__main__":
    main()
