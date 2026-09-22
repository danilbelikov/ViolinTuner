# Takes the screen of the emulator as it is and compares it with an earlier one: the check that an
# optimisation of the drawing changed nothing that can be seen (docs/plan-performance.md). With
# «убрать анимации» on (animator duration scale 0) the living pictures stand still, so the same
# screen gives the same picture.
#
#   python3 tools/perf/snap.py take <file.raw> [serial]
#   python3 tools/perf/snap.py compare <before.raw> <after.raw>
#
# The status bar (the clock) and the navigation bar are left out of the comparison.
import os
import struct
import subprocess
import sys

TOP, BOTTOM = 0.06, 0.05  # shares of the height left out: the status bar and the navigation bar


def take(path, serial):
    adb = [os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'), '-s', serial]
    raw = subprocess.run(adb + ['exec-out', 'screencap'], capture_output=True).stdout
    open(path, 'wb').write(raw)


def pixels(path):
    raw = open(path, 'rb').read()
    w, h = struct.unpack('<II', raw[:8])
    return w, h, raw[len(raw) - w * h * 4:]


def compare(a, b):
    w, h, pa = pixels(a)
    w2, h2, pb = pixels(b)
    if (w, h) != (w2, h2):
        return f'sizes differ: {w}×{h} and {w2}×{h2}'
    y0, y1 = int(h * TOP), int(h * (1 - BOTTOM))
    differ = 0
    worst = 0
    for y in range(y0, y1):
        row = y * w * 4
        ra, rb = pa[row:row + w * 4], pb[row:row + w * 4]
        if ra == rb:
            continue
        for x in range(0, w * 4, 4):
            d = abs(ra[x] - rb[x]) + abs(ra[x + 1] - rb[x + 1]) + abs(ra[x + 2] - rb[x + 2])
            if d > 12:
                differ += 1
            worst = max(worst, d)
    share = 100 * differ / (w * (y1 - y0))
    return f'{share:.3f} % of the pixels differ (the largest difference {worst} of 765)'


if __name__ == '__main__':
    if sys.argv[1] == 'take':
        take(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else 'emulator-5554')
    else:
        print(compare(sys.argv[2], sys.argv[3]))
