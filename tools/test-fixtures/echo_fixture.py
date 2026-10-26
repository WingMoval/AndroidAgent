#!/usr/bin/env python3
"""
AG.TOPIC-SESSION-PERSISTENCE-IMPLEMENTATION-0I.DEVICE-VERIFY-1 — context-echo fixture.

Test infrastructure only (like openai_compatible_fixture.py). A minimal
OpenAI-compatible /v1 endpoint that, on each /v1/chat/completions request,
echoes the message count + last user message back as the assistant reply.

WHY: to prove MODEL_CONTEXT_CONTAINS_RESTORED_HISTORY on a real device.
When the app restores a durable TopicSession and the user sends a new message,
the provider request carries the restored history + the new user message.
Echoing the count lets us verify the restored messages were actually supplied
to the next model call (not just shown in the UI transcript).

STANDARD LIBRARY ONLY. Binds 127.0.0.1; device reaches it via `adb reverse`.
Accepts any Bearer. Never logs the Authorization header / API key.

The echo response is DETERMINISTIC and NON-SECRET: message count + the user's
own message text (already on screen). No new secrets are introduced.
"""
import argparse
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

DEFAULT_MODEL = "fixture-stream"


def build_content_chunk(text: str) -> str:
    return {
        "choices": [
            {"index": 0, "delta": {"role": "assistant", "content": text}}
        ]
    }


def build_final_chunk() -> str:
    return {
        "choices": [
            {"index": 0, "delta": {}, "finish_reason": "stop"}
        ]
    }


class EchoHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("FIXTURE_REQUEST " + (fmt % args) + "\n")
        sys.stderr.flush()

    def _read_body(self) -> bytes:
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length <= 0:
            return b""
        return self.rfile.read(length)

    def _send_json(self, status: int, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_sse(self, line: str):
        self.wfile.write(("data: " + line + "\n\n").encode("utf-8"))
        self.wfile.flush()

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            self._send_json(200, {"status": "ok"})
        elif path == "/v1/models":
            self._send_json(200, {"object": "list", "data": [{"id": DEFAULT_MODEL, "object": "model"}]})
        else:
            self._send_json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        path = urlparse(self.path).path
        if path != "/v1/chat/completions":
            self._send_json(404, {"error": {"message": "not found"}})
            return
        raw = self._read_body()
        try:
            req = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception:
            req = {}
        messages = req.get("messages", [])
        msg_count = len(messages)
        last_user = ""
        for m in reversed(messages):
            if isinstance(m, dict) and m.get("role") == "user":
                last_user = str(m.get("content", ""))
                break
        stream = bool(req.get("stream", False))
        # Safe diagnostic: message count + the user's own message (on screen already).
        sys.stderr.write("ECHO_REQUEST msg_count=%d last_user=%r\n" % (msg_count, last_user))
        sys.stderr.flush()
        # Deterministic, non-secret echo that proves the restored context was supplied.
        reply = "ECHO msg_count=%d | last_user=%s" % (msg_count, last_user)
        if not stream:
            self._send_json(200, {
                "id": "fixture-nonstream",
                "object": "chat.completion",
                "created": int(time.time()),
                "model": DEFAULT_MODEL,
                "choices": [{"index": 0, "message": {"role": "assistant", "content": reply}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
            })
            return
        self._stream_response(reply)

    def _stream_response(self, reply: str):
        # Connection: close is REQUIRED (see openai_compatible_fixture.py note).
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        # Send the reply in a few chunks so the on-device streaming path is exercised.
        words = reply.split(" ")
        for i in range(0, len(words), 4):
            chunk = " ".join(words[i:i + 4])
            if i < len(words) - 4:
                chunk += " "
            self._send_sse(json.dumps(build_content_chunk(chunk)))
            time.sleep(0.02)
        self._send_sse(json.dumps(build_final_chunk()))
        self._send_sse("[DONE]")
        sys.stderr.write("STREAM_COMPLETED\n")
        sys.stderr.flush()


def main(argv):
    p = argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=18080)
    args = p.parse_args(argv)
    if args.host not in ("127.0.0.1", "localhost"):
        sys.stderr.write("WARNING: non-loopback bind %r\n" % args.host)
    server = ThreadingHTTPServer((args.host, args.port), EchoHandler)
    server.daemon_threads = True
    sys.stderr.write("ECHO_FIXTURE_READY host=%s port=%d model=%s\n" % (args.host, args.port, DEFAULT_MODEL))
    sys.stderr.write("ADB_REVERSE adb reverse tcp:%d tcp:%d\n" % (args.port, args.port))
    sys.stderr.write("DEVICE_BASE_URL http://127.0.0.1:%d/v1\n" % args.port)
    sys.stderr.flush()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        sys.stderr.write("\nECHO_FIXTURE_STOPPED\n")
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    # argv = sys.argv[1:]: the harness/runner may re-inject the script path as the
    # first arg; only the flags after the script are ours.
    sys.exit(main(sys.argv[1:]))
