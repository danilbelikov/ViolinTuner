#!/usr/bin/env python3
"""Fills the debug app on the emulator with a believable player for the store screenshots.

    python3 tools/store/seed.py ru|en

Only the emulator and only com.violinjourney.app.debug: the app's database is replaced by a copy with
~70 days of practice ending today, the road up to Vienna, a furnished room and a repertoire in the
chosen language. Recordings are not seeded — record them on Live of a -PfakePitch=true build, which
analyses the scripted notes as it would a violin. Go through the onboarding once before running this:
Room creates the database on the first start."""
import datetime as dt
import os
import random
import sqlite3
import subprocess
import sys
import tempfile

ADB = os.path.expanduser('~/Library/Android/sdk/platform-tools/adb')
SERIAL = 'emulator-5554'
PKG = 'com.violinjourney.app.debug'
DB = f'/data/data/{PKG}/databases/violin.db'

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
    for table in ['practice_entries', 'journey_earnings', 'journey_arrivals', 'home_purchases', 'home_choices', 'pieces', 'trophies']:
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
    con.commit()
    return len(days), sum(m for _, _, m in days)


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
        con.execute('PRAGMA journal_mode=DELETE')
        con.close()
        # adb joins its arguments into one line for the device shell: the quotes keep the redirect inside run-as
        with open(local, 'rb') as db:
            adb('exec-in', f"run-as {PKG} sh -c 'cat > {DB}'", stdin=db)
    adb('shell', f"run-as {PKG} rm -f {DB}-wal {DB}-shm")
    print(f'{lang}: {count} practice days, {minutes // 60} h {minutes % 60} min')


if __name__ == '__main__':
    main()
