# Phase 1 items, v3: every item built from one of Cody's Arcforge sprites (tools/arcforge_ref/), recoloured and,
# where needed, reshaped. Vanilla-style stepped light + one-step grain throughout.
import os, random, colorsys
from PIL import Image
from el_style import H, put, img
import arc_based
REF=arc_based.REF
def load(n): return Image.open(os.path.join(REF,n+'.png')).convert('RGBA')
def L(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
def palette(im): return sorted({im.getpixel((x,y))[:3] for x in range(16) for y in range(16) if im.getpixel((x,y))[3]},key=L)
def remap(im,m):
    """Exact colour map: {source rgb: target rgb}; colours not in the map are kept."""
    out=im.copy()
    for y in range(16):
        for x in range(16):
            p=im.getpixel((x,y))
            if p[3] and p[:3] in m: out.putpixel((x,y),tuple(m[p[:3]])+(p[3],))
    return out
def grain(im,seed,p=0.12,keep=(),light=0.3):
    r=random.Random(seed); src=im.copy()
    for y in range(1,15):
        for x in range(1,15):
            c=src.getpixel((x,y))
            if not c[3] or c[:3] in keep: continue
            if all(src.getpixel((x+dx,y+dy))==c for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))) and r.random()<p:
                k=0.9 if r.random()>=light else 1.1; im.putpixel((x,y),tuple(max(0,min(255,int(v*k))) for v in c[:3])+(c[3],))
    return im
def ramp(*hexes): return [H(h) for h in hexes]
TIERS=[ramp('#7A3212','#B8521F','#F07A3C','#FFB48A'),ramp('#7A5C08','#B88E14','#F0C030','#FFE08A'),ramp('#00663F','#00A06B','#00D992','#B5FFE3'),
       ramp('#164A80','#2474C2','#3FA3F5','#A8D8FF'),ramp('#4C2685','#7A44C8','#A66BF5','#DDC4FF')]
CYAN=ramp('#0E5560','#168A96','#2FD0D8','#A8F4F7')

def wafer():
    """Arcforge rubber gasket's circle, hole filled: polished silver lit from the top-left, the gasket's own rim
       shading kept (lit left, dark right), a soft diagonal rainbow sheen and a glint."""
    src=load('rubber_gasket'); P=palette(src)            # 0 outline .. 5 lightest
    SIL=ramp('#2B3036','#4E565F','#737C87','#98A1AB','#BCC4CC','#DDE3E8')
    out=remap(src,{P[0]:SIL[0],P[1]:SIL[2],P[2]:SIL[2],P[3]:SIL[3],P[4]:SIL[4],P[5]:SIL[5]})
    rain=ramp('#E7A6CB','#EED39C','#B0E2A6','#A0D0EA','#BDACEC')
    rows=[[x for x in range(16) if src.getpixel((x,y))[3]] for y in range(16)]
    for y in range(16):
        if not rows[y]: continue
        x0,x1=min(rows[y]),max(rows[y])
        for x in range(x0+2,x1-1):
            if src.getpixel((x,y))[3] and L(src.getpixel((x,y))[:3])>60 and not (x0+1<x<x1-1): continue
            k=(x+y)/2-7.5
            c=SIL[5] if k<-4 else SIL[4] if k<-1 else SIL[3] if k<2 else SIL[2]
            band=x-y
            if -2<=band<=2: rc=rain[band+2]; c=tuple(round(c[i]*0.5+rc[i]*0.5) for i in range(3))
            out.putpixel((x,y),c+(255,))
    for (x,y) in ((4,4),(5,4),(4,5)): out.putpixel((x,y),SIL[5]+(255,))
    return grain(out,71,0.10,keep={SIL[5],SIL[0]})
def boule():
    """Arcforge gas cartridge as a grown silicon boule: dark blue-grey metal, the band becomes a faint growth ring,
       the valve becomes the seed nub."""
    src=load('wrought_gas_cartridge'); B=ramp('#14181E','#222832','#323A46','#46505E','#64707F','#93A0AE')
    m={H('#0C0D0F'):B[0],H('#868E96'):B[2],H('#9EA6AD'):B[3],H('#B6BDC3'):B[4],H('#CCD2D7'):B[5],
       H('#9C7A22'):B[1],H('#A88430'):B[2],H('#BA9736'):B[2],H('#C8A040'):B[3],H('#D9B54A'):B[3],H('#E5C86B'):B[4],H('#E8C860'):B[4],H('#F2DC8C'):B[4],
       H('#3CB45A'):B[3]}
    out=remap(src,m)
    for y in (0,1):
        for x in range(16): out.putpixel((x,y),(0,0,0,0))
    for x in range(16):                                    # seed nub: 2 px, outlined
        if out.getpixel((x,2))[3]: out.putpixel((x,2),B[0]+(255,) if x in (6,9) else B[3]+(255,))
    for x in (7,8): out.putpixel((x,1),B[0]+(255,))
    return grain(out,72,0.12,keep={B[5]})
def sheet():
    """Arcforge plastic sheet with its interior highlight specks (rows 5-6) folded into the face tone."""
    src=load('plastic_sheet'); P=palette(src)
    for (x,y) in ((4,5),(5,5),(4,6)):                       # highlight specks
        src.putpixel((x,y),P[3]+(255,))
    for x in range(6,10): src.putpixel((x,10),P[3]+(255,))   # crease line (keep the curl shading at x 10-12)
    return src,P
def copper_foil():
    """Arcforge plastic sheet (curled corner) recoloured to rolled copper."""
    src,P=sheet(); C=ramp('#3E1C0C','#8A4420','#B8642F','#DC8A4E','#F6B886')
    return grain(remap(src,{P[0]:C[0],P[1]:C[1],P[2]:C[2],P[3]:C[3],P[4]:C[4]}),73,0.12,light=0)
def substrate():
    """Arcforge plastic sheet as a blank PCB: green laminate, copper traces and pads, mounting holes."""
    src,P=sheet(); GR=ramp('#0B2614','#174A2C','#1F6038','#2C7C4A','#45A066')
    out=remap(src,{P[0]:GR[0],P[1]:GR[1],P[2]:GR[2],P[3]:GR[3],P[4]:GR[4]}); out=grain(out,74,0.12)
    CU=ramp('#8C5A1E','#C98A3A','#F0C070')
    for x in range(3,9): out.putpixel((x,6),CU[1]+(255,))
    for y in range(6,11): out.putpixel((8,y),CU[1]+(255,))
    for x in range(4,8): out.putpixel((x,10),CU[1]+(255,))
    for y in range(8,11): out.putpixel((3,y),CU[1]+(255,))
    for x in range(9,12): out.putpixel((x,8),CU[1]+(255,))
    for y in range(8,12): out.putpixel((11,y),CU[1]+(255,))
    for x in range(5,8): out.putpixel((x,4),CU[1]+(255,))
    for p in ((3,6),(8,6),(3,8),(8,10),(4,10),(11,8),(11,11),(5,4)): out.putpixel(p,CU[2]+(255,))
    for p in ((3,12),(11,12),(10,11)): out.putpixel(p,GR[0]+(255,))
    return out
def fiberglass():
    """Arcforge carbon fiber weave recoloured to pale green/white fiberglass."""
    return grain(arc_based.recolour('carbon_fiber.png',['#2E4A3A','#6F927C','#A3C4AE','#CFE6D6','#F3FBF5']),75,0.06)
def solder_paste():
    """Arcforge thermal capsule as a tub of solder paste: grey lids, white plastic body, a window showing grey paste."""
    src=load('wrought_thermal_capsule')
    LID=ramp('#3A3F46','#6E757E','#9AA1AA','#C3C9D0','#E3E7EB'); BOD=ramp('#9A9C9E','#C6C8CA','#DEE0E2','#F2F3F4')
    PASTE=ramp('#3C4046','#5E636A','#80868E','#A2A8B0')
    m={H('#0C0D0F'):H('#1A1C20'),H('#9C7A22'):LID[0],H('#BA9736'):LID[1],H('#D9B54A'):LID[2],H('#E5C86B'):LID[3],H('#F2DC8C'):LID[4],
       H('#6A5A44'):H('#2A2D32'),H('#A69C86'):BOD[0],H('#BCB29C'):BOD[1],H('#CEC6B2'):BOD[2],H('#DED8C6'):BOD[3],
       H('#B85A18'):PASTE[0],H('#F2721F'):PASTE[1],H('#FFB45A'):PASTE[2],H('#FFE0A0'):PASTE[3]}
    return grain(remap(src,m),78,0.10,keep=set(PASTE))
def module_die(src_name,accent,face=None):
    """Arcforge module body as a die: slate package and gold pins kept; accent strip and face pattern recoloured."""
    src=load(src_name); out=src.copy(); dp,sh,base,lt=accent
    acc={H('#E0B020'):base,H('#8A6A10'):sh,H('#B88A1E'):sh,H('#B8F2EA'):lt,H('#D8CFA8'):base,H('#F4EED8'):lt,H('#7A6E4A'):dp,H('#5E656D'):sh}
    for y in range(13):                                    # recolour accents only above the pin row (pins stay gold)
        for x in range(16):
            p=src.getpixel((x,y))
            if p[3] and p[:3] in acc: out.putpixel((x,y),acc[p[:3]]+(255,))
    return grain(out,76,0.10,keep=set(accent))
def logic_die(): return module_die('area_module',CYAN)
def storage_die(t): return module_die('insulation_upgrade',TIERS[t])
def photomask(kind):
    """Arcforge plate die frame as a photomask: chrome frame kept, inside replaced by glass with the circuit pattern,
       bottom strip coloured by kind (logic cyan, storage violet)."""
    src=load('plate_die'); P=palette(src)
    CR=ramp('#0C0D0F','#4A525C','#7C8590','#A9B1BA','#D7DDE3'); GL=ramp('#3E6F7A','#6AA0AC','#96CAD4','#C4EAF0')
    strip=CYAN if kind=='logic' else TIERS[4]
    m={H('#0C0D0F'):CR[0],H('#747880'):CR[4],H('#2E3237'):CR[1],H('#484C54'):CR[2],H('#5C646C'):CR[3],H('#4A8FE0'):strip[2]}
    out=remap(src,m)
    for y in range(3,13):
        for x in range(2,14):
            t=3 if x+y<11 else 2 if x+y<19 else 1
            if x-y in (2,3): t=3
            out.putpixel((x,y),GL[t]+(255,))
    PAT=H('#24375C') if kind=='logic' else H('#4A2558')
    if kind=='logic':
        pts=[(3,4),(4,4),(5,4),(5,5),(5,6),(6,6),(7,6),(7,7),(7,8),(8,8),(9,8),(9,9),(9,10),(10,10),(11,10),(11,11),
             (3,7),(3,8),(3,9),(4,9),(5,9),(5,10),(5,11),(9,4),(10,4),(11,4),(11,5),(11,6),(12,6)]
        for p in pts: out.putpixel(p,PAT+(255,))
    else:
        for y in range(4,12,2):
            for x in range(3,13,3): out.putpixel((x,y),PAT+(255,)); out.putpixel((x+1,y),PAT+(255,))
    out.putpixel((12,12),strip[3]+(255,))
    return grain(out,77,0.08,keep={PAT,GL[3]})
def drive(t):
    """Tilted Arcforge-plate drive (the variant Cody picked): slate plate, lit rim, label face, tier-colour band,
       green status light - no printed capacity."""
    SL=['#1F2228','#8D949D','#454B54','#555B65','#666D77','#79808A']
    m=arc_based.rect_plate(); im=Image.new('RGBA',(16,16),(0,0,0,0)); dp,sh,base,lt=TIERS[t]
    for p,i in m.items(): im.putpixel(p,H(SL[i])+(255,))
    A=(4,1); long=(10,5); short=(-4,6)
    for (x,y),i in m.items():
        if i not in (3,4,5): continue
        px,py=x+0.5-A[0],y+0.5-A[1]; det=long[0]*short[1]-long[1]*short[0]
        u=(px*short[1]-py*short[0])/det; v=(long[0]*py-long[1]*px)/det
        if 0.12<u<0.9 and 0.22<v<0.8: im.putpixel((x,y),(226,230,234,255) if i>=4 else (206,210,216,255))
        if 0.12<u<0.9 and 0.12<v<=0.22: im.putpixel((x,y),lt+(255,) if u<0.5 else base+(255,))
        if 0.86<u<0.98 and 0.8<v<0.95: im.putpixel((x,y),(60,224,90,255))
    return im
ITEMS={'silica':arc_based.silica,'silica_blend':arc_based.silica_blend,'silicon_boule':boule,'silicon_wafer':wafer,
       'ferrite':arc_based.ferrite,'copper_foil':copper_foil,'fiberglass':fiberglass,'solder_paste':solder_paste,
       'circuit_substrate':substrate,'logic_die':logic_die,'logic_photomask':lambda: photomask('logic'),
       'storage_photomask':lambda: photomask('storage')}
for i,c in enumerate(['8k','32k','128k','512k','2m']):
    ITEMS[f'storage_die_{c}']=(lambda i=i: storage_die(i)); ITEMS[f'storage_drive_{c}']=(lambda i=i: drive(i))
