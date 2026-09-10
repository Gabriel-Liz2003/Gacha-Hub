#!/usr/bin/env python3
"""Rebuild the Genshin catalog and exact costs from a pinned, offline source snapshot.

No runtime scraping, guessed costs, proportional totals, or recommendation generation.
"""
import json
import sys
sys.path.insert(0,str(__import__("pathlib").Path(__file__).resolve().parent))
from content_pipeline import canonical
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DAYS = {name: i for i, name in enumerate(['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'], 1)}
EXCLUDED = {'manekin.json', 'manekina.json'}  # Source has no valid counts for these UGC avatars.
TRACKS = ['Ataque Normal (nível base)', 'Habilidade Elemental (nível base)', 'Supremo Elemental (nível base)']


def generate(base, snapshot):
    sha = snapshot['sha']
    root = f'https://github.com/{snapshot["repository"]}/blob/{sha}/src/data/English'
    def source(folder, filename):
        return dict(name='genshin-db • dados de evolução', url=f'{root}/{folder}/{filename}',
                    checkedAt=snapshot['checkedAt'], patch=snapshot['patch'],
                    note='Snapshot de 20/08/2026. Talentos usam nível base, sem bônus de constelação. Ascensão não inclui EXP de nível.')
    previous = {c.get('providerId'): c for c in base['characters'] if c['game'] == 'GENSHIN'}
    characters = [c for c in base['characters'] if c['game'] != 'GENSHIN']
    old_genshin_ids = {c['id'] for c in base['characters'] if c['game'] == 'GENSHIN'}
    costs = [c for c in base['costs'] if c['characterId'] not in old_genshin_ids]
    material_data = {m['id']: m for m in snapshot['materials']}
    material_ids = {202: 'genshin:mora'}  # Preserve the initial inventory ID.
    referenced = set()
    def amounts(items):
        result = {}
        for item in items:
            assert isinstance(item.get('count'), int) and item['count'] > 0, item
            assert item['id'] in material_data, item
            referenced.add(item['id'])
            mid = material_ids.get(item['id'], f'genshin:material-{item["id"]}')
            assert mid not in result, item
            result[mid] = item['count']
        assert result
        return result
    talents = {r['file']: r for r in snapshot['rows'] if r['kind'] == 'talent'}
    for row in snapshot['rows']:
        if row['kind'] != 'character' or row['file'] in EXCLUDED:
            continue
        pid = str(row['id'])
        old = previous.get(pid, {})
        cid = old.get('id', f'genshin:{pid}')
        characters.append(dict(old, id=cid, game='GENSHIN', name=row['name'], rarity=row['rarity'],
                               element=row.get('element', ''), specialty=row.get('weapon', ''), providerId=pid,
                               image=snapshot['images'].get(row['file'][:-5], old.get('image', '')),
                               sources=old.get('sources', []) + [source('characters', row['file'])]
                               if not any(s.get('url') == source('characters', row['file'])['url'] for s in old.get('sources', []))
                               else old['sources']))
        for level in range(1, 7):
            costs.append(dict(characterId=cid, track='Ascensão (sem EXP)', **{'from': level-1, 'to': level},
                              costs=amounts(row['costs'][f'ascend{level}']), source=source('characters', row['file'])))
        # Traveler costs depend on the selected element and sometimes the talent slot.
        # Do not attach an arbitrary elemental table to Aether or Lumine.
        talent = talents.get(row['file'])
        if talent:
            assert set(talent['costs']) == {f'lvl{n}' for n in range(2, 11)}
            for track in TRACKS:
                for level in range(2, 11):
                    costs.append(dict(characterId=cid, track=track, **{'from': level-1, 'to': level},
                                      costs=amounts(talent['costs'][f'lvl{level}']), source=source('talents', row['file'])))
    # Keep legacy materials for existing inventory and historical project snapshots.
    materials = {m['id']: m for m in base['materials']}
    for mid in sorted(referenced):
        row = material_data[mid]
        days = [DAYS[d] for d in row.get('days', [])]
        locations = ([row['domain']] if row.get('domain') else []) + row.get('sources', [])
        key = material_ids.get(mid, f'genshin:material-{mid}')
        materials[key] = dict(id=key, game='GENSHIN', name=row['name'], category=row.get('type', 'Material'),
                              days=sorted(set(days)), location=' • '.join(locations), sources=[source('materials', row['file'])])
    count = sum(c['game'] == 'GENSHIN' for c in characters)
    # Schema 2 prevents older APKs (single-row storage) from accepting this large pack.
    return canonical(dict(base, schemaVersion=max(2,base["schemaVersion"]), version=max(2,base["version"]), publishedAt=max(base["publishedAt"],snapshot['checkedAt']), characters=characters,
                materials=list(materials.values()), costs=costs,
                coverage=base['coverage'] if base['schemaVersion']>=3 else f'PARCIAL: {len(characters)} personagens ({count} Genshin), {len(base["builds"])} builds. Genshin: ascensões por etapa e talentos 1–10; talentos do Viajante, EXP e armas ainda ausentes. Outros jogos mantêm cobertura inicial.'))


if __name__ == '__main__':
    base = json.loads((ROOT/'content/starter.json').read_text())
    snapshot = json.loads((ROOT/'content/sources/genshin-db.json').read_text())
    pack = generate(base, snapshot)
    # Compact APK data: sources and costs remain fully inspectable as JSON.
    data = json.dumps(pack, ensure_ascii=False, separators=(',', ':')) + '\n'
    for path in ['content/starter.json', 'app/src/main/assets/starter.json']:
        (ROOT/path).write_text(data)
    print({key: len(pack[key]) for key in ['characters', 'builds', 'materials', 'costs']})
    print(f'Content bytes: {len(data.encode())}')
