# Pads every block texture whose (frame) size isn't a multiple of 16 to the next multiple of 16, transparent on the right
# and bottom (animated: per frame), and rescales the UVs of every model face that uses it, so no one sprite caps the block
# atlas's mip levels (the atlas takes the smallest power of two dividing any sprite's frame size; 16 = mip level 4).
# Idempotent: textures already a multiple of 16 are left alone. Run after any exporter that rewrites these textures.
import json, glob, os
from PIL import Image
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'encodedlogistics'))

def up16(n):
    return -(-n // 16) * 16

def num(x):
    x = round(x, 4)
    return int(x) if x == int(x) else x

scale = {}  # texture id -> (sx, sy)
for path in sorted(glob.glob('textures/block/**/*.png', recursive=True)):
    im = Image.open(path).convert('RGBA')
    meta_path = path + '.mcmeta'
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else None
    anim = meta.get('animation') if meta else None
    if anim and ('width' in anim or 'height' in anim):
        fw, fh = anim.get('width', im.width), anim.get('height', im.height)
    elif anim:
        fw = fh = min(im.width, im.height)
    else:
        fw, fh = im.width, im.height
    frames_x, frames_y = im.width // fw, im.height // fh
    W, H = up16(fw), up16(fh)
    if (W, H) == (fw, fh):
        continue
    out = Image.new('RGBA', (W * frames_x, H * frames_y), (0, 0, 0, 0))
    for fy in range(frames_y):
        for fx in range(frames_x):
            out.alpha_composite(im.crop((fx * fw, fy * fh, fx * fw + fw, fy * fh + fh)), (fx * W, fy * H))
    out.save(path)
    if anim and ('width' in anim or 'height' in anim):
        anim['width'], anim['height'] = W, H
        open(meta_path, 'w').write(json.dumps(meta, indent=2) + '\n')
    tid = 'encodedlogistics:' + path.replace('\\', '/')[len('textures/'):-4]
    scale[tid] = (fw / W, fh / H)
    print('padded', path, (fw, fh), '->', (W, H), 'x', frames_x * frames_y)

for path in sorted(glob.glob('models/**/*.json', recursive=True)):
    m = json.load(open(path))
    tex = m.get('textures', {})
    changed = False
    for e in m.get('elements', []):
        for face in e['faces'].values():
            tid = tex.get(face['texture'].lstrip('#'))
            if tid in scale:
                sx, sy = scale[tid]
                assert 'uv' in face, (path, e.get('name'))
                u0, v0, u1, v1 = face['uv']
                face['uv'] = [num(u0 * sx), num(v0 * sy), num(u1 * sx), num(v1 * sy)]
                changed = True
    if changed:
        open(path, 'w').write(json.dumps(m, indent=1) + '\n')
        print('rescaled', path)
