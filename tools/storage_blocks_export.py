# Storage blocks for neodymium, tantalum, gallium and ferrite, as Arcforge's metal blocks: nine ingots (or Ferrite)
# to a block and back; and raw ore blocks for the three metals, as Arcforge's: nine raw ores to a block and back.
# Run from the project root: python tools/storage_blocks_export.py
# The textures are Arcforge 2.8.1's (tools/arcforge_ref/v2/) moved onto our ramps by arc_based.transfer: a metal block
# in one of Arcforge's metal block families against that metal's ingot, with our ingot ramp; a raw ore block in the
# raw ore's family against Arcforge's raw ore, with our raw ore ramp (items_p2.raw uses the same families).
import os, json, sys, random
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import arc_based
from items_p2 import NEO_INGOT, TAN_INGOT, NEO_RAMP, TAN_RAMP
from items_p3 import GA_INGOT, GA_RAMP
A='src/main/resources/assets/encodedlogistics/'; D='src/main/resources/data/'; RL=lambda p:'encodedlogistics:'+p
FERRITE=['#0E0E0F','#161618','#1D1E20','#25262A','#33353A','#43464C','#5D6168','#7D828A']   # arc_based.ferrite's ramp
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def block(ingot,family,specks=False):
    im=arc_based.transfer(f'{family}_block.png',f'{family}_ingot.png',ingot)
    if specks:                                                       # ferrite: sparse one-step ceramic specks, as its item
        ramp=sorted({im.getpixel((x,y))[:3] for x in range(16) for y in range(16)},key=arc_based.L); r=random.Random(19)
        for y in range(1,15):
            for x in range(1,15):
                p=im.getpixel((x,y))[:3]; i=ramp.index(p)
                if 2<=i<len(ramp)-2 and r.random()<0.18: im.putpixel((x,y),ramp[i-1]+(255,))
    return im
def raw_block(ramp,family): return arc_based.transfer(f'{family}_block.png',f'{family}.png',ramp)
# name: (texture, ingredient for 9 -> block, item 9 come back as, beacon base, tag)
BLOCKS={'neodymium_block':(lambda: block(NEO_INGOT,'nickel'),'#c:ingots/neodymium','neodymium_ingot',True,'neodymium'),
        'tantalum_block':(lambda: block(TAN_INGOT,'tungsten'),'#c:ingots/tantalum','tantalum_ingot',True,'tantalum'),
        'gallium_block':(lambda: block(GA_INGOT,'silver'),'#c:ingots/gallium','gallium_ingot',True,'gallium'),
        'ferrite_block':(lambda: block(FERRITE,'lead',True),RL('ferrite'),'ferrite',False,'ferrite'),
        'raw_neodymium_block':(lambda: raw_block(NEO_RAMP,'raw_nickel'),'#c:raw_materials/neodymium','raw_neodymium',False,'raw_neodymium'),
        'raw_tantalum_block':(lambda: raw_block(TAN_RAMP,'raw_galena'),'#c:raw_materials/tantalum','raw_tantalum',False,'raw_tantalum'),
        'raw_gallium_block':(lambda: raw_block(GA_RAMP,'raw_nickel'),'#c:raw_materials/gallium','raw_gallium',False,'raw_gallium')}
def add_tag(p,values):
    o=json.load(open(p)) if os.path.exists(p) else {'values':[]}
    o['values']+=[v for v in values if v not in o['values']]; jd(o,p)
for b,(texture,ingredient,unit,beacon,tag) in BLOCKS.items():
    save(texture(),A+f'textures/block/{b}.png')
    jd({'variants':{'':{'model':RL(f'block/{b}')}}},A+f'blockstates/{b}.json')
    jd({'parent':'minecraft:block/cube_all','textures':{'all':RL(f'block/{b}')}},A+f'models/block/{b}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'block/{b}')}},A+f'items/{b}.json')
    jd({'type':'minecraft:block','pools':[{'condition':{'type':'minecraft:survives_explosion'},'entries':[{'type':'minecraft:item','name':RL(b)}],
        'rolls':1}],'random_sequence':RL(f'blocks/{b}')},D+f'encodedlogistics/loot_table/blocks/{b}.json')
    jd({'type':'minecraft:crafting_shaped','category':'misc','pattern':['###','###','###'],'key':{'#':ingredient},'result':{'id':RL(b),'count':1}},
       D+f'encodedlogistics/recipe/{b}.json')
    jd({'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[f'#c:storage_blocks/{tag}'],'result':{'id':RL(unit),'count':9}},
       D+f'encodedlogistics/recipe/{unit}_from_{b}.json')
    for kind in ('block','item'):
        jd({'values':[RL(b)]},D+f'c/tags/{kind}/storage_blocks/{tag}.json'); add_tag(D+f'c/tags/{kind}/storage_blocks.json',[f'#c:storage_blocks/{tag}'])
    add_tag(D+'minecraft/tags/block/mineable/pickaxe.json',[RL(b)]); add_tag(D+'minecraft/tags/block/needs_iron_tool.json',[RL(b)])
    if beacon: add_tag(D+'minecraft/tags/block/beacon_base_blocks.json',[RL(b)])
lang=json.load(open(A+'lang/en_us.json',encoding='utf-8'))
missing=[f'block.encodedlogistics.{b}' for b in BLOCKS if f'block.encodedlogistics.{b}' not in lang]
print('missing lang keys:',missing or 'none')
