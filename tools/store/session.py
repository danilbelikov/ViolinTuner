#!/usr/bin/env python3
"""Moves the running practice back, so that «Закончить занятие» saves a session of the length the screenshots want.

    python3 tools/store/session.py [minutes]

Start a practice in the app first. The sheet «Закончить» never lets a session be longer than the time gone by, so the
start is moved in the DataStore file itself (keys `practice_started_at`, `practice_last_sound_at`); the app is stopped
for it and started again. Only the emulator and only com.violinjourney.app.debug."""
import os
import subprocess
import sys
import tempfile

ADB = os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
SERIAL = 'emulator-5554'
PKG = 'com.violinjourney.app.debug'
PREFS = f'/data/data/{PKG}/files/datastore/user_settings.preferences_pb'
KEYS = {'practice_started_at', 'practice_last_sound_at'}


def varint(data, i):
    shift = value = 0
    while True:
        b = data[i]
        value |= (b & 0x7F) << shift
        i += 1
        if b < 0x80:
            return value, i
        shift += 7


def encode(value):
    value &= (1 << 64) - 1
    out = bytearray()
    while True:
        b = value & 0x7F
        value >>= 7
        if value:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def fields(data):
    """(field number, wire type, raw value bytes or int) of one message level."""
    i, out = 0, []
    while i < len(data):
        tag, i = varint(data, i)
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value, i = varint(data, i)
        elif wire == 2:
            size, i = varint(data, i)
            value, i = data[i:i + size], i + size
        elif wire == 1:
            value, i = data[i:i + 8], i + 8
        elif wire == 5:
            value, i = data[i:i + 4], i + 4
        else:
            raise ValueError(f'wire type {wire}')
        out.append((number, wire, value))
    return out


def write(items):
    out = bytearray()
    for number, wire, value in items:
        out += encode(number << 3 | wire)
        if wire == 0:
            out += encode(value)
        elif wire == 2:
            out += encode(len(value)) + value
        else:
            out += value
    return bytes(out)


def shift(data, delta_ms):
    """Preferences { map<string, Value> preferences = 1 }; Value.long = 4."""
    entries = []
    for number, wire, entry in fields(data):
        parts = fields(entry)
        key = next(v for n, _, v in parts if n == 1).decode()
        if key in KEYS:
            parts = [(n, w, write([(m, x, val - delta_ms if m == 4 else val) for m, x, val in fields(v)]) if n == 2 else v)
                     for n, w, v in parts]
            print(key, 'moved back')
        entries.append((number, wire, write(parts)))
    return write(entries)


def main():
    minutes = int(sys.argv[1]) if len(sys.argv) > 1 else 45
    run = lambda *a, **kw: subprocess.run([ADB, '-s', SERIAL, *a], check=True, **kw)
    run('shell', 'am', 'force-stop', PKG)
    data = run('exec-out', 'run-as', PKG, 'cat', PREFS, capture_output=True).stdout
    with tempfile.NamedTemporaryFile(delete=False) as f:
        f.write(shift(data, minutes * 60_000))
    run('push', f.name, '/data/local/tmp/prefs.pb', capture_output=True)
    run('shell', 'chmod', '644', '/data/local/tmp/prefs.pb')
    run('shell', f'run-as {PKG} cp /data/local/tmp/prefs.pb {PREFS}')
    run('shell', 'am', 'start', '-n', f'{PKG}/com.violinjourney.app.MainActivity', capture_output=True)


if __name__ == '__main__':
    main()
