# Item icons (16x16, vanilla-style: outline in the darkest step of the item's own material, light from the top-left).
from PIL import Image
from el_style import H, put
from style_kit import g
from rack_tex import b
from device_tex import RED, BLU, GRN, LCD, CU
def new(): return Image.new('RGBA',(16,16),(0,0,0,0))
def _l(a,c,t): return tuple(round(a[k]+(c[k]-a[k])*t) for k in range(3))
def smooth(im,ramp_fn,x0,y0,w,h,lo,hi):
    for y in range(y0,y0+h):
        for x in range(x0,x0+w):
            t=0.7*((y-y0)/max(1,h-1))+0.3*((x-x0)/max(1,w-1)); v=hi-(hi-lo)*t; k=int(v); put(im,x,y,_l(ramp_fn(k),ramp_fn(k+1),v-k))
def outline(im,col):
    src=im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x,y))[3]: continue
            if any(0<=x+dx<16 and 0<=y+dy<16 and src.getpixel((x+dx,y+dy))[3] for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))): put(im,x,y,col)
def rack_icon():
    """Tall cabinet in 3/4 view: mesh front with a header and handle, side panel with the seam, casters."""
    im=new()
    smooth(im,b,3,1,7,14,1.5,5.0); smooth(im,b,10,1,3,14,1.0,3.6)  # door and side, smoothly lit from the top-left
    for y in range(3,14):
        for x in range(4,9):
            if (x+y)%2: put(im,x,y,b(0))                        # mesh openings
    for y in range(1,15): put(im,10,y,b(4)); put(im,9,y,b(1))
    for x in range(10,13): put(im,x,8,b(1))
    put(im,4,7,g(6)); put(im,4,8,g(4))                          # handle
    for x in range(4,9): put(im,x,2,b(4))
    for x in (4,8,11): put(im,x,15,g(5))                        # casters
    outline(im,b(0)); return im
def device_icon(kind):
    """1U/2U unit in 3/4 view: top face, front strip with the device's signature detail, ears."""
    im=new(); top0,front0=(4,9) if kind!='ups' else (3,7)
    fh=3 if kind!='ups' else 6
    smooth(im,g,2,top0,12,front0-top0,4.0,6.8)                  # top face, smooth sheen
    smooth(im,g,2,front0,12,fh,2.2,3.8)                         # faceplate
    for y in range(front0,front0+fh): put(im,1,y,g(6)); put(im,14,y,g(5))   # ears
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
