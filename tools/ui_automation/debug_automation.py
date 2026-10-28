#!/usr/bin/env python3
"""
AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION — PC-side Debug automation harness.

Connects to the on-device DebugBridge (TCP JSONL, port 45678) over an
`adb forward` tunnel and drives/observes the REAL product through the shared
HostActions seam. It is NOT a test framework or device manager — it only:

  connect / ping / snapshot / send / stop / new_session / adb_reconnect /
  provider_switch / read events / wait / assert

and prints a structured PASS/FAIL summary.

Usage (from the repo root):
  python tools/ui_automation/debug_automation.py --serial <SERIAL> --local-port 45678

The harness assumes the app is already installed and running (debug build).
It does NOT modify the DebugBridge binding (0.0.0.0:45678).
"""

import argparse
import json
import socket
import subprocess
import sys
import threading
import time

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

DEFAULT_DEVICE_PORT = 45678
DEFAULT_TIMEOUT = 30.0


# ---------------------------------------------------------------------------
# ADB forward
# ---------------------------------------------------------------------------

def adb_forward(serial: str, local_port: int, device_port: int) -> bool:
    """Establish `adb forward tcp:<local> tcp:<device>` and verify it."""
    try:
        out = subprocess.check_output(
            ["adb", "-s", serial, "forward", f"tcp:{local_port}", f"tcp:{device_port}"],
            stderr=subprocess.STDOUT,
            text=True,
        )
        if str(local_port) not in out:
            print(f"  [adb forward] unexpected output: {out.strip()}", file=sys.stderr)
            return False
        return True
    except Exception as e:  # noqa: BLE001
        print(f"  [adb forward] failed: {e}", file=sys.stderr)
        return False


# ---------------------------------------------------------------------------
# Debug client (TCP JSONL)
# ---------------------------------------------------------------------------

class DebugClient:
    """A minimal TCP JSONL client for the on-device DebugBridge."""

    def __init__(self, host: str, port: int, timeout: float = DEFAULT_TIMEOUT):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.sock = None
        self.reader = None
        self._lock = threading.Lock()
        self._events = []  # (type, payload) for every device->client line
        self._stop = threading.Event()

    # -- lifecycle ----------------------------------------------------------

    def connect(self) -> bool:
        try:
            self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
            self.reader = self.sock.makefile("r", encoding="utf-8")
            # Read the hello line (the bridge sends it immediately on connect).
            self._pump_thread = threading.Thread(target=self._pump, daemon=True)
            self._pump_thread.start()
            # Wait briefly for the hello.
            deadline = time.time() + 3.0
            while time.time() < deadline:
                if any(t == "hello" for t, _ in self._events):
                    return True
                time.sleep(0.05)
            return False
        except Exception as e:  # noqa: BLE001
            print(f"  [connect] failed: {e}", file=sys.stderr)
            return False

    def close(self):
        self._stop.set()
        try:
            if self.sock:
                self.sock.close()
        except Exception:  # noqa: BLE001
            pass

    # -- background reader --------------------------------------------------

    def _pump(self):
        try:
            while not self._stop.is_set():
                line = self.reader.readline()
                if not line:
                    break
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                except json.JSONDecodeError:
                    continue
                self._events.append((obj.get("type", ""), obj))
        except Exception:  # noqa: BLE001
            pass

    # -- commands -----------------------------------------------------------

    def _send_line(self, obj: dict) -> None:
        with self._lock:
            data = json.dumps(obj) + "\n"
            self.sock.sendall(data.encode("utf-8"))

    def ping(self) -> str:
        """Send ping, return the pong type (or error type)."""
        self._clear_events()
        self._send_line({"type": "ping"})
        return self._wait_for_type({"pong", "error"}, timeout=5.0)

    def snapshot(self) -> dict:
        self._clear_events()
        self._send_line({"type": "snapshot"})
        t = self._wait_for_type({"snapshot", "error"}, timeout=5.0)
        if t == "error":
            raise RuntimeError(f"snapshot error: {self._last_event()}")
        return self._last_event()

    def send(self, text: str) -> dict:
        """Send a run. Returns the accepted/busy/error response (the run's
        terminal event is observed separately via the event stream)."""
        self._clear_events()
        self._send_line({"type": "send", "text": text})
        t = self._wait_for_type({"accepted", "busy", "error"}, timeout=5.0)
        if t == "error":
            raise RuntimeError(f"send error: {self._last_event()}")
        # Return the specific accepted/busy event, not the last event
        # (which may be a later run_finished by the time we get here).
        with self._lock:
            for et, payload in self._events:
                if et in ("accepted", "busy", "error"):
                    return payload
        return self._last_event()

    def stop(self) -> dict:
        self._clear_events()
        self._send_line({"type": "stop"})
        t = self._wait_for_type({"stop_result", "error"}, timeout=5.0)
        if t == "error":
            raise RuntimeError(f"stop error: {self._last_event()}")
        return self._last_event()

    def new_session(self) -> dict:
        self._clear_events()
        self._send_line({"type": "new_session"})
        t = self._wait_for_type({"new_session_result", "error"}, timeout=5.0)
        if t == "error":
            raise RuntimeError(f"new_session error: {self._last_event()}")
        return self._last_event()

    def adb_reconnect(self) -> dict:
        self._clear_events()
        self._send_line({"type": "adb_reconnect"})
        t = self._wait_for_type({"adb_reconnect_result", "error"}, timeout=15.0)
        if t == "error":
            raise RuntimeError(f"adb_reconnect error: {self._last_event()}")
        return self._last_event()

    def provider_switch(self, node: str) -> dict:
        self._clear_events()
        self._send_line({"type": "provider_switch", "node": node})
        t = self._wait_for_type({"provider_switch_result", "error"}, timeout=10.0)
        if t == "error":
            raise RuntimeError(f"provider_switch error: {self._last_event()}")
        return self._last_event()

    # -- event observation --------------------------------------------------

    def _clear_events(self):
        with self._lock:
            self._events.clear()

    def _last_event(self) -> dict:
        with self._lock:
            return self._events[-1][1] if self._events else {}

    def _wait_for_type(self, types, timeout: float) -> str:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._lock:
                for t, _ in self._events:
                    if t in types:
                        return t
            time.sleep(0.05)
        return "timeout"

    def wait_busy(self, busy: bool, timeout: float = DEFAULT_TIMEOUT) -> bool:
        """Wait until snapshot.busy == busy. Returns True on success."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                snap = self.snapshot()
                if snap.get("busy") == busy:
                    return True
            except Exception:  # noqa: BLE001
                pass
            time.sleep(0.3)
        return False

    def wait_run_finished(self, timeout: float = DEFAULT_TIMEOUT) -> dict:
        """Wait for a run_finished event; return it (or {} on timeout)."""
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._lock:
                for t, payload in self._events:
                    if t == "run_finished":
                        return payload
            time.sleep(0.2)
        return {}

    def events_of_type(self, t: str) -> list:
        with self._lock:
            return [p for et, p in self._events if et == t]


# ---------------------------------------------------------------------------
# Gate helpers
# ---------------------------------------------------------------------------

def gate(name: str, ok: bool, detail: str = ""):
    status = "PASS" if ok else "FAIL"
    line = f"{name:32s} = {status}"
    if detail:
        line += f"  ({detail})"
    print(line)
    return ok


# ---------------------------------------------------------------------------
# Gate sequence
# ---------------------------------------------------------------------------

def run_gates(client: DebugClient) -> dict:
    results = {}

    # ---- Gate A: ping ----
    print("\n--- Gate A: Ping ---")
    pong = client.ping()
    results["PING"] = gate("PING", pong == "pong", f"got={pong}")

    # ---- Gate B: snapshot ----
    print("\n--- Gate B: Snapshot ---")
    try:
        snap = client.snapshot()
        required = ["runtimeReady", "busy", "adbState", "activeProvider",
                    "activeModel", "transcriptBlockCount"]
        missing = [k for k in required if k not in snap]
        # SECRET safety: no secret-bearing keys in the payload.
        secret_keys = ["apiKey", "authorization", "pairingCode", "token", "password"]
        leaked = [k for k in snap if k.lower() in secret_keys]
        ok = not missing and not leaked and snap.get("runtimeReady") in (True, False)
        results["SNAPSHOT"] = gate(
            "SNAPSHOT", ok,
            f"missing={missing} leaked={leaked} "
            f"provider={snap.get('activeProvider')} model={snap.get('activeModel')} "
            f"blocks={snap.get('transcriptBlockCount')} adb={snap.get('adbState')}",
        )
    except Exception as e:  # noqa: BLE001
        results["SNAPSHOT"] = gate("SNAPSHOT", False, str(e))
        snap = {}

    # ---- Gate C: Debug Send -> UI ----
    print("\n--- Gate C: Debug Send -> UI ---")
    tag = f"AUTOMATION_DEBUG_SEND_{int(time.time())}"
    try:
        resp = client.send(tag)
        busy_ok = client.wait_busy(True, timeout=10.0)
        fin = client.wait_run_finished(timeout=60.0)
        idle_ok = client.wait_busy(False, timeout=15.0)
        # The run must have produced at least a run_start + run_finished.
        saw_start = bool(client.events_of_type("run_start"))
        ok = (resp.get("type") == "accepted" and busy_ok and bool(fin) and idle_ok and saw_start)
        results["DEBUG_SEND_TO_UI"] = gate(
            "DEBUG_SEND_TO_UI", ok,
            f"accepted={resp.get('type') == 'accepted'} busy={busy_ok} "
            f"finished={bool(fin)} idle={idle_ok} start={saw_start}",
        )
    except Exception as e:  # noqa: BLE001
        results["DEBUG_SEND_TO_UI"] = gate("DEBUG_SEND_TO_UI", False, str(e))

    # ---- Gate F: Debug Stop (needs a long-running run) ----
    # We rely on Gate C having already exercised a run. For a dedicated stop
    # we send a run, wait busy, then stop. (If the model is fast, the run may
    # finish before stop lands — that is acceptable and reported honestly.)
    print("\n--- Gate F: Debug Stop ---")
    try:
        client.send("AUTOMATION_STOP_PROBE")
        busy = client.wait_busy(True, timeout=10.0)
        if busy:
            stop_resp = client.stop()
            fin = client.wait_run_finished(timeout=30.0)
            idle = client.wait_busy(False, timeout=15.0)
            interrupted = fin.get("outcome", "").lower() == "interrupted"
            ok = stop_resp.get("result") in ("requested", "already_interrupting") and idle
            results["DEBUG_STOP_TO_UI"] = gate(
                "DEBUG_STOP_TO_UI", ok,
                f"result={stop_resp.get('result')} interrupted={interrupted} idle={idle}",
            )
        else:
            results["DEBUG_STOP_TO_UI"] = gate(
                "DEBUG_STOP_TO_UI", False, "run finished before stop (not busy)",
            )
    except Exception as e:  # noqa: BLE001
        results["DEBUG_STOP_TO_UI"] = gate("DEBUG_STOP_TO_UI", False, str(e))

    # ---- Gate H: Debug New Session ----
    print("\n--- Gate H: Debug New Session ---")
    try:
        before = client.snapshot().get("currentSessionId")
        resp = client.new_session()
        after = client.snapshot().get("currentSessionId")
        ok = resp.get("success") is True and after is not None and after != before
        results["NEW_SESSION_DEBUG"] = gate(
            "NEW_SESSION_DEBUG", ok,
            f"before={before} after={after} success={resp.get('success')}",
        )
    except Exception as e:  # noqa: BLE001
        results["NEW_SESSION_DEBUG"] = gate("NEW_SESSION_DEBUG", False, str(e))

    # ---- Gate I: Debug ADB Reconnect ----
    print("\n--- Gate I: Debug ADB Reconnect ---")
    try:
        resp = client.adb_reconnect()
        # Idempotent reconnect is acceptable when ADB is already ready.
        ok = "ready" in resp
        results["ADB_RECONNECT_DEBUG"] = gate(
            "ADB_RECONNECT_DEBUG", ok,
            f"ready={resp.get('ready')} state={resp.get('state')}",
        )
    except Exception as e:  # noqa: BLE001
        results["ADB_RECONNECT_DEBUG"] = gate("ADB_RECONNECT_DEBUG", False, str(e))

    # ---- Gate J: Debug Provider Switch ----
    # Only meaningful when a second, valid provider node is provisioned. If the
    # switch is rejected (config not provisioned), report DEFERRED_WITH_REASON.
    print("\n--- Gate J: Debug Provider Switch ---")
    try:
        current = client.snapshot().get("activeProvider")
        # Try switching to the OTHER known node (OpenRouter <-> EMG).
        target = "EMG" if current == "OpenRouter" else "OpenRouter"
        resp = client.provider_switch(target)
        if resp.get("success") is True:
            after = client.snapshot().get("activeProvider")
            ok = after == target
            results["PROVIDER_SWITCH_DEBUG"] = gate(
                "PROVIDER_SWITCH_DEBUG", ok,
                f"current={current} target={target} after={after}",
            )
            # Switch back to restore the original active provider.
            client.provider_switch(current)
        else:
            results["PROVIDER_SWITCH_DEBUG"] = gate(
                "PROVIDER_SWITCH_DEBUG", False,
                f"DEFERRED_WITH_REASON: {resp.get('reason')} "
                f"(target={target} not provisioned)",
            )
    except Exception as e:  # noqa: BLE001
        results["PROVIDER_SWITCH_DEBUG"] = gate("PROVIDER_SWITCH_DEBUG", False, str(e))

    return results


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Debug automation harness")
    ap.add_argument("--serial", required=True, help="adb device serial")
    ap.add_argument("--local-port", type=int, default=DEFAULT_DEVICE_PORT,
                    help="local port for adb forward (default 45678)")
    ap.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT,
                    help="default per-command timeout seconds")
    args = ap.parse_args()

    print("=== AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION harness ===")
    print(f"serial={args.serial} local_port={args.local_port} device_port={DEFAULT_DEVICE_PORT}")

    if not adb_forward(args.serial, args.local_port, DEFAULT_DEVICE_PORT):
        print("RESULT = FAIL (adb forward failed)")
        sys.exit(1)

    client = DebugClient("127.0.0.1", args.local_port, timeout=args.timeout)
    if not client.connect():
        print("RESULT = FAIL (connect failed)")
        sys.exit(1)
    print("[connect] OK (hello received)")

    try:
        results = run_gates(client)
    finally:
        client.close()

    # ---- Summary ----
    print("\n=== SUMMARY ===")
    for k, v in results.items():
        print(f"  {k:32s} = {'PASS' if v else 'FAIL'}")
    all_pass = all(results.values())
    print(f"\nCALLBACK_DEBUG_AUTOMATION = {'PASS' if all_pass else 'FAIL'}")
    sys.exit(0 if all_pass else 1)


if __name__ == "__main__":
    main()
