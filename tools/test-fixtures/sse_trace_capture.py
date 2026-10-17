#!/usr/bin/env python3
"""
AG.HOST-STOP-1.NATURAL-COMPLETION.CLOSEOUT — byte-level SSE trace capture.

Test infrastructure only (stdlib only). Records the EXACT bytes a client receives
on a streaming /v1/chat/completions request, so the D3 natural-completion
adjudication can separate:

  - what the fixture SENT (server side)
  - what the client RECEIVED (byte-for-byte, with timing)

and therefore answer §6/§12/§13 with FACTS instead of guesses:
  - is the HTTP framing legal (status / headers / [DONE] / blank-line separators)?
  - is there an EOF, and how soon after the last data byte?
  - does the client's next read block (i.e. no EOF) for the full read-timeout?

Usage:
  python sse_trace_capture.py --port 18080 --timeout-sec 15 --out trace.json

The client connects, sends one minimal POST (stream=true), and then reads with
per-read timeouts, recording every chunk with a timestamp. The run ends when the
server closes the connection (EOF) or --timeout-sec elapses.
"""

import argparse
import json
import socket
import sys
import time

MINIMAL_BODY = json.dumps(
    {"model": "fixture-stream", "stream": True, "messages": [{"role": "user", "content": "trace"}]}
)

MINIMAL_REQUEST = (
    "POST /v1/chat/completions HTTP/1.1\r\n"
    "Host: 127.0.0.1:18080\r\n"
    "Content-Type: application/json\r\n"
    "Authorization: Bearer fixture-key\r\n"
    "Content-Length: %d\r\n"
    "Connection: close\r\n"
    "\r\n" % len(MINIMAL_BODY)
) + MINIMAL_BODY


def main(argv):
    p = argparse.ArgumentParser(description="byte-level SSE trace capture (test infra).")
    p.add_argument("--port", type=int, default=18080)
    p.add_argument("--timeout-sec", type=float, default=15.0,
                   help="max wall time to wait for EOF after the last byte")
    p.add_argument("--read-timeout-sec", type=float, default=6.0,
                   help="per-read socket timeout while waiting for more bytes")
    p.add_argument("--out", default="sse_trace.json")
    args = p.parse_args(argv)

    trace = {
        "start": None,
        "handshake": [],
        "body_chunks": [],
        "eof_at": None,
        "total_bytes": 0,
        "error": None,
    }
    t0 = time.time()
    trace["start"] = t0

    try:
        s = socket.create_connection(("127.0.0.1", args.port), timeout=10.0)
        s.settimeout(args.read_timeout_sec)
        s.sendall(MINIMAL_REQUEST.encode("utf-8"))

        # --- read headers until CRLFCRLF -------------------------------------
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = s.recv(4096)
            if not chunk:
                break
            buf += chunk
        if b"\r\n\r\n" in buf:
            head, _, rest = buf.partition(b"\r\n\r\n")
            for line in head.decode("utf-8", "replace").split("\r\n"):
                trace["handshake"].append(line)
        else:
            trace["error"] = "incomplete headers; conn closed"
            head, rest = b"", b""

        # --- read body until EOF or overall timeout ---------------------------
        first_byte_at = None
        last_byte_at = None
        while time.time() - t0 < args.timeout_sec:
            try:
                chunk = s.recv(65536)
            except socket.timeout:
                trace.setdefault("read_timeouts", []).append(
                    {"t": round(time.time() - t0, 3)}
                )
                continue
            if not chunk:
                last_byte_at = last_byte_at or time.time()
                trace["eof_at"] = round(time.time() - t0, 3)
                break
            if first_byte_at is None:
                first_byte_at = time.time()
            last_byte_at = time.time()
            trace["body_chunks"].append(
                {
                    "t": round(time.time() - t0, 3),
                    "len": len(chunk),
                    "hex_head": chunk[:24].hex(),
                    "text": chunk.decode("utf-8", "replace")[:200],
                }
            )
            trace["total_bytes"] += len(chunk)

        s.close()
    except Exception as e:  # noqa: BLE001 - trace everything
        trace["error"] = "%s: %s" % (type(e).__name__, e)

    trace["finished"] = round(time.time() - t0, 3)
    if trace["eof_at"] is None and last_byte_at is None:
        trace["gap_after_last_byte_sec"] = None
    elif trace["eof_at"] is not None and last_byte_at is not None:
        trace["gap_after_last_byte_sec"] = round(trace["eof_at"] - last_byte_at, 3)
    else:
        trace["gap_after_last_byte_sec"] = None

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(trace, f, indent=2)
    sys.stderr.write("TRACE_SAVED %s\n" % args.out)
    sys.stderr.write("TRACE_SUMMARY status_line=%s eof_at=%s total_bytes=%d error=%s\n"
                     % (trace["handshake"][0] if trace["handshake"] else None,
                        trace["eof_at"], trace["total_bytes"], trace["error"]))
    sys.stderr.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
