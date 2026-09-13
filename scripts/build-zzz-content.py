#!/usr/bin/env python3
"""Offline expansion from a licensed roster snapshot and reviewed factual tables."""
import copy
import json
import re
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
TRACKS=['Basic','Dodge','Assist','Special','Chain']
SPECIAL_ATTRIBUTES={'Miyabi':'Frost','YeShunguang':'Honed Edge','Yixuan':'Auric Ink'}
BASE_ATTRIBUTES={'Frost':'Ice','Honed Edge':'Physical','Auric Ink':'Ether'}

def read(name):
    return json.loads((ROOT/'content/sources'/name).read_text(encoding='utf-8'))

def generate(base, snapshot=None, facts=None, editorial=None):
    snapshot=snapshot or read('zzz-optimizer.json')
    facts=facts or read('zzz-progression-facts.json')
    editorial=editorial or read('zzz-editorial.json')
    pack=copy.deepcopy(base)
    old={c['providerId']:c for c in base['characters'] if c['game']=='ZZZ' and c.get('providerId')}
    legacy={'Ellen':'zzz:ellen','Dialyn':'zzz:dialyn','Lighter':'zzz:lighter','Lycaon':'zzz:lycaon','AstraYao':'zzz:astra'}
    def source(path, costs=False):
        s=facts if costs else snapshot
        return dict(name='ZZZ • fatos de progressão' if costs else 'Zenless Optimizer • catálogo',
                    url=f'https://github.com/{s["repository"]}/blob/{s["sha"]}/{path}',
                    checkedAt=s['checkedAt'],patch=s.get('patch','não informado'))
    chars=[]
    for row in snapshot['rows']:
        key=row['key']
        cid=legacy.get(key,old.get(row['id'],{}).get('id','zzz:'+re.sub(r'(?<!^)(?=[A-Z])','-',key).lower()))
        chars.append(dict(id=cid,game='ZZZ',name=row['name'],rarity=4 if row['rarity']=='A' else 5,
                          element=SPECIAL_ATTRIBUTES.get(key,row['attribute'].title()),specialty=row['specialty'].title(),
                          role=row['specialty'].title(),faction=re.sub(r'(?<!^)(?=[A-Z])',' ',row['faction']),providerId=row['id'],
                          image=f'https://raw.githubusercontent.com/{snapshot["repository"]}/{snapshot["sha"]}/libs/zzz/assets/src/gen/chars/{key}/circle.png',
                          sources=[source(row['file'])]))
    chars.extend(editorial.get('additionalCharacters',[]))
    pack['characters']=[c for c in pack['characters'] if c['game']!='ZZZ']+chars
    # Keep materials already published: existing inventories may reference them.
    materials={m['id']:m for m in pack['materials']}
    costs=[c for c in pack['costs'] if not c['characterId'].startswith('zzz:')]
    def material(mid,name,category,src):
        key='zzz:denny' if mid==10 else 'zzz:'+str(mid)
        materials[key]=dict(id=key,game='ZZZ',name=name,category=category,sources=[src])
        return key
    for c in chars:
        pid=c['providerId']
        if pid not in facts['verifiedProviderIds']:continue
        src=source(f'character/{pid}.json',True)
        denny=material(10,'Denny','Moeda',src)
        exp=material('agent-exp','EXP de agente (pontos; sem Denny de aplicação)','EXP',src)
        hamster=material(100941,'Hamster Cage Pass','Habilidades',src)
        def edge(track,start,end,amounts,extra=''):
            assert end>start and amounts and all(type(v)==int and v>0 for v in amounts.values())
            costs.append(dict(characterId=c['id'],track=track,**{'from':start,'to':end},costs=amounts,
                              source=dict(src,note=extra) if extra else src))
        for n,quantity in enumerate(facts['levelExp'],1):
            edge('EXP de nível (sem Denny)',n,n+1,{exp:quantity},'Pontos de EXP exatos. Não inclui Denny nem escolhe uma combinação de logs; confira o custo de aplicação no jogo.')
        specialty=c['specialty']
        final={'Attack':"Pioneer’s Attack",'Stun':'Buster','Anomaly':'Controller','Support':'Ruler','Defense':'Defender','Rupture':'Destroyer'}[specialty]
        seal_names=[f'Basic {specialty} Certification Seal',f'Advanced {specialty} Certification Seal',f'{final} Certification Seal']
        seals=[material(mid,name,'Promoção',src) for mid,name in zip(facts['promotionFamilies'][specialty],seal_names)]
        for n,(money,tier,quantity) in enumerate(facts['promotion']):
            edge('Promoção',n,n+1,{denny:money,seals[tier]:quantity})
        attribute=BASE_ATTRIBUTES.get(c['element'],c['element'])
        chips=[material(mid,f'{tier} {attribute} Chip','Habilidades',src) for mid,tier in zip(facts['skillFamilies'][attribute],['Basic','Advanced','Specialized'])]
        for track in TRACKS:
            for n,(money,tier,quantity) in enumerate(facts['skill'],1):
                amounts={denny:money,chips[tier]:quantity}
                if n==11:amounts[hamster]=1
                edge(track,n,n+1,amounts)
        if pid in facts['coreBindings']:
            data,boss=[material(mid,facts['coreMaterialNames'][str(mid)],'Core Skill',src) for mid in facts['coreBindings'][pid]]
            for n,(money,data_n,boss_n) in enumerate(facts['core']):
                amounts={denny:money}
                if data_n:amounts[data]=data_n
                if boss_n:amounts[boss]=boss_n
                edge('Core',n,n+1,amounts,'0 = sem melhoria; 1–6 = A–F. Não inclui Potential.')
    pack['materials']=list(materials.values())
    pack['costs']=costs
    pack['builds']=[b for b in pack['builds'] if not b['characterId'].startswith('zzz:')]+editorial['builds']
    pack['teams']=[t for t in pack['teams'] if t['game']!='ZZZ']+editorial['teams']
    pack['wEngines']=editorial.get('wEngines',[])
    pack['driveDiscs']=editorial.get('driveDiscs',[])
    pack.update(version=3,publishedAt='2026-09-12',coverage=f'PARCIAL: {len(pack["characters"])} personagens; {len(chars)} agentes ZZZ, {len(editorial["builds"])} builds ZZZ. Promoção, habilidades 1–12 e EXP em pontos para 58 agentes; Core para {len(facts["coreBindings"])}. Claret: custos e Provider ID ainda não verificados. EXP não inclui Denny de aplicação de logs. Genshin preservado; HSR/WuWa sem expansão.')
    return pack

if __name__=='__main__':
    # Every entry point runs the same complete pipeline.
    import runpy
    runpy.run_path(str(ROOT/'scripts/make-starter.py'),run_name='__main__')
