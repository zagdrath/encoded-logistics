# Optical gear + modular chassis. Rack devices are built FROM the shipped rack-device sheets (ears, body tones, sockets, LED
# conventions); block forms use tools/el_kit.py (docs/TEXTURE_STYLE.md).
import os,sys,json
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import el_kit as K
R='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/'; A='src/main/resources/assets/encodedlogistics/'; RL=lambda p:'encodedlogistics:'+p
T=R+'textures/block/rack_device/'
H=lambda h: tuple(int(h[i:i+2],16) for i in (1,3,5))
def save(im,p): os.makedirs(os.path.dirname(A+p),exist_ok=True); im.save(A+p)
def jd(o,p): os.makedirs(os.path.dirname(A+p),exist_ok=True); json.dump(o,open(A+p,'w'),indent=1)
def mc(p,ft): open(A+p+'.mcmeta','w').write(json.dumps({'animation':{'frametime':ft}},indent=2)+'\n')
TAPE=Image.open(T+'tape_library_6u.png').convert('RGBA'); ROUT=Image.open(T+'router.png').convert('RGBA'); MON=Image.open(T+'monitoring_server.png').convert('RGBA')
# shipped palette, sampled from the Tape Library front
EAR_L=TAPE.crop((0,0,10,48)); EAR_R=TAPE.crop((94,0,104,48))
BODY=TAPE.getpixel((92,40)); INSET=TAPE.getpixel((40,30)); LIP=TAPE.getpixel((40,4)); SHADE=TAPE.getpixel((40,46))
SOCKET=ROUT.crop((12,1,20,7))                                                   # the Router's RJ45 socket (8 x 6)
GRN,GRN_G=H('#3CE05A'),H('#B5FFB0'); AMB,AMB_G=H('#F08A2A'),H('#FFC890'); RED,RED_G=H('#E5483C'),H('#FF9A8A')
WAVE=[H(x) for x in ('#B02E26','#EC7F27','#FED83D','#87D32E','#5E9C2E','#50C2EC','#3C44AA','#8932B8')]   # 8 channel colours (dye bases)
def ears(h):
    """Full-height ears from the shipped Tape Library: its 48-texel ear, extended by repeating its middle (screws keep their spacing)."""
    L=Image.new('RGBA',(10,h)); Rr=Image.new('RGBA',(10,h))
    for y in range(h):
        sy=y if y<40 else (40+(y-40)%8 if y<h-8 else 48-(h-y))
        L.putpixel((0,0),(0,0,0,0))
        for x in range(10): L.putpixel((x,y),EAR_L.getpixel((x,min(47,sy)))); Rr.putpixel((x,y),EAR_R.getpixel((x,min(47,sy))))
    return L,Rr
def fill(im,x0,y0,w,h,c):
    for y in range(h):
        for x in range(w): im.putpixel((x0+x,y0+y),tuple(c)[:3]+(255,))
def inset(im,x0,y0,w,h):
    """A recessed panel the shipped way: inset fill, a lit lip along the top, a shade line along the bottom."""
    fill(im,x0,y0,w,h,INSET)
    for x in range(w): im.putpixel((x0+x,y0),tuple(LIP)[:3]+(255,)); im.putpixel((x0+x,y0+h-1),tuple(SHADE)[:3]+(255,))
def blank(im,x0,y0,w,h):
    """An empty-slot blanking plate: body tone, lit top / left, shade bottom / right, two screws."""
    fill(im,x0,y0,w,h,BODY)
    for x in range(w): im.putpixel((x0+x,y0),tuple(LIP)[:3]+(255,)); im.putpixel((x0+x,y0+h-1),tuple(SHADE)[:3]+(255,))
    for y in range(h): im.putpixel((x0,y0+y),tuple(LIP)[:3]+(255,)); im.putpixel((x0+w-1,y0+y),tuple(SHADE)[:3]+(255,))
    for (sx,sy) in ((x0+1,y0+1),(x0+w-2,y0+h-2)):
        if w>3 and h>3: im.putpixel((sx,sy),EAR_L.getpixel((4,4))[:3]+(255,))
def chassis_sheet(U,slots_fn):
    """A tall rack device sheet (128 x 256): ears + body at rows 0..8U, the rear at 128.."""
    h=8*U; im=Image.new('RGBA',(128,256),(0,0,0,0)); L,Rr=ears(h)
    im.alpha_composite(L,(0,0)); im.alpha_composite(Rr,(94,0)); fill(im,10,0,84,h,BODY)
    for x in range(10,94): im.putpixel((x,0),tuple(LIP)[:3]+(255,)); im.putpixel((x,h-1),tuple(SHADE)[:3]+(255,))
    slots_fn(im,h)
    for i in range(3): im.putpixel((90,4+i*3),tuple(SHADE)[:3]+(255,))           # status LED sockets by the right ear
    fill(im,0,128,104,h,BODY)
    for y in range(128,128+h,8):
        for x in range(4,100,6): im.putpixel((x,y+4),tuple(SHADE)[:3]+(255,))     # rear vents
    fill(im,112,0,16,16,BODY); return im
def chassis_status(U,state,frames):
    st=Image.new('RGBA',(128,256*frames),(0,0,0,0))
    for f in range(frames):
        Y=256*f
        if state=='on': c,g=GRN,GRN_G
        elif state=='degraded': c,g=AMB,AMB_G
        else: c,g=(RED,RED_G) if f%2==0 else (None,None)
        if c: st.putpixel((90,Y+4),g+(255,)); st.putpixel((90,Y+7),c+(255,))
    return st
# ---- slot layouts (texels on the front) ----
def shelf_slots(im,h):                       # 6U: 10 vertical card slots (2 controller + 8 line) + fibre tray along the bottom
    for i in range(10): blank(im,12+i*8,2,7,36)
    inset(im,12,40,80,7)
    for x in range(14,90,4): im.putpixel((x,43),tuple(LIP)[:3]+(255,))
SHELF_SLOTS=[(12+i*8,2,7,36) for i in range(10)]
def switch_slots(im,h):                      # 10U: supervisors (2 half-width, top row), 8 line cards (1U rows), 4 PSUs (bottom row)
    blank(im,12,1,40,7); blank(im,52,1,40,7)
    for r in range(8): blank(im,12,9+r*8,80,7)
    for i in range(4): blank(im,12+i*20,73,20,6)
SW_SLOTS={'sup':[(12,1,40,7),(52,1,40,7)],'line':[(12,9+r*8,80,7) for r in range(8)],'psu':[(12+i*20,73,20,6) for i in range(4)]}
def blade_slots(im,h):                       # 10U: 16 half-height vertical blades (2 rows of 8)
    for r in range(2):
        for i in range(8): blank(im,12+i*10,2+r*39,9,37)
BLADE_SLOTS=[(12+i*10,2+r*39,9,37) for r in range(2) for i in range(8)]
# ---- card / blade / PSU faceplates (each its own small sheet, 8 texels per px) ----
def card(w,h,kind):
    im=Image.new('RGBA',(128,64),(0,0,0,0)); fill(im,0,0,w,h,TAPE.getpixel((70,10)))
    for x in range(w): im.putpixel((x,0),tuple(LIP)[:3]+(255,)); im.putpixel((x,h-1),tuple(SHADE)[:3]+(255,))
    for y in range(h): im.putpixel((0,y),tuple(LIP)[:3]+(255,)); im.putpixel((w-1,y),tuple(SHADE)[:3]+(255,))
    if h>w:                                  # vertical cards / blades: ejector at top, ports down the face, LED sockets
        for x in range(1,w-1): im.putpixel((x,1),EAR_L.getpixel((4,10))[:3]+(255,))
        if kind in ('transponder','mux','protection','interconnect'):
            for k in range(2 if kind!='mux' else 4): im.alpha_composite(SOCKET.crop((1,0,7,4)).resize((w-2,4)),(1,6+k*6))
        if kind in ('controller','mgmt'):
            for k in range(3): im.putpixel((w//2,6+k*3),tuple(SHADE)[:3]+(255,))      # alarm LED sockets (critical / major / minor)
            inset(im,1,16,w-2,6)
        if kind=='amplifier': inset(im,1,8,w-2,10)
        if kind in ('compute','memory','fabrication','storage'):
            for y in range(4,h-6,3): im.putpixel((2,y),tuple(SHADE)[:3]+(255,)); im.putpixel((w-3,y),tuple(SHADE)[:3]+(255,))
            if kind=='storage': inset(im,1,6,w-2,10); inset(im,1,18,w-2,10)
            if kind=='memory':
                for y in range(6,h-8,2): im.putpixel((w//2,y),tuple(INSET)[:3]+(255,))
        im.putpixel((w//2,h-3),tuple(SHADE)[:3]+(255,))                          # status LED socket
    else:                                    # horizontal cards: dense ports, cages, a display on supervisors
        if kind=='access':
            for k in range(12): im.alpha_composite(SOCKET.crop((1,0,7,4)).resize((5,4)),(3+k*6,2))
        if kind=='uplink':
            for k in range(4): K.recess(im,6+k*18,2,12,4)
        if kind=='routing': inset(im,4,2,30,4)
        if kind=='trunk':
            for k in range(8): K.recess(im,4+k*9,2,6,4)
        if kind=='supervisor': inset(im,3,2,16,4)
        if kind=='psu':
            for x in range(3,w-6,2): im.putpixel((x,3),tuple(SHADE)[:3]+(255,))
        im.putpixel((w-3,h//2),tuple(SHADE)[:3]+(255,))
    return im
def card_glow(w,h,kind,frames=4):
    st=Image.new('RGBA',(128,64*frames),(0,0,0,0))
    for f in range(frames):
        Y=64*f
        if h>w: st.putpixel((w//2,Y+h-3),GRN+(255,))
        else: st.putpixel((w-3,Y+h//2),GRN+(255,))
        if kind=='access':
            for k in range(12):
                if (k*3+f)%4: st.putpixel((3+k*6+2,Y+1),(GRN if (k+f)%5 else AMB)+(255,))
        if kind in ('controller','mgmt'): st.putpixel((w//2,Y+6),GRN+(255,))
        if kind=='supervisor':
            for x in range(4,18): st.putpixel((x,Y+3),(GRN_G if x==4 else GRN)+(255,))
        if kind=='mux':
            for k in range(4): st.putpixel((w//2,Y+5+k*6),WAVE[(k*2+f)%8]+(255,))
    return st
CARDS={'controller_card':(7,36,'controller'),'transponder_card':(7,36,'transponder'),'mux_card':(7,36,'mux'),'amplifier_card':(7,36,'amplifier'),'protection_card':(7,36,'protection'),
       'supervisor_card':(40,7,'supervisor'),'access_card':(80,7,'access'),'uplink_card':(80,7,'uplink'),'routing_card':(80,7,'routing'),'trunk_card':(80,7,'trunk'),'chassis_psu':(20,6,'psu'),
       'compute_blade':(9,37,'compute'),'memory_blade':(9,37,'memory'),'fabrication_blade':(9,37,'fabrication'),'storage_blade':(9,37,'storage'),
       'interconnect_module':(9,37,'interconnect'),'management_module':(9,37,'mgmt'),'blade_psu':(20,6,'psu')}
# ---- 1U Optical Multiplexer / Amplifier: the Monitoring Server's sheet with a new type tab, their own front details ----
def onu(accent,kind):
    im=MON.copy(); p=im.load()
    for y in range(8):
        for x in range(104):
            r,g,b,a=p[x,y]
            if a and g>r+30 and b>r+20: v=max(r,g,b); p[x,y]=accent[min(len(accent)-1,max(0,int((v-40)/30)))]+(a,)
    fill(im,19,1,28,6,BODY)
    if kind=='mux':
        for k in range(8): im.putpixel((21+k*3,4),tuple(SHADE)[:3]+(255,))       # 8 channel LED sockets
        im.alpha_composite(SOCKET,(50,1)); im.alpha_composite(SOCKET,(60,1))       # line in / out
    else:
        im.alpha_composite(SOCKET,(22,1)); im.alpha_composite(SOCKET,(38,1))       # input / output
        inset(im,50,2,12,4)                                                         # gain indicator window
    return im
def onu_glow(kind,state,frames=4):
    st=Image.new('RGBA',(128,128*frames),(0,0,0,0))
    for f in range(frames):
        Y=128*f
        if state=='on':
            if kind=='mux':
                for k in range(8):
                    if not (k==5 and f%2): st.putpixel((21+k*3,Y+4),WAVE[k]+(255,))
            else:
                for x in range(51,51+2+f*2): st.putpixel((x,Y+4),GRN+(255,))       # gain bar
            st.putpixel((92,Y+3),GRN_G+(255,)); st.putpixel((92,Y+4),GRN+(255,))
        else:
            if f%2==0: st.putpixel((92,Y+3),RED_G+(255,)); st.putpixel((92,Y+4),RED+(255,))
    return st
