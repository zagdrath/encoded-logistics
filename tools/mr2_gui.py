# Green-screen control panels for the Midrange line (CRT grid, tall pixels; slots = phosphor frames at true GUI pixels).
import os, sys
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
import mid_gui as MG
from crt_screens import head, pad
def control_panel(items,tier,expansion=False):
    title='MIDRANGE CONTROL PANEL' if tier==1 else 'INTEGRATED SYSTEM CONTROL PANEL'
    L=head(title,'MRCTL' if tier==1 else 'IMCTL')
    thr,mx,bj=((2,128,2) if expansion else (1,64,1)) if tier==1 else (4,512,4)
    L+=['',
        '{n}  System status  . . :   {r} A6b2 {/r}  {b}RUNNING - job active',
        '{n}  Threads  . . . . . :   '+f'{thr}'+'         Max job . . :   '+f'{mx}'+'       Batch jobs . :   '+f'{bj}',
        '']
    if tier==1:
        L+=['{b}  Library diskette'+('s' if expansion else '')+'{n}                         {b}Current job','',
            '{n}                PRODLIB    5 of 8          {n}  Job 0007  Logic Die x16',
            '{n}                '+('TOOLLIB    3 of 8' if expansion else '                 ')+'          {n}  Step 2 of 3  [####......]  41%']
        sl=[(3,7,items['diskette_8in_written'],True)]+([(9,7,items['diskette_8in_written'],True)] if expansion else [])
        if expansion: L[1]=L[1]
    else:
        L+=['{b}  Diskette magazine{n}  (4 diskettes, 32 recipes)           {b}Current jobs','',
            '{n}                                         {n}  0007 Logic Die x16      41%',
            '{n}    PRODLIB  TOOLLIB  RACKLIB  (empty)   {n}  0011 Heatsink x32       12%']
        sl=[(3,7,items['diskette_magazine'],True)]+[(13+i*5,7,items['diskette_8in_written'] if i<3 else None,False) for i in range(4)]
    L+=['','{b}  Job queue{n}',
        '{b}  Job   Item                  Qty   Status',
        '{n}  0008  Copper Foil            32   Queued',
        '{n}  0009  Circuit Substrate      16   Queued',
        '{n}  0010  Heatsink                4   {d}Held - missing Ferrite','',
        '{n}  {r} IPL {/r}   {r} Hold queue {/r}   {r} Release {/r}']
    L=MG.finalize(L,'F3=Exit   F5=Refresh   F7=IPL   F10=Hold   F11=Release   F12=Cancel')
    return MG.compose(L,sl)
def printer(items):
    img=MG.line_printer(items); return img
