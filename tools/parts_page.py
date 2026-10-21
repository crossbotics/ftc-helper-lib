#!/usr/bin/env python3
"""Writes docs/guides/parts-catalog.md from the simulator's parts catalog (sim/parts/parts.json).

    python3 tools/parts_page.py           write the page
    python3 tools/parts_page.py --check   exit 1 if the page is not up to date (the GitHub build runs this)

Edit parts.json, never the page: the page is made from it.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "sim", "parts", "parts.json")
PAGE = os.path.join(ROOT, "docs", "guides", "parts-catalog.md")


def num(v, digits=1):
    if v is None:
        return "—"
    if isinstance(v, bool):
        return "yes" if v else "no"
    if float(v).is_integer():
        return str(int(v))
    return f"{v:.{digits}f}".rstrip("0").rstrip(".")


def servo_5v(s):
    """The row the simulator uses: the byVoltage row closest to 5 V (same as SimParts.Servo)."""
    sec, volts = s.get("secPer60Deg"), s.get("voltage")
    best = None
    for r in s.get("byVoltage") or []:
        v, t = r.get("voltage"), r.get("secPer60Deg")
        if v is not None and t is not None and (best is None or abs(v - 5.0) < best):
            best, sec, volts = abs(v - 5.0), t, v
    return sec, volts


def family(m):
    i = m["id"]
    for prefix, name in [("gobilda-5203", "goBILDA 5203 Yellow Jacket (REX shaft)"),
                         ("gobilda-5204", "goBILDA 5204 Yellow Jacket (D-shaft)"),
                         ("rev-hd-hex", "REV HD Hex"), ("rev-core-hex", "REV Core Hex"),
                         ("am-neverest", "AndyMark NeveRest"),
                         ("gobilda-2000", "goBILDA 2000 Series servos"), ("rev-smart", "REV Smart Robot Servos"),
                         ("axon", "Axon servos")]:
        if i.startswith(prefix):
            return name
    return m.get("seller", "Other")


def page(cat):
    motors, servos = cat["motors"], cat["servos"]
    out = []
    w = out.append
    w("# Parts catalog: motors, servos, wheels and rollers")
    w("")
    w("> **Work in progress:** this library and its docs are still changing. Something wrong or unclear? [Tell us](https://github.com/jasoncross/ftc-helper-lib/issues).")
    w("")
    w("<!-- Made by tools/parts_page.py from sim/parts/parts.json. Edit parts.json, then run the script. -->")
    w("")
    w("The simulator knows these FTC motors and servos. A robot file names a part by its **id** "
      "(`part=gobilda-5203-312`), and the simulator takes the part's real numbers from here: how fast it turns, "
      "how many encoder counts it gives per turn, and its current (motors), or its range and speed (servos).")
    w("")
    w(f"Every number comes from the seller's page or the seller's own documents, read on **{cat['checked']}** "
      "(the link is in each row). A dash (—) means the seller does not publish that number. "
      f"{len(motors)} motors and {len(servos)} servos. See also: [the simulator guide](simulator.md).")
    w("")
    w("## Use a part")
    w("")
    w("In the robot file (`simulator/robot.properties` in a robot project):")
    w("")
    w("```properties")
    w("drive.part = gobilda-5203-312                       # all four wheel motors")
    w("motor.lift = lift, part=gobilda-5203-117, min=0, max=2750")
    w("motor.flywheel = flywheel, part=gobilda-5203-6000, feeder=feeder")
    w("servo.claw = 0.5, part=gobilda-2000-torque          # start position, then the part")
    w("```")
    w("")
    w("Numbers written in the robot file win over the part's numbers (for example `ticksPerRev=` on a motor). "
      "A typo in an id stops the simulator with a list of the ids it knows.")
    w("")
    w("## Motors")
    w("")
    w("Free speed and current at 12 V. **Counts per turn** are encoder counts per turn of the output shaft, as "
      "the FTC SDK reports them. **Top speed** is what `getVelocity()` shows at full power with no load: "
      "free speed × counts per turn ÷ 60. On a real robot, `setVelocity()` and RUN_USING_ENCODER use the motor "
      "TYPE chosen in the robot configuration; pick the matching type there.")
    groups = []
    for m in motors:
        f = family(m)
        if not groups or groups[-1][0] != f:
            groups.append((f, []))
        groups[-1][1].append(m)
    for f, ms in groups:
        w("")
        w(f"### {f}")
        w("")
        w("| id | Part | Part number | Ratio | Free speed (RPM) | Counts per turn | Top speed (counts/s) "
          "| Stall torque (kg-cm) | Stall current (A) |")
        w("|---|---|---|---|---|---|---|---|---|")
        for m in ms:
            top = m["freeRpm"] * m["countsPerRev"] / 60 if m.get("freeRpm") and m.get("countsPerRev") else None
            w(f"| `{m['id']}` | [{m['name']}]({m['url']}) | {m.get('partNumber') or '—'} | {num(m.get('gearRatio'), 2)}:1 "
              f"| {num(m.get('freeRpm'))} | {num(m.get('countsPerRev'), 2)} | {num(top, 0)} "
              f"| {num(m.get('stallTorqueKgCm'), 2)} | {num(m.get('stallCurrentA'))} |")
    w("")
    w("## Servos")
    w("")
    w("A Control Hub servo port gives about 5 V, so the simulator uses the seller's speed row closest to 5 V "
      "(**Speed used**); the headline number on the seller's page is often at 6 V. **Range** is how far "
      "positions 0 to 1 turn it. **Continuous** = it can be switched to continuous rotation (a CR servo).")
    groups = []
    for s in servos:
        f = family(s)
        if not groups or groups[-1][0] != f:
            groups.append((f, []))
        groups[-1][1].append(s)
    for f, ss in groups:
        w("")
        w(f"### {f}")
        w("")
        w("| id | Part | Part number | Range (°) | Speed used (s per 60°) | Headline speed (s per 60°) "
          "| Torque (kg-cm) | Stall current (A) | Continuous |")
        w("|---|---|---|---|---|---|---|---|---|")
        for s in ss:
            sec, volts = servo_5v(s)
            used = f"{num(sec, 3)} at {num(volts)} V" if sec is not None else "—"
            w(f"| `{s['id']}` | [{s['name']}]({s['url']}) | {s.get('partNumber') or '—'} | {num(s.get('rangeDeg'))} "
              f"| {used} | {num(s.get('secPer60Deg'), 3)} at {num(s.get('voltage'))} V | {num(s.get('torqueKgCm'))} "
              f"| {num(s.get('stallCurrentA'))} | {num(s.get('continuousCapable'))} |")
    w("")
    w("A servo with no range here (—) moves at the robot file's `servo.speed` (positions per second) in the "
      "simulator; its range is set by programming it.")
    w("")
    w("## Wheels and rollers")
    w("")
    w("For `Robot.java` (not the robot file): a `Shape` names one of these and takes its size from here, "
      "and `Drive.wheel(id)` its diameter ([robot module, Shapes](../modules/robot.md#shapes-and-parts)). "
      "**Width** is along the axle; a dash (—) means the seller does not give it, so the shape gives its "
      "length (`lengthMm`).")
    w("")
    w("| id | Part | Part number | Diameter (mm) | Width (mm) | Checked | Notes |")
    w("|---|---|---|---|---|---|---|")
    for x in cat.get("wheels", []):
        w(f"| `{x['id']}` | [{x['name']}]({x['url']}) | {x.get('partNumber') or '—'} | {num(x.get('diameterMm'))} "
          f"| {num(x.get('widthMm'))} | {x['checked']} | {x.get('notes') or ''} |")
    w("")
    w("## A part that is not in the list")
    w("")
    w("Three ways, from quickest to most shared:")
    w("")
    w("**1. Write its numbers in the robot file.** Use the seller's page.")
    w("")
    w("```properties")
    w("# A motor: free speed (RPM) and encoder counts per output turn; or maxTicksPerSec= directly.")
    w("motor.arm = lift, rpm=90, ticksPerRev=1993.6, min=0, max=1200")
    w("# The drivetrain: counts per turn and top speed (counts per second).")
    w("drive.ticksPerRev = 537.7")
    w("drive.maxTicksPerSec = 2796")
    w("# A servo: its range (degrees) and seconds per 60 degrees (use the 4.8-5 V number).")
    w("servo.gate = 0.5, rangeDeg=180, secPer60=0.12")
    w("```")
    w("")
    w("**2. Keep the team's own parts file.** Good when several robot files use the part, or to keep its link "
      "and notes. Put a JSON file next to the robot file and name it there:")
    w("")
    w("```properties")
    w("parts = parts.json")
    w("```")
    w("")
    w("It has the same form as the library's catalog. Only `id` and the numbers the simulator uses are needed (a motor: `freeRpm` and `countsPerRev`; a servo: `rangeDeg` and `secPer60Deg`); "
      "the rest is for people. Give each part its own id: an id that is already in the library is refused, so "
      "a typo can't quietly replace a real part.")
    w("")
    w("```json")
    w(json.dumps({"motors": [{"id": "team-arm-motor", "name": "Our arm motor (bought used)",
                              "url": "https://www.example.com/the-seller-page", "freeRpm": 90,
                              "countsPerRev": 1993.6, "stallCurrentA": 9.2, "checked": "2026-10-16"}],
                  "servos": [{"id": "team-gate-servo", "name": "Our gate servo", "rangeDeg": 180,
                              "secPer60Deg": 0.12, "voltage": 4.8}]}, indent=2))
    w("```")
    w("")
    w("Then `motor.arm = lift, part=team-arm-motor` works like a library part.")
    w("")
    w("**3. Add it to the library's catalog** (for every team): add it to `sim/parts/parts.json` with the "
      "seller's link and the date you checked it, run `python3 tools/parts_page.py` to update this page, and "
      "open a pull request ([contributing](../contributing.md)).")
    w("")
    w("## What each number is for")
    w("")
    w("| Field | Used for |")
    w("|---|---|")
    w("| `id` | the name a robot file uses (`part=...`) |")
    w("| `freeRpm` | motors: top speed with no load (with `countsPerRev`, the top speed in counts per second) |")
    w("| `countsPerRev` | motors: encoder counts per turn of the output shaft (`getCurrentPosition()` steps) |")
    w("| `stallCurrentA`, `freeCurrentA` | motors: the current shown in the Control Hub panel |")
    w("| `stallTorqueKgCm` | motors: shown here; the simple physics does not use it yet |")
    w("| `rangeDeg`, `secPer60Deg`, `byVoltage` | servos: how fast a position change takes |")
    w("| `torqueKgCm`, `stallCurrentA`, `continuousCapable` | servos: shown here; not used by the physics |")
    w("| `name`, `partNumber`, `seller`, `url`, `checked`, `notes` | people: what it is and where the numbers came from |")
    w("")
    w("## Notes on each part")
    w("")
    w("From the catalog, for checking a number against the seller's page.")
    w("")
    for p in motors + servos:
        if p.get("notes"):
            w(f"- `{p['id']}`: {p['notes']}")
    w("")
    w(f"Catalog notes: {cat['notes']}")
    return "\n".join(out) + "\n"


def main():
    with open(CATALOG, encoding="utf-8") as f:
        cat = json.load(f)
    ids = [p["id"] for p in cat["motors"] + cat["servos"]]
    dup = sorted({i for i in ids if ids.count(i) > 1})
    if dup:
        sys.exit(f"parts.json: ids used twice: {dup}")
    text = page(cat)
    if "--check" in sys.argv:
        old = open(PAGE, encoding="utf-8").read() if os.path.exists(PAGE) else ""
        if old != text:
            sys.exit("docs/guides/parts-catalog.md is out of date: run python3 tools/parts_page.py")
        print("Parts page is up to date.")
        return
    with open(PAGE, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"wrote {os.path.relpath(PAGE, ROOT)} ({len(cat['motors'])} motors, {len(cat['servos'])} servos)")


if __name__ == "__main__":
    main()
