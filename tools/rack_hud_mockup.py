# HUD popup mockups: in-world render aimed at a rack unit (crosshair = image centre) + targeted-unit outline + popup.
import os,sys,math,numpy as np
sys.path.insert(0,'/home/claude/rack/tools')
exec(open('/home/claude/rack/tools/rack_preview.py').read().split("if __name__=='__main__':")[0])
exec(open('/home/claude/rack/tools/rack_gui_preview.py').read().split('# --- elevation')[0])
OUTL=np.zeros((16,16,4),np.uint8); OUTL[...,:3]=(92,240,184); OUTL[...,3]=255
def outline(origin,u,size):
    """Thin box edges round a device's full height: x 1.5..14.5, z 1.75..29.25 (model px), inflated 0.15 px."""
    ox,oy,oz=origin; y0=RM.U_y(u)-0.15; y1=RM.U_y(u)+size+0.15; x0,x1,z0,z1=1.35,14.65,1.6,29.4; w=0.12
    P=lambda x,y,z:(ox+x/16,oy+y/16,oz+z/16); Q=[]
    def bar(a,b2):
        (xa,ya,za),(xb,yb,zb)=a,b2
        for f in ('north','south','east','west','up','down'):
            frm=(min(xa,xb)-w,min(ya,yb)-w,min(za,zb)-w); to=(max(xa,xb)+w,max(ya,yb)+w,max(za,zb)+w)
            vs=[];uv=[]
            for p,(s,t) in mcrender.face_corners(f,frm,to): vs.append(P(*p)); uv.append((16*s,16*t))
            Q.append((np.array(vs),np.array(uv),OUTL,1.0,True))
    for y in (y0,y1):
        bar((x0,y,z0),(x1,y,z0)); bar((x0,y,z0),(x0,y,z1)); bar((x1,y,z0),(x1,y,z1))
    for x in (x0,x1): bar((x,y0,z0),(x,y1,z0))
    return Q
def popup(lines,header=None,status=None,bar=None,empty=None,S=3):
    """Compose the popup panel (9-slice) at GUI scale S."""
    pad=5; rows=[]
    if empty: w=tw(empty)+2*pad; h=8+2*4
    else:
        widths=[tw(header[1])+20,tw(header[2])+20,tw(status[1])+8]+[tw(a)+8+tw(b) for a,b in lines]
        w=max(110,max(widths)+2*pad); h=4+18+6+10+len(lines)*10+(8 if bar else 0)+4
    im=Image.new('RGBA',(w,h),(0,0,0,0)); pan=tex('gui/sprites/hud/panel.png')
    # 9-slice
    for (sx,sy,dx,dy,dw,dh) in ((0,0,0,0,3,3),(13,0,w-3,0,3,3),(0,13,0,h-3,3,3),(13,13,w-3,h-3,3,3)):
        im.alpha_composite(pan.crop((sx,sy,sx+3,sy+3)),(dx,dy))
    im.alpha_composite(pan.crop((3,0,13,3)).resize((w-6,3),Image.NEAREST),(3,0)); im.alpha_composite(pan.crop((3,13,13,16)).resize((w-6,3),Image.NEAREST),(3,h-3))
    im.alpha_composite(pan.crop((0,3,3,13)).resize((3,h-6),Image.NEAREST),(0,3)); im.alpha_composite(pan.crop((13,3,16,13)).resize((3,h-6),Image.NEAREST),(w-3,3))
    im.alpha_composite(pan.crop((3,3,13,13)).resize((w-6,h-6),Image.NEAREST),(3,3))
    if empty: T(im,pad,4,empty,'#B4B4B4',True); return im.resize((w*S,h*S),Image.NEAREST)
    y=4; im.alpha_composite(tex(f'item/{header[0]}.png'),(pad,y)); T(im,pad+20,y,header[1],'#F0F0F0',True); T(im,pad+20,y+9,header[2],'#B4B4B4')
    y+=19; im.alpha_composite(tex('gui/sprites/hud/divider.png').resize((w-2*pad,1)),(pad,y)); y+=4
    dot={'Online':'dot_online','Offline':'dot_offline','Fault':'dot_fault'}[status[0]]; col={'Online':'#00D992','Offline':'#7A7A7A','Fault':'#FF6B6B'}[status[0]]
    im.alpha_composite(tex(f'gui/sprites/hud/{dot}.png'),(pad,y+1)); T(im,pad+8,y,status[1],col,True); y+=11
    for i,(a,b2) in enumerate(lines):
        T(im,pad,y,a,'#9A9A9A'); T(im,w-pad-tw(b2),y,b2,'#F0F0F0',True); y+=10
        if bar and i==bar[0]:
            im.alpha_composite(tex('gui/sprites/hud/bar_track.png').resize((w-2*pad,5)),(pad,y)); fill=tex(f'gui/sprites/hud/{bar[2]}.png')
            im.alpha_composite(fill.resize((max(1,int((w-2*pad-2)*bar[1])),3)),(pad+1,y+1)); y+=8
    return im.resize((w*S,h*S),Image.NEAREST)
def crosshair(img):
    d=ImageDraw.Draw(img); cx,cy=img.width//2,img.height//2
    d.rectangle((cx-1,cy-9,cx+1,cy+9),fill=(240,240,240)); d.rectangle((cx-9,cy-1,cx+9,cy+1),fill=(240,240,240))
F=floor(-4,8,-5,6); origin=(0,1,0)
def shot(name,target_u,size,pop,show_outline=True):
    Q=rack(origin,1.0,0,DEV)+F
    if show_outline: Q+=outline(origin,target_u,size)
    ty=origin[1]+(RM.U_y(target_u)+size/2)/16
    img=render(Q,(0.72,ty+0.22,-0.95),(0.5,ty,0.4),W=1280,Hh=760,fov=50).convert('RGBA')
    crosshair(img); img.alpha_composite(pop,(img.width//2+36,img.height//2-24)); img.convert('RGB').save(P+name)
shot('hud_empty.png',8,1,popup([],empty='U8 - Empty'),show_outline=False)
shot('hud_firewall.png',14,1,popup([('Default policy','View only'),('Custom players','3')],header=('firewall','Firewall','U14'),status=('Online','Online')))
shot('hud_router.png',12,1,popup([('Active routes','3'),('Throughput','64 items/s')],header=('router','Router','U12'),status=('Online','Online')))
shot('hud_ups.png',3,2,popup([('Battery','87%'),('Load','1,240 FE/t'),('Runtime','6m 12s'),('Mode','Online')],header=('ups','UPS','U3-U4'),status=('Online','Online - on mains'),bar=(0,0.87,'bar_fill')))
print('ok')
