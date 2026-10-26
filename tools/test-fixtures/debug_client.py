#!/usr/bin/env python3
"""Debug Bridge client for device verification (DEV-I1..I4).

Connects to the on-device AG.DEBUG-0 bridge (127.0.0.1:45678 via `adb forward`)
and sends messages through the SAME AgentRuntime.send path the UI uses.

Usage:
    python debug_client.py send "message"   # send via bridge, wait, dump transcript
    python debug_client.py ping
    python debug_client.py dump             # dump transcript via uiautomator

The bridge speaks JSONL. A send returns "accepted" then streams events; the
final "run_finished" marks the end. We then read the transcript from the UI.
"""
import json
import socket
import subprocess
import sys
import time

ADB = r"C:\android\platform-tools\adb.exe"
PKG = "com.androidagent.app"
HOST, PORT = "127.0.0.1", 45678


def sh(cmd, timeout=30):
    r = subprocess.run([ADB, "shell", cmd], capture_output=True, text=True, timeout=timeout)
    return r.stdout.strip()


def dump_transcript():
    xml = sh("uiautomator dump /sdcard/ui.xml && cat /sdcard/ui.xml", timeout=40)
    import re
    for m in re.finditer(r'<node [^>]*/>', xml):
        s = m.group(0)
        if "id/transcript" in s:
            tm = re.search(r'text="([^"]*)"', s)
            if tm:
                return tm.group(1)
    return ""


def decode(s):
    return s.replace("&#10;", "\n").replace("&#13;", "\r").replace("&quot;", '"')


def connect(timeout=5):
    s = socket.create_connection((HOST, PORT), timeout=timeout)
    s.settimeout(timeout)
    return s


def send_line(sock, obj):
    sock.sendall((json.dumps(obj) + "\n").encode("utf-8"))


def recv_line(sock):
    """Read one JSONL line (the bridge may interleave; we read until newline)."""
    buf = b""
    while True:
        b = sock.recv(1)
        if not b:
            break
        buf += b
        if buf.endswith(b"\n"):
            break
    return buf.decode("utf-8", "replace").strip()


def do_send(text, wait=8):
    sock = connect()
    # Read the hello line the bridge sends on connect.
    hello = recv_line(sock)
    sys.stderr.write("HELLO: %s\n" % hello)
    send_line(sock, {"type": "send", "text": text})
    # Consume stream until run_finished (or timeout).
    deadline = time.time() + wait
    outcome = None
    while time.time() < deadline:
        line = recv_line(sock)
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            continue
        t = msg.get("type")
        sys.stderr.write("EVENT: %s\n" % line[:200])
        if t == "accepted":
            pass
        elif t == "busy":
            sys.stderr.write("BRIDGE BUSY\n")
            sock.close()
            return "(busy)"
        elif t == "run_finished":
            outcome = msg.get("outcome")
            break
        elif t == "run_failed":
            outcome = "failed:" + str(msg.get("failure"))
            break
    sock.close()
    time.sleep(1)
    return outcome, dump_transcript()


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 0
    cmd = sys.argv[1]
    if cmd == "ping":
        sock = connect()
        line = recv_line(sock)
        send_line(sock, {"type": "ping"})
        pong = recv_line(sock)
        sock.close()
        print("hello=%s pong=%s" % (line, pong))
    elif cmd == "send":
        outcome, transcript = do_send(sys.argv[2])
        print("OUTCOME: %s" % outcome)
        print("---TRANSCRIPT---")
        print(decode(transcript))
    elif cmd == "dump":
        print(decode(dump_transcript()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
