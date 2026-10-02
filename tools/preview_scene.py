# Preview: renders the new blocks FROM the exported files together with the controller and cables.
# Run from the project root: python tools/preview_scene.py <vanilla textures dir>   (writes previews/*.png)
# The vanilla directory must hold smooth_stone.png and polished_blackstone.png (the facade demo; not shipped).
import os,sys,numpy as np
TOOLS=os.path.dirname(os.path.abspath(__file__)); OUT=os.path.abspath('previews')
VAN=os.path.join(os.path.abspath(sys.argv[1]),'') if len(sys.argv)>1 else ''
os.makedirs(OUT,exist_ok=True)
os.chdir('src/main/resources'); sys.path.insert(0,TOOLS)
import mcrender; from mcrender import *
from PIL import Image
TB='assets/encodedlogistics/textures/block/'
DIRS={'north':(0,0,-1),'south':(0,0,1),'east':(1,0,0),'west':(-1,0,0),'up':(0,1,0),'down':(0,-1,0)}
OPP={'north':'south','south':'north','east':'west','west':'east','up':'down','down':'up'}
ROTY={'north':0,'east':90,'south':180,'west':270}
def tex(path,frame=0):
    im=Image.open(path).convert('RGBA')
    if im.height>im.width: n=im.height//16; im=im.crop((0,16*(frame%n),16,16*(frame%n)+16))
    return np.array(im)
def cube_quads(pos,faces_tex,bright=False):
    q=[]
    for face,t in faces_tex.items():
        vs=[];uv=[]
        for p,(s,tt) in face_corners(face,(0,0,0),(16,16,16)):
            vs.append((pos[0]+p[0]/16,pos[1]+p[1]/16,pos[2]+p[2]/16)); uv.append((16*s,16*tt))
        q.append((np.array(vs),np.array(uv),t,1.0 if bright else SHADE[face],bright))
    return q
def controller(pos,frame):
    b=tex(TB+'controller_block_0.png'); e=tex(TB+'controller_block_0_emissive.png',frame)
    return cube_quads(pos,{f:b for f in DIRV})+cube_quads(pos,{f:e for f in DIRV},True)
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(116,120,126); t[0,:,:3]=(130,134,140); t[:,0,:3]=(130,134,140); t[15,:,:3]=(100,104,110); t[:,15,:3]=(100,104,110)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
def cable_colour(bid):
    for t in ('dense_network_cable','network_cable','fiber_cable'):
        if bid==t: return 'neutral'
        if bid.endswith('_'+t): return bid[:-len(t)-1]
def facade_quads(pos,sides,target,cable_dirs,hole):
    """Facade panels with the trimming rule from HANDOFF section 5."""
    out=[]
    for side in sides:
        x0,y0,z0,x1,y1,z1=0,0,0,16,16,1
        def rect(side):
            # local north panel -> rotate into place by building world boxes directly
            return {'north':(0,0,0,16,16,1),'south':(0,0,15,16,16,16),'west':(0,0,0,1,16,16),'east':(15,0,0,16,16,16),
                    'down':(0,0,0,16,1,16),'up':(0,15,0,16,16,16)}[side]
        b=list(rect(side))
        if side in ('north','south','east','west'):
            if 'up' in sides: b[4]=15
            if 'down' in sides: b[1]=1
        if side in ('east','west'):
            if 'north' in sides: b[2]=1
            if 'south' in sides: b[5]=15
        boxes=[b]
        if side in cable_dirs:                       # cut the hole the cable passes through
            a,c=8-hole//2,8+hole//2; X0,Y0,Z0,X1,Y1,Z1=b
            if side in ('north','south'):
                boxes=[[X0,c,Z0,X1,Y1,Z1],[X0,Y0,Z0,X1,a,Z1],[X0,a,Z0,a,c,Z1],[c,a,Z0,X1,c,Z1]]
            elif side in ('east','west'):
                boxes=[[X0,c,Z0,X1,Y1,Z1],[X0,Y0,Z0,X1,a,Z1],[X0,a,Z0,X1,c,a],[X0,a,c,X1,c,Z1]]
            else:
                boxes=[[X0,Y0,c,X1,Y1,Z1],[X0,Y0,Z0,X1,Y1,a],[X0,Y0,a,a,Y1,c],[c,Y0,a,X1,Y1,c]]
        for X0,Y0,Z0,X1,Y1,Z1 in boxes:
            for face in DIRV:
                vs=[];uv=[]
                for p,(s,tt) in face_corners(face,(X0,Y0,Z0),(X1,Y1,Z1)):
                    vs.append((pos[0]+p[0]/16,pos[1]+p[1]/16,pos[2]+p[2]/16))
                    # world-mapped UVs: the target's texture lines up across neighbouring blocks
                    u={'north':16-p[0],'south':p[0],'west':p[2],'east':16-p[2],'up':p[0],'down':p[0]}[face]
                    v={'up':p[2],'down':16-p[2]}.get(face,16-p[1])
                    uv.append((min(16,max(0,u)),min(16,max(0,v))))
                out.append((np.array(vs),np.array(uv),target,SHADE[face],False))
    return out
def world_quads(W,frame=4):
    Q=[]
    def kind(p): return W.get(p,(None,))[0]
    for pos,obj in W.items():
        k=obj[0]
        if k=='controller': Q+=controller(pos,frame); continue
        if k=='power_inlet': Q+=block_quads('power_inlet',{'facing':obj[1],'powered':obj[2]},pos,frame); continue
        if k=='capacitor_bank': Q+=block_quads('capacitor_bank',{'fill':str(obj[1])},pos,frame); continue
        if k=='segment_isolator': Q+=block_quads('segment_isolator',{'axis':obj[1],'active':obj[2]},pos,frame); continue
        if k=='cable':
            bid=obj[1]; anchors=obj[2] if len(obj)>2 else set(); facades=obj[3] if len(obj)>3 else None
            st={}
            for d,(dx,dy,dz) in DIRS.items():
                n=(pos[0]+dx,pos[1]+dy,pos[2]+dz); nb=W.get(n)
                if d in anchors or nb is None: st[d]='none'; continue
                if nb[0]=='cable':
                    if OPP[d] in (nb[2] if len(nb)>2 else set()): st[d]='none'; continue
                    a,b=cable_colour(bid),cable_colour(nb[1]); st[d]='cable' if (a=='neutral' or b=='neutral' or a==b) else 'none'
                elif nb[0]=='power_inlet': st[d]='block' if OPP[d]!=nb[1] else 'none'
                elif nb[0]=='segment_isolator': st[d]='block' if d in {'x':('east','west'),'y':('up','down'),'z':('north','south')}[nb[1]] else 'none'
                else: st[d]='block'
            if facades: st={d:('cable' if v=='block' and d in facades[0] else v) for d,v in st.items()}   # facade side: plain arm, no flange
            Q+=block_quads(bid,st,pos,frame)
            for d in anchors:
                dense=bid.startswith('dense') or '_dense_' in bid
                m='encodedlogistics:block/cable_anchor'+('_dense' if dense else '')
                rx={'up':270,'down':90}.get(d,0); ry=ROTY.get(d,0)
                Q+=model_quads(m,rx,ry,pos,frame)
            if facades:
                sides,target=facades; dirs=[d for d,v in st.items() if v!='none']
                hole=8 if ('dense' in bid) else 6
                Q+=facade_quads(pos,sides,target,dirs,hole)
    return Q
if __name__=='__main__':
    stone=tex(VAN+'smooth_stone.png'); black=tex(VAN+'polished_blackstone.png')
    W={(0,0,0):('controller',),(-1,0,0):('power_inlet','south','true'),(-1,0,2):('power_inlet','south','false')}
    W[(-1,0,1)]=('cable','network_cable')
    for x in range(1,6): W[(x,0,0)]=('cable','network_cable')
    for i,(x,f) in enumerate(((1,1),(2,2),(3,3),(4,4))): W[(x,1,0)]=('capacitor_bank',f)
    W[(0,1,0)]=('capacitor_bank',0)
    W[(6,0,0)]=('segment_isolator','x','true')
    for x in range(7,10): W[(x,0,0)]=('cable','orange_network_cable')
    for z in range(1,4): W[(2,0,z)]=('cable','cyan_fiber_cable')
    for z in range(1,4): W[(3,0,z)]=('cable','magenta_fiber_cable')       # side by side with cyan: no connection
    for z in range(1,3): W[(5,0,z)]=('cable','lime_fiber_cable')
    W[(5,0,0)]=('cable','network_cable',{'south'})                     # anchored: lime fibre below it stays separate
    W[(8,0,0)]=('cable','orange_network_cable',set(),(['up','north','south'],black))
    W[(9,0,0)]=('cable','orange_network_cable',set(),(['up','down','north','south','east','west'],stone))
    F=floor(-3,12,-3,6)
    Q=world_quads(W)+F
    render(Q,(4.6,3.3,7.4),(3.6,0.6,0.8),W=1600,Hh=900,fov=58).save(OUT+'/overview.png')
    render(Q,(0.9,2.0,3.3),(0.6,0.8,0.4),W=1400,Hh=800,fov=55).save(OUT+'/inlet_capacitors.png')
    render(Q,(4.6,1.4,3.6),(3.6,0.4,1.4),W=1400,Hh=800,fov=55).save(OUT+'/fiber_anchor.png')
    render(Q,(8.6,1.6,2.6),(7.6,0.6,0.2),W=1400,Hh=800,fov=55).save(OUT+'/isolator_facades.png')
    print('ok')
