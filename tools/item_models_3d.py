# 3D item models for blocks smaller than a block (the Small Wireless Bridge, the Display Panel, the Wireless Ports, the
# Access Point): the block's model (its parts merged), centred in the item's 16 px space and shown in GUIs and frames at
# the size a full block shows, rather than the small flat sprite or the block-sized-but-small model they had.
# Run from the repository root: python tools/item_models_3d.py
import copy, json

A = 'src/main/resources/assets/encodedlogistics/'


def load(path):
    return json.load(open(A + 'models/' + path + '.json', encoding='utf-8'))


def merge(paths):
    textures, elements = {}, []
    for path in paths:
        m = load(path)
        # Texture variables prefixed per part, so two parts' #side don't clash.
        prefix = path.split('/')[-1] + '_'
        for key, value in m.get('textures', {}).items():
            if key != 'particle':
                textures[prefix + key] = value
        for e in m['elements']:
            e = copy.deepcopy(e)
            for face in e['faces'].values():
                face['texture'] = '#' + prefix + face['texture'][1:]
            elements.append(e)
        textures.setdefault('particle', m['textures'].get('particle'))
    return textures, elements


def centre(elements):
    lo = [min(min(e['from'][i], e['to'][i]) for e in elements) for i in range(3)]
    hi = [max(max(e['from'][i], e['to'][i]) for e in elements) for i in range(3)]
    shift = [8 - (lo[i] + hi[i]) / 2 for i in range(3)]
    for e in elements:
        e['from'] = [round(e['from'][i] + shift[i], 4) for i in range(3)]
        e['to'] = [round(e['to'][i] + shift[i], 4) for i in range(3)]
        if 'rotation' in e and 'origin' in e['rotation']:
            e['rotation']['origin'] = [round(e['rotation']['origin'][i] + shift[i], 4) for i in range(3)]
    return max(hi[i] - lo[i] for i in range(3))


def display(size, yaw):
    # A full block (16 px) shows at 0.625 in a slot; scale so this model's longest side shows as long, at most 1.
    s = round(min(1.0, 0.625 * 16 / size), 3)
    return {
        'gui': {'rotation': [30, yaw, 0], 'translation': [0, 0, 0], 'scale': [s, s, s]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [s * 0.4, s * 0.4, s * 0.4]},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [s * 0.8, s * 0.8, s * 0.8]},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [s * 1.6, s * 1.6, s * 1.6]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [s * 0.6, s * 0.6, s * 0.6]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [s * 0.64, s * 0.64, s * 0.64]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [s * 0.64, s * 0.64, s * 0.64]},
    }


# yaw: the slot's view: 225 shows a model's north (front) face, 45 its south (front of a model that faces south).
def write(item, parts, yaw=225):
    textures, elements = merge(parts)
    size = centre(elements)
    model = {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements, 'display': display(size, yaw)}
    first = load(parts[0])
    if 'render_type' in first:
        model['render_type'] = first['render_type']
    json.dump(model, open(A + 'models/item/%s.json' % item, 'w', newline='\n'), indent=1)
    json.dump({'model': {'type': 'minecraft:model', 'model': 'encodedlogistics:item/' + item}},
              open(A + 'items/%s.json' % item, 'w', newline='\n'), indent=1)


if __name__ == '__main__':
    write('small_wireless_bridge', ['block/small_wireless_bridge/small_wireless_bridge', 'block/small_wireless_bridge/led_linked'], 45)
    write('display_panel', ['block/display_panel/base', 'block/display_panel/bezel_top', 'block/display_panel/bezel_bottom',
                            'block/display_panel/bezel_left', 'block/display_panel/bezel_right'])
    write('wireless_ingress_port', ['block/wireless/wireless_ingress_port'], 45)
    write('wireless_egress_port', ['block/wireless/wireless_egress_port'], 45)
    write('access_point', ['block/wireless/access_point'])
    print('ok')
