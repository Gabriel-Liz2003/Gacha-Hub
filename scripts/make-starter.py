#!/usr/bin/env python3
"""Single offline pipeline: preserved seed -> Genshin -> ZZZ."""
import importlib.util
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def module(name):
    spec=importlib.util.spec_from_file_location(name,ROOT/'scripts'/(name+'.py'))
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result)
    return result
def generate():
    def read(name):return json.loads((ROOT/'content/sources'/name).read_text(encoding='utf-8'))
    pack=module('build-genshin-content').generate(read('base.json'),read('genshin-db.json'))
    return module('build-zzz-content').generate(pack)
def main():
    pack=generate()
    data=(json.dumps(pack,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8')
    assert len(data)<8*1024*1024,'Content exceeds 8 MiB'
    for key in ['characters','materials','costs','builds','teams','banners','wEngines','driveDiscs']:
        assert all(len(json.dumps(row,ensure_ascii=False).encode('utf-8'))<256*1024 for row in pack.get(key,[])),key
    for path in ['content/starter.json','app/src/main/assets/starter.json']:(ROOT/path).write_bytes(data)
    print({key:len(pack[key]) for key in ['characters','materials','costs','builds','teams','wEngines','driveDiscs']})
    print(f'Content bytes: {len(data)}')
if __name__=='__main__':main()
