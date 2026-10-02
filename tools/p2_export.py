# Encoded Logistics - Phase 2 asset + data exporter. Run from the project root: python tools/p2_export.py
# Writes the Phase 2 items, ores, parts, worldgen, tags and recipes; checks that en_us.json has every Phase 2 key.
import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from items_p2 import ITEMS, BLOCK_TEX
import parts_p2 as P
from p1_blocks import term_front, term_side, term_back
A='src/main/resources/assets/encodedlogistics/'; D='src/main/resources/data/'
T=A+'textures/'; M=A+'models/'
RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
MC8='{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n'
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def items():
    for n,fn in ITEMS.items():
        save(fn(),f'{T}item/{n}.png')
        jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'{M}item/{n}.json')
        jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'{A}items/{n}.json')
ORES={'neodymium':{'stone':'neodymium_ore','deep':'deepslate_neodymium_ore'},'tantalum':{'stone':'tantalum_ore','deep':'deepslate_tantalum_ore'}}
def ore_model(name,deep):
    tex={'particle':'minecraft:block/'+('deepslate' if deep else 'stone'),'base':'minecraft:block/'+('deepslate' if deep else 'stone'),'ore':RL(f'block/ore/{name}')}
    if deep: tex['base_top']='minecraft:block/deepslate_top'
    faces=lambda t:{d:{'texture':('#base_top' if (deep and t=='#base' and d in ('up','down')) else t),'cullface':d} for d in ('down','up','north','south','west','east')}
    return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tex,
            'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':faces('#base')},{'from':[0,0,0],'to':[16,16,16],'faces':faces('#ore')}]}
def ores():
    for key,f in BLOCK_TEX.items(): save(f(),f'{T}block/{key}.png')
    for metal,o in ORES.items():
        for kind,name in o.items():
            deep=kind=='deep'
            jd(ore_model(name,deep),f'{M}block/{name}.json')
            jd({'variants':{'':{'model':RL(f'block/{name}')}}},f'{A}blockstates/{name}.json')
            jd({'model':{'type':'minecraft:model','model':RL(f'block/{name}')}},f'{A}items/{name}.json')
            jd({'type':'minecraft:block','pools':[{'entries':[{'type':'minecraft:alternatives','children':[
                {'type':'minecraft:item','condition':'minecraft:tool/can_silk_touch','name':RL(name)},
                {'type':'minecraft:item','modifier':[{'type':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},
                                                    {'type':'minecraft:explosion_decay'}],'name':RL(f'raw_{metal}')}]}],'rolls':1}],
                'random_sequence':RL(f'blocks/{name}')},f'{D}encodedlogistics/loot_table/blocks/{name}.json')
def part_item(name,model):
    jd({'parent':RL(model),'display':{'gui':{'rotation':[30,200,0],'scale':[0.8,0.8,0.8]},'ground':{'scale':[0.4,0.4,0.4]},
        'fixed':{'rotation':[0,180,0],'scale':[0.6,0.6,0.6]},'firstperson_righthand':{'rotation':[0,200,0],'scale':[0.45,0.45,0.45]},
        'thirdperson_righthand':{'rotation':[75,200,0],'translation':[0,2.5,0],'scale':[0.4,0.4,0.4]}}},f'{M}item/{name}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{name}')}},f'{A}items/{name}.json')
def parts():
    B=T+'block/part/'; save(P.parts_tex(),B+'parts.png')
    for kind,acc,inward in (('ingress_port',P.INGRESS,True),('egress_port',P.EGRESS,False)):
        save(P.port_mouth(acc),B+f'{kind}_mouth.png'); save(P.port_side(acc,inward),B+f'{kind}_side.png'); save(P.port_side(acc,inward,True),B+f'{kind}_side_glow.png')
        idle,active=P.port_models(kind,acc,inward); jd(idle,f'{M}part/{kind}.json'); jd(active,f'{M}part/{kind}_active.json'); part_item(kind,f'part/{kind}_active')
    save(P.tap_plate(),B+'inventory_tap_plate.png'); save(P.tap_body(),B+'inventory_tap_body.png'); save(P.tap_body(True),B+'inventory_tap_body_glow.png')
    idle,active=P.tap_models(); jd(idle,f'{M}part/inventory_tap.json'); jd(active,f'{M}part/inventory_tap_active.json'); part_item('inventory_tap','part/inventory_tap_active')
    save(P.sensor_panel(),B+'threshold_sensor_panel.png'); save(P.sensor_lamp(False),B+'threshold_sensor_lamp_off.png'); save(P.sensor_lamp(True),B+'threshold_sensor_lamp_on.png')
    off,on=P.sensor_models(); jd(off,f'{M}part/threshold_sensor.json'); jd(on,f'{M}part/threshold_sensor_on.json'); part_item('threshold_sensor','part/threshold_sensor_on')
    # fabrication terminal: Access Terminal housing, gold bezel stripe, crafting screen
    F=T+'block/fabrication_terminal/'
    save(P.fab_front(term_front(False)),F+'front.png'); save(term_side(),F+'side.png'); save(term_back(),F+'back.png')
    save(P.fab_screen(),F+'screen.png'); open(F+'screen.png.mcmeta','w',newline='\n').write(MC8)
    tx={k:RL(f'block/fabrication_terminal/{k}') for k in ('front','side','back','screen')}; tx['parts']=RL('block/part/parts'); tx['particle']=tx['front']
    face=lambda t,uv:{'texture':t,'uv':uv}
    housing={'from':[1,1,0],'to':[15,15,2.5],'faces':{'north':face('#front',[1,1,15,15]),'south':face('#back',[1,1,15,15]),
             'east':face('#side',[0,0,2.5,14]),'west':face('#side',[0,0,2.5,14]),'up':face('#side',[0,0,14,2.5]),'down':face('#side',[0,0,14,2.5])}}
    stub={'from':[6,6,2.5],'to':[10,10,5],'faces':{f:face('#parts',P.PU['mid']) for f in ('east','west','up','down','south')}}
    screen={'from':[3,4,-0.02],'to':[13,13,-0.02],'faces':{'north':face('#screen',[3,3,13,12])},**GLOW}
    jd({'parent':'minecraft:block/block','textures':tx,'elements':[housing,stub]},f'{M}part/fabrication_terminal.json')
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':[housing,stub,screen]},f'{M}part/fabrication_terminal_online.json')
    part_item('fabrication_terminal','part/fabrication_terminal_online')
def worldgen():
    W=D+'encodedlogistics/worldgen/'
    spec={'neodymium':dict(size=8,air=0.0,count=8,h={'type':'minecraft:trapezoid','min_inclusive':{'absolute':-32},'max_inclusive':{'absolute':48}}),
          'tantalum':dict(size=6,air=0.4,count=4,h={'type':'minecraft:uniform','min_inclusive':{'above_bottom':0},'max_inclusive':{'absolute':-8}})}
    for metal,s in spec.items():
        o=ORES[metal]
        # 26.3: configured features live in worldgen/feature, flat (no "config"), states by id
        jd({'type':'minecraft:ore','discard_chance_on_air_exposure':s['air'],'size':s['size'],'targets':[
            {'state':RL(o['stone']),'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:stone_ore_replaceables'}},
            {'state':RL(o['deep']),'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'}}]},
           W+f'feature/ore_{metal}.json')
        jd({'feature':RL(f'ore_{metal}'),'placement':[{'type':'minecraft:count','count':s['count']},{'type':'minecraft:in_square'},
            {'type':'minecraft:height_range','height':s['h']},{'type':'minecraft:biome'}]},W+f'placed_feature/ore_{metal}.json')
        jd({'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':RL(f'ore_{metal}'),'step':'underground_ores'},
           D+f'encodedlogistics/neoforge/biome_modifier/add_ore_{metal}.json')
def tags():
    blocks=[RL(n) for o in ORES.values() for n in o.values()]
    def merge_tag(path):                                    # the tag also lists the mod's other blocks: add, don't replace
        have=json.load(open(path))['values'] if os.path.exists(path) else []
        jd({'values':have+[b for b in blocks if b not in have]},path)
    merge_tag(D+'minecraft/tags/block/mineable/pickaxe.json'); merge_tag(D+'minecraft/tags/block/needs_iron_tool.json')
    for metal,o in ORES.items():
        jd({'values':[RL(o['stone']),RL(o['deep'])]},D+f'c/tags/block/ores/{metal}.json')
        jd({'values':[RL(o['stone']),RL(o['deep'])]},D+f'c/tags/item/ores/{metal}.json')
        jd({'values':[RL(f'raw_{metal}')]},D+f'c/tags/item/raw_materials/{metal}.json')
        jd({'values':[RL(f'{metal}_ingot')]},D+f'c/tags/item/ingots/{metal}.json')
    jd({'values':[RL(o['stone']) for o in ORES.values()]},D+'c/tags/block/ores_in_ground/stone.json')
    jd({'values':[RL(o['deep']) for o in ORES.values()]},D+'c/tags/block/ores_in_ground/deepslate.json')
    jd({'values':['#c:ores/neodymium','#c:ores/tantalum']},D+'c/tags/block/ores.json'); jd({'values':['#c:ores/neodymium','#c:ores/tantalum']},D+'c/tags/item/ores.json')
    jd({'values':[RL('filter_module'),RL('throughput_module')]},D+'encodedlogistics/tags/item/port_modules.json')
def recipes():
    R=D+'encodedlogistics/recipe/'; E=RL; V=lambda n:'minecraft:'+n
    def res(i,c=1): return {'id':i,'count':c}
    def w(n,o): jd(o,R+n+'.json')
    def shaped(n,pat,key,r,c=1): w(n,{'type':'minecraft:crafting_shaped','category':'misc','pattern':pat,'key':key,'result':res(r,c)})
    def shapeless(n,ings,r,c=1): w(n,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ings,'result':res(r,c)})
    def cook(n,typ,ing,r,xp,t): w(n,{'type':typ,'category':'misc','ingredient':ing,'result':res(r),'experience':xp,'cookingtime':t})
    def litho(n,wafer,add,mask,r): w(n,{'type':E('lithography'),'wafer':wafer,'additive':add,'photomask':mask,'result':res(r),'energy':6000,'time':140})
    for metal,o in ORES.items():
        for src,tag in ((E(f'raw_{metal}'),'raw'),('#c:ores/'+metal,'ore')):
            cook(f'{metal}_ingot_from_smelting_{tag}','minecraft:smelting',src,E(f'{metal}_ingot'),0.7,200)
            cook(f'{metal}_ingot_from_blasting_{tag}','minecraft:blasting',src,E(f'{metal}_ingot'),0.7,100)
    shapeless('doped_silicon',[E('silicon_wafer'),V('redstone')],E('doped_silicon'))
    shaped('memory_photomask',['GFG','FDF','GFG'],{'G':V('glass'),'F':E('ferrite'),'D':E('doped_silicon')},E('memory_photomask'))
    litho('memory_die',E('doped_silicon'),E('ferrite'),E('memory_photomask'),E('memory_die'))
    shapeless('tantalum_capacitor',[E('tantalum_ingot'),E('copper_foil'),E('solder_paste')],E('tantalum_capacitor'),2)
    litho('storage_die_128k',E('doped_silicon'),E('neodymium_ingot'),E('storage_photomask'),E('storage_die_128k'))
    shaped('storage_die_512k',['DND','DMD'],{'D':E('storage_die_128k'),'N':E('neodymium_ingot'),'M':E('memory_die')},E('storage_die_512k'))
    for t in ('128k','512k'):
        shaped(f'storage_drive_{t}',['IFI','IDI','ICI'],{'I':V('iron_ingot'),'F':E('ferrite'),'D':E(f'storage_die_{t}'),'C':E('circuit_substrate')},E(f'storage_drive_{t}'))
    shapeless('ingress_port',[E('circuit_substrate'),E('logic_die'),E('tantalum_capacitor'),V('iron_ingot'),V('hopper')],E('ingress_port'))
    shapeless('egress_port',[E('circuit_substrate'),E('logic_die'),E('tantalum_capacitor'),V('iron_ingot'),V('dropper')],E('egress_port'))
    shapeless('inventory_tap',[E('circuit_substrate'),E('logic_die'),V('chest'),E('ferrite')],E('inventory_tap'))
    shapeless('threshold_sensor',[E('logic_die'),V('redstone_torch'),V('comparator')],E('threshold_sensor'))
    shapeless('filter_module',[E('circuit_substrate'),E('logic_die'),V('paper')],E('filter_module'))
    shapeless('throughput_module',[E('circuit_substrate'),E('memory_die'),E('tantalum_capacitor')],E('throughput_module'))
    shapeless('fabrication_terminal',[E('access_terminal'),V('crafting_table'),E('memory_die')],E('fabrication_terminal'))
def lang():
    L={'block.encodedlogistics.neodymium_ore':'Neodymium Ore','block.encodedlogistics.deepslate_neodymium_ore':'Deepslate Neodymium Ore',
       'block.encodedlogistics.tantalum_ore':'Tantalum Ore','block.encodedlogistics.deepslate_tantalum_ore':'Deepslate Tantalum Ore',
       'item.encodedlogistics.raw_neodymium':'Raw Neodymium','item.encodedlogistics.raw_tantalum':'Raw Tantalum',
       'item.encodedlogistics.neodymium_ingot':'Neodymium Ingot','item.encodedlogistics.tantalum_ingot':'Tantalum Ingot',
       'item.encodedlogistics.doped_silicon':'Doped Silicon','item.encodedlogistics.memory_die':'Memory Die',
       'item.encodedlogistics.memory_photomask':'Memory Photomask','item.encodedlogistics.tantalum_capacitor':'Tantalum Capacitor',
       'item.encodedlogistics.filter_module':'Filter Module','item.encodedlogistics.throughput_module':'Throughput Module',
       'item.encodedlogistics.fuzzy_match_module':'Fuzzy Match Module','item.encodedlogistics.redstone_control_module':'Redstone Control Module',
       'item.encodedlogistics.ingress_port':'Ingress Port','item.encodedlogistics.egress_port':'Egress Port',
       'item.encodedlogistics.inventory_tap':'Inventory Tap','item.encodedlogistics.threshold_sensor':'Threshold Sensor',
       'item.encodedlogistics.fabrication_terminal':'Fabrication Terminal','gui.encodedlogistics.fabrication_terminal':'Fabrication Terminal',
       'gui.encodedlogistics.port.filter':'Filter','gui.encodedlogistics.port.modules':'Modules',
       'gui.encodedlogistics.redstone.ignore':'Redstone: ignored','gui.encodedlogistics.redstone.high':'Redstone: active with signal',
       'gui.encodedlogistics.redstone.low':'Redstone: active without signal','gui.encodedlogistics.redstone.pulse':'Redstone: once per pulse',
       'gui.encodedlogistics.tap.priority':'Priority','gui.encodedlogistics.tap.access':'Access',
       'gui.encodedlogistics.tap.read_write':'Read and write','gui.encodedlogistics.tap.read':'Read only','gui.encodedlogistics.tap.write':'Write only',
       'gui.encodedlogistics.sensor.item':'Item','gui.encodedlogistics.sensor.threshold':'Threshold',
       'gui.encodedlogistics.sensor.above':'Emit when above','gui.encodedlogistics.sensor.below':'Emit when below','gui.encodedlogistics.sensor.equal':'Emit when equal',
       'gui.encodedlogistics.sensor.state.on':'Emitting','gui.encodedlogistics.sensor.state.off':'Off',
       'gui.encodedlogistics.terminal.clear_grid':'Return grid to network',
       'tooltip.encodedlogistics.module.filter':'Adds filter options to a port','tooltip.encodedlogistics.module.throughput':'Faster transfers (up to 3 per port)',
       'tooltip.encodedlogistics.part.lane':'Uses 1 lane'}
    have=json.load(open(A+'lang/en_us.json',encoding='utf-8'))      # en_us.json holds them; report what's missing or differs
    for k,v in L.items():
        if have.get(k)!=v: print('en_us.json:',k,'should be',repr(v))
    return len(L)
if __name__=='__main__':
    items(); ores(); parts(); worldgen(); tags(); recipes(); n=lang()
    print('items',len(ITEMS),'lang',n,'files',sum(len(f) for _,_,f in os.walk('src')))
