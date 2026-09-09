import json,unittest,datetime,pathlib,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
class ContentTests(unittest.TestCase):
 def setUp(self): self.p=json.loads((ROOT/'content/starter.json').read_text())
 def test_asset_matches_published(self):self.assertEqual((ROOT/'content/starter.json').read_bytes(),(ROOT/'app/src/main/assets/starter.json').read_bytes())
 def test_all_games(self):self.assertEqual({c['game'] for c in self.p['characters']},{'ZZZ','HSR','GENSHIN','WUWA'})
 def test_unique_ids(self):
  for kind in ['characters','builds','materials','teams']:
   ids=[x['id'] for x in self.p[kind]];self.assertEqual(len(ids),len(set(ids)))
 def test_costs_never_cross_games(self):
  chars={c['id']:c for c in self.p['characters']};mats={m['id']:m for m in self.p['materials']}
  for c in self.p['costs']:
   self.assertLess(c['from'],c['to'])
   for k,v in c['costs'].items():self.assertEqual(chars[c['characterId']]['game'],mats[k]['game']);self.assertGreater(v,0)
 def test_every_build_has_source_and_context(self):
  for b in self.p['builds']:
   self.assertTrue(b['sources'])
   for s in b['sources']:self.assertTrue(s['url'].startswith('https://'));datetime.date.fromisoformat(s['checkedAt'])
   for x in b.get('benchmarks',[]):self.assertLessEqual(x['low'],x['adequate']);self.assertLessEqual(x['adequate'],x['excellent']);self.assertTrue(x['context'])
 def test_teams_scoped_and_complete(self):
  chars={c['id']:c for c in self.p['characters']}
  for t in self.p['teams']:
   self.assertEqual(len(t['slots']),4 if t['game'] in ['HSR','GENSHIN'] else 3)
   for slot in t['slots']:
    for cid in slot:self.assertEqual(chars[cid]['game'],t['game'])
 def test_reference_totals(self):
  h=next(c for c in self.p['costs'] if 'hsr:credit' in c['costs']);w=next(c for c in self.p['costs'] if 'wuwa:rage' in c['costs']);self.assertEqual(h['costs']['hsr:credit'],246400);self.assertEqual(w['costs']['wuwa:rage'],46);self.assertEqual(w['costs']['wuwa:pecok'],60)
 def test_permissions(self):
  manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
  ns='{http://schemas.android.com/apk/res/android}'
  self.assertEqual([x.attrib[ns+'name'] for x in manifest.findall('uses-permission')],['android.permission.INTERNET'])
 def test_coverage_honest(self):self.assertIn('PARCIAL',self.p['coverage'])
if __name__=='__main__':unittest.main()
