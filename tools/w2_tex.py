# Wireless textures per docs/TEXTURE_STYLE.md: one 11-step steel ramp, flat fields, structural shading only (bevel frame
# with glint at 6-7 and inner lip, recesses with a step-0 shadow line and a step-6 lit lip, raised detail step 7 / ends
# step 8 with a step-0 shadow down-right), top-left light, two-tone glow. Casing faces start from the shipped
# network_casing.png. No speckle, no gradients.
from PIL import Image
H=lambda h: tuple(int(h[i:i+2],16) for i in (1,3,5))
S=[H(x) for x in ('#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB')]
LB=[H(x) for x in ('#17577B','#2678A0','#399CC6','#50C2EC','#70E8FF','#89F9FF','#A3FFFA')]     # light_blue dye ramp -3..+3
OR=[H(x) for x in ('#7B0F02','#A02C0A','#C65217','#EC7F27','#FF9B44','#FFAB5D','#FFBB77')]     # orange
YE=[H(x) for x in ('#7B360B','#A05D16','#C68B25','#ECC138','#FFD756','#FFDE70','#FFE489')]     # yellow (link / warning)
RD=[H(x) for x in ('#610E23','#7E1929','#9C262E','#BA3B37','#D85E55','#F6877A','#FFA698')]     # red (fault)
REPO='/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/block/'
def new(w=16,h=16): return Image.new('RGBA',(w,h),(0,0,0,0))
def px(im,x,y,c): im.putpixel((x,y),tuple(c)+(255,))
def fill(im,x0,y0,w,h,c):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w): px(im,x,y,c)
def frame(im,x0,y0,w,h):
    """Recessed bevel frame (guide 4): rail top/left 9, bottom/right 6; corners 10 / 7 / 7 / 5; glint (+1) at 6-7 along
       every edge (relative to the piece); inner lip 2 under the lit rails, 5 inside the shaded ones."""
    x1,y1=x0+w-1,y0+h-1
    for x in range(x0,x1+1): px(im,x,y0,S[9]); px(im,x,y1,S[6])
    for y in range(y0,y1+1): px(im,x0,y,S[9]); px(im,x1,y,S[6])
    for k in (6,7):
        if x0+k<x1: px(im,x0+k,y0,S[10]); px(im,x0+k,y1,S[7])
        if y0+k<y1: px(im,x0,y0+k,S[10]); px(im,x1,y0+k,S[7])
    px(im,x0,y0,S[10]); px(im,x1,y0,S[7]); px(im,x0,y1,S[7]); px(im,x1,y1,S[5])
    for x in range(x0+1,x1): px(im,x,y0+1,S[2]); px(im,x,y1-1,S[5])
    for y in range(y0+1,y1): px(im,x0+1,y,S[2]); px(im,x1-1,y,S[5])
def recess(im,x0,y0,w,h,back=2):
    """Pocket: back wall in two steps (darker under the overhang, one lighter toward the lit lip), step-0 shadow line
       under the overhang (top) and down the left, lit lower lip step 6."""
    fill(im,x0,y0,w,h,S[back])
    for y in range(y0+(h+1)//2,y0+h):
        for x in range(x0+(w)//3,x0+w): px(im,x,y,S[min(10,back+1)])
    for x in range(x0,x0+w): px(im,x,y0,S[0]); px(im,x,y0+h-1,S[6])
    for y in range(y0,y0+h-1): px(im,x0,y,S[0])
def raised(im,pts):
    """Raised detail: runs step 7, ends step 8 (first/last of each run), cast shadow step 0 down-right."""
    pts=list(pts); P=set(pts)
    for (x,y) in pts:
        if (x+1,y+1) not in P and 0<=x+1<im.width and 0<=y+1<im.height: px(im,x+1,y+1,S[0])
    for i,(x,y) in enumerate(pts): px(im,x,y,S[8] if i in (0,len(pts)-1) else S[7])
def field(im,x0,y0,w,h,base,lo=-1,hi=1):
    """Stepped falloff across a field, the way vanilla shades a face: the top-left third one step lighter, the
       bottom-right third one step darker, whole ramp steps in bands several pixels wide (no gradient, no specks)."""
    span=max(1,w+h-2)
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            t=((x-x0)+(y-y0))/span
            k=hi if t<0.22 else 0 if t<0.48 else lo if t<0.76 else lo-1
            px(im,x,y,S[max(0,min(10,base+k))])
def casing():
    return Image.open(REPO+'network_casing.png').convert('RGBA').copy()
def glow_px(ramp,glint=False): return ramp[6] if glint else ramp[3]          # base on runs, glint at ends
# ---------------- Access Point ----------------
def ap_top():
    """The casing's top face with a recessed mounting ring where the puck sits (the puck is modelled)."""
    im=casing()
    recess(im,3,3,10,10,back=1)
    return im
RING=[(5,4),(6,4),(7,4),(8,4),(9,4),(10,4),(11,5),(11,6),(11,7),(11,8),(11,9),(11,10),(10,11),(9,11),(8,11),(7,11),(6,11),(5,11),(4,10),(4,9),(4,8),(4,7),(4,6),(4,5)]
def puck_top():
    """The puck's top (steel 8-10, the guide's 'white'): flat field step 9, lit rim top-left 10, shaded rim 7,
       a groove ring for the LED (step 6 back, step 5 inner edge), a raised centre badge."""
    im=new(); field(im,0,0,16,16,9,-1,1)
    for x in range(1,15): px(im,x,1,S[10])                                   # lit rim
    for y in range(1,15): px(im,1,y,S[10])
    for x in range(1,15): px(im,x,14,S[7])
    for y in range(1,15): px(im,14,y,S[7])
    for x in range(16): px(im,x,0,S[9]); px(im,x,15,S[6])
    for y in range(16): px(im,0,y,S[9]); px(im,15,y,S[6])
    for (x,y) in RING: px(im,x,y,S[6])
    for (x,y) in RING:
        if y>=11 or x>=11: px(im,x,y,S[5])                                    # groove's shaded side
    raised(im,[(7,7),(8,7)]); px(im,7,8,S[8]); px(im,8,8,S[7])
    return im
def puck_side():
    im=new()
    for y in range(16):
        for x in range(16): px(im,x,y,S[9] if x<5 else S[8] if x<11 else S[7])   # stepped across the face
    for x in range(16): px(im,x,0,S[10]); px(im,x,2,S[6])
    for x in range(16): px(im,x,8,S[6]); px(im,x,12,S[6])                     # band lines
    return im
def ring_glow(state):
    """LED ring: online light blue, linking (no controller) blinking yellow, fault blinking red. Two tones: base on the
       ring's runs, glint at its four corners. Off = no overlay."""
    ramp={'online':LB,'linking':YE,'fault':RD}[state]; blink=state!='online'
    im=new(16,32 if blink else 16)
    corners={(5,4),(10,4),(11,5),(11,10),(10,11),(5,11),(4,10),(4,5)}
    for (x,y) in RING: px(im,x,y,glow_px(ramp,(x,y) in corners))
    return im,blink
# ---------------- Wireless Bridge ----------------
def bridge_side():
    """Casing side with a raised vertical rib pair (antenna feed) and a status window."""
    im=casing()
    recess(im,6,4,4,3,back=1)                                                # status window
    raised(im,[(7,8),(7,9),(7,10),(7,11)]); raised(im,[(9,8),(9,10),(9,11)] if False else [(9,8),(9,9),(9,10),(9,11)])
    return im
def bridge_top():
    im=casing(); recess(im,5,5,6,6,back=1); return im                       # mast socket
def mast():
    """Antenna mast (modelled): steel 7 runs, 8 ends; collar bands 9; tip cap 10."""
    im=new()
    for y in range(16):
        for x in range(16): px(im,x,y,S[9] if x%4==0 else S[8] if x%4==1 else S[7] if x%4==2 else S[6])   # each 4-texel face: lit -> shaded
    for y in (4,9):
        for x in range(16): px(im,x,y,S[10] if x%4<2 else S[8])               # collar bands
    for x in range(16): px(im,x,0,S[10]); px(im,x,15,S[5])
    return im
def window_glow(state):
    ramp={'online':LB,'linking':YE,'fault':RD,'active':LB}[state]; blink=state in ('linking','fault')
    im=new(16,32 if blink else 16)
    for x in range(7,10): px(im,x,5,glow_px(ramp,x==7))
    if state=='active':
        im2=new(16,32); im2.alpha_composite(im,(0,0)); 
        for x in range(7,10): im2.putpixel((x,16+5),tuple(glow_px(ramp,x==9))+(255,))
        return im2,True
    return im,blink
def tip_glow(state):
    ramp={'online':LB,'linking':YE,'fault':RD,'active':LB}[state]; im=new(); fill(im,0,0,16,16,glow_px(ramp)); fill(im,0,0,6,6,glow_px(ramp,True)); return im
# ---------------- Wireless Ingress / Egress (QIO-style panels) ----------------
def port_back(kind):
    """The face a player sees: framed panel, recessed mouth with slat rails (raised, step 7/8) like the cabled port, an
       accent inlay (cable dye ramp: ingress light blue, egress orange) along the top, the LED window bottom right."""
    A=LB if kind=='ingress' else OR
    im=new(); field(im,0,0,16,16,5); frame(im,0,0,16,16)
    recess(im,3,4,10,8,back=2)
    for x in range(4,12): px(im,x,10,S[1])                                   # deeper lower floor of the mouth
    for y in (6,9): raised(im,[(x,y) for x in range(4,12)])
    for x in range(4,12): px(im,x,2,A[2] if x in (4,11) else A[3])          # accent inlay
    px(im,4,2,A[4])
    fill(im,11,13,2,1,S[1])                                                  # LED window
    return im
def port_front():
    """Contact face against the inventory: framed, a recessed transfer slot."""
    im=new(); field(im,0,0,16,16,5); frame(im,0,0,16,16); recess(im,4,4,8,8,back=1)
    for x in range(5,11,2): px(im,x,7,S[3]); px(im,x,9,S[3])
    for x in range(1,15): px(im,x,13,S[4]); px(im,x,14,S[7]) if False else None
    raised(im,[(2,13),(3,13)]); raised(im,[(12,13),(13,13)])                    # screw heads
    raised(im,[(2,2),(3,2)]); raised(im,[(12,2),(13,2)])
    return im
def port_side(kind):
    A=LB if kind=='ingress' else OR
    im=new(); field(im,0,0,16,16,6)
    for x in range(16): px(im,x,0,S[9]); px(im,x,1,S[2]); px(im,x,15,S[5])
    for x in range(2,14,3): px(im,x,2,A[3]); px(im,x+1,3,S[0]) if x+1<16 else None
    for x in range(16): px(im,x,8,S[4]); px(im,x,9,S[7])                      # panel seam (lit lower edge)
    for y in range(16): px(im,8,y,S[4]) if y not in (0,1,2,3) else None                                  # accent ticks (like the cabled port sides)
    return im
def led_glow(state):
    ramp={'online':LB,'linking':YE,'fault':RD,'idle':LB}[state]; blink=state in ('linking','fault')
    im=new(16,32 if blink else 16); px(im,11,13,glow_px(ramp,True)); px(im,12,13,glow_px(ramp)); return im,blink
# ---------------- items (outline in the material's darkest step; 4-7 tones; top-left light) ----------------
def outline(im,col):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): px(im,x,y,col)
    return im
def icon_ap():
    im=new()
    for y in range(9,14):
        for x in range(2,14): px(im,x,y,S[6] if y>11 else S[7] if x>8 else S[8])          # casing
    for x in range(2,14): px(im,x,9,S[9])
    for y in range(5,9):
        for x in range(4,12): px(im,x,y,S[9] if y<7 else S[8])                           # puck
    for x in range(5,11): px(im,x,5,S[10])
    for x in range(5,11): px(im,x,8,LB[3])
    px(im,5,8,LB[6]); px(im,10,8,LB[6])
    return outline(im,S[1])
def icon_bridge():
    im=new()
    for y in range(9,15):
        for x in range(3,13): px(im,x,y,S[6] if y>12 else S[7] if x>8 else S[8])
    for x in range(3,13): px(im,x,9,S[9])
    for y in range(2,9): px(im,7,y,S[8]); px(im,8,y,S[6])
    px(im,7,1,LB[6]); px(im,8,1,LB[3]); px(im,7,4,S[10]); px(im,7,6,S[10])
    for x in range(6,9): px(im,x,11,LB[3])
    return outline(im,S[1])
def icon_port(kind):
    A=LB if kind=='ingress' else OR; im=new()
    for y in range(3,13):
        for x in range(3,13): px(im,x,y,S[5] if y>10 else S[6] if x>9 else S[7])
    for x in range(3,13): px(im,x,3,S[9])
    for y in range(3,13): px(im,3,y,S[9])
    for x in range(5,11): px(im,x,5,A[3])
    for y in (7,9):
        for x in range(5,11): px(im,x,y,S[8] if x==5 else S[4])
    px(im,10,11,LB[6] if kind=='ingress' else OR[5])
    px(im,12,1,S[8]); px(im,12,2,S[7])                                            # antenna nub
    return outline(im,S[1])
def icon_radio():
    G=[H(x) for x in ('#1F450C','#355A14','#4E701F','#6A852B','#819A41')]           # green dye ramp (PCB)
    im=new()
    for y in range(4,13):
        for x in range(2,14):
            t=(x-2)+(y-4); px(im,x,y,G[4] if t<5 else G[3] if t<13 else G[2])
    for x in range(2,14): px(im,x,4,G[4])
    for y in range(5,11):
        for x in range(3,9): px(im,x,y,S[8] if y==5 or x==3 else S[7] if y<10 else S[5])   # shielded can
    for (x,y) in ((10,5),(11,5),(12,5),(12,6),(12,7),(11,7),(10,7),(10,8),(10,9),(11,9),(12,9)): px(im,x,y,YE[4] if (x,y)==(10,5) else YE[3])   # antenna trace
    for x in range(2,14,2): px(im,x,12,YE[2])
    return outline(im,G[0])
