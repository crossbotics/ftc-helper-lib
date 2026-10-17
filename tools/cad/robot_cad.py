"""Robot CAD (a STEP file of the whole robot) -> what the robot-file skill needs to build the simulator's robot file.

Three steps, each a command (run them with the Python that has build123d: see tools/cad/README.md):

  read   STEP OUT          Reads the STEP once (big files take a few minutes) and writes OUT/parts.json (every part:
                           name, seller part number and the parts catalog match, size, position, and for round parts
                           their spin axis and radius) and OUT/meshes.npz (a coarse mesh of each part).
  clues  OUT FRAME         With the robot frame (FRAME = a JSON file with cad.units, cad.axes, cad.origin as in the
                           robot file), prints the drive wheels, every motor and servo with what it lines up with
                           (same axis, or linked by gears/belts/chains on shared axles), and the round parts: the
                           facts the agent guesses from. Writes OUT/clues.json.
  groups OUT FRAME GROUPS  Writes one STL per group (GROUPS = a JSON file the agent writes: which parts make the
                           chassis and each shape) into OUT/cad/, and prints each group's size and place in the robot
                           frame with a suggested shape line, to compare with the robot file.

Coordinates: parts.json keeps the CAD's own (mm). The robot frame is forward, left, up in inches from the robot's
centre on the floor (docs/guides/simulator.md, "CAD models of the parts").
"""
import json
import math
import os
import re
import sys
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.join(HERE, "..", "..", "sim", "parts", "parts.json")

# Seller part numbers as they appear in CAD names: goBILDA 1234-5678-9012, REV REV-41-1300, AndyMark am-1234,
# Studica 70-1234 or 75-1234 (checked against the 2026-27 starter bot STEP files).
PART_NUMBER = re.compile(r"(\d{4}-\d{4}-\d{4}|REV-\d{2}-\d{4}|am-\d{4}[a-z]?|\b7[05]-\d{4}\b)", re.I)


# A motor or servo by its name, when its part number is missing or not in the catalog.
ACTUATOR = re.compile(r"gear ?motor|\bmotor\b|servo|neverest|hd hex|core hex|yellow jacket", re.I)
NOT_ACTUATOR = re.compile(r"mount|bracket|horn|plate|block|hub|coupler|spacer|screw|nut|carrier", re.I)


def actuator_kind(name):
    if not ACTUATOR.search(name or "") or NOT_ACTUATOR.search(name or ""):
        return None
    return "servo" if re.search(r"servo", name, re.I) else "motor"


def part_number(*names):
    for n in names:
        m = PART_NUMBER.search(n or "")
        if m:
            return m.group(1)
    return None


def catalog():
    try:
        d = json.load(open(CATALOG))
    except OSError:
        return {}
    out = {}
    for kind in ("motors", "servos"):
        for p in d.get(kind, []):
            pn = p.get("partNumber") or ""
            if "+" in pn:
                continue      # a motor with gearbox cartridges: the CAD has them as separate parts (the agent adds the ratio)
            for alt in pn.split("/"):
                alt = alt.strip().lower()
                if alt and alt not in out:
                    out[alt] = {"id": p["id"], "kind": kind[:-1], "name": p["name"],
                                "freeRpm": p.get("freeRpm"), "gearRatio": p.get("gearRatio")}
    return out


# ---------------------------------------------------------------- read

def read(step, out_dir, deflection=0.6):
    from OCP.STEPCAFControl import STEPCAFControl_Reader
    from OCP.TDocStd import TDocStd_Document
    from OCP.XCAFDoc import XCAFDoc_DocumentTool
    from OCP.collections import Sequence_TDF_Label
    from OCP.TDF import TDF_Label
    from OCP.TDataStd import TDataStd_Name
    from OCP.TCollection import TCollection_ExtendedString
    from OCP.TopLoc import TopLoc_Location
    from OCP.IFSelect import IFSelect_RetDone
    from OCP.BRepMesh import BRepMesh_IncrementalMesh
    from OCP.TopExp import TopExp_Explorer
    from OCP.TopAbs import TopAbs_FACE, TopAbs_REVERSED
    from OCP.TopoDS import TopoDS
    from OCP.BRep import BRep_Tool
    from OCP.BRepAdaptor import BRepAdaptor_Surface
    from OCP.GeomAbs import GeomAbs_Cylinder
    from OCP.GProp import GProp_GProps
    from OCP.BRepGProp import BRepGProp
    from OCP.TopoDS import TopoDS_Compound
    from OCP.BRep import BRep_Builder
    from OCP.Bnd import Bnd_Box
    from OCP.BRepBndLib import BRepBndLib

    t0 = time.time()
    doc = TDocStd_Document(TCollection_ExtendedString("doc"))
    r = STEPCAFControl_Reader()
    r.SetNameMode(True)
    if r.ReadFile(step) != IFSelect_RetDone:
        sys.exit(f"can't read {step} as a STEP file")
    r.Transfer(doc)
    print(f"read the STEP in {time.time() - t0:.0f} s", flush=True)
    st = XCAFDoc_DocumentTool.ShapeTool_s(doc.Main())
    cat = catalog()

    def name(label):
        a = TDataStd_Name()
        return a.Get().ToExtString() if label.FindAttribute(TDataStd_Name.GetID_s(), a) else ""

    # One "part" = a leaf shape, or a whole sub-assembly whose name carries a seller part number (a motor with its
    # gearbox is one part, not 15).
    parts = []

    def small(shapes, mm=250.0):
        box = Bnd_Box()
        for sh in shapes:
            BRepBndLib.Add_s(sh, box)
        if box.IsVoid():
            return True
        lo, hi = box.CornerMin(), box.CornerMax()
        return math.dist((lo.X(), lo.Y(), lo.Z()), (hi.X(), hi.Y(), hi.Z())) < mm

    def collect(label, loc, path):
        """All leaf shapes under label, placed."""
        if st.IsReference_s(label):
            ref = TDF_Label()
            st.GetReferredShape_s(label, ref)
            return collect(ref, loc.Multiplied(st.GetLocation_s(label)), path)
        if st.IsAssembly_s(label):
            kids = Sequence_TDF_Label()
            st.GetComponents_s(label, kids)
            out = []
            for i in range(1, kids.Length() + 1):
                out += collect(kids.Value(i), loc, path)
            return out
        return [st.GetShape_s(label).Moved(loc)]

    def walk(label, loc, path):
        if st.IsReference_s(label):
            ref = TDF_Label()
            st.GetReferredShape_s(label, ref)
            # the part's own (product) name; the instance name only when it has none. SolidWorks exports name every
            # instance NAUO1, NAUO2... and keep the real name on the product.
            inst, prod = name(label), name(ref)
            here = prod if prod and not re.fullmatch(r"\s*", prod) else inst
            loc2 = loc.Multiplied(st.GetLocation_s(label))
            pn = part_number(here, name(ref))
            if st.IsAssembly_s(ref) and (pn or ACTUATOR.search(here)):
                # a motor or servo from the catalog, or a small bought part (a wheel with its hub): one part.
                # A big sub-assembly with a part number (a whole kit) is walked into.
                shapes = collect(ref, loc2, path)
                if (pn and pn.lower() in cat) or small(shapes):
                    parts.append((path + [here], shapes))
                    return
            walk(ref, loc2, path + [here])
            return
            walk(ref, loc2, path + [here])
            return
        if st.IsAssembly_s(label):
            kids = Sequence_TDF_Label()
            st.GetComponents_s(label, kids)
            for i in range(1, kids.Length() + 1):
                walk(kids.Value(i), loc, path)
            return
        parts.append((path or [name(label)], [st.GetShape_s(label).Moved(loc)]))

    free = Sequence_TDF_Label()
    st.GetFreeShapes(free)
    for i in range(1, free.Length() + 1):
        walk(free.Value(i), TopLoc_Location(), [])

    meshes, out = {}, []
    for k, (path, shapes) in enumerate(parts):
        b = BRep_Builder()
        comp = TopoDS_Compound()
        b.MakeCompound(comp)
        for s in shapes:
            b.Add(comp, s)
        BRepMesh_IncrementalMesh(comp, deflection, False, 0.5, True)
        tris, cyl, lines = [], {}, {}
        ex = TopExp_Explorer(comp, TopAbs_FACE)
        while ex.More():
            f = TopoDS.Face(ex.Current())
            ad = BRepAdaptor_Surface(f)
            if ad.GetType() == GeomAbs_Cylinder:
                c = ad.Cylinder()
                ax = c.Axis()
                d = np.array([ax.Direction().X(), ax.Direction().Y(), ax.Direction().Z()])
                d = d if d[np.argmax(np.abs(d))] > 0 else -d
                g = GProp_GProps()
                BRepGProp.SurfaceProperties_s(f, g)
                key = tuple(np.round(d, 2))
                cyl[key] = cyl.get(key, 0) + g.Mass()
                p = np.array([ax.Location().X(), ax.Location().Y(), ax.Location().Z()])
                line = (key, tuple(np.round(p - (p @ d) * d, 0)))          # the axis line (its point nearest the origin)
                lines[line] = lines.get(line, 0) + g.Mass()
            l = TopLoc_Location()
            tri = BRep_Tool.Triangulation_s(f, l)
            if tri is not None:
                tr = l.Transformation()
                rev = f.Orientation() == TopAbs_REVERSED
                nodes = np.array([[q.X(), q.Y(), q.Z()] for q in (tri.Node(i).Transformed(tr) for i in range(1, tri.NbNodes() + 1))])
                idx = np.array([tri.Triangle(i).Get() for i in range(1, tri.NbTriangles() + 1)]) - 1
                if rev:
                    idx = idx[:, [0, 2, 1]]
                tris.append(nodes[idx].reshape(-1))
            ex.Next()
        t = np.concatenate(tris).astype(np.float32) if tris else np.zeros(0, np.float32)
        meshes[f"p{k}"] = t
        pts = t.reshape(-1, 3) if len(t) else np.zeros((1, 3))
        lo, hi = pts.min(0), pts.max(0)
        names = [n for n in path if n]
        pn = None
        for n in reversed(names):                       # the nearest name with a part number
            pn = part_number(n)
            if pn:
                break
        entry = {"i": k, "name": names[-1] if names else "", "path": "/".join(names), "partNumber": pn,
                 "box": [round(float(v), 1) for v in (*lo, *hi)], "triangles": int(len(t) // 9)}
        if pn and pn.lower() in cat:
            entry["catalog"] = cat[pn.lower()]
            entry["actuator"] = entry["catalog"]["kind"]
        elif actuator_kind(entry["name"]):
            entry["actuator"] = actuator_kind(entry["name"])     # a motor or servo the catalog does not have
        # Round: a direction its cylinder faces turn about (biggest area first) across which the part is as wide one
        # way as the other, and no wider than a circle round the middle. A plate with round holes is not round.
        total = sum(cyl.values())
        for dkey, area in sorted(cyl.items(), key=lambda kv: -kv[1])[:3]:
            d = np.array(dkey) / np.linalg.norm(dkey)
            u = np.cross(d, [1, 0, 0] if abs(d[0]) < 0.9 else [0, 1, 0])
            u /= np.linalg.norm(u)
            v = np.cross(d, u)
            a, b, w = pts @ u, pts @ v, pts @ d
            wa, wb = a.max() - a.min(), b.max() - b.min()
            if wa < 1 or min(wa, wb) / max(wa, wb) < 0.85:
                continue
            ca, cb = (a.max() + a.min()) / 2, (b.max() + b.min()) / 2
            radius = float(np.sqrt((a - ca) ** 2 + (b - cb) ** 2).max())
            if radius * 2 > 1.15 * max(wa, wb):
                continue
            mid = ca * u + cb * v + d * (w.max() + w.min()) / 2
            entry["round"] = {"axis": [round(float(x), 3) for x in d], "centre": [round(float(x), 1) for x in mid],
                              "radius": round(radius, 1), "length": round(float(w.max() - w.min()), 1),
                              "share": round(area / max(total, 1e-9), 2)}
            break
        # A motor's or servo's shaft: the axis line with the most cylinder area (a motor's can and output shaft share
        # it). Kept for every part; the clues use it for the motors and servos.
        if lines:
            (dkey, p), _ = max(lines.items(), key=lambda kv: kv[1])
            d = np.array(dkey) / np.linalg.norm(dkey)
            rel = pts - np.array(p)
            along = rel @ d
            mid = np.array(p) + d * (along.min() + along.max()) / 2
            entry["shaft"] = {"axis": [round(float(x), 3) for x in d], "centre": [round(float(x), 1) for x in mid],
                              "length": round(float(along.max() - along.min()), 1)}
        out.append(entry)
        if (k + 1) % 100 == 0:
            print(f"  {k + 1} of {len(parts)} parts", flush=True)
    os.makedirs(out_dir, exist_ok=True)
    json.dump({"step": os.path.abspath(step), "units": "mm", "parts": out}, open(os.path.join(out_dir, "parts.json"), "w"), indent=0)
    np.savez_compressed(os.path.join(out_dir, "meshes.npz"), **meshes)
    named = sum(1 for e in out if e.get("catalog"))
    print(f"{len(out)} parts ({named} matched to the parts catalog), "
          f"{sum(e['triangles'] for e in out)} triangles, in {time.time() - t0:.0f} s -> {out_dir}")


# ---------------------------------------------------------------- the robot frame

class Frame:
    """CAD mm -> robot inches (forward, left, up), the same rule as the simulator's SimCad."""

    def __init__(self, path):
        f = json.load(open(path))
        units = f.get("cad.units", "mm")
        self.scale = {"mm": 1 / 25.4, "cm": 1 / 2.54, "m": 1 / 0.0254, "in": 1.0}[units]
        self.m = np.zeros((3, 3))
        for i, a in enumerate(f["cad.axes"].replace(" ", "").split(",")):
            self.m[i, "xyz".index(a[-1])] = -1.0 if a.startswith("-") else 1.0
        if np.linalg.det(self.m) < 0:
            sys.exit("cad.axes would draw the robot mirrored")
        self.origin = np.array([float(v) for v in str(f.get("cad.origin", "0,0,0")).split(",")])
        self.text = f

    def p(self, x):
        return (np.asarray(x, float) - self.origin) @ self.m.T * self.scale

    def d(self, v):
        return np.asarray(v, float) @ self.m.T


def load(out_dir):
    d = json.load(open(os.path.join(out_dir, "parts.json")))
    return d, np.load(os.path.join(out_dir, "meshes.npz"))


def axis_name(v):
    names = ["forward", "left", "up"]
    k = int(np.argmax(np.abs(v)))
    return names[k] if abs(v[k]) > 0.97 else "tilted " + ",".join(f"{c:.2f}" for c in v)


# ---------------------------------------------------------------- clues

def clues(out_dir, frame_path):
    d, _ = load(out_dir)
    F = Frame(frame_path)
    parts = d["parts"]
    for e in parts:
        b = np.array(e["box"])
        corners = np.array([[b[i], b[j], b[k]] for i in (0, 3) for j in (1, 4) for k in (2, 5)])
        r = F.p(corners)
        e["_lo"], e["_hi"] = r.min(0), r.max(0)
        r = e.get("round") or (e.get("shaft") if e.get("actuator") else None)
        if r:
            e["_axis"] = F.d(r["axis"])
            e["_centre"] = F.p(r["centre"])
            e["_radius"] = r.get("radius", 0) * F.scale
            e["_length"] = r["length"] * F.scale

    def coaxial(a, b, tol_in=0.25):
        if "_axis" not in a or "_axis" not in b or abs(a["_axis"] @ b["_axis"]) < 0.995:
            return False
        w = b["_centre"] - a["_centre"]
        along = w @ a["_axis"]
        off = w - along * a["_axis"]
        # on the same line, and near along it (a shaft and what is on it; not the far side's motor on the same line)
        return np.linalg.norm(off) < tol_in and abs(along) < (a["_length"] + b["_length"]) / 2 + 4

    rnd = [e for e in parts if "round" in e]
    wheels = [e for e in rnd if 1.4 <= e["_radius"] <= 3.0 and e["_centre"][2] <= e["_radius"] + 0.6
              and axis_name(e["_axis"]) == "left"]
    drive = [{"i": e["i"], "name": e["name"], "partNumber": e["partNumber"], "diameterIn": round(2 * e["_radius"], 2),
              "at": [round(float(v), 2) for v in e["_centre"]]} for e in wheels]
    links = [e for e in rnd if re.search(r"gear|sprocket|pulley|belt|chain|\b2[23]\d\d-|\b3[34]\d\d-", e["path"], re.I)]
    actuators = [e for e in parts if e.get("actuator")]
    out = {"frame": F.text, "driveWheels": drive, "actuators": []}
    print(f"Robot frame: {F.text}")
    lo = np.min([e["_lo"] for e in parts], 0)
    hi = np.max([e["_hi"] for e in parts], 0)
    print(f"Whole robot: {hi[0] - lo[0]:.1f} x {hi[1] - lo[1]:.1f} in, {hi[2]:.1f} in tall, lowest point {lo[2]:.2f} in "
          f"(should be about 0: cad.origin is on the floor)")
    if wheels:
        xs = sorted({round(float(w['_centre'][0]), 1) for w in wheels})
        ys = sorted({round(float(w['_centre'][1]), 1) for w in wheels})
        print(f"\nDrive wheels ({len(wheels)}): diameters {sorted({w['diameterIn'] for w in drive})} in; "
              f"axles at forward {xs}; sides at left {ys}")
    else:
        print("\nNo drive wheels found (round parts touching the floor with a left-right axle): check cad.axes and cad.origin.")
    print("\nMotors and servos (from the parts catalog) and what each lines up with:")
    for a in actuators:
        c = a.get("catalog") or {"kind": a["actuator"], "id": "(not in catalog)"}
        mid = (a["_lo"] + a["_hi"]) / 2
        same = [e for e in rnd if e is not a and coaxial(a, e)] if "_axis" in a else []
        linked = []
        for g in links:
            if "_axis" in a and coaxial(a, g):
                linked += [e for e in rnd if e is not g and e not in same and e is not a and abs(e["_axis"] @ g["_axis"]) > 0.995
                           and np.linalg.norm(e["_centre"] - g["_centre"]) < 6 and not coaxial(a, e)]
        driven = sorted({(e["name"], round(2 * e["_radius"], 1), axis_name(e["_axis"])) for e in same + linked
                         if e["_radius"] > 0.4 and not e.get("actuator")}, key=lambda t: -t[1])
        line = (f"  {c['kind']:5s} {c['id']:24s} {a['name'][:40]:40s} at {mid[0]:6.1f} {mid[1]:6.1f} {mid[2]:5.1f} in (forward, left, up)"
                + (f", shaft {axis_name(a['_axis'])}" if "_axis" in a else ""))
        print(line)
        for n, dia, ax in driven[:6]:
            print(f"        lines up with: {n} ({dia} in across, axle {ax})")
        out["actuators"].append({"i": a["i"], "name": a["name"], "path": a["path"], "catalog": c,
                                 "at": [round(float(v), 2) for v in mid],
                                 "shaft": axis_name(a["_axis"]) if "_axis" in a else None,
                                 "linesUpWith": [{"name": n, "diameterIn": dia, "axle": ax} for n, dia, ax in driven]})
    unknown = sorted({f"{e['name']} ({e['partNumber'] or 'no part number'})" for e in actuators if not e.get("catalog")})
    if unknown:
        print("\nMotors and servos not in the parts catalog: find the catalog entry with the same motor and ratio, "
              "or give the speed and counts by hand:\n  " + "\n  ".join(unknown))
    big = sorted([e for e in rnd if e["_radius"] > 0.6 and not e.get("actuator") and e not in wheels],
                 key=lambda e: -e["_radius"])[:20]
    print("\nOther round parts (rollers, launcher wheels, hubs), biggest first:")
    for e in big:
        print(f"  {e['name'][:40]:40s} {2 * e['_radius']:5.2f} in across, {e['_length']:4.1f} in long, axle {axis_name(e['_axis'])}, "
              f"centre {e['_centre'][0]:6.1f} {e['_centre'][1]:6.1f} {e['_centre'][2]:5.1f}")
    out["roundParts"] = [{"i": e["i"], "name": e["name"], "diameterIn": round(2 * e["_radius"], 2), "lengthIn": round(e["_length"], 2),
                          "axle": axis_name(e["_axis"]), "centre": [round(float(v), 2) for v in e["_centre"]]} for e in big]
    json.dump(out, open(os.path.join(out_dir, "clues.json"), "w"), indent=1)


# ---------------------------------------------------------------- groups

def select(parts, F, rule):
    """The parts a group rule picks: {"parts": [i, ...]}, or {"near": [f, l, u], "within": in, "axle": "left"|...,
    "onAxis": true (only parts centred on that axle line), "names": "regex"}; "except": [i, ...] drops parts."""
    if "parts" in rule:
        pick = set(rule["parts"])
    else:
        pick = set()
        c = np.array(rule["near"], float)
        for e in parts:
            b = np.array(e["box"])
            mid = F.p((b[:3] + b[3:]) / 2)
            if "names" in rule and not re.search(rule["names"], e["path"], re.I):
                continue
            if np.linalg.norm(mid - c) > rule.get("within", 2.0):
                continue
            if rule.get("onAxis"):
                ax = {"forward": 0, "left": 1, "up": 2}[rule["axle"]]
                off = [k for k in range(3) if k != ax]
                if any(abs(mid[k] - c[k]) > rule.get("axisTolIn", 0.15) for k in off):
                    continue
            pick.add(e["i"])
    return pick - set(rule.get("except", []))


def groups(out_dir, frame_path, groups_path):
    d, m = load(out_dir)
    F = Frame(frame_path)
    G = json.load(open(groups_path))
    parts = d["parts"]
    taken, picks = set(), {}
    for g, rule in G.items():
        if rule == "rest":
            continue
        picks[g] = select(parts, F, rule)
        both = picks[g] & taken
        if both:
            print(f"  note: {g} also picks parts already in another group: {sorted(both)[:10]}")
        taken |= picks[g]
    for g, rule in G.items():
        if rule == "rest":
            picks[g] = {e["i"] for e in parts} - taken
    os.makedirs(os.path.join(out_dir, "cad"), exist_ok=True)
    print(f"{'group':22s} {'parts':>5s} {'triangles':>9s}   size (forward x left x up, in)   from (f, l, u)  to (f, l, u)")
    for g, ids in picks.items():
        t = np.concatenate([m[f"p{i}"] for i in sorted(ids)] or [np.zeros(0, np.float32)]).reshape(-1, 9)
        fn = os.path.join(out_dir, "cad", f"{g}.stl")
        with open(fn, "wb") as fh:
            fh.write(b"\0" * 80)
            fh.write(np.uint32(len(t)).tobytes())
            rec = np.zeros(len(t), dtype=[("n", "<f4", 3), ("v", "<f4", 9), ("a", "<u2")])
            rec["v"] = t
            fh.write(rec.tobytes())
        if not len(t):
            print(f"{g:22s} {0:5d}  (empty)")
            continue
        r = F.p(t.reshape(-1, 3))
        lo, hi = r.min(0), r.max(0)
        print(f"{g:22s} {len(ids):5d} {len(t):9d}   {hi[0] - lo[0]:5.2f} x {hi[1] - lo[1]:5.2f} x {hi[2] - lo[2]:5.2f}"
              f"   {lo[0]:6.2f} {lo[1]:6.2f} {lo[2]:5.2f}  {hi[0]:6.2f} {hi[1]:6.2f} {hi[2]:5.2f}")
    print(f"\nwrote {len(picks)} STL files to {os.path.join(out_dir, 'cad')}")


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) >= 3 and a[0] == "read":
        read(a[1], a[2], float(a[3]) if len(a) > 3 else 0.6)
    elif len(a) == 3 and a[0] == "clues":
        clues(a[1], a[2])
    elif len(a) == 4 and a[0] == "groups":
        groups(a[1], a[2], a[3])
    else:
        sys.exit(__doc__)
