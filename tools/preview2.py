import os,sys,math,numpy as np
sys.path.insert(0,'/home/claude/rack2/tools')
exec(open('/home/claude/rack2/tools/rack_preview.py').read().split("if __name__=='__main__':")[0])
import devices2 as D2
def front_quad(origin,u,n,tx,ty,tw,th,tex,uv,bright=False,z=1.73):
    """Quad on a device front at texture-space rect (tx,ty,tw,th) of an n-U device at U u (renderer extra)."""
    ox,oy,oz=origin; y_top=RM.U_y(u)+n
    x1=14.5-tx/8; x0=14.5-(tx+tw)/8; y1=y_top-ty/8; y0=y_top-(ty+th)/8
    P=lambda x,y:(ox+x/16,oy+y/16,oz+z/16)
    vs=np.array([P(x1,y1),P(x1,y0),P(x0,y0),P(x0,y1)]); u0,v0,u1,v1=uv
    uvs=np.array([(u0,v0),(u0,v1),(u1,v1),(u1,v0)],float)
    return (vs,uvs,tex,1.0 if bright else 0.8,bright)
def drives(origin,kind,u,occupancy):
    """occupancy: list per bay of None or (tier 0..4, fill 0..1)."""
    tex=load_tex(f'encodedlogistics:block/rack_device/{kind}'); n=2 if kind=='nas' else 4
    bays=D2.nas_bays() if kind=='nas' else D2.san_bays(); w,h=(12,14) if kind=='nas' else (6,14); y0=64 if kind=='nas' else 80
    Q=[]
    for (bx,by),occ in zip(bays,occupancy):
        if occ is None: continue
        t,f=occ; k=0 if f<0.5 else 1 if f<0.75 else 2 if f<1 else 3
        sx=t*(w+1); Q.append(front_quad(origin,u,n,bx,by,w,h,tex,(sx/8,y0/8,(sx+w)/8,(y0+h)/8)))           # UV in 0..16 (texel/8)
        lx=70+k*3; ly=y0
        Q.append(front_quad(origin,u,n,bx+w-3,by+h-4,2,2,tex,(lx/8,ly/8,(lx+2)/8,(ly+2)/8),True,1.72))
    return Q
# our uv in face quads is texel space for load_tex arrays: convert
def fix(Q):
    out=[]
    for v,uv,t,s,b in Q:
        out.append((v,np.array(uv)*1.0,t,s,b))
    return out
import random
r=random.Random(5)
DEV2=[('ups',1,'on'),('san',3,'on'),('nas',7,'on'),('compute_server',9,'on'),('memory_server',11,'on'),('fabrication_server',12,'on'),
      ('monitoring_server',14,'on'),('l3_switch',15,'on'),('l2_switch_48',16,'on'),('l2_switch_24',17,'fault'),('firewall',18,'on'),('router',19,'on')]
def scene(front_open=1.0,rear_open=0.0):
    Q=rack((0,1,0),front_open,rear_open,DEV2,frame=1)
    nas_occ=[(0,0.2),(1,0.6),(2,0.85),(3,1.0),None,(4,0.1)]
    san_occ=[(r.randrange(5),r.random()*1.1) if r.random()<0.8 else None for _ in range(24)]
    Q+=drives((0,1,0),'nas',7,nas_occ)+drives((0,1,0),'san',3,san_occ)
    return Q
if __name__=='__main__':
    F=floor(-3,4,-4,6)
    Q=scene()
    render(Q+F,(0.55,2.2,-1.35),(0.5,1.55,0.6),W=1300,Hh=1000,fov=55).save(OUT+'rack_batch2_front.png')
    render(Q+F,(-1.6,2.6,-3.6),(0.6,1.4,0.6),W=1300,Hh=1000,fov=55).save(OUT+'rack_batch2_overview.png')
    Q2=rack((0,1,0),0,1.0,DEV2,frame=1)
    render(Q2+F,(0.5,2.0,3.6),(0.5,1.5,1.3),W=1300,Hh=1000,fov=55).save(OUT+'rack_batch2_rear.png')
    print('ok')
