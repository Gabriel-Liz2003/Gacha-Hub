#!/usr/bin/env python3
"""Rebuild the whole current catalog offline. Never emit the obsolete demo pack."""
import importlib.util
import json
from pathlib import Path
from content_pipeline import write_pack
ROOT=Path(__file__).resolve().parents[1]
def read(path): return json.loads((ROOT/path).read_text())
def generator(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/f'build-{name}-content.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module.generate

def generate():
    base=generator('genshin')(read('content/sources/base.json'),read('content/sources/genshin-db.json'))
    return generator('zzz')(base,*[read(f'content/sources/{n}.json') for n in ['zzz-3.2','zzz-enka','zzz-live-roster','zzz-builds','zzz-materials','zzz-editorial']])

if __name__=='__main__':
    pack=generate()
    print('Bytes:',write_pack(ROOT,pack))
    print({k:len(pack[k]) for k in ['characters','builds','materials','costs','weapons','discSets','teams']})
