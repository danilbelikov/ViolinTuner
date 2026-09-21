#!/usr/bin/env python3
"""Checks one translation against the Russian source: python3 tools/i18n/check.py <tag>   (tag «en» is res/values).

The same rules as LocalizationTest, without Gradle: keys, placeholders, array lengths, leftovers of Russian,
XML that parses, apostrophes and quotes that Android would choke on."""
import os, re, sys
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'app', 'src', 'main', 'res')
FILES = ['strings.xml', 'strings_home.xml', 'home_catalog.xml']
PLACEHOLDER = re.compile(r'%(\d+\$)?[sdf]|%%')
CYRILLIC = re.compile('[А-Яа-яЁё]')


def read(directory):
    strings, arrays = {}, {}
    for name in FILES:
        path = os.path.join(directory, name)
        if not os.path.exists(path):
            print(f'MISSING FILE {path}')
            continue
        root = ET.parse(path).getroot()
        for node in root:
            if node.tag == 'string':
                strings[node.get('name')] = ''.join(node.itertext())
            elif node.tag == 'string-array':
                arrays[node.get('name')] = [''.join(i.itertext()) for i in node.findall('item')]
    return strings, arrays


def main():
    tag = sys.argv[1]
    source_s, source_a = read(os.path.join(ROOT, 'values-ru'))
    target_s, target_a = read(os.path.join(ROOT, 'values' if tag == 'en' else f'values-{tag}'))
    problems = []
    for key in source_s:
        if key not in target_s:
            problems.append(f'no string «{key}»')
    for key in target_s:
        if key not in source_s:
            problems.append(f'unknown string «{key}»')
    for key, text in target_s.items():
        if key not in source_s:
            continue
        want = sorted(m.group(0) for m in PLACEHOLDER.finditer(source_s[key]))
        have = sorted(m.group(0) for m in PLACEHOLDER.finditer(text))
        if want != have:
            problems.append(f'«{key}»: placeholders {have}, the source has {want}')
        if CYRILLIC.search(text):
            problems.append(f'«{key}» is still in Russian')
        if not text.strip() and source_s[key].strip():
            problems.append(f'«{key}» is empty')
        if re.search(r"(?<!\\)'", text):
            problems.append(f'«{key}»: an apostrophe that is not escaped as \\\'')
        if re.search(r'(?<!\\)"', text):
            problems.append(f'«{key}»: a double quote that is not escaped as \\"')
    for key, items in source_a.items():
        if key not in target_a:
            problems.append(f'no array «{key}»')
        elif len(target_a[key]) != len(items):
            problems.append(f'array «{key}»: {len(target_a[key])} items, the source has {len(items)}')
        else:
            for i, item in enumerate(target_a[key]):
                if CYRILLIC.search(item):
                    problems.append(f'«{key}»[{i}] is still in Russian')
                if re.search(r"(?<!\\)'", item):
                    problems.append(f'«{key}»[{i}]: an apostrophe that is not escaped')
    print(f'{tag}: {len(target_s)} strings, {len(target_a)} arrays, {len(problems)} problems')
    for p in problems[:80]:
        print('  ' + p)
    sys.exit(1 if problems else 0)


main()
