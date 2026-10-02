import os,sys,numpy as np
sys.path.insert(0,'/home/claude/p3/tools'); OUT='/home/claude/p3/previews/'
os.chdir('/home/claude/p3/preview_root')
import mcrender
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); im=Image.open(f'assets/{ns}/textures/{path}.png').convert('RGBA')
    if im.height>im.width: n=im.height//im.width; im=im.crop((0,16*(frame%n),16,16*(frame%n)+16))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import model_quads, block_quads, render, face_corners, SHADE, DIRV
ROT={'north':(0,0),'south':(0,180),'east':(0,90),'west':(0,270),'up':(270,0),'down':(90,0)}
def part(name,pos,side,frame=0): rx,ry=ROT[side]; return model_quads('encodedlogistics:part/'+name,rx,ry,pos,frame)
# scheduler CTM, using the same face convention as the controller handoff (east/north mirrored, masks by neighbour)
FACES={'south':((1,0,0),(0,-1,0),(0,0,1)),'east':((0,0,1),(0,-1,0),(1,0,0)),'up':((1,0,0),(0,0,1),(0,1,0))}
def add(a,b,k=1): return (a[0]+k*b[0],a[1]+k*b[1],a[2]+k*b[2])
def sched(S,frame=3):
    Q=[]
    for p,kind in S.items():
        for f,(r,d,n) in FACES.items():
            if add(p,n) in S: continue
            m=(1 if add(p,d,-1) in S else 0)|(2 if add(p,r) in S else 0)|(4 if add(p,d) in S else 0)|(8 if add(p,r,-1) in S else 0)
            base=Image.fromarray(load_tex(f'encodedlogistics:block/scheduler/{kind}_ctm_{m:02d}'))
            glow=None
            if kind=='scheduler_core': glow=Image.fromarray(load_tex('encodedlogistics:block/scheduler/core_display',frame))
            if kind=='thread_unit': glow=Image.fromarray(load_tex('encodedlogistics:block/scheduler/thread_unit_glow'))
            for img_,bright in ((base,False),(glow,True)):
                if img_ is None: continue
                t=img_.transpose(Image.FLIP_LEFT_RIGHT) if f=='east' else img_
                vs=[];uv=[]
                for q,(s,tt) in face_corners(f,(0,0,0),(16,16,16)):
                    vs.append((p[0]+q[0]/16,p[1]+q[1]/16,p[2]+q[2]/16)); uv.append((16*s,16*tt))
                Q.append((np.array(vs),np.array(uv),np.array(t),1.0 if bright else SHADE[f],bright))
    return Q
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
S={}
for x in range(3):
    for y in range(2):
        for z in range(2): S[(x,y,z)]='job_buffer'
S[(1,0,1)]='scheduler_core'; S[(0,0,1)]='thread_unit'; S[(2,0,1)]='thread_unit'
Q=sched(S)
Q+=block_quads('fabricator',{'facing':'south','active':'false'},(4,0,1))+block_quads('fabricator',{'facing':'south','active':'true'},(5,0,1))
Q+=block_quads('gateway',{'active':'true'},(6,0,1))
for x in range(4,8):
    Q+=block_quads('network_cable',{'north':'none','south':'none','up':'none','down':'block' if False else 'none','east':'cable' if x<7 else 'none','west':'cable' if x>4 else 'none'},(x,1,1))
Q+=part('schematic_encoder_online',(7,1,1),'south',4)
Q+=block_quads('deepslate_gallium_ore',{},(8,0,2))
F=floor(-2,11,-3,6)
render(Q+F,(4.2,3.3,6.6),(4.0,0.9,1.0),W=1600,Hh=900,fov=58).save(OUT+'overview.png')
render(Q+F,(1.5,1.6,4.1),(1.5,1.0,1.4),W=1400,Hh=800,fov=60).save(OUT+'scheduler.png')
render(Q+F,(5.5,1.2,3.5),(5.0,0.6,1.4),W=1400,Hh=800,fov=55).save(OUT+'fabricator.png')
render(Q+F,(7.4,1.6,3.2),(7.2,1.0,1.4),W=1400,Hh=800,fov=55).save(OUT+'gateway_encoder_ore.png')
print('ok')
