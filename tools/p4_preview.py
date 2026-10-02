import os,sys,numpy as np
sys.path.insert(0,'/home/claude/p4/tools'); OUT='/home/claude/p4/previews/'
os.chdir('/home/claude/p4/preview_root')
import mcrender
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); im=Image.open(f'assets/{ns}/textures/{path}.png').convert('RGBA')
    if im.height>im.width: n=im.height//im.width; im=im.crop((0,16*(frame%n),16,16*(frame%n)+16))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import model_quads, block_quads, render
ROT={'north':(0,0),'south':(0,180),'east':(0,90),'west':(0,270),'up':(270,0),'down':(90,0)}
def part(name,pos,side,frame=0): rx,ry=ROT[side]; return model_quads('encodedlogistics:part/'+name,rx,ry,pos,frame)
def plane(kind,pos,side,mask,active=False):
    q=part(kind+('_active' if active else ''),pos,side,0)
    v,uv,_,sh,br=q[0]; q[0]=(v,uv,load_tex(f'encodedlogistics:block/part/{kind}/ctm_{mask:02d}'),sh,br)
    return q
def cable(pos,conn):
    st={d:'none' for d in ('north','south','east','west','up','down')}; st.update(conn); return block_quads('network_cable',st,pos)
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
Q=[]
Q+=block_quads('relay_antenna',{'facing':'south','online':'false'},(0,0,0))+block_quads('relay_antenna',{'facing':'south','online':'true'},(1,0,0),0)
for i,s in enumerate(('unlinked','linked_idle','linked_active')): Q+=block_quads('network_bridge',{'facing':'south','status':s},(3+i,0,0),2)
# P2P endpoints on a cable run (in on the bottom row, out on the row above)
for x in range(0,4):
    Q+=cable((x,0,3),{'east':'cable' if x<3 else 'none','west':'cable' if x>0 else 'none','up':'cable'})
    Q+=cable((x,1,3),{'east':'cable' if x<3 else 'none','west':'cable' if x>0 else 'none','down':'cable'})
for i,k in enumerate(('items','energy','redstone','lanes')):
    Q+=part(f'p2p_{k}_in_linked',(i,0,3),'south'); Q+=part(f'p2p_{k}_out_linked',(i,1,3),'south')
# collector planes: 2x2 wall on cables (south faces): mask bits up=1 right=2 down=4 left=8 (seen from the front)
for x in (7,8):
    for y in (0,1):
        Q+=cable((x,y,1),{'east':'cable' if x==7 else 'none','west':'cable' if x==8 else 'none','up':'cable' if y==0 else 'none','down':'cable' if y==1 else 'none'})
        m=(1 if y==0 else 0)|(2 if x==7 else 0)|(4 if y==1 else 0)|(8 if x==8 else 0)
        Q+=plane('collector_plane',(x,y,1),'south',m,active=(x,y)==(7,0))
for x in (10,11):
    Q+=cable((x,0,1),{'east':'cable' if x==10 else 'none','west':'cable' if x==11 else 'none'})
    Q+=plane('deployer_plane',(x,0,1),'south',(2 if x==10 else 0)|(8 if x==11 else 0))
F=floor(-2,14,-3,6)
render(Q+F,(5.6,3.4,8.4),(5.6,0.8,1.2),W=1600,Hh=900,fov=60).save(OUT+'overview.png')
render(Q+F,(2.2,2.4,3.4),(2.6,1.0,0.4),W=1400,Hh=800,fov=60).save(OUT+'relay_bridge.png')
render(Q+F,(2.0,1.4,5.6),(2.0,1.0,3.4),W=1400,Hh=800,fov=55).save(OUT+'p2p.png')
render(Q+F,(9.2,1.4,4.2),(9.2,1.0,1.4),W=1400,Hh=800,fov=60).save(OUT+'planes.png')
print('ok')
