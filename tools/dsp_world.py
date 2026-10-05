import os,sys,json,numpy as np
sys.path.insert(0,'/home/claude/dsp/tools'); os.chdir('/home/claude/dsp/root')
import mcrender
from PIL import Image
def load_tex(rl,frame=0):
    ns,path=rl.split(':'); f=f'assets/{ns}/textures/{path}.png'; im=Image.open(f).convert('RGBA')
    if os.path.exists(f+'.mcmeta'):
        a=json.load(open(f+'.mcmeta')).get('animation',{}); fh=a.get('height',im.width)
        if im.height>fh: n=im.height//fh; im=im.crop((0,fh*(frame%n),im.width,fh*(frame%n)+fh))
    return np.array(im)
mcrender.load_tex=load_tex
from mcrender import block_quads, render
def wall_q(x0,x1,y1,z):
    t=load_tex('minecraft:block/smooth_stone'); return [(np.array([(X,Y+1,z),(X+1,Y+1,z),(X+1,Y,z),(X,Y,z)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,0.9,False) for X in range(x0,x1) for Y in range(0,y1)]
def floor(x0,x1,z0,z1):
    t=load_tex('minecraft:block/polished_andesite'); return [(np.array([(X,0,Z),(X+1,0,Z),(X+1,0,Z+1),(X,0,Z+1)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),t,1.0,False) for X in range(x0,x1) for Z in range(z0,z1)]
def screen(X0,Y0,cols,rows,canvas_png,z=0):
    """Panels at x X0-i (screen-left = +x when facing north), y Y0+j; bezel only on the merged screen's outer edges;
       the canvas (32 px per block) drawn on each block's glass, emissive - as the block entity renderer will."""
    cv=Image.open(canvas_png).convert('RGBA'); Q=[]
    for i in range(cols):
        for j in range(rows):
            x,y=X0-i,Y0+j
            st={'facing':'north','top':str(j<rows-1).lower(),'bottom':str(j>0).lower(),'left':str(i>0).lower(),'right':str(i<cols-1).lower(),
                'led':str(i==cols-1 and j==0).lower(),'state':'online'}
            Q+=block_quads('display_panel',st,(x,y,z),0)
            sl=np.array(cv.crop((32*i,32*(rows-1-j),32*i+32,32*(rows-j))).resize((32,32),Image.NEAREST))
            zz=z+0.5-0.004
            Q.append((np.array([(x+1,y,zz),(x,y,zz),(x,y+1,zz),(x+1,y+1,zz)],float),np.array([(0,16),(16,16),(16,0),(0,0)],float),sl,1.0,True))
    return Q
if __name__=='__main__':
    raw='/home/claude/dsp/previews/raw_3x2.png'
    Q=wall_q(-6,4,5,1.0)+floor(-6,4,-6,1)
    Q+=screen(1,1,3,2,raw)
    Q+=block_quads('display_panel',{'facing':'north','top':'false','bottom':'false','left':'false','right':'false','led':'true','state':'nosignal'},(-3,2,0),0)
    Q+=block_quads('small_wireless_bridge',{'facing':'north','state':'linked'},(-4,1,-1),0)
    Q+=[(np.array([(X,Y+1,0),(X+1,Y+1,0),(X+1,Y,0),(X,Y,0)],float),np.array([(0,0),(16,0),(16,16),(0,16)],float),load_tex('minecraft:block/iron_block'),0.8,False) for X,Y in ((-4,1),)]
    render(Q,(-0.8,2.0,-4.6),(-0.8,1.9,0.5),W=1400,Hh=850,fov=55,ss=1).save('/home/claude/dsp/previews/world_3x2.png'); print('ok')
