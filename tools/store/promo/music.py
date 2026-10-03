#!/usr/bin/env python3
"""The music of the promo video: 15 s of a light plucked arpeggio over a soft pad, synthesized here so that no one else's
licence comes with it.

    python3 tools/store/promo/music.py <out.wav>

96 beats a minute, a bar of 2.5 s — the scenes of the video change on its bar lines (promo.html, SCENES). Am · F · C · G ·
F · C: the first bar is sparse while Live finds the note, the last one rings out under the end card. A soft swell of noise
leads into every new scene."""
import sys
import wave

import numpy as np

RATE = 44_100
LENGTH = 15.0
BEAT = 60 / 96
BAR = 4 * BEAT
# (bass, pad, arpeggio) per bar, MIDI
BARS = [
    (45, (57, 60, 64), (69, 72, 76, 81)),  # Am
    (41, (53, 57, 60), (65, 69, 72, 77)),  # F
    (48, (55, 60, 64), (64, 67, 72, 76)),  # C
    (43, (55, 59, 62), (62, 67, 71, 74)),  # G
    (41, (53, 57, 60), (65, 69, 72, 77)),  # F
    (48, (55, 60, 64), (67, 72, 74, 76)),  # C add9, under the end card
]
ARP = [0, 2, 1, 3, 2, 1, 3, 2]  # the order of the four notes over the eight eighths of a bar
SWELLS = [5.0, 7.5, 10.0, 12.5]  # scene changes


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def at(t):
    return int(round(t * RATE))


def pluck(midi, length=1.6, gain=1.0):
    t = np.arange(at(length)) / RATE
    f = hz(midi)
    out = np.zeros_like(t)
    for k in range(1, 9):
        if f * k > RATE / 2.2:
            break
        out += np.sin(2 * np.pi * f * k * t + k) / k ** 1.6 * np.exp(-t * (2.2 + 1.8 * k))
    attack = np.minimum(t / 0.004, 1)
    return out * attack * gain


def bell(midi, length=2.4, gain=1.0):
    t = np.arange(at(length)) / RATE
    f = hz(midi)
    out = np.sin(2 * np.pi * f * t) * np.exp(-t * 1.6) + 0.35 * np.sin(2 * np.pi * f * 2.76 * t) * np.exp(-t * 4)
    return out * np.minimum(t / 0.003, 1) * gain


def pad(midis, length, gain=1.0):
    t = np.arange(at(length)) / RATE
    out = np.zeros_like(t)
    for m in midis:
        for detune in (-5, 0, 5):
            f = hz(m) * 2 ** (detune / 1200)
            out += np.sin(2 * np.pi * f * t) + 0.12 * np.sin(4 * np.pi * f * t)
    env = np.minimum(t / 0.7, 1) * np.minimum((length - t) / 0.9, 1).clip(0, 1)
    return out / (3 * len(midis)) * env * gain


def bass(midi, length, gain=1.0):
    t = np.arange(at(length)) / RATE
    f = hz(midi)
    out = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)
    return out * np.minimum(t / 0.02, 1) * np.exp(-t * 0.9) * gain


def swell(rng, length=0.7, gain=1.0):
    n = at(length)
    noise = rng.standard_normal(n)
    spectrum = np.fft.rfft(noise)
    freqs = np.fft.rfftfreq(n, 1 / RATE)
    spectrum *= np.exp(-((np.log2(np.maximum(freqs, 1) / 2500)) ** 2) / 1.2)  # a soft band around 2.5 kHz
    out = np.fft.irfft(spectrum, n)
    out /= np.abs(out).max()
    t = np.arange(n) / RATE
    return out * (t / length) ** 2.5 * np.minimum((length - t) / 0.05, 1) * gain


def add(track, sound, start, pan=0.0):
    i = at(start)
    j = min(len(track), i + len(sound))
    if j <= i:
        return
    left, right = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
    track[i:j, 0] += sound[:j - i] * left
    track[i:j, 1] += sound[:j - i] * right


def reverb(track, rng, seconds=2.2, mix=0.28):
    n = at(seconds)
    t = np.arange(n) / RATE
    out = np.empty_like(track)
    size = len(track) + n
    for ch in range(2):
        ir = rng.standard_normal(n) * np.exp(-t / 0.55)
        ir[: at(0.012)] = 0  # pre-delay
        ir /= np.sqrt((ir ** 2).sum())
        wet = np.fft.irfft(np.fft.rfft(track[:, ch], size) * np.fft.rfft(ir, size), size)[: len(track)]
        out[:, ch] = track[:, ch] * (1 - mix) + wet * mix * 2.2
    return out


def main():
    rng = np.random.default_rng(3)
    track = np.zeros((at(LENGTH + 2.5), 2))
    for b, (low, chord, arp) in enumerate(BARS):
        start = b * BAR
        last = b == len(BARS) - 1
        add(track, pad(chord, BAR + (2.5 if last else 0.9), 0.30), start)
        add(track, bass(low, BAR + (1.5 if last else 0.3), 0.32), start)
        for i, step in enumerate(ARP):
            if b == 0 and i % 2:  # the first bar is sparse
                continue
            if last and i > 4:  # the last bar thins out under the end card
                break
            accent = 1.0 if i % 4 == 0 else 0.72
            add(track, pluck(arp[step], gain=0.30 * accent), start + i * BEAT / 2, pan=(-0.35 if i % 2 else 0.35))
    for t in SWELLS:
        add(track, swell(rng, gain=0.06), t - 0.7)
        add(track, bell(BARS[int(t // BAR)][2][3] + 12, gain=0.07), t, pan=0.2)
    add(track, bell(84, 3.0, 0.09), 12.5)  # the end card's chime: C6
    add(track, bell(91, 3.0, 0.05), 12.5 + BEAT / 2, pan=-0.3)  # G6
    track = reverb(track, rng)[: at(LENGTH)]
    t = np.arange(len(track)) / RATE
    track *= np.minimum(t / 0.03, 1)[:, None] * np.clip((LENGTH - t) / 1.6, 0, 1)[:, None] ** 1.5
    track *= 10 ** (-1 / 20) / np.abs(track).max()
    pcm = (track * 32767).astype('<i2')
    with wave.open(sys.argv[1], 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


if __name__ == '__main__':
    main()
