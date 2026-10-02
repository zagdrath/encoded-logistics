# Minecraft-style shading pass: keeps every texture's layout (same pixels opaque, same roles) and replaces
# smooth gradients / flat fills with stepped, hue-shifted ramps plus material grain, lifting the darks.
import os, random, colorsys, glob
from PIL import Image
def H(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
# Grey ramp for steel + recesses: cool shadows, neutral-warm highlights (hue-shifted, stepped, no pure black)
GREY=[H(c) for c in ['#1F2228','#2B2F36','#373C44','#454B54','#555B65','#666D77','#79808A','#8D949D','#A3A9B1','#BBC0C6','#D3D7DB']]
def L(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
GL=[L(c) for c in GREY]
LIFT=0.80          # gamma < 1 lifts the darks toward Minecraft's mid-tone range
def lifted(l): return 255*(l/255)**LIFT
def grey_tone(c): l=lifted(L(c)); return min(range(len(GREY)),key=lambda i:abs(GL[i]-l))
def is_dye(c):
    h,s,v=colorsys.rgb_to_hsv(*[x/255 for x in c]); return s>0.28 and v>0.18
def dye_ramp(base):
    """7 tones (-3..+3) around a dye colour: shadows cooler/more saturated, highlights warmer/softer."""
    h,s,v=colorsys.rgb_to_hsv(*[x/255 for x in base]); out=[]
    for k in range(-3,4):
        vv=min(1,max(0.06,v*(1+0.16*k))); ss=min(1,max(0,s+(-0.10*k if k>0 else -0.05*k)))
        target=0.66 if k<0 else 0.14                      # shadows toward blue/purple, highlights toward yellow
        d=((target-h+0.5)%1)-0.5; hh=(h+d*0.045*abs(k))%1
        r,g,b=colorsys.hsv_to_rgb(hh,ss,vv); out.append((round(r*255),round(g*255),round(b*255)))
    return out
def grain(w,h,seed,mode):
    """Tone offsets (-1/0/+1). 'u' = runs along x (brushed along the cable), 'v' = runs along y, 'c' = small clusters."""
    rnd=random.Random(seed); off=[[0]*w for _ in range(h)]
    if mode in 'uv':
        A,B=(h,w) if mode=='u' else (w,h)
        for a in range(A):
            b=0
            while b<B:
                n=rnd.randint(2,4); o=rnd.choices((-1,0,1),(0.20,0.68,0.12))[0]   # mostly darker wear, few bright glints
                for k in range(b,min(B,b+n)):
                    if mode=='u': off[a][k]=o
                    else: off[k][a]=o
                b+=n
    else:
        for y in range(h):
            for x in range(w):
                if rnd.random()<0.13:
                    o=-1 if rnd.random()<0.7 else 1; off[y][x]=o
                    if x+1<w and rnd.random()<0.5: off[y][x+1]=o
    return off
def repaint(src,dst,mode,seed,dye_base=None,glow_mask=None):
    im=Image.open(src).convert('RGBA'); w,h=im.size; out=Image.new('RGBA',(w,h),(0,0,0,0))
    P=im.load(); O=out.load()
    # per 16x16 frame (animated sheets), so neutral cable frames each keep their own hue
    for fy in range(0,h,16):
        frame=[(x,y) for y in range(fy,min(h,fy+16)) for x in range(w) if P[x,y][3]>0]
        dyes=[P[x,y][:3] for x,y in frame if is_dye(P[x,y][:3])]
        hue=lambda c: colorsys.rgb_to_hsv(*[v/255 for v in c])[0]
        clusters=[]                                   # group dye pixels by hue; each group gets its own ramp
        for c in sorted(dyes,key=hue):
            if clusters and min(abs(hue(c)-hue(clusters[-1][0])),1-abs(hue(c)-hue(clusters[-1][0])))<0.045: clusters[-1].append(c)
            else: clusters.append([c])
        bases=[sorted(cl,key=L)[len(cl)//2] for cl in clusters]
        def ramp_for(c):
            if dye_base: return dye_ramp(dye_base),L(dye_base)
            b=min(bases,key=lambda b: min(abs(hue(c)-hue(b)),1-abs(hue(c)-hue(b))))
            return dye_ramp(b),L(b)
        ramp=bool(bases) or bool(dye_base)
        g=grain(w,16,seed,mode)   # same grain every frame: no flicker
        role={}
        for x,y in frame:
            c=P[x,y][:3]; role[(x,y)]='dye' if (ramp and is_dye(c)) else 'grey'
        for x,y in frame:
            c=P[x,y][:3]; r=role[(x,y)]
            # grain only inside a run of the same role (keep 1 px lines and edges crisp)
            nb=[((x-1,y),(x+1,y)) if mode!='v' else ((x,y-1),(x,y+1))][0]
            interior=all(role.get(n)==r for n in nb)
            o=g[y-fy][x] if interior else 0
            if r=='dye':
                rp,bl=ramp_for(c)
                k=max(-3,min(3,round((L(c)/max(bl,1)-1)/0.16)+o)); O[x,y]=rp[k+3]+(P[x,y][3],)
            else:
                t=max(0,min(len(GREY)-1,grey_tone(c)+o)); O[x,y]=GREY[t]+(P[x,y][3],)
    out.save(dst)
