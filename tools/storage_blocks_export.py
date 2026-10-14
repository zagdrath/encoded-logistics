# Storage blocks for neodymium, tantalum, gallium and ferrite, as Arcforge's metal blocks: nine ingots (or Ferrite)
# to a block and back. Run from the project root: python tools/storage_blocks_export.py
# The texture is Arcforge's nickel_block.png (tools/arcforge_ref/) recoloured with each ingot's ramp: Arcforge's block
# uses its ingot's steps 3, 5, 5.5, 6 and 7, so ours take the same steps of our ingots.
import os, json, sys, random
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import arc_based
from items_p2 import NEO_INGOT, TAN_INGOT
from items_p3 import GA_INGOT
A='src/main/resources/assets/encodedlogistics/'; D='src/main/resources/data/'; RL=lambda p:'encodedlogistics:'+p
FERRITE=['#0E0E0F','#161618','#1D1E20','#25262A','#33353A','#43464C','#5D6168','#7D828A']   # arc_based.ferrite's ramp
def save(im,p): os.makedirs(os.path.dirname(p),exist_ok=True); im.save(p)
def jd(o,p): os.makedirs(os.path.dirname(p),exist_ok=True); json.dump(o,open(p,'w',newline='\n'),indent=1)
def mid(a,b): a,b=arc_based.H(a),arc_based.H(b); return tuple(round((x+y)/2) for x,y in zip(a,b))
def block(ingot,specks=False):
    im,_=arc_based.recolour_indexed('nickel_block.png',[ingot[3],ingot[5],mid(ingot[5],ingot[6]),ingot[6],ingot[7]])
    if specks:                                                       # ferrite: sparse one-step ceramic specks, as its item
        ramp=[arc_based.H(c) for c in ingot]; r=random.Random(19)
        for y in range(1,15):
            for x in range(1,15):
                p=im.getpixel((x,y))[:3]
                if p in ramp and 4<=ramp.index(p)<=6 and r.random()<0.18: im.putpixel((x,y),ramp[ramp.index(p)-1]+(255,))
    return im
# name: (ramp, ingredient for 9 -> block, item 9 come back as, beacon base)
BLOCKS={'neodymium':(NEO_INGOT,'#c:ingots/neodymium','neodymium_ingot',True),
        'tantalum':(TAN_INGOT,'#c:ingots/tantalum','tantalum_ingot',True),
        'gallium':(GA_INGOT,'#c:ingots/gallium','gallium_ingot',True),
        'ferrite':(FERRITE,RL('ferrite'),'ferrite',False)}
def add_tag(p,values):
    o=json.load(open(p)) if os.path.exists(p) else {'values':[]}
    o['values']+=[v for v in values if v not in o['values']]; jd(o,p)
for n,(ramp,ingredient,unit,beacon) in BLOCKS.items():
    b=f'{n}_block'
    save(block(ramp,n=='ferrite'),A+f'textures/block/{b}.png')
    jd({'variants':{'':{'model':RL(f'block/{b}')}}},A+f'blockstates/{b}.json')
    jd({'parent':'minecraft:block/cube_all','textures':{'all':RL(f'block/{b}')}},A+f'models/block/{b}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'block/{b}')}},A+f'items/{b}.json')
    jd({'type':'minecraft:block','pools':[{'condition':{'type':'minecraft:survives_explosion'},'entries':[{'type':'minecraft:item','name':RL(b)}],
        'rolls':1}],'random_sequence':RL(f'blocks/{b}')},D+f'encodedlogistics/loot_table/blocks/{b}.json')
    jd({'type':'minecraft:crafting_shaped','category':'misc','pattern':['###','###','###'],'key':{'#':ingredient},'result':{'id':RL(b),'count':1}},
       D+f'encodedlogistics/recipe/{b}.json')
    jd({'type':'minecraft:crafting_shapeless','category':'misc','ingredients':[f'#c:storage_blocks/{n}'],'result':{'id':RL(unit),'count':9}},
       D+f'encodedlogistics/recipe/{unit}_from_{b}.json')
    for kind in ('block','item'):
        jd({'values':[RL(b)]},D+f'c/tags/{kind}/storage_blocks/{n}.json'); add_tag(D+f'c/tags/{kind}/storage_blocks.json',[f'#c:storage_blocks/{n}'])
    add_tag(D+'minecraft/tags/block/mineable/pickaxe.json',[RL(b)]); add_tag(D+'minecraft/tags/block/needs_iron_tool.json',[RL(b)])
    if beacon: add_tag(D+'minecraft/tags/block/beacon_base_blocks.json',[RL(b)])
lang=json.load(open(A+'lang/en_us.json',encoding='utf-8'))
missing=[f'block.encodedlogistics.{n}_block' for n in BLOCKS if f'block.encodedlogistics.{n}_block' not in lang]
print('missing lang keys:',missing or 'none')
