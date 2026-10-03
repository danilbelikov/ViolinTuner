#!/usr/bin/env python3
"""Fills the debug app on the emulator with a believable player for the store screenshots.

    python3 tools/store/seed.py ru|en

Only the emulator and only com.violinjourney.app.debug: the app's database is replaced by a copy with
~70 days of practice ending today, the road up to Vienna, a furnished room and a repertoire in the
chosen language. The takes of the first piece are cloned from a session recorded on Live of a
-PfakePitch=true build (its analysis is real, of the scripted notes), each given a synthesized sound. Go through the onboarding once before running this:
Room creates the database on the first start.

The numbers are picked to read well: practice up to yesterday is 120 h less one session of SESSION_MIN minutes, so that
the session finished on the screenshot (`tools/store/session.py`) makes 120 h, a streak of STREAK days and a purse
REST_TO_NEXT bars short of Prague."""
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
SHEETS = f'/data/data/{PKG}/files/repertoire'
# the first page of the Vivaldi, engraved from sheet-vivaldi.html (abcjs) and saved as a photo of the notes would be
SHEET = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'sheet-vivaldi.jpg')
THUMB_MAX_SIDE = 320  # RepertoireConfig.thumbMaxSidePx

PIECES = {
    'ru': [
        ('Концерт ля минор, ч. 1', 'А. Вивальди', 'A', 'NATURAL', 'MINOR', 'IN_REPERTOIRE', 'Держать ровный штрих в пассажах, не спешить к концу фразы.'),
        ('Чардаш', 'В. Монти', 'D', 'NATURAL', 'MINOR', 'LEARNING', 'Медленная часть — вибрато шире; флажолеты проверить отдельно.'),
        ('Юмореска', 'А. Дворжак', 'G', 'FLAT', 'MAJOR', 'IN_REPERTOIRE', ''),
        ('Мелодия', 'П. Чайковский', 'E', 'FLAT', 'MAJOR', 'READING', ''),
        ('Концерт № 2, ч. 3', 'Ф. Зейтц', 'G', 'NATURAL', 'MAJOR', 'IN_REPERTOIRE', ''),
    ],
    'en': [
        ('Concerto in A minor, 1st mvt', 'A. Vivaldi', 'A', 'NATURAL', 'MINOR', 'IN_REPERTOIRE', 'Keep the bow even through the passages; do not rush the end of the phrase.'),
        ('Csárdás', 'V. Monti', 'D', 'NATURAL', 'MINOR', 'LEARNING', 'Slow part — wider vibrato; check the harmonics separately.'),
        ('Humoresque', 'A. Dvořák', 'G', 'FLAT', 'MAJOR', 'IN_REPERTOIRE', ''),
        ('Mélodie', 'P. Tchaikovsky', 'E', 'FLAT', 'MAJOR', 'READING', ''),
        ('Concerto No. 2, 3rd mvt', 'F. Seitz', 'G', 'NATURAL', 'MAJOR', 'IN_REPERTOIRE', ''),
    ],
}
# (tonic, accidental, kind, octaves); titles as ScaleTexts.title writes them
SCALES = [('G', 'NATURAL', 'MAJOR', 3, 'G-dur'), ('A', 'NATURAL', 'MELODIC_MINOR', 2, 'a-moll'), ('D', 'NATURAL', 'MAJOR', 2, 'D-dur')]
SCALE_WORDS = {'ru': ('{} · {} октавы', ' мелодический'), 'en': ('{} · {} octaves', ' melodic')}
STROKES = {'ru': ['Деташе и легато', 'Спиккато'], 'en': ['Détaché and legato', 'Spiccato']}
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
TOTAL_HOURS = 120  # at the end of today's session: level 7, «до 8 уровня — 80 ч»
SESSION_MIN = 45  # the session of today, finished on the screenshot
STREAK = 30  # days in a row, today included
TAKTS_PER_MINUTE = 2  # JourneyConfig.taktsPerMinute
NEXT_STOP = 1_600  # Prague
REST_TO_NEXT = 250  # «до Праги — ещё 250» after today's session
LEFT_OVER = NEXT_STOP - REST_TO_NEXT - SESSION_MIN * TAKTS_PER_MINUTE  # bars in the purse before it

# (built-in kind, weekday or days from today, start, minutes, title, place); a lesson repeats weekly
EVENTS = {
    'ru': {
        'lesson': ('LESSON', 1, 17 * 60 + 30, 45, 'Урок по специальности', 'Музыкальная школа № 3'),
        'rehearsal': ('REHEARSAL', 20, 16 * 60, 60, 'Репетиция с концертмейстером', 'Малый зал'),
        'concert': ('PERFORMANCE', 21, 18 * 60, 90, 'Осенний концерт', 'Малый зал'),
        'past': ('PERFORMANCE', -130, 18 * 60, 90, 'Весенний отчётный концерт', 'Большой зал школы'),
    },
    'en': {
        'lesson': ('LESSON', 1, 17 * 60 + 30, 45, 'Violin lesson', 'Music school'),
        'rehearsal': ('REHEARSAL', 20, 16 * 60, 60, 'Rehearsal with the pianist', 'Recital hall'),
        'concert': ('PERFORMANCE', 21, 18 * 60, 90, 'Autumn concert', 'Recital hall'),
        'past': ('PERFORMANCE', -130, 18 * 60, 90, 'Spring recital', 'School main hall'),
    },
}


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

    for table in ['sheet_pages', 'piece_groups', 'piece_backings', 'take_backings', 'event_pieces', 'calendar_events', 'event_series', 'event_kinds']:
        c.execute(f'DELETE FROM {table}')

    # practice: 200 days back, gaps long ago, an unbroken streak up to yesterday — today's session makes it STREAK;
    # the minutes are stretched so that with today's session the total is TOTAL_HOURS exactly
    days = []
    for back in range(200, 0, -1):
        day = today - dt.timedelta(days=back)
        if back == STREAK or (back > STREAK and rnd.random() < 0.22):
            continue
        minutes = rnd.choice([30, 35, 40, 45, 50, 60, 60, 75, 90])
        days.append([day, minutes])
    goal = TOTAL_HOURS * 60 - SESSION_MIN
    have = sum(m for _, m in days)
    for entry in days:
        entry[1] = max(15, round(entry[1] * goal / have / 5) * 5)
    days[0][1] += goal - sum(m for _, m in days)
    days = [(day, dt.datetime.combine(day, dt.time(hour=rnd.choice([8, 17, 18, 19]), minute=rnd.choice([0, 10, 25, 40]))), m)
            for day, m in days]
    for day, start, minutes in days:
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
        for hours in (1, 10, 50, 100):
            if before < hours * 60 <= elapsed:
                c.execute('INSERT INTO trophies VALUES (?,?,1)', (hours, day.isoformat()))

    for i, (stop, price) in enumerate(ROAD):
        c.execute('INSERT INTO journey_arrivals VALUES (?,?,?)', (stop, ms(now - dt.timedelta(days=120 - i * 25)), price))
    for i, (item, (slot, price)) in enumerate(HOME.items()):
        c.execute('INSERT INTO home_purchases VALUES (?,?,?,?)', (item, 'ITEM', price, ms(now - dt.timedelta(days=90 - i * 5))))
        c.execute('INSERT INTO home_choices VALUES (?,?)', (slot, item))

    created = ms(now - dt.timedelta(days=60))
    for title, composer, tonic, acc, mode, status, notes in PIECES[lang]:
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section) '
                  'VALUES (?,?,?,?,?,NULL,?,?,?,?,?)', (title, composer, tonic, acc, mode, status, notes, created, created, 'PIECES'))
    pattern, melodic = SCALE_WORDS[lang]
    for tonic, acc, kind, octaves, name in SCALES:
        title = pattern.format(name + (melodic if kind == 'MELODIC_MINOR' else ''), octaves)
        mode = 'MAJOR' if kind == 'MAJOR' else 'MINOR'
        status = 'IN_REPERTOIRE' if octaves == 3 else 'LEARNING'
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section, scaleKind, scaleOctaves) '
                  "VALUES (?,'',?,?,?,NULL,?,'',?,?,'SCALES',?,?)", (title, tonic, acc, mode, status, created, created, kind, octaves))
    for title, composer in ETUDES[lang]:
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section) '
                  "VALUES (?,?,NULL,NULL,NULL,NULL,'LEARNING','',?,?,'ETUDES')", (title, composer, created, created))
    for title in STROKES[lang]:
        c.execute('INSERT INTO pieces(title, composer, keyTonic, keyAccidental, keyMode, tempoBpm, status, notes, createdAtEpochMs, updatedAtEpochMs, section) '
                  "VALUES (?,'',NULL,NULL,NULL,NULL,'LEARNING','',?,?,'STROKES')", (title, created, created))

    # blocks of the last 30 days, for «Время по элементам»: (piece index in insertion order, minutes a day, every n-th day)
    ids = [row[0] for row in c.execute('SELECT id FROM pieces ORDER BY id')]
    for index, minutes, every in [(0, 20, 1), (1, 15, 2), (5, 10, 1), (8, 15, 3), (2, 10, 4)]:
        for back in range(1, STREAK, every):  # within the streak: a block makes a day practised
            day = today - dt.timedelta(days=back)
            start = dt.datetime.combine(day, dt.time(hour=18, minute=index * 5))
            c.execute('INSERT INTO piece_blocks(pieceId, date, startedAtEpochMs, durationMs, goalMs, done, paid) VALUES (?,?,?,?,?,1,1)',
                      (ids[index], day.isoformat(), ms(start), minutes * 60_000, minutes * 60_000))
    seed_events(c, lang, today, ids)
    con.commit()
    return len(days), sum(m for _, _, m in days)


def seed_events(c, lang, today, ids):
    """A weekly lesson, a rehearsal and the autumn concert ahead with its programme, a recital months ago."""
    created = ms(dt.datetime.now() - dt.timedelta(days=60))
    def event(key, date, series=None):
        kind, _, start, minutes, title, place = EVENTS[lang][key]
        c.execute('INSERT INTO calendar_events(kind, kindId, date, startMinutes, durationMinutes, title, place, notes, seriesId, detached, createdAtEpochMs) '
                  "VALUES (?,NULL,?,?,?,?,?,'',?,0,?)", (kind, date.isoformat(), start, minutes, title, place, series, created))
        return c.lastrowid
    kind, weekday, start, minutes, title, place = EVENTS[lang]['lesson']
    first = today - dt.timedelta(days=56)
    first += dt.timedelta(days=(weekday - first.weekday()) % 7)
    # laid up to its first date only: the app lays the rest ahead on start, as after the form
    c.execute('INSERT INTO event_series(kind, kindId, stepDays, firstDate, untilDate, laidUntil, startMinutes, durationMinutes, title, place) '
              'VALUES (?,NULL,7,?,NULL,?,?,?,?,?)', (kind, first.isoformat(), first.isoformat(), start, minutes, title, place))
    event('lesson', first, c.lastrowid)
    for key in ('rehearsal', 'concert', 'past'):
        at = event(key, today + dt.timedelta(days=EVENTS[lang][key][1]))
        program = [0, 1] if key != 'past' else [4, 2]
        if key != 'rehearsal':
            for position, index in enumerate(program):
                c.execute('INSERT INTO event_pieces VALUES (?,?,?)', (at, ids[index], position))


# The takes of the first piece: (days ago, score, near, the note that drifts and by how much, the best one); the off share
# and the errors are what the synthesized analysis gives
TAKES = [(9, 68, 20, (68, 18), False), (4, 81, 13, (68, 14), False), (1, 93, 5, None, True)]
BUCKET_MS = 50  # IntonationConfig.sessionBucketMs
TOLERANCE, NEAR = 8, 20  # the default tolerance and the near bound
# Vivaldi's A minor concerto, the opening bars: (midi note, beats) at 100 bpm
MELODY = [(69, 1), (69, 1), (69, 1), (69, 1), (76, 1), (76, 1), (76, 1), (76, 1), (72, .5), (71, .5), (69, .5), (71, .5),
          (72, .5), (74, .5), (76, 1), (74, .5), (72, .5), (71, .5), (69, .5), (68, 1), (64, 1), (69, 2)]


def take_analysis(seconds, score, near, drift, seed):
    """Samples of the melody as Live records them (spec 5.5): each note slides in from its attack and sways with a
    slight vibrato; one note may sit off on average. The attack and the spread are searched until the in-tune share is
    exactly [score] and the near one within 4 of [near]; returns the blob and the row's numbers."""
    notes, t = [], 0
    beat = round(0.6 * 1000 / BUCKET_MS)
    while t * BUCKET_MS < seconds * 1000:
        for midi, beats in MELODY:
            notes.append((midi, int(beats * beat)))
            t += int(beats * beat)
    total = int(seconds * 1000 / BUCKET_MS)
    zone = lambda c: 'I' if abs(c) <= TOLERANCE else 'N' if abs(c) <= NEAR else 'O'
    for attempt in range(20_000):
        rnd = random.Random(seed * 100_000 + attempt)
        attack, spread, longest = rnd.uniform(6, 34), rnd.uniform(1, 9), rnd.randint(1, 3)
        samples = []
        for midi, length in notes:
            mean = drift[1] if drift and midi == drift[0] else rnd.gauss(0, spread)
            slide = rnd.randint(1, longest)
            start = rnd.choice([-1, 1]) * rnd.uniform(attack * 0.6, attack)
            for i in range(length - 1):  # the last bucket of a note is the bow change: no pitch
                c = start + (mean - start) * i / slide if i < slide else mean + 2.5 * math.sin(i * 0.9) + rnd.uniform(-1, 1)
                samples.append((midi, c))
            samples.append(None)
        samples = samples[:total]
        cents = [c for s in samples if s for _, c in [s]]
        pct = lambda z: round(100 * sum(zone(c) == z for c in cents) / len(cents))
        got = min(pct('N'), 100 - score)
        if pct('I') == score and abs(got - near) <= 4:
            near = got
            blob = bytearray()
            for sample in samples:
                if sample is None:
                    blob += bytes([0xFF, 0, 0])
                else:
                    blob += bytes([sample[0]]) + struct.pack('>h', round(sample[1] * 100))
            preview, run = '', None
            for sample in samples:  # the first eight notes by their mean, as SessionAnalyzer.previewZones
                if sample and (run is None or run[0] != sample[0]):
                    run = [sample[0], []]
                if sample:
                    run[1].append(sample[1])
                elif run:
                    preview += zone(sum(run[1]) / len(run[1]))
                    run = None
            return bytes(blob), dict(score=score, near=near, off=100 - score - near,
                                     mae=sum(abs(c) for c in cents) / len(cents), bias=sum(cents) / len(cents), preview=preview[:8])
    raise RuntimeError(f'no analysis gives {score} % in tune and about {near} % near')


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
    c.execute('DELETE FROM session_samples WHERE sessionId NOT IN (SELECT id FROM sessions)')
    template = c.execute('SELECT id, durationMs, a4Hz, toleranceCents, nearCents, previewZones, biasCents FROM sessions WHERE pieceId IS NULL ORDER BY id LIMIT 1').fetchone()
    if template is None:
        print('no recorded session to make takes from: record one on Live of a -PfakePitch=true build')
        return []
    tid, duration, a4, tol, near, zones, bias = template
    piece = c.execute("SELECT id FROM pieces WHERE section = 'PIECES' ORDER BY id LIMIT 1").fetchone()[0]
    c.execute('DELETE FROM sessions WHERE pieceId IS NOT NULL')
    now = dt.datetime.now().replace(second=0, microsecond=0)
    files = []
    for n, (back, score, near_pct, drift, best) in enumerate(TAKES):
        name = f'{uuid.uuid4()}.m4a'
        wav = os.path.join(tmp, f'take{n}.wav')
        violin_wav(wav, duration / 1000, n)
        subprocess.run(['afconvert', '-f', 'm4af', '-d', 'aac', '-b', '128000', wav, os.path.join(tmp, name)], check=True)
        started = now - dt.timedelta(days=back, hours=2)
        blob, row = take_analysis(duration / 1000, score, near_pct, drift, n)
        c.execute('INSERT INTO sessions(title, startedAtEpochMs, durationMs, a4Hz, toleranceCents, nearCents, scorePercent, nearPercent, offPercent, '
                  'maeCents, biasCents, previewZones, audioPath, pieceId, videoPath) VALUES (NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)',
                  (ms(started), duration, a4, TOLERANCE, NEAR, row['score'], row['near'], row['off'], row['mae'], row['bias'], row['preview'], name, piece))
        take = c.lastrowid
        c.execute('INSERT INTO session_samples VALUES (?,?,?)', (take, BUCKET_MS, blob))
        if best:
            c.execute('UPDATE pieces SET bestTakeId = ? WHERE id = ?', (take, piece))
        files.append(name)
    con.commit()
    return files


def seed_sheet(con, tmp):
    """The Vivaldi gets its page of notes: the file, its thumbnail and the row; returns the files to push."""
    from PIL import Image
    c = con.cursor()
    piece = c.execute("SELECT id FROM pieces WHERE section = 'PIECES' ORDER BY id LIMIT 1").fetchone()[0]
    name = str(uuid.uuid4())
    page, thumb = f'{name}.jpg', f'{name}-thumb.jpg'
    image = Image.open(SHEET).convert('RGB')
    image.save(os.path.join(tmp, page), quality=88)
    image.thumbnail((THUMB_MAX_SIDE, THUMB_MAX_SIDE))
    image.save(os.path.join(tmp, thumb), quality=85)
    c.execute('INSERT INTO sheet_pages(pieceId, position, fileName, thumbFileName) VALUES (?,0,?,?)', (piece, page, thumb))
    con.commit()
    return [page, thumb]


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
        sheets = seed_sheet(con, tmp)
        con.execute('PRAGMA journal_mode=DELETE')
        con.close()
        # adb joins its arguments into one line for the device shell: the quotes keep the redirect inside run-as
        with open(local, 'rb') as db:
            adb('exec-in', f"run-as {PKG} sh -c 'cat > {DB}'", stdin=db)
        adb('shell', f"run-as {PKG} mkdir -p {SESSIONS}")
        for name in takes:
            with open(os.path.join(tmp, name), 'rb') as audio:
                adb('exec-in', f"run-as {PKG} sh -c 'cat > {SESSIONS}/{name}'", stdin=audio)
        adb('shell', f"run-as {PKG} mkdir -p {SHEETS}")
        for name in sheets:
            with open(os.path.join(tmp, name), 'rb') as image:
                adb('exec-in', f"run-as {PKG} sh -c 'cat > {SHEETS}/{name}'", stdin=image)
    adb('shell', f"run-as {PKG} rm -f {DB}-wal {DB}-shm")
    print(f'{lang}: {count} practice days, {minutes // 60} h {minutes % 60} min, {len(takes)} takes')


if __name__ == '__main__':
    main()
