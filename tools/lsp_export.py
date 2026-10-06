import os, json, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from lsp import *
SH={}
save(metal_tex(),'textures/block/cage_light/metal.png'); save(cage_tex(),'textures/block/cage_light/cage.png'); save(base_tex(),'textures/block/cage_light/base.png')
CL=Image.new('RGBA',(16,16),(0,0,0,0))
for y in range(16):
    for x in range(16): px(CL,x,y,S[6] if (x+y)<14 else S[5])
for x in range(16): px(CL,x,0,S[7])
save(CL,'textures/block/cage_light/collar.png')
hs=Image.new('RGBA',(16,16),(0,0,0,0))
for y in range(16):
    for x in range(16): px(hs,x,y,S[3] if x+y<12 else S[2] if x+y<22 else S[1])
for x in range(16): px(hs,x,0,S[5])
save(hs,'textures/block/siren/housing.png'); save(grille_tex('perf'),'textures/block/siren/grille.png')
# ---- Cage Lights ----
USER_SHEET=Image.open('/mnt/user-data/uploads/caged_signal_light.png').convert('RGBA')
for n,h in DYES.items():
    b=f'cage_light_{n}'; R=ramp(h)
    for lit in (False,True):
        sh=USER_SHEET.copy(); p=sh.load()
        for y in range(sh.height):
            for x in range(sh.width):
                r,g,bb,a_=p[x,y]
                if a_ and min(r,g,bb)>200: p[x,y]=tuple(R[5] if lit else R[2])+(255,)       # the white window takes the dye colour
        save(sh,f'textures/block/cage_light/caged_signal_light_{n}'+('_on' if lit else '')+'.png')
    save(glass_tex(h,False),f'textures/block/cage_light/glass_{n}.png'); save(glass_tex(h,True),f'textures/block/cage_light/glass_{n}_on.png')
    E=cage_light()
    jd(model({'sheet':f'block/cage_light/caged_signal_light_{n}','glass':f'block/cage_light/glass_{n}'},E),f'models/block/cage_light/{b}.json')
    Eon=[dict(e,**GLOW) if e['name'] in ('glass',) else e for e in E]
    jd(model({'sheet':f'block/cage_light/caged_signal_light_{n}_on','glass':f'block/cage_light/glass_{n}_on'},Eon),f'models/block/cage_light/{b}_on.json')
    jd({'variants':{f'facing={fc},lit={l}':{'model':RL(f'block/cage_light/{b}'+('_on' if l=='true' else '')),**r} for fc,r in FACE6.items() for l in ('false','true')}},f'blockstates/{b}.json')
SH['cage_light']=shapes(cage_light())
# ---- Sirens ----
for n in SIREN_COLOURS:
    b=f'siren_{n}'
    save(lens_tex(n,'off'),f'textures/block/siren/lens_{n}.png'); save(lens_tex(n,'on'),f'textures/block/siren/lens_{n}_on.png')
    for mode,ft in (('slow',20),('medium',10),('fast',4)):                     # flashing: on frame + transparent frame
        st=Image.new('RGBA',(16,32),(0,0,0,0)); st.alpha_composite(lens_tex(n,'on'),(0,0)); save(st,f'textures/block/siren/lens_{n}_{mode}.png'); mc(f'textures/block/siren/lens_{n}_{mode}.png',ft)
    tx={'metal':'block/cage_light/metal','housing':'block/siren/housing','grille':'block/siren/grille','lens':f'block/siren/lens_{n}'}
    jd(model(tx,siren()),f'models/block/siren/{b}.json')
    for mode in ('solid','slow','medium','fast'):
        jd(model({'lens':f'block/siren/lens_{n}_'+('on' if mode=='solid' else mode)},siren_glow()),f'models/block/siren/{b}_{mode}.json')
    mp=[]
    for fc,r in FACE6.items():
        mp.append({'when':{'facing':fc},'apply':{'model':RL(f'block/siren/{b}'),**r}})
        for mode in ('solid','slow','medium','fast'): mp.append({'when':{'facing':fc,'light':mode},'apply':{'model':RL(f'block/siren/{b}_{mode}'),**r}})
    jd({'multipart':mp},f'blockstates/{b}.json')
SH['siren']=shapes(siren())
# ---- Speaker (one block: ceilings and walls) - the author's front texture ----
import shutil
os.makedirs(A_+'textures/block/speaker',exist_ok=True); shutil.copy('/mnt/user-data/uploads/smart_speaker_front.png',A_+'textures/block/speaker/smart_speaker_front.png')
for st in ('playing','error'): save(led_tex(st),f'textures/block/speaker/led_{st}.png')
tx={'face':'block/speaker/smart_speaker_front'}
jd(model(tx,speaker()),'models/block/speaker/speaker.json')
for st in ('playing','error'): jd(model({'led':f'block/speaker/led_{st}'},led(1,11,1.01)),f'models/block/speaker/speaker_led_{st}.json')   # the texture's LED pixel (14, 4), face rotated 180
mp=[]
for fc,r in FACE6.items():
    mp.append({'when':{'facing':fc},'apply':{'model':RL('block/speaker/speaker'),**r}})
    for st in ('playing','error'): mp.append({'when':{'facing':fc,'state':st},'apply':{'model':RL(f'block/speaker/speaker_led_{st}'),**r}})
jd({'multipart':mp},'blockstates/speaker.json'); SH['speaker']=shapes(speaker())
# ---- item icons ----
def icon_light(h,lit=True):
    R=ramp(h); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(4,12):
        for x in range(6,10): px(im,x,y,R[4] if x in (7,8) else R[3])
    for y in range(3,12): px(im,5,y,S[1]); px(im,10,y,S[1])
    for x in range(4,12): px(im,x,3,S[2]); px(im,x,9,S[1])
    for x in (5,10): px(im,x,2,S[2])
    for x in range(6,10): px(im,x,12,S[6])
    for x in range(3,13): px(im,x,13,S[3]); px(im,x,14,S[1])
    px(im,7,5,R[6])
    return outline(im,S[0])
def icon_siren(n):
    R=ramp({'green':'#3CC83C','orange':'#F08A2A','yellow':'#FFD83C','red':'#E5352C'}[n]); im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(2,6):
        for x in range(6,10): px(im,x,y,R[5] if y<4 else R[4])
    for y in range(6,12):
        for x in range(3,13): px(im,x,y,(S[0] if (x%2==0 and y%2==0) else S[3]) if 4<=x<=11 else S[2])
    for x in range(6,10): px(im,x,12,S[2]); px(im,x,13,S[2])
    for x in range(4,12): px(im,x,14,S[3])
    px(im,7,2,(255,255,240))
    return outline(im,S[0])
def icon_speaker():
    return Image.open('/mnt/user-data/uploads/smart_speaker_front.png').convert('RGBA')
icons={**{f'cage_light_{n}':icon_light(h) for n,h in DYES.items()},**{f'siren_{n}':icon_siren(n) for n in SIREN_COLOURS},'speaker':icon_speaker()}
for n,im in icons.items():
    save(im,f'textures/item/{n}.png'); jd({'parent':'minecraft:item/generated','textures':{'layer0':RL(f'item/{n}')}},f'models/item/{n}.json')
    jd({'model':{'type':'minecraft:model','model':RL(f'item/{n}')}},f'items/{n}.json')
os.makedirs('shapes',exist_ok=True); json.dump(SH,open('shapes/collision_shapes.json','w'),indent=1)
print('files',sum(len(f) for _,_,f in os.walk('src')))
