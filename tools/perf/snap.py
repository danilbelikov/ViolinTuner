# Takes the screen of the emulator as it is and compares it with an earlier one: the check that an
# optimisation of the drawing changed nothing that can be seen (docs/plan-performance.md). With
# «убрать анимации» on (animator duration scale 0) the living pictures stand still, so the same
# screen gives the same picture.
#
#   python3 tools/perf/snap.py take <file.raw> [serial]
#   python3 tools/perf/snap.py compare <before.raw> <after.raw>
#   python3 tools/perf/snap.py compare-region <before.raw> <after.raw> x y w h [--search 40]
#
# The status bar (the clock) and the navigation bar are left out of the comparison.
#
# compare-region compares one region of the two screens — the ring, the note, the word and the cents of Live
# (spec 3.36, «Неизменное») — where the controls around it moved it up or down: the rectangle x y w h of the
# first screen, in its pixels (dp × density: 2.625 on the Pixel 7), is looked for in the second one shifted by
# −search … +search pixels vertically. It prints the shift that fits best (positive — lower on the second
# screen) and the share of the pixels of the region that differ there.
#
# The shifts are ranked by the rows' sums over vertical bands of the region, not by rows equal byte for byte:
# a gradient is dithered on the grid of the screen, not of the ring, so where the ring moved no row of it is
# ever equal again, while the sums of a band hardly feel a dither of ±1. The best few are then compared pixel by
# pixel with the threshold SAME, under which the dither stays.
import os
import struct
import subprocess
import sys

TOP, BOTTOM = 0.06, 0.05  # shares of the height left out: the status bar and the navigation bar
SAME = 12  # the largest difference of a pixel (the sum over R, G and B) still taken for the same
SEARCH = 40  # pixels up and down that compare-region looks through by default
CANDIDATES = 3  # shifts that compare-region compares pixel by pixel, the ones whose bands match best
BANDS = 16  # vertical bands of the region whose sums, row by row, rank the shifts


def take(path, serial):
    adb = [os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'), '-s', serial]
    raw = subprocess.run(adb + ['exec-out', 'screencap'], capture_output=True).stdout
    open(path, 'wb').write(raw)


def pixels(path):
    raw = open(path, 'rb').read()
    w, h = struct.unpack('<II', raw[:8])
    return w, h, raw[len(raw) - w * h * 4:]


def differing(ra, rb):
    """How many pixels of two rows (RGBA bytes) differ, and by how much at most."""
    differ = 0
    worst = 0
    for x in range(0, len(ra), 4):
        d = abs(ra[x] - rb[x]) + abs(ra[x + 1] - rb[x + 1]) + abs(ra[x + 2] - rb[x + 2])
        if d > SAME:
            differ += 1
        worst = max(worst, d)
    return differ, worst


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
        d, m = differing(ra, rb)
        differ += d
        worst = max(worst, m)
    share = 100 * differ / (w * (y1 - y0))
    return f'{share:.3f} % of the pixels differ (the largest difference {worst} of 765)'


def region_rows(data, width, x, y, w, h):
    """The rows of the rectangle x y w h of a screen `width` pixels wide, as byte strings."""
    return [data[(row * width + x) * 4:(row * width + x + w) * 4] for row in range(y, y + h)]


def band_sums(rows, w):
    """Every row cut into BANDS vertical bands (at least one pixel each) and the sum of the bytes of each: a dither of ±1 on a
    pixel hardly moves them, a region that moved up or down moves them a lot."""
    bands = min(BANDS, w)
    edges = [w * i // bands * 4 for i in range(bands + 1)]
    return [[sum(row[edges[i]:edges[i + 1]]) for i in range(bands)] for row in rows]


def compare_region(a, b, x, y, w, h, search):
    wa, ha, pa = pixels(a)
    wb, hb, pb = pixels(b)
    if wa != wb:
        return f'widths differ: {wa} and {wb}'
    if x < 0 or y < 0 or w <= 0 or h <= 0 or x + w > wa or y + h > ha:
        return f'the region {x} {y} {w} {h} is not inside the first screen ({wa}×{ha})'
    shifts = [dy for dy in range(-search, search + 1) if 0 <= y + dy and y + dy + h <= hb]
    if not shifts:
        return 'no shift keeps the region inside the second screen'
    before = region_rows(pa, wa, x, y, w, h)
    # first the cheap measure, deaf to a dither: how far the sums of the bands of every row are from the second screen's at
    # each shift — the rows of the second screen over the whole search are summed once
    top = shifts[0] + y
    sums_before = band_sums(before, w)
    sums_after = band_sums(region_rows(pb, wb, x, top, w, shifts[-1] + y + h - top), w)
    distances = []
    for dy in shifts:
        at = y + dy - top
        distance = sum(abs(p - q) for ra, rb in zip(sums_before, sums_after[at:at + h]) for p, q in zip(ra, rb))
        distances.append((distance, abs(dy), dy))
    distances.sort()
    # then pixel by pixel, only for the shifts whose bands match best
    results = []
    for _, _, dy in distances[:CANDIDATES]:
        after = region_rows(pb, wb, x, y + dy, w, h)
        equal = sum(1 for ra, rb in zip(before, after) if ra == rb)
        differ = 0
        worst = 0
        for ra, rb in zip(before, after):
            if ra == rb:
                continue
            d, m = differing(ra, rb)
            differ += d
            worst = max(worst, m)
        results.append((differ, abs(dy), dy, worst, equal))
    differ, _, dy, worst, equal = min(results)
    share = 100 * differ / (w * h)
    others = ', '.join(f'{r[2]:+d} px: {100 * r[0] / (w * h):.3f} %' for r in sorted(results) if r[2] != dy)
    line = (f'best shift {dy:+d} px (the region stands that much lower on the second screen): {share:.3f} % of its pixels differ '
            f'(the largest difference {worst} of 765), {equal} of {h} rows equal')
    return line + (f'; next: {others}' if others else '')


def usage():
    return ('usage: snap.py take <file.raw> [serial] | compare <before.raw> <after.raw> | '
            'compare-region <before.raw> <after.raw> x y w h [--search 40]')


if __name__ == '__main__':
    command = sys.argv[1] if len(sys.argv) > 1 else ''
    if command == 'take':
        take(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else 'emulator-5554')
    elif command == 'compare':
        print(compare(sys.argv[2], sys.argv[3]))
    elif command == 'compare-region':
        args = sys.argv[2:]
        search = SEARCH
        if '--search' in args:
            at = args.index('--search')
            search = int(args[at + 1])
            del args[at:at + 2]
        if len(args) != 6:
            sys.exit(usage())
        print(compare_region(args[0], args[1], *(int(v) for v in args[2:6]), search))
    else:
        sys.exit(usage())
