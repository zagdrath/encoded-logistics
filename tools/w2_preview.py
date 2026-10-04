import os,sys,json,math,numpy as np
sys.path.insert(0,'/home/claude/wls2/tools'); os.chdir('/home/claude/wls2/root')
import mcrender
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); f=f'assets/{ns}/textures/{path}.png'; im=Image.open(f).convert('RGBA')
    if os.path.exists(f+'.mcmeta'):
        a=json.load(open(f+'.mcmeta')).get('animation',{}); fh=a.get('height',im.width)
        if im.height>fh: n=im.height//fh; im=im.crop((0,fh*(frame%n),im.width,fh*(frame%n)+fh))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import block_quads, model_quads, render
OUT='/home/claude/wls2/previews/'
def cubeq(x,y,z,tex):
    t=load_tex('minecraft:block/'+tex); Q=[]
    for (a,b,c_,d,sh) in (((x,y+1,z),(x+1,y+1,z),(x+1,y+1,z+1),(x,y+1,z+1),1.0),((x,y,z),(x+1,y,z),(x+1,y+1,z),(x,y+1,z),0.8),
                          ((x,y,z+1),(x+1,y,z+1),(x+1,y+1,z+1),(x,y+1,z+1),0.8),((x,y,z),(x,y,z+1),(x,y+1,z+1),(x,y+1,z),0.6),
                          ((x+1,y,z),(x+1,y,z+1),(x+1,y+1,z+1),(x+1,y+1,z),0.6),((x,y,z),(x+1,y,z),(x+1,y,z+1),(x,y,z+1),0.5)):
        Q.append((np.array([a,b,c_,d],float),np.array([(0,16),(16,16),(16,0),(0,0)],float),t,sh,False))
    return Q
def rot_y(Q,piv,deg):
    a=math.radians(deg); c,s=math.cos(a),math.sin(a); out=[]
    for v,uv,t,sh,br in Q:
        p=v-np.array(piv); x=p[:,0]*c+p[:,2]*s; z=-p[:,0]*s+p[:,2]*c
        out.append((np.stack([x+piv[0],p[:,1]+piv[1],z+piv[2]],1),uv,t,sh,br))
    return out
def floor(x0,x1,z0,z1):
    t=load_tex('minecraft:block/polished_andesite'); return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
def scene():
    Q=[]
    Q+=block_quads('access_point',{'facing':'up','state':'online'},(0,0,0),0)
    Q+=cubeq(2,2,1,'smooth_stone'); Q+=block_quads('access_point',{'facing':'down','state':'linking'},(2,1,1),0)
    Q+=block_quads('wireless_bridge',{'state':'active'},(4,0,0),0)
    Q+=block_quads('network_cable',{'north':'none','south':'none','east':'block','west':'block','up':'none','down':'none'},(1,0,0),0) if False else []
    Q+=cubeq(0,0,-3,'barrel_top') if False else cubeq(0,0,-3,'iron_block')
    Q+=block_quads('wireless_ingress_port',{'facing':'north','state':'online'},(0,0,-2),0)
    Q+=cubeq(3,0,-3,'iron_block'); Q+=block_quads('wireless_egress_port',{'facing':'north','state':'fault'},(3,0,-2),0)
    Q+=block_quads('network_controller' if False else 'access_point',{'facing':'north','state':'fault'},(6,0,0),1)
    return Q
if __name__=='__main__':
    F=floor(-3,10,-6,4); Q=scene()
    render(Q+F,(1.8,2.4,-5.2),(3.4,0.6,-0.8),W=1500,Hh=900,fov=55,ss=1).save(OUT+'overview.png')
    render(Q+F,(0.5,1.7,-1.3),(0.5,0.7,0.5),W=900,Hh=800,fov=50,ss=1).save(OUT+'access_point.png')
    render(Q+F,(4.5,1.7,-1.4),(4.5,0.7,0.5),W=900,Hh=800,fov=50,ss=1).save(OUT+'wireless_bridge.png')
    render(Q+F,(1.5,1.2,-0.4),(1.5,0.5,-2.4),W=1100,Hh=700,fov=55,ss=1).save(OUT+'ports.png')
    print('ok')
