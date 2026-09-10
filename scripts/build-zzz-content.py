#!/usr/bin/env python3
"""Rebuild ZZZ from reviewed snapshots; no network requests, no runtime scraping.

The live manifest is independently reviewed, never inferred from data-file presence.
Existing IDs are retained. W-Engine progression is stored once per weapon.
"""
import json, math, re
from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent))
from content_pipeline import canonical
ROOT=Path(__file__).resolve().parents[1]
ALIASES={'Soldier 0 - Anby':'Anby: Soldier 0','Starlight - Billy':'Billy - Starlight','Jane':'Jane Doe'}
SKILLS={'Basic':'basic','Dodge':'dodge','Assist':'assist','Special':'special','Chain':'chain'}
STATS={'HpMax':'HP','Attack':'ATK','Defence':'DEF','BreakStun':'Impact','Crit':'CRIT Rate','CritDamage':'CRIT DMG','ElementMystery':'Anomaly Proficiency','ElementAbnormalPower':'Anomaly Mastery','PenRate':'PEN Ratio','SpRecover':'Energy Regen'}
def clean(t):return re.sub(r'<[^>]*>','',t).replace('\\n','\n').replace('\xa0',' ').strip()
def source(snapshot,path,note=''):
 return dict(name='Hakushin • dados do jogo',url=f'https://github.com/{snapshot["repository"]}/blob/{snapshot["sha"]}/{path}',checkedAt=snapshot['checkedAt'],patch=snapshot['patch'],note=note)
def generate(base,snapshot,enka,manifest,guides,material_info,editorial):
 live=set(manifest['live']);rows={ALIASES.get(c['Name'],c['Name']):c for c in snapshot['characters']}
 assert live<=rows.keys(),live-rows.keys()
 old={c.get('providerId'):c for c in base['characters'] if c['game']=='ZZZ'}
 old_names={c['name']:c for c in base['characters'] if c['game']=='ZZZ'}
 characters=[c for c in base['characters'] if c['game']!='ZZZ'];builds=[b for b in base['builds'] if b['characterId'] not in {c['id'] for c in base['characters'] if c['game']=='ZZZ'}]
 costs=[c for c in base['costs'] if c['characterId'] not in {c['id'] for c in base['characters'] if c['game']=='ZZZ'}]
 teams=[t for t in base['teams'] if t['game']!='ZZZ'];mats={m['id']:m for m in base['materials'] if m['game']!='ZZZ'}
 def amounts(v):
  result={}
  for id,n in v.items():
   assert isinstance(n,int) and n>0
   mid='zzz:denny' if str(id)=='10' else f'zzz:material-{id}'
   result[mid]=n
   assert str(id) in material_info['items'], f'Missing material {id}'
   info=material_info['items'][str(id)]
   mats[mid]=dict(id=mid,game='ZZZ',name=info['name'],category=info['category'],location=info['location'],sources=info['sources'])
  return result
 for id,name in [('zzz:agent-exp','EXP de agente'),('zzz:weapon-exp','EXP de W-Engine')]:
  mats[id]=dict(id=id,game='ZZZ',name=name,category='EXP',location='Simulação de Combate • contabilize pontos de EXP, não quantidade de itens.',sources=[source(snapshot,'character/1011.json' if id.endswith('agent-exp') else 'weapon/14119.json')])
 engines=[]
 excluded_signatures={enka['avatars'][str(c['Id'])]['WeaponId'] for name,c in rows.items() if name not in live}
 for w in snapshot['weapons']:
  if w['Id'] in excluded_signatures:continue
  wid=f'zzz:weapon-{w["Id"]}';ws=source(snapshot,f'weapon/{w["Id"]}.json','Atributos Lv.60/promoção 5; efeito na fase 1. EXP segue os índices da tabela original; promoção é uma trilha separada.')
  upgrades=[]
  for i,bundle in enumerate(w['Materials'].split('|')):
   upgrades.append(dict(track='ascension',**{'from':i,'to':i+1},costs=amounts({k:int(v) for k,v in (x.split(':') for x in bundle.split(','))})))
  for lv in range(1,60):
   exp=w['Level'][str(lv)]['Exp']
   upgrades.append(dict(track='level',**{'from':lv,'to':lv+1},costs={'zzz:weapon-exp':exp}))
  b=w['BaseProperty'];sub=w['RandProperty'];pct='%' in sub['Format']
  stats={b['Name']:math.floor(b['Value']*(1+(w['Level']['60']['Rate']+w['Stars']['5']['StarRate'])/10000)),sub['Name']+('%' if pct else ''):round(sub['Value']*(1+w['Stars']['5']['RandRate']/10000)/(100 if pct else 1),2)}
  engines.append(dict(id=wid,game='ZZZ',name=w['Name'],rarity=w['Rarity']+1,specialty=next(iter(w['WeaponType'].values())),providerId=str(w['Id']),image='https://enka.network'+enka['weapons'][str(w['Id'])]['ImagePath'],baseStats=stats,statContext='Lv.60 • promoção 5 • efeito na fase 1; passiva exige especialidade compatível.',effect=clean(w['Talents']['1']['Desc']),sources=[ws],upgrades=upgrades))
 discs=[dict(id=f'zzz:disc-{d["Id"]}',game='ZZZ',name=enka['loc']['en'].get(enka['equipment']['Suits'][str(d['Id'])]['Name'],d['Name']),providerId=str(d['Id']),image='https://enka.network'+enka['equipment']['Suits'][str(d['Id'])]['Icon'],twoPiece=clean(d['Desc2']),fourPiece=clean(d['Desc4']),sources=[source(snapshot,f'equipment/{d["Id"]}.json')]) for d in snapshot['discs']]
 cid_by_name={name:old.get(str(c['Id']),old_names.get(name,{})).get('id',f'zzz:{c["Id"]}') for name,c in rows.items() if name in live}
 guide_by_name={g['name']:g for g in guides['rows']}
 for name in sorted(live):
  c=rows[name];pid=str(c['Id']);cid=cid_by_name[name];g=guide_by_name[name];edit=editorial['agents'].get(name,{})
  src=source(snapshot,f'character/{pid}.json','Atributos base Lv.1, sem Núcleo, discos, W-Engine ou buffs. Habilidades usam níveis base; M3/M5 não aumentam custos.')
  stats={v:c['Stats'][k]/(100 if k in ['Crit','CritDamage','PenRate','SpRecover'] else 1) for k,v in STATS.items()}
  special=next(iter(c['SpecialElementType'].values()),'')
  if not isinstance(c['Strategy'],list): c=dict(c,Strategy=['',next(iter(c['WeaponType'].values())),name+' • '+next(iter(c['ElementType'].values()))+' / '+next(iter(c['WeaponType'].values()))])
  characters.append(dict(id=cid,game='ZZZ',name=name,fullName=c.get('FullName',name),rarity=c['Rarity']+1,element=next(iter(c['ElementType'].values())),attributeVariant=special,specialty=next(iter(c['WeaponType'].values())),faction=next(iter(c['Camp'].values())),role=clean(c['Strategy'][1]),description=clean(c['Strategy'][-1]),image='https://enka.network'+enka['avatars'][pid]['CircleIcon'],providerId=pid,sources=[src],baseStats=stats,statContext=src['note'],skillNames={SKILLS[k]:list(dict.fromkeys(v['names'])) for k,v in c['Skill'].items()},mindscapes=[f'M{k} • {v["name"]}' for k,v in c['Talent'].items()],signatureWeaponId=f'zzz:weapon-{enka["avatars"][pid]["WeaponId"]}',additionalAbility=' '.join(c['Passive']['conditions'])))
  def edge(track,a,z,v):costs.append(dict(characterId=cid,track=track,**{'from':a,'to':z},costs=v,source=src))
  for lv,exp in enumerate(c['LevelEXP'][:-1],1):edge('level',lv,lv+1,{'zzz:agent-exp':exp})
  for lv,v in c['Level'].items():
   if v['Materials']:edge('ascension',int(lv)-1,int(lv),amounts(v['Materials']))
  for lv,v in c['Passive']['Materials'].items():edge('core',int(lv)-1,int(lv),amounts(v))
  for sk,v in c['Skill'].items():
   for lv,a in v['Material'].items():
    if a:edge('skill:'+SKILLS[sk],int(lv),int(lv)+1,amounts(a))
  source_build=dict(name='Prydwen • build individual',url=g['url'],checkedAt=g['checkedAt'],patch=g['patch'],note='Síntese editorial. Consulte a página para hipóteses de Mindscape, Potential e cálculos completos.')
  wids=edit.get('weapons',g['weapons']);dopts=edit.get('sets',g['sets'][:2])
  assert all(d['two'] for d in dopts),(name,dopts)
  options=[]
  for i,wid in enumerate(wids):
   w=next(w for w in engines if w['providerId']==str(wid))
   tier='bis' if i==0 else ('accessible' if wid in editorial['accessibleWeapons'] else ('premium' if w['rarity']==5 or wid in editorial['battlePassWeapons'] else 'alternative'))
   note='Passe pago.' if wid in editorial['battlePassWeapons'] else ('A passiva não ativa nesta especialidade; opção apenas pelos atributos.' if w['specialty']!=characters[-1]['specialty'] else '')
   options.append(dict(weaponId=w['id'],tier=tier,note=note))
  builds.append(dict(id='ellen-dialyn' if cid=='zzz:ellen' else f'build:zzz-{pid}',characterId=cid,title='Build recomendada • '+('versão atual' if not edit.get('title') else edit['title']),role=characters[-1]['role'],weaponOptions=options,discOptions=[dict(fourPieceId=f'zzz:disc-{d["four"]}',twoPieceIds=[f'zzz:disc-{n}' for n in d['two'][:4]],note=d.get('note','Escolha um conjunto de 2 peças; não equipe todas as alternativas ao mesmo tempo.')) for d in dopts],slots=dict({'1':'HP','2':'ATK','3':'DEF'},**edit.get('slots',g['slots'])),substats=[edit.get('substats',g['substats'])],skillPriority=['Core']+g['skillPriority'],statAdvice=edit.get('statAdvice',g['statNotes']),benchmarks=edit.get('benchmarks',[]),notes=edit.get('notes','A prioridade de equipamentos considera o conjunto completo da build. Compare metas com o mesmo nível, Núcleo, Mindscape e estado de buffs. As alternativas não preservam necessariamente os mesmos limites de atributos.'),sources=[source_build]))
 for t in editorial['teams']:
  name=t['agent'];g=guide_by_name[name]
  teams.append(dict(id='team:zzz-'+str(rows[name]['Id'])+'-'+str(len(teams)),game='ZZZ',name=name+' • '+t['title'],slots=[[cid_by_name[n] for n in slot] for slot in t['slots']],explanation=t['notes'],source=dict(name='Prydwen • sinergias + composição editorial',url=g['url'],checkedAt=g['checkedAt'],patch=g['patch'],note='As opções de vagas são alternativas contextualizadas; não é uma simulação de DPS.')))
 return canonical(dict(base,schemaVersion=3,version=3,publishedAt='2026-09-10',characters=characters,builds=builds,materials=list(mats.values()),costs=costs,teams=teams,weapons=engines,discSets=discs,coverage=f'ZZZ live até 10/09/2026: {len(live)} agentes, builds individuais, equipamentos e progressão. Cobertura dos outros jogos PARCIAL. EXP usa pontos; confira as observações do planejador.'))

def main():
 def read(p):return json.loads((ROOT/p).read_text())
 pack=generate(read('content/starter.json'),read('content/sources/zzz-3.2.json'),read('content/sources/zzz-enka.json'),read('content/sources/zzz-live-roster.json'),read('content/sources/zzz-builds.json'),read('content/sources/zzz-materials.json'),read('content/sources/zzz-editorial.json'))
 data=json.dumps(pack,ensure_ascii=False,separators=(',',':'))+'\n'
 for p in ['content/starter.json','app/src/main/assets/starter.json']:(ROOT/p).write_text(data)
 print({k:len(pack[k]) for k in ['characters','builds','weapons','discSets','materials','costs','teams']});print('Bytes',len(data.encode()))
if __name__=='__main__':main()
