#!/usr/bin/env python3
"""AG.FILE-0 L3 — real-agent E2E: model autonomously calls file tool.

Sends a prompt via the DebugBridge `send` channel (same AgentRuntime.send as UI),
then monitors the live event stream for:
  1. tool_call with name=file
  2. tool_result with SEVENTEEN in output
  3. final_answer with SEVENTEEN in content

PASS = all three observed, in order.
"""

import json
import socket
import sys
import threading
import time

HOST = "127.0.0.1"
PORT = 45678
PROMPT = "What is the answer to the L3 probe? Read the file facts.txt in your workspace and give me the exact answer."
MARKER = "SEVENTEEN"
TIMEOUT = 120.0


class Monitor:
    def __init__(self):
        self.tool_call_file = False
        self.tool_result_marker = False
        self.final_answer_marker = False
        self.events = []
        self.done = threading.Event()

    def handle(self, kind, obj):
        self.events.append((kind, obj))
        if kind == "tool_call" and obj.get("tool") == "file":
            self.tool_call_file = True
            print("  [OBSERVED] tool_call: file")
        elif kind == "tool_result":
            out = obj.get("output", "")
            if MARKER in out:
                self.tool_result_marker = True
                print("  [OBSERVED] tool_result contains %s" % MARKER)
        elif kind == "final_answer":
            content = obj.get("content", "")
            if MARKER in content:
                self.final_answer_marker = True
                print("  [OBSERVED] final_answer contains %s" % MARKER)
            self.done.set()
        elif kind == "run_failed":
            print("  [RUN FAILED] %s" % obj.get("failure"))
            self.done.set()
        elif kind == "run_finished":
            self.done.set()

    def check(self):
        ok = self.tool_call_file and self.tool_result_marker and self.final_answer_marker
        return ok


def main():
    sock = socket.create_connection((HOST, PORT), timeout=10)
    sock.settimeout(None)

    # Read hello
    buf = bytearray()
    while b"\n" not in buf:
        buf += sock.recv(4096)
    hello = json.loads(buf.decode("utf-8", errors="replace").split("\n")[0])
    print("Connected. Protocol: %s" % hello.get("protocol"))

    mon = Monitor()

    def reader():
        buf = bytearray()
        while True:
            try:
                chunk = sock.recv(4096)
            except Exception:
                break
            if not chunk:
                break
            buf += chunk
            while True:
                idx = buf.find(b"\n")
                if idx < 0:
                    break
                line = bytes(buf[:idx]).decode("utf-8", errors="replace")
                del buf[:idx + 1]
                try:
                    obj = json.loads(line)
                except ValueError:
                    continue
                kind = obj.get("type", "?")
                if kind not in ("hello", "pong"):
                    print("[%s] %s" % (kind, json.dumps(obj, ensure_ascii=False)[:200]))
                mon.handle(kind, obj)

    t = threading.Thread(target=reader, daemon=True)
    t.start()

    time.sleep(0.5)

    print("\nSending prompt: %s\n" % PROMPT)
    sock.sendall((json.dumps({"type": "send", "text": PROMPT}) + "\n").encode("utf-8"))

    # Wait for run to finish or timeout
    start = time.time()
    while not mon.done.is_set():
        if time.time() - start > TIMEOUT:
            print("\n[TIMEOUT] run did not finish within %ss" % TIMEOUT)
            break
        time.sleep(0.5)

    mon.done.wait(5)

    print("\n=== L3 RESULT ===")
    print("tool_call file:       %s" % ("YES" if mon.tool_call_file else "NO"))
    print("tool_result %s: %s" % (MARKER, "YES" if mon.tool_result_marker else "NO"))
    print("final_answer %s: %s" % (MARKER, "YES" if mon.final_answer_marker else "NO"))

    ok = mon.check()
    print("\nL3 OBSERVATION_DEPENDENCY: %s" % ("PASS" if ok else "FAIL"))

    # Show all events for the record
    print("\n--- Event trace ---")
    for kind, obj in mon.events:
        print("  %s: %s" % (kind, json.dumps(obj, ensure_ascii=False)[:150]))

    try:
        sock.close()
    except Exception:
        pass

    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
