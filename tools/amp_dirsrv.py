# Standalone Optical Amplifier (inline fibre amplifier housing) and standalone Directory Server (full-block server):
# textures, block models and blockstates. Run from the project root: python tools/amp_dirsrv.py
# Textures are 64x64 sheets at the mod's 16 px/block density (1 px per model unit, so uv = px / 4).
import json, os
from PIL import Image
A='src/main/resources/assets/encodedlogistics/'
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))+(255,)
# The mod's steel ramp (tools/mcpass.py GREY) plus the status greens / screen green / fibre core yellow.
G=[H(c) for c in ['#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB']]
LED=(60,224,90,255); LED_HI=(181,255,176,255); AMBER=(255,190,80,255); RED=(235,60,50,255); RED_HI=(255,160,150,255)
SCREEN=(20,42,34,255); SCREEN_HI=(30,58,46,255)
CORE=(255,222,112,255); CORE_HI=(255,240,190,255); COIL_OFF=(120,98,52,255); COIL_OFF2=(92,76,44,255)
GLASS=(26,32,40,255); GLASS2=(34,42,52,255)
LABEL=G[9]; LABEL_INK=G[1]

def save(im,p): os.makedirs(os.path.dirname(A+p),exist_ok=True); im.save(A+p)
def jd(o,p):
    os.makedirs(os.path.dirname(A+p),exist_ok=True)
    with open(A+p,'w') as f: json.dump(o,f,indent=1)
def fill(im,x,y,w,h,c):
    for j in range(y,y+h):
        for i in range(x,x+w): im.putpixel((i,j),c)
def px(im,x,y,c): im.putpixel((x,y),c)
def panel(im,x,y,w,h,base=G[5],hi=G[7],lo=G[3]):
    """A steel plate: light top/left edge, dark bottom/right edge."""
    fill(im,x,y,w,h,base); fill(im,x,y,w,1,hi); fill(im,x,y,1,h,hi); fill(im,x,y+h-1,w,1,lo); fill(im,x+w-1,y,1,h,lo)
def recess(im,x,y,w,h,base=G[2]):
    """A slot / opening: dark top/left lip (shadow), lighter bottom/right lip."""
    fill(im,x,y,w,h,base); fill(im,x,y,w,1,G[1]); fill(im,x,y,1,h,G[1])
    if h>2: fill(im,x+1,y+h-1,w-1,1,G[3])
def screw(im,x,y): px(im,x,y,G[2])
def uv(x,y,w,h): return [x/4,y/4,(x+w)/4,(y+h)/4]
def face(r,tex='#t',flip=False,cull=None):
    u=uv(*r)
    if flip: u=[u[2],u[1],u[0],u[3]]
    f={'uv':u,'texture':tex}
    if cull: f['cullface']=cull
    return f
def el(name,fr,to,faces,**kw):
    e={'name':name,'from':fr,'to':to,'faces':faces}; e.update(kw); return e
GLOWKW={'shade':False,'neoforge_data':{'block_light':15,'sky_light':15}}

# ------------------------------------------------------------------------------------------------ Optical Amplifier
# Model (FACING north): light enters the back port (south, z=16) and leaves the front port (north, z=0); the blockstate
# turns it with y so the ports sit on the two faces the block connects through (FACING's axis). The ports are 8x8
# flanges centred on (8,8): the same size and centre as a Fiber Cable's block-end flange, so a cable mates flush.
AS=(0,0,10,12); AT=(16,0,10,10); AE=(32,0,10,12); AB=(48,0,10,10)
FIN_SIDE=(32,16,2,6); FIN_END=(35,16,1,6); FIN_TOP=(37,16,1,2)
FL_FACE=(0,32,8,8); FL_EDGE_V=(8,32,1,8); FL_EDGE_H=(0,40,8,1)
BOOT_SIDE=(16,32,2,6); BOOT_TOP=(16,40,6,2)
CORE_DOT=(0,48,2,2)
G_TOP_ON=(16,0,10,10); G_TOP_FAULT=(32,0,10,10)

def amp_textures():
    im=Image.new('RGBA',(64,64),(0,0,0,0))
    # housing side (east face; u runs back -> front): label plate with the light-direction arrow above, fin recess below
    x,y,w,h=AS; panel(im,x,y,w,h)
    fill(im,x+1,y+1,8,4,LABEL); fill(im,x+1,y+4,8,1,G[7]); fill(im,x+1,y+1,8,1,G[10])
    for c in (1,4,7): px(im,x+c,y+1,LABEL_INK); px(im,x+c+1,y+2,LABEL_INK); px(im,x+c,y+3,LABEL_INK)   # >>> : light flows to the front
    recess(im,x+1,y+6,8,5,G[3])
    for i in range(1,9,2): px(im,x+i,y+7,G[2])                                                       # vent dots between the fins
    for i in range(2,9,2): px(im,x+i,y+9,G[2])
    # housing top: LED strip at the front, the glass window over the erbium coil behind it
    x,y,w,h=AT; panel(im,x,y,w,h)
    recess(im,x+1,y+1,8,2,G[2])
    for i in (2,4,6): px(im,x+i,y+2,G[1])
    px(im,x+8,y+2,G[4])
    fill(im,x+1,y+4,8,5,G[3]); fill(im,x+2,y+4,6,5,GLASS); fill(im,x+1,y+5,8,3,GLASS)                # rounded window
    for (i,j) in coil_pixels(): px(im,x+i,y+j,COIL_OFF if (i+j)%2 else COIL_OFF2)
    px(im,x+2,y+5,GLASS2); px(im,x+3,y+4,GLASS2)                                                      # glass glint
    # housing ends (mostly behind the port flanges)
    x,y,w,h=AE; panel(im,x,y,w,h); screw(im,x+1,y+1); screw(im,x+8,y+1); screw(im,x+1,y+10); screw(im,x+8,y+10)
    # housing bottom
    x,y,w,h=AB; panel(im,x,y,w,h,G[4],G[5],G[2])
    # fins: bright ridge on the outer face, mid-grey sides
    x,y,w,h=FIN_SIDE; fill(im,x,y,w,h,G[7]); fill(im,x,y,w,1,G[9]); fill(im,x,y+h-1,w,1,G[5])
    fill(im,*FIN_END[:2],1,FIN_END[3],G[6]); px(im,FIN_END[0],FIN_END[1],G[8])
    fill(im,*FIN_TOP[:2],1,2,G[9])
    # port flange face: steel ring, dark bulkhead adapter, ceramic ferrule with the fibre core in the middle
    x,y,w,h=FL_FACE; panel(im,x,y,w,h,G[7],G[9],G[5])
    for (sx,sy) in ((1,1),(6,1),(1,6),(6,6)): px(im,x+sx,y+sy,G[4])
    fill(im,x+2,y+2,4,4,G[1]); fill(im,x+3,y+2,2,4,G[2]); fill(im,x+2,y+3,4,2,G[2])
    fill(im,x+3,y+3,2,2,G[10]); px(im,x+4,y+4,CORE)
    fill(im,*FL_EDGE_V[:2],1,8,G[6]); fill(im,*FL_EDGE_H[:2],8,1,G[6])
    # boot / bulkhead body: ribbed dark collar
    x,y,w,h=BOOT_SIDE; fill(im,x,y,w,h,G[3]); fill(im,x,y,w,1,G[5])
    for j in (2,4): fill(im,x,y+j,w,1,G[2])
    x,y,w,h=BOOT_TOP; fill(im,x,y,w,h,G[4]); fill(im,x,y,1,h,G[5])
    fill(im,*CORE_DOT[:2],2,2,G[9])
    save(im,'textures/block/optical_amplifier/optical_amplifier.png')

    gl=Image.new('RGBA',(64,64),(0,0,0,0))
    x,y,_,_=G_TOP_ON
    px(gl,x+2,y+2,LED); px(gl,x+4,y+2,LED); px(gl,x+6,y+2,LED_HI)                                       # PWR, AMP, OUT
    for (i,j) in coil_pixels(): px(gl,x+i,y+j,CORE_HI if (i*3+j)%4==0 else CORE)
    x,y,_,_=G_TOP_FAULT
    px(gl,x+2,y+2,LED); px(gl,x+8,y+2,RED)                                       # PWR, LOS (input loss of signal)
    fill(gl,*CORE_DOT[:2],2,2,CORE_HI); px(gl,CORE_DOT[0]+1,CORE_DOT[1]+1,CORE)
    save(gl,'textures/block/optical_amplifier/glow.png')

def coil_pixels():
    """Two concentric fibre loops (the doped coil) inside the 8x5 window at (1,4)."""
    pts=set()
    for i in range(3,7): pts.add((i,4)); pts.add((i,8))
    for j in range(5,8): pts.add((1,j)); pts.add((8,j))
    pts|={(2,4),(7,4),(2,8),(7,8)}
    pts-={(2,4),(7,4),(2,8),(7,8)}; pts|={(2,5),(7,5),(2,7),(7,7)}                                  # round the corners
    for i in range(4,6): pts.add((i,6))                                                              # inner loop / splice
    pts|={(3,6),(6,6)}
    return sorted(pts)

def amp_models():
    T='#t'
    def box6(name,fr,to,n,s,e,w,u,d,flip_w=False):
        return el(name,fr,to,{'north':face(n,T),'south':face(s,T),'east':face(e,T),'west':face(w,T,flip=flip_w),'up':face(u,T),'down':face(d,T)})
    els=[]
    # no base plate: it's inline on a fibre run and may hang in the air, so the housing is centred on the fibre (y 2-14)
    els.append(el('housing',[3,2,3],[13,14,13],{'north':face(AE,T),'south':face(AE,T),'east':face(AS,T),'west':face(AS,T,flip=True),
                                              'up':face(AT,T),'down':face(AB,T)}))
    for z in (4,7,10):
        for x0 in (2,13):
            els.append(el(f'fin_{"w" if x0==2 else "e"}{z}',[x0,3,z],[x0+1,9,z+2],{'north':face(FIN_END,T),'south':face(FIN_END,T),
                       ('east' if x0==13 else 'west'):face(FIN_SIDE,T,flip=(x0==2)),'up':face(FIN_TOP,T),'down':face(FIN_TOP,T)}))
    for tag,zb,zf in (('out',1,0),('in',13,15)):                    # front (out) and back (in) ports
        els.append(el(f'boot_{tag}',[5,5,zb],[11,11,zb+2],{'east':face(BOOT_SIDE,T),'west':face(BOOT_SIDE,T),'up':face(BOOT_TOP,T),'down':face(BOOT_TOP,T)}))
        els.append(el(f'flange_{tag}',[4,4,zf],[12,12,zf+1],{'north':face(FL_FACE,T,cull='north' if zf==0 else None),
                       'south':face(FL_FACE,T,cull='south' if zf==15 else None),
                       'east':face(FL_EDGE_V,T),'west':face(FL_EDGE_V,T),'up':face(FL_EDGE_H,T),'down':face(FL_EDGE_H,T)}))
    for f in els:                                                   # drop None cullfaces
        for fd in f['faces'].values():
            if fd.get('cullface') is None: fd.pop('cullface',None)
    tex='encodedlogistics:block/optical_amplifier/optical_amplifier'
    jd({'parent':'minecraft:block/block','textures':{'t':tex,'particle':tex},'elements':els},'models/block/optical_amplifier/optical_amplifier.json')
    def glow(top,cores):
        g=[el('glow_top',[3,14.01,3],[13,14.01,13],{'up':face(top,'#g')},**GLOWKW)]
        if cores:
            g.append(el('glow_core_out',[7,7,-0.01],[9,9,-0.01],{'north':face(CORE_DOT,'#g',cull='north')},**GLOWKW))
            g.append(el('glow_core_in',[7,7,16.01],[9,9,16.01],{'south':face(CORE_DOT,'#g',cull='south')},**GLOWKW))
        return {'parent':'minecraft:block/block','textures':{'g':'encodedlogistics:block/optical_amplifier/glow','particle':tex},'elements':g}
    jd(glow(G_TOP_ON,True),'models/block/optical_amplifier/optical_amplifier_on.json')
    jd(glow(G_TOP_FAULT,False),'models/block/optical_amplifier/optical_amplifier_fault.json')
    parts=[]
    rot={'north':0,'east':90,'south':180,'west':270}
    for suffix,cond in (('',None),('_on','on'),('_fault','fault')):
        for f,r in rot.items():
            w={'facing':f}
            if cond: w['state']=cond
            a={'model':f'encodedlogistics:block/optical_amplifier/optical_amplifier{suffix}'}
            if r: a['y']=r
            parts.append({'when':w,'apply':a})
    jd({'multipart':parts},'blockstates/optical_amplifier.json')

# ------------------------------------------------------------------------------------------------- Directory Server
DF=(0,0,16,16); DS=(16,0,16,16); DK=(32,0,16,16); DT=(48,0,16,16); DD=(0,16,16,16)
def dir_textures():
    im=Image.new('RGBA',(64,64),(0,0,0,0))
    # front: bezel, status screen, LED row, two rows of drive bays, intake vent
    x,y,w,h=DF; panel(im,x,y,w,h)
    fill(im,x+1,y+1,14,1,G[6])
    fill(im,x+2,y+2,12,5,G[2]); fill(im,x+3,y+3,10,3,SCREEN); fill(im,x+3,y+3,10,1,SCREEN_HI)             # screen in its bezel
    fill(im,x+2,y+6,12,1,G[3])
    for i in (3,5,7): px(im,x+i,y+8,G[1])                                                           # LED sockets
    fill(im,x+10,y+8,3,1,G[3]); px(im,x+13,y+8,G[8])                                                # power button
    for row in (9,11):
        for col in (2,7):
            recess(im,x+col,y+row,5,2,G[3]); fill(im,x+col+1,y+row+1,3,1,G[4]); px(im,x+col+4,y+row+1,G[7])   # bay + handle
    fill(im,x+2,y+13,12,1,G[3])
    for i in range(2,14,2): px(im,x+i,y+13,G[1])                                                    # intake vent
    fill(im,x+1,y+14,14,1,G[4])
    screw(im,x+1,y+1); screw(im,x+14,y+1); screw(im,x+1,y+14); screw(im,x+14,y+14)
    # side: plain plate with a vent grille and a rail seam
    x,y,w,h=DS; panel(im,x,y,w,h)
    fill(im,x+1,y+7,14,1,G[4]); fill(im,x+1,y+8,14,1,G[6])                                           # chassis rail
    for j in (2,4):
        recess(im,x+3,y+j,10,1,G[2])
    for j in (10,12):
        recess(im,x+3,y+j,10,1,G[2])
    screw(im,x+1,y+1); screw(im,x+14,y+1); screw(im,x+1,y+14); screw(im,x+14,y+14)
    # back: two PSUs with fan grilles, network ports, exhaust vent
    x,y,w,h=DK; panel(im,x,y,w,h)
    for col in (1,8):
        panel(im,x+col,y+1,7,6,G[4],G[6],G[2])
        fill(im,x+col+2,y+2,3,3,G[1]); px(im,x+col+3,y+3,G[5])                                       # fan hub
        px(im,x+col+2,y+2,G[3]); px(im,x+col+4,y+4,G[3])
        fill(im,x+col+5,y+5,1,1,G[0])                                                               # power inlet
    for i in range(2,14,3): recess(im,x+i,y+8,2,2,G[1]); px(im,x+i+1,y+9,G[6])                      # RJ ports
    fill(im,x+2,y+11,12,3,G[3])
    for i in range(2,14,2):
        for j in (11,13): px(im,x+i+(j%2),y+j,G[1])                                                  # exhaust mesh
    for i in range(3,14,2): px(im,x+i,y+12,G[1])
    # top / bottom: cover plates with a seam and screws
    x,y,w,h=DT; panel(im,x,y,w,h)
    fill(im,x+1,y+5,14,1,G[3]); fill(im,x+1,y+6,14,1,G[6])
    for (i,j) in ((1,1),(14,1),(1,14),(14,14),(7,3),(8,3),(1,8),(14,8)): screw(im,x+i,y+j)
    fill(im,x+4,y+9,8,4,G[4]); fill(im,x+4,y+9,8,1,G[3]); fill(im,x+5,y+10,6,1,LABEL); fill(im,x+5,y+11,4,1,G[7])   # service label
    x,y,w,h=DD; panel(im,x,y,w,h,G[4],G[5],G[2])
    for (i,j) in ((1,1),(13,1),(1,13),(13,13)): fill(im,x+i,y+j,2,2,G[1])                          # feet
    save(im,'textures/block/directory_server/directory_server.png')

    gl=Image.new('RGBA',(64,64),(0,0,0,0)); x,y,_,_=DF
    for (i,j,n) in ((3,3,7),(3,4,9),(4,5,5)):                                                      # directory listing on the screen
        fill(gl,x+i,y+j,n,1,LED)
    px(gl,x+3,y+5,LED_HI); px(gl,x+12,y+3,LED_HI)
    px(gl,x+3,y+8,LED); px(gl,x+5,y+8,LED); px(gl,x+7,y+8,AMBER); px(gl,x+13,y+8,LED_HI)
    for row in (9,11):
        for col in (2,7): px(gl,x+col+4,y+row+1,LED)                                                # drive activity
    save(gl,'textures/block/directory_server/glow.png')

def dir_models():
    T='#t'; tex='encodedlogistics:block/directory_server/directory_server'
    body=el('body',[0,0,0],[16,16,16],{'north':face(DF,T,cull='north'),'south':face(DK,T,cull='south'),'east':face(DS,T,cull='east'),
            'west':face(DS,T,flip=True,cull='west'),'up':face(DT,T,cull='up'),'down':face(DD,T,cull='down')})
    jd({'parent':'minecraft:block/block','textures':{'t':tex,'particle':tex},'elements':[body]},'models/block/directory_server/directory_server.json')
    glow=el('glow',[0,0,-0.01],[16,16,-0.01],{'north':face(DF,'#g',cull='north')},**GLOWKW)
    jd({'parent':'minecraft:block/block','textures':{'g':'encodedlogistics:block/directory_server/glow','particle':tex},'elements':[glow]},
       'models/block/directory_server/directory_server_on.json')

if __name__=='__main__':
    amp_textures(); amp_models(); dir_textures(); dir_models()
