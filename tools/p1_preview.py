import os,sys,numpy as np
# Renders the Phase 1 blocks next to the controller and cables into previews/. Run from the project root:
# python tools/p1_preview.py
TOOLS=os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0,TOOLS)
OUT=os.path.abspath('previews')+'/'
src=open(os.path.join(TOOLS,'preview_scene.py')).read().split("if __name__=='__main__':")[0]
exec(src)
from PIL import Image
D='encodedlogistics:block/drive_bay/'
def box_q(frm,to,faces,origin,ry):
    out=[]
    for f,(tx,uv,br) in faces.items():
        vs=[];uvs=[]
        for p,(s,tt) in face_corners(f,frm,to):
            q=rot_y((p[0]-8,p[1]-8,p[2]-8),ry); vs.append((origin[0]+(q[0]+8)/16,origin[1]+(q[1]+8)/16,origin[2]+(q[2]+8)/16))
            uvs.append((uv[0]+s*(uv[2]-uv[0]),uv[1]+tt*(uv[3]-uv[1])))
        out.append((np.array(vs),np.array(uvs),tx,1.0 if br else SHADE[vec_dir(rot_y(DIRV[f],ry))],br))
    return out
def drive_bay(pos,facing,slots):
    Q=block_quads('drive_bay',{'facing':facing},pos); ry={'north':0,'east':90,'south':180,'west':270}[facing]
    dr=load_tex(D+'drives'); le=load_tex(D+'leds'); gl=load_tex(D+'leds_glow'); pa=load_tex(D+'parts')
    for i,s in enumerate(slots):
        if s is None: continue
        t,fill=s; col,row=divmod(i,5); x0,y0=[1,9][col],[1,4,7,10,13][row]
        k=0 if fill<0.5 else 1 if fill<0.75 else 2 if fill<1 else 3
        X0,Y0,X1,Y1=16-x0-6,16-y0-2,16-x0,16-y0; dk=(pa,(8,0,10,2),False)
        Q+=box_q((X0,Y0,0.75),(X1,Y1,2),{'north':(dr,(0,2*t,6,2*t+2),False),'up':dk,'down':dk,'east':dk,'west':dk},pos,ry)
        Q+=box_q((16-x0-2,Y0,0.74),(X1,Y1,0.74),{'north':(le,(2*k,0,2*k+2,2),False)},pos,ry)
        Q+=box_q((16-x0-2,Y0,0.73),(X1,Y1,0.73),{'north':(gl,(2*k,0,2*k+2,2),True)},pos,ry)
    return Q
def terminal(pos,side,online,frame=0):
    rx={'up':270,'down':90}.get(side,0); ry={'north':0,'east':90,'south':180,'west':270}.get(side,0)
    return model_quads('encodedlogistics:part/access_terminal'+('_online' if online else ''),rx,ry,pos,frame)
W={(0,0,0):('controller',)}
for x in range(1,6): W[(x,0,0)]=('cable','network_cable')
W[(2,0,1)]=('cable','network_cable')
Q=world_quads(W,4)
Q+=drive_bay((3,1,0),'south',[(0,0.2),(1,0.55),(2,0.8),(3,1.0),(4,0.1),(0,0.4),None,(1,0.3),(2,0.9),(3,0.6)])
Q+=drive_bay((4,1,0),'south',[(4,0.3),(3,0.2),None,(2,0.7),(1,0.5),None,(0,0.95),(4,1.0),None,(3,0.1)])
Q+=terminal((2,0,1),'south',True,2)
Q+=block_quads('lithography_press',{'facing':'south','active':'false'},(-2,0,1))+block_quads('lithography_press',{'facing':'south','active':'true'},(-1,0,2),3)
F=floor(-4,8,-3,6)
render(Q+F,(2.2,2.6,6.4),(1.6,0.7,1.0),W=1600,Hh=900,fov=58).save(OUT+'overview.png')
render(Q+F,(-1.0,1.3,4.0),(-1.1,0.5,1.6),W=1400,Hh=800,fov=55).save(OUT+'lithography_press.png')
render(Q+F,(3.0,1.5,3.6),(2.7,0.6,1.2),W=1400,Hh=800,fov=55).save(OUT+'access_terminal.png')
render(Q+F,(4.2,2.0,3.9),(3.6,1.3,0.6),W=1400,Hh=800,fov=55).save(OUT+'drive_bay.png')
print('ok')
