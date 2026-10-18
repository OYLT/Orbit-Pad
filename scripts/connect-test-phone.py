"""Enter the receiver address using the native app's observed Android controls."""
import argparse
import pathlib
import re
import subprocess
import urllib.request
import json
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--address', required=True)
p.add_argument('--port', required=True, type=int)
a = p.parse_args()
assert re.fullmatch(r'[0-9.]+:[0-9]+', a.address)
adb = ['adb', '-s', a.serial]
root = pathlib.Path(__file__).resolve().parents[1]
file = root / 'artifacts/redmi-native-connect.xml'
def run(*args): return subprocess.run([*adb, *map(str, args)], check=True, capture_output=True)
def tree():
    run('shell', 'uiautomator', 'dump', '/sdcard/orbitpad-test-ui.xml')
    run('pull', '/sdcard/orbitpad-test-ui.xml', file)
    return ET.parse(file).getroot()
def tap(node):
    assert node.attrib['package'] == 'app.orbitpad.controller'
    x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.attrib['bounds']))
    run('shell','input','tap',(x1+x2)//2,(y1+y2)//2)
def field(index):
    hint = '192.168.1.100:3210' if index == 0 else '000000'
    for attempt in range(6):
        page = tree()
        nodes = [n for n in page.iter('node') if n.attrib.get('class') == 'android.widget.EditText' and n.attrib.get('package') == 'app.orbitpad.controller' and n.attrib.get('hint') == hint]
        if nodes: return nodes[0]
        assert any(n.attrib.get('class') == 'android.widget.ScrollView' and n.attrib.get('package') == 'app.orbitpad.controller' for n in page.iter('node')), 'Expected native connection page'
        bounds = next(n.attrib['bounds'] for n in page.iter('node') if n.attrib.get('class') == 'android.widget.ScrollView')
        x1,y1,x2,y2 = map(int,re.findall(r'\d+', bounds)); x=(x1+x2)//2
        start,end=(y1+(y2-y1)*.3,y1+(y2-y1)*.8) if index==0 else (y1+(y2-y1)*.8,y1+(y2-y1)*.3)
        run('shell','input','swipe',x,int(start),x,int(end),350)
    raise AssertionError('Connection input not found')
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
with opener.open(f'http://127.0.0.1:{a.port}/api/host') as response: code = json.load(response)['pairCode']
for index,value in [(0,a.address),(1,code)]:
    tap(field(index)); run('shell','input','keyevent','123'); run('shell','input','keyevent',*(['67']*40))
    run('shell','input','text',value); run('shell','input','keyevent','4')
buttons = []
for attempt in range(4):
    page=tree()
    buttons = [n for n in page.iter('node') if n.attrib.get('class') == 'android.widget.Button' and n.attrib.get('text','').startswith('连接并开始')]
    if buttons: break
    b = next(n.attrib['bounds'] for n in page.iter('node') if n.attrib.get('class')=='android.widget.ScrollView')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',b));x=(x1+x2)//2
    run('shell','input','swipe',x,int(y1+(y2-y1)*.8),x,int(y1+(y2-y1)*.3),350)
assert len(buttons) == 1
tap(buttons[0]); print('Submitted native connection form')
