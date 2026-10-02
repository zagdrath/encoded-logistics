# The Minecraft-style shading pass (tools/mcpass.py) over the cable textures, in place. tools/export_cables.py runs it
# right after writing the base textures; don't run it on its own, since a second pass over already-shaded textures
# changes them again. Deterministic: the grain is seeded from each file name, and a glow overlay gets the same seed as
# its base so the grain matches pixel for pixel. (The Drive Bay's textures ship already shaded, in
# textures/block/drive_bay/.)
import os, glob, shutil, zlib, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from mcpass import repaint
BLOCK='src/main/resources/assets/encodedlogistics/textures/block/'
seed=lambda p: zlib.crc32(os.path.basename(p).encode())&0xffff
MODE={'h':'u','v':'v','j':'c','f':'c'}

def run_cables():
    n=0
    for tier in ('normal','dense'):
        d=BLOCK+f'cable/{tier}/'
        for base in sorted(glob.glob(d+'*.png')):
            name=os.path.basename(base)
            if name.endswith('_glow.png'): continue
            sheet=name[:-4].split('_')[-1]; mode=MODE[sheet]
            s=seed(name)
            g=base[:-4]+'_glow.png'
            # Read the glow's mask before the base is repainted.
            glow_mask=Image.open(g).convert('RGBA') if os.path.exists(g) and not os.path.exists(g+'.mcmeta') else None
            repaint(base,base,mode,s); n+=1
            if os.path.exists(g+'.mcmeta'):
                repaint(g,g,mode,s)              # the neutral cycle: every frame, same grain
            elif glow_mask is not None:
                b=Image.open(base).convert('RGBA'); o=Image.new('RGBA',glow_mask.size,(0,0,0,0))
                for y in range(glow_mask.height):
                    for x in range(glow_mask.width):
                        if glow_mask.getpixel((x,y))[3]>0: o.putpixel((x,y),b.getpixel((x,y)))
                o.save(g)
            n+=1
    print(n,'textures repainted')

if __name__=='__main__':
    sys.exit('Run tools/export_cables.py instead: it writes the base textures and then runs this pass.')
