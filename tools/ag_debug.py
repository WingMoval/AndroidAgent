#!/usr/bin/env python3
"""AG.DEBUG-0 — minimal PC debug client for the AndroidAgent debug bridge.

Usage:
    adb forward tcp:45678 tcp:45678
    python tools/ag_debug.py [--port 45678]

Standard library only. Connects to the device-side DebugBridge (bound to
127.0.0.1) and:
  - prints the live AgentEvent execution stream (UI- or debug-originated runs),
  - accepts `send <text>` commands that enter the SAME AgentRuntime the UI uses.

The debug bridge binds 127.0.0.1 only and exists in debug builds only.
"""

import argparse
import json
import socket
import sys
import threading

HOST = "127.0.0.1"
DEFAULT_PORT = 45678


def recv_lines(sock):
    """Yield decoded UTF-8 lines from a socket, including a trailing newline sentinel."""
    buf = bytearray()
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            return
        buf += chunk
        while True:
            idx = buf.find(b"\n")
            if idx < 0:
                break
            line = bytes(buf[:idx]).decode("utf-8", errors="replace")
            del buf[: idx + 1]
            yield line


def pretty(kind, obj):
    """Render one bridge message in a compact, developer-friendly form."""
    if kind == "hello":
        return "AG Debug connected. (protocol=%s)" % obj.get("protocol")
    if kind == "accepted":
        return "[ACCEPTED]"
    if kind == "busy":
        return "[BUSY] a run is already in progress (same AgentRuntime guard)"
    if kind == "error":
        return "[ERROR] %s" % obj.get("message")
    if kind == "pong":
        return "[PONG]"
    if kind == "run_start":
        return "[RUN START] session=%s" % obj.get("sessionId")
    if kind == "model_turn_start":
        return "[STATE] Thinking... (step %s)" % obj.get("step")
    if kind == "model_turn_end":
        content = obj.get("content")
        line = "[MODEL] %s" % obj.get("turnType")
        if content:
            line += "\n%s" % content
        return line
    if kind == "tool_call":
        return "[TOOL] %s\n> %s" % (obj.get("tool"), obj.get("arguments"))
    if kind == "tool_result":
        s = "[RESULT] success=%s" % obj.get("success")
        out = obj.get("output")
        if out:
            s += "\n%s" % out
        if obj.get("error"):
            s += "\nerror: %s" % obj.get("error")
        return s
    if kind == "tool_result_direct":
        s = "[DIRECT] tool=%s success=%s" % (obj.get("tool"), obj.get("success"))
        out = obj.get("output")
        if out:
            s += "\n%s" % out
        if obj.get("error"):
            s += "\nerror: %s" % obj.get("error")
        return s
    if kind == "final_answer":
        return "[ANSWER]\n%s" % obj.get("content")
    if kind == "run_failed":
        return "[RUN FAILED] %s" % obj.get("failure")
    if kind == "run_finished":
        return "[RUN] Finished (%s)" % obj.get("outcome")
    if kind == "reasoning_started":
        return "[THINKING] started"
    if kind == "reasoning_delta":
        return "[THINKING] %s" % obj.get("text")
    if kind == "reasoning_finished":
        return "[THOUGHT] finished"
    if kind == "content_started":
        return "[ASSISTANT] started"
    if kind == "content_delta":
        return "[ASSISTANT] %s" % obj.get("text")
    if kind == "content_finished":
        return "[ASSISTANT] finished"
    return "[%s] %s" % (kind, json.dumps(obj, ensure_ascii=False))


def reader_loop(sock):
    try:
        for line in recv_lines(sock):
            try:
                obj = json.loads(line)
            except ValueError:
                print("[RAW] %s" % line)
                continue
            kind = obj.get("type", "?")
            print(pretty(kind, obj))
            sys.stdout.flush()
    except Exception as exc:  # socket closed / error
        print("[DISCONNECTED] %s" % exc)
        sys.exit(0)


def main():
    ap = argparse.ArgumentParser(description="AG.DEBUG-0 PC debug client")
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = ap.parse_args()

    sock = socket.create_connection((HOST, args.port), timeout=10)
    sock.settimeout(None)

    t = threading.Thread(target=reader_loop, args=(sock,), daemon=True)
    t.start()

    print("AG Debug connected. Type 'send <text>', 'invoke <name> <json-args>', or 'ping'; Ctrl-C to exit.")
    try:
        for line in sys.stdin:
            line = line.strip()
            if not line:
                continue
            if line == "ping":
                sock.sendall((json.dumps({"type": "ping"}) + "\n").encode("utf-8"))
            elif line.startswith("send "):
                text = line[5:].strip()
                if not text:
                    print("[ERROR] send requires text")
                    continue
                sock.sendall((json.dumps({"type": "send", "text": text}) + "\n").encode("utf-8"))
            elif line.startswith("invoke "):
                rest = line[7:].strip()
                if " " not in rest:
                    print("[ERROR] invoke requires: invoke <tool-name> <json-args>")
                    continue
                name, args_json = rest.split(" ", 1)
                sock.sendall((json.dumps({"type": "tool_invoke", "name": name, "arguments": args_json}) + "\n").encode("utf-8"))
            else:
                print("usage: 'send <text>' | 'invoke <name> <json-args>' | 'ping'")
    except KeyboardInterrupt:
        pass
    finally:
        try:
            sock.close()
        except Exception:
            pass
        print("")


if __name__ == "__main__":
    main()