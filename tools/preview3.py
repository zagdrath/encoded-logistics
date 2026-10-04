import os,sys,math,random,numpy as np
sys.path.insert(0,'/home/claude/rack3/tools')
exec(open('/home/claude/rack3/tools/rack_preview.py').read().split("if __name__=='__main__':")[0])
import devices3 as D3
def rot_x(Q,pivot,deg):
    a=math.radians(deg); c,s=math.cos(a),math.sin(a); out=[]
    for v,uv,t,sh,br in Q:
        p=v-np.array(pivot); y=p[:,1]*c-p[:,2]*s; z=p[:,1]*s+p[:,2]*c
        out.append((np.stack([p[:,0]+pivot[0],y+pivot[1],z+pivot[2]],1),uv,t,sh,br))
    return out
def shift(Q,d): return [(v+np.array(d),uv,t,sh,br) for v,uv,t,sh,br in Q]
def console(origin,u,drawer=0.0,lid=0.0,online=True,frame=0):
    """drawer 0..1 -> -12.5 px along z; lid 0..1 -> +100 deg about X at hinge (8, 0.625, 13.5) (moves with the drawer)."""
    ox,oy,oz=origin; base=(ox,oy+RM.U_y(u)/16,oz)
    Q=model_quads('encodedlogistics:block/rack_device/rack_console',0,0,base,frame)
    D=[0,0,-12.5*drawer/16]
    Q+=shift(model_quads('encodedlogistics:block/rack_device/rack_console_drawer'+('_on' if online else ''),0,0,base,frame),D)
    Q+=shift(model_quads('encodedlogistics:block/rack_device/rack_console_keyboard',0,0,base,frame),D)
    L=model_quads('encodedlogistics:block/rack_device/rack_console_lid'+('_on' if online and lid>0.5 else ''),0,0,base,frame)
    piv=[base[0]+8/16,base[1]+0.625/16,base[2]+13.5/16]
    Q+=shift(rot_x(L,piv,100*lid),D)
    return Q
def fquad(origin,u,n,tx,ty,tw,th,tex,uvt,TH=128,bright=False,z=1.72,back=False):
    ox,oy,oz=origin; y_top=RM.U_y(u)+n
    if not back: x1=14.5-tx/8; x0=14.5-(tx+tw)/8
    else: x1=14.5-tx/8; x0=14.5-(tx+tw)/8; z=29.28                    # back u also runs right to left (RackClientDevices)
    y1=y_top-ty/8; y0=y_top-(ty+th)/8
    P=lambda x,y:(ox+x/16,oy+y/16,oz+z/16)
    if not back: vs=np.array([P(x1,y1),P(x1,y0),P(x0,y0),P(x0,y1)])
    else: vs=np.array([P(x1,y1),P(x1,y0),P(x0,y0),P(x0,y1)])
    u0,v0,u1,v1=uvt[0]/8,uvt[1]*16/TH,uvt[2]/8,uvt[3]*16/TH
    return (vs,np.array([(u0,v0),(u0,v1),(u1,v1),(u1,v0)],float),tex,1.0 if bright else 0.8,bright)
def library(origin,u,n,tapes,drives,picker=(0.5,0.3)):
    name=f'tape_library_{n}u'; TH=128 if n==4 else 256; tex=load_tex(f'encodedlogistics:block/rack_device/{name}'); ex=64 if n==4 else 128
    Q=[]
    for (sx,sy),t in zip(D3.lib_layout(n),tapes):
        if t is None: continue
        gen,fill=t; k=[6,7,8,9,10].index(gen); f=0 if fill<0.5 else 1 if fill<0.75 else 2 if fill<1 else 3
        Q.append(fquad(origin,u,n,sx,sy,3,9,tex,(k*5,ex,k*5+3,ex+9),TH))
        Q.append(fquad(origin,u,n,sx,sy+9,3,1,tex,(30+f*5,ex,33+f*5,ex+1),TH,True,1.71))
    wy1=8*n-3; px=15+int(picker[0]*46); py=4+int(picker[1]*(wy1-12))
    Q.append(fquad(origin,u,n,px,2,8,2,tex,(60,ex,68,ex+2),TH,False,1.70))                   # carriage on the rail
    for yy in range(4,py,1): Q.append(fquad(origin,u,n,px+3,yy,2,1,tex,(112,0,114,1),TH,False,1.70))
    Q.append(fquad(origin,u,n,px,py,8,6,tex,(60,ex,68,ex+6),TH,False,1.69))                  # picker head
    bays=[(4+(i%2)*26,2+(i//2)*12) for i in range(2 if n==4 else 4)]
    for (bx,by),d in zip(bays,drives):
        if d is None: continue
        Q.append(fquad(origin,u,n,bx,by,24,10,tex,(0,ex+12,24,ex+22),TH,back=True))
        s={'idle':0,'read':1,'write':2}[d]; Q.append(fquad(origin,u,n,bx+20,by+1,2,1,tex,(26+s*3,ex+12,28+s*3,ex+13),TH,True,back=True))
    return Q
r=random.Random(3)
def rand_tapes(k): return [((r.choice((6,7,8,9,10)),r.random()*1.1) if r.random()<0.8 else None) for _ in range(k)]
DEV3=[('ups',1,'on'),('tape_library_6u',3,'on'),('tape_library_4u',9,'on'),('wireless_controller',13,'on'),('l3_switch',14,'on'),('nas',15,'on')]
def scene(console_state=(1.0,1.0),front=1.0,rear=0.0):
    Q=rack((0,1,0),front,rear,DEV3,frame=1)
    Q+=console((0,1,0),17,*console_state)
    Q+=library((0,1,0),3,6,rand_tapes(48),['read','write',None,'idle'],(0.35,0.55))
    Q+=library((0,1,0),9,4,rand_tapes(24),['idle',None],(0.7,0.2))
    return Q
if __name__=='__main__':
    F=floor(-3,4,-4,6)
    Q=scene()
    render(Q+F,(0.5,0.95,-1.4),(0.5,0.85,0.5),W=1300,Hh=1150,fov=52).save(OUT+'rack_batch3_front.png')
    render(Q+F,(-0.9,1.5,-1.8),(0.5,1.0,0.3),W=1300,Hh=1000,fov=55).save(OUT+'rack_console_open.png')
    render(Q+F,(0.2,0.6,-0.85),(0.5,0.5,0.5),W=1300,Hh=800,fov=45).save(OUT+'tape_library_close.png')
    Q2=rack((0,1,0),0,1.0,DEV3,frame=1)+library((0,1,0),3,6,[None]*48,['read','write',None,'idle'])+library((0,1,0),9,4,[None]*24,['idle',None])+console((0,1,0),17)
    render(Q2+F,(0.5,0.9,3.3),(0.5,0.7,1.3),W=1300,Hh=1000,fov=55).save(OUT+'rack_batch3_rear.png')
    print('ok')
