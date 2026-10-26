#!/usr/bin/env python3
"""Pull the app-private Room DB (db + wal + shm) via `run-as cat` (binary-safe)
and query it with the PC's sqlite3. Read-only: never modifies the device DB.

Usage: python db_inspect.py [output_dir]
"""
import os
import sqlite3
import subprocess
import sys

ADB = r"C:\android\platform-tools\adb.exe"
PKG = "com.androidagent.app"
DBREL = "databases/topic_sessions.db"


def run(args):
    return subprocess.run(args, capture_output=True)


def pull_bytes(rel):
    # Base64-encode on-device so the binary survives the adb text pipe
    # (no newline-stripping / CRLF / null-byte corruption).
    r = run([ADB, "shell", "run-as", PKG, "base64", rel])
    import base64
    b64 = r.stdout.decode("ascii", "ignore").replace("\n", "").replace("\r", "")
    try:
        return base64.b64decode(b64, validate=False)
    except Exception as e:
        print("base64 decode failed for %s: %s (len=%d)" % (rel, e, len(b64)))
        return r.stdout


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.environ["TEMP"], "db_i2")
    os.makedirs(out, exist_ok=True)
    for rel, name in [(DBREL, "ts.db"), (DBREL + "-wal", "ts.db-wal"), (DBREL + "-shm", "ts.db-shm")]:
        data = pull_bytes(rel)
        path = os.path.join(out, name)
        with open(path, "wb") as f:
            f.write(data)
        print("%-10s %d bytes" % (name, len(data)))
    db = os.path.join(out, "ts.db")
    wal = os.path.join(out, "ts.db-wal")
    if not os.path.exists(wal) or os.path.getsize(wal) == 0:
        print("WARNING: WAL empty/missing; main DB may not have the live data")
    con = sqlite3.connect(db)
    cur = con.cursor()
    print("=== tables ===")
    for r in cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
        print("  ", r[0])
    print("DATABASE_VERSION =", cur.execute("PRAGMA user_version").fetchone()[0])
    print("=== sessions (by last_used_at desc) ===")
    for r in cur.execute("SELECT id, created_at, last_used_at FROM topic_sessions ORDER BY last_used_at DESC"):
        print("  session=%s created_at=%s last_used_at=%s" % (r[0], r[1], r[2]))
    print("SESSION_COUNT =", cur.execute("SELECT COUNT(*) FROM topic_sessions").fetchone()[0])
    print("=== messages per session ===")
    for r in cur.execute("SELECT topic_session_id, COUNT(*), MIN(sequence), MAX(sequence) FROM messages GROUP BY topic_session_id"):
        print("  %s count=%d seq %d..%d" % (r[0], r[1], r[2], r[3]))
    print("MESSAGE_COUNT =", cur.execute("SELECT COUNT(*) FROM messages").fetchone()[0])
    print("=== message types ===")
    for r in cur.execute("SELECT type, COUNT(*) FROM messages GROUP BY type"):
        print("  %s -> %d" % (r[0], r[1]))
    print("=== message type x session ===")
    for r in cur.execute("SELECT topic_session_id, type, sequence FROM messages ORDER BY topic_session_id, sequence"):
        print("  %s seq=%d type=%s" % (r[0], r[2], r[1]))
    con.close()


if __name__ == "__main__":
    main()
