# Pads the desk and chair block textures to multiples of 16 (animated: per frame) and rescales the models' UVs to match,
# so they don't cap the block atlas's mip levels. Run after desk_export.py.
import json, glob, os
from PIL import Image
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'encodedlogistics'))

def up16(n):
    return -(-n // 16) * 16

scale = {}  # texture id -> (sx, sy)
for path in glob.glob('textures/block/terminal_desk/*.png') + glob.glob('textures/block/swivel_chair/*.png'):
    im = Image.open(path).convert('RGBA')
    meta_path = path + '.mcmeta'
    meta = json.load(open(meta_path)) if os.path.exists(meta_path) else None
    anim = meta.get('animation') if meta else None
    fw = anim.get('width', im.width) if anim else im.width
    fh = anim.get('height', im.width) if anim else im.height
    frames = im.height // fh if anim else 1
    W, H = up16(fw), up16(fh)
    if (W, H) == (fw, fh):
        continue
    out = Image.new('RGBA', (W, H * frames), (0, 0, 0, 0))
    for f in range(frames):
        out.alpha_composite(im.crop((0, f * fh, fw, f * fh + fh)), (0, f * H))
    out.save(path)
    if anim:
        anim['width'], anim['height'] = W, H
        open(meta_path, 'w').write(json.dumps(meta, indent=2) + '\n')
    tid = 'encodedlogistics:' + path.replace('\\', '/')[len('textures/'):-4]
    scale[tid] = (fw / W, fh / H)
    print('padded', path, (fw, fh), '->', (W, H), 'x', frames)

for path in glob.glob('models/block/terminal_desk/*.json') + glob.glob('models/block/swivel_chair/*.json'):
    m = json.load(open(path))
    tex = m.get('textures', {})
    changed = False
    for e in m.get('elements', []):
        for face in e['faces'].values():
            ref = face['texture'].lstrip('#')
            tid = tex.get(ref)
            if tid in scale:
                sx, sy = scale[tid]
                assert 'uv' in face, (path, e.get('name'))
                u0, v0, u1, v1 = face['uv']
                face['uv'] = [round(u0 * sx, 4), round(v0 * sy, 4), round(u1 * sx, 4), round(v1 * sy, 4)]
                changed = True
    if changed:
        open(path, 'w').write(json.dumps(m, indent=1) + '\n')
        print('rescaled', path)
