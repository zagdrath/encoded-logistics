# Phase 1 recipes and lang. Run from the project root: python tools/p1_data.py
# Writes the recipes; checks that en_us.json has every Phase 1 key (it prints any that are missing or differ).
# Tiers past REGISTERED carry a neoforge:registered condition, so their recipes load once those items are added.
import json,os
D='src/main/resources/data/encodedlogistics/recipe/'; os.makedirs(D,exist_ok=True)
E=lambda n:'encodedlogistics:'+n; V=lambda n:'minecraft:'+n
def res(i,c=1): return {'id':i,'count':c}
REGISTERED=['8k','32k','128k','512k']
P2=('storage_die_128k','storage_die_512k','storage_drive_128k','storage_drive_512k')   # tools/p2_export.py writes these
def w(name,obj):
    if name in P2: return
    later=[t for t in ('128k','512k','2m') if t not in REGISTERED and name.endswith('_'+t)]
    if later: obj={'neoforge:conditions':[{'type':'neoforge:registered','value':obj['result']['id']}],**obj}
    json.dump(obj,open(D+name+'.json','w',newline='\n'),indent=1)
def shaped(name,pattern,key,result,count=1): w(name,{'type':'minecraft:crafting_shaped','category':'misc','pattern':pattern,'key':key,'result':res(result,count)})
def shapeless(name,ings,result,count=1): w(name,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ings,'result':res(result,count)})
def cook(name,typ,ing,result,xp=0.1,t=200): w(name,{'type':typ,'category':'misc','ingredient':ing,'result':res(result),'experience':xp,'cookingtime':t})
def cut(name,ing,result,count): w(name,{'type':'minecraft:stonecutting','ingredient':ing,'result':res(result,count)})
def litho(name,wafer,additive,mask,result,energy=4000,time=100): w(name,{'type':E('lithography'),'wafer':wafer,'additive':additive,'photomask':mask,'result':res(result),'energy':energy,'time':time})
cook('silica_from_blasting','minecraft:blasting',V('sand'),E('silica'),0.1,100)
shapeless('silica_blend',[E('silica')]*4+[V('coal')],E('silica_blend'))
cook('silicon_boule_from_smelting','minecraft:smelting',E('silica_blend'),E('silicon_boule'),0.3,200)
cut('silicon_wafer_from_stonecutting',E('silicon_boule'),E('silicon_wafer'),4)
shapeless('ferrite',[V('iron_ingot'),V('redstone')],E('ferrite'))
cut('copper_foil_from_stonecutting',V('copper_ingot'),E('copper_foil'),2)
shapeless('fiberglass',[V('glass'),V('string'),V('string')],E('fiberglass'),2)
shapeless('solder_paste',[V('copper_ingot'),V('slime_ball')],E('solder_paste'),2)
shapeless('circuit_substrate',[E('fiberglass'),E('copper_foil'),E('solder_paste')],E('circuit_substrate'))
shaped('logic_photomask',['GFG','FRF','GFG'],{'G':V('glass'),'F':E('ferrite'),'R':V('redstone')},E('logic_photomask'))
shaped('storage_photomask',['GFG','FFF','GFG'],{'G':V('glass'),'F':E('ferrite')},E('storage_photomask'))
litho('logic_die',E('silicon_wafer'),V('redstone'),E('logic_photomask'),E('logic_die'))
litho('storage_die_8k',E('silicon_wafer'),E('ferrite'),E('storage_photomask'),E('storage_die_8k'))
tiers=['8k','32k','128k','512k','2m']
for a,b in zip(tiers,tiers[1:]):
    shaped(f'storage_die_{b}',['DSD','DLD'],{'D':E(f'storage_die_{a}'),'S':E('solder_paste'),'L':E('logic_die')},E(f'storage_die_{b}'))
for t in tiers:
    shaped(f'storage_drive_{t}',['IFI','IDI','ICI'],{'I':V('iron_ingot'),'F':E('ferrite'),'D':E(f'storage_die_{t}'),'C':E('circuit_substrate')},E(f'storage_drive_{t}'))
shapeless('access_terminal',[E('circuit_substrate'),E('logic_die'),V('glass_pane'),E('network_cable')],E('access_terminal'))
shaped('lithography_press',['IGI','FRF','ICI'],{'I':V('iron_ingot'),'G':V('glass'),'F':E('ferrite'),'R':V('redstone'),'C':E('circuit_substrate')},E('lithography_press'))
L='src/main/resources/assets/encodedlogistics/lang/'; os.makedirs(L,exist_ok=True)
names={'silica':'Silica','silica_blend':'Silica Blend','silicon_boule':'Silicon Boule','silicon_wafer':'Silicon Wafer','ferrite':'Ferrite','copper_foil':'Copper Foil',
       'fiberglass':'Fiberglass','solder_paste':'Solder Paste','circuit_substrate':'Circuit Substrate','logic_die':'Logic Die',
       'logic_photomask':'Logic Photomask','storage_photomask':'Storage Photomask'}
lang={f'item.encodedlogistics.{k}':v for k,v in names.items()}
for t,lbl in zip(tiers,['8K','32K','128K','512K','2M']):
    lang[f'item.encodedlogistics.storage_die_{t}']=f'{lbl} Storage Die'; lang[f'item.encodedlogistics.storage_drive_{t}']=f'{lbl} Storage Drive'
lang.update({'block.encodedlogistics.lithography_press':'Lithography Press','block.encodedlogistics.drive_bay':'Drive Bay',
  'item.encodedlogistics.access_terminal':'Access Terminal','gui.encodedlogistics.access_terminal':'Access Terminal',
  'gui.encodedlogistics.terminal.search':'Search...','gui.encodedlogistics.terminal.sort.name':'Sort by name',
  'gui.encodedlogistics.terminal.sort.count':'Sort by amount','gui.encodedlogistics.terminal.sort.mod':'Sort by mod',
  'gui.encodedlogistics.terminal.dir.asc':'Ascending','gui.encodedlogistics.terminal.dir.desc':'Descending',
  'gui.encodedlogistics.terminal.offline':'Network offline','gui.encodedlogistics.lithography_press.progress':'Exposure %s%%',
  'tooltip.encodedlogistics.photomask.reusable':'Reusable - not consumed by the Lithography Press',
  'tooltip.encodedlogistics.drive.bytes':'%s / %s bytes used','tooltip.encodedlogistics.drive.types':'%s / %s types',
  'jei.encodedlogistics.lithography':'Lithography','emi.category.encodedlogistics.lithography':'Lithography'})
have=json.load(open(L+'en_us.json',encoding='utf-8'))
for k,v in lang.items():
    if have.get(k)!=v: print('en_us.json:',k,'should be',repr(v))
print(len(os.listdir(D)),'recipes',len(lang),'lang keys')
