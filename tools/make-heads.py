#!/usr/bin/env python3
"""The head catalog: python3 tools/make-heads.py [heads.json]
-> src/main/resources/skullsanctum/heads.tsv.gz

Reads the Heads Database by TheSilentPro (https://github.com/TheSilentPro/heads, CC0 1.0, archived in June 2026), from
GitHub or from a local copy given as the argument, and writes one line per head:
texture hash <TAB> category <TAB> name <TAB> tags separated by spaces. HeadCatalog reads it from the jar.
"""
import datetime
import gzip
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
URL = 'https://raw.githubusercontent.com/TheSilentPro/heads/main/heads.json'
OUT = ROOT / 'src/main/resources/skullsanctum/heads.tsv.gz'
# the order of the browser's tabs after the classics; HeadCatalog.CATEGORIES must list the same
CATEGORIES = ['alphabet', 'animals', 'blocks', 'decoration', 'food-drinks', 'humans', 'humanoid', 'miscellaneous',
              'monsters', 'plants']
HASH = re.compile(r'[0-9a-f]{1,64}')


def slug(name):
    return re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')


def clean(text):
    return re.sub(r'\s+', ' ', text).strip()


if len(sys.argv) > 1:
    heads = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
else:
    with urllib.request.urlopen(URL, timeout=120) as response:
        heads = json.load(response)

lines, seen = [], set()
for head in heads:
    category = slug(head['category'])
    if category not in CATEGORIES:
        sys.exit(f'unknown category {head["category"]!r} (head {head.get("id")}): add it to CATEGORIES and HeadCatalog')
    texture = head['texture'].strip().lower()
    name = clean(head.get('name') or '')
    if not HASH.fullmatch(texture) or not name or texture in seen:
        continue
    seen.add(texture)
    tags = ' '.join(clean(tag) for tag in head.get('tags') or [] if clean(tag))
    lines.append(f'{texture}\t{category}\t{name}\t{tags}')

header = f'# Heads Database by TheSilentPro, CC0 1.0, {datetime.date.today()}, {len(lines)} heads'
OUT.parent.mkdir(parents=True, exist_ok=True)
with gzip.GzipFile(OUT, 'wb', compresslevel=9, mtime=0) as out:
    out.write(('\n'.join([header] + lines) + '\n').encode('utf-8'))
print(f'{len(lines)} heads -> {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KiB)')
