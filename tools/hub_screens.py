#!/usr/bin/env python3
"""
Draws pictures of the Driver Hub's screen (the telemetry lines) for the docs, as SVG files.

Why: a student on a finder page sees the same lines here as on the Driver Hub, so they know where they are.
The lines below are copied from what the finders show (their show(...) methods): when a finder's screen
changes, change its lines here and run this again.

Where: a terminal, in the ftc-helper-lib folder.
    python3 tools/hub_screens.py

It writes docs/images/hub-<name>.svg. A line that starts with "*" is marked (yellow), so the page can
say "look at this line". The "*" is not drawn.

The step pictures are in tools/hub_steps/<page>.json, one file per docs/tuning page. Each picture has:
  button, where   the button to press, and the words before it ("Press on gamepad 1:")
  lines           the screen lines that matter ("..." = lines left out)
  order           "screen-first" (you read the screen, then press: menus, choose screens) or
                  "press-first" (the screen shows what the press did). Default: press-first with a
                  servo, else screen-first.
  pos, frm, note  optional: draws a servo seen from above, at pos (0..1), moved from frm, with a note
  stops           optional, with pos: where the hard stops are (default [0.10, 0.90]; [] = none)
  stop_label      optional: the word on each stop (default "frame")
  title           optional: the screen's title (default "Driver Hub")
"""
import json
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs/images"

SCREENS = {
    "servo-finder-done": ("Tune and check: Servo Finder", "running", [
        "--- Servo \"claw\"  (BACK = another) ---",
        "Hint : bigger = clockwise (seller); test: D-pad up",
        "Position : 0.770",
        "Step : 0.010  (D-pad left = smaller, right = bigger)",
        "End X : 0.120",
        "End B : 0.880",
        "D-pad up/down = move (LB = past the ends), X/B = ends, A = save position, Y = undo",
        "GUIDE (or hold START) = help",
        "--- Copy into your code ---",
        "PositionServo.create(hardwareMap, \"claw\").limits(0.120, 0.880)",
        "    .position(\"P1\", 0.210)",
        "    .position(\"P2\", 0.770)",
        "*Tunables.Claw: P1 = 0.210, P2 = 0.770",
        "*    // rename P1, P2... (open, closed); keep them between 0.120 and 0.880",
        "(saved to the hub when you press STOP)",
    ]),
}

FONT = 14          # px, monospace
CHAR = 8.45        # px per character at FONT
LINE = 21          # px per line
PAD = 16


def svg(title, state, lines):
    width = max(720, int(max(len(l.lstrip("*")) for l in lines) * CHAR) + 2 * PAD + 40)
    screen_top = 70
    height = screen_top + len(lines) * LINE + 2 * PAD + 30
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
         f'font-family="Helvetica, Arial, sans-serif" font-size="15">',
         f'<rect width="{width}" height="{height}" rx="18" fill="#2b2f36"/>',
         # the Driver Station app's top bar: the OpMode and its state
         f'<rect x="14" y="14" width="{width - 28}" height="44" rx="6" fill="#3d434d"/>',
         f'<text x="30" y="42" fill="#ffffff" font-weight="bold">{escape(title)}</text>',
         f'<text x="{width - 30}" y="42" fill="#9fd89f" text-anchor="end">{"▶ " + state}</text>',
         # the telemetry area
         f'<rect x="14" y="{screen_top}" width="{width - 28}" height="{height - screen_top - 14}" rx="6" fill="#ffffff"/>']
    y = screen_top + PAD + 14
    for raw in lines:
        mark = raw.startswith("*")
        text = raw[1:] if mark else raw
        if mark:
            o.append(f'<rect x="22" y="{y - 15}" width="{width - 44}" height="{LINE}" rx="3" fill="#fff3a8"/>')
        bold = ' font-weight="bold"' if text.startswith("---") else ""
        o.append(f'<text x="{14 + PAD}" y="{y}" font-family="Menlo, Consolas, monospace" font-size="{FONT}" '
                 f'fill="#1a1a1a"{bold} xml:space="preserve">{escape(text)}</text>')
        y += LINE
    o.append('</svg>')
    return "\n".join(o) + "\n"


for name, (title, state, lines) in SCREENS.items():
    path = OUT / f"hub-{name}.svg"
    path.write_text(svg(title, state, lines))
    print("wrote", path.relative_to(ROOT))


# ---------------------------------------------------------------------------------------------------
# One still picture per step: the button to press, the servo seen from above (only for steps that move
# it), and the screen lines that matter for that step ("..." = lines left out). Narrow, so it reads on a
# phone.
import math

def angle(pos):            # 0..1 -> -135..+135 degrees (a 270 degree servo); 0.5 points up
    return (pos - 0.5) * 270

def step_picture(button, where, lines, pos=None, frm=None, note="", title="Driver Hub", order=None,
                 stops=(0.10, 0.90), stop_label="frame"):
    W = 520
    if order is None:
        order = "press-first" if pos is not None else "screen-first"
    # press-first: the button (and the servo) on top, then the screen it gives. screen-first: the screen,
    # then the button, because you read the screen, and then you press.
    hub_h = 54 + len(lines) * LINE + 2 * PAD
    top_h = 190 if pos is not None else (70 if order == "press-first" else 6)
    H = top_h + hub_h + (6 if order == "press-first" else 70)
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" '
         f'font-family="Helvetica, Arial, sans-serif" font-size="15">',
         f'<rect width="{W}" height="{H}" fill="#ffffff"/>']
    if pos is not None:
        cx, cy, r = 130, 100, 62
        def pt(deg, rad):
            a = math.radians(deg - 90)
            return cx + rad * math.cos(a), cy + rad * math.sin(a)
        o.append(f'<text x="{cx}" y="20" text-anchor="middle" font-size="13" fill="#555">the servo, seen from above</text>')
        for p in stops:          # the hard stops, just past the ends
            x, y = pt(angle(p) + (-7 if p < 0.5 else 7), r + 6)
            o.append(f'<rect x="{x - 8:.1f}" y="{y - 8:.1f}" width="16" height="16" fill="#8a6d3b" transform="rotate({angle(p):.1f} {x:.1f} {y:.1f})"/>')
            tx, ty = pt(angle(p) + (-22 if p < 0.5 else 22), r + 22)   # beside the stop, on its outer side
            o.append(f'<text x="{tx:.0f}" y="{ty:.0f}" text-anchor="middle" font-size="12" fill="#8a6d3b">{escape(stop_label)}</text>')
        o.append(f'<rect x="{cx - 26}" y="{cy - 19}" width="52" height="38" rx="6" fill="#d8dde6" stroke="#333" stroke-width="2"/>')
        if frm is not None:      # where the arm was (pale), and an arrow along the way it moved
            o.append(f'<g transform="rotate({angle(frm):.1f} {cx} {cy})"><line x1="{cx}" y1="{cy}" x2="{cx}" y2="{cy - r}" '
                     f'stroke="#a9bde0" stroke-width="9" stroke-linecap="round" stroke-dasharray="4 6"/></g>')
            a0, a1 = angle(frm), angle(pos)
            (x0, y0), (x1, y1) = pt(a0, r + 10), pt(a1, r + 10)
            big = 1 if abs(a1 - a0) > 180 else 0
            sweep = 1 if a1 > a0 else 0
            o.append(f'<defs><marker id="ah" viewBox="0 0 10 10" refX="7" refY="5" markerWidth="5" markerHeight="5" orient="auto">'
                     f'<path d="M0,0 L10,5 L0,10 z" fill="#1f4e9c"/></marker></defs>')
            o.append(f'<path d="M{x0:.1f},{y0:.1f} A{r + 10},{r + 10} 0 {big} {sweep} {x1:.1f},{y1:.1f}" fill="none" '
                     f'stroke="#1f4e9c" stroke-width="2.5" marker-end="url(#ah)"/>')
        o.append(f'<g transform="rotate({angle(pos):.1f} {cx} {cy})"><line x1="{cx}" y1="{cy}" x2="{cx}" y2="{cy - r}" '
                 f'stroke="#1f4e9c" stroke-width="9" stroke-linecap="round"/></g><circle cx="{cx}" cy="{cy}" r="10" fill="#1f4e9c"/>')
        o.append(f'<text x="{cx}" y="{top_h - 8}" text-anchor="middle" font-size="14" fill="#2d7a32">{escape(note)}</text>')
        bx, by = 300, 70
    elif order == "press-first":
        bx, by = 16, 10
    else:
        bx, by = 16, top_h + hub_h + 6
    # the button
    if pos is not None:
        o.append(f'<text x="{bx}" y="{by}" font-size="14" fill="#555">{escape(where)}</text>')
        py = by + 12
        px = bx
    else:
        o.append(f'<text x="{bx}" y="{by + 33}" font-size="14" fill="#555">{escape(where)}</text>')
        py = by + 8
        px = bx + 14 + int(7.2 * len(where))
    wb = 30 + 11 * len(button)
    o.append(f'<rect x="{px}" y="{py}" width="{wb}" height="42" rx="21" fill="#ffd54d" stroke="#333" stroke-width="2"/>')
    o.append(f'<text x="{px + wb / 2}" y="{py + 28}" text-anchor="middle" font-size="19" font-weight="bold" fill="#222">{escape(button)}</text>')
    # the Driver Hub
    t = top_h
    o.append(f'<rect x="6" y="{t}" width="{W - 12}" height="{hub_h}" rx="14" fill="#2b2f36"/>')
    o.append(f'<text x="22" y="{t + 30}" fill="#ffffff" font-size="14" font-weight="bold">{escape(title)}</text>')
    o.append(f'<rect x="16" y="{t + 44}" width="{W - 32}" height="{hub_h - 54}" rx="6" fill="#ffffff"/>')
    y = t + 44 + PAD + 10
    for raw in lines:
        mark = raw.startswith("*")
        text = raw[1:] if mark else raw
        if mark:
            o.append(f'<rect x="20" y="{y - 15}" width="{W - 40}" height="{LINE}" rx="3" fill="#fff3a8"/>')
        style = ' font-weight="bold"' if text.startswith("---") or text == "Finders" else (
            ' fill="#999"' if text.strip() in ("...", "") else ' fill="#1a1a1a"')
        o.append(f'<text x="26" y="{y}" font-family="Menlo, Consolas, monospace" font-size="13"{style} '
                 f'xml:space="preserve">{escape(text)}</text>')
        y += LINE
    o.append('</svg>')
    return "\n".join(o) + "\n"

for data in sorted((ROOT / "tools/hub_steps").glob("*.json")):
    for name, args in json.loads(data.read_text())["pictures"].items():
        path = OUT / f"hub-{name}.svg"
        path.write_text(step_picture(**args))
        print("wrote", path.relative_to(ROOT))
