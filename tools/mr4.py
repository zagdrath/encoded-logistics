# v4: 1 texel per model px everywhere; high-res (4 texels/px) ONLY for the segment displays (tiny inserts) and the
# Integrated's console screen (the Terminal Desk's CRT texture). 1311 Disk Drive with a stepped sloped front and a 3D glass
# compartment showing the disk pack; 729 reels in 3D.
from mr3 import *
import mid_tex as M
def disp_glow(state,frames,digits,w,h):
    """High-res insert for a segment display only: digits 3x5 on a w x h texel strip (4 texels / px)."""
    st=Image.new('RGBA',(w,h*frames),(0,0,0,0)); ipl=['C1','C3','C6','C9','CA','CC','--','A6']
    for f in range(frames):
        im=Image.new('RGBA',(w,h),(0,0,0,0))
        for y in range(h):
            for x in range(w): px(im,x,y,S[0])
        if state=='ipl': code,col=ipl[f%8],M.AMB[2]
        elif state=='run': code,col='A60',M.GRN[3]
        elif state=='busy': code,col=('A6'+str(f%10)),M.GRN[3]
        else: code,col=('E2C' if f%2==0 else '   '),M.RED[3]
        for i,ch in enumerate((code+'   ')[:digits]): M.seg(im,1+i*4,(h-5)//2,ch,col)
        st.alpha_composite(im,(0,h*f))
    return st
def lamps_glow(state,frames,w,h,lamps):
    """1:1 lamp overlay: lamps = [(x, y, role)], role power / attn / activity."""
    st=Image.new('RGBA',(w,h*frames),(0,0,0,0))
    for f in range(frames):
        for (x,y,role) in lamps:
            on={'power':state!='off','attn':state=='attn' or (state=='ipl' and f%2==0),'activity':(state=='busy' and f%2==0) or (state=='ipl' and f%3==0)}[role]
            if on: st.putpixel((x,y+h*f),tuple({'power':M.GRN[3],'attn':M.RED[4],'activity':M.AMB[3]}[role])+(255,))
    return st
# ---------- Midrange System ----------
def midrange(expansion):
    A=Atlas(64,64)
    f=face(18,12,CREAM,8); seam(f,CREAM,y=1,base=8); seam(f,CREAM,x=8,base=8); louvres(f,CREAM,2,7,14,2,8); A.add('front',f)
    s=face(12,12,CREAM,7); louvres(s,CREAM,1,2,5,4,7); seam(s,CREAM,x=7,base=7); A.add('side',s)
    r=face(18,12,CREAM,6)
    for x in (2,5,8,11,14): recess(r,S,x,2,2,2,1)
    louvres(r,CREAM,2,7,14,2,6); A.add('rear',r)
    A.add('top',face(18,12,CREAM,9)); A.add('h_top',face(9,11,CREAM,9)); A.add('o_top',face(7,10,CREAM,9)); A.add('h_side',face(11,3,CREAM,7))
    A.add('h_back',face(9,3,CREAM,6)); A.add('o_back',face(7,3,CREAM,6)); A.add('deck',face(2,11,CREAM,8)); A.add('dk',face(18,1,S,1)); A.add('dk_s',face(12,1,S,1))
    A.add('epo',face(4,4,RD,3))
    d=face(9,3,CREAM,8)                                                        # diskette unit front, 1:1: window row + slot(s) + latch
    for x in range(1,8): px(d,x,1,S[1])
    for k in range(2 if expansion else 1):
        px(d,1+k*4,2,S[0]); px(d,2+k*4,2,S[0]); px(d,3+k*4,2,S[0]); px(d,4+k*4,2,S[7])
    A.add('disk',d)
    o=face(7,3,CREAM,8)                                                        # operator panel front, 1:1: dark strip, lamps, display socket
    for x in range(1,7): px(o,x,1,S[2]); px(o,x,2,S[1])
    A.add('oper',o)
    E=[box('body',(-1,1,4),(17,13,16),A,{'north':'front','south':'rear','east':'side','west':'side','up':'top'}),
       box('plinth',(-0.5,0,4.5),(16.5,1,15.5),A,{'north':'dk','south':'dk','east':'dk_s','west':'dk_s'}),
       box('disk_unit',(8,13,5),(17,16,16),A,{'north':'disk','up':'h_top','east':'h_side','west':'h_side','south':'h_back'}),
       box('deck',(6,13,6),(8,13.5,16),A,{'up':'deck','north':'dk'}),
       box('oper_unit',(-1,13,6),(6,16,16),A,{'north':'oper','up':'o_top','east':'h_side','west':'h_side','south':'o_back'}),
       box('epo',(17,9.5,5.5),(17.4,10.5,6.5),A,{'*':'epo'},False)]
    # overlays (emissive): 1:1 lamps across the panel's dark strip, + the high-res 2-digit display insert (2 x 1.25 px)
    glow=[el('oper_lamps',(-1,13,5.99),(6,16,5.99),{'north':F('#lamps',[0,0,16,16])},False,**GLOW),
          el('oper_display',(-0.5,14.25,5.98),(2.5,15.5,5.98),{'north':F('#disp',[0,0,16,16])},False,**GLOW)]
    return A,None,E,glow
# ---------- Integrated ----------
def integrated():
    A=Atlas(128,64)
    for n in ('front_l','front_r'):
        f=face(14,12,CREAM,8); seam(f,CREAM,y=1,base=8); louvres(f,CREAM,1,3,12,3,8); A.add(n,f)
    A.add('side',face(12,12,CREAM,7)); r=face(28,12,CREAM,6); louvres(r,CREAM,3,7,22,2,6); A.add('rear',r)
    A.add('deck',face(28,12,CREAM,9)); A.add('hood_s',face(7,7,CREAM,7)); A.add('hood_t',face(13,7,CREAM,9)); A.add('hood_b',face(13,7,CREAM,6))
    A.add('mag_t',face(10,8,CREAM,9)); A.add('mag_s',face(8,3,CREAM,7)); A.add('mag_b',face(10,3,CREAM,6))
    A.add('kb_s',face(11,1,CREAM,7)); A.add('dk',face(28,1,S,1)); A.add('dk_s',face(12,1,S,1))
    fa=face(13,7,S,2)                                                          # fascia, 1:1: dark panel, screen recess, lamps, knobs, buttons
    for x in range(13): px(fa,x,0,CREAM[9]); px(fa,x,6,CREAM[5])
    recess(fa,S,7,1,5,5,0)                                                     # screen area (texture-left = high x)
    for x in (1,3,5): px(fa,x,2,S[0])
    px(fa,2,4,S[7]); px(fa,4,4,S[7]); px(fa,1,5,CREAM[8]); px(fa,3,5,CREAM[8])
    A.add('fascia',fa)
    mg=face(10,3,CREAM,8)
    for x in range(1,8): px(mg,x,1,S[1])
    for k in range(4): px(mg,2+k*2,1,CREAM[10])
    px(mg,8,1,S[7]); A.add('mag',mg)
    E=[box('body_left',(8,1,4),(22,13,16),A,{'north':'front_l','east':'side','south':'rear','up':'deck'}),
       box('body_right',(-6,1,4),(8,13,16),A,{'north':'front_r','west':'side','south':'rear','up':'deck'}),
       box('plinth',(-5.5,0,4.5),(21.5,1,15.5),A,{'north':'dk','south':'dk','east':'dk_s','west':'dk_s'}),
       box('magazine_unit',(10,13,6),(20,16,14),A,{'north':'mag','up':'mag_t','east':'mag_s','west':'mag_s','south':'mag_b'}),
       box('console_hood',(-5,13,9),(8,20,16),A,{'north':'fascia','up':'hood_t','east':'hood_s','west':'hood_s','south':'hood_b'}),
       box('console_keyboard',(-4,13,4.5),(7,14,8.5),A,{'up':('#kb',[0,0,16,16]),'north':'kb_s','east':'kb_s','west':'kb_s','south':'kb_s'})]
    glow=[el('fascia_lamps',(-5,13,8.99),(8,20,8.99),{'north':F('#lamps',[0,0,16,16])},False,**GLOW),
          el('fascia_display',(-4.5,15,8.98),(-1.5,16.25,8.98),{'north':F('#disp',[0,0,16,16])},False,**GLOW)]
    screen=[el('screen',(1.5,14.5,8.98),(6.5,19,8.98),{'north':F('#screen',[0,0,16,16])},False,**GLOW)]   # the fascia's screen recess
    return A,None,E,glow,screen
# ---------- Line Printer (1:1 control strip) ----------
def line_printer():
    A=Atlas(64,64)
    st=face(20,9,CREAM,7); seam(st,CREAM,y=1,base=7); louvres(st,CREAM,3,5,14,2,7); A.add('stand',st); A.add('stand_s',face(12,9,CREAM,6))
    b=face(20,5,CREAM,8)
    for x in range(1,19): px(b,x,2,S[1]); px(b,x,3,S[2])
    for x in (2,3): px(b,x,2,M.RED[1])
    for k in range(6): px(b,8+k*2,3,BLUE[6])
    A.add('body',b); A.add('body_back',face(20,5,CREAM,7)); A.add('body_s',face(12,5,CREAM,7)); A.add('body_t',face(20,12,CREAM,9))
    A.add('trac',face(3,2,CREAM,10)); A.add('trac_s',face(5,2,CREAM,9)); A.add('knob',face(2,2,S,2)); A.add('rod',face(1,1,S,8)); A.add('slot',face(14,1,S,0))
    E=[box('stand',(-2,0,4),(18,9,16),A,{'north':'stand','south':'stand','east':'stand_s','west':'stand_s'}),
       box('body',(-2,9,4),(18,14,16),A,{'north':'body','south':'body_back','east':'body_s','west':'body_s','up':'body_t'}),
       box('paper_slot',(1,14,8),(15,14.05,9),A,{'up':'slot'},False),
       box('tractor_l',(13,14,6),(16,16,11),A,{'*':'trac','east':'trac_s','west':'trac_s'}),
       box('tractor_r',(0,14,6),(3,16,11),A,{'*':'trac','east':'trac_s','west':'trac_s'}),
       box('knob',(18,10.5,9),(19,12.5,11),A,{'*':'knob'},False),
       el('paper',(3,14,8.5),(13,22,8.5),{'north':F('#paper',[0,0,16,16]),'south':F('#paper',[0,0,16,16])},False,rotation={'angle':22.5,'axis':'x','origin':[8,14,8.5]})]
    for i,x in enumerate((3,6,9.5,13)):
        E.append(box(f'basket_{i}',(x,14,13),(x+0.3,20,13.3),A,{'*':'rod'},False,rotation={'angle':22.5,'axis':'x','origin':[x,14,13]}))
    E.append(box('basket_bar',(3,19.5,15),(13.3,19.8,15.3),A,{'*':'rod'},False))
    glow=el('panel_glow',(-2,9,3.99),(18,14,3.99),{'north':F('#glow',[0,0,16,16])},False,**GLOW)
    return A,None,E,glow
def lp_glow1(frames=2):
    st=Image.new('RGBA',(20,5*frames),(0,0,0,0))
    for f in range(frames):
        st.putpixel((17,5*f+2),M.RED[4]+(255,))
        if f==0: st.putpixel((16,5*f+2),M.RED[3]+(255,))
        for x in range(4,7): st.putpixel((19-x,5*f+2),(M.AMB[2] if (x+f)%2 else M.AMB[3])+(255,))
    return st
# ---------- 1311 Disk Drive: stepped sloped front, 3D glass pack compartment ----------
GLASS=None
def disk_drive():
    """14 x 16 x 12 (1311). Cabinet (grey frame, blue door, bright trim), grey belt; upper unit y 11..16 whose WHOLE front is
       one slope: a full-width panel rotated 22.5 about X (top leaning back to z 4 + 1.91), the left half solid (control
       strip, lamps), the right half glass over the pack compartment. Everything behind the slope starts at z 6.1, so nothing
       pokes through it (no clipping). Compartment: cream frame, glass top, the disk pack in 3D inside."""
    A=Atlas(64,64)
    d=face(12,9,BLUE,6); seam(d,BLUE,y=1,base=6); A.add('door',d); A.add('frame_s',face(12,10,S,4))
    A.add('trim',face(12,1,S,9)); A.add('belt',face(14,1,S,5)); A.add('belt_s',face(12,1,S,5))
    sl=face(7,5,CREAM,8)                                                         # slope panel, solid half (control strip)
    for x in range(1,6): px(sl,x,2,S[2])
    px(sl,1,2,M.GRN[1]); px(sl,2,2,M.RED[1]); px(sl,4,2,S[6])
    A.add('slope',sl); A.add('slope_e',face(7,1,CREAM,7))
    A.add('ctl_top',face(6,10,CREAM,9)); A.add('ctl_side',face(10,5,CREAM,7)); A.add('ctl_back',face(6,5,CREAM,6))
    A.add('post',face(1,5,CREAM,9)); A.add('rail',face(7,1,CREAM,9)); A.add('rail_s',face(10,1,CREAM,8)); A.add('floor',face(7,10,S,1))
    WOOD=[H('#4A2A14'),H('#5E3618'),H('#74441E'),H('#8A5226'),H('#A0622E'),H('#B47238'),H('#C68446'),H('#D69658'),H('#E2A86C'),H('#ECBA82'),H('#F4CC9A')]
    A.add('platter',face(6,1,WOOD,6)); A.add('platter_t',face(6,6,WOOD,7)); A.add('hub',face(3,3,CREAM,10)); A.add('hub_s',face(3,1,CREAM,9))
    Z=6.1
    E=[box('cabinet',(1,0.5,4),(15,10,16),A,{'north':'door','east':'frame_s','west':'frame_s','south':'door'}),
       box('trim',(2,9,3.9),(14,9.5,4),A,{'north':'trim'},False),
       box('belt',(1,10,4),(15,11,16),A,{'north':'belt','south':'belt','east':'belt_s','west':'belt_s','up':'belt'}),
       # control half (viewer left, x 8..15): body behind the slope
       box('ctl_body',(8,11,Z),(15,16,16),A,{'up':'ctl_top','east':'ctl_side','west':'ctl_side','south':'ctl_back'}),
       # the slope: ONE solid box rotated 22.5 about X - its front face is the slope, its 2.4 px depth runs back into the
       # body and the belt (hidden inside them), its side faces close the wedge; inset 0.01 so they never z-fight the body
       el('slope_solid',(8.01,11,4),(14.99,16.4,6.4),{'north':F('#t',A.uv('slope')),'east':F('#t',A.uv('ctl_side')),'west':F('#t',A.uv('ctl_side'))},True,
          rotation={'angle':22.5,'axis':'x','origin':[11.5,11,4]}),
       # pack compartment (viewer right, x 1..8): frame, floor, pack
       box('comp_floor',(1,11,Z),(8,11.2,15.5),A,{'up':'floor'},False),
       box('post_bl',(1,11,15),(2,16,16),A,{'*':'post'}),box('post_br',(7,11,15),(8,16,16),A,{'*':'post'}),
       box('rail_b',(1,15.5,15),(8,16,16),A,{'*':'rail'}),box('rail_r',(1,15.5,Z),(1.6,16,15),A,{'*':'rail_s'}),
       box('rail_top_f',(1,15.5,Z),(8,16,Z+0.6),A,{'*':'rail'}),
       el('slope_frame_r',(1.01,11,4),(1.6,16.4,6.4),{d:F('#t',A.uv('post')) for d in ('north','east','west')},True,rotation={'angle':22.5,'axis':'x','origin':[4.5,11,4]})]
    for k in range(5):
        y=11.4+k*0.55
        E.append(box(f'platter{k}_a',(2.5,y,8),(6.5,y+0.3,13),A,{'up':'platter_t','*':'platter'},False))
        E.append(box(f'platter{k}_b',(2,y,8.5),(7,y+0.3,12.5),A,{'up':'platter_t','*':'platter'},False))
    E+=[box('hub',(3.3,14.1,9.3),(5.7,14.4,11.7),A,{'up':'hub','*':'hub_s'},False),box('spindle',(4.1,11.2,10.1),(4.9,14.1,10.9),A,{'*':'post'},False)]
    glass=[el('glass_slope',(1.6,11,4.1),(8,16.2,4.1),{'north':F('#glass',[0,0,16,16]),'south':F('#glass',[0,0,16,16])},False,rotation={'angle':22.5,'axis':'x','origin':[4.5,11,4]}),
           el('glass_top',(1.6,15.99,Z+0.6),(8,15.99,15),{'up':F('#glass',[0,0,16,16]),'down':F('#glass',[0,0,16,16])},False),
           el('glass_side',(1.02,11.2,Z),(1.02,15.5,15),{'west':F('#glass',[0,0,16,16]),'east':F('#glass',[0,0,16,16])},False)]
    lamp=el('lamp',(13,12.6,3.9),(14.2,13.4,3.9),{'north':F('#lamp',[0,0,16,16])},False,rotation={'angle':22.5,'axis':'x','origin':[11.5,11,4]},**GLOW)
    return A,E,glass,lamp
def glass_tex():
    """Glass (translucent): smoked steel-ramp tint, a stepped diagonal glint band - alpha 70 / 110 on the glint."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            t=x+y; c=S[8] if 6<=t<=9 else S[5]; im.putpixel((x,y),tuple(c)+(110 if 6<=t<=9 else 60,))
    for x in range(16): im.putpixel((x,0),tuple(S[9])+(150,))
    return im
# ---------- 729 Tape Drive: 1:1 front, 3D reels behind the window ----------
def tape_drive():
    """729: 18 x 40 x 14 px, centred on the block (x -1..17, 1 px overhang each side; z 1..15). Blocks are limited to 32 px,
       so the master carries y 0..32 and the top block of the 1 x 3 footprint carries y 32..40 (control strip, cap).
       Front: blue centre door (y 1..19), reel window y 20..32 - a real opening with a recessed well, two 6 px reels and the
       head behind glass - control strip on top."""
    A=Atlas(64,128)
    lo=face(18,19,CREAM,8)
    for y in range(1,18):
        for x in range(6,12): px(lo,x,y,BLUE[6] if x<9 else BLUE[5])
    seam(lo,CREAM,x=5,base=8); seam(lo,CREAM,x=12,base=8); px(lo,8,9,S[8]); px(lo,9,9,S[8]); A.add('lower',lo)
    A.add('side_lo',face(14,19,CREAM,7)); A.add('rear_lo',face(18,19,CREAM,6)); A.add('jamb',face(1,12,CREAM,7)); A.add('side_win',face(14,12,CREAM,7)); A.add('rear_win',face(18,12,CREAM,6))
    A.add('well',face(16,12,S,1)); A.add('well_s',face(4,12,S,2)); A.add('well_t',face(16,4,S,0))
    for n,b in (('reel',9),('reel2',7)): A.add(n,face(6,6,S,b)); A.add(n+'_e',face(6,1,S,b-2))
    A.add('hub',face(2,2,S,3))
    up=face(18,8,CREAM,8)
    for x in range(1,17): px(up,x,3,S[2]); px(up,x,4,S[1])
    for i,c in enumerate((YE[3],S[6],M.RED[1],M.RED[1],S[6],S[6])): px(up,3+i*2,4,c)
    for y in range(1,3):
        for x in range(13,16): px(up,x,y,S[9])                                     # unit number plate
    A.add('upper',up); A.add('side_up',face(14,8,CREAM,7)); A.add('rear_up',face(18,8,CREAM,6)); A.add('top',face(18,14,CREAM,9))
    E=[box('lower',(-1,0.5,1),(17,20,15),A,{'north':'lower','east':'side_lo','west':'side_lo','south':'rear_lo'}),
       box('jamb_l',(16,20,1),(17,32,15),A,{'north':'jamb','east':'side_win','west':'well_s'}),
       box('jamb_r',(-1,20,1),(0,32,15),A,{'north':'jamb','west':'side_win','east':'well_s'}),
       box('well',(0,20,4),(16,32,15),A,{'north':'well','south':'rear_win'}),
       box('well_floor',(0,20,1),(16,20.01,4),A,{'up':'well_t'},False)]
    for (x0,n) in ((9.5,'reel'),(0.5,'reel2')):                                   # supply (viewer left), take-up (right)
        E.append(box(n+'_a',(x0,24.5,2.8),(x0+6,28.5,4),A,{'north':n,'*':n+'_e'},False))
        E.append(box(n+'_b',(x0+1,23.5,2.8),(x0+5,29.5,4),A,{'north':n,'*':n+'_e'},False))
        E.append(box(n+'_hub',(x0+2,25.5,2.5),(x0+4,27.5,2.8),A,{'*':'hub'},False))
    E.append(box('head',(7,21,3),(9,23,4),A,{'*':'hub'},False))
    top=[box('upper',(-1,0,1),(17,8,15),A,{'north':'upper','east':'side_up','west':'side_up','south':'rear_up','up':'top'})]
    glass=[el('glass',(0,20,2),(16,32,2),{'north':F('#glass',[0,0,16,16]),'south':F('#glass',[0,0,16,16])},False)]
    glow=el('lamps',(-1,0,0.99),(17,8,0.99),{'north':F('#glow',[0,0,16,16])},False,**GLOW)
    return A,E,glass,glow,top
