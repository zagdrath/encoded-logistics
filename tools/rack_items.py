# Item icons (16x16, vanilla-style: outline in the darkest step of the item's own material, light from the top-left).
from PIL import Image
from el_style import H, put
from style_kit import g
from rack_tex import b
from device_tex import RED, BLU, GRN, LCD, CU
def new(): return Image.new('RGBA',(16,16),(0,0,0,0))
def outline(im,col):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,col)
def rack_icon():
    """Tall cabinet in 3/4 view: mesh front with a header and handle, side panel with the seam, casters."""
    im=new()
    for y in range(1,15):
        for x in range(3,10):                                   # front door
            edge=x in (3,9) or y in (1,14) or y==2
            put(im,x,y,b(5) if (x==3 or y==1) else b(2) if edge else (b(1) if (x+y)%2 else b(3)))
        for x in range(10,13):                                  # side panel
            put(im,x,y,b(4) if x==10 else b(3) if y!=8 else b(1))
    put(im,4,7,g(6)); put(im,4,8,g(4))                          # handle
    for x in range(4,9): put(im,x,2,b(4))
    for x in (4,8,11): put(im,x,15,g(5))                        # casters
    outline(im,b(0)); return im
def device_icon(kind):
    """1U/2U unit in 3/4 view: top face, front strip with the device's signature detail, ears."""
    im=new(); top0,front0=(4,9) if kind!='ups' else (3,7)
    fh=3 if kind!='ups' else 6
    for y in range(top0,front0):
        for x in range(2,14): put(im,x,y,g(6) if y==top0 else g(5) if x<8 else g(4))
    for y in range(front0,front0+fh):
        for x in range(1,15): put(im,x,y,g(3) if 2<=x<=13 else g(6))
    if kind=='firewall':
        for y in range(front0,front0+fh): put(im,3,y,RED[3])
        for x in range(5,13,2): put(im,x,front0+1,g(0)); put(im,x,front0,CU[4])
        put(im,13,front0+1,GRN[4])
    elif kind=='router':
        for y in range(front0,front0+fh): put(im,3,y,BLU[3])
        for x in (5,7): put(im,x,front0+1,g(0))
        for x in range(9,13): put(im,x,front0+1,g(7) if x%2 else g(1))
        put(im,13,front0+1,GRN[4])
    else:
        for y in range(front0+1,front0+4):
            for x in range(3,8): put(im,x,y,LCD[3] if y>front0+1 else LCD[4])
        for x in range(9,13): put(im,x,front0+2,GRN[4] if x<12 else g(1))
        for y in range(front0+1,front0+5): put(im,13,y,g(1) if y%2 else g(2))
    outline(im,g(0)); return im
ICONS={'server_rack':rack_icon,'firewall':lambda: device_icon('firewall'),'router':lambda: device_icon('router'),'ups':lambda: device_icon('ups')}
