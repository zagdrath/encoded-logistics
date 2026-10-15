# The modular chassis panel's background (textures/gui/rack/chassis.png): the rack panels' plain top panel, no list
# well or scroll track - ChassisPanel draws each tab's wells itself (the face's, sized to the chassis), so nothing sits
# under a scroll bar it doesn't have. Run: python tools/chassis_gui.py src/main/resources/assets/encodedlogistics/
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from rack_gui import top_panel
def chassis_gui(T):
    im=Image.new('RGBA',(256,256),(0,0,0,0)); p=im.load(); top_panel(p)
    im.save(T+'gui/rack/chassis.png')
if __name__=='__main__':
    A=sys.argv[1]; T=A+'textures/'; os.makedirs(T+'gui/rack',exist_ok=True)
    chassis_gui(T); print('chassis gui ok')
