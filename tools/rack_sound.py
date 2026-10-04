# Synthesised door sounds (mono 44.1 kHz) -> .ogg via ffmpeg: sheet-metal latch click + swing.
import numpy as np, subprocess, os, shutil, tempfile, wave
SR=44100
def env(n,a,d): t=np.arange(n)/SR; return np.minimum(1,t/a)*np.exp(-t/d)
def click(rng,dur=0.18,f=(2300,3900,1250)):
    n=int(SR*dur); t=np.arange(n)/SR
    burst=rng.normal(0,1,n)*env(n,0.0005,0.006)                       # latch transient
    ring=sum(np.sin(2*np.pi*fr*t+rng.uniform(0,6))*w for fr,w in zip(f,(0.6,0.35,0.5)))*env(n,0.001,0.05)  # sheet-metal ring
    return 0.9*burst+0.5*ring
def swing(rng,dur=0.42,rising=True):
    n=int(SR*dur); t=np.arange(n)/SR; noise=rng.normal(0,1,n)
    k=np.ones(60)/60; air=np.convolve(noise,k,'same')*8                # low-passed air movement
    shape=np.sin(np.pi*t/dur)**2; creak=np.sin(2*np.pi*(180+120*(t/dur if rising else 1-t/dur))*t)*0.05
    return (air*0.12+creak)*shape
def write(name,sig,out):
    sig=sig/np.max(np.abs(sig))*0.85; pcm=(sig*32767).astype(np.int16)
    os.makedirs(out,exist_ok=True)
    if shutil.which('ffmpeg') is None:
        import soundfile                                    # no ffmpeg: libsndfile's own Vorbis encoder
        soundfile.write(f'{out}/{name}.ogg',pcm/32767.0,SR,format='OGG',subtype='VORBIS')
        return
    wav=os.path.join(tempfile.gettempdir(),f'{name}.wav')
    with wave.open(wav,'w') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    os.makedirs(out,exist_ok=True)
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',wav,'-c:a','libvorbis','-q:a','4',f'{out}/{name}.ogg'],check=True)
def make(out):
    for i in range(2):
        rng=np.random.default_rng(10+i)
        op=np.concatenate([click(rng),swing(rng,rising=True)])
        rng=np.random.default_rng(20+i)
        cl=np.concatenate([swing(rng,rising=False)[:int(SR*0.32)],click(rng,0.22,(1900,3300,1050))*1.1])
        write(f'rack_door_open{i+1}',op,out); write(f'rack_door_close{i+1}',cl,out)
