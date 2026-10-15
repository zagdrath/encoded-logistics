# 1.3.0 rack gear pass: the backs of the new rack devices (Chassis Switch, Blade Enclosure, Optical Transport Shelf, rack
# Optical Multiplexer / Amplifier, rack Directory Server), their side patches, and distinct inventory icons.
# Works in place on the shipped sheets (the original generators' source trees aren't in the repo): every region it paints
# is fully overwritten, so it can be re-run. Parts are cut from shipped rear art (the Network Controller's PSU and fan,
# the Tape Library's RJ45, the 1U units' IEC inlet) so the new backs match the old ones.
# Run from the project root: python tools/rack_gear_130.py
import os,json
from PIL import Image
A='src/main/resources/assets/encodedlogistics/'
T=A+'textures/block/rack_device/'; I=A+'textures/item/'; M=A+'models/block/rack_device/'
def load(n): return Image.open(T+n+'.png').convert('RGBA')
NC=load('network_controller_4u'); TAPE=load('tape_library_6u')
# ---- palette (sampled from the shipped rears) ----
EDGE_L=(121,128,138); EDGE_D=(43,47,54); DARK=(31,34,40); HI=(163,169,177); MHI=(141,148,157); MFACE=(102,109,119); SH=(55,60,68)
BG_TOP=(79,85,94); BG_BOT=(59,64,72)
CU,CU_D=(241,159,98),(208,122,64); GRN=(60,224,90); GRN_D=(46,138,76); AMB=(240,138,42)
YEL,YEL_D=(240,196,52),(184,140,30)            # yellow single-mode fibre jacket
BLU,BLU_D=(78,128,210),(44,82,150)             # LC/SC coupler (UPC blue)
LAB,LAB_D=(200,204,208),(120,126,134)          # white label plate, barcode ticks
WAVE=[(176,46,38),(236,127,39),(254,216,61),(135,211,46),(80,194,236),(60,68,170),(137,50,184),(94,156,46)]
# blanking plate tones (as tools/opt.py takes them from the Tape Library front)
BODY=TAPE.getpixel((92,40))[:3]; LIP=TAPE.getpixel((40,4))[:3]; BSH=TAPE.getpixel((40,46))[:3]; SCREW=TAPE.getpixel((4,4))[:3]

def P(im,x,y,c): im.putpixel((x,y),tuple(c)[:3]+(255,))
def fill(im,x,y,w,h,c):
    for j in range(h):
        for i in range(w): P(im,x+i,y+j,c)
def mix(a,b,t): return tuple(round(a[k]+(b[k]-a[k])*t) for k in range(3))
def masked(src,box,bgx):
    """A sprite cut from a shipped rear: keeps the pixels that differ from that row's panel background (sampled at bgx)."""
    x0,y0,x1,y1=box; sp=Image.new('RGBA',(x1-x0,y1-y0),(0,0,0,0))
    for y in range(y0,y1):
        bg=src.getpixel((bgx,y))[:3]
        for x in range(x0,x1):
            c=src.getpixel((x,y))
            if sum(abs(c[k]-bg[k]) for k in range(3))>9: sp.putpixel((x-x0,y-y0),c)
    return sp
PSU=NC.crop((2,33,25,47))                      # 23 x 14 hot-swap PSU: fan, IEC inlet, pull handle
FAN=masked(NC,(89,34,103,47),70)               # 14 x 13 big fan grille
RJ=masked(TAPE,(83,104,91,111),70)             # RJ45 with its orange link LEDs
IEC=load('monitoring_server').crop((6,33,14,39))   # 1U IEC inlet (8 x 6)
SFP=NC.crop((52,35,60,39))                     # SFP cage 8 x 4
FAN_S=masked(load('monitoring_server'),(66,32,77,40),50)   # 1U fan 11 x 8

def panel(im,y0,h):
    """A rear face: lit top and left edge, dark right and bottom, a smooth top-to-bottom shade."""
    for y in range(h):
        c=mix(BG_TOP,BG_BOT,y/max(1,h-1))
        for x in range(104): P(im,x,y0+y,c)
    for x in range(104): P(im,x,y0,EDGE_L); P(im,x,y0+h-1,EDGE_D)
    for y in range(h): P(im,0,y0+y,EDGE_L); P(im,103,y0+y,EDGE_D)
def module(im,x,y,w,h,face=None):
    """A hot-swap module's frame: lit top / left, shaded bottom / right, a recessed seam around it."""
    face=face or MFACE
    for j in range(h):
        for i in range(w): P(im,x+i,y+j,mix(face,mix(face,SH,0.5),j/max(1,h-1)))
    for i in range(w): P(im,x+i,y,MHI); P(im,x+i,y+h-1,SH)
    for j in range(h): P(im,x,y+j,MHI); P(im,x+w-1,y+j,SH)
    for i in range(w+1): P(im,x+i,y+h,EDGE_D)
    for j in range(h+1): P(im,x+w,y+j,EDGE_D)
def handle_v(im,x,y,h):
    for j in range(h): P(im,x,y+j,HI); P(im,x+1,y+j,SH)
def handle_h(im,x,y,w):
    for i in range(w): P(im,x+i,y,HI); P(im,x+i,y+1,SH)
def screw(im,x,y): P(im,x,y,SCREW)
def led(im,x,y,c): P(im,x,y,c)
def fan_module(im,x,y,w,h,ledc=GRN):
    """A fan module: frame, the shipped fan grille centred, a pull handle along the top, its status LED."""
    module(im,x,y,w,h,(84,90,100))
    fx=x+(w-FAN.width)//2; fy=y+h-FAN.height-1
    fill(im,fx+1,fy+1,FAN.width-2,FAN.height-2,(66,72,81))
    im.alpha_composite(FAN,(fx,fy))
    if fy-y>=3: handle_h(im,x+2,y+1,w-6); led(im,x+w-3,y+1,ledc)
    else: led(im,x+w-2,y+1,ledc)
def label(im,x,y,w,h=3):
    fill(im,x,y,w,h,LAB)
    for i in range(1,w-1,2): P(im,x+i,y+h-1,LAB_D)
    P(im,x+1,y+1,LAB_D); P(im,x+2,y+1,LAB_D)
def ground(im,x,y):
    """Grounding lug: two copper studs on a copper tongue, the earth wire running off it."""
    fill(im,x,y,5,3,CU_D); P(im,x+1,y+1,CU); P(im,x+3,y+1,CU)
    for j in range(3,6): P(im,x+2,y+j,(46,138,76) if j%2 else (230,210,60))
def lc_duplex(im,x,y,cap=None):
    """An LC duplex coupler (blue), or a dust-capped one when cap is a colour."""
    fill(im,x,y,3,2,BLU if cap is None else cap); P(im,x+1,y,BLU_D if cap is None else DARK); P(im,x+1,y+1,BLU_D if cap is None else DARK)
    P(im,x,y+2,SH); P(im,x+2,y+2,SH)
def fibre(im,pts,shade=True):
    """A yellow patch lead along a pixel polyline (light on top, a shade pixel under horizontal runs)."""
    for (x0,y0),(x1,y1) in zip(pts,pts[1:]):
        n=max(abs(x1-x0),abs(y1-y0))
        for k in range(n+1):
            x=x0+round((x1-x0)*k/max(1,n)); y=y0+round((y1-y0)*k/max(1,n))
            P(im,x,y,YEL)
            if shade and y1==y0 and im.getpixel((x,y+1))[:3]!=YEL: P(im,x,y+1,YEL_D)
def blank(im,x0,y0,w,h):
    """An empty bay's blanking plate, as the front's (tools/opt.py)."""
    fill(im,x0,y0,w,h,BODY)
    for x in range(w): P(im,x0+x,y0,LIP); P(im,x0+x,y0+h-1,BSH)
    for y in range(h): P(im,x0,y0+y,LIP); P(im,x0+w-1,y0+y,BSH)
    if w>3 and h>3: screw(im,x0+1,y0+1); screw(im,x0+w-2,y0+h-2)
def dc_feed(im,x,y,w,h,tag):
    """-48 V DC power entry: a module with a terminal block of copper screws, a breaker rocker and its LED."""
    module(im,x,y,w,h)
    fill(im,x+2,y+2,w-9,4,DARK)
    for i in range(x+3,x+w-8,2): P(im,i,y+3,CU); P(im,i,y+4,CU_D)
    fill(im,x+w-6,y+2,3,5,DARK); P(im,x+w-5,y+3,(200,60,50)); P(im,x+w-5,y+4,(150,40,36))   # breaker
    label(im,x+2,y+h-4,6,2); led(im,x+w-2,y+2,GRN)
    for j in range(2): P(im,x+9+j,y+h-3,tag)
def side_patch(im):
    """The device's side / top / bottom patch (16 x 16 at (112,0)): the Tape Library's panel."""
    im.paste(TAPE.crop((112,0,128,16)),(112,0))

# ================= Chassis Switch (10U, back at 128, 104 x 80) =================
def chassis_switch():
    im=load('chassis_switch'); y0=128; h=80
    for y in range(y0,256):
        for x in range(128): im.putpixel((x,y),(0,0,0,0))
    panel(im,y0,h)
    # two fan trays, side by side, three columns x four rows of fans each, with a pull handle down their outer edge
    for t,tx in enumerate((3,47)):
        module(im,tx,y0+3,42,58,(84,90,100))
        handle_h(im,tx+4,y0+5,26)
        for r in range(4):
            for c in range(3):
                fx=tx+1+c*13; fy=y0+8+r*13
                fill(im,fx+1,fy+1,FAN.width-2,FAN.height-2,(66,72,81)); im.alpha_composite(FAN,(fx,fy))
        led(im,tx+36,y0+5,GRN); led(im,tx+38,y0+5,GRN_D)
        screw(im,tx+1,y0+4); screw(im,tx+40,y0+4)
    # right strip: system label, ESD jack, alarm port, grounding lug
    label(im,91,y0+4,10,5)
    fill(im,93,y0+12,5,4,DARK); P(im,95,y0+13,SH); P(im,95,y0+14,CU_D)                     # ESD jack
    fill(im,91,y0+19,9,4,SH); fill(im,92,y0+20,7,2,DARK)                                    # alarm DB connector
    for i in range(93,98,2): P(im,i,y0+20,MFACE)
    im.alpha_composite(RJ,(91,y0+25)); im.alpha_composite(RJ,(91,y0+33))                     # two RJ45 (out-of-band, timing)
    ground(im,93,y0+44)
    for j in range(52,61,3): handle_h(im,90,y0+j,11)                                       # cable lacing bars
    # bottom row: four power inlets, under the front's four PSUs
    for i in range(4):
        x=3+i*21; module(im,x,y0+64,20,13)
        im.alpha_composite(IEC,(x+3,y0+67)); fill(im,x+13,y0+67,3,5,DARK); P(im,x+14,y0+68,(200,60,50))
        P(im,x+12,y0+66,EDGE_D); led(im,x+17,y0+66,GRN)
        fill(im,x+3,y0+74,9,1,(26,28,33))                                                   # power cord clamp
    label(im,89,y0+66,12,3); fill(im,89,y0+71,12,5,SH); fill(im,90,y0+72,10,3,DARK)          # serial label, console port
    side_patch(im); im.save(T+'chassis_switch.png')

# ================= Blade Enclosure (10U, back at 128) =================
def blade_enclosure():
    im=load('blade_enclosure'); y0=128; h=80
    for y in range(y0,256):
        for x in range(128): im.putpixel((x,y),(0,0,0,0))
    panel(im,y0,h)
    # the rear bays (ChassisLayout.BLADE: R1 R2 interconnects, M1 M2 management, P1-P6 power) as blanking plates -
    # parts draw over them
    for bx in (12,22,34,44): blank(im,bx,y0+2,9,37)
    for i in range(6): blank(im,12+20*(i%3),y0+(64 if i<3 else 72),20,6)
    # bay guides and labels
    for bx in (11,21,31,33,43,53):
        for j in range(2,39): P(im,bx,y0+j,mix(BG_TOP,EDGE_D,0.5))
    for bx in (13,23,35,45): fill(im,bx+1,y0+40,6,1,LAB)
    # under the modules: enclosure link ports, onboard admin serial, a fan row
    for i,bx in enumerate((12,20)): im.alpha_composite(RJ,(bx,y0+43))
    fill(im,29,y0+44,8,4,SH); fill(im,30,y0+45,6,2,DARK)
    for i in range(31,36,2): P(im,i,y0+45,MFACE)
    label(im,40,y0+43,14,4)
    for i,fx in enumerate((3,20,37)): fan_module(im,fx,y0+50+0,16,13 if i else 13)
    # ten fans: two columns x four, and two more beside the power row
    for c in range(3):
        for r in range(4): fan_module(im,57+c*15,y0+2+r*15,15,14,GRN if (c,r)!=(1,2) else GRN)
    # AC inlets for the six supplies, and the strain-relief bar
    for i in range(3):
        x=74+i*9; fill(im,x,y0+64,8,8,SH); im.alpha_composite(IEC.crop((0,0,8,6)),(x,y0+65))
    handle_h(im,74,y0+74,27)
    # left strip: ground lug, label
    ground(im,3,y0+3); label(im,2,y0+12,8,3)
    for j in range(18,38,4): P(im,5,y0+j,SH); P(im,6,y0+j,SH)
    for i in range(3,10,2): P(im,i,y0+67,SH)
    side_patch(im); im.save(T+'blade_enclosure.png')

# ================= Optical Transport Shelf (6U, back at 64 - RackDeviceType.backOrigin) =================
def transport_shelf():
    im=load('optical_transport_shelf'); y0=64; h=48
    for y in range(64,256):
        for x in range(128): im.putpixel((x,y),(0,0,0,0))
    panel(im,y0,h)
    dc_feed(im,3,y0+3,22,12,BLU); dc_feed(im,3,y0+17,22,12,(200,60,50))                     # A and B feeds
    # fan tray: one long module, two rows of four fans
    module(im,28,y0+3,58,27,(84,90,100))
    for r in range(2):
        for c in range(4):
            fx=31+c*13; fy=y0+4+r*13
            fill(im,fx+1,fy+1,FAN.width-2,FAN.height-2,(66,72,81)); im.alpha_composite(FAN,(fx,fy))
    handle_v(im,84,y0+6,20); led(im,30,y0+4,GRN)
    # alarm / timing / management: RJ45s, BITS timing BNC, the alarm contact block
    im.alpha_composite(RJ,(89,y0+3)); im.alpha_composite(RJ,(95,y0+3))
    fill(im,89,y0+12,5,5,SH); P(im,91,y0+14,HI); P(im,90,y0+13,DARK); P(im,92,y0+15,DARK)        # BNC
    fill(im,95,y0+12,6,5,SH); P(im,96,y0+13,HI); P(im,99,y0+15,HI)
    fill(im,89,y0+19,12,4,DARK)
    for i in range(90,100,2): P(im,i,y0+20,CU); P(im,i,y0+21,CU_D)                            # alarm terminal strip
    label(im,89,y0+25,12,4)
    # fibre management tray across the bottom: a row of LC couplers with their yellow patch leads, looped into a
    # trough and out the right-hand side
    fill(im,2,y0+32,100,14,(48,52,60))
    for x in range(2,102): P(im,x,y0+32,EDGE_D); P(im,x,y0+45,mix(BG_BOT,EDGE_D,0.5))
    for i in range(12):
        x=5+i*7
        P(im,x+1,y0+33,WAVE[i%8])                                                            # wavelength tag
        lc_duplex(im,x,y0+34)
    for x in range(5,102): P(im,x,y0+42,YEL); P(im,x,y0+43,YEL_D)                          # the bundle
    for x in range(12,102,5): P(im,x,y0+42,(220,176,40))
    for i in range(12):
        x=5+i*7; d=(0,1,2,1)[i%4]
        fibre(im,[(x+i%2*2,y0+37),(x+i%2*2,y0+38+d),(x+i%2*2+2,y0+40+d),(x+i%2*2+2,y0+41)],False)
    for x in (24,52,80,96):                                                                 # lacing clips over the bundle
        fill(im,x,y0+41,2,4,SH); P(im,x,y0+41,HI)
    side_patch(im); im.save(T+'optical_transport_shelf.png')
    # the model's back face: 6U sheets keep it at 64 (RackDeviceType.backOrigin), unmirrored like the chassis
    m=json.load(open(M+'optical_transport_shelf.json'))
    m['elements'][0]['faces']['south']['uv']=[0,4,13,7.0]; json.dump(m,open(M+'optical_transport_shelf.json','w'),indent=1)

def unmirror(name):
    """A chassis's back face drawn as seen from behind (the bays' x run from the left as seen: ChassisRender,
    ChassisPanel), not mirrored."""
    m=json.load(open(M+name+'.json')); f=m['elements'][0]['faces']['south']
    u=f['uv']; f['uv']=[min(u[0],u[2]),u[1],max(u[0],u[2]),u[3]]; json.dump(m,open(M+name+'.json','w'),indent=1)

# ================= 1U rack units (back at 32, 104 x 8) =================
def one_u_base(name):
    im=load(name)
    for y in range(32,40):
        for x in range(104): im.putpixel((x,y),(0,0,0,0))
    panel(im,32,8); return im
def mux_rear():
    im=one_u_base('optical_multiplexer'); y=32
    im.alpha_composite(IEC,(4,y+1)); ground(im,14,y+1)
    # eight channel ports (LC duplex, a wavelength tag each), their leads looping down and off to the right
    for k in range(8):
        x=24+k*6; P(im,x+1,y+1,WAVE[k]); lc_duplex(im,x,y+2)
        fibre(im,[(x+1,y+5),(x+1,y+6)],False)
    fibre(im,[(25,y+6),(84,y+6)],False)
    # line / monitor ports (dust caps)
    lc_duplex(im,74,y+2,(220,220,220)); P(im,75,y+1,LAB); lc_duplex(im,79,y+2,(220,220,220)); P(im,80,y+1,LAB)
    im.alpha_composite(FAN_S,(88,y)); label(im,85,y+1,2,2)
    im.save(T+'optical_multiplexer.png')
def amp_rear():
    im=one_u_base('optical_amplifier'); y=32
    im.alpha_composite(IEC,(4,y+1)); im.alpha_composite(IEC,(14,y+1))                         # dual supplies
    ground(im,24,y+1)
    # signal in / out and the monitor tap, each with its yellow lead
    for k,(x,cap) in enumerate(((36,None),(44,None),(54,(220,220,220)))):
        lc_duplex(im,x,y+2,cap); P(im,x+1,y+1,(GRN,AMB,LAB)[k])
    fibre(im,[(37,y+5),(37,y+6),(60,y+6)],False); fibre(im,[(45,y+5),(45,y+6)],False)
    im.alpha_composite(RJ.crop((0,0,8,7)),(62,y+1)); label(im,72,y+2,8,3)                      # management port
    im.alpha_composite(FAN_S,(80,y)); im.alpha_composite(FAN_S,(91,y))
    im.save(T+'optical_amplifier.png')
def directory_rear():
    im=one_u_base('directory_server'); y=32
    for x in (3,15):                                                                         # two small hot-swap PSUs
        module(im,x,y+1,11,6); im.alpha_composite(IEC.crop((0,0,6,5)),(x+1,y+2)); P(im,x+9,y+2,GRN); P(im,x+8,y+5,HI)
    for k in range(4): im.alpha_composite(RJ.crop((0,0,8,7)),(30+k*7,y+1))                  # four NICs
    fill(im,60,y+2,7,3,BLU_D); fill(im,61,y+3,5,1,DARK)                                       # serial / VGA
    for x in (69,73): fill(im,x,y+2,3,4,DARK); P(im,x,y+2,HI); P(im,x+1,y+3,(60,100,200))  # USB
    label(im,77,y+2,5,3)
    im.alpha_composite(FAN_S,(83,y)); im.alpha_composite(FAN_S,(93,y))
    im.save(T+'directory_server.png')

# ================= inventory icons (16 x 16, the rack-device icon family) =================
OUT=(31,34,40); EAR_L=(121,128,138); EAR_R=(102,109,119)
TOP1=[(141,148,157),(119,125,135)]; TOP2=[(107,114,123),(85,91,101)]
FR_T=(85,91,101); FR_B=(55,60,68)
def icon_frame(top,bottom,top_rows=2):
    """The family's 3/4 unit: an outlined top face (top_rows rows) above a front face from top+top_rows to bottom, ears."""
    im=Image.new('RGBA',(16,16),(0,0,0,0)); f0=top+1+top_rows
    for x in range(2,14): P(im,x,top,OUT)
    for r in range(top_rows):
        a,b=[mix(TOP1[0],TOP2[0],r/max(1,top_rows-1)),mix(TOP1[1],TOP2[1],r/max(1,top_rows-1))]
        P(im,1,top+1+r,OUT); P(im,14,top+1+r,OUT)
        for x in range(2,14): P(im,x,top+1+r,mix(a,b,(x-2)/11))
    for y in range(f0,bottom+1):
        c=mix(FR_T,FR_B,(y-f0)/max(1,bottom-f0))
        P(im,0,y,OUT); P(im,1,y,EAR_L); P(im,14,y,EAR_R); P(im,15,y,OUT)
        for x in range(2,14): P(im,x,y,c)
    for x in range(1,15): P(im,x,bottom+1,OUT)
    return im,f0
def row_c(im,y): return im.getpixel((2,y))[:3]
def icon_chassis_switch():
    im,f=icon_frame(0,13)                                     # front rows 3..13
    # supervisors (top row): two half-width cards, each a green status display and a handle
    for x0 in (3,8):
        P(im,x0,f+1,EAR_L); P(im,x0+1,f+1,(92,208,138)); P(im,x0+2,f+1,(131,241,167)); P(im,x0+3,f+1,OUT)
    # line cards: three full-width cards of ports, each port's link LED lit or dark
    pat=[(GRN,OUT,GRN,OUT,AMB,OUT,GRN,OUT,GRN),(GRN,OUT,GRN,OUT,GRN,OUT,OUT,OUT,GRN),(AMB,OUT,GRN,OUT,GRN,OUT,GRN,OUT,GRN)]
    for k,y in enumerate((f+3,f+5,f+7)):
        P(im,3,y,EAR_L)
        for i,c in enumerate(pat[k]): P(im,4+i,y,c if c!=OUT else (43,47,54))
        for x in range(3,13): P(im,x,y+1,mix(row_c(im,y+1),OUT,0.45))
    # power supplies along the bottom: four, each a handle and its LED
    for x0 in (3,6,9):
        P(im,x0,f+9,(141,148,157)); P(im,x0+1,f+9,GRN_D)
    P(im,12,f+9,(141,148,157))
    return im
def icon_blade_enclosure():
    im,f=icon_frame(0,13)
    # two rows of five vertical blades: a lit handle at the top, the blade face, a status LED at the foot
    for top in (f+1,f+6):
        for x in range(3,13):
            for y in range(top,top+4): P(im,x,y,(43,47,54))
        for k,x in enumerate((3,5,7,9,11)):
            P(im,x,top,(163,169,177))
            for y in range(top+1,top+3): P(im,x,y,(110,117,127)); P(im,x+1,y,(72,78,87))
            P(im,x+1,top,(110,117,127))
            P(im,x,top+3,(BLU if (k,top)==(2,f+1) else GRN)); P(im,x+1,top+3,(72,78,87))
    for x in range(3,13): P(im,x,f+5,mix(row_c(im,f+5),OUT,0.3))
    return im
def icon_transport_shelf():
    im,f=icon_frame(2,13)                                     # 6U: a shorter box than the 10U chassis
    # five transponder cards: a wavelength LED at the top, the faceplate, an LC port with its yellow lead
    cols=[WAVE[0],WAVE[2],WAVE[3],WAVE[4],WAVE[6]]
    for x in range(3,13):
        for y in range(f,f+5): P(im,x,y,(43,47,54))
    for k,x in enumerate((3,5,7,9,11)):
        P(im,x,f,(163,169,177)); P(im,x+1,f,(110,117,127))
        P(im,x,f+1,cols[k]); P(im,x+1,f+1,(110,117,127))
        P(im,x,f+2,(110,117,127)); P(im,x+1,f+2,(110,117,127))
        P(im,x,f+3,BLU); P(im,x+1,f+3,(110,117,127))
        P(im,x,f+4,YEL)
    # the leads drop into the fibre tray and run out to the right
    for x in range(3,13): P(im,x,f+5,(43,47,54)); P(im,x,f+6,YEL)
    for k,x in enumerate((3,5,7,9,11)): P(im,x,f+5,YEL)
    P(im,13,f+6,YEL_D)
    for x in range(3,13): P(im,x,f+7,mix(row_c(im,f+7),OUT,0.3))
    return im
def icon_1u(tab,detail):
    im,f=icon_frame(4,11,top_rows=4)                          # front rows 9..11, as the shipped 1U icons
    for y in range(f,f+3): P(im,3,y,tab if y>f else mix(tab,(255,255,255),0.5))
    detail(im,f); return im
def d_mux(im,f):
    for k in range(6): P(im,5+k,f+1,WAVE[(0,1,2,3,4,6)[k]])  # one LED per wavelength
    P(im,12,f+1,(94,255,114))
def d_amp(im,f):
    P(im,5,f+1,BLU); P(im,6,f+1,(43,47,54)); P(im,7,f+1,BLU)                     # in / out ports
    P(im,5,f+2,YEL); P(im,7,f+2,YEL)
    for k,x in enumerate((9,10,11)): P(im,x,f+1,(GRN_D,GRN,(181,255,176))[k])   # gain bar
    P(im,9,f+2,(43,47,54)); P(im,10,f+2,(43,47,54))
    P(im,12,f+1,(94,255,114))
def d_dir(im,f):
    for x in (5,7,9):                                                      # three drive caddies
        P(im,x,f+1,(121,128,138)); P(im,x,f+2,(102,109,119))
    P(im,10,f+1,(43,47,54)); P(im,11,f+1,(173,245,78)); P(im,12,f+1,(94,255,114))
def icons():
    icon_chassis_switch().save(I+'chassis_switch.png')
    icon_blade_enclosure().save(I+'blade_enclosure.png')
    icon_transport_shelf().save(I+'optical_transport_shelf.png')
    icon_1u((236,180,40),d_mux).save(I+'optical_multiplexer_rack.png')
    icon_1u((236,127,39),d_amp).save(I+'optical_amplifier_rack.png')
    icon_1u((173,245,78),d_dir).save(I+'directory_server_rack.png')

if __name__=='__main__':
    chassis_switch(); blade_enclosure(); transport_shelf(); unmirror('chassis_switch'); unmirror('blade_enclosure')
    mux_rear(); amp_rear(); directory_rear(); icons()
