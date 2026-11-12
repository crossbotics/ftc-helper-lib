#!/usr/bin/env python3
"""
Draws the Shot Tuner's field map for this season (docs/images/shot-tuner-test-spots.svg): the field from
the drivers' wall, the field parts from the season's field file, the goal, and the spots to test.

Why: the spots depend on the field. In BIOBUZZ the goal is near the middle, so the spots go toward the
corner, not straight back. Each new season: change the lines under THE SEASON (and the words), run it, and
change the season paragraph in docs/tuning/12-shot-tuner.md.

Where: a terminal, in the ftc-helper-lib folder.
    python3 tools/shot_spots.py
"""
import math, json
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
# THE SEASON: the field file, the goal (Tunables.Aim goalX, goalY), and the spots (inches from the goal, degrees).
FIELD = ROOT / "sim/fields/biobuzz-2026.json"
SEASON = "BIOBUZZ"
f=json.load(open(FIELD))
W=520; o=[]
def t(x,y,s,size=14,fill="#222",anchor="start",bold=False):
    b=' font-weight="bold"' if bold else ''
    o.append(f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size}" fill="{fill}" text-anchor="{anchor}"{b}>{s}</text>')
X0,Y0,PX=40,96,440
S=PX/144
P=lambda x,y:(X0+(x+72)*S, Y0+(72-y)*S)   # field inches (our view: x right, y away from our wall) -> pixels
t(16,26,"Where to test the Shot Tuner: " + SEASON,17,bold=True)
t(16,46,"Top view, from your drivers' wall. 1 tile = 24 in. The goal (your",13,"#555")
t(16,62,"HIVE's up CELL) is near the middle, so straight back you soon",13,"#555")
t(16,78,"reach the wall. Go out at an angle, toward your corner.",13,"#555")
o.append(f'<rect x="{X0}" y="{Y0}" width="{PX}" height="{PX}" fill="#f4f6f9" stroke="#333" stroke-width="2.5"/>')
for i in range(1,6):
    a=X0+i*24*S
    o.append(f'<line x1="{a:.1f}" y1="{Y0}" x2="{a:.1f}" y2="{Y0+PX}" stroke="#d5dae2"/>')
    o.append(f'<line x1="{X0}" y1="{Y0+i*24*S:.1f}" x2="{X0+PX}" y2="{Y0+i*24*S:.1f}" stroke="#d5dae2"/>')
def poly(pts,fill,stroke="none",extra=""):
    d=" ".join(f"{P(x,y)[0]:.1f},{P(x,y)[1]:.1f}" for x,y in pts)
    o.append(f'<polygon points="{d}" fill="{fill}" stroke="{stroke}" {extra}/>')
for e in f["elements"]:
    n=e["name"]
    if n in ("Red HIVE","Blue HIVE"):
        poly(e["polygon"],"#e8d9a8","#b89a3e",'stroke-width="1.5" fill-opacity="0.7"')
    elif n.startswith("HIVE frame base") or (n.startswith("FLOWER") and "lip" not in n and "back" not in n):
        poly(e["polygon"],"#3b4148")
t(*P(-24,-8), "your HIVE",12,"#7a6420"); t(*P(-24,14), "other HIVE",12,"#7a6420")
gx,gy=13,-13
spots=[(30,-45),(42,-45),(54,-45),(66,-45)]
for i,(r,a) in enumerate(spots):
    x,y=gx+r*math.cos(math.radians(a)), gy+r*math.sin(math.radians(a))
    px,py=P(x,y)
    o.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="11" fill="#2d7a32"/>'); t(px,py+5,str(i+1),13,"#fff","middle",True)
    t(px-16,py+5,f"{r} in",12,"#2d7a32","end")
for x,y in ((gx,gy-42),(gx+42,gy)):
    px,py=P(x,y)
    o.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="9" fill="#fff" stroke="#1f4e9c" stroke-width="3"/>')
gpx,gpy=P(gx,gy)
o.append(f'<circle cx="{gpx:.1f}" cy="{gpy:.1f}" r="12" fill="#fff" stroke="#c0392b" stroke-width="4"/><circle cx="{gpx:.1f}" cy="{gpy:.1f}" r="4" fill="#c0392b"/>')
t(gpx+16,gpy-10,"goal",14,"#c0392b",bold=True)
mx,my=P(0,0)
o.append(f'<rect x="{mx-12:.1f}" y="{my-12:.1f}" width="24" height="24" rx="4" fill="none" stroke="#e07b00" stroke-width="3"/>')
o.append(f'<line x1="{mx:.1f}" y1="{my+1:.1f}" x2="{mx:.1f}" y2="{my-10:.1f}" stroke="#e07b00" stroke-width="3"/>')
t(mx-16,my+4,"odometry start",12,"#b35f00","end")
o.append(f'<rect x="{X0}" y="{Y0+PX}" width="{PX}" height="14" fill="#555"/>')
t(X0+PX/2,Y0+PX+11,"YOUR DRIVERS' WALL",11,"#fff","middle",True)
t(X0+PX+16,Y0+PX/2,"audience",12,"#555","middle")
o[-1]=o[-1].replace("<text ",f'<text transform="rotate(90 {X0+PX+16} {Y0+PX/2})" ')
y=Y0+PX+42
o.append(f'<circle cx="28" cy="{y-5}" r="10" fill="#2d7a32"/>'); t(28,y,"1",12,"#fff","middle",True)
t(46,y,"Spots 1 to 4: toward your corner, the nearest first. The farthest",14)
t(46,y+18,"spot is about 66 in from the goal, close to the corner.",14)
o.append(f'<circle cx="28" cy="{y+41}" r="8" fill="#fff" stroke="#1f4e9c" stroke-width="3"/>')
t(46,y+46,"Side check: the same distance (42 in) straight toward your",14)
t(46,y+64,"wall and toward the audience. Each in its own run (STOP, INIT).",14)
o.append(f'<rect x="20" y="{y+78}" width="18" height="12" fill="#3b4148"/>')
t(46,y+89,"The HIVE frame and the FLOWERs: robots cannot go there. Robots",14)
t(46,y+107,"fit under the HIVEs, but a shot from there is too steep.",14)
H=y+122
open(ROOT / "docs/images/shot-tuner-test-spots.svg","w").write(
 f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" font-family="Helvetica, Arial, sans-serif">\n<rect width="{W}" height="{H}" fill="#ffffff"/>\n'+"\n".join(o)+"\n</svg>\n")
