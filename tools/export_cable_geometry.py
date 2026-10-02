# Network Cable + Dense Network Cable geometry (the cable spec, same family as Fiber Cable):
#   normal: 6x6 tube, 6x6x6 junction cube, 8x8x1 flange
#   dense:  8x8 tube, 10x10x10 junction cube, 12x12x1 flange
# No necks: the arms run flush into the straight body / junction cube, so a straight run is one continuous tube
# (sleeve 0..5 | body 5..11 | sleeve 11..16) and joints don't show a step, gap or end plate. Faces that are always
# inside the cable (tube ends against the cube or a neighbour) are left out.
# Textures: h/v/j/f sheets from tools/export_cables.py (tube along u / along v, junction face, flange face).
import os, json, sys
A='src/main/resources/assets/encodedlogistics'
TEX=A+'/textures/block/cable'; MOD=A+'/models/block/cable'
COLOURS=['neutral','white','orange','magenta','light_blue','yellow','lime','pink','cyan','purple','blue','green','red']
GLOW={'neoforge_data':{'block_light':15,'sky_light':15},'shade':False}
SOLID=[15,15,16,16]
END_ROW=8                                                       # item end cap (tube x tube) at (0,8) on _h
def face(t,uv): return {'texture':t,'uv':uv}
def el(a,b,f): return {'from':list(a),'to':list(b),'faces':f}
SIZE={'normal':dict(tube=6,junction=6,flange=8,flange_t=1),
      'dense': dict(tube=8,junction=10,flange=12,flange_t=1)}
def box(w,z0,z1): lo,hi=8-w/2,8+w/2; return (lo,lo,z0),(hi,hi,z1)
def tube(tier,z0,z1,ends=()):
    """A length of tube along z; u follows z on every side so the rings line up along the whole run."""
    w=SIZE[tier]['tube']
    f={'east':face('#h',[16-z1,0,16-z0,w]),'west':face('#h',[z0,0,z1,w]),
       'up':face('#v',[0,z0,w,z1]),'down':face('#v',[0,16-z1,w,16-z0])}
    for e in ends: f[e]=face('#h',[0,END_ROW,w,END_ROW+w])
    return el(*box(w,z0,z1),f)
def flange(tier):
    f=SIZE[tier]['flange']; t=SIZE[tier]['flange_t']
    return el(*box(f,0,t),{'north':face('#f',[0,0,f,f]),'south':face('#f',[0,0,f,f]),
              'east':face('#h',SOLID),'west':face('#h',SOLID),'up':face('#h',SOLID),'down':face('#h',SOLID)})
def junction(tier):
    n=SIZE[tier]['junction']; lo,hi=8-n/2,8+n/2
    return el((lo,lo,lo),(hi,hi,hi),{d:face('#j',[0,0,n,n]) for d in ('north','south','east','west','up','down')})
def parts(tier):
    S=SIZE[tier]; h=S['junction']//2; t=S['flange_t']
    return {'cube_straight':[tube(tier,5,11)],                 # the straight body: the same tube as the arms
            'cube_junction':[junction(tier)],
            'arm_straight':[tube(tier,0,5)],
            'arm_junction':[tube(tier,0,8-h)],
            'arm_block':[flange(tier),tube(tier,t,8-h)],
            'item':[tube(tier,0,16,ends=('north','south'))]}
def with_glow(els):
    out=list(els)
    for e in els:
        g={'from':e['from'],'to':e['to'],'faces':{k:{**v,'texture':v['texture']+'_glow'} for k,v in e['faces'].items()}}; g.update(GLOW); out.append(g)
    return out
def write(src_tex=None):
    for tier in ('normal','dense'):
        P=parts(tier)
        for name,els in P.items():
            json.dump({'parent':'minecraft:block/block','render_type':'minecraft:cutout','textures':{'particle':'#j'},
                       'elements':with_glow(els)},open(f'{MOD}/{tier}/template_{name}.json','w',newline='\n'),indent=1)
        for c in COLOURS:
            t={k:f'encodedlogistics:block/cable/{tier}/{c}_{k}' for k in ('h','v','j','f')}
            t.update({k+'_glow':v+'_glow' for k,v in list(t.items())}); t['particle']=t['j']
            os.makedirs(f'{MOD}/{tier}/{c}',exist_ok=True)
            for name in P: json.dump({'parent':f'encodedlogistics:block/cable/{tier}/template_{name}','textures':t},open(f'{MOD}/{tier}/{c}/{name}.json','w',newline='\n'),indent=1)
    shapes={tier:{name:[e['from']+e['to'] for e in els] for name,els in parts(tier).items() if name!='item'} for tier in ('normal','dense')}
    return shapes
if __name__=='__main__':
    for tier in ('normal','dense'): os.makedirs(f'{MOD}/{tier}',exist_ok=True)
    shapes=write()
    os.makedirs('reference',exist_ok=True)
    json.dump({'note':'Network/Dense cable parts, pixels [x1,y1,z1,x2,y2,z2], arms pointing NORTH. Replace the arrays in CableShapes.',
               'parts':shapes},open('reference/cable_shapes_aligned.json','w',newline='\n'),indent=1)
    print('done')
