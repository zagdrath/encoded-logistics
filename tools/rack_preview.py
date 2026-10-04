import os,sys,math,numpy as np
sys.path.insert(0,'/home/claude/rack/tools'); OUT='/home/claude/rack/previews/'
os.chdir('/home/claude/rack/preview_root')
import mcrender
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); f=f'assets/{ns}/textures/{path}.png'; im=Image.open(f).convert('RGBA')
    if os.path.exists(f+'.mcmeta'):                                  # animated strips; frame height from the mcmeta (6U: 256)
        import json as _j
        a=_j.load(open(f+'.mcmeta')).get('animation',{}); fh=a.get('height',im.width)
        if im.height>fh: n=im.height//fh; im=im.crop((0,fh*(frame%n),im.width,fh*(frame%n)+fh))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import model_quads, block_quads, render
import rack_models as RM
def rotate_y(Q,pivot,deg):
    """BER door swing: rotate quads about a vertical axis through pivot (world coords). Matches poseStack
       translate(pivot) -> mulPose(Axis.YP.rotationDegrees(deg)) -> translate(-pivot)."""
    a=math.radians(deg); c,s=math.cos(a),math.sin(a); out=[]
    for v,uv,t,sh,br in Q:
        p=v-np.array(pivot); x=p[:,0]*c+p[:,2]*s; z=-p[:,0]*s+p[:,2]*c
        out.append((np.stack([x+pivot[0],p[:,1]+pivot[1],z+pivot[2]],1),uv,t,sh,br))
    return out
def rack(origin,front_open=0.0,rear_open=0.0,devices=(),state='on',frame=0):
    """origin = master block (middle of the front column). Doors: front -110 deg at full open (outward), rear leaves
       left -/right + 110 deg; open in 0..1."""
    Q=model_quads('encodedlogistics:block/rack/rack_frame',0,0,origin,frame)
    ox,oy,oz=origin; px=lambda p: [ox+p[0]/16,oy+p[1]/16,oz+p[2]/16]
    Q+=rotate_y(model_quads('encodedlogistics:block/rack/rack_door_front',0,0,origin),px([15.5,0,0.5]),-110*front_open)
    Q+=rotate_y(model_quads('encodedlogistics:block/rack/rack_door_rear_left',0,0,origin),px([15.5,0,31.5]),110*rear_open)
    Q+=rotate_y(model_quads('encodedlogistics:block/rack/rack_door_rear_right',0,0,origin),px([0.5,0,31.5]),-110*rear_open)
    for (name,u,st) in devices:
        m=f'encodedlogistics:block/rack_device/{name}'+('' if st=='off' else '_'+st)
        Q+=model_quads(m,0,0,(ox,oy+RM.U_y(u)/16,oz),frame)
    return Q
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
DEV=[('ups',3,'on'),('router',12,'on'),('firewall',14,'on'),('firewall',20,'fault'),('router',30,'off')]
if __name__=='__main__':
    F=floor(-4,8,-5,6)
    Q=rack((0,1,0),0,0,DEV)+rack((2,1,0),1.0,0,DEV)
    render(Q+F,(-1.4,2.6,-4.4),(1.2,1.4,0.6),W=1400,Hh=1000,fov=55).save(OUT+'rack_front.png')
    render(Q+F,(3.2,1.7,-1.6),(2.4,1.2,0.5),W=1400,Hh=1000,fov=55).save(OUT+'rack_open_close.png')
    Q2=rack((0,1,0),0,1.0,DEV)
    render(Q2+F,(1.6,2.4,5.4),(0.5,1.4,1.2),W=1400,Hh=1000,fov=55).save(OUT+'rack_rear_open.png')
    print('ok')
