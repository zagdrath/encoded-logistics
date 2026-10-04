import os,sys,math,numpy as np
sys.path.insert(0,'/home/claude/desk/tools'); OUT='/home/claude/desk/previews/'
os.chdir('/home/claude/desk/preview_root')
import mcrender, json
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); f=f'assets/{ns}/textures/{path}.png'; im=Image.open(f).convert('RGBA')
    if os.path.exists(f+'.mcmeta'):
        a=json.load(open(f+'.mcmeta')).get('animation',{}); fh=a.get('height',im.width)
        if im.height>fh: n=im.height//fh; im=im.crop((0,fh*(frame%n),im.width,fh*(frame%n)+fh))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import model_quads, block_quads, render
def rotate_y(Q,pivot,deg):
    a=math.radians(deg); c,s=math.cos(a),math.sin(a); out=[]
    for v,uv,t,sh,br in Q:
        p=v-np.array(pivot); x=p[:,0]*c+p[:,2]*s; z=-p[:,0]*s+p[:,2]*c
        out.append((np.stack([x+pivot[0],p[:,1]+pivot[1],z+pivot[2]],1),uv,t,sh,br))
    return out
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
def desk(origin,screen='on',clutter=True,frame=0): return block_quads('terminal_desk',{'facing':'north','part':'master','screen':screen,'clutter':str(clutter).lower()},origin,frame)
def chair(origin,yaw):
    """yaw: degrees the sitter faces (0 = north)."""
    Q=model_quads('encodedlogistics:block/swivel_chair/base',0,0,origin)
    S=model_quads('encodedlogistics:block/swivel_chair/seat',0,0,origin)
    return Q+rotate_y(S,(origin[0]+0.5,0,origin[2]+0.5),-yaw)
if __name__=='__main__':
    F=floor(-3,5,-4,4)
    Q=desk((0,0,0),'on',True,0)+chair((0,0,-1),180)+desk((3,0,0),'off',False)+chair((5,0,-1),135)
    render(Q+F,(-0.6,2.1,-3.2),(1.2,0.7,0.4),W=1400,Hh=900,fov=55).save(OUT+'desk_overview.png')
    render(Q+F,(1.0,1.15,-1.2),(0.6,0.95,0.3),W=1300,Hh=900,fov=50).save(OUT+'desk_close.png')
    render(chair((0,0,0),200)+F,(0.5,1.2,-1.4),(0.5,0.6,0.5),W=900,Hh=900,fov=50).save(OUT+'chair.png')
    for i,(s,fr) in enumerate((('off',0),('boot',1),('boot',6),('on',0))):
        render(desk((0,0,0),s,False,fr)+F,(0.55,1.15,-1.0),(0.55,1.05,0.4),W=600,Hh=420,fov=45).save(OUT+f'screen_{i}_{s}.png')
    print('ok')
