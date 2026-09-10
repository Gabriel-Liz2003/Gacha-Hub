"""Shared deterministic ordering, not a new content model."""
import json

def canonical(pack):
    pack = dict(pack)
    for key in ['characters', 'materials', 'builds', 'teams', 'banners', 'weapons', 'discSets']:
        if key in pack:
            pack[key] = sorted(pack[key], key=lambda row: row['id'])
    pack['costs'] = sorted(pack['costs'], key=lambda row: (row['characterId'], row['track'], row['from']))
    return pack

def write_pack(root, pack):
    text = json.dumps(canonical(pack), ensure_ascii=False, separators=(',', ':')) + '\n'
    for path in ['content/starter.json', 'app/src/main/assets/starter.json']:
        (root/path).write_text(text)
    return len(text.encode())
