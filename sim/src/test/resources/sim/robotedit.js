// The simulator page's Edit robot mode (docs/guides/simulator.md, "Edit robot: shape your robot"): move and size
// the robot's shapes in the 3D view, check them against the size rules in every pose, and copy the .looks(...)
// Java into Robot.java. The simulator does the shapes and the Java (SimRobotEdit); this file is the 3D view of
// the robot alone, the panel and the drag. Nothing here writes a file: the student copies the Java.
//
// Units: the panel and the Java use mm and degrees (atMm, sizeMm); the 3D view uses inches like the rest of the
// page. Robot frame (f forward, l left, u up) -> the page's: x = -l, y = f, z = up.
"use strict";
(() => {
  const $ = (s) => document.querySelector(s);
  const esc = (t) => String(t == null ? "" : t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const MM = 25.4;
  const E = window.simEdit = { on: false, scene: null, frame: () => {} };

  let info = null;        // GET /robot-edit: the shapes as read, whose part each is
  let edits = {};         // shape -> its changes { atMm, sizeMm, lengthMm, diameterMm, axis, tiltDeg, angles, range, startAt, travelMm }
  let values = {};        // shape -> the pose shown (degrees, or inches along a slide); none = the start of the match
  let shotAngles = {};    // launcher -> its changed shot angle, degrees
  let view = null;        // POST /robot-edit/preview: the pieces, the frames, the size at every pose
  let java = null;        // POST /robot-edit/java
  let sel = null;         // the shape picked
  let tool = "move";      // move or size
  let showAll = false;    // the Java for every part, not only the changed ones
  let saved = null;       // the 3D view before the mode (camera, controls)
  let say = "";           // a short note under the buttons (Try it, Copy...)
  let problem = "";       // what the simulator said about the last change
  let framed = false;

  const shape = (n) => info && info.shapes.find((s) => s.name === n);

  // ------------------------------------------------------------ talking to the simulator
  async function getJSON(url) {
    const r = await fetch(url, { cache: "no-store" });
    return r.json();
  }
  async function postJSON(url, data) {
    const r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
    return r.json();
  }
  function setMode(on) {
    return fetch("/robot-edit/mode", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: "on=" + (on ? 1 : 0) })
      .catch(() => {});
  }

  // One preview at a time: a change while one is on its way asks again when it comes back.
  let busy = false, again = false;
  async function refresh() {
    if (busy) { again = true; return; }
    busy = true;
    try {
      const v = await postJSON("/robot-edit/preview", { edits, values, shotAngles });
      if (v.error) problem = v.error;
      else {
        problem = ""; view = v;
        if (!Object.keys(values).length) { startValues = {}; for (const [k, r] of Object.entries(v.values || {})) startValues[k] = r[0]; }
        draw();
      }
    } catch (e) { problem = "No answer from the simulator."; }
    busy = false;
    if (again) { again = false; refresh(); return; }
    // the picked shape's numbers and pose buttons again, unless one of its boxes is being typed in
    const a = document.activeElement;
    if (!(a && a.closest && a.closest("#eShape") && a.matches("input, select"))) renderShape();
    renderChecks();
    askJava();
  }
  let javaTimer = 0;
  function askJava() {
    clearTimeout(javaTimer);
    javaTimer = setTimeout(async () => {
      try { java = await postJSON("/robot-edit/java", { edits, all: showAll, shotAngles }); } catch (e) { java = { error: "No answer from the simulator." }; }
      renderJava();
    }, 120);
  }
  function changed() { say = ""; refresh(); }

  // ------------------------------------------------------------ open and close

  async function enter() {
    if (E.on) return;
    if (document.body.classList.contains("building")) $("#builderBtn").click();
    set3d(true);
    for (let i = 0; i < 100 && !v3.ready; i++) await new Promise((ok) => setTimeout(ok, 50));
    if (!v3.ready) return;
    try { info = await getJSON("/robot-edit"); } catch (e) { return; }
    edits = JSON.parse(JSON.stringify(info.tried || {}));
    shotAngles = JSON.parse(JSON.stringify(info.triedAngles || {}));
    values = {};
    await setMode(true);
    if (!E.scene) build();
    E.scene.background = v3.scene.background;
    saved = { pos: v3.camera.position.clone(), target: v3.controls.target.clone(), view: v3.view,
      minD: v3.controls.minDistance, maxD: v3.controls.maxDistance, maxP: v3.controls.maxPolarAngle };
    v3.controls.minDistance = 4; v3.controls.maxDistance = 200; v3.controls.maxPolarAngle = Math.PI;
    framed = false;
    E.on = true;
    if (typeof setEdit === "function") setEdit(false);
    document.body.classList.add("editing");
    $("#editSection").hidden = false;
    $("#editRobotBtn").classList.add("on");
    $("#editRobotBtn").setAttribute("aria-pressed", "true");
    sel = null;
    render();
    resize3d();
    refresh();
  }

  async function leave() {
    if (!E.on) return;
    E.on = false;
    document.body.classList.remove("editing");
    $("#editSection").hidden = true;
    $("#editRobotBtn").classList.remove("on");
    $("#editRobotBtn").setAttribute("aria-pressed", "false");
    v3.controls.minDistance = saved.minD; v3.controls.maxDistance = saved.maxD; v3.controls.maxPolarAngle = saved.maxP;
    if (saved.view && saved.view !== "free") setView(saved.view);
    else { v3.camera.position.copy(saved.pos); v3.controls.target.copy(saved.target); v3.controls.update(); }
    resize3d();
    await setMode(false);
  }

  $("#editRobotBtn").onclick = () => (E.on ? leave() : enter());
  $("#editBackBtn").onclick = () => leave();

  // ------------------------------------------------------------ the 3D view of the robot alone

  let robot, guides, labels;
  function build() {
    const scene = new THREE.Scene();
    scene.add(new THREE.HemisphereLight(0xffffff, 0x667788, 0.9));
    const sun = new THREE.DirectionalLight(0xffffff, 0.7); sun.position.set(-40, -60, 120); scene.add(sun);
    const fill = new THREE.DirectionalLight(0xffffff, 0.35); fill.position.set(60, 40, 60); scene.add(fill);
    const grid = new THREE.GridHelper(48, 16, 0x8a96a3, 0x8a96a3);   // 3 in squares on the tiles
    grid.rotation.x = Math.PI / 2;
    grid.material.transparent = true; grid.material.opacity = 0.35;
    scene.add(grid);
    robot = new THREE.Group(); scene.add(robot);
    guides = new THREE.Group(); scene.add(guides);
    labels = new THREE.Group(); scene.add(labels);
    E.scene = scene;
    E.frame = () => {};
  }

  function disposeGroup(g) {
    for (const o of [...g.children]) {
      g.remove(o);
      o.traverse((x) => {
        if (x.geometry) x.geometry.dispose();
        if (x.material) { if (x.material.map) x.material.map.dispose(); x.material.dispose(); }
      });
    }
  }

  // A piece's frame in the page's axes (as the main view's poseShapes does it).
  function pieceMatrix(p) {
    const m = new THREE.Matrix4();
    m.makeBasis(new THREE.Vector3(-p.ef[1], p.ef[0], p.ef[2]), new THREE.Vector3(-p.el[1], p.el[0], p.el[2]),
      new THREE.Vector3(-p.eu[1], p.eu[0], p.eu[2]));
    m.setPosition(-p.c[1], p.c[0], p.c[2]);
    return m;
  }

  // The 8 corners of a piece (robot frame inches).
  function corners(p) {
    const out = [];
    for (const a of [-0.5, 0.5]) for (const b of [-0.5, 0.5]) for (const c of [-0.5, 0.5]) {
      out.push([0, 1, 2].map((k) => p.c[k] + a * p.size[0] * p.ef[k] + b * p.size[1] * p.el[k] + c * p.size[2] * p.eu[k]));
    }
    return out;
  }

  // ------------------------------------------------------------ the size check of the pose shown
  // At the start of the match the robot fits an 18 in cube (R102); after the start, 18 x 24 in and 29 in tall
  // (R105). The box is placed where the robot fits best, but always round the chassis.
  function atStart() {
    return Object.keys(values).length === 0;
  }
  function check() {
    if (!view) return null;
    const pcs = view.pieces, chassis = pcs.find((p) => p.name === "chassis");
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    pcs.forEach((p) => corners(p).forEach((c) => { for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], c[k]); hi[k] = Math.max(hi[k], c[k]); } }));
    const start = atStart();
    const place = (L, k) => {
      const half = chassis ? chassis.size[k] / 2 : 0, room = Math.max(0, L / 2 - half);
      const mid = Math.max(-room, Math.min(room, (lo[k] + hi[k]) / 2));
      return [mid - L / 2, mid + L / 2];
    };
    const over = (box) => Math.max(0, box[0][0] - lo[0], hi[0] - box[0][1]) + Math.max(0, box[1][0] - lo[1], hi[1] - box[1][1]);
    let box;
    if (start) box = [place(18, 0), place(18, 1), [0, 18]];
    else {
      const a = [place(24, 0), place(18, 1), [0, 29]], b = [place(18, 0), place(24, 1), [0, 29]];
      box = over(b) < over(a) ? b : a;
    }
    const out = [];   // [shape, words]
    const red = new Set();
    const seen = new Set();
    for (const p of pcs) {
      let worst = 0, way = "";
      const ways = [["forward", 0, 1], ["back", 0, 0], ["left", 1, 1], ["right", 1, 0], ["up", 2, 1]];
      for (const c of corners(p)) {
        for (const [w, k, end] of ways) {
          const d = end ? c[k] - box[k][1] : box[k][0] - c[k];
          if (d > worst) { worst = d; way = w; }
        }
        if (-c[2] > 0.1 && -c[2] > worst) { worst = -c[2]; way = "below the tiles"; }
      }
      if (worst > 0.02) {
        red.add(p.name);
        if (!seen.has(p.name)) {
          seen.add(p.name);
          const mm = Math.round(worst * MM);
          out.push([p.name, way === "below the tiles" ? `goes ${mm} mm below the tiles`
            : way === "up" ? `is ${mm} mm too tall (more than ${start ? 18 : 29} in)`
            : `sticks out ${mm} mm (${way}) of the ${start ? "18 in start cube" : "18 x 24 in box"}`]);
        }
      }
    }
    return { box, red, out, start, size: [hi[0] - lo[0], hi[1] - lo[1], hi[2]] };
  }

  function draw() {
    if (!E.scene || !view) return;
    disposeGroup(robot); disposeGroup(guides); disposeGroup(labels);
    const c = check();
    for (const p of view.pieces) {
      const cyl = p.kind === "cyl";
      const geo = cyl ? new THREE.CylinderGeometry(p.size[0] / 2, p.size[0] / 2, p.size[1], 24) : new THREE.BoxGeometry(...p.size);
      const isChassis = p.name === "chassis", isSel = p.name === sel, isRed = c.red.has(p.name);
      const mat = new THREE.MeshPhongMaterial({ color: isRed ? 0xe5534b : p.color, shininess: 25, transparent: isChassis || (sel && !isSel),
        opacity: isChassis ? 0.55 : sel && !isSel ? 0.75 : 1 });
      if (isSel) mat.emissive = new THREE.Color(0x2a4a7a);
      const m = new THREE.Mesh(geo, mat);
      m.matrixAutoUpdate = false;
      m.matrix.copy(pieceMatrix(p));
      m.userData.shape = p.name;
      const e = new THREE.LineSegments(new THREE.EdgesGeometry(geo),
        new THREE.LineBasicMaterial({ color: isSel ? 0xffffff : isRed ? 0x9b1c1c : 0x1b1f24, transparent: !isSel, opacity: isSel ? 1 : 0.4 }));
      e.userData.shape = p.name;
      m.add(e);
      robot.add(m);
    }
    // each launcher: an arrow the way its shots leave, so the launcher is easy to find among the boxes
    for (const sh of view.shots || []) {
      const dir = new THREE.Vector3(-sh.dir[1], sh.dir[0], sh.dir[2]).normalize();
      const from = new THREE.Vector3(-sh.from[1], sh.from[0], sh.from[2]);
      const arrow = new THREE.ArrowHelper(dir, from, 10, 0xd29922, 2.6, 1.6);
      arrow.line.material.linewidth = 2;
      guides.add(arrow);
      const t = SimView.label(THREE, `${sh.name}: shots leave this way`, { heightIn: 1.1, background: "rgba(154,103,0,.92)" });
      t.position.copy(from).addScaledVector(dir, 11).add(new THREE.Vector3(0, 0, 1.5));
      labels.add(t);
    }
    // a word over each part that does not fit (red is also some parts' own colour)
    for (const name of c.red) {
      const pcs = view.pieces.filter((p) => p.name === name);
      let top = null;
      pcs.forEach((p) => corners(p).forEach((q) => { if (!top || q[2] > top[2]) top = q; }));
      const t = SimView.label(THREE, `${name}: does not fit`, { heightIn: 1.1, background: "rgba(207,34,46,.92)" });
      t.position.set(-top[1], top[0], top[2] + 1.2);
      labels.add(t);
    }
    // the guides: the box the robot must fit in now (see-through), and the 29 in line
    const [bf, bl, bu] = c.box;
    const w = bl[1] - bl[0], l = bf[1] - bf[0], h = bu[1];
    const boxGeo = new THREE.BoxGeometry(w, l, h);
    const glass = new THREE.Mesh(boxGeo, new THREE.MeshBasicMaterial({ color: c.red.size ? 0xe5534b : 0x2ecc71, transparent: true, opacity: 0.06, depthWrite: false }));
    const frame = new THREE.LineSegments(new THREE.EdgesGeometry(boxGeo), new THREE.LineDashedMaterial({ color: c.red.size ? 0xe5534b : 0x2ecc71, dashSize: 0.8, gapSize: 0.6 }));
    frame.computeLineDistances();
    [glass, frame].forEach((o) => o.position.set(-(bl[0] + bl[1]) / 2, (bf[0] + bf[1]) / 2, h / 2));
    guides.add(glass, frame);
    // the 29 in height line all round (dashed), with the footprint the match allows under it
    const line = (pts, color) => {
      const g = new THREE.BufferGeometry().setFromPoints(pts.map((q) => new THREE.Vector3(...q)));
      const o = new THREE.Line(g, new THREE.LineDashedMaterial({ color, dashSize: 1, gapSize: 0.8 }));
      o.computeLineDistances();
      guides.add(o);
    };
    const x0 = -bl[1], x1 = -bl[0];
    line([[x0, bf[0], 29], [x1, bf[0], 29], [x1, bf[1], 29], [x0, bf[1], 29], [x0, bf[0], 29]], 0xf2c230);
    const t1 = SimView.label(THREE, c.start ? "18 in start cube" : "18 x 24 in, after the start", { heightIn: 1.3 });
    t1.position.set(x1 + 1, bf[0], h + 0.8); labels.add(t1);
    const t2 = SimView.label(THREE, "29 in", { heightIn: 1.3, background: "rgba(122,90,0,.85)" });
    t2.position.set(x0 - 1, bf[1], 29); labels.add(t2);
    const front = SimView.label(THREE, "FRONT", { heightIn: 1.4 });
    front.position.set(0, bf[1] + 2.5, 0.8); labels.add(front);
    if (!framed) {
      framed = true;
      // close, from the front left and above, looking at the middle of the robot
      const mid = new THREE.Vector3(0, 0, Math.min(10, (c.size[2] || 12) / 2));
      const r = Math.max(14, Math.max(c.size[0], c.size[1], c.size[2]) * 1.9);
      v3.controls.target.copy(mid);
      v3.camera.position.set(mid.x - r * 0.75, mid.y + r * 0.9, mid.z + r * 0.6);
      v3.controls.update();
    }
  }

  // ------------------------------------------------------------ the panel

  const num = (v) => (v == null || !isFinite(v) ? "" : Math.round(v));
  const WAYS = ["forward", "left", "up"];
  const SIZE_WAYS = ["forward", "left-right", "up"];

  // A shape's numbers now: as read, with its changes.
  function now(s) {
    const e = edits[s.name] || {};
    return {
      at: e.atMm || s.at, size: e.sizeMm || s.size, length: e.lengthMm ?? s.lengthMm, diameter: e.diameterMm ?? s.diameterMm,
      axis: e.axis || s.axis, tilt: e.tiltDeg ?? +s.tilt, angles: Object.assign({}, s.angles, e.angles || {}),
      range: e.range || (s.passive && s.type === "arm" ? [s.min, s.max] : null), startAt: e.startAt ?? (s.passive && s.type === "arm" ? s.start : null),
      travel: e.travelMm ?? s.travelMm,
    };
  }
  function setEditValue(name, key, value) {
    const s = shape(name);
    const e = edits[name] || (edits[name] = {});
    e[key] = value;
    // a change back to what was read is no change
    const was = { atMm: s.at, sizeMm: s.size, lengthMm: s.lengthMm, diameterMm: s.diameterMm, axis: s.axis, tiltDeg: +s.tilt,
      startAt: s.passive ? s.start : null, travelMm: s.travelMm, range: s.passive ? [s.min, s.max] : null, angles: s.angles }[key];
    if (JSON.stringify(was) === JSON.stringify(value) || (key === "angles" && Object.keys(value).every((k) => Math.abs(value[k] - s.angles[k]) < 1e-6))) delete e[key];
    if (!Object.keys(e).length) delete edits[name];
  }

  // The named poses of a shape: [label, value] (degrees, or inches for a slide or a claw).
  function poses(s) {
    const out = [];
    const r = view && view.values && view.values[s.name];
    if (!s.moves || !r) return out;
    const n = now(s);
    let p0 = s.pos0, p1 = s.pos1;
    if (s.angleAt && Object.keys(s.angles).length === 2) {
      // the two named angles decide the servo's numbers (like Robot.java does)
      const [a, b] = Object.keys(s.angles), va = s.angleAt[0], vb = s.angleAt[1];
      const per = (n.angles[b] - n.angles[a]) / (vb - va);
      p0 = n.angles[a] - per * va; p1 = p0 + per;
    }
    for (const [name, pos] of Object.entries(s.servoPositions || {})) out.push([name, p0 + pos * (p1 - p0)]);
    if (s.passive && s.type === "arm") {
      out.push(["start", n.startAt], ["bottom stop", r[1]], ["top stop", r[2]]);
    } else if (!out.length) {
      if (s.type === "slide") out.push(["down", r[1]], ["up", r[2]]);
      else if (s.type === "claw") out.push(["closed", r[1]], ["open", r[2]]);
      else out.push(["one end", r[1]], ["other end", r[2]]);
    }
    return out;
  }
  // "Every part out": each moving shape at its named pose farthest from where the match starts.
  let startValues = {};   // shape -> its value at the start of the match (from the last preview at the start)
  function outPose() {
    const out = {};
    for (const s of (info ? info.shapes : [])) {
      const st = startValues[s.name];
      const ps = poses(s);
      if (st == null || !ps.length) continue;
      let best = null;
      for (const [, v] of ps) if (best == null || Math.abs(v - st) > Math.abs(best - st)) best = v;
      if (best != null && Math.abs(best - st) > 1e-6) out[s.name] = best;
    }
    return out;
  }
  function outNow() {
    const want = outPose(), keys = Object.keys(want);
    return keys.length > 0 && keys.length === Object.keys(values).length && keys.every((k) => Math.abs(want[k] - values[k]) < 1e-6);
  }
  const poseWords = (s, v) => (s.unit === "deg" ? `${Math.round(v)}&deg;` : `${Math.round(v * MM)} mm`);

  function render() {
    const p = $("#editPanel");
    if (!info) { p.innerHTML = ""; return; }
    const list = info.shapes.map((s) => `<button class="e-chip${s.name === sel ? " on" : ""}${edits[s.name] ? " changed" : ""}" data-pick="${esc(s.name)}"
      title="${esc(s.name + ": " + (s.part || "robot file"))}">${esc(s.name)}${s.where === "guess" ? ' <span class="e-tag">guess</span>' : ""}${edits[s.name] ? " *" : ""}</button>`).join("");
    p.innerHTML = `
      <p class="muted e-intro">${info.java ? `Shapes from <code>${esc(info.robotClass.split(".").slice(-2).join("."))}</code>. Change them here, then copy the Java into Robot.java.`
        : esc(info.why)} The simulator holds still while you edit.</p>
      <div class="e-list" role="group" aria-label="Shapes">${list || '<span class="muted">This robot has no shapes yet.</span>'}</div>
      <div id="eShape"></div>
      <div class="e-pose row"><b>Pose:</b> <button data-start class="${atStart() ? "on" : ""}">Start of the match</button>
        <button data-out class="${outNow() ? "on" : ""}" title="Every moving part at the named pose farthest from where it starts (an intake deployed, a slide up): easier to see and change">Every part out</button>
        <span class="muted" id="ePoseNote">${atStart() ? "every part where it starts" : outNow() ? "every moving part out" : "parts moved"}. Click a part for its own poses.</span></div>
      <div id="eShots"></div>
      <div id="eChecks" role="status"></div>
      <div class="row e-actions"><button id="eTry" class="primary" title="The simulator uses these shapes now (in memory only): go back to driving to see pieces hit them">Try it</button>
        <button id="eUndoAll" title="Every shape back as the robot file has it">Undo all</button>
        <span class="e-say" id="eSay">${esc(say)}</span></div>
      <h3>Copy into Robot.java</h3>
      <label class="muted"><input type="checkbox" id="eAll" ${showAll ? "checked" : ""}> Show every part (not only the changed ones)</label>
      <div id="eJava"></div>`;
    p.querySelectorAll("[data-pick]").forEach((b) => (b.onclick = () => pick(b.dataset.pick)));
    p.querySelector("[data-start]").onclick = () => { values = {}; render(); refresh(); };
    p.querySelector("[data-out]").onclick = () => { values = outPose(); render(); refresh(); };
    renderShots();
    $("#eTry").onclick = tryIt;
    $("#eUndoAll").onclick = async () => {
      edits = {}; shotAngles = {}; values = {}; say = "";
      if (Object.keys(info.tried || {}).length || Object.keys(info.triedAngles || {}).length) {
        await postJSON("/robot-edit/try", { edits: {}, shotAngles: {} });
        info.tried = {}; info.triedAngles = {};
        say = "The simulator is back to the robot file's shapes and shot angles.";
      }
      render(); refresh();
    };
    $("#eAll").onchange = (ev) => { showAll = ev.target.checked; askJava(); };
    renderShape();
    renderChecks();
    renderJava();
  }

  function pick(name) {
    sel = sel === name ? null : name;
    render();
    draw();
  }

  function field(key, i, label, value, unit) {
    return `<label>${label} <input type="number" step="1" data-k="${key}" data-i="${i}" value="${num(value)}"> ${unit}</label>`;
  }

  function renderShape() {
    const box = $("#eShape");
    const s = sel && shape(sel);
    if (!s) { box.innerHTML = '<p class="muted">Click a shape (in the 3D view or above) to change it.</p>'; return; }
    if (s.name === "chassis") { box.innerHTML = ""; return; }
    const n = now(s);
    const where = { looks: `From the .looks(...) of ${esc(s.part)}.`, guess: `A guess (TODO measure): ${esc(s.part)} has no .looks(...) yet. Change it, then add the Java.`,
      fixed: "From fixed(...) in Robot.java.", file: "From the robot file only." }[s.where] || "";
    let h = `<div class="e-sel"><b>${esc(s.name)}</b> <span class="muted">(${esc(s.type)}${s.passive ? ", moved by gravity or springs" : ""})</span>
      <div class="muted">${where}</div></div>
      <div class="row e-tools" role="group" aria-label="What a drag changes"><span class="e-seg"><button data-tool="move" class="${tool === "move" ? "on" : ""}">Move</button><button data-tool="size" class="${tool === "size" ? "on" : ""}">Size</button></span>
        <span class="muted">Drag it: flat. Shift + drag: up and down. Arrow keys, Page Up/Down: 1 mm (Shift: 10 mm).</span></div>
      <div class="e-grid"><span class="e-k">Place <code>atMm</code></span>${WAYS.map((w, i) => field("atMm", i, w, n.at[i], "mm")).join("")}</div>`;
    if (s.spins) {
      h += `<div class="e-grid"><span class="e-k">Size</span>${field("lengthMm", -1, "length", n.length, "mm")}
        ${s.catalog ? `<span class="muted">diameter ${num(n.diameter)} mm (from the parts catalog)</span>` : field("diameterMm", -1, "diameter", n.diameter, "mm")}</div>`;
    } else {
      h += `<div class="e-grid"><span class="e-k">Size <code>sizeMm</code></span>${SIZE_WAYS.map((w, i) => field("sizeMm", i, w, n.size[i], "mm")).join("")}</div>`;
    }
    const axes = ["up", "down", "forward", "back", "left", "right"].filter((a) => !(s.type === "arm" && (a === "forward" || a === "back")));
    if (s.type !== "claw" && s.type !== "box" && s.type !== "turret") {
      h += `<div class="e-grid"><span class="e-k">Turns about</span><label>axis <select data-k="axis">${axes.map((a) => `<option${a === n.axis ? " selected" : ""}>${a}</option>`).join("")}</select></label></div>`;
    }
    h += `<div class="e-grid"><span class="e-k">Tilt</span>${field("tiltDeg", -1, "front up", n.tilt, "&deg;")}</div>`;
    if (Object.keys(s.angles).length === 2 && s.angleAt) {
      h += `<div class="e-grid"><span class="e-k">Named angles</span>${Object.keys(s.angles).map((a) => `<label>${esc(a)} <input type="number" step="1" data-k="angles" data-a="${esc(a)}" value="${num(n.angles[a])}"> &deg;</label>`).join("")}</div>`;
    }
    if (s.passive && s.type === "arm") {
      h += `<div class="e-grid"><span class="e-k">Hard stops <code>range</code></span>${field("range", 0, "lowest", n.range[0], "&deg;")}${field("range", 1, "highest", n.range[1], "&deg;")}
        ${field("startAt", -1, "start", n.startAt, "&deg;")}</div>`;
    }
    if (s.travelMm != null) h += `<div class="e-grid"><span class="e-k">Travel</span>${field("travelMm", -1, "stage moves", n.travel, "mm")}</div>`;
    const ps = poses(s);
    if (ps.length) {
      const cur = view && view.values[s.name] ? view.values[s.name][0] : null;
      h += `<div class="e-grid"><span class="e-k">Poses</span><span class="row">${ps.map(([name, v]) =>
        `<button data-pose="${v}" class="${cur != null && !atStart() && Math.abs(cur - v) < 1e-3 && values[s.name] != null ? "on" : ""}">${esc(name)} <span class="muted">${poseWords(s, v)}</span></button>`).join("")}</span></div>`;
    }
    h += `<div class="row"><button data-undo>Undo changes to ${esc(s.name)}</button></div>`;
    if (problem) h += `<p class="e-bad">${esc(problem)}</p>`;
    box.innerHTML = h;
    box.querySelectorAll("[data-tool]").forEach((b) => (b.onclick = () => { tool = b.dataset.tool; renderShape(); }));
    box.querySelectorAll("input[data-k]").forEach((inp) => {
      inp.addEventListener("keydown", (ev) => {
        // Shift + arrow: 10 at a time (the box's own arrows go 1)
        if (ev.shiftKey && (ev.key === "ArrowUp" || ev.key === "ArrowDown")) {
          ev.preventDefault();
          inp.value = (+inp.value || 0) + (ev.key === "ArrowUp" ? 10 : -10);
          fromInput(s, inp);
        }
      });
      inp.addEventListener("input", () => fromInput(s, inp));
    });
    const ax = box.querySelector("select[data-k=axis]");
    if (ax) ax.onchange = () => { setEditValue(s.name, "axis", ax.value); changed(); renderList(); };
    box.querySelectorAll("[data-pose]").forEach((b) => (b.onclick = () => { values[s.name] = +b.dataset.pose; render(); refresh(); }));
    box.querySelector("[data-undo]").onclick = () => { delete edits[s.name]; render(); changed(); };
  }

  function fromInput(s, inp) {
    if (inp.value === "" || !isFinite(+inp.value)) return;
    const v = Math.round(+inp.value), k = inp.dataset.k, i = +inp.dataset.i, n = now(s);
    if (k === "atMm" || k === "sizeMm") { const a = (k === "atMm" ? n.at : n.size).slice(); a[i] = v; setEditValue(s.name, k, a); }
    else if (k === "range") { const a = n.range.slice(); a[i] = v; setEditValue(s.name, k, a); }
    else if (k === "angles") { const a = Object.assign({}, n.angles); a[inp.dataset.a] = v; setEditValue(s.name, k, a); }
    else setEditValue(s.name, k, v);
    changed();
    renderList();
  }

  // the shape list's "changed" marks, without drawing the rest again (a box being typed in keeps its place)
  function renderList() {
    document.querySelectorAll("#editPanel [data-pick]").forEach((b) => {
      const name = b.dataset.pick;
      b.classList.toggle("changed", !!edits[name]);
    });
  }

  function renderChecks() {
    const el = $("#eChecks");
    if (!el) return;
    if (problem) { el.innerHTML = `<p class="e-bad">${esc(problem)}</p>`; return; }
    const c = check();
    if (!c) { el.innerHTML = ""; return; }
    let h = c.out.length ? `<ul class="e-bad">${c.out.map(([n, w]) => `<li><b>${esc(n)}</b> ${esc(w)}.</li>`).join("")}</ul>`
      : `<p class="e-good">This pose fits ${c.start ? "the 18 in start cube" : "18 x 24 in, 29 in tall"}.</p>`;
    const m = view.most;
    h += m.ok ? `<p class="e-good">Every pose fits 18 x 24 in, 29 in tall (${m.poses} poses checked).</p>`
      : `<p class="e-bad">Not every pose fits: the worst is ${esc(m.pose)}.</p>`;
    el.innerHTML = h;
  }

  // Each launcher's shot angle: the arrow follows it. Shapes (a ramp) do not change a shot: the simulator
  // throws the piece from the launcher at this angle.
  function renderShots() {
    const el = $("#eShots");
    if (!el) return;
    const list = info.launchers || [];
    if (!list.length) { el.innerHTML = ""; return; }
    el.innerHTML = '<h3>Shot angle</h3>' + list.map((l) => l.by
      ? `<p class="muted">${esc(l.name)}: its ${esc(l.by)} sets the angle.</p>`
      : `<div class="row"><label>${esc(l.name)} <input type="number" min="0" max="180" step="1" data-shot="${esc(l.name)}" value="${shotAngles[l.name] ?? l.angle}"> degrees</label></div>`).join("")
      + '<p class="muted">Up from the launcher\'s front: 90 = straight up, more than 90 = out of the back. The arrow shows it. Moving a shape (a ramp) does not change it.</p>';
    el.querySelectorAll("[data-shot]").forEach((box) => (box.oninput = () => {
      const l = list.find((x) => x.name === box.dataset.shot), v = parseFloat(box.value);
      if (!Number.isFinite(v)) return;
      if (v === l.angle) delete shotAngles[l.name]; else shotAngles[l.name] = v;
      changed();
    }));
  }

  function renderJava() {
    const el = $("#eJava");
    if (!el) return;
    if (!java) { el.innerHTML = ""; return; }
    if (java.error) { el.innerHTML = `<p class="e-bad">${esc(java.error)}</p>`; return; }
    if (!java.parts.length) { el.innerHTML = '<p class="muted">No changes yet. Move or size a shape, and its Java shows here.</p>'; return; }
    el.innerHTML = java.parts.map((p, i) => `<div class="e-part"><div class="row"><b>${esc(p.name)}</b>
        <button data-copy="${i}" title="Copy this part's Java">Copy</button></div>
        <p class="muted">${esc(p.where)}</p>${p.warn ? `<p class="e-warn">${esc(p.warn)}</p>` : ""}<pre class="e-code">${esc(p.java)}</pre></div>`).join("")
      + (java.parts.length > 1 ? '<div class="row"><button data-copyall>Copy all</button></div>' : "");
    el.querySelectorAll("[data-copy]").forEach((b) => (b.onclick = () => copy(java.parts[+b.dataset.copy].java, b)));
    const all = el.querySelector("[data-copyall]");
    if (all) all.onclick = () => copy(java.parts.map((p) => `// ${p.name}: ${p.where}\n${p.java}`).join("\n\n"), all);
  }

  async function copy(text, button) {
    try { await navigator.clipboard.writeText(text); button.textContent = "Copied"; }
    catch (e) { button.textContent = "Select the text and copy it"; }
    setTimeout(() => { button.textContent = button.dataset.copyall != null ? "Copy all" : "Copy"; }, 1600);
  }

  async function tryIt() {
    const r = await postJSON("/robot-edit/try", { edits, shotAngles });
    if (r.error) { say = ""; problem = r.error; renderShape(); renderChecks(); return; }
    info.tried = JSON.parse(JSON.stringify(edits));
    info.triedAngles = JSON.parse(JSON.stringify(shotAngles));
    say = Object.keys(edits).length || Object.keys(shotAngles).length ? "The simulator uses these shapes now. Back to driving to try them (nothing is saved: copy the Java)."
      : "The simulator is back to the robot file's shapes and shot angles.";
    $("#eSay").textContent = say;
  }

  // ------------------------------------------------------------ picking and dragging in the 3D view

  let ray = null;          // made once three.js is loaded (the 3D view loads it when it starts)
  const stage = $("#stage");
  function aim(ev) {
    if (!ray) ray = new THREE.Raycaster();
    const b = v3.renderer.domElement.getBoundingClientRect();
    ray.setFromCamera(new THREE.Vector2((ev.clientX - b.left) / b.width * 2 - 1, -(ev.clientY - b.top) / b.height * 2 + 1), v3.camera);
  }
  function hitShape() {
    const hits = ray.intersectObjects(robot.children, false);
    return hits.length ? hits[0] : null;
  }
  // robot frame <-> page
  const toRobot = (v) => [v.y, -v.x, v.z];
  // a robot frame change into the shape's parent frame (frames: the parent's turn, rows of a 3x3)
  function intoParent(name, d) {
    const f = view.frames[name];
    if (!f) return d;
    return [0, 1, 2].map((j) => f[j] * d[0] + f[3 + j] * d[1] + f[6 + j] * d[2]);
  }
  const AXIS = { forward: [1, 0, 0], back: [-1, 0, 0], left: [0, 1, 0], right: [0, -1, 0], up: [0, 0, 1], down: [0, 0, -1] };

  const g = { on: false, moved: false, plane: null, start: null, name: null, at0: null, size0: null, len0: 0, sign: [1, 1, 1], vertical: false, down: null };
  stage.addEventListener("pointerdown", (ev) => {
    if (!E.on || ev.button !== 0 || !robot) return;
    aim(ev);
    const h = hitShape();
    g.down = { x: ev.clientX, y: ev.clientY, name: h ? h.object.userData.shape : null };
    if (!h || h.object.userData.shape !== sel || sel === "chassis") return;     // only the picked shape drags; elsewhere the view turns
    const s = shape(sel), n = now(s);
    g.on = true; g.moved = false; g.name = sel; g.vertical = ev.shiftKey;
    g.start = h.point.clone();
    if (g.vertical) {
      const dir = new THREE.Vector3(); v3.camera.getWorldDirection(dir); dir.z = 0;
      if (dir.lengthSq() < 1e-6) dir.set(0, 1, 0);
      g.plane = new THREE.Plane().setFromNormalAndCoplanarPoint(dir.normalize(), g.start);
    } else {
      g.plane = new THREE.Plane(new THREE.Vector3(0, 0, 1), -g.start.z);
    }
    g.at0 = n.at.slice(); g.size0 = n.size ? n.size.slice() : null; g.len0 = n.length;
    // which faces were grabbed: a drag pulls those outward
    const piece = view.pieces.find((p) => p.name === sel);
    const rel = toRobot(g.start).map((v, k) => v - piece.c[k]);
    g.sign = intoParent(sel, rel).map((v) => (v >= 0 ? 1 : -1));
    v3.controls.enabled = false;
    stage.setPointerCapture(ev.pointerId);
    ev.stopPropagation(); ev.preventDefault();
  }, true);
  stage.addEventListener("pointermove", (ev) => {
    if (!g.on) return;
    aim(ev);
    const p = new THREE.Vector3();
    if (!ray.ray.intersectPlane(g.plane, p)) return;
    let d = toRobot(p.clone().sub(g.start)).map((v) => v * MM);       // mm, robot frame
    if (g.vertical) d = [0, 0, d[2]];
    else d[2] = 0;
    const dp = intoParent(g.name, d);
    if (Math.hypot(...dp) > 0.5) g.moved = true;
    const s = shape(g.name);
    if (tool === "move") setEditValue(g.name, "atMm", g.at0.map((v, k) => Math.round(v + dp[k])));
    else if (s.spins) {
      // a roller grows along its axle, both ends at once (it is centred on its place)
      const a = AXIS[now(s).axis] || AXIS.left, along = dp[0] * a[0] + dp[1] * a[1] + dp[2] * a[2];
      const end = g.sign[0] * a[0] + g.sign[1] * a[1] + g.sign[2] * a[2] >= 0 ? 1 : -1;
      setEditValue(g.name, "lengthMm", Math.max(1, Math.round(g.len0 + 2 * along * end)));
    } else setEditValue(g.name, "sizeMm", g.size0.map((v, k) => Math.max(1, Math.round(v + g.sign[k] * dp[k]))));
    changed();
    ev.stopPropagation();
  }, true);
  const end = (ev) => {
    if (g.on) {
      g.on = false;
      v3.controls.enabled = true;
      render();
      ev.stopPropagation();
      return;
    }
    // a click (no drag) on a shape picks it
    if (E.on && g.down && ev.type === "pointerup" && Math.hypot(ev.clientX - g.down.x, ev.clientY - g.down.y) < 4) {
      if (g.down.name && g.down.name !== "chassis") pick(g.down.name);
      else if (!g.down.name && sel) pick(sel);
    }
    g.down = null;
  };
  stage.addEventListener("pointerup", end, true);
  stage.addEventListener("pointercancel", end, true);

  // Keys while the mode is open: arrows move (or size) the picked shape; nothing else on the page hears them.
  window.addEventListener("keydown", (ev) => {
    if (!E.on || ev.ctrlKey || ev.metaKey || ev.altKey) return;
    if (ev.target.matches && ev.target.matches("input, select, textarea, [contenteditable]")) return;
    if (document.body.classList.contains("td-open")) return;
    if (ev.key === "Escape") { if (sel) pick(sel); ev.stopImmediatePropagation(); return; }
    if (ev.key === "Tab") return;
    const step = ev.shiftKey ? 10 : 1;
    const ways = { ArrowUp: [0, 1], ArrowDown: [0, -1], ArrowLeft: [1, 1], ArrowRight: [1, -1], PageUp: [2, 1], PageDown: [2, -1] }[ev.key];
    if (ways && sel && sel !== "chassis") {
      const s = shape(sel), n = now(s);
      if (tool === "move") { const a = n.at.slice(); a[ways[0]] += ways[1] * step; setEditValue(sel, "atMm", a); }
      else if (s.spins) setEditValue(sel, "lengthMm", Math.max(1, n.length + ways[1] * step));
      else { const a = n.size.slice(); a[ways[0]] = Math.max(1, a[ways[0]] + ways[1] * step); setEditValue(sel, "sizeMm", a); }
      changed();
      renderShape();
      renderList();
      ev.preventDefault();
    }
    // the view keys (1-8) and the keyboard gamepad stay quiet while editing
    ev.stopImmediatePropagation();
  }, true);
})();
