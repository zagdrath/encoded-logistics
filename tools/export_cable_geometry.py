# Aligns Network Cable + Dense Network Cable to the cable spec (same family as Fiber Cable):
#   normal: 6x6x6 core cube | 4x4x1 neck | 6x6x4 sleeve   (sleeves of neighbours meet as one 8 px run)
#   dense:  8x8 sleeve, 6x6 neck, 8x8x6 straight cube, 10x10x10 junction cube, 12x12 flange
# Keeps the blockstates, part names and the existing h/v/j/f textures; adds one dense sheet (_c) for the 8-wide cube.
import os, json, sys
from PIL import Image
A='src/main/resources/assets/encodedlogistics'
TEX=A+'/textures/block/cable'; MOD=A+'/models/block/cable'
COLOURS=['neutral','white','orange','magenta','light_blue','yellow','lime','pink','cyan','purple','blue','green','red']
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade_direction_override':'up'}
SOLID=[15,15,16,16]
def face(t,uv): return {'texture':t,'uv':uv}
def el(a,b,f): return {'from':list(a),'to':list(b),'faces':f}
SIZE={'normal':dict(sleeve=6,neck=4,cube=6,junction=6,flange=8,flange_t=1,neck_row=8),
      'dense': dict(sleeve=8,neck=6,cube=8,junction=10,flange=12,flange_t=1,neck_row=8)}
def box(w,z0,z1): lo,hi=8-w/2,8+w/2; return (lo,lo,z0),(hi,hi,z1)
def sleeve(tier,z0,z1):
    w=SIZE[tier]['sleeve']
    return el(*box(w,z0,z1),{'east':face('#h',[16-z1,0,16-z0,w]),'west':face('#h',[z0,0,z1,w]),
              'up':face('#v',[0,z0,w,z1]),'down':face('#v',[0,16-z1,w,16-z0]),'north':face('#h',SOLID),'south':face('#h',SOLID)})
def neck(tier,z0,z1):
    n=SIZE[tier]['neck']; r=SIZE[tier]['neck_row']
    return el(*box(n,z0,z1),{'east':face('#h',[16-z1,r,16-z0,r+n]),'west':face('#h',[z0,r,z1,r+n]),
              'up':face('#v',[r,z0,r+n,z1]),'down':face('#v',[r,16-z1,r+n,16-z0])})
def flange(tier):
    f=SIZE[tier]['flange']; t=SIZE[tier]['flange_t']
    return el(*box(f,0,t),{'north':face('#f',[0,0,f,f]),'south':face('#f',[0,0,f,f]),
              'east':face('#h',SOLID),'west':face('#h',SOLID),'up':face('#h',SOLID),'down':face('#h',SOLID)})
def cube(tier,kind):
    S=SIZE[tier]
    if kind=='junction' or tier=='normal':
        n=S['junction'] if kind=='junction' else S['cube']; lo,hi=8-n/2,8+n/2
        return el((lo,lo,lo),(hi,hi,hi),{d:face('#j',[0,0,n,n]) for d in ('north','south','east','west','up','down')})
    # dense straight cube: 8 wide, 6 long (z 5..11); sides from the derived _c sheet
    return el((4,4,5),(12,12,11),{'east':face('#c',[0,0,6,8]),'west':face('#c',[0,0,6,8]),
              'up':face('#c',[0,8,8,14]),'down':face('#c',[0,8,8,14]),'north':face('#c',[8,0,16,8]),'south':face('#c',[8,0,16,8])})
def parts(tier):
    S=SIZE[tier]; h=S['junction']/2
    straight=[sleeve(tier,0,4),neck(tier,4,5)]                       # pairs with cube_straight (z 5..11)
    if 8-h-1>=1: jarm=[sleeve(tier,0,8-h-1),neck(tier,8-h-1,8-h)]  # from the junction cube to the block edge
    else: jarm=[neck(tier,0,8-h)]
    t=S['flange_t']
    blk=[flange(tier)]+([sleeve(tier,t,8-h-1)] if 8-h-1>t else [])+[neck(tier,8-h-1,8-h)]
    item=[sleeve(tier,0,4),neck(tier,4,5),cube(tier,'straight'),neck(tier,11,12),sleeve(tier,12,16)]
    return {'cube_straight':[cube(tier,'straight')],'cube_junction':[cube(tier,'junction')],
            'arm_straight':straight,'arm_junction':jarm,'arm_block':blk,'item':item}
def with_glow(els):
    out=list(els)
    for e in els:
        g={'from':e['from'],'to':e['to'],'faces':{k:{**v,'texture':v['texture']+'_glow'} for k,v in e['faces'].items()}}; g.update(GLOW); out.append(g)
    return out
def dense_cube_sheet(src,dst,frames_ok=True):
    """_c sheet from the colour's own 10x10 junction face: drop inner rows/cols so frame, ring and lit centre survive.
       (0,0) 6 along x 8 across = side faces; (0,8) 8x6 = top/bottom; (8,0) 8x8 = ends."""
    im=Image.open(src).convert('RGBA'); n=im.height//16; out=Image.new('RGBA',(16,16*n),(0,0,0,0))
    for f in range(n):
        j=im.crop((0,16*f,10,16*f+10))
        def keep(rows,cols): return [[j.getpixel((x,y)) for x in cols] for y in rows]
        R8=[0,1,3,4,5,6,8,9]; R6=[0,1,4,5,8,9]
        for (ox,oy,rows,cols) in ((0,0,R8,R6),(0,8,R6,R8),(8,0,R8,R8)):
            px=keep(rows,cols)
            for y,row in enumerate(px):
                for x,c in enumerate(row): out.putpixel((ox+x,16*f+oy+y),c)
    out.save(dst)
def write(src_tex):
    for tier in ('normal','dense'):
        P=parts(tier)
        for name,els in P.items():
            json.dump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':{'particle':'#j'},
                       'elements':with_glow(els)},open(f'{MOD}/{tier}/template_{name}.json','w',newline='\n'),indent=1)
        for c in COLOURS:
            t={k:f'encodedlogistics:block/cable/{tier}/{c}_{k}' for k in ('h','v','j','f')+(('c',) if tier=='dense' else ())}
            t.update({k+'_glow':v+'_glow' for k,v in list(t.items())}); t['particle']=t['j']
            os.makedirs(f'{MOD}/{tier}/{c}',exist_ok=True)
            for name in P: json.dump({'parent':f'encodedlogistics:block/cable/{tier}/template_{name}','textures':t},open(f'{MOD}/{tier}/{c}/{name}.json','w',newline='\n'),indent=1)
            if tier=='dense':
                for suf in ('','_glow'):
                    dense_cube_sheet(f'{src_tex}/dense/{c}_j{suf}.png',f'{TEX}/dense/{c}_c{suf}.png')
                    if os.path.exists(f'{src_tex}/dense/{c}_j{suf}.png.mcmeta'):
                        open(f'{TEX}/dense/{c}_c{suf}.png.mcmeta','w',newline='\n').write(open(f'{src_tex}/dense/{c}_j{suf}.png.mcmeta').read())
    shapes={tier:{name:[e['from']+e['to'] for e in els] for name,els in parts(tier).items() if name!='item'} for tier in ('normal','dense')}
    return shapes
if __name__=='__main__':
    src=sys.argv[1] if len(sys.argv)>1 else TEX
    os.makedirs(f'{TEX}/dense',exist_ok=True)
    for tier in ('normal','dense'): os.makedirs(f'{MOD}/{tier}',exist_ok=True)
    shapes=write(src)
    os.makedirs('reference',exist_ok=True)
    json.dump({'note':'Network/Dense cable parts after alignment, pixels [x1,y1,z1,x2,y2,z2], arms pointing NORTH. Replace the arrays in CableShapes.',
               'parts':shapes},open('reference/cable_shapes_aligned.json','w',newline='\n'),indent=1)
    print('done')
