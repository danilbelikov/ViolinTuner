#!/usr/bin/env python3
"""Seeds a running practice into a pulled DataStore file (user_settings.preferences_pb) for «Занятие не закончено» (stage 106).

    python3 seed_forgotten.py <file> sounded|silent|answered|expired [running_minutes] [quiet_minutes]

Keeps every other key (settings, onboarding) as it is; rewrites practice_started_at, practice_last_sound_at and
practice_last_mark_answer. Times are «now − N minutes» of this Mac's clock — the emulator's clock must be close to it.
PreferenceMap: repeated field 1 = entry {1: key (string), 2: Value}; Value.boolean = field 1 (varint), Value.long = field 4 (varint).
"""
import sys
import time

KEYS = {b'practice_started_at', b'practice_last_sound_at', b'practice_last_mark_answer'}


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        out.append(b | (0x80 if n else 0))
        if not n:
            return bytes(out)


def read_varint(data, i):
    shift = result = 0
    while True:
        b = data[i]
        i += 1
        result |= (b & 0x7F) << shift
        shift += 7
        if not b & 0x80:
            return result, i


def entries(data):
    """The raw map entries (field 1 of PreferenceMap) with their keys."""
    i = 0
    while i < len(data):
        tag, i = read_varint(data, i)
        length, i = read_varint(data, i)
        body = data[i:i + length]
        i += length
        assert tag == 0x0A, f'unexpected tag {tag}'
        _, j = read_varint(body, 0)  # key tag 0x0A
        key_len, j = read_varint(body, j)
        yield body[j:j + key_len], body


def entry(key, value):
    body = b'\x0a' + varint(len(key)) + key + b'\x12' + varint(len(value)) + value
    return b'\x0a' + varint(len(body)) + body


def long_value(n):
    return b'\x20' + varint(n)


def bool_value(b):
    return b'\x08' + (b'\x01' if b else b'\x00')


def main():
    path, kind = sys.argv[1], sys.argv[2]
    running = int(sys.argv[3]) if len(sys.argv) > 3 else 192       # «Идёт 3 ч 12 мин»
    quiet = int(sys.argv[4]) if len(sys.argv) > 4 else 128         # the last mark 2 h 8 min ago → «… · 1 ч 4 мин»
    if kind == 'expired':
        running = max(running, 13 * 60)
    now = int(time.time() * 1000)
    data = open(path, 'rb').read()
    kept = b''.join(raw_entry(body) for k, body in entries(data) if k not in KEYS)
    out = kept + entry(b'practice_started_at', long_value(now - running * 60_000))
    if kind != 'silent':
        out += entry(b'practice_last_sound_at', long_value(now - quiet * 60_000))
    if kind == 'answered':
        out += entry(b'practice_last_mark_answer', bool_value(True))
    open(path, 'wb').write(out)
    print(f'{kind}: started {running} min ago' + ('' if kind == 'silent' else f', last mark {quiet} min ago'))


def raw_entry(body):
    return b'\x0a' + varint(len(body)) + body


if __name__ == '__main__':
    main()
