import importlib.util,json,pathlib,unittest,urllib.parse
ROOT=pathlib.Path(__file__).resolve().parents[1]
def read(name):return json.loads((ROOT/name).read_text())
class ZzzTests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.p=read('content/starter.json');cls.raw=read('content/sources/zzz-3.2.json');cls.live=read('content/sources/zzz-live-roster.json');cls.chars=[c for c in cls.p['characters'] if c['game']=='ZZZ'];cls.ids={c['id'] for c in cls.chars};cls.edges=[e for e in cls.p['costs'] if e['characterId'] in cls.ids]
 def test_live_manifest_and_legacy_ids(self):
  self.assertEqual(59,len(self.chars));self.assertEqual(set(self.live['live']),{c['name'] for c in self.chars})
  for name in ['ellen','dialyn','lighter','lycaon','astra']:self.assertIn('zzz:'+name,self.ids)
  self.assertNotIn('Roxy',{c['name'] for c in self.chars})
 def test_taxonomy_and_details(self):
  for c in self.chars:
   self.assertIn(c['rarity'],[4,5]);self.assertTrue(c['faction']);self.assertTrue(c['element']);self.assertTrue(c['specialty']);self.assertEqual(6,len(c['mindscapes']));self.assertEqual({'basic','dodge','assist','special','chain'},set(c['skillNames']));self.assertTrue(c['baseStats'])
 def test_provider_mapping_unique(self):self.assertEqual(59,len({c['providerId'] for c in self.chars}))
 def test_complete_exact_transitions(self):
  raw={str(c['Id']):c for c in self.raw['characters']}
  for c in self.chars:
   edges=[e for e in self.edges if e['characterId']==c['id']];self.assertEqual(125,len(edges));r=raw[c['providerId']]
   expected={'level':(1,60),'ascension':(0,5),'core':(0,6),**{'skill:'+k:(1,12) for k in ['basic','dodge','assist','special','chain']}}
   for track,(start,end) in expected.items():
    got=sorted([e for e in edges if e['track']==track],key=lambda e:e['from']);self.assertEqual(list(range(start,end)),[e['from'] for e in got]);self.assertTrue(all(e['to']==e['from']+1 for e in got))
    for e in got:
     if track=='level':self.assertEqual({'zzz:agent-exp':r['LevelEXP'][e['from']-1]},e['costs']);continue
     v=r['Level'][str(e['from']+1)]['Materials'] if track=='ascension' else r['Passive']['Materials'][str(e['to'])] if track=='core' else r['Skill'][track.split(':')[1].capitalize()]['Material'][str(e['from'])]
     self.assertEqual({('zzz:denny' if k=='10' else 'zzz:material-'+k):n for k,n in v.items()},e['costs'])
 def test_reference_totals(self):
  for c in self.chars:
   edges=[e for e in self.edges if e['characterId']==c['id']]
   self.assertEqual(900000,sum(e['costs'].get('zzz:agent-exp',0) for e in edges))
   self.assertEqual(3705000,sum(e['costs'].get('zzz:denny',0) for e in edges))
 def test_weapon_progression_and_materials(self):
  mats={m['id'] for m in self.p['materials'] if m['game']=='ZZZ'}
  self.assertEqual(99,len(self.p['weapons']))
  for w in self.p['weapons']:
   self.assertEqual(64,len(w['upgrades']))
   for e in w['upgrades']:
    self.assertEqual(e['from']+1,e['to']);self.assertTrue(set(e['costs'])<=mats);self.assertTrue(all(v>0 for v in e['costs'].values()))
 def test_builds_and_equipment_references(self):
  ws={w['id'] for w in self.p['weapons']};ds={d['id'] for d in self.p['discSets']};bs=[b for b in self.p['builds'] if b['characterId'] in self.ids]
  self.assertEqual(self.ids,{b['characterId'] for b in bs});self.assertEqual(59,len(bs));self.assertEqual(30,len(ds))
  for c in self.chars:self.assertIn(c['signatureWeaponId'],ws)
  for b in bs:
   self.assertTrue(b['weaponOptions']);self.assertTrue(b['discOptions']);self.assertTrue({'4','5','6'}<=b['slots'].keys())
   for w in b['weaponOptions']:self.assertIn(w['weaponId'],ws)
   for d in b['discOptions']:self.assertIn(d['fourPieceId'],ds);self.assertTrue(d['twoPieceIds']);self.assertTrue(set(d['twoPieceIds'])<=ds);self.assertNotIn(d['fourPieceId'],d['twoPieceIds'])
   for v in b['benchmarks']:self.assertEqual(v['low'],v['adequate'])
 def test_sources_and_images_https(self):
  for c in self.chars+self.p['weapons']+self.p['discSets']:
   for url in [c['image']]+[s['url'] for s in c['sources']]:
    u=urllib.parse.urlparse(url);self.assertEqual('https',u.scheme);self.assertTrue(u.hostname)
 def test_all_agents_have_three_slot_teams(self):
  teams=[t for t in self.p['teams'] if t['game']=='ZZZ'];self.assertEqual(59,len(teams))
  self.assertEqual(self.ids,set(x for t in teams for s in t['slots'] for x in s))
  for t in teams:self.assertEqual(3,len(t['slots']));self.assertTrue(set(x for s in t['slots'] for x in s)<=self.ids)
 def test_full_pipeline_reproduces_pack_and_preserves_other_games(self):
  spec=importlib.util.spec_from_file_location('pipeline',ROOT/'scripts/make-starter.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
  self.assertTrue(self.p==m.generate());self.assertEqual(120,sum(c['game']=='GENSHIN' for c in self.p['characters']))
