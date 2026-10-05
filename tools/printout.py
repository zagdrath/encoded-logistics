# Printout (line-printer output): continuous-form green-bar paper, tractor-feed sprocket strips, fold perforations,
# text in the terminal font sheet (6 x 10) in ribbon ink. Page canvas 384 x 512 px: 56 columns x 48 lines.
from PIL import Image
from w2_tex import S, H, outline
FONT=Image.open('/home/claude/elrepo/src/main/resources/assets/encodedlogistics/textures/font/terminal.png').convert('RGBA')
EXTRA='\u00ac\u2500\u2502\u250c\u2510\u2514\u2518\u251c\u2524\u252c\u2534\u253c'
W,Hh=384,512; MARGIN=24; TX=MARGIN+8; TY=16; COLS=56; LINES=48; LH=10
PAPER=H('#F4F2E8'); BAR=H('#D8EBCF'); BAR_EDGE=H('#C6DFBA'); STRIP=H('#ECE9DC'); HOLE=H('#B9B4A2'); HOLE_IN=H('#8E8A7C')
PERF=H('#C9C5B4'); INK=H('#2C3036'); INK_LIGHT=H('#5A606A'); RED_INK=H('#9E2A22')
def base():
    """Paper: green bars 3 lines tall every 6 lines, tractor strips (24 px) with sprocket holes every 16 px, perforation
       lines between strip and body, fold perforation at top and bottom edges."""
    im=Image.new('RGB',(W,Hh),PAPER); p=im.load()
    for k in range(0,LINES,6):
        y0=TY+k*LH
        for y in range(y0,min(Hh,y0+3*LH)):
            for x in range(MARGIN,W-MARGIN): p[x,y]=BAR
        for x in range(MARGIN,W-MARGIN): p[x,y0]=BAR_EDGE; 
    for x0 in (0,W-MARGIN):
        for y in range(Hh):
            for x in range(x0,x0+MARGIN): p[x,y]=STRIP
        cx=x0+MARGIN//2
        for cy in range(8,Hh,16):
            for y in range(cy-4,cy+5):
                for x in range(cx-4,cx+5):
                    d=(x-cx)**2+(y-cy)**2
                    if d<=16: p[x,y]=HOLE_IN if (x-cx)+(y-cy)<0 else HOLE           # punched hole, shaded upper-left
    for y in range(0,Hh,3):
        p[MARGIN-1,y]=PERF; p[W-MARGIN,y]=PERF
    for x in range(0,W,3): p[x,0]=PERF; p[x,Hh-1]=PERF
    return im
def gi(ch): return ord(ch)-32 if 32<=ord(ch)<128 else (96+EXTRA.index(ch) if ch in EXTRA else 31)
def text(im,col,row,s,ink=INK):
    p=im.load()
    for i,ch in enumerate(s):
        if col+i>=COLS: break
        k=gi(ch); gx,gy=(k%16)*6,(k//16)*10; x0,y0=TX+(col+i)*6,TY+row*LH
        for yy in range(10):
            for xx in range(6):
                if FONT.getpixel((gx+xx,gy+yy))[3]: p[x0+xx,y0+yy]=ink
def page(title,system,date,pg,pages,lines):
    """Header (rows 0-2), body from row 4, footer at row 47."""
    im=base()
    text(im,0,0,f'{system:<10}'+title.center(COLS-30)+f'{date:>20}')
    text(im,0,1,'ENCODED LOGISTICS'+f'PAGE {pg:>3} OF {pages:<3}'.rjust(COLS-17),INK_LIGHT)
    text(im,0,2,'\u2500'*COLS,INK_LIGHT)
    for i,(s,ink) in enumerate(lines[:LINES-6]): text(im,0,4+i,s,ink)
    text(im,0,LINES-1,('*** END OF REPORT ***' if pg==pages else '*** CONTINUED ***').center(COLS),INK_LIGHT)
    return im
def icon_sheet():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(2,14):
        for x in range(3,13): im.putpixel((x,y),(PAPER if (y//2)%2==0 else BAR)+(255,))
        im.putpixel((3,y),STRIP+(255,)); im.putpixel((12,y),STRIP+(255,))
        if y%3==0: im.putpixel((3,y),HOLE+(255,)); im.putpixel((12,y),HOLE+(255,))
    for y in (4,6,8,10):
        for x in range(5,5+(6 if y!=10 else 3)): im.putpixel((x,y),INK+(255,))
    return outline(im,S[3])
def icon_stack():
    """Fan-fold stack: the top sheet + zig-zag folds below (2 visible), lit top, shaded fold edges."""
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for k in range(3):
        y0=11+k
        for x in range(2+k%2,13+k%2): im.putpixel((x,y0),(PERF if k%2 else STRIP)+(255,))
    for y in range(3,11):
        for x in range(3,13): im.putpixel((x,y),(PAPER if (y//2)%2==0 else BAR)+(255,))
        im.putpixel((3,y),STRIP+(255,)); im.putpixel((12,y),STRIP+(255,))
        if y%3==0: im.putpixel((3,y),HOLE+(255,)); im.putpixel((12,y),HOLE+(255,))
    for y in (5,7,9):
        for x in range(5,11): im.putpixel((x,y),INK+(255,))
    return outline(im,S[3])
