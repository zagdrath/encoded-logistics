# Closes the backs of the cable part models: every element (except glow overlays) gets a south face (z toward the
# cable core) if it has none, in the dark steel swatch of its #parts texture. On a cable the core hides most of them,
# but on a part host (a part mounted on a block face) nothing is behind the part, and an open back shows through - the
# ports' body ring and every stub end did. Run after any of the part exporters (p2_export, p3_export, p4_export);
# running it again changes nothing.
import glob, json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'encodedlogistics', 'models', 'part')
DARK = [4, 0, 6, 2]  # PU['dark'] in parts_p2.py; parts_steel has the same swatch layout


def close(path):
    with open(path) as f:
        model = json.load(f)
    if 'parts' not in model.get('textures', {}):
        return False
    changed = False
    for element in model.get('elements', []):
        if 'neoforge_data' in element or 'south' in element['faces']:
            continue
        element['faces']['south'] = {'texture': '#parts', 'uv': DARK}
        changed = True
    if changed:
        with open(path, 'w') as f:
            json.dump(model, f, indent=1)
            f.write('\n')
    return changed


if __name__ == '__main__':
    paths = sorted(glob.glob(os.path.join(ROOT, '**', '*.json'), recursive=True))
    print(f'closed {sum(close(p) for p in paths)} of {len(paths)} part models')
