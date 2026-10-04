# Synthesised sounds for batch 3 (mono 44.1 kHz -> .ogg): console rail slide + hinge click, picker servo + tape load.
import numpy as np, sys, os
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from rack_sound import SR, env, click, write
def slide(rng,dur=0.42,out=True):
    n=int(SR*dur); t=np.arange(n)/SR
    roll=np.convolve(rng.normal(0,1,n),np.ones(30)/30,'same')*4*np.sin(np.pi*t/dur)**1.5     # ball-bearing rail rumble
    ticks=np.zeros(n)
    for k in range(int(dur*38)): ticks[int(k*SR/38+rng.integers(0,200))%n]+=rng.uniform(0.3,0.7)  # bearing ticks
    ticks=np.convolve(ticks,np.exp(-np.arange(200)/25),'same')
    stop=click(rng,0.12,(1600,2900,900))*0.7
    return np.concatenate([roll*0.25+ticks*0.35,stop]) if out else np.concatenate([stop[:200]*0,roll*0.25+ticks*0.35,stop])
def hinge(rng): return click(rng,0.16,(2600,4200,1400))
def servo(rng,dur=0.5):
    n=int(SR*dur); t=np.arange(n)/SR; f=520+380*np.sin(np.pi*t/dur)
    w=np.sin(2*np.pi*np.cumsum(f)/SR)*0.25+np.sin(4*np.pi*np.cumsum(f)/SR)*0.08
    return w*np.minimum(1,t/0.03)*np.minimum(1,(dur-t)/0.05)
def clunk(rng):
    n=int(SR*0.22); t=np.arange(n)/SR
    return np.sin(2*np.pi*140*t)*env(n,0.001,0.04)*0.9+click(rng,0.22,(900,1700,600))*0.4
def make(out):
    for i in range(2):
        r=np.random.default_rng(50+i)
        write(f'console_slide_out{i+1}',slide(r,out=True),out); write(f'console_slide_in{i+1}',slide(r,out=False),out)
        write(f'console_hinge{i+1}',hinge(r),out); write(f'picker_move{i+1}',servo(r),out); write(f'tape_load{i+1}',clunk(r),out)
if __name__=='__main__': make(sys.argv[1])
