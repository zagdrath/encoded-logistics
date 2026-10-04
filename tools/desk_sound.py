# Synthesised sounds (mono 44.1 kHz -> .ogg): CRT hum (seamless loop), degauss thunk, keyboard clacks.
import numpy as np, sys, os
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from rack_sound import SR, env, write
def hum(dur=2.0):
    """60 Hz mains hum + harmonics and a very faint 15.7 kHz flyback whine; whole cycles so it loops without a click."""
    n=int(SR*dur); t=np.arange(n)/SR
    s=0.5*np.sin(2*np.pi*60*t)+0.25*np.sin(2*np.pi*120*t)+0.12*np.sin(2*np.pi*180*t)+0.03*np.sin(2*np.pi*15734*t)
    s+=0.02*np.random.default_rng(1).normal(0,1,n)
    return s*0.5
def degauss():
    """Power-on degauss: a deep thunk, then a decaying 60 Hz warble as the coil field collapses."""
    n=int(SR*1.1); t=np.arange(n)/SR
    thunk=np.sin(2*np.pi*55*t)*np.exp(-t/0.06)*1.0+np.random.default_rng(2).normal(0,1,n)*np.exp(-t/0.01)*0.4
    warble=np.sin(2*np.pi*60*t)*np.sin(2*np.pi*7*t)*np.exp(-t/0.35)*0.6
    return thunk+warble
def clack(seed):
    rng=np.random.default_rng(seed); n=int(SR*0.09); t=np.arange(n)/SR
    f=rng.uniform(1800,2600)
    return rng.normal(0,1,n)*np.exp(-t/0.004)*0.8+np.sin(2*np.pi*f*t)*np.exp(-t/0.012)*0.5+np.sin(2*np.pi*420*t)*np.exp(-t/0.02)*0.3
def make(out):
    write('crt_hum',hum(),out); write('crt_degauss',degauss(),out)
    for i in range(4): write(f'key_clack{i+1}',clack(10+i),out)
if __name__=='__main__': make(sys.argv[1])
