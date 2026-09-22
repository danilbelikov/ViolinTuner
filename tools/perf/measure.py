# Measures the app as it stands on the screen that is open now: frames a second, the main thread's
# recording of a frame (DrawStart → SyncQueued), the render thread's part (SyncStart → FrameCompleted)
# and CPU by thread. Used for the plan docs/plan-performance.md.
#
#   python3 tools/perf/measure.py <label> [seconds] [serial]
#
# The serial is the emulator's by default. The owner's phone may be connected to this machine as
# well: it is measured only when its serial is given — and only with the app the owner put there.
# The emulator draws in software (SwiftShader), so its render thread is slower than a phone's many
# times over; the main thread and the proportions between screens are what compare honestly.
import os
import re
import subprocess
import sys
import time

label = sys.argv[1]
secs = int(sys.argv[2]) if len(sys.argv) > 2 else 10
serial = sys.argv[3] if len(sys.argv) > 3 else 'emulator-5554'
ADB = [os.path.expanduser('~/Library/Android/sdk/platform-tools/adb'), '-s', serial]
PKG = 'com.example.violintuner'


def sh(*args):
    return subprocess.run(ADB + ['shell', *args], capture_output=True, text=True).stdout


pid = sh('pidof', PKG).strip()
if not pid:
    sys.exit(f'{PKG} is not running on {serial}')
sh('dumpsys', 'gfxinfo', PKG, 'reset')
started = time.time()
# CPU by thread, a sample a second; the first sample counts from the start of the process and is dropped
top = sh('top', '-b', '-H', '-d', '1', '-n', str(secs + 1), '-p', pid)
elapsed = time.time() - started
info = sh('dumpsys', 'gfxinfo', PKG, 'framestats')

samples = top.split('Threads:')[2:]
cpu = {}
for block in samples:
    for line in block.splitlines():
        m = re.match(r'\s*(\d+)\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S\s+([\d.]+)\s+[\d.]+\s+\S+\s+(.+?)\s+' + re.escape(PKG[:15]), line)
        if m:
            name = 'main' if m.group(1) == pid else m.group(3).strip()
            cpu.setdefault(name, []).append(float(m.group(2)))
count = max(1, len(samples))
per = {k: sum(v) / count for k, v in cpu.items()}
total = sum(per.values())
others = sorted(((v, k) for k, v in per.items() if k not in ('main', 'RenderThread') and v >= 1.0), reverse=True)[:3]

rendered = re.search(r'Total frames rendered: (\d+)', info)
janky = re.search(r'Janky frames: (\d+) \(([\d.]+)%\)', info)
frames = int(rendered.group(1)) if rendered else 0

rows = []
for block in info.split('---PROFILEDATA---')[1:]:
    lines = [line for line in block.strip().splitlines() if line]
    if lines and lines[0].startswith('Flags,'):
        header = lines[0].rstrip(',').split(',')
        for line in lines[1:]:
            values = line.rstrip(',').split(',')
            if len(values) >= len(header) and values[0] == '0':
                rows.append(dict(zip(header, map(int, values[:len(header)]))))


def span(a, b):
    return [(r[b] - r[a]) / 1e6 for r in rows if r.get(a, 0) > 0 and r.get(b, 0) > 0]


def q(xs, p):
    if not xs:
        return float('nan')
    xs = sorted(xs)
    return xs[min(len(xs) - 1, int(p * len(xs)))]


draw = span('DrawStart', 'SyncQueued')
anim = span('AnimationStart', 'PerformTraversalsStart')
render = span('SyncStart', 'FrameCompleted')
print(f"{label:30s} {frames / elapsed:5.1f} fps | запись p50 {q(draw, .5):6.2f} p90 {q(draw, .9):6.2f} мс | анимация p50 {q(anim, .5):5.2f} | "
      f"рендер p50 {q(render, .5):6.2f} p90 {q(render, .9):6.2f} мс | CPU {total:5.1f}% main {per.get('main', 0.0):5.1f}% RenderThread {per.get('RenderThread', 0.0):5.1f}%"
      + (' | ' + ', '.join(f'{k} {v:.0f}%' for v, k in others) if others else '')
      + (f' | janky {janky.group(2)}%' if janky else ''))
