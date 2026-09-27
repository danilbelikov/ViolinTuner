#!/usr/bin/env python3
"""Copies the public pages (docs/store/site/) into a clone of the violin-journey repository.

    python3 tools/store/site.py <path to the clone>

Refuses while a page still holds a placeholder in square brackets — [email], [developer name],
[publication date] and the like: a privacy policy with holes must not go public. Files of the clone
that the site no longer has are removed, each named as it goes; its .git is left alone. Committing and
pushing is up to you.

Refuses any folder but a clone of violin-journey outside this repository: the removal would otherwise
wipe whatever it was pointed at — `.` or `..` by a slip is this very working tree, with its untracked
files (local.properties among them) that git cannot bring back."""
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.realpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
SITE = os.path.join(REPO, 'docs', 'store', 'site')
# the origin of the site's own repository, by SSH or HTTPS: …github.com:danilbelikov/violin-journey.git and the like
SITE_REMOTE = re.compile(r'[/:]violin-journey(\.git)?/?$')
PLACEHOLDER = re.compile(r'\[(email|имя разработчика|developer name|дата публикации|publication date)\]')


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    target = os.path.realpath(sys.argv[1])
    if not os.path.isdir(os.path.join(target, '.git')):
        sys.exit(f'{target} is not a git clone')
    # this repository, a folder inside it or one that holds it: the removal below would reach its files
    if os.path.commonpath([target, REPO]) in (target, REPO):
        sys.exit(f'{target} overlaps this repository ({REPO}): give the clone of violin-journey')
    origin = subprocess.run(['git', '-C', target, 'remote', 'get-url', 'origin'], capture_output=True, text=True).stdout.strip()
    if not SITE_REMOTE.search(origin):
        sys.exit(f'{target} is not a clone of violin-journey (origin: {origin or "none"})')

    holes = []
    for folder, _, files in os.walk(SITE):
        for name in files:
            if name.endswith('.html'):
                path = os.path.join(folder, name)
                with open(path, encoding='utf-8') as f:
                    holes += [f'{os.path.relpath(path, SITE)}: {m.group(0)}' for m in PLACEHOLDER.finditer(f.read())]
    if holes:
        sys.exit('fill these in first:\n  ' + '\n  '.join(sorted(set(holes))))

    for entry in os.listdir(target):
        if entry != '.git' and not os.path.exists(os.path.join(SITE, entry)):
            path = os.path.join(target, entry)
            shutil.rmtree(path) if os.path.isdir(path) else os.remove(path)
            print(f'removed {entry}')
    shutil.copytree(SITE, target, dirs_exist_ok=True)
    print(f'copied {SITE} → {target}')


if __name__ == '__main__':
    main()
