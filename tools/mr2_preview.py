import os,sys,json,numpy as np
sys.path.insert(0,'/home/claude/mr2/tools'); os.chdir('/home/claude/mr2/root')
import mcrender
from PIL import Image, ImageFilter
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); f=f'assets/{ns}/textures/{path}.png'; im=Image.open(f).convert('RGBA')
    if os.path.exists(f+'.mcmeta'):
        a=json.load(open(f+'.mcmeta')).get('animation',{}); fh=a.get('height',im.width); fw=a.get('width',im.width)
        if im.height>fh: n=im.height//fh; im=im.crop((0,fh*(frame%n),fw,fh*(frame%n)+fh))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import block_quads, render
OUT='/home/claude/mr2/previews/'
def floor(x0,x1,z0,z1):
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(120,124,130); t[0,:,:3]=(136,140,146); t[:,0,:3]=(136,140,146)
    return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
def person(x,z):
    """1.8 m reference column (0.6 x 1.8 x 0.3 m = 9.6 x 28.8 x 4.8 px) - scale check only."""
    t=np.zeros((16,16,4),np.uint8); t[...,3]=255; t[...,:3]=(70,110,160); Q=[]
    x0,x1,z0,z1,h=x+0.2,x+0.8,z+0.35,z+0.65,1.8
    for (a,b,c_,d) in (((x0,0,z0),(x1,0,z0),(x1,h,z0),(x0,h,z0)),((x0,0,z1),(x1,0,z1),(x1,h,z1),(x0,h,z1)),((x0,0,z0),(x0,0,z1),(x0,h,z1),(x0,h,z0)),
                       ((x1,0,z0),(x1,0,z1),(x1,h,z1),(x1,h,z0)),((x0,h,z0),(x1,h,z0),(x1,h,z1),(x0,h,z1))):
        Q.append((np.array([a,b,c_,d],float),np.array([(0,16),(16,16),(16,0),(0,0)],float),t,0.85,False))
    return Q
def scene(state='busy',active=True,console='on'):
    Q=block_quads('midrange_system',{'part':'master','facing':'north','expansion':'pos','state':state},(0,0,0),2)
    Q+=block_quads('expansion_cabinet',{'facing':'north','attached':'neg','lit':'true'},(2,0,0),0)
    Q+=block_quads('integrated_midrange',{'part':'master','facing':'north','state':state,'console':console},(4,0,0),1)
    Q+=block_quads('keypunch',{'facing':'north','part':'master','active':str(active).lower()},(0,0,-3),2)
    Q+=block_quads('card_reader',{'facing':'north','active':str(active).lower()},(1,0,-3),2)
    Q+=block_quads('line_printer',{'facing':'north','part':'master','active':str(active).lower()},(3,0,-3),2)
    Q+=block_quads('terminal_desk',{'facing':'north','part':'master','screen':'on','clutter':'false'},(-3,0,0),0)
    Q+=person(-1,0)+person(-1,-3)
    return Q
if __name__=='__main__':
    F=floor(-4,8,-5,2); Q=scene()
    render(Q+F,(-2.0,2.6,-7.2),(2.4,0.6,-1.0),W=1500,Hh=950,fov=50,ss=1).save(OUT+'overview.png')
    render(Q+F,(3.2,1.7,-2.0),(3.0,0.6,0.5),W=1200,Hh=850,fov=55,ss=1).save(OUT+'midrange_pair.png')
    render(Q+F,(5.9,1.9,-1.9),(5.0,0.85,0.6),W=1200,Hh=850,fov=55,ss=1).save(OUT+'integrated.png')
    print('ok')
