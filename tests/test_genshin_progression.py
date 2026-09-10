import collections
import importlib.util
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('builder', ROOT/'scripts/build-genshin-content.py')
builder=importlib.util.module_from_spec(spec);spec.loader.exec_module(builder)

class GenshinProgressionTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.pack=json.loads((ROOT/'content/starter.json').read_text())
  cls.snapshot=json.loads((ROOT/'content/sources/genshin-db.json').read_text())
 def costs(self,character,track,start,end):
  result=collections.Counter()
  for level in range(start,end):
   row=next(c for c in self.pack['costs'] if c['characterId']==character and c['track']==track and c['from']==level)
   self.assertEqual(level+1,row['to'])
   result.update(row['costs'])
  return result
 def test_catalog_and_legacy_ids(self):
  chars=[c for c in self.pack['characters'] if c['game']=='GENSHIN']
  self.assertEqual(120,len(chars))
  self.assertEqual('gi:bennett',next(c['id'] for c in chars if c['providerId']=='10000032'))
  self.assertEqual('gi:yelan',next(c['id'] for c in chars if c['providerId']=='10000060'))
  self.assertFalse(any(c['name'] in ['Manekin','Manekina'] for c in chars))
 def test_bennett_full_ascension_reference(self):
  costs=self.costs('gi:bennett','Ascensão (sem EXP)',0,6)
  self.assertEqual(420000,costs['genshin:mora'])
  self.assertEqual(46,costs['genshin:material-113011'])
  self.assertEqual(168,costs['genshin:material-100024'])
  self.assertEqual([18,30,36],[costs[f'genshin:material-{n}'] for n in [112035,112036,112037]])
 def test_partial_ascension_is_not_fraction_of_total(self):
  costs=self.costs('gi:bennett','Ascensão (sem EXP)',4,6)
  self.assertEqual(220000,costs['genshin:mora'])
  self.assertEqual(32,costs['genshin:material-113011'])
  self.assertEqual(105,costs['genshin:material-100024'])
 def test_furina_triple_crown(self):
  costs=collections.Counter()
  for track in builder.TRACKS:costs.update(self.costs('genshin:10000089',track,1,10))
  self.assertEqual(4957500,costs['genshin:mora'])
  self.assertEqual(3,costs['genshin:material-104319'])
  self.assertEqual(18,costs['genshin:material-113056'])
  self.assertEqual([9,63,114],[costs[f'genshin:material-{n}'] for n in [104341,104342,104343]])
 def test_domains_preserve_calendar(self):
  mats={m['id']:m for m in self.pack['materials']}
  self.assertEqual([2,5,7],mats['genshin:material-104304']['days'])
  self.assertEqual([2,5,7],mats['genshin:material-104341']['days'])
  self.assertEqual([],mats['genshin:mora']['days'])
 def test_traveler_does_not_get_arbitrary_talents(self):
  for pid in ['10000005','10000007']:
   rows=[c for c in self.pack['costs'] if c['characterId']==f'genshin:{pid}']
   self.assertEqual(6,len(rows))
   self.assertTrue(all(c['track']=='Ascensão (sem EXP)' for c in rows))
 def test_all_generated_edges_match_source_quantities(self):
  # Every row, not a sample: expansion must preserve material quantities exactly.
  source_rows={(r['kind'],r['file']):r for r in self.snapshot['rows']}
  for row in self.pack['costs']:
   if 'genshin-db' not in row['source']['url']:continue
   filename=row['source']['url'].rsplit('/',1)[1]
   kind='character' if row['track']=='Ascensão (sem EXP)' else 'talent'
   key=('ascend' if kind=='character' else 'lvl')+str(row['to'])
   source=source_rows[kind,filename]['costs'][key]
   expected={('genshin:mora' if x['id']==202 else f'genshin:material-{x["id"]}'):x['count'] for x in source}
   self.assertEqual(expected,row['costs'])
 def test_generator_is_reproducible(self):
  self.assertEqual(self.pack,builder.generate(self.pack,self.snapshot))
 def test_size_within_import_limit(self):
  self.assertLess((ROOT/'content/starter.json').stat().st_size,8*1024*1024)
 def test_large_pack_requires_capable_reader(self):
  self.assertGreaterEqual(self.pack['schemaVersion'],2)

if __name__=='__main__':unittest.main()
