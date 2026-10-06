# Enforce Minecraft-size pixels: every face samples exactly its own size in texels (1 texel per model px). A face whose uv
# region is bigger or smaller than the face is re-mapped to a region of the face's size, keeping the region's start (clamped).
import json,glob,sys
from PIL import Image
A=sys.argv[1] if len(sys.argv)>1 else 'src/main/resources/assets/encodedlogistics/'
def dims(e,d):
    a,b=e['from'],e['to']
    return {'north':(b[0]-a[0],b[1]-a[1]),'south':(b[0]-a[0],b[1]-a[1]),'east':(b[2]-a[2],b[1]-a[1]),'west':(b[2]-a[2],b[1]-a[1]),'up':(b[0]-a[0],b[2]-a[2]),'down':(b[0]-a[0],b[2]-a[2])}[d]
def audit(fix):
    bad=0
    for f in glob.glob(A+'models/block/**/*.json',recursive=True):
        m=json.load(open(f)); ch=False
        for e in m.get('elements',[]):
            for d,fc in e['faces'].items():
                t=m['textures'][fc['texture'][1:]].split(':')[1]; W,Hh=Image.open(A+'textures/'+t+'.png').size
                import os
                if os.path.exists(A+'textures/'+t+'.png.mcmeta') and Hh>W: Hh=W                # an animation strip: one frame
                sx,sy=W/16,Hh/16                                                  # texels per uv unit
                u=fc['uv']; fw,fh=dims(e,d)
                if fw<0.02 or fh<0.02: continue
                tw=abs(u[2]-u[0])*sx; th=abs(u[3]-u[1])*sy
                if abs(tw-fw)>0.51 or abs(th-fh)>0.51:
                    bad+=1
                    if fix:
                        uw,uh=fw/sx,fh/sy; u0=min(min(u[0],u[2]),16-uw); v0=min(min(u[1],u[3]),16-uh)
                        fc['uv']=[round(u0,4),round(v0,4),round(u0+uw,4),round(v0+uh,4)]; ch=True
        if ch: json.dump(m,open(f,'w'),indent=1)
    return bad
if __name__=='__main__':
    print('mismatched faces before:',audit(True),' after:',audit(False))
