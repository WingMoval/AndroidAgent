#!/usr/bin/env python3
"""
AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION — Gate A-I runner.
Runs all gates against the on-device DebugBridge via adb forward.
"""
import json
import sys
import time

sys.path.insert(0, "tools/ui_automation")
from debug_automation import DebugClient, gate

SERIAL = "APLMUT4312005935"
PORT = 45678

def main():
    print("=== AG.UI-SHELL-1.CALLBACK-DEBUG-AUTOMATION — Gate A-I ===")
    print(f"serial={SERIAL} port={PORT}")

    client = DebugClient("127.0.0.1", PORT, timeout=30.0)
    if not client.connect():
        print("RESULT = FAIL (connect failed)")
        sys.exit(1)
    print("[connect] OK (hello received)")

    results = {}

    # ---- Gate A: Ping ----
    print("\n--- Gate A: Ping ---")
    pong = client.ping()
    results["GATE_A_PING"] = gate("GATE_A_PING", pong == "pong", f"got={pong}")

    # ---- Gate B: Snapshot ----
    print("\n--- Gate B: Snapshot ---")
    try:
        snap = client.snapshot()
        required = ["runtimeReady", "busy", "adbState", "activeProvider",
                    "activeModel", "transcriptBlockCount", "lastBlockType",
                    "currentSessionId"]
        missing = [k for k in required if k not in snap]
        secret_keys = ["apiKey", "authorization", "pairingCode", "token", "password"]
        leaked = [k for k in snap if k.lower() in secret_keys]
        ok = not missing and not leaked and snap.get("runtimeReady") in (True, False)
        results["GATE_B_SNAPSHOT"] = gate(
            "GATE_B_SNAPSHOT", ok,
            f"missing={missing} leaked={leaked} "
            f"runtimeReady={snap.get('runtimeReady')} "
            f"provider={snap.get('activeProvider')} "
            f"model={snap.get('activeModel')} "
            f"blocks={snap.get('transcriptBlockCount')} "
            f"adb={snap.get('adbState')}",
        )
        results["SNAPSHOT_SECRET_SAFE"] = "YES" if not leaked else "NO"
    except Exception as e:
        results["GATE_B_SNAPSHOT"] = gate("GATE_B_SNAPSHOT", False, str(e))
        results["SNAPSHOT_SECRET_SAFE"] = "UNKNOWN"

    # ---- Gate C: Debug Send -> UI ----
    print("\n--- Gate C: Debug Send -> UI ---")
    tag = f"AUTOMATION_DEBUG_SEND_{int(time.time())}"
    try:
        resp = client.send(tag)
        busy_ok = client.wait_busy(True, timeout=10.0)
        fin = client.wait_run_finished(timeout=60.0)
        idle_ok = client.wait_busy(False, timeout=15.0)
        saw_start = bool(client.events_of_type("run_start"))
        # Count User blocks in the transcript via snapshot
        snap_after = client.snapshot()
        ok = (resp.get("type") == "accepted" and busy_ok and bool(fin)
              and idle_ok and saw_start)
        results["GATE_C_DEBUG_TO_UI"] = gate(
            "GATE_C_DEBUG_TO_UI", ok,
            f"accepted={resp.get('type') == 'accepted'} busy={busy_ok} "
            f"finished={bool(fin)} idle={idle_ok} start={saw_start} "
            f"outcome={fin.get('outcome', '?')}",
        )
    except Exception as e:
        results["GATE_C_DEBUG_TO_UI"] = gate("GATE_C_DEBUG_TO_UI", False, str(e))

    # ---- Gate F: Debug Stop (needs a long-running run) ----
    print("\n--- Gate F: Debug Stop ---")
    try:
        client.send("AUTOMATION_STOP_PROBE")
        busy = client.wait_busy(True, timeout=10.0)
        if busy:
            stop_resp = client.stop()
            fin = client.wait_run_finished(timeout=30.0)
            idle = client.wait_busy(False, timeout=15.0)
            interrupted = fin.get("outcome", "").lower() == "interrupted"
            ok = (stop_resp.get("result") in ("requested", "already_interrupting")
                  and interrupted and idle)
            results["GATE_F_DEBUG_STOP_TO_UI"] = gate(
                "GATE_F_DEBUG_STOP_TO_UI", ok,
                f"result={stop_resp.get('result')} interrupted={interrupted} idle={idle}",
            )
        else:
            results["GATE_F_DEBUG_STOP_TO_UI"] = gate(
                "GATE_F_DEBUG_STOP_TO_UI", False,
                "run finished before stop (not busy)",
            )
    except Exception as e:
        results["GATE_F_DEBUG_STOP_TO_UI"] = gate("GATE_F_DEBUG_STOP_TO_UI", False, str(e))

    # ---- Gate G: New Session (Debug) ----
    print("\n--- Gate G: New Session (Debug) ---")
    try:
        before = client.snapshot().get("currentSessionId")
        resp = client.new_session()
        after = client.snapshot().get("currentSessionId")
        ok = resp.get("success") is True and after is not None and after != before
        results["GATE_G_NEW_SESSION_DEBUG"] = gate(
            "GATE_G_NEW_SESSION_DEBUG", ok,
            f"before={before} after={after} success={resp.get('success')}",
        )
    except Exception as e:
        results["GATE_G_NEW_SESSION_DEBUG"] = gate("GATE_G_NEW_SESSION_DEBUG", False, str(e))

    # ---- Gate I: Debug Provider Switch ----
    print("\n--- Gate I: Debug Provider Switch ---")
    try:
        current = client.snapshot().get("activeProvider")
        target = "EMG" if current == "OpenRouter" else "OpenRouter"
        resp = client.provider_switch(target)
        if resp.get("success") is True:
            after = client.snapshot().get("activeProvider")
            ok = after == target
            results["GATE_I_PROVIDER_SWITCH_DEBUG"] = gate(
                "GATE_I_PROVIDER_SWITCH_DEBUG", ok,
                f"current={current} target={target} after={after}",
            )
            # Switch back
            client.provider_switch(current)
        else:
            results["GATE_I_PROVIDER_SWITCH_DEBUG"] = gate(
                "GATE_I_PROVIDER_SWITCH_DEBUG", False,
                f"DEFERRED_WITH_REASON: {resp.get('reason')} (target={target})",
            )
    except Exception as e:
        results["GATE_I_PROVIDER_SWITCH_DEBUG"] = gate("GATE_I_PROVIDER_SWITCH_DEBUG", False, str(e))

    # ---- Post-switch ping (DEBUG_SURVIVES_PROVIDER_SWITCH) ----
    print("\n--- Post-switch Ping ---")
    try:
        pong = client.ping()
        results["DEBUG_SURVIVES_PROVIDER_SWITCH"] = gate(
            "DEBUG_SURVIVES_PROVIDER_SWITCH", pong == "pong",
            f"ping after switch={pong}",
        )
    except Exception as e:
        results["DEBUG_SURVIVES_PROVIDER_SWITCH"] = gate(
            "DEBUG_SURVIVES_PROVIDER_SWITCH", False, str(e))

    client.close()

    # ---- Summary ----
    print("\n=== SUMMARY ===")
    for k, v in results.items():
        if isinstance(v, bool):
            print(f"  {k:40s} = {'PASS' if v else 'FAIL'}")
        else:
            print(f"  {k:40s} = {v}")

    core_gates = ["GATE_A_PING", "GATE_B_SNAPSHOT", "GATE_C_DEBUG_TO_UI",
                  "GATE_F_DEBUG_STOP_TO_UI", "GATE_G_NEW_SESSION_DEBUG"]
    core_pass = all(results.get(g) for g in core_gates)
    print(f"\nCALLBACK_DEBUG_AUTOMATION_CORE = {'PASS' if core_pass else 'FAIL'}")
    sys.exit(0 if core_pass else 1)

if __name__ == "__main__":
    main()
