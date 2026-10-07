# Encoded Logistics - Phase 3 asset + data exporter. Run from the project root: python tools/p3_export.py
# Writes the Phase 3 items, ore, blocks, parts, worldgen, tags and recipes; checks that en_us.json has every Phase 3 key.
import os, json, sys, random
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from item_display import centred
from el_style import *
from items_p3 import ITEMS, BLOCK_TEX, PROCESSOR, CRAFTING
import blocks_p3 as B
from p1_blocks import term_front, term_side, term_back, press_glass
A='src/main/resources/assets/encodedlogistics/'; D='src/main/resources/data/'
T=A+'textures/'; M=A+'models/'; RL=lambda p:'encodedlogistics:'+p
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'}
MC8='{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n'
MC6='{\n  "animation": {\n    "frametime": 6,\n    "interpolate": false\n  }\n}\n'
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def merge_tag(path,values):                                     # shared tags list other phases' entries too: add, don't replace
    have=json.load(open(path))['values'] if os.path.exists(path) else []
    jd({'values':have+[v for v in values if v not in have]},path)
def face(t,uv,**k): d={'texture':t,'uv':uv}; d.update(k); return d
def tr(x0,y0,w,h): return (16-x0-w,16-y0-h,16-x0,16-y0)
PU={'lit':[0,0,2,2],'mid':[2,0,4,2],'dark':[4,0,6,2],'shadow':[6,0,8,2]}
def items():
    for n,fn in ITEMS.items():
        save(fn(),f'{T}item/{n}.png')
        jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'{M}item/{n}.json')
        plain={'type':'minecraft:model','model':RL(f'item/{n}')}
        if n.startswith('encoded_schematic_'):                  # Shift shows the output (SchematicOutputModel)
            jd({'model':{'type':'minecraft:condition','property':'minecraft:keybind_down','keybind':'key.sneak',
                'on_true':{'type':RL('schematic_output'),'fallback':plain},'on_false':plain}},f'{A}items/{n}.json')
        else: jd({'model':plain},f'{A}items/{n}.json')
def ore():
    for k,f in BLOCK_TEX.items(): save(f(),f'{T}block/{k}.png')
    name='deepslate_gallium_ore'
    faces=lambda t:{d:{'texture':('#base_top' if (t=='#base' and d in ('up','down')) else t),'cullface':d} for d in ('down','up','north','south','west','east')}
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':{'particle':'minecraft:block/deepslate','base':'minecraft:block/deepslate',
        'base_top':'minecraft:block/deepslate_top','ore':RL(f'block/ore/{name}')},
        'elements':[{'from':[0,0,0],'to':[16,16,16],'faces':faces('#base')},{'from':[0,0,0],'to':[16,16,16],'faces':faces('#ore')}]},f'{M}block/{name}.json')
    jd({'variants':{'':{'model':RL(f'block/{name}')}}},f'{A}blockstates/{name}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'block/{name}')}},f'{A}items/{name}.json')
    jd({'type':'minecraft:block','pools':[{'entries':[{'type':'minecraft:alternatives','children':[
        {'type':'minecraft:item','condition':'minecraft:tool/can_silk_touch','name':RL(name)},
        {'type':'minecraft:item','modifier':[{'type':'minecraft:apply_bonus','enchantment':'minecraft:fortune','formula':'minecraft:ore_drops'},
                                            {'type':'minecraft:explosion_decay'}],'name':RL('raw_gallium')}]}],'rolls':1}],
        'random_sequence':RL(f'blocks/{name}')},f'{D}encodedlogistics/loot_table/blocks/{name}.json')
    W=D+'encodedlogistics/worldgen/'
    # 26.3: configured features live in worldgen/feature, flat (no "config"), states by id
    jd({'type':'minecraft:ore','discard_chance_on_air_exposure':0.6,'size':4,'targets':[
        {'state':RL(name),'target':{'predicate_type':'minecraft:tag_match','tag':'minecraft:deepslate_ore_replaceables'}}]},W+'feature/ore_gallium.json')
    jd({'feature':RL('ore_gallium'),'placement':[{'type':'minecraft:count','count':2},{'type':'minecraft:in_square'},
        {'type':'minecraft:height_range','height':{'type':'minecraft:trapezoid','min_inclusive':{'above_bottom':0},'max_inclusive':{'absolute':-32}}},
        {'type':'minecraft:biome'}]},W+'placed_feature/ore_gallium.json')
    jd({'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':RL('ore_gallium'),'step':'underground_ores'},
       D+'encodedlogistics/neoforge/biome_modifier/add_ore_gallium.json')
    for t in ('block','item'):
        jd({'values':[RL(name)]},D+f'c/tags/{t}/ores/gallium.json')
    merge_tag(D+'c/tags/block/ores_in_ground/deepslate.json',[RL(name)])
    merge_tag(D+'c/tags/block/ores.json',['#c:ores/gallium']); merge_tag(D+'c/tags/item/ores.json',['#c:ores/gallium'])
    jd({'values':[RL('raw_gallium')]},D+'c/tags/item/raw_materials/gallium.json'); jd({'values':[RL('gallium_ingot')]},D+'c/tags/item/ingots/gallium.json')
    jd({'values':[RL('gallium_dust')]},D+'c/tags/item/dusts/gallium.json'); merge_tag(D+'c/tags/item/dusts.json',['#c:dusts/gallium'])
    blocks=[RL(name),RL('fabricator'),RL('gateway'),RL('scheduler_core'),RL('job_buffer'),RL('thread_unit')]
    merge_tag(D+'minecraft/tags/block/mineable/pickaxe.json',blocks); merge_tag(D+'minecraft/tags/block/needs_iron_tool.json',[RL(name)])
    jd({'values':[RL('scheduler_core'),RL('job_buffer'),RL('thread_unit')]},D+'encodedlogistics/tags/block/scheduler_parts.json')
def fabricator():
    P=T+'block/fabricator/'
    save(B.fab_front(),P+'front.png'); save(B.fab_side(305),P+'side.png'); save(B.fab_side(307),P+'top.png'); save(B.fab_lights(False),P+'lights.png')
    save(B.fab_lights(True),P+'lights_on.png'); save(B.fab_wash(),P+'wash.png'); save(B.fab_arm(),P+'arm.png'); save(press_glass(),P+'glass.png')
    tx={k:RL(f'block/fabricator/{k}') for k in ('front','side','top','lights','lights_on','arm','glass','wash')}
    tx.update({'parts':RL('block/parts_steel'),'leds':RL('block/leds'),'leds_glow':RL('block/leds_glow'),'particle':RL('block/fabricator/side')})
    els=[{'from':[0,0,2],'to':[16,16,16],'faces':{'north':face('#front',[0,0,16,16]),'south':face('#side',[0,0,16,16]),'east':face('#side',[0,0,14,16]),
          'west':face('#side',[2,0,16,16]),'up':face('#top',[0,2,16,16]),'down':face('#side',[0,0,16,14])}}]
    x0,y0,w,h=B.WIN
    for (a,b,c,d) in ((0,0,16,y0),(0,y0+h,16,16-y0-h),(0,y0,x0,h),(x0+w,y0,16-x0-w,h)):
        X0,Y0,X1,Y1=tr(a,b,c,d); f={'north':face('#front',[a,b,a+c,b+d])}
        f['up']=face('#top',[X0,0,X1,2]) if Y1==16 else face('#parts',PU['lit']); f['down']=face('#side',[X0,14,X1,16]) if Y0==0 else face('#parts',PU['shadow'])
        f['east']=face('#side',[14,16-Y1,16,16-Y0]) if X1==16 else face('#parts',PU['dark']); f['west']=face('#side',[0,16-Y1,2,16-Y0]) if X0==0 else face('#parts',PU['mid'])
        els.append({'from':[X0,Y0,0],'to':[X1,Y1,2],'faces':f})
    WX0,WY0,WX1,WY1=tr(x0,y0,w,h)
    arm=[{'from':[WX0,WY1-1.5,0.9],'to':[WX1,WY1-0.5,1.8],'faces':{'north':face('#arm',[0,0,10,1]),'down':face('#arm',[0,1,10,2])}},       # gantry beam
         {'from':[7,WY1-4.5,0.8],'to':[9,WY1-1.5,1.8],'faces':{'north':face('#arm',[0,2,2,5]),'east':face('#arm',[2,2,3,5]),'west':face('#arm',[0,2,1,5])}},   # head
         {'from':[7.5,WY1-5.5,1.0],'to':[8.5,WY1-4.5,1.6],'faces':{'north':face('#arm',[0,6,1,7]),'east':face('#arm',[0,6,1,7]),'west':face('#arm',[0,6,1,7])}}]
    lights=lambda t:{'from':[WX0+1,WY1-0.5,0.6],'to':[WX1-1,WY1,1.6],'faces':{'down':face(t,[0,0,8,1]),'north':face(t,[0,1,8,2])}}
    glass={'from':[WX0,WY0,0.4],'to':[WX1,WY1,0.4],'faces':{'north':face('#glass',[0,0,10,7]),'south':face('#glass',[0,0,10,7])}}
    led=lambda t,uv:{'from':[3,2,-0.02],'to':[5,3,0],'faces':{'north':face(t,uv)}}
    idle=els+arm+[lights('#lights'),led('#leds',[0,0,2,1]),glass]
    g1=lights('#lights_on'); g1.update(GLOW)
    wash={'from':[WX0,WY0,1.95],'to':[WX1,WY1,1.95],'faces':{'north':face('#wash',[0,0,10,8])},**GLOW}
    active=els+[wash]+arm+[lights('#lights_on'),g1,led('#leds',[6,0,8,1]),{**led('#leds_glow',[6,0,8,1]),**GLOW},glass]
    for n,e in (('fabricator',idle),('fabricator_active',active)):
        jd({'parent':'minecraft:block/block','render_type':'minecraft:translucent','textures':tx,'elements':e},f'{M}block/{n}.json')
    rot={'north':{},'east':{'y':90},'south':{'y':180},'west':{'y':270}}
    jd({'variants':{f'facing={f},active={a}':{'model':RL('block/fabricator'+('_active' if a=='true' else '')),**r} for f,r in rot.items() for a in ('false','true')}},f'{A}blockstates/fabricator.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/fabricator')}},f'{A}items/fabricator.json')
def gateway():
    P=T+'block/gateway/'; save(B.gateway_face(),P+'face.png'); save(B.gateway_face(True,True),P+'face_glow.png')
    cube=lambda t:{'from':[0,0,0],'to':[16,16,16],'faces':{d:face(t,[0,0,16,16]) for d in ('north','south','east','west','up','down')}}
    tx={'face':RL('block/gateway/face'),'glow':RL('block/gateway/face_glow'),'particle':RL('block/gateway/face')}
    jd({'parent':'minecraft:block/block','textures':tx,'elements':[cube('#face')]},f'{M}block/gateway.json')
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':[cube('#face'),{**cube('#glow'),**GLOW}]},f'{M}block/gateway_active.json')
    jd({'variants':{'active=false':{'model':RL('block/gateway')},'active=true':{'model':RL('block/gateway_active')}}},f'{A}blockstates/gateway.json')
    jd({'model':{'type':'minecraft:model','model':RL('block/gateway')}},f'{A}items/gateway.json')
def scheduler():
    P=T+'block/scheduler/'
    for kind in ('scheduler_core','job_buffer','thread_unit'):
        for m in range(16): save(B.sched_tex(kind,m),P+f'{kind}_ctm_{m:02d}.png')
    save(B.core_display(8,True),P+'core_display.png'); open(P+'core_display.png.mcmeta','w',newline='\n').write(MC6)
    save(B.core_display(8,False),P+'core_display_unformed.png'); open(P+'core_display_unformed.png.mcmeta','w',newline='\n').write(MC6)
    save(B.thread_glow(),P+'thread_unit_glow.png')
    cube=lambda t:{'from':[0,0,0],'to':[16,16,16],'faces':{d:face(t,[0,0,16,16]) for d in ('north','south','east','west','up','down')}}
    def model(kind,mask,extra=None):
        tx={'base':RL(f'block/scheduler/{kind}_ctm_{mask:02d}'),'particle':RL(f'block/scheduler/{kind}_ctm_00')}
        els=[cube('#base')]
        if extra: tx['glow']=RL(f'block/scheduler/{extra}'); els.append({**cube('#glow'),**GLOW})
        return {'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':els}
    # unformed models (static) and formed fallbacks (mask 15); formed faces come from the dynamic CTM model
    jd(model('scheduler_core',0,'core_display_unformed'),f'{M}block/scheduler/scheduler_core.json')
    jd(model('scheduler_core',15,'core_display'),f'{M}block/scheduler/scheduler_core_formed.json')
    jd(model('job_buffer',0),f'{M}block/scheduler/job_buffer.json'); jd(model('job_buffer',15),f'{M}block/scheduler/job_buffer_formed.json')
    jd(model('thread_unit',0),f'{M}block/scheduler/thread_unit.json'); jd(model('thread_unit',15),f'{M}block/scheduler/thread_unit_formed.json')
    jd(model('thread_unit',15,'thread_unit_glow'),f'{M}block/scheduler/thread_unit_formed_active.json')
    S=lambda n:RL(f'block/scheduler/{n}')
    # every state uses the connected model (SchedulerModel), which picks the masks and overlays itself
    for k in ('scheduler_core','job_buffer','thread_unit'): jd({'variants':{'':{'type':RL('scheduler'),'block':k}}},f'{A}blockstates/{k}.json')
    for k in ('scheduler_core','job_buffer','thread_unit'): jd({'model':{'type':'minecraft:model','model':S(k)}},f'{A}items/{k}.json')
def encoder():
    F=T+'block/schematic_encoder/'
    fr=term_front(False)
    for x in range(3,13): put(fr,x,13,PROCESSOR[2] if x<8 else PROCESSOR[1])
    save(fr,F+'front.png'); save(term_side(),F+'side.png'); save(term_back(),F+'back.png')
    MINT=B.MINT; strip=Image.new('RGBA',(16,128),(0,0,0,0))
    for f in range(8):
        P_=lambda x,y,c: strip.putpixel((3+x,16*f+3+y),c+(255,))
        for y in range(9):
            for x in range(10): P_(x,y,MINT[0])
        for x in range(10): P_(x,0,MINT[1])
        for gy in range(2,5):
            for gx in range(0,3): P_(gx,gy,MINT[2])
        P_(4,3,MINT[3]); P_(5,3,MINT[3])
        for y in range(2,8):                                    # schematic card being written
            for x in range(7,10): P_(x,y,B.GOLD[1] if y<3 else MINT[1])
        for x in range(7,10):
            if 3+(f%5)>=3: P_(x,min(7,3+f%5),B.GOLD[3])
        for x in range(0,6): P_(x,7,MINT[3] if x<=f%6 else MINT[1])   # write progress
    save(strip,F+'screen.png'); open(F+'screen.png.mcmeta','w',newline='\n').write(MC8)
    tx={k:RL(f'block/schematic_encoder/{k}') for k in ('front','side','back','screen')}; tx['parts']=RL('block/part/parts'); tx['particle']=tx['front']
    housing={'from':[1,1,0],'to':[15,15,2.5],'faces':{'north':face('#front',[1,1,15,15]),'south':face('#back',[1,1,15,15]),
             'east':face('#side',[0,0,2.5,14]),'west':face('#side',[0,0,2.5,14]),'up':face('#side',[0,0,14,2.5]),'down':face('#side',[0,0,14,2.5])}}
    stub={'from':[6,6,2.5],'to':[10,10,5],'faces':{f:face('#parts',PU['mid']) for f in ('east','west','up','down','south')}}
    screen={'from':[3,4,-0.02],'to':[13,13,-0.02],'faces':{'north':face('#screen',[3,3,13,12])},**GLOW}
    jd({'parent':'minecraft:block/block','textures':tx,'elements':[housing,stub]},f'{M}part/schematic_encoder.json')
    jd({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':tx,'elements':[housing,stub,screen]},f'{M}part/schematic_encoder_online.json')
    jd({'parent':RL('part/schematic_encoder_online'),'display':centred({'gui':{'rotation':[30,200,0],'scale':[0.8,0.8,0.8]},'ground':{'scale':[0.4,0.4,0.4]},
        'fixed':{'rotation':[0,180,0],'scale':[0.6,0.6,0.6]},'firstperson_righthand':{'rotation':[0,200,0],'scale':[0.45,0.45,0.45]},
        'thirdperson_righthand':{'rotation':[75,200,0],'translation':[0,2.5,0],'scale':[0.4,0.4,0.4]}},f'{M}part/schematic_encoder_online.json')},
        f'{M}item/schematic_encoder.json')
    jd({'model':{'type':'minecraft:model','model':RL('item/schematic_encoder')}},f'{A}items/schematic_encoder.json')
def recipes():
    R=D+'encodedlogistics/recipe/'; E=RL; V=lambda n:'minecraft:'+n
    def res(i,c=1): return {'id':i,'count':c}
    def w(n,o): jd(o,R+n+'.json')
    def shaped(n,pat,key,r,c=1): w(n,{'type':'minecraft:crafting_shaped','category':'misc','pattern':pat,'key':key,'result':res(r,c)})
    def shapeless(n,ings,r,c=1): w(n,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ings,'result':res(r,c)})
    def cook(n,typ,ing,r,xp,t): w(n,{'type':typ,'category':'misc','ingredient':ing,'result':res(r),'experience':xp,'cookingtime':t})
    def litho(n,wafer,add,mask,r): w(n,{'type':E('lithography'),'wafer':wafer,'additive':add,'photomask':mask,'result':res(r),'energy':8000,'time':180})
    for src,tag in ((E('raw_gallium'),'raw'),('#c:ores/gallium','ore')):
        cook(f'gallium_ingot_from_smelting_{tag}','minecraft:smelting',src,E('gallium_ingot'),1.0,200)
        cook(f'gallium_ingot_from_blasting_{tag}','minecraft:blasting',src,E('gallium_ingot'),1.0,200)
    cook('gallium_ingot_from_smelting_dust','minecraft:smelting','#c:dusts/gallium',E('gallium_ingot'),1.0,200)
    cook('gallium_ingot_from_blasting_dust','minecraft:blasting','#c:dusts/gallium',E('gallium_ingot'),1.0,200)
    from p2_export import crushing; crushing(R,'gallium')          # Arcforge's Arc Crusher, only with Arcforge installed
    shaped('processor_photomask',['GFG','FAF','GFG'],{'G':V('glass'),'F':E('ferrite'),'A':E('gallium_ingot')},E('processor_photomask'))
    litho('processor_die',E('doped_silicon'),E('gallium_ingot'),E('processor_photomask'),E('processor_die'))
    shaped('heatsink',['CCC','CFC'],{'C':V('copper_ingot'),'F':E('ferrite')},E('heatsink'))
    shaped('storage_die_2m',['GDG','DPD','GTG'],{'G':E('gallium_ingot'),'D':E('storage_die_512k'),'P':E('processor_die'),'T':E('tantalum_capacitor')},E('storage_die_2m'))
    shaped('storage_drive_2m',['TFT','IDI','ICI'],{'T':E('tantalum_ingot'),'I':V('iron_ingot'),'F':E('ferrite'),'D':E('storage_die_2m'),'C':E('circuit_substrate')},E('storage_drive_2m'))
    shapeless('schematic_card',[E('circuit_substrate'),E('memory_die'),V('paper')],E('schematic_card'),4)
    shapeless('schematic_encoder',[E('fabrication_terminal'),E('processor_die'),E('schematic_card')],E('schematic_encoder'))
    shaped('fabricator',['IPI','CTC','IKI'],{'I':V('iron_ingot'),'P':E('processor_die'),'C':E('circuit_substrate'),'T':V('crafting_table'),'K':V('piston')},E('fabricator'))
    shaped('gateway',['HLH','CTC','IMI'],{'H':V('hopper'),'L':E('logic_die'),'M':E('memory_die'),'C':E('circuit_substrate'),'T':E('tantalum_capacitor'),'I':V('iron_ingot')},E('gateway'))
    shaped('scheduler_core',['FPF','CHC','FPF'],{'F':E('ferrite'),'P':E('processor_die'),'C':E('circuit_substrate'),'H':E('heatsink')},E('scheduler_core'))
    shaped('job_buffer',['MFM','MCM'],{'M':E('memory_die'),'F':E('ferrite'),'C':E('circuit_substrate')},E('job_buffer'))
    shaped('thread_unit',['FHF','FPF','FTF'],{'F':E('ferrite'),'H':E('heatsink'),'P':E('processor_die'),'T':E('tantalum_capacitor')},E('thread_unit'))
def drops():                                                    # the autocrafting blocks drop themselves (as the Drive Bay does)
    for b in ('fabricator','gateway','scheduler_core','job_buffer','thread_unit'):
        p=f'{D}encodedlogistics/loot_table/blocks/{b}.json'; os.makedirs(os.path.dirname(p),exist_ok=True)
        json.dump({'type':'minecraft:block','pools':[{'condition':{'type':'minecraft:survives_explosion'},
            'entries':[{'type':'minecraft:item','name':RL(b)}],'rolls':1}],'random_sequence':RL(f'blocks/{b}')},open(p,'w',newline='\n'),indent=2)
def lang():
    L={'item.encodedlogistics.schematic_card':'Schematic Card','item.encodedlogistics.encoded_schematic_crafting':'Encoded Schematic (Crafting)',
       'item.encodedlogistics.encoded_schematic_processing':'Encoded Schematic (Processing)','item.encodedlogistics.raw_gallium':'Raw Gallium',
       'item.encodedlogistics.gallium_ingot':'Gallium Ingot','item.encodedlogistics.gallium_dust':'Gallium Dust','item.encodedlogistics.processor_die':'Processor Die',
       'item.encodedlogistics.processor_photomask':'Processor Photomask','item.encodedlogistics.heatsink':'Heatsink',
       'item.encodedlogistics.storage_die_2m':'2M Storage Die','item.encodedlogistics.storage_drive_2m':'2M Storage Drive',
       'item.encodedlogistics.schematic_encoder':'Schematic Encoder','block.encodedlogistics.deepslate_gallium_ore':'Deepslate Gallium Ore',
       'block.encodedlogistics.fabricator':'Fabricator','block.encodedlogistics.gateway':'Gateway','block.encodedlogistics.scheduler_core':'Scheduler Core',
       'block.encodedlogistics.job_buffer':'Job Buffer','block.encodedlogistics.thread_unit':'Thread Unit',
       'gui.encodedlogistics.schematic_encoder':'Schematic Encoder','gui.encodedlogistics.encoder.mode.crafting':'Crafting',
       'gui.encodedlogistics.encoder.mode.processing':'Processing','gui.encodedlogistics.encoder.encode':'Encode','gui.encodedlogistics.encoder.clear':'Clear',
       'gui.encodedlogistics.fabricator.schematics':'Schematics','gui.encodedlogistics.gateway.schematics':'Schematics',
       'gui.encodedlogistics.gateway.stock':'Keep stocked','gui.encodedlogistics.gateway.buffer':'Buffer',
       'gui.encodedlogistics.scheduler.active':'Active jobs','gui.encodedlogistics.scheduler.queue':'Queue','gui.encodedlogistics.scheduler.threads':'Threads %s / %s',
       'gui.encodedlogistics.scheduler.buffer':'Job buffer %s / %s','gui.encodedlogistics.scheduler.cancel':'Cancel job',
       'gui.encodedlogistics.scheduler.unformed':'Structure not formed','gui.encodedlogistics.craft.amount':'Craft amount','gui.encodedlogistics.craft.next':'Next',
       'gui.encodedlogistics.craft.plan':'Craft plan','gui.encodedlogistics.craft.stored':'Have','gui.encodedlogistics.craft.to_craft':'Make',
       'gui.encodedlogistics.craft.missing':'Miss','gui.encodedlogistics.craft.stored.tooltip':'In storage','gui.encodedlogistics.craft.to_craft.tooltip':'Will be crafted','gui.encodedlogistics.craft.missing.tooltip':'Missing - cannot start','gui.encodedlogistics.craft.scheduler':'Scheduler: %s','gui.encodedlogistics.craft.scheduler.auto':'Scheduler: Auto',
       'gui.encodedlogistics.craft.start':'Start','gui.encodedlogistics.craft.cancel':'Cancel','gui.encodedlogistics.craft.status':'Job status',
       'gui.encodedlogistics.craft.progress':'%s / %s crafted','gui.encodedlogistics.terminal.craftable':'Show craftable','gui.encodedlogistics.terminal.craft_hint':'Middle-click or Ctrl-click to craft',
       'tooltip.encodedlogistics.schematic.output':'Makes: %s','tooltip.encodedlogistics.schematic.hold_shift':'Hold Shift to show the output'}
    have=json.load(open(A+'lang/en_us.json',encoding='utf-8'))      # en_us.json holds them; report what's missing or differs
    for k,v in L.items():
        if have.get(k)!=v: print('en_us.json:',k,'should be',repr(v))
    return len(L)
if __name__=='__main__':
    items(); ore(); fabricator(); gateway(); scheduler(); encoder(); recipes(); drops(); n=lang()
    # the Phase 2 + 3 texture pass (tools/p23_v2.py) redraws some of what this wrote: write those again on top
    import p23_export; p23_export.paint()
    print('lang',n,'files',sum(len(f) for _,_,f in os.walk('src')))
