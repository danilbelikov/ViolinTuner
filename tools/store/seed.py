#!/usr/bin/env python3
"""Fills the debug app on the emulator with a believable player for the store screenshots.

    python3 tools/store/seed.py ru|en

Only the emulator and only com.violinjourney.app.debug: the app's database is replaced by a copy with
~70 days of practice ending today, the road up to Vienna, a furnished room and a repertoire in the
chosen language. The takes of the first piece are cloned from a session recorded on Live of a
-PfakePitch=true build (its analysis is real, of the scripted notes), each given a synthesized sound. Go through the onboarding once before running this:
Room creates the database on the first start."""
import datetime as dt
import os
import random
import sqlite3
import subprocess
import sys
import math
import struct
import tempfile
import uuid
import wave

ADB = os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
SERIAL = 'emulator-5554'
PKG = 'com.violinjourney.app.debug'
DB = f'/data/data/{PKG}/databases/violin.db'
SESSIONS = f'/data/data/{PKG}/files/sessions'

PIECES = {
    'ru': [
        ('Концерт ля минор, ч. 1', 'А. Вивальди', 'A', 'NATURAL', 'MINOR', 'IN_REPERTOIRE', 'Держать ровный штрих в пассажах, не спешить к концу фразы.'),
        ('Чардаш', 'В. Монти', 'D', 'NATURAL', 'MINOR', 'LEARNING', 'Медленная часть — вибрато шире; флажолеты проверить отдельно.'),
        ('Юмореска', 'А. Дворжак', 'G', 'FLAT', 'MAJOR', 'LEARNING', ''),
        ('Мелодия', 'П. Чайковский', 'E', 'FLAT', 'MAJOR', 'READING', ''),
        ('Концерт № 2, ч. 3', 'Ф. Зейтц', 'G', 'NATURAL', 'MAJOR', 'IN_REPERTOIRE', ''),
    ],
    'en': [
        ('Concerto in A minor, 1st mvt', 'A. Vivaldi', 'A', 'NATURAL', 'MINOR', 'IN_REPERTOIRE', 'Keep the bow even through the passages; do not rush the end of the phrase.'),
        ('Csárdás', 'V. Monti', 'D', 'NATURAL', 'MINOR', 'LEARNING', 'Slow part — wider vibrato; check the harmonics separately.'),
        ('Humoresque', 'A. Dvořák', 'G', 'FLAT', 'MAJOR', 'LEARNING', ''),
        ('Mélodie', 'P. Tchaikovsky', 'E', 'FLAT', 'MAJOR', 'READING', ''),
        ('Concerto No. 2, 3rd mvt', 'F. Seitz', 'G', 'NATURAL', 'MAJOR', 'IN_REPERTOIRE', ''),
    ],
}
# (tonic, accidental, kind, octaves); titles as ScaleTexts.title writes them
SCALES = [('G', 'NATURAL', 'MAJOR', 3, 'G-dur'), ('A', 'NATURAL', 'MELODIC_MINOR', 2, 'a-moll'), ('D', 'NATURAL', 'MAJOR', 2, 'D-dur')]
SCALE_WORDS = {'ru': ('{} · {} октавы', ' мелодический'), 'en': ('{} · {} octaves', ' melodic')}
ETUDES = {'ru': [('Этюд № 2', 'Р. Крейцер'), ('Этюд № 8', 'Ф. Вольфарт')], 'en': [('Étude No. 2', 'R. Kreutzer'), ('Étude No. 8', 'F. Wohlfahrt')]}

ROAD = [('cremona', 300), ('milan', 500), ('salzburg', 800), ('vienna', 1200)]
# item → slot; every one is sold in a city already reached (or anywhere)
HOME = {
    'vln_quarter': ('violin', 500), 'case_velvet': ('case', 300), 'stand_wood': ('stand', 400),
    'portrait': ('wallL', 300), 'metronome': ('deskM', 250), 'notes': ('deskR', 80),
    'wp_damask': ('wallpaper', 400), 'floor_dark': ('floor', 250), 'curtain_velvet': ('curtain', 200),
    'view_mount': ('view', 400), 'floorlamp': ('floorR', 500), 'plaid': ('chairTop', 150),
    'rug_persian': ('rug', 400), 'cat_ginger': ('pet', 800), 'garland': ('garland', 200),
}
LEFT_OVER = 850  # bars still in the purse: part of the way to Prague


def adb(*args, **kw):
    return subprocess.run([ADB, '-s', SERIAL, *args], check=True, **kw)


def ms(t):
    return int(t.timestamp() * 1000)


def seed(con, lang):
    rnd = random.Random(7)
    now = dt.datetime.now().replace(second=0, microsecond=0)
    today = now.date()
    c = con.cursor()
    for table in ['piece_blocks', 'practice_entries', 'journey_earnings', 'journey_arrivals', 'home_purchases', 'home_choices', 'pieces', 'trophies']:
        c.execute(f'DELETE FROM {table}')

    # practice: 70 days back, a few gaps long ago, an unbroken streak of the last 23 days, today included
    days = []
    for back in range(70, -1, -1):
        day = today - dt.timedelta(days=back)
        if back > 23 and rnd.random() < 0.18:
            continue
        minutes = rnd.choice([20, 25, 30, 35, 40, 45, 50, 60, 75]) if back else 35
        start = dt.datetime.combine(day, dt.time(hour=rnd.choice([8, 17, 18, 19]), minute=rnd.choice([0, 10, 25, 40])))
        if back == 0:
            start = now - dt.timedelta(minutes=minutes + 10)
        days.append((day, start, minutes))
        c.execute('INSERT INTO practice_entries(date, startedAtEpochMs, durationMs, manual) VALUES (?,?,?,0)',
                  (day.isoformat(), ms(start), minutes * 60_000))

    # bars: what the road and the home cost, plus what is left, spread over the practices
    total = sum(p for _, p in ROAD) + sum(p for _, p in HOME.values()) + LEFT_OVER
    weights = [m for _, _, m in days]
    shares = [total * w // sum(weights) for w in weights]
    shares[-1] += total - sum(shares)
    for (day, start, minutes), takts in zip(days, shares):
        notes = minutes * 40
        c.execute('INSERT INTO journey_earnings(atEpochMs, notesPlayed, notesInTune, durationMs, takts, piecesPaid) VALUES (?,?,?,?,?,0)',
                  (ms(start + dt.timedelta(minutes=minutes)), notes, int(notes * 0.72), minutes * 60_000, takts))

    # the trophies already passed, as seen: otherwise the app hands them out over the first screenshot
    elapsed = 0
    for day, _, minutes in days:
        before, elapsed = elapsed, elapsed + minutes
        for hours in (1, 10):
            if before < hours * 60 <= elapsed:
                c.execute('INSERT INTO trophies VALUES (?,?,1)', (hours, day.isoformat()))

    for i, (stop, price) in enumerate(ROAD):
        c.execute('INSERT INTO journey_arrivals VALUES (?,?,?)', (stop, ms(now - dt.timedelta(days=50 - i * 12)), price))
    for i, (item, (slot, price)) in enumerate(HOME.items()):
        c.execute('INSERT INTO home_purchases VALUES (?,?,?,?)', (item, 'ITEM', price, ms(now - dt.timedelta(days=40 - i * 2))))
        c.execute('INSERT INTO home_choices VALUES (?,?)', (slot, item))

    created = ms(now - dt.timedelta(days=60))
    for title, composer, tonic, acc, mode, status, notes in PIECES[lang]:
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section) '
                  'VALUES (?,?,?,?,?,NULL,?,?,?,?,?)', (title, composer, tonic, acc, mode, status, notes, created, created, 'PIECES'))
    pattern, melodic = SCALE_WORDS[lang]
    for tonic, acc, kind, octaves, name in SCALES:
        title = pattern.format(name + (melodic if kind == 'MELODIC_MINOR' else ''), octaves)
        mode = 'MAJOR' if kind == 'MAJOR' else 'MINOR'
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section, scaleKind, scaleOctaves) '
                  "VALUES (?,'',?,?,?,NULL,'LEARNING','',?,?,'SCALES',?,?)", (title, tonic, acc, mode, created, created, kind, octaves))
    for title, composer in ETUDES[lang]:
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section) '
                  "VALUES (?,?,NULL,NULL,NULL,NULL,'LEARNING','',?,?,'ETUDES')", (title, composer, created, created))

    # blocks of the last 30 days, for «Время по элементам»: (piece index in insertion order, minutes a day, every n-th day)
    ids = [row[0] for row in c.execute('SELECT id FROM pieces ORDER BY id')]
    for index, minutes, every in [(0, 20, 1), (1, 15, 2), (5, 10, 1), (8, 15, 3), (2, 10, 4)]:
        for back in range(0, 30, every):
            day = today - dt.timedelta(days=back)
            start = dt.datetime.combine(day, dt.time(hour=18, minute=index * 5))
            c.execute('INSERT INTO piece_blocks(pieceId, date, startedAtEpochMs, durationMs, goalMs, done, paid) VALUES (?,?,?,?,?,1,1)',
                      (ids[index], day.isoformat(), ms(start), minutes * 60_000, minutes * 60_000))
    con.commit()
    return len(days), sum(m for _, _, m in days)


# The takes of the first piece: (days ago, score, near, off, mean error, the best one)
TAKES = [(9, 64, 20, 16, 11.8, False), (4, 73, 17, 10, 9.1, False), (1, 81, 13, 6, 7.2, True)]
# Vivaldi's A minor concerto, the opening bars: (midi note, beats) at 100 bpm
MELODY = [(69, 1), (69, 1), (69, 1), (69, 1), (76, 1), (76, 1), (76, 1), (76, 1), (72, .5), (71, .5), (69, .5), (71, .5),
          (72, .5), (74, .5), (76, 1), (74, .5), (72, .5), (71, .5), (69, .5), (68, 1), (64, 1), (69, 2)]


def violin_wav(path, seconds, seed):
    """A bowed, vibrating sawtooth: not a violin, but a waveform and a sound that pass for one on a screenshot."""
    rate, rnd = 44_100, random.Random(seed)
    notes, t = [], 0.0
    while t < seconds:
        for midi, beats in MELODY:
            notes.append((t, beats * 0.6, 440 * 2 ** ((midi - 69) / 12)))
            t += beats * 0.6
    frames = bytearray()
    phase = 0.0
    for i in range(int(seconds * rate)):
        now = i / rate
        start, length, freq = next((n for n in notes if n[0] <= now < n[0] + n[1]), notes[-1])
        into = now - start
        vibrato = 1 + (0.004 * math.sin(2 * math.pi * 5.5 * into) if into > 0.15 else 0)
        phase += 2 * math.pi * freq * vibrato / rate
        tone = sum(math.sin(k * phase) / k ** 1.3 for k in range(1, 9))
        envelope = min(1, into / 0.04) * min(1, (length - into) / 0.06) * (0.8 + 0.2 * math.sin(math.pi * into / length))
        sample = 0.22 * envelope * tone + 0.004 * (rnd.random() - 0.5)
        frames += struct.pack('<h', int(max(-1, min(1, sample)) * 32767))
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(bytes(frames))


def seed_takes(con, tmp):
    """Clones the first recorded session into takes of the first piece, each with a sound of its own."""
    c = con.cursor()
    template = c.execute('SELECT id, durationMs, a4Hz, toleranceCents, nearCents, previewZones, biasCents FROM sessions WHERE pieceId IS NULL ORDER BY id LIMIT 1').fetchone()
    if template is None:
        print('no recorded session to make takes from: record one on Live of a -PfakePitch=true build')
        return []
    tid, duration, a4, tol, near, zones, bias = template
    samples = c.execute('SELECT bucketMs, data FROM session_samples WHERE sessionId = ?', (tid,)).fetchone()
    piece = c.execute("SELECT id FROM pieces WHERE section = 'PIECES' ORDER BY id LIMIT 1").fetchone()[0]
    c.execute('DELETE FROM sessions WHERE pieceId IS NOT NULL')
    now = dt.datetime.now().replace(second=0, microsecond=0)
    files = []
    for n, (back, score, near_pct, off_pct, mae, best) in enumerate(TAKES):
        name = f'{uuid.uuid4()}.m4a'
        wav = os.path.join(tmp, f'take{n}.wav')
        violin_wav(wav, duration / 1000, n)
        subprocess.run(['afconvert', '-f', 'm4af', '-d', 'aac', '-b', '128000', wav, os.path.join(tmp, name)], check=True)
        started = now - dt.timedelta(days=back, hours=2)
        c.execute('INSERT INTO sessions(title, startedAtEpochMs, durationMs, a4Hz, toleranceCents, nearCents, scorePercent, nearPercent, offPercent, '
                  'maeCents, biasCents, previewZones, audioPath, pieceId, videoPath) VALUES (NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)',
                  (ms(started), duration, a4, tol, near, score, near_pct, off_pct, mae, bias, zones, name, piece))
        take = c.lastrowid
        if samples:
            c.execute('INSERT INTO session_samples VALUES (?,?,?)', (take, samples[0], samples[1]))
        if best:
            c.execute('UPDATE pieces SET bestTakeId = ? WHERE id = ?', (take, piece))
        files.append(name)
    con.commit()
    return files


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else 'ru'
    adb('shell', 'am', 'force-stop', PKG)
    with tempfile.TemporaryDirectory() as tmp:
        local = os.path.join(tmp, 'violin.db')
        for suffix in ['', '-wal', '-shm']:
            with open(local + suffix, 'wb') as out:
                subprocess.run([ADB, '-s', SERIAL, 'exec-out', 'run-as', PKG, 'cat', DB + suffix], stdout=out)
        con = sqlite3.connect(local)
        con.execute('PRAGMA wal_checkpoint(TRUNCATE)')
        count, minutes = seed(con, lang)
        takes = seed_takes(con, tmp)
        con.execute('PRAGMA journal_mode=DELETE')
        con.close()
        # adb joins its arguments into one line for the device shell: the quotes keep the redirect inside run-as
        with open(local, 'rb') as db:
            adb('exec-in', f"run-as {PKG} sh -c 'cat > {DB}'", stdin=db)
        adb('shell', f"run-as {PKG} mkdir -p {SESSIONS}")
        for name in takes:
            with open(os.path.join(tmp, name), 'rb') as audio:
                adb('exec-in', f"run-as {PKG} sh -c 'cat > {SESSIONS}/{name}'", stdin=audio)
    adb('shell', f"run-as {PKG} rm -f {DB}-wal {DB}-shm")
    print(f'{lang}: {count} practice days, {minutes // 60} h {minutes % 60} min, {len(takes)} takes')


if __name__ == '__main__':
    main()
