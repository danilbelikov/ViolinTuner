#!/usr/bin/env python3
"""Copies the public pages (docs/store/site/) into a clone of the violin-journey repository.

    python3 tools/store/site.py <path to the clone>

Refuses while a page still holds a placeholder in square brackets — [email], [developer name],
[publication date] and the like: a privacy policy with holes must not go public. Files of the clone
that the site no longer has are removed; its .git is left alone. Committing and pushing is up to you."""
import os
import re
import shutil
import sys

SITE = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'docs', 'store', 'site'))
PLACEHOLDER = re.compile(r'\[(email|имя разработчика|developer name|дата публикации|publication date)\]')


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    target = os.path.abspath(sys.argv[1])
    if not os.path.isdir(os.path.join(target, '.git')):
        sys.exit(f'{target} is not a git clone')

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
    shutil.copytree(SITE, target, dirs_exist_ok=True)
    print(f'copied {SITE} → {target}')


if __name__ == '__main__':
    main()
