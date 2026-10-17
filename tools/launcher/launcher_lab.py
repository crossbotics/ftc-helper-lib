"""The launcher program: how a robot's launcher throws, worked out from its CAD -> a shot table for the simulator.

    python tools/launcher/launcher_lab.py LAUNCHER.json

It cuts the robot's CAD (read by tools/cad/robot_cad.py) along the ball's path through the launcher wheel, then
runs many shots through that 2D outline: the ball is a soft disc squeezed between the spinning wheel and the hood
or plates, with grip that depends on how fast the surfaces slip, and gravity. The numbers the CAD cannot give (how
soft the balls are, how much the wheel and the hood grip, exactly where the ball comes in) are tried over ranges, so
the result is the LIKELY spread of shots, not one exact path. Film a few real shots to narrow it (docs/guides/
simulator.md, "Shot tables").

LAUNCHER.json (paths relative to it; inches and the robot frame: forward, left, up from the robot's centre on the floor):
  {"cadWork": "cad-work", "frame": "cad-work/frame.json",
   "wheel": {"parts": [233]},                      the launcher wheel (a robot_cad.py group rule)
   "guides": {"near": [-1.6, 0, 6.6], "within": 6},   parts the ball can touch (default: everything else near the wheel)
   "entry": {"at": [1.5, -0.2, 5.0], "speedIn": [-20, 10]},   where the ball starts (centre) and its speed (in/s,
                                                    in the ball's plane: across and up, as printed) as it reaches the wheel
   "entrySpreadIn": 0.3,                            how much the start point varies
   "feedForceN": 1.0, "feedTimeS": 0.05,            the feeder's push along the entry direction, and for how long
   "wheelTurn": "auto",                             +1: the front of the wheel moves up; -1: down; auto = try both
   "gear": 1, "shaftRpm": [1500, 2000, 2500, 3000],  wheel turns per motor turn, and the motor speeds to table
   "shape": [-1.57, -0.18, 6.61],                   the flywheel shape's at= (exit points are given from it)
   "pieces": {"POLLEN": {"diameterIn": 2.8, "massG": 25}},
   "ranges": {"ballStiffness": [1500, 8000], "wheelGrip": [0.6, 1.0], "guideGrip": [0.2, 0.5]},
   "cases": 40, "out": "launcher-table.json"}

ballStiffness is N/m per contact (a worn ball 1500, a new one 8000: a guess until someone squeezes one on a
kitchen scale); the grips are friction coefficients (Gecko-style wheels about 0.8; plastic or aluminium 0.2-0.5).
"""
import json
import math
import os
import sys
import time

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "cad"))
import robot_cad  # noqa: E402

IN = 0.0254


def plane_basis(axle):
    a = np.asarray(axle, float) / np.linalg.norm(axle)
    f = np.array([1.0, 0, 0]) - a[0] * a
    if np.linalg.norm(f) < 0.3:
        f = np.array([0, 1.0, 0]) - a[1] * a
    e1 = f / np.linalg.norm(f)
    e2 = np.cross(e1, a)
    if e2[2] < 0 or (abs(e2[2]) < 1e-9 and e2[1] < 0):
        # the axle's sign is arbitrary: turn it so the plane's second direction is up (angles and exits up-positive,
        # like the simulator's angle=)
        a, e2 = -a, -e2
    return a, e1, e2


def slice_segments(tris, centre, a, e1, e2, offsets, reach):
    """Triangles (n, 9) in robot inches cut by the planes centre + o * a (o in offsets): 2D segments (m, 4) in
    inches, in the (e1, e2) plane, from the wheel centre, keeping those within reach."""
    t = tris.reshape(-1, 3, 3) - centre
    segs = []
    for o in offsets:
        h = t @ a - (o + 1.3e-6)       # a hair off, so no corner lies exactly on the cutting plane
        s0, s1, s2 = np.sign(h[:, 0]), np.sign(h[:, 1]), np.sign(h[:, 2])
        cut = ~((s0 == s1) & (s1 == s2))
        tc, hc = t[cut], h[cut]
        pts = []
        for i, j in ((0, 1), (1, 2), (2, 0)):
            hi, hj = hc[:, i], hc[:, j]
            cross = (hi * hj) < 0
            u = np.where(cross, hi / np.where(cross, hi - hj, 1), np.nan)
            p = tc[:, i] + u[:, None] * (tc[:, j] - tc[:, i])
            pts.append(p)
        P = np.stack(pts, 1)                                   # (k, 3 edges, 3): two of the three are real
        ok = ~np.isnan(P[:, :, 0])
        keep = ok.sum(1) == 2
        P, ok = P[keep], ok[keep]
        A = np.array([p[o][0] for p, o in zip(P, ok)]) if len(P) else np.zeros((0, 3))
        B = np.array([p[o][1] for p, o in zip(P, ok)]) if len(P) else np.zeros((0, 3))
        if len(A):
            segs.append(np.stack([A @ e1, A @ e2, B @ e1, B @ e2], 1))
    if not segs:
        return np.zeros((0, 4))
    S = np.concatenate(segs)
    mid = (S[:, :2] + S[:, 2:]) / 2
    S = S[np.linalg.norm(mid, axis=1) < reach]
    # fewer, longer pieces: snap ends to a 1 mm grid and drop repeats and specks
    S = np.round(S / 0.04) * 0.04
    S = S[np.linalg.norm(S[:, :2] - S[:, 2:], axis=1) > 0.02]
    S = np.unique(S, axis=0)
    return S


def run(cfg, base):
    def path(p):
        return p if os.path.isabs(p) else os.path.join(base, p)

    work = path(cfg["cadWork"])
    F = robot_cad.Frame(path(cfg["frame"]))
    d, meshes = robot_cad.load(work)
    parts = d["parts"]
    wheel_ids = robot_cad.select(parts, F, cfg["wheel"])
    if not wheel_ids:
        sys.exit("no wheel parts picked: check \"wheel\"")
    # the wheel: its biggest round part
    w = max((parts[i] for i in wheel_ids if "round" in parts[i]), key=lambda e: e["round"]["radius"], default=None)
    if w is None:
        sys.exit("none of the wheel parts is round: check \"wheel\"")
    centre = F.p(w["round"]["centre"])
    R = w["round"]["radius"] * F.scale
    a, e1, e2 = plane_basis(F.d(w["round"]["axis"]))
    guides_rule = cfg.get("guides") or {"near": list(centre), "within": R + 4}
    guide_ids = robot_cad.select(parts, F, guides_rule) - wheel_ids
    tris = np.concatenate([F.p(meshes[f"p{i}"].reshape(-1, 3)).reshape(-1, 9) for i in sorted(guide_ids)]
                          or [np.zeros((0, 9))])
    pieces = cfg.get("pieces") or {"POLLEN": {"diameterIn": 2.8, "massG": 25}}
    rmax = max(p["diameterIn"] for p in pieces.values()) / 2
    offsets = [-0.6 * rmax, 0.0, 0.6 * rmax]
    S = slice_segments(tris, centre, a, e1, e2, offsets, R + 2 * rmax + 3)
    print(f"wheel: {w['name']}, {2 * R:.2f} in across, centre {centre.round(2)} (robot frame), axle {robot_cad.axis_name(a)}")
    print(f"the ball's plane: across = {robot_cad.axis_name(e1)}, up = {robot_cad.axis_name(e2)} (angles are up from 'across'; "
          f"the table is in the flywheel shape's own forward/up, so the shape must face the robot's forward in the CAD pose)")
    print(f"guides: {len(guide_ids)} parts -> {len(S)} outline pieces in the ball's plane")

    ent = cfg["entry"]
    p0 = np.asarray(ent["at"], float) - centre
    p0 = np.array([p0 @ e1, p0 @ e2]) * IN
    v0 = np.asarray(ent.get("speedIn", [0, 0]), float) * IN      # in the ball's plane: across, up
    # the start must be clear of the parts and the wheel, or the first step throws the ball out
    rmin = min(pc["diameterIn"] for pc in pieces.values()) / 2
    if len(S):
        AB0 = S[:, 2:] - S[:, :2]
        u0 = np.clip(((p0 / IN - S[:, :2]) * AB0).sum(1) / np.maximum((AB0 * AB0).sum(1), 1e-12), 0, 1)
        gap0 = np.linalg.norm(p0 / IN - (S[:, :2] + u0[:, None] * AB0), axis=1).min() - rmax
        if gap0 < 0:
            print(f"WARNING: the ball's start (entry) is {-gap0:.2f} in inside a part: move \"entry\" (see the picture)")
    if np.linalg.norm(p0 / IN) < R + rmin:
        print("WARNING: the ball's start (entry) is inside the wheel: move \"entry\"")
    g = np.array([0, 0, -9.81])
    g2 = np.array([g @ e1, g @ e2])
    rng = np.random.default_rng(cfg.get("seed", 1))
    ranges = {"ballStiffness": [1500, 8000], "wheelGrip": [0.6, 1.0], "guideGrip": [0.2, 0.5]}
    ranges.update(cfg.get("ranges", {}))
    ncase = int(cfg.get("cases", 40))
    gear = float(cfg.get("gear", 1.0))
    rpms = list(cfg.get("shaftRpm", [1500, 2000, 2500, 3000]))
    spread = float(cfg.get("entrySpreadIn", 0.3)) * IN
    turns = [1, -1] if cfg.get("wheelTurn", "auto") == "auto" else [int(cfg["wheelTurn"])]
    shape = np.asarray(cfg.get("shape", list(centre)), float) - centre
    shape2 = np.array([shape @ e1, shape @ e2])

    # every case: piece x rpm x random draw (x wheel turn when auto)
    rows = []
    for name, pc in pieces.items():
        for rpm in rpms:
            for k in range(ncase):
                for turn in turns:
                    rows.append((name, pc, rpm, k, turn))
    n = len(rows)
    r = np.array([row[1]["diameterIn"] / 2 * IN for row in rows])
    m = np.array([row[1]["massG"] / 1000 for row in rows])
    # inertia: 2/3 m r^2 for a thin-walled ball, 2/5 for a solid one ("inertiaShare" per piece; default thin-walled)
    I = np.array([row[1].get("inertiaShare", 2 / 3) for row in rows]) * m * r * r
    pick = lambda key: rng.uniform(*ranges[key], n)
    kb, mu_w, mu_g = pick("ballStiffness"), pick("wheelGrip"), pick("guideGrip")
    # damping from the piece's bounce (the field file's "bounce": the speed kept after a hit; default 0.3)
    e_ = np.clip(np.array([row[1].get("bounce", 0.3) for row in rows]), 0.01, 0.99)
    zeta = -np.log(e_) / np.sqrt(math.pi ** 2 + np.log(e_) ** 2)
    cb = 2 * zeta * np.sqrt(kb * m)
    wsurf = np.array([row[4] * row[2] * gear * 2 * math.pi / 60 for row in rows])   # wheel spin, rad/s (+ = e1 -> e2)
    Rm = R * IN
    p = np.tile(p0, (n, 1)) + rng.normal(0, spread, (n, 2))
    v = np.tile(v0, (n, 1))
    om = np.zeros(n)
    A0 = S[:, :2] * IN
    AB0 = S[:, 2:] * IN - A0
    L20 = np.maximum((AB0 * AB0).sum(1), 1e-12)
    A, AB, L2 = A0, AB0, L20
    status = np.zeros(n, int)                               # 0 going, 1 shot, 2 did not leave
    free_t = np.zeros(n)
    touched = np.zeros(n, bool)
    exit_p, exit_v, exit_w = np.zeros((n, 2)), np.zeros((n, 2)), np.zeros(n)
    DT, T_END = 2e-5, float(cfg.get("timeS", 0.2))
    feed_n, feed_t = float(cfg.get("feedForceN", 1.0)), float(cfg.get("feedTimeS", 0.05))
    feed_dir = v0 / np.linalg.norm(v0) if np.linalg.norm(v0) > 0 else -p0 / np.linalg.norm(p0)
    t0 = time.time()
    watch = [i for i, row in enumerate(rows) if row[3] < 3]        # a few paths for the picture
    paths = {i: [] for i in watch}

    def friction(fn, slip, mu):
        return -mu * fn * np.tanh(slip / 0.05)

    for step in range(int(T_END / DT)):
        live = status == 0
        if not live.any():
            break
        if step % 100 == 0:
            for i in watch:
                if live[i]:
                    paths[i].append(p[i] / IN)
        Fx = np.tile(g2, (n, 1)) * m[:, None]
        Tq = np.zeros(n)
        contact = np.zeros(n, bool)
        # the wheel
        dvec = p
        dist = np.linalg.norm(dvec, axis=1)
        nrm = dvec / dist[:, None]
        delta = np.minimum(r + Rm - dist, r)
        on = (delta > 0) & live
        tan = np.stack([-nrm[:, 1], nrm[:, 0]], 1)
        vn = (v * nrm).sum(1)
        fn = np.where(on, kb * delta - cb * vn, 0).clip(min=0)
        slip = (v * tan).sum(1) - om * r - wsurf * Rm        # ball surface at -r n; wheel surface at +R n
        ft = friction(fn, slip, mu_w)
        Fx += fn[:, None] * nrm + ft[:, None] * tan
        Tq += -r * ft
        contact |= on
        touched |= on
        # the guides (each outline piece as a thin rod); every 100 steps (2 ms), only the pieces near some ball
        if step % 100 == 0 and len(A0):
            rel = p[live][:, None, :] - A0[None]
            u = np.clip((rel * AB0[None]).sum(2) / L20[None], 0, 1)
            dd = np.linalg.norm(rel - u[..., None] * AB0[None], axis=2)
            near = (dd < (r.max() + 0.06)).any(0) if live.any() else np.zeros(len(A0), bool)
            A, AB, L2 = A0[near], AB0[near], L20[near]
        if len(A):
            rel = p[:, None, :] - A[None]                                  # (n, s, 2)
            u = np.clip((rel * AB[None]).sum(2) / L2[None], 0, 1)
            q = A[None] + u[..., None] * AB[None]
            dv = p[:, None, :] - q
            dd = np.linalg.norm(dv, axis=2)
            dl = r[:, None] + 0.0005 - dd
            hit = (dl > 0) & live[:, None]
            if hit.any():
                nn = dv / np.maximum(dd, 1e-9)[..., None]
                # One push per surface: the outline of one plate is many short pieces (and three cuts), all touching
                # the ball together. Pieces are grouped by the direction they push (12 sectors of 30 degrees) and
                # only the deepest in each group pushes.
                sector = np.floor((np.arctan2(nn[..., 1], nn[..., 0]) + math.pi) / (math.pi / 6)).astype(int) % 12
                for b_ in range(12):
                    mask = hit & (sector == b_)
                    if not mask.any():
                        continue
                    dlm = np.where(mask, dl, -1.0)
                    j = dlm.argmax(1)
                    rows_ = np.nonzero(dlm.max(1) > 0)[0]
                    if not len(rows_):
                        continue
                    jj = j[rows_]
                    nb = nn[rows_, jj]
                    tb = np.stack([-nb[:, 1], nb[:, 0]], 1)
                    db = np.minimum(dl[rows_, jj], r[rows_])           # a squeeze past the ball's radius is not real
                    vn = (v[rows_] * nb).sum(1)
                    fn = (kb[rows_] * db - cb[rows_] * vn).clip(min=0)
                    sl = (v[rows_] * tb).sum(1) - om[rows_] * r[rows_]
                    ftt = friction(fn, sl, mu_g[rows_])
                    Fx[rows_] += fn[:, None] * nb + ftt[:, None] * tb
                    Tq[rows_] += -r[rows_] * ftt
                contact |= hit.any(1)
        # the feeder keeps pushing the ball along its entry direction for the first feedTimeS (a ball that is only
        # dropped would bounce off the wheel)
        if step * DT < feed_t:
            Fx += np.where((~touched)[:, None] | (np.linalg.norm(p, axis=1) < Rm + r + 0.01)[:, None], feed_dir[None] * feed_n, 0)
        v = np.where(live[:, None], v + Fx / m[:, None] * DT, v)
        p = np.where(live[:, None], p + v * DT, p)
        om = np.where(live, om + Tq / I * DT, om)
        free_t = np.where(contact, 0, free_t + DT)
        speed = np.linalg.norm(v, axis=1)
        away = np.linalg.norm(p, axis=1) > Rm + r + 0.004
        # left: touched the wheel, free of everything for 3 ms, moving away from the wheel. A shot if it leaves at
        # least 30 % of the wheel's surface speed (slower: it bounced off or dribbled out, not thrown)
        outward = (p * v).sum(1) > 0
        left = live & touched & (free_t > 0.003) & away & outward
        done = left & (speed > 0.3 * np.abs(wsurf) * Rm)
        exit_p[done], exit_v[done], exit_w[done] = p[done], v[done], om[done]
        status[done] = 1
        status[left & ~done & (step * DT > feed_t)] = 2       # slow and free: dropped (the feeder may still push it)
        lost = live & (np.linalg.norm(p, axis=1) > Rm + 0.25)
        status[lost & ~done] = 2
    status[status == 0] = 2
    print(f"{n} shots in {time.time() - t0:.0f} s")

    # which way the wheel turns: the one that shoots more
    if len(turns) == 2:
        rate = {t: np.mean([status[i] == 1 for i, row in enumerate(rows) if row[4] == t]) for t in turns}
        turn = max(rate, key=rate.get)
        for t in turns:
            ok = [i for i, row in enumerate(rows) if row[4] == t and status[i] == 1]
            if ok:
                V = exit_v[ok]
                ang = np.degrees(np.arctan2(V[:, 1], V[:, 0]))
                print(f"wheel turn {t:+d}: {100 * rate[t]:.0f} % shot, leaving at {np.median(ang):.0f} deg (median)")
            else:
                print(f"wheel turn {t:+d}: no shots")
        if min(rate.values()) > 0.2:
            print(f"WARNING: both ways shoot. Using {turn:+d} (more shots), but ask the team: does the front of the "
                  "wheel move up (+1) or down (-1) when it shoots? Then set \"wheelTurn\".")
    else:
        turn = turns[0]
    table = {"source": f"tools/launcher/launcher_lab.py from {d['step']}: {ncase} cases per row, ranges {ranges}; "
                       f"entry {ent}; wheel {2 * R:.2f} in, turn {turn:+d}, gear {gear}",
             "pieces": {}}
    print(f"\n{'piece':12s} {'rpm':>5s} {'shot':>5s}  {'speed in/s':>14s}  {'angle deg':>12s}  {'backspin rad/s':>15s}  exit (f, u)")
    for name in pieces:
        out = []
        for rpm in rpms:
            idx = [i for i, row in enumerate(rows) if row[0] == name and row[2] == rpm and row[4] == turn]
            ok = [i for i in idx if status[i] == 1]
            if not ok:
                # kept, so the simulator knows nothing leaves at this speed (it would otherwise scale another row)
                out.append({"shaftRpm": rpm, "shot": 0, "speedIn": [0, 0], "angleDeg": [0, 0], "backspin": [0, 0], "exit": [0, 0]})
                print(f"{name:12s} {rpm:5d}  none left the launcher")
                continue
            V = exit_v[ok] / IN
            spd = np.linalg.norm(V, axis=1)
            # angles as directions: their mean direction, and the spread around it (179 and -179 are 2 degrees apart)
            ang_r = np.arctan2(V[:, 1], V[:, 0])
            mean_r = math.atan2(np.sin(ang_r).mean(), np.cos(ang_r).mean())
            ang = np.degrees(mean_r + (ang_r - mean_r + math.pi) % (2 * math.pi) - math.pi)
            back = exit_w[ok] * np.sign(V[:, 0] + 1e-12)
            ex = exit_p[ok] / IN - shape2
            row = {"shaftRpm": rpm, "shot": round(len(ok) / len(idx), 3),
                   "speedIn": [round(float(spd.mean()), 1), round(float(spd.std()), 1)],
                   "angleDeg": [round(float(ang.mean()), 1), round(float(ang.std()), 1)],
                   "backspin": [round(float(back.mean()), 1), round(float(back.std()), 1)],
                   "exit": [round(float(ex[:, 0].mean()), 2), round(float(ex[:, 1].mean()), 2)]}
            out.append(row)
            print(f"{name:12s} {rpm:5d} {row['shot']:5.2f}  {row['speedIn'][0]:7.1f} +-{row['speedIn'][1]:5.1f}"
                  f"  {row['angleDeg'][0]:6.1f} +-{row['angleDeg'][1]:4.1f}  {row['backspin'][0]:8.1f} +-{row['backspin'][1]:5.1f}"
                  f"  {row['exit']}")
        table["pieces"][name] = out
    if cfg.get("picture"):
        picture(path(cfg["picture"]), S, R, p0 / IN, shape2, paths, rows, status, turn)
    out_path = path(cfg.get("out", "launcher-table.json"))
    json.dump(table, open(out_path, "w"), indent=1)
    print(f"\nwrote {out_path}")


def picture(fn, S, R, entry, shape2, paths, rows, status, turn):
    """An SVG of the cut: the outline pieces, the wheel, where the ball starts, and a few paths (green: shot)."""
    span = R + 7
    k = 40.0                                     # px per inch
    def xy(x, y):
        return (x + span) * k, (span - y) * k
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{2 * span * k:.0f}" height="{2 * span * k:.0f}" '
           f'style="background:#fff">']
    for x0, y0, x1, y1 in S:
        a, b = xy(x0, y0), xy(x1, y1)
        out.append(f'<line x1="{a[0]:.1f}" y1="{a[1]:.1f}" x2="{b[0]:.1f}" y2="{b[1]:.1f}" stroke="#555" stroke-width="1"/>')
    c = xy(0, 0)
    out.append(f'<circle cx="{c[0]:.1f}" cy="{c[1]:.1f}" r="{R * k:.1f}" fill="none" stroke="#1f6feb" stroke-width="2"/>')
    e = xy(*entry)
    out.append(f'<circle cx="{e[0]:.1f}" cy="{e[1]:.1f}" r="5" fill="#d29922"/>')
    sh = xy(*shape2)
    out.append(f'<circle cx="{sh[0]:.1f}" cy="{sh[1]:.1f}" r="3" fill="#1f6feb"/>')
    for i, pts in paths.items():
        if rows[i][4] != turn or len(pts) < 2:
            continue
        col = "#2da44e" if status[i] == 1 else "#cf222e"
        d = " ".join(f"{xy(*q)[0]:.1f},{xy(*q)[1]:.1f}" for q in pts)
        out.append(f'<polyline points="{d}" fill="none" stroke="{col}" stroke-width="1.5"/>')
    out.append(f'<text x="8" y="18" font-size="14">forward -> right, up -> up; inches from the wheel centre x {k:.0f} px</text>')
    out.append("</svg>")
    open(fn, "w").write("\n".join(out))
    print(f"picture: {fn}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    cfg_path = sys.argv[1]
    run(json.load(open(cfg_path)), os.path.dirname(os.path.abspath(cfg_path)))
