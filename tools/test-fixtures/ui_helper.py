#!/usr/bin/env python3
"""UI automation helper for the device verification (DEV-I1..I4).

Usage:
    python ui_helper.py send "message text"   # set input, tap Send, wait, dump transcript
    python ui_helper.py dump                   # dump transcript + status
    python ui_helper.py new                    # tap New Session
    python ui_helper.py pid                    # print app pid
    python ui_helper.py stop                   # tap Stop

Only uses adb shell input / uiautomator. No production changes.
"""
import subprocess, sys, time

ADB = r"C:\android\platform-tools\adb.exe"
PKG = "com.androidagent.app"


def sh(cmd, timeout=30):
    r = subprocess.run([ADB, "shell", cmd], capture_output=True, text=True, timeout=timeout)
    return r.stdout.strip()


def dump(node="transcript"):
    return sh(f"uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml", timeout=40)


def field_text(xml, res_id):
    # res_id like com.androidagent.app:id/input
    import re
    for m in re.finditer(r'<node [^>]*resource-id="([^"]*)"[^>]*text="([^"]*)"[^>]*/>', xml):
        rid, txt = m.group(1), m.group(2)
        if rid.endswith(":" + res_id.split(":")[-1]) or rid == f"{PKG}:id/{res_id}":
            return txt
    # fallback: attribute order may differ
    for m in re.finditer(r'<node [^>]*/>', xml):
        s = m.group(0)
        if f"id/{res_id.split(':')[-1]}" in s:
            tm = re.search(r'text="([^"]*)"', s)
            if tm:
                return tm.group(1)
    return ""


def get_text(res_id):
    xml = dump()
    if not xml:
        return ""
    return field_text(xml, res_id)


def set_text(res_id, text):
    sh(f"input tap 0 0")  # no-op
    # focus the field via uiautomator by tapping its bounds
    xml = dump()
    import re
    for m in re.finditer(r'<node [^>]*/>', xml):
        s = m.group(0)
        if f"id/{res_id.split(':')[-1]}" in s:
            bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
            if bm:
                x1, y1, x2, y2 = map(int, bm.groups())
                cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
                sh(f"input tap {cx} {cy}")
                break
    time.sleep(0.4)
    sh(f"input text {text}")


def tap(res_id):
    xml = dump()
    import re
    for m in re.finditer(r'<node [^>]*/>', xml):
        s = m.group(0)
        if f"id/{res_id.split(':')[-1]}" in s:
            bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
            if bm:
                x1, y1, x2, y2 = map(int, bm.groups())
                sh(f"input tap {(x1+x2)//2} {(y1+y2)//2}")
                return True
    return False


def pid():
    return sh(f"pidof {PKG}")


def send(text, wait=6):
    # tap the input to focus it
    tap("input")
    time.sleep(0.4)
    # clear any existing text: select-all then delete (a single, fast command)
    sh("input keyevent KEYCODE_MOVE_END")
    for _ in range(30):
        sh("input keyevent KEYCODE_DEL", timeout=10)
    time.sleep(0.3)
    sh(f"input text {text}", timeout=20)
    time.sleep(0.4)
    tap("send")
    time.sleep(wait)
    return get_text("transcript")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 0
    cmd = sys.argv[1]
    if cmd == "send":
        out = send(sys.argv[2])
        print(out)
    elif cmd == "dump":
        print(get_text("transcript"))
    elif cmd == "status":
        print(get_text("status"))
    elif cmd == "new":
        ok = tap("new_session")
        time.sleep(3)
        print("new tapped:", ok)
    elif cmd == "pid":
        print(pid())
    elif cmd == "stop":
        print("stop tapped:", tap("stop"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
