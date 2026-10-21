#!/usr/bin/env python3
"""Draws docs/images/biobuzz-field-map.svg (lesson 7) and docs/images/biobuzz-pedro-path.svg (lesson 8): the BIOBUZZ field from above, in the library's frame as seen
from the RED drivers' wall (x right, y away, inches; heading 0 = away, counterclockwise +), with the
6 x 6 tiles, the field parts the simulator knows (sim/fields/biobuzz-2026.json) and lesson 7's three
points (lesson 8: the Pedro example's path instead). From the BLUE wall the numbers are the same: BIOBUZZ's field is turned half a turn.

    python3 tools/field_map.py          (writes the file)
    python3 tools/field_map.py --check  (exit 1 if the file is out of date)
"""
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIELD = os.path.join(ROOT, "sim", "fields", "biobuzz-2026.json")
OUT = os.path.join(ROOT, "docs", "images", "biobuzz-field-map.svg")
OUT_PEDRO = os.path.join(ROOT, "docs", "images", "biobuzz-pedro-path.svg")

S = 5.0          # pixels per inch
M = 60           # margin, pixels
HALF = 72.0      # the field's half width, inches


def px(x, y):
    return (M + (x + HALF) * S, M + (HALF - y) * S)


def poly(points, fill, stroke="none", opacity=1.0):
    d = " ".join("%.1f,%.1f" % px(x, y) for x, y in points)
    return '<polygon points="%s" fill="%s" stroke="%s" fill-opacity="%.2f"/>' % (d, fill, stroke, opacity)


def arrow(x, y, heading_deg, color, label, dx=6, dy=-6):
    # Heading 0 points away (+y), counterclockwise +: the direction is (-sin h, cos h).
    h = math.radians(heading_deg)
    tx, ty = x - math.sin(h) * 9, y + math.cos(h) * 9
    x0, y0 = px(x, y)
    x1, y1 = px(tx, ty)
    out = ['<circle cx="%.1f" cy="%.1f" r="7" fill="%s"/>' % (x0, y0, color),
           '<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="%s" stroke-width="4" marker-end="url(#head)"/>'
           % (x0, y0, x1, y1, color),
           '<text x="%.1f" y="%.1f" font-size="15" font-weight="600" fill="%s">%s</text>'
           % (x0 + dx * S / 2, y0 + dy * S / 2, color, label)]
    return "\n".join(out)


def bezier(pts, n=40):
    # Points along a Bezier curve (de Casteljau), for drawing.
    out = []
    for i in range(n + 1):
        t = i / n
        q = list(pts)
        while len(q) > 1:
            q = [(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t) for a, b in zip(q, q[1:])]
        out.append(q[0])
    return out


def polyline(points, color, width=4, dash=None):
    d = " ".join("%.1f,%.1f" % px(x, y) for x, y in points)
    return '<polyline points="%s" fill="none" stroke="%s" stroke-width="%d"%s/>' % (
        d, color, width, ' stroke-dasharray="%s"' % dash if dash else "")


def build(pedro=False):
    f = json.load(open(FIELD))
    w = h = int(2 * M + 2 * HALF * S)
    out = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" '
           'font-family="system-ui, sans-serif">' % (w, h + 40, w, h + 40),
           '<defs><marker id="head" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="4" markerHeight="4" '
           'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="context-stroke"/></marker></defs>',
           '<rect width="100%" height="100%" fill="#ffffff"/>',
           poly([(-HALF, -HALF), (HALF, -HALF), (HALF, HALF), (-HALF, HALF)], "#e9ebee", "#555")]
    # Tiles: 24 in, lines every 24 in, labelled.
    for v in range(-72, 73, 24):
        x0, y0 = px(v, -HALF)
        x1, y1 = px(v, HALF)
        out.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="#b8bcc4" stroke-width="1"/>' % (x0, y0, x1, y1))
        x0, y0 = px(-HALF, v)
        x1, y1 = px(HALF, v)
        out.append('<line x1="%.1f" y1="%.1f" x2="%.1f" y2="%.1f" stroke="#b8bcc4" stroke-width="1"/>' % (x0, y0, x1, y1))
        lx, ly = px(v, -HALF)
        out.append('<text x="%.1f" y="%.1f" font-size="13" text-anchor="middle" fill="#333">%d</text>' % (lx, ly + 18, v))
        lx, ly = px(-HALF, v)
        out.append('<text x="%.1f" y="%.1f" font-size="13" text-anchor="end" fill="#333">%d</text>' % (lx - 6, ly + 4, v))
    # Field parts (solids and zones), clipped to the field.
    for e in f.get("elements", []):
        kind = e.get("kind")
        if kind not in ("solid", "zone", "overhead"):
            continue
        if any(abs(x) > HALF + 1 or abs(y) > HALF + 1 for x, y in e["polygon"]):
            continue
        name = e["name"]
        if name.endswith(" lip") or name.endswith(" back"):
            continue
        opacity = 0.35 if kind == "zone" else (0.30 if kind == "overhead" else 0.9)
        out.append(poly(e["polygon"], e.get("color", "#888"), "none", opacity))
    # Labels of the main parts.
    labels = [(0, -20, "HIVE (red)"), (0, 20, "HIVE (blue)"), (23, -63, "FLOWER"), (-68, -23, "FLOWER"),
              (-35, -64, "red loading zone")]
    for x, y, t in labels:
        lx, ly = px(x, y)
        out.append('<text x="%.1f" y="%.1f" font-size="13" text-anchor="middle" fill="#222">%s</text>' % (lx, ly, t))
    if pedro:
        # Lesson 8: pedrobot/autos/CurveToTheGoal.java. Part 1 a line, part 2 a curve; then driveTo park.
        out.append(polyline([(-56, -61), (-48, -50)], "#1b5fa8"))
        out.append(polyline(bezier([(-48, -50), (-44, -36), (-30, -30)]), "#1b5fa8"))
        out.append(polyline([(-48, -50), (-44, -36), (-30, -30)], "#1b5fa8", 1, "4 4"))
        cx, cy = px(-44, -36)
        out.append('<circle cx="%.1f" cy="%.1f" r="5" fill="none" stroke="#1b5fa8" stroke-width="2"/>' % (cx, cy))
        out.append('<text x="%.1f" y="%.1f" font-size="13" fill="#1b5fa8">control point (-44, -36)</text>' % (cx + 14, cy + 30))
        jx, jy = px(-48, -50)
        out.append('<circle cx="%.1f" cy="%.1f" r="4" fill="#1b5fa8"/>' % (jx, jy))
        out.append('<text x="%.1f" y="%.1f" font-size="13" fill="#1b5fa8">part 1 ends, part 2 starts (-48, -50)</text>' % (jx + 8, jy + 14))
        out.append(polyline([(-30, -30), (-56, -36)], "#5b2a86", 2, "6 4"))
        out.append(arrow(-56, -61, 0, "#1b7f3b", "start (-56, -61), heading 0"))
        out.append(arrow(-30, -30, 45, "#c05600", "goal (-30, -30), heading 45, hold", dx=4, dy=8))
        out.append(arrow(-56, -36, 0, "#5b2a86", "park (-56, -36): driveTo", dx=-30, dy=-12))
    else:
        # Lesson 7's points (odometrybot/autos/ToTheGoal.java and Tunables.Auto).
        out.append(arrow(-56, -61, 0, "#1b7f3b", "start (-56, -61), heading 0"))
        out.append(arrow(-30, -30, 45, "#c05600", "goal (-30, -30), heading 45", dx=4, dy=-8))
        out.append(arrow(-56, -36, 0, "#5b2a86", "park (-56, -36), heading 0", dx=4, dy=-4))
    # The axes and the drivers.
    ox, oy = px(0, 0)
    out.append('<circle cx="%.1f" cy="%.1f" r="4" fill="#000"/>' % (ox, oy))
    out.append('<text x="%.1f" y="%.1f" font-size="13" fill="#000">(0, 0)</text>' % (ox + 6, oy - 6))
    bx, by = px(0, -HALF)
    out.append('<text x="%.1f" y="%.1f" font-size="15" font-weight="600" text-anchor="middle" fill="#c8322a">'
               'your drivers stand here (this wall): x right, y away, heading 0 = facing away, + = left (counterclockwise)</text>'
               % (bx, by + 40))
    out.append('</svg>')
    return "\n".join(out) + "\n"


def main():
    files = [(OUT, build()), (OUT_PEDRO, build(pedro=True))]
    if "--check" in sys.argv:
        ok = all(os.path.exists(f) and open(f).read() == t for f, t in files)
        print("Field maps are up to date." if ok else "Field maps are out of date: run python3 tools/field_map.py")
        sys.exit(0 if ok else 1)
    for f, t in files:
        os.makedirs(os.path.dirname(f), exist_ok=True)
        open(f, "w").write(t)
        print("wrote " + os.path.relpath(f, ROOT))


if __name__ == "__main__":
    main()
