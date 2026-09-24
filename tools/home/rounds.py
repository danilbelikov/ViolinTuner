#!/usr/bin/env python3
"""Every thing of the home after purchase, on the emulator (spec 3.29, stage 77): does it stand where it should, is it
drawn, does it move.

   python3 tools/home/rounds.py <out dir> [rounds, e.g. 0-8] [houses, e.g. rent,wood] [--no-outside] [serial]

The rounds come from the catalogue (docs/design/project/home3/project/home-catalog.js): round k puts the k-th thing of
every place in it, so nine rounds show all 107. For each round and house the app's database on the EMULATOR is given
every thing and the wooden house bought and the round's choices, the app opens the home on the whole screen — the room,
then the home from outside — and each is shot twice 0.6 s apart: <out>/r<k>.<house>.<room|out>.png and beside it
.moved.png, the first frame dimmed with red cells where the two differ. The database is changed for good: pull a copy
first if it matters. Never a phone: the owner's one lives with real data (CLAUDE.md), and the script refuses any serial
that is not an emulator's.
"""
import json, os, re, sqlite3, struct, subprocess, sys, time, zlib

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ARGS = [a for a in sys.argv[1:] if not a.startswith('--')]
OUT = os.path.abspath(ARGS[0]) if ARGS else sys.exit(__doc__)
SERIAL = ARGS[3] if len(ARGS) > 3 else 'emulator-5554'
if not SERIAL.startswith('emulator-'):
    sys.exit(f'{SERIAL} is not an emulator: this script rewrites the database of the app')
ADB = [os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'), '-s', SERIAL]
PKG = 'com.violinjourney.app.debug'
DB = os.path.join(OUT, 'db')
os.makedirs(DB, exist_ok=True)
PLAN = json.loads(subprocess.run(['node', '-e', """
global.window = {};
require(process.argv[1]);
const C = window.buildHomeCatalog({ createElement: () => null });
const bySlot = {};
for (const it of C.ITEMS) (bySlot[it.s] = bySlot[it.s] || []).push(it.id);
const n = Math.max(...Object.values(bySlot).map(a => a.length));
const rounds = Array.from({ length: n }, (_, k) => Object.fromEntries(Object.entries(bySlot).map(([s, a]) => [s, a[k % a.length]])));
console.log(JSON.stringify({ rounds, items: C.ITEMS.map(i => i.id) }));
""", os.path.join(ROOT, 'docs/design/project/home3/project/home-catalog.js')], capture_output=True, text=True, check=True).stdout)


def adb(*args, **kw):
    return subprocess.run(ADB + list(args), capture_output=True, **kw)


def pull():
    adb('shell', 'am', 'force-stop', PKG)
    time.sleep(0.8)
    for f in ('violin.db', 'violin.db-wal', 'violin.db-shm'):
        data = adb('exec-out', 'run-as', PKG, 'cat', f'databases/{f}').stdout
        path = os.path.join(DB, f)
        if data:
            open(path, 'wb').write(data)
        elif os.path.exists(path):
            os.remove(path)


def push(choices, house):
    db = sqlite3.connect(os.path.join(DB, 'violin.db'))
    c = db.cursor()
    for item in PLAN['items']:
        c.execute("insert or ignore into home_purchases (id, kind, price, boughtAtEpochMs) values (?, 'ITEM', 0, 1790000000000)", (item,))
    c.execute("insert or ignore into home_purchases (id, kind, price, boughtAtEpochMs) values ('wood', 'HOUSE', 0, 1790000000000)")
    c.execute('delete from home_choices')
    for slot, item in choices.items():
        c.execute('insert into home_choices (slot, itemId) values (?, ?)', (slot, item))
    c.execute("insert into home_choices (slot, itemId) values ('@house', ?)", (house,))
    db.commit()
    c.execute('pragma wal_checkpoint(TRUNCATE)')
    c.execute('pragma journal_mode=DELETE')
    db.close()
    adb('push', os.path.join(DB, 'violin.db'), '/data/local/tmp/violin.db')
    adb('shell', f"run-as {PKG} sh -c 'cp /data/local/tmp/violin.db databases/violin.db && rm -f databases/violin.db-wal databases/violin.db-shm'")
    adb('shell', 'rm', '/data/local/tmp/violin.db')


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml')
    xml = adb('shell', 'cat', '/sdcard/ui.xml').stdout.decode('utf-8', 'replace')
    out = []
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        t = re.search(r' text="([^"]*)"', n).group(1)
        d = re.search(r'content-desc="([^"]*)"', n).group(1)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        out.append((t, d, tuple(map(int, b.groups()))))
    return out


def tap(want, tries=10):
    for _ in range(tries):
        for t, d, (x0, y0, x1, y1) in nodes():
            if want in t or want in d:
                adb('shell', 'input', 'tap', str((x0 + x1) // 2), str((y0 + y1) // 2))
                return True
        time.sleep(1)
    print('   not found:', want)
    return False


def raw():
    data = adb('exec-out', 'screencap').stdout
    w, h = struct.unpack('<II', data[:8])
    return w, h, data[len(data) - w * h * 4:]


def png(path, w, h, rgb_rows):
    raw_rows = b''.join(b'\x00' + row for row in rgb_rows)
    def chunk(kind, body):
        return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw_rows, 6)) + chunk(b'IEND', b'')
    open(path, 'wb').write(data)


def shoot(name):
    """Two frames 0.6 s apart: the first as a picture, and where they differ as a map (half size)."""
    w, h, a = raw()
    time.sleep(0.6)
    _, _, b = raw()
    step, cell = 2, 16
    rows = []
    moved = set()
    for cy in range(0, h, cell):
        for cx in range(0, w, cell):
            diff = 0
            for y in range(cy, min(h, cy + cell), 4):
                base = (y * w + cx) * 4
                for x in range(0, min(cell, w - cx), 4):
                    i = base + x * 4
                    if abs(a[i] - b[i]) + abs(a[i + 1] - b[i + 1]) + abs(a[i + 2] - b[i + 2]) > 30:
                        diff += 1
            if diff >= 2:
                moved.add((cx // cell, cy // cell))
    for y in range(0, h, step):
        row = bytearray()
        for x in range(0, w, step):
            i = (y * w + x) * 4
            r, g, bl = a[i], a[i + 1], a[i + 2]
            if (x // cell, y // cell) in moved:
                row += bytes((255, int(g * 0.3), int(bl * 0.3)))
            else:
                row += bytes((r * 2 // 3, g * 2 // 3, bl * 2 // 3))
        rows.append(bytes(row))
    png(os.path.join(OUT, f'{name}.moved.png'), len(rows[0]) // 3, len(rows), rows)
    rows = [bytes(a[(y * w + x) * 4 + c] for x in range(0, w, step) for c in range(3)) for y in range(0, h, step)]
    png(os.path.join(OUT, f'{name}.png'), w // step, h // step, rows)
    return len(moved)


def view(k, house, outside):
    """From «Занятия» to the home on the whole screen, the side chosen on the home's own screen: on the whole screen
    a living picture keeps uiautomator from ever seeing the screen idle."""
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-W', '-n', f'{PKG}/.MainActivity')
    time.sleep(2.5)
    if not tap('Дом · '):
        return
    time.sleep(3)
    if outside:
        if not tap('Снаружи'):
            return
        time.sleep(1.5)
    if not tap('Дом снаружи: ' if outside else 'Комната: '):
        return
    time.sleep(4.5)
    side = 'out' if outside else 'room'
    print(f'   {side} moved cells:', shoot(f'r{k}.{house}.{side}'))


def run(k, house, outside=True):
    choices = PLAN['rounds'][k]
    print(f'round {k} · {house}')
    pull()
    push(choices, house)
    view(k, house, False)
    if outside:
        view(k, house, True)


if __name__ == '__main__':
    spec = ARGS[1] if len(ARGS) > 1 else f"0-{len(PLAN['rounds']) - 1}"
    lo, hi = (map(int, spec.split('-')) if '-' in spec else (int(spec), int(spec)))
    houses = (ARGS[2] if len(ARGS) > 2 else 'rent,wood').split(',')
    for k in range(lo, hi + 1):
        for house in houses:
            run(k, house, outside='--no-outside' not in sys.argv)
