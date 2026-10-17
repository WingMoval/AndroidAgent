#!/usr/bin/env python3
"""
AG.HOST-STOP-1 — OpenAI-compatible test fixture for real-device Stop acceptance.

WHAT THIS IS
    Test infrastructure only. A minimal OpenAI-compatible `/v1` endpoint the
    on-device Debug APK can point its production OpenAI-compatible provider at
    through `adb reverse`. It exists so a human can watch real streaming on a
    phone and tap Stop, with NO real LLM and NO API cost.

WHAT THIS IS NOT
    A ModelProvider implementation, a product provider, a tool, a new AG
    module, a Roadmap capability, or a production dependency. It has no Stable
    ID of its own; it belongs to AG.HOST-STOP-1's test infrastructure.

STANDARD LIBRARY ONLY
    No pip install, no third-party server framework, no Docker. Python 3 stdlib
    (http.server, json, threading, argparse, time, sys) only.

SECURITY
    Binds to 127.0.0.1 by default (loopback). The device reaches it only via
    `adb reverse tcp:18080 tcp:18080`. Passing --host 0.0.0.0 is allowed but
    prints a loud warning (do NOT do that on a shared network).
    Accepts ANY Authorization Bearer value (there is no real key). It never
    logs the Authorization header, the API key, or the full user prompt.
"""

import argparse
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

FIXTURE_NAME = "androidagent-openai-compatible"
DEFAULT_MODEL = "fixture-stream"

# A counter so each request's tokens are unique (helps prove a new Run reached
# the fixture after a Stop, rather than replaying a cached stream).
_request_lock = threading.Lock()
_request_counter = 0


def next_request_id():
    global _request_counter
    with _request_lock:
        _request_counter += 1
        return _request_counter


def build_content_chunk(text: str) -> str:
    """One well-formed OpenAI-compatible streaming content delta."""
    return {
        "choices": [
            {
                "index": 0,
                "delta": {"role": "assistant", "content": text},
            }
        ]
    }


def build_final_chunk() -> str:
    """The terminal chunk carrying finish_reason (no content)."""
    return {
        "choices": [
            {
                "index": 0,
                "delta": {},
                "finish_reason": "stop",
            }
        ]
    }


def build_nonstream_response(content: str) -> dict:
    """A minimal non-streaming chat completion body."""
    return {
        "id": "fixture-nonstream",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": DEFAULT_MODEL,
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": content},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
    }


class FixtureHandler(BaseHTTPRequestHandler):
    # The server object carries the CLI-provided stream settings.
    protocol_version = "HTTP/1.1"

    # --- logging -----------------------------------------------------------
    def log_message(self, fmt, *args):
        # Route the stdlib access log to stderr with a stable prefix. The
        # request line is metadata (path/method), never a secret.
        sys.stderr.write("FIXTURE_REQUEST " + (fmt % args) + "\n")
        sys.stderr.flush()

    # --- helpers -----------------------------------------------------------
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
        # Send one SSE `data:` line and flush immediately so the client sees
        # it in real time (the on-device streaming read).
        data = "data: " + line + "\n\n"
        self.wfile.write(data.encode("utf-8"))
        self.wfile.flush()

    # --- routing -----------------------------------------------------------
    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            self._send_json(200, {"status": "ok", "fixture": FIXTURE_NAME})
        elif path == "/v1/models":
            self._send_json(
                200,
                {
                    "object": "list",
                    "data": [
                        {
                            "id": DEFAULT_MODEL,
                            "object": "model",
                            "created": int(time.time()),
                            "owned_by": "fixture",
                        }
                    ],
                },
            )
        else:
            self._send_json(404, {"error": {"message": "not found", "type": "invalid_request_error"}})

    def do_POST(self):
        path = urlparse(self.path).path
        if path != "/v1/chat/completions":
            self._send_json(404, {"error": {"message": "not found", "type": "invalid_request_error"}})
            return

        raw = self._read_body()
        try:
            req = json.loads(raw.decode("utf-8")) if raw else {}
        except Exception:
            req = {}
        stream = bool(req.get("stream", False))
        rid = next_request_id()
        sys.stderr.write("REQUEST_ACCEPTED id=%d stream=%s\n" % (rid, stream))
        sys.stderr.flush()

        if not stream:
            # Minimal non-streaming response. The current provider always
            # streams, but a few flows may probe non-stream; keep it honest.
            content = "fixture nonstream reply (request %d)" % rid
            self._send_json(200, build_nonstream_response(content))
            return

        self._stream_response(rid)

    def _stream_response(self, rid: int):
        delay_s = getattr(self.server, "delay_ms", 300) / 1000.0
        chunks = getattr(self.server, "chunks", 600)

        # SSE response headers.
        #
        # AG.HOST-STOP-1.NATURAL-COMPLETION.CLOSEOUT: `Connection: close` is
        # REQUIRED here, not a style choice. Without it the stdlib HTTP/1.1
        # handler keeps the socket open after the final `[DONE]` byte (the
        # response has no Content-Length and no chunked framing, and the
        # handler has no way to know the body is over). A client that terminates
        # its stream read on `readLine() == null` (physical EOF) — which is what
        # the production Android transport does — then blocks until its socket
        # read timeout instead of completing. The pre-fix byte trace recorded
        # exactly this: all 7 data events received, then no EOF for 24 s; the
        # post-fix trace shows EOF at the last byte (t=0.508 s). Real OpenAI
        # endpoints close the SSE response; this matches them.
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        sys.stderr.write("STREAM_STARTED id=%d chunks=%d delay_ms=%d\n"
                         % (rid, chunks, int(delay_s * 1000)))
        sys.stderr.flush()

        sent = 0
        try:
            for i in range(chunks):
                text = "fixture token %04d " % (i + 1)
                self._send_sse(json.dumps(build_content_chunk(text)))
                sent += 1
                time.sleep(delay_s)
            # Terminal chunk + [DONE] marker (the provider's done signal).
            self._send_sse(json.dumps(build_final_chunk()))
            self._send_sse("[DONE]")
            sys.stderr.write("STREAM_COMPLETED id=%d chunk_count=%d\n" % (rid, sent))
            sys.stderr.flush()
        except (BrokenPipeError, ConnectionResetError):
            # The on-device client tore the connection down (the user Stop).
            # Stop emitting immediately; this is an expected, clean outcome —
            # NOT a server failure. No traceback.
            sys.stderr.write("CLIENT_DISCONNECTED id=%d chunk_count=%d\n" % (rid, sent))
            sys.stderr.flush()
        except Exception as e:  # pragma: no cover - defensive; keep server alive
            sys.stderr.write("STREAM_ERROR id=%d type=%s chunk_count=%d\n"
                             % (rid, type(e).__name__, sent))
            sys.stderr.flush()


def parse_args(argv):
    p = argparse.ArgumentParser(description="OpenAI-compatible test fixture (loopback).")
    p.add_argument("--host", default="127.0.0.1",
                   help="bind address (default 127.0.0.1; 0.0.0.0 is allowed but warns)")
    p.add_argument("--port", type=int, default=18080, help="bind port (default 18080)")
    p.add_argument("--delay-ms", type=int, default=300,
                   help="delay between streaming chunks in ms (default 300)")
    p.add_argument("--chunks", type=int, default=600,
                   help="number of content chunks to stream (default 600)")
    return p.parse_args(argv)


def main(argv):
    args = parse_args(argv)
    if args.host not in ("127.0.0.1", "localhost"):
        sys.stderr.write(
            "WARNING: binding to non-loopback host %r exposes the fixture on the "
            "network. For the standard device test use the default 127.0.0.1 + "
            "adb reverse.\n" % args.host
        )
        sys.stderr.flush()

    server = ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    server.daemon_threads = True
    server.delay_ms = args.delay_ms
    server.chunks = args.chunks

    sys.stderr.write("FIXTURE_READY host=%s port=%d model=%s\n"
                     % (args.host, args.port, DEFAULT_MODEL))
    sys.stderr.write("ADB_REVERSE adb reverse tcp:%d tcp:%d\n" % (args.port, args.port))
    sys.stderr.write("DEVICE_BASE_URL http://127.0.0.1:%d/v1\n" % args.port)
    sys.stderr.write("DEVICE_MODEL %s\n" % DEFAULT_MODEL)
    sys.stderr.write("DEVICE_API_KEY fixture-key (any Bearer value accepted)\n")
    sys.stderr.flush()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        sys.stderr.write("\nFIXTURE_STOPPED\n")
        sys.stderr.flush()
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
