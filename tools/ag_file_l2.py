#!/usr/bin/env python3
"""AG.FILE-0 L2 — real-device direct tool validation.

Prerequisite (run first):
    adb shell am instrument -w com.androidagent.app/com.androidagent.app.FileL2FixtureTest#create_fixtures

This script connects to the DebugBridge and runs F1-F16 file tool tests via
tool_invoke. All test data is in filesDir/agent/.l2test/ (created by the
androidTest fixture, NOT by this script). Cleanup is also done via androidTest.

Zero LLM, zero model tokens.
"""

import json
import socket
import sys
import time

HOST = "127.0.0.1"
PORT = 45678

# ------------------------------------------------------------------ helpers

def recv_line(sock):
    buf = bytearray()
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            return None
        buf += chunk
        idx = buf.find(b"\n")
        if idx >= 0:
            line = bytes(buf[:idx]).decode("utf-8", errors="replace")
            del buf[:idx + 1]
            return line

def send_json(sock, obj):
    sock.sendall((json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8"))

def read_until_type(sock, want_type, timeout=15.0):
    sock.settimeout(timeout)
    deadline = time.time() + timeout
    while time.time() < deadline:
        line = recv_line(sock)
        if line is None:
            break
        try:
            obj = json.loads(line)
        except ValueError:
            continue
        if obj.get("type") == want_type:
            return obj
    return None

# ------------------------------------------------------------------ checkers

def check_f1(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = (out.get("action") == "read" and out.get("content") == "hello\nworld"
          and out.get("totalLines") == 2 and not out.get("isBinary")
          and not out.get("truncated"))
    return ok, "content=%r totalLines=%s" % (out.get("content"), out.get("totalLines"))

def check_f2(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("content") == "" and out.get("totalBytes") == 0
    return ok, "content=%r totalBytes=%s" % (out.get("content"), out.get("totalBytes"))

def check_f3(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("type") == "file" and out.get("sizeBytes") == 12
    return ok, "type=%s sizeBytes=%s" % (out.get("type"), out.get("sizeBytes"))

def check_f4(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    names = [e["name"] for e in out.get("entries", [])]
    ok = "hello.txt" in names and "sub" in names and "empty.txt" in names
    return ok, "entries=%s" % names

def check_f5(r):
    if r.get("success"):
        return False, "expected success=false"
    ok = "PATH_OUTSIDE_ROOT" in (r.get("error") or "")
    return ok, "error=%s" % r.get("error")

def check_f6(r):
    if r.get("success"):
        return False, "expected success=false"
    ok = "PATH_OUTSIDE_ROOT" in (r.get("error") or "")
    return ok, "error=%s" % r.get("error")

def check_f7(r):
    if r.get("success"):
        return False, "expected success=false"
    ok = "NOT_FOUND" in (r.get("error") or "")
    return ok, "error=%s" % r.get("error")

def check_f8(r):
    if r.get("success"):
        return False, "expected success=false"
    ok = "NOT_A_FILE" in (r.get("error") or "")
    return ok, "error=%s" % r.get("error")

def check_f9(r):
    if r.get("success"):
        return False, "expected success=false"
    ok = "NOT_A_DIRECTORY" in (r.get("error") or "")
    return ok, "error=%s" % r.get("error")

def check_f10(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("content") == "hi"
    return ok, "content=%r" % out.get("content")

def check_f11(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("isBinary") is True and out.get("content") == ""
    return ok, "isBinary=%s content=%r" % (out.get("isBinary"), out.get("content"))

def check_f12(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("truncated") is True and out.get("bytesRead") == 100
    return ok, "truncated=%s bytesRead=%s totalBytes=%s" % (
        out.get("truncated"), out.get("bytesRead"), out.get("totalBytes"))

def check_f13(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    m = out.get("matches", [])
    ok = out.get("totalMatches") == 1 and len(m) == 1 and m[0].get("line") == 3
    return ok, "totalMatches=%s line=%s" % (out.get("totalMatches"), m[0].get("line") if m else None)

def check_f14(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    m = out.get("matches", [])
    ok = out.get("totalMatches") == 1 and len(m) == 1 and m[0].get("path") == ".l2test/sub/a.txt"
    return ok, "totalMatches=%s path=%s" % (out.get("totalMatches"), m[0].get("path") if m else None)

def check_f15(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    ok = out.get("content") == "l2\nl3" and out.get("totalLines") == 5
    return ok, "content=%r totalLines=%s" % (out.get("content"), out.get("totalLines"))

def check_f16(r):
    if not r.get("success"):
        return False, "success=false: %s" % r.get("error")
    out = json.loads(r["output"])
    m = out.get("matches", [])
    ok = len(m) == 3 and out.get("truncated") is True
    return ok, "matches=%s truncated=%s totalMatches=%s" % (len(m), out.get("truncated"), out.get("totalMatches"))

# ------------------------------------------------------------------ test cases

TESTS = [
    ("F1",  "read hello.txt", "file",
     '{"action":"read","path":".l2test/hello.txt"}', check_f1),
    ("F2",  "read empty.txt", "file",
     '{"action":"read","path":".l2test/empty.txt"}', check_f2),
    ("F3",  "stat hello.txt", "file",
     '{"action":"stat","path":".l2test/hello.txt"}', check_f3),
    ("F4",  "list .l2test/", "file",
     '{"action":"list","path":".l2test"}', check_f4),

    ("F5",  "read ../shared_prefs (escape)", "file",
     '{"action":"read","path":"../shared_prefs"}', check_f5),
    ("F6",  "read /etc/hostname (system)", "file",
     '{"action":"read","path":"/etc/hostname"}', check_f6),
    ("F7",  "read missing file", "file",
     '{"action":"read","path":".l2test/nope.txt"}', check_f7),
    ("F8",  "read a directory", "file",
     '{"action":"read","path":".l2test/sub"}', check_f8),
    ("F9",  "list a file", "file",
     '{"action":"list","path":".l2test/hello.txt"}', check_f9),

    ("F10", "read BOM file", "file",
     '{"action":"read","path":".l2test/bom.txt"}', check_f10),
    ("F11", "read binary file", "file",
     '{"action":"read","path":".l2test/binary.dat"}', check_f11),
    ("F12", "read big.txt maxBytes=100", "file",
     '{"action":"read","path":".l2test/big.txt","maxBytes":100}', check_f12),

    ("F13", "search lines.txt pattern=l3", "file",
     '{"action":"search","path":".l2test/lines.txt","pattern":"l3"}', check_f13),
    ("F14", "search sub/ pattern=needle", "file",
     '{"action":"search","path":".l2test/sub","pattern":"needle"}', check_f14),
    ("F15", "read lines.txt offset=1 limit=2", "file",
     '{"action":"read","path":".l2test/lines.txt","offsetLines":1,"limitLines":2}', check_f15),
    ("F16", "search many.txt pattern=hit maxMatches=3", "file",
     '{"action":"search","path":".l2test/many.txt","pattern":"hit","maxMatches":3}', check_f16),
]

# ------------------------------------------------------------------ main

def main():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    sock.settimeout(None)

    hello = read_until_type(sock, "hello", timeout=10)
    if not hello:
        print("[FAIL] no hello from bridge")
        sys.exit(1)
    print("Connected. Protocol: %s" % hello.get("protocol"))

    results = []
    for test_id, desc, tool, args, checker in TESTS:
        send_json(sock, {"type": "tool_invoke", "name": tool, "arguments": args})
        resp = read_until_type(sock, "tool_result_direct", timeout=30)
        if resp is None:
            ok, detail = False, "no tool_result_direct received"
        else:
            ok, detail = checker(resp)

        status = "PASS" if ok else "FAIL"
        print("[%s] %s (%s)" % (status, test_id, desc))
        if not ok:
            print("       %s" % detail)
            if resp:
                out = resp.get("output", "")
                if out:
                    print("       output: %s" % out[:300])
                err = resp.get("error", "")
                if err:
                    print("       error: %s" % err[:300])
        results.append((test_id, ok))

    passed = sum(1 for _, ok in results if ok)
    failed = sum(1 for _, ok in results if not ok)
    print("\n=== SUMMARY: %d PASS, %d FAIL, %d total ===" % (passed, failed, len(results)))
    for tid, ok in results:
        print("  %s  %s" % ("PASS" if ok else "FAIL", tid))

    try:
        sock.close()
    except Exception:
        pass

    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
