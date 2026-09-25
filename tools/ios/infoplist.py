#!/usr/bin/env python3
"""Writes iosApp/iosApp/InfoPlist.xcstrings — the texts iOS itself shows for the app — from the words of the app:
python3 tools/ios/infoplist.py

The request for the microphone (NSMicrophoneUsageDescription) is the text of the prompt on Live, mic_permission_text of app/src/main/res,
and the request for the camera (NSCameraUsageDescription) is camera_permission_text, in every language the app speaks (spec 3.26);
so the app and the system never say different things. Not to be edited by hand."""
import json
import os
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'res')
OUT = os.path.join(HERE, '..', '..', 'iosApp', 'iosApp', 'InfoPlist.xcstrings')

# the folders of the Android-style resources → the language codes of iOS
LANGUAGES = {
    'values': 'en', 'values-ru': 'ru', 'values-de': 'de', 'values-fr': 'fr', 'values-es': 'es',
    'values-it': 'it', 'values-pt': 'pt', 'values-ko': 'ko', 'values-zh': 'zh-Hans', 'values-ja': 'ja',
}
KEYS = {'NSMicrophoneUsageDescription': 'mic_permission_text', 'NSCameraUsageDescription': 'camera_permission_text'}


def text(folder, name):
    root = ET.parse(os.path.join(RES, folder, 'strings.xml')).getroot()
    for node in root.findall('string'):
        if node.get('name') == name:
            # Android's escapes undone: iOS shows the text as it is
            return ''.join(node.itertext()).replace("\\'", "'").replace('\\"', '"').replace('\\?', '?').replace('\\@', '@')
    raise SystemExit(f'no «{name}» in {folder}')


def main():
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
