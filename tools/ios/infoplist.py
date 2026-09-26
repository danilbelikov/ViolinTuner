#!/usr/bin/env python3
"""Writes iosApp/iosApp/InfoPlist.xcstrings — the texts iOS itself shows for the app — from the words of the app:
python3 tools/ios/infoplist.py

The request for the microphone (NSMicrophoneUsageDescription) is the text of the prompt on Live, mic_permission_text of app/src/main/res,
the request for the camera (NSCameraUsageDescription) is camera_permission_text, and the request to add to Photos
(NSPhotoLibraryAddUsageDescription — «Save Video» of the system share sheet) is photos_add_permission_text, in every language the app
speaks (spec 3.26); so the app and the system never say different things. Not to be edited by hand.

iOS shows a text of the catalog only for a key the Info.plist has: each key is declared in the Xcode project as a build setting
INFOPLIST_KEY_<key> with the English text, in every configuration that generates the Info.plist. The script checks that first and
stops with the line to paste when one is missing or says other words; only then it writes the catalog."""
import json
import os
import re
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res')
OUT = os.path.join(HERE, '..', '..', 'iosApp', 'iosApp', 'InfoPlist.xcstrings')
PROJECT = os.path.join(HERE, '..', '..', 'iosApp', 'iosApp.xcodeproj', 'project.pbxproj')

# the folders of the Android-style resources → the language codes of iOS
LANGUAGES = {
    'values': 'en', 'values-ru': 'ru', 'values-de': 'de', 'values-fr': 'fr', 'values-es': 'es',
    'values-it': 'it', 'values-pt': 'pt', 'values-ko': 'ko', 'values-zh': 'zh-Hans', 'values-ja': 'ja',
}
KEYS = {
    'NSMicrophoneUsageDescription': 'mic_permission_text',
    'NSCameraUsageDescription': 'camera_permission_text',
    'NSPhotoLibraryAddUsageDescription': 'photos_add_permission_text',
}
SOURCE_FOLDER = 'values'  # English: the words of the build setting, the language the catalog starts from

CONFIGURATIONS = re.compile(r'/\* Begin XCBuildConfiguration section \*/(.*?)/\* End XCBuildConfiguration section \*/', re.S)
CONFIGURATION = re.compile(r'^\t\t(\w+) /\* (.*?) \*/ = \{\n(.*?)^\t\t\};', re.S | re.M)
SETTING = r'^\s*{name} = ("(?:[^"\\]|\\.)*"|[^;\s]+);'
ESCAPES = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\'}


def text(folder, name):
    root = ET.parse(os.path.join(RES, folder, 'strings.xml')).getroot()
    for node in root.findall('string'):
        if node.get('name') == name:
            # Android's escapes undone: iOS shows the text as it is
            return ''.join(node.itertext()).replace("\\'", "'").replace('\\"', '"').replace('\\?', '?').replace('\\@', '@')
    raise SystemExit(f'no «{name}» in {folder}')


def unescaped(match):
    code = match.group(1)
    return chr(int(code[1:], 16)) if len(code) == 5 else ESCAPES.get(code, code)


def unquoted(value):
    """A value of the project file as Xcode reads it: the quotes taken off and the escapes inside them undone."""
    return re.sub(r'\\(U[0-9a-fA-F]{4}|.)', unescaped, value[1:-1]) if value.startswith('"') else value


def quoted(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'


def check_project():
    """Every configuration that generates the Info.plist declares every key with the English words of the app."""
    with open(PROJECT, encoding='utf-8') as f:
        section = CONFIGURATIONS.search(f.read())
    if section is None:
        raise SystemExit(f'no build configurations in {os.path.relpath(PROJECT, os.getcwd())}')
    generating = [(object_id, name, body) for object_id, name, body in CONFIGURATION.findall(section.group(1))
                  if re.search(r'^\s*GENERATE_INFOPLIST_FILE = YES;', body, re.M)]
    if not generating:
        raise SystemExit('no build configuration generates the Info.plist (GENERATE_INFOPLIST_FILE = YES)')
    problems = []
    for key, name in KEYS.items():
        setting = f'INFOPLIST_KEY_{key}'
        want = text(SOURCE_FOLDER, name)
        for object_id, configuration, body in generating:
            found = re.search(SETTING.format(name=setting), body, re.M)
            if found is None:
                problems.append(f'{configuration} ({object_id}) has no {setting}')
            elif unquoted(found.group(1)) != want:
                problems.append(f'{configuration} ({object_id}): {setting} says other words than {name} of res/{SOURCE_FOLDER}')
            else:
                continue
            problems.append(f'    {setting} = {quoted(want)};')
    if problems:
        raise SystemExit('the Xcode project is behind the words of the app:\n' + '\n'.join(problems))


def main():
    check_project()
    strings = {}
    for key, name in KEYS.items():
        strings[key] = {
            'comment': f'The same words as {name} of the shared resources; written by tools/ios/infoplist.py.',
            'extractionState': 'manual',
            'localizations': {
                code: {'stringUnit': {'state': 'translated', 'value': text(folder, name)}}
                for folder, code in sorted(LANGUAGES.items(), key=lambda item: item[1])
            },
        }
    catalog = {'sourceLanguage': 'en', 'strings': strings, 'version': '1.0'}
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2, sort_keys=True)
        f.write('\n')
    print('wrote', os.path.relpath(OUT, os.getcwd()))


main()
