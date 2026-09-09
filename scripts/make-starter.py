"""Reproducible, intentionally partial editorial pack. Never scrapes or invents costs."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def src(name,url,patch='Não informado',updated=None):
    return dict(name=name,url=url,checkedAt='2026-09-08',updatedAt=updated,patch=patch)
ell=src('Prydwen','https://www.prydwen.gg/zenless/characters/ellen','2.5','2026-08-19')
ting=src('Prydwen','https://www.prydwen.gg/star-rail/characters/tingyun')
ben=src('KQM','https://keqingmains.com/q/bennett-quickguide/','5.7')
enc=src('Prydwen','https://www.prydwen.gg/wuthering-waves/characters/encore')
chars=[]
def char(id,game,name,element='',role='',specialty='',rarity=5,pid='',source=None,image=''):
    chars.append(dict(id=id,game=game,name=name,element=element,role=role,specialty=specialty,rarity=rarity,providerId=pid,sources=[source] if source else [],image=image))
char('zzz:ellen','ZZZ','Ellen','Ice','DPS','Attack',pid='1191',source=ell,image='https://cdn.prydwen.gg/images/zenless-zone-zero/characters/ellen_card.webp')
char('zzz:dialyn','ZZZ','Dialyn',role='Stun',source=ell)
char('zzz:lighter','ZZZ','Lighter',role='Stun',source=ell)
char('zzz:lycaon','ZZZ','Lycaon',role='Stun',pid='1141',source=ell)
char('zzz:astra','ZZZ','Astra Yao',role='Support',source=ell)
char('hsr:tingyun','HSR','Tingyun','Lightning','Support','Harmony',4,'1202',ting,'https://cdn.prydwen.gg/images/honkai-star-rail/characters/tingyun_card.webp')
char('gi:bennett','GENSHIN','Bennett','Pyro','Healing Support','Sword',4,'10000032',ben)
char('gi:xiangling','GENSHIN','Xiangling',pid='10000023',rarity=4,source=ben)
char('gi:xingqiu','GENSHIN','Xingqiu',pid='10000025',rarity=4,source=ben)
char('gi:yelan','GENSHIN','Yelan',pid='10000060',source=ben)
char('ww:encore','WUWA','Encore','Fusion','DPS','Rectifier',source=enc,image='https://cdn.prydwen.gg/images/ww/characters/encore_card.webp')
char('ww:lupa','WUWA','Lupa',source=enc)
char('ww:sanhua','WUWA','Sanhua',rarity=4,source=enc)
char('ww:shorekeeper','WUWA','Shorekeeper',source=enc)
def bench(stat,adequate,excellent,unit='',context='',cap=None):
    return dict(stat=stat,adequate=adequate,excellent=excellent,low=round(adequate*.8,2),unit=unit,context=context,cap=cap)
def build(id,cid,title,role,source,**kw):
    return dict(id=id,characterId=cid,title=title,role=role,sources=[source],**kw)
builds=[
 build('ellen-dialyn','zzz:ellen','DPS com Dialyn','DPS',ell,
   bis=['Deep Sea Visitor S1'],premium=['Myriad Eclipse S1'],accessible=['Starlight Engine S5'],
   sets=['4 Puffer Electro + 2 Woodpecker Electro (com Dialyn)','4 Woodpecker Electro + 2 Puffer Electro'],
   slots={'4':'CRIT DMG','5':'PEN Ratio / Ice DMG','6':'ATK%'},substats=['CRIT DMG','CRIT Rate','ATK%'],
   benchmarks=[bench('ATK',2600,3000,context='Referência nível 60; não compara buffs automaticamente.'),bench('CRIT Rate',90,100,'%',context='Confira buffs de motor e mindscape; ajuste a leitura para o contexto da fonte.',cap=100)],
   skillPriority=['Chain','Special','Basic','Dodge','Assist'],notes='Puffer depende de Dialyn. Confira Potential e condições no guia. Faixas do comparador são editoriais.'),
 build('tingyun-support','hsr:tingyun','Suporte de hypercarry','Support',ting,
   bis=['A Grounded Ascent S1'],premium=['Dance! Dance! Dance! S5'],accessible=['Meshing Cogs S5'],
   sets=["4 Sacerdos’ Relived Ordeal + 2 Sprightly Vonwacq",'Alternativa: Messenger Traversing Hackerspace'],
   slots={'Body':'ATK% / HP%','Feet':'SPD','Sphere':'ATK% / HP%','Rope':'Energy Regen'},substats=['SPD','ATK%','HP','DEF'],
   benchmarks=[bench('ATK',2400,2600,context='Nível 80; ajuste ao limite do buff para seu DPS.'),bench('SPD',143,160,context='134 também é breakpoint viável; ajuste a ordem dos turnos.')],
   skillPriority=['Skill','Ultimate','Talent','Basic']),
 build('bennett-healer','gi:bennett','Suporte — Double Pyro','Healing Support',ben,
   bis=['Mistsplitter Reforged (se ER atendida)'],premium=['Aquila Favonia'],accessible=['Sapwood Blade'],
   sets=['4 Noblesse Oblige','Alternativa: Scroll of the Hero of Cinder City'],
   slots={'Sands':'ER / HP%','Goblet':'HP%','Circlet':'Healing Bonus / HP%'},substats=['Energy Recharge','HP%'],
   benchmarks=[bench('Energy Recharge',175,220,'%',context='Double Pyro; rotação 20s; geração de partículas e Favonius alteram a exigência. Mais ER não implica maior DPS.')],
   skillPriority=['Burst','Skill','Normal Attack'],notes='O buff depende do ATK base, não do ATK total dos artefatos. Guia de versão 5.7; consulte revisões posteriores.'),
 build('encore-carry','ww:encore','Hypercarry','DPS',enc,
   bis=['Stringmaster R1'],premium=['Rime-Draped Sprouts R1'],accessible=['Jinzhou Keeper R5'],
   sets=['Molten Rift; Inferno Rider'],slots={'4':'CRIT','3a':'Fusion DMG','3b':'Fusion DMG / ATK%','1a':'ATK%','1b':'ATK%'},
   substats=['Energy Regen','CRIT','ATK%'],benchmarks=[bench('ATK',2000,2400,context='S0, nível 90, fora de combate.'),bench('CRIT Rate',60,75,'%',context='S0, fora de combate.',cap=100)],
   skillPriority=['Liberation','Forte','Skill','Intro','Basic'])
]
materials=[];costs=[]
def add_cost(cid,game,track,source,items):
    mapping={}
    for key,name,quantity,category in items:
        mid=game.lower()+':'+key
        if not any(x['id']==mid for x in materials):
            materials.append(dict(id=mid,game=game,name=name,category=category,sources=[source]))
        mapping[mid]=quantity
    costs.append(dict(characterId=cid,track=track,**{'from':0,'to':6},costs=mapping,source=source))
add_cost('hsr:tingyun','HSR','Ascensão total (sem EXP)',ting,[
 ('credit','Credit',246400,'Moeda'),('scionette','Immortal Scionette',12,'Comum'),('aeroblossom','Immortal Aeroblossom',13,'Comum'),('lumintwig','Immortal Lumintwig',12,'Comum'),('crown','Lightning Crown of the Past Shadow',50,'Boss')])
add_cost('ww:encore','WUWA','Ascensão total (sem EXP)',enc,[
 ('shell','Shell Credits',170000,'Moeda'),('lf-core','LF Whisperin Core',4,'Comum'),('mf-core','MF Whisperin Core',12,'Comum'),('hf-core','HF Whisperin Core',12,'Comum'),('ff-core','FF Whisperin Core',4,'Comum'),('rage','Rage Tacet Core',46,'Boss'),('pecok','Pecok Flower',60,'Coleta')])
# Empty currencies are useful for manual checklists; no unsourced upgrade quantities.
materials.extend([dict(id='zzz:denny',game='ZZZ',name='Denny',category='Moeda'),dict(id='genshin:mora',game='GENSHIN',name='Mora',category='Moeda')])
teams=[dict(id='ellen-source',game='ZZZ',name='Ellen • referência de atordoamento',slots=[['zzz:ellen'],['zzz:dialyn','zzz:lighter','zzz:lycaon'],['zzz:astra']],explanation='Stun abre a janela de dano; Astra oferece suporte.',source=ell),dict(id='bennett-double-hydro',game='GENSHIN',name='Bennett • Double Hydro',slots=[['gi:bennett'],['gi:xiangling'],['gi:xingqiu'],['gi:yelan']],explanation='Vaporização com dois aplicadores Hydro.',source=ben),dict(id='encore-carry',game='WUWA',name='Encore • carry',slots=[['ww:encore'],['ww:lupa','ww:sanhua'],['ww:shorekeeper']],explanation='Alternativas indicadas pela fonte; rotação depende do suporte.',source=enc)]
pack=dict(schemaVersion=1,version=1,publishedAt='2026-09-08',coverage='Pacote inicial PARCIAL: 14 personagens, 4 builds e 2 tabelas totais de ascensão. Não contém todos os personagens, custos por nível, armas, habilidades, banners ou calendário de domínios. Atualize por pacote JSON; nenhum conteúdo ausente é estimado.',characters=chars,builds=builds,materials=materials,costs=costs,teams=teams,banners=[])
text=json.dumps(pack,ensure_ascii=False,indent=2)+'\n'
(ROOT/'content/starter.json').write_text(text)
(ROOT/'app/src/main/assets/starter.json').write_text(text)
example=dict(schemaVersion=1,game='GENSHIN',owned=[dict(characterId='gi:bennett',level=40,copies=1,ascension=1,stats={'Energy Recharge':180.0},skills={'Burst':4,'Skill':4,'Normal Attack':1})])
(ROOT/'content/import-example.json').write_text(json.dumps(example,indent=2)+'\n')
