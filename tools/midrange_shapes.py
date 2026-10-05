# The Midrange line's shapes (data/encodedlogistics/shapes/midrange.json), from boxes that follow each model (px,
# from the master block's corner, the model facing north; a model may run past its block into the footprint's
# others, and overhang it). Slopes (the Disk Drive's front, the Line Printer's paper and basket) are steps.
#   "outline": per block (and variant), its whole model's boxes: what's selected and outlined, one shape over the
#              whole footprint;
#   "collision": per block, per footprint block ("x,y,z" offsets), the boxes clipped to that block, in its own px.
# Run from the repo root: python tools/midrange_shapes.py
import json, os

FOOTPRINTS = {
    'midrange_system': [(0, 0, 0)],
    'expansion_cabinet[attached=none]': [(0, 0, 0)],
    'expansion_cabinet[attached=neg]': [(0, 0, 0)],
    'expansion_cabinet[attached=pos]': [(0, 0, 0)],
    'integrated_midrange': [(-1, 0, 0), (0, 0, 0), (1, 0, 0), (-1, 1, 0), (0, 1, 0)],
    'keypunch': [(0, 0, 0), (0, 1, 0)],
    'card_reader': [(0, 0, 0)],
    'line_printer': [(0, 0, 0), (0, 1, 0)],
    'disk_drive': [(0, 0, 0)],
    'tape_drive': [(0, 0, 0), (0, 1, 0), (0, 2, 0)],
}


def cabinet(x0):
    # The Expansion Cabinet: body, plinth, cap; x0 its left edge.
    return [[x0, 1, 4, x0 + 10, 13, 16], [x0 + 0.5, 0, 4.5, x0 + 9.5, 1, 15.5], [x0, 13, 5, x0 + 10, 15, 16]]


BOXES = {
    'midrange_system': [[-1, 1, 4, 17, 13, 16], [-0.5, 0, 4.5, 16.5, 1, 15.5], [8, 13, 5, 17, 16, 16], [-1, 13, 6, 6, 16, 16],
                        [6, 13, 6, 8, 13.5, 16]],
    'expansion_cabinet[attached=none]': cabinet(3),
    'expansion_cabinet[attached=neg]': cabinet(1),
    'expansion_cabinet[attached=pos]': cabinet(5),
    'integrated_midrange': [[8, 1, 4, 22, 13, 16], [-6, 1, 4, 8, 13, 16], [-5.5, 0, 4.5, 21.5, 1, 15.5], [10, 13, 6, 20, 16, 14],
                            [-5, 13, 9, 8, 20, 16], [-4, 13, 4.5, 7, 14, 8.5]],
    'keypunch': [[0, 11, 4, 16, 12, 16], [8, 1, 6, 15, 11, 15], [0.5, 0, 4.5, 1.5, 11, 5.5], [0.5, 0, 14.5, 1.5, 11, 15.5],
                 [1, 12, 10, 15, 16, 16], [1.5, 16, 11, 5.5, 18, 15], [10.5, 16, 11, 14.5, 17, 15], [1, 12, 4.5, 8, 13, 8.5]],
    'card_reader': [[2, 0, 5, 14, 9, 16], [1, 9, 4, 15, 11, 16], [2, 11, 7, 7, 15, 14], [9, 11, 7, 14, 12, 14]],
    'line_printer': [[-2, 0, 4, 18, 14, 16], [13, 14, 6, 16, 16, 11], [0, 14, 6, 3, 16, 11], [18, 10.5, 9, 19, 12.5, 11],
                     # The paper leaning back out of its slot, in steps.
                     [3, 14, 8.3, 13, 16, 9.3], [3, 16, 9.1, 13, 18, 10.1], [3, 18, 9.9, 13, 20, 10.9], [3, 20, 10.7, 13, 21.4, 11.6],
                     # The wire basket.
                     [3, 14, 13, 13.3, 19.8, 15.3]],
    'disk_drive': [[1, 0.5, 4, 15, 11, 16], [1, 11, 6.1, 15, 16, 16],
                   # The sloped front, in steps (its top edge at z 6.07).
                   [1, 11, 4, 15, 12.25, 6.1], [1, 12.25, 4.5, 15, 13.5, 6.1], [1, 13.5, 5.0, 15, 14.75, 6.1], [1, 14.75, 5.6, 15, 16, 6.1]],
    'tape_drive': [[-1, 0.5, 1, 17, 40, 15]],
}


def clip(box, offset):
    ox, oy, oz = offset[0] * 16, offset[1] * 16, offset[2] * 16
    x1, y1, z1 = max(box[0] - ox, 0), max(box[1] - oy, 0), max(box[2] - oz, 0)
    x2, y2, z2 = min(box[3] - ox, 16), min(box[4] - oy, 16), min(box[5] - oz, 16)
    return [x1, y1, z1, x2, y2, z2] if x1 < x2 and y1 < y2 and z1 < z2 else None


def main():
    out = {'outline': {}, 'collision': {}}
    for key, boxes in BOXES.items():
        out['outline'][key] = boxes
        blocks = {}
        for offset in FOOTPRINTS[key]:
            clipped = [b for b in (clip(box, offset) for box in boxes) if b]
            blocks['%d,%d,%d' % offset] = clipped
        out['collision'][key] = blocks
    path = os.path.join('src', 'main', 'resources', 'data', 'encodedlogistics', 'shapes', 'midrange.json')
    with open(path, 'w', newline='\n') as f:
        json.dump(out, f, indent=1)
        f.write('\n')
    print('wrote', path)


if __name__ == '__main__':
    main()
