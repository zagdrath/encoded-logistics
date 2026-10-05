# Midrange System + Keypunch + Card Reader + Line Printer textures. World surfaces: vanilla method (1 texel/px, small
# palettes, brushed runs, stepped bands, no speckle, no gradients). Instrument panels: 4 texels/px.
import random
from PIL import Image
from el_style import H, put
from desk_tex import vfill, rim, c, pal, DARK, STEEL, CASE, KEY
PLAST=pal('#8F928E','#A4A7A2','#B6B9B4','#C6C9C4','#D4D7D2','#E0E2DE','#EBEDE9')      # midrange cabinet: cool light grey
BEIGE=pal('#8C8270','#A39983','#B8AE97','#C9C0A9','#D7CFBA','#E3DCC9','#EDE8D8')      # printer / reader: warm beige
CONSOLE=pal('#4A5560','#5A6672','#6B7884','#7D8A96','#909CA8','#A4B0BA')             # keypunch console: blue-grey steel
BLUE=pal('#16365E','#22508A','#2E6FB8','#5A94D4','#9CC2EC')
RED=pal('#5A0E0A','#8E231C','#C8322A','#E5483C','#FF9C90')
GRN=pal('#0B3A1C','#127A3A','#2EC060','#7CFFA0','#C8FFD8')
AMB=pal('#4E340C','#9A6410','#F5B23A','#FFD27A')
def new(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))
def grille(im,x0,y0,w,h,p,base,pitch=2):
    """Vertical vent slots: dark slot, lit right lip (light from the top-left)."""
    for x in range(x0,x0+w,pitch):
        for y in range(y0,y0+h): put(im,x,y,c(p,0)); 
        for y in range(y0,y0+h): put(im,x+1,y,c(p,base+1)) if pitch>1 and x+1<x0+w else None
    for x in range(x0,x0+w): put(im,x,y0-1,c(p,base-1)); put(im,x,y0+h,c(p,base+2))
# ---------------- Midrange System ----------------
def mr_front():
    """16x16 front (15 x 13.5 px used, cabinet from y 0.5): crisp edges with a softened (stepped) corner, a bottom vent
       grille; the control panel, slot and switch panel are separate elements over it."""
    im=new(16,16); vfill(im,0,0,16,16,PLAST,4,901)
    for y in range(16): put(im,0,y,c(PLAST,6)); put(im,15,y,c(PLAST,2)); put(im,14,y,c(PLAST,3))
    for x in range(16): put(im,x,0,c(PLAST,6))
    put(im,0,0,c(PLAST,5)); put(im,15,0,c(PLAST,3))                     # softened corners
    grille(im,2,11,12,3,PLAST,4)
    return im
def mr_side():
    """32x16 side (32 deep x 13.5 tall): front grille strip low, the removable service panel (seam, two vent banks,
       a lower vent bank) like the reference's right side."""
    im=new(32,16); vfill(im,0,0,32,16,PLAST,4,902,axis='h')
    for x in range(32): put(im,x,0,c(PLAST,6))
    for y in range(16): put(im,0,y,c(PLAST,5)); put(im,31,y,c(PLAST,2))
    for y in range(1,15): put(im,10,y,c(PLAST,1)); put(im,11,y,c(PLAST,5))    # service panel seam (vertical)
    for x in range(11,31): put(im,x,9,c(PLAST,1)); put(im,x,10,c(PLAST,5))    # horizontal seam
    grille(im,13,2,8,5,PLAST,4); grille(im,23,2,7,5,PLAST,4)                   # upper vent banks
    grille(im,13,12,16,2,PLAST,4); grille(im,2,12,7,2,PLAST,4)                 # lower vents
    return im
def mr_top():
    im=new(32,16); vfill(im,0,0,32,16,PLAST,5,903,axis='v',bands=False); rim(im,0,0,32,16,PLAST,6,3); return im
def mr_rear():
    im=new(16,16); vfill(im,0,0,16,16,PLAST,3,904)
    grille(im,2,2,12,6,PLAST,3)
    for (x,y) in ((3,11),(7,11),(11,11)):                                      # rear connectors
        for dy in range(2):
            for dx in range(3): put(im,x+dx,y+dy,c(DARK,1))
    return im
def mr_panel():
    """24 x 12 at 4 texels/px (5.5 x 3 px used: 22 x 12): bevelled plate; power and attention lamps stacked on the left;
       a wide segmented display window (4 digits, 3x5 segments, 1-texel gaps); a row of 6 function-select buttons."""
    im=new(24,12); vfill(im,0,0,24,12,PLAST,4,905,bands=False); rim(im,0,0,22,12,PLAST,6,2)
    for y in range(1,8):
        for x in range(5,21): put(im,x,y,c(DARK,0) if y in (1,7) or x in (5,20) else c(DARK,1))   # display window
    for (x,y) in ((2,2),(2,5)):                                                              # lamps: power, attention
        put(im,x,y,c(DARK,2)); put(im,x+1,y,c(DARK,2)); put(im,x,y+1,c(DARK,1)); put(im,x+1,y+1,c(DARK,1))
    for i in range(6):                                                                       # function-select buttons
        x=3+i*3
        put(im,x,9,c(PLAST,6)); put(im,x+1,9,c(PLAST,5)); put(im,x,10,c(PLAST,3)); put(im,x+1,10,c(PLAST,2))
    return im
SEG={'0':'abcdef','1':'bc','2':'abged','3':'abgcd','4':'fgbc','5':'afgcd','6':'afgedc','7':'abc','8':'abcdefg','9':'abcdfg',
     'A':'abcefg','b':'cdefg','C':'adef','d':'bcdeg','E':'adefg','F':'aefg','P':'abefg','L':'def','-':'g',' ':''}
SEGPTS={'a':[(0,0),(1,0),(2,0)],'b':[(2,0),(2,1),(2,2)],'c':[(2,2),(2,3),(2,4)],'d':[(0,4),(1,4),(2,4)],
        'e':[(0,2),(0,3),(0,4)],'f':[(0,0),(0,1),(0,2)],'g':[(0,2),(1,2),(2,2)]}
def seg(im,x,y,ch,col):
    for s_ in SEG.get(ch,''):
        for (dx,dy) in SEGPTS[s_]: put(im,x+dx,y+dy,col)
def mr_panel_glow(state,frames):
    """Emissive overlay for the panel (same 24x12 layout). ipl: codes cycle, lamps flicker; run: steady code + power
       lamp; busy: + activity lamp (first function button) blinking, job counter; attn: attention lamp + error code blink."""
    st=Image.new('RGBA',(24,12*frames),(0,0,0,0))
    ipl=['C100','C1A2','C3F0','C6E4','C900','CA10','CC00','----']
    for f in range(frames):
        im=Image.new('RGBA',(24,12),(0,0,0,0)); power=False; attn=False
        if state=='ipl':
            code=ipl[f%len(ipl)]; col=AMB[2]; power=f%3!=2; attn=f%2==0
        elif state=='run': code='A600'; col=GRN[3]; power=True
        elif state=='busy':
            code='A6'+'b'+str(f%10); col=GRN[3]; power=True
            if f%2==0: put(im,3,9,AMB[3]); put(im,4,9,AMB[3]); put(im,3,10,AMB[2]); put(im,4,10,AMB[2])
        else: code='E2C4' if f%2==0 else '    '; col=RED[3]; power=True; attn=True
        if power:
            for (x,y,k) in ((2,2,3),(3,2,2),(2,3,2),(3,3,2)): put(im,x,y,GRN[k])
        if attn:
            for (x,y,k) in ((2,5,4),(3,5,3),(2,6,3),(3,6,3)): put(im,x,y,RED[k])
        for i,ch in enumerate(code): seg(im,7+i*3+i,2,ch,col)
        st.alpha_composite(im,(0,12*f))
    return st
def mr_slot():
    """8 x 28 at 4 texels/px (2 x 7 px): vertical diskette slot recess with a lit lip, latch-lever pivot."""
    im=new(8,28); vfill(im,0,0,8,28,PLAST,4,906,bands=False); rim(im,0,0,8,28,PLAST,6,2)
    for y in range(2,26):
        put(im,3,y,c(DARK,0)); put(im,4,y,c(DARK,1)); put(im,5,y,c(PLAST,6))
    return im
def mr_switch():
    """12 x 8 at 4 texels/px: small bezel with the red rocker power switch."""
    im=new(12,8); vfill(im,0,0,12,8,PLAST,5,907,bands=False); rim(im,0,0,12,8,PLAST,6,2)
    for y in range(2,6):
        for x in range(4,8): put(im,x,y,c(RED,3) if y<4 else c(RED,1))
    put(im,4,2,c(RED,4)); return im
def mr_lever():
    im=new(8,8); vfill(im,0,0,8,8,DARK,2,908,bands=False)
    for x in range(8): put(im,x,0,c(DARK,4))
    return im
# ---------------- Keypunch ----------------
def kp_cabinet():
    im=new(16,16); vfill(im,0,0,16,16,CONSOLE,3,911); rim(im,0,0,16,16,CONSOLE,5,0)
    for y in range(3,13): put(im,7,y,c(CONSOLE,1)); put(im,8,y,c(CONSOLE,4))   # cabinet door seam
    put(im,6,7,c(STEEL,6)); put(im,9,7,c(STEEL,6))                               # handles
    return im
def kp_top():
    im=new(16,16); vfill(im,0,0,16,16,BEIGE,4,912,bands=False); rim(im,0,0,16,16,BEIGE,6,2); return im
def kp_unit():
    """16x16: upper card-path unit front (14 x 4 px): the card bed channel (dark), the punch station window, column
       indicator scale."""
    im=new(16,16); vfill(im,0,0,16,16,CONSOLE,4,913,bands=False); rim(im,0,0,16,4,CONSOLE,5,1)
    for x in range(1,15): put(im,x,2,c(DARK,1)); put(im,x,3,c(CONSOLE,5))         # card bed
    for x in range(6,10): put(im,x,1,c(DARK,0))                                   # punch station window
    for x in range(2,14,2): put(im,x,0,c(CONSOLE,2))                              # column scale
    return im
def card_feed(frames=8,w=16):
    """Card moving along the bed (row 2 of the unit face), 8 frames; cream card with punched holes appearing."""
    st=Image.new('RGBA',(w,16*frames),(0,0,0,0))
    for f in range(frames):
        x0=-4+f*2
        for x in range(max(1,x0),min(15,x0+5)):
            st.putpixel((x,16*f+2),H('#E8DFC4')+(255,))
            if x<8 and (x+f)%2==0: st.putpixel((x,16*f+2),H('#8C7A52')+(255,))
    return st
def hopper(p):
    im=new(16,16); vfill(im,0,0,16,16,p,3,914,bands=False); rim(im,0,0,16,16,p,5,1)
    for y in range(2,14):
        for x in range(2,14): put(im,x,y,H('#E8DFC4') if y%2==0 else H('#D8CEB0'))   # deck of cards seen from above
    return im
# ---------------- Card Reader ----------------
def cr_front():
    im=new(16,16); vfill(im,0,0,16,16,BEIGE,4,921); rim(im,0,0,16,16,BEIGE,6,2)
    for y in range(3,8):
        for x in range(3,13): put(im,x,y,c(DARK,1) if y in (3,7) else c(DARK,2))    # operator strip
    for i,col in enumerate((GRN[1],AMB[1],RED[1])): put(im,4+i*2,5,col)
    for x in range(9,12): put(im,x,5,c(BLUE,2))
    grille(im,3,11,10,2,BEIGE,4)
    return im
def cr_side():
    im=new(16,16); vfill(im,0,0,16,16,BEIGE,3,922); rim(im,0,0,16,16,BEIGE,5,1); grille(im,4,10,8,3,BEIGE,3); return im
def cr_lamps_on():
    im=new(16,16); put(im,4,5,GRN[3]); put(im,6,5,AMB[2]); return im
def cr_track(frames=6):
    """Top-surface feed track (16 wide, row 7-8): cards moving from the hopper (right) to the stacker (left)."""
    st=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    for f in range(frames):
        for x in range(16): st.putpixel((x,16*f+7),c(DARK,1)+(255,)); st.putpixel((x,16*f+8),c(DARK,0)+(255,))
        for k in range(3):
            x0=14-((f*3+k*6)%16)
            for x in range(x0,x0+3):
                if 0<=x<16: st.putpixel((x,16*f+7),H('#E8DFC4')+(255,))
    return st
# ---------------- Line Printer ----------------
def lp_body():
    im=new(16,16); vfill(im,0,0,16,16,BEIGE,4,931); rim(im,0,0,16,16,BEIGE,6,2); grille(im,3,12,10,2,BEIGE,4); return im
def lp_side():
    im=new(16,16); vfill(im,0,0,16,16,BEIGE,3,932); rim(im,0,0,16,16,BEIGE,5,1)
    for (x,y,k) in ((10,5,0),(11,5,1),(10,6,1),(11,6,2),(12,5,1),(12,6,0)): put(im,x,y,c(DARK,k+1))   # platen knob
    return im
def lp_panel():
    """28 x 8 at 4 texels/px (7 x 2 px control strip): dark panel, two red lamps, a 4-digit display, a block of blue
       function buttons, a small blue cluster on the right."""
    im=new(28,8); vfill(im,0,0,28,8,DARK,1,933,bands=False); rim(im,0,0,28,8,DARK,3,0)
    for (x,y) in ((2,1),(4,1)): put(im,x,y,c(RED,1))
    put(im,2,4,c(RED,2)); put(im,2,5,c(RED,1))                                             # red switch
    for x in range(5,10): put(im,x,4,c(DARK,0))                                           # display
    for r in range(2):
        for i in range(5): put(im,12+i*2,3+r*2,c(BLUE,3)); put(im,12+i*2,4+r*2,c(BLUE,1))
    put(im,24,3,c(BLUE,3)); put(im,24,5,c(BLUE,3)); put(im,22,5,c(BLUE,2))
    return im
def lp_panel_glow(frames=2):
    st=Image.new('RGBA',(28,8*frames),(0,0,0,0))
    for f in range(frames):
        st.putpixel((2,8*f+1),RED[4]+(255,))
        if f==0: st.putpixel((4,8*f+1),RED[3]+(255,))
        for x in range(5,10): st.putpixel((x,8*f+4),AMB[2]+(255,) if (x+f)%2 else AMB[3]+(255,))
    return st
def paper(frames=1,scroll=True):
    """Fan-fold green-bar paper with tractor holes (16 x 16 per frame). Printing: 8 frames scrolling the bars up."""
    st=Image.new('RGBA',(16,16*frames),(0,0,0,0))
    for f in range(frames):
        for y in range(16):
            band=((y+f*2)//2)%2==0
            for x in range(16):
                col=H('#EEF2E6') if band else H('#C8DDB8')
                if x in (0,15): col=H('#E2E6DA')
                if x in (0,15) and (y+f*2)%3==0: col=H('#9AA894')
                if 2<x<13 and (y+f*2)%4==1 and ((x+(y+f*2)//4*3)%6)<4 and scroll: col=H('#7E8A78')   # printed lines (thin, regular)
                st.putpixel((x,16*f+y),col+(255,))
            if (y+f*2)%16==0:
                for x in range(16): st.putpixel((x,16*f+y),H('#B4BCA8')+(255,))             # fold perforation
    return st
def lid():
    im=new(16,16)
    for y in range(16):
        for x in range(16): put(im,x,y,c(DARK,2) if 1<x<14 and 1<y<14 else c(BEIGE,4))
    for x in range(2,14): put(im,x,2,c(DARK,4))
    return im
def wire():
    im=new(16,16)
    for y in range(16):
        for x in range(16): put(im,x,y,c(STEEL,6) if (x+y)%3 else c(STEEL,4))
    return im
# ---------------- items ----------------
def _out(im,col):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,col)
def punch_card(punched):
    """80-column card: cream stock, a clipped top-left corner, printed column guide lines; punched = rectangular holes."""
    CARD=pal('#B8A97C','#CDBF93','#DDD1AA','#E9DFBE','#F2EBD2')
    im=new(16,16)
    for y in range(4,12):
        for x in range(1,15): put(im,x,y,c(CARD,3 if y<6 else 2 if y<10 else 1))
    for p_ in ((1,4),(2,4),(1,5)): im.putpixel(p_,(0,0,0,0))                         # clipped corner
    for x in range(3,14,2): put(im,x,6,c(CARD,1))                                      # printed digit rows
    if punched:
        r=random.Random(5)
        for x in range(3,14):
            y=r.choice((5,7,8,9,10)); put(im,x,y,H('#3A3020'))
            if r.random()<0.3: put(im,x,r.choice((7,9)),H('#3A3020'))
    _out(im,H('#5A4E30')); return im
def diskette(written):
    """8" diskette: black square disk in a white paper jacket, centre hub hole, oval read window, label (written: lines)."""
    im=new(16,16)
    for y in range(2,15):
        for x in range(1,14): put(im,x,y,c(DARK,1 if x>2 else 2))
    for y in range(1,14):
        for x in range(3,15): put(im,x,y,H('#EEEDE6') if y>1 and x<14 else H('#DAD8CE'))  # paper jacket (offset)
    for y in range(4,12):
        for x in range(4,13): put(im,x,y,c(DARK,2))                                       # disk visible through jacket cut
    for (x,y) in ((8,7),(9,7),(8,8),(9,8)): put(im,x,y,H('#C8C6BC'))                       # hub hole
    for y in range(9,12): put(im,8,y,c(DARK,0)); put(im,9,y,c(DARK,0))                     # head window
    for y in range(2,4):
        for x in range(4,13): put(im,x,y,H('#F7F6EE') if not written else H('#F2F0E2'))   # label
    if written:
        for x in range(5,12): put(im,x,2,H('#3A4A8C') if x%2 else H('#6A7AB8'))
        for x in range(5,10): put(im,x,3,H('#3A4A8C'))
    _out(im,H('#1A1A18')); return im
def icon_box(body,top,front_fn):
    im=new(16,16)
    for y in range(4,8):
        for x in range(2,14): put(im,x,y,c(top,5 if y<6 else 4))
    for y in range(8,15):
        for x in range(2,14): put(im,x,y,c(body,4 if y<11 else 3))
        put(im,1,y,c(body,5)); put(im,14,y,c(body,2))
    front_fn(im); _out(im,H('#1A1A18')); return im
def icons():
    def mr(im):
        for x in range(8,13): put(im,x,9,c(DARK,1))
        put(im,9,9,GRN[3]); put(im,6,9,c(DARK,0)); put(im,6,10,c(DARK,0)); put(im,6,11,c(DARK,0))
        for x in range(3,13,2): put(im,x,13,c(PLAST,1))
        put(im,9,11,RED[3])
    def kp(im):
        for x in range(3,13): put(im,x,8,c(DARK,1))
        for x in range(10,13): put(im,x,5,H('#E8DFC4')); put(im,x,4,H('#E8DFC4'))
        for x in range(4,11,2): put(im,x,10,c(KEY,4))
    def crd(im):
        for x in range(9,13):
            for y in (4,5): put(im,x,y,H('#E8DFC4'))
        for x in range(4,11): put(im,x,10,c(DARK,1))
        put(im,5,10,GRN[3])
    def lp(im):
        for y in range(1,5):
            for x in range(4,12): put(im,x,y,H('#EEF2E6') if y%2 else H('#C8DDB8'))
        for x in range(3,13): put(im,x,10,c(DARK,1))
        put(im,4,10,RED[3]); put(im,8,10,BLUE[3]); put(im,10,10,BLUE[3])
    return {'midrange_system':icon_box(PLAST,PLAST,mr),'keypunch':icon_box(CONSOLE,BEIGE,kp),'card_reader':icon_box(BEIGE,BEIGE,crd),'line_printer':icon_box(BEIGE,BEIGE,lp)}
