import os,sys,numpy as np
# Renders the Phase 2 parts and ores into previews/. Run from the project root: python tools/p2_preview.py <vanilla assets dir>
# (the vanilla dir holds assets/minecraft/textures/..., for the stone, deepslate and barrels in the scene; not shipped)
TOOLS=os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0,TOOLS); OUT=os.path.abspath('previews')+'/'
VANILLA=os.path.abspath(sys.argv[1]) if len(sys.argv)>1 else None
os.makedirs(OUT,exist_ok=True); os.chdir('src/main/resources')
from mcrender import *
import mcrender
from PIL import Image
_orig=mcrender.load_tex
def load_tex(rl,frame=0):                      # resolve minecraft: textures too (preview only)
    ns,path=rl.split(':'); p=f'assets/{ns}/textures/{path}.png'
    if ns=='minecraft' and VANILLA: p=os.path.join(VANILLA,p)
    im=Image.open(p).convert('RGBA')
    if im.height>im.width: n=im.height//im.width; im=im.crop((0,16*(frame%n),16,16*(frame%n)+16))
    return np.array(im)
mcrender.load_tex=load_tex
import importlib; importlib.reload(mcrender)
mcrender.load_tex=load_tex
from mcrender import model_quads, block_quads, render, face_corners, SHADE, DIRV
ROT={'north':(0,0),'south':(0,180),'east':(0,90),'west':(0,270),'up':(270,0),'down':(90,0)}
def part(name,pos,side,frame=0): rx,ry=ROT[side]; return mcrender.model_quads(RLp(name),rx,ry,pos,frame)
def RLp(n): return 'encodedlogistics:part/'+n
def cube(pos,tex):
    q=[]
    for f in DIRV:
        t=load_tex(tex.get(f,tex['side']))
        vs=[];uv=[]
        for p,(s,tt) in face_corners(f,(0,0,0),(16,16,16)):
            vs.append((pos[0]+p[0]/16,pos[1]+p[1]/16,pos[2]+p[2]/16)); uv.append((16*s,16*tt))
        q.append((np.array(vs),np.array(uv),t,SHADE[f],False))
    return q
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
Q=[]
for x in range(0,7):
    st={'north':'none','south':'none','up':'none','down':'none','east':'cable' if x<6 else 'none','west':'cable' if x>0 else 'none'}
    Q+=block_quads('network_cable',st,(x,1,0))
barrel={'side':'minecraft:block/barrel_side','up':'minecraft:block/barrel_top','down':'minecraft:block/barrel_top'}
for x in (1,3,5): Q+=cube((x,1,-1),barrel)
Q+=part('ingress_port_active',(1,1,0),'north',2)+part('egress_port_active',(3,1,0),'north',2)+part('inventory_tap_active',(5,1,0),'north')
Q+=part('threshold_sensor_on',(2,1,0),'south')+part('fabrication_terminal_online',(4,1,0),'south',3)+part('threshold_sensor',(6,1,0),'south')
Q+=part('ingress_port',(0,1,0),'south')
for i,n in enumerate(('neodymium_ore','deepslate_neodymium_ore','tantalum_ore','deepslate_tantalum_ore')): Q+=block_quads(n,{},(1+i,0,2))
F=floor(-2,9,-3,5)
render(Q+F,(3.2,2.6,5.6),(3.0,0.9,0.4),W=1600,Hh=900,fov=58).save(OUT+'overview.png')
render(Q+F,(2.0,1.9,2.4),(2.2,1.3,0.2),W=1400,Hh=800,fov=55).save(OUT+'parts_front.png')
render(Q+F,(1.2,2.2,-2.6),(2.6,1.2,-0.4),W=1400,Hh=800,fov=60).save(OUT+'ports_back.png')
render(Q+F,(2.6,1.2,4.3),(2.6,0.4,2.2),W=1400,Hh=800,fov=55).save(OUT+'ores.png')
print('ok')
