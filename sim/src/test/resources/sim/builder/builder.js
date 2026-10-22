// The simulator page's auto builder: build an autonomous on the field (Simple steps, or a Pedro path),
// press Run, and the simulator runs it with the team's real code (the Built Auto). Then copy the Java, save
// a scenario, print the setup card. Docs: docs/guides/simulator.md, "The auto builder".
//
// The plan, its checks, the Java and the scenario come from the web builder's own files (stepscore.js,
// pathcore.js, mapdraw.js, setupcard.js: the same files as crossbotics.com's path builder), so the Java is
// the same for the same plan. simcore.js adds what a run needs. The page (index.html) gives this module a
// few hooks (window.simPage): its 3D view, the state it gets 10 times a second, and its field map.
import * as P from './pathcore.js';
import * as S from './stepscore.js';
import * as C from './simcore.js';
import { el, pts, drawField, drawRobot, handleRadius, toField } from './mapdraw.js';
import { openSetupCard } from './setupcard.js';

const page = window.simPage;
const $ = (id) => document.getElementById(id);
const STORE = 'simAutoBuilder-v1';
const LIMIT = 72;
const PLAN = '#8250df', GHOST = '#8250df', ACT = '#c2410c', PATH = '#1b5fa8', BAD = '#c8322a', START = '#1b7f3b';
const MOVING = new Set(['move', 'turn', 'turnTo']);
const fmt = (v) => P.num(P.round1(v));
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const snap = (v, q = 0.5) => Math.round(v / q) * q;
const clampXY = (x, y) => [Math.max(-LIMIT, Math.min(LIMIT, x)), Math.max(-LIMIT, Math.min(LIMIT, y))];

// ------------------------------------------------------------------ the state

// The web builder's examples: lesson 6's ScoreAndPark (any drive), and the Pedro example robot's path.
const STEPS_EXAMPLE = {
	drive: 'mecanum', mirror: false,
	start: { x: -56, y: -61.1, h: 0, note: 'Our wall, left corner tile, facing the field', spot: true, noteAuto: true },
	steps: [
		{ kind: 'run', label: 'Hold the piece', code: 'robot.claw::close' },
		{ kind: 'move', label: 'To the goal', inches: 24, dir: 0, power: 0.6 },
		{ kind: 'action', code: 'Actions.scoreHigh(robot)', timeout: 5 },
		{ kind: 'park', sec: 3 },
		{ kind: 'move', label: 'Park', inches: 20, dir: 90, power: 0.7 }],
	opmode: 'Score and Park', className: '', teleop: 'Quick Start TeleOp', pkg: 'org.firstinspires.ftc.teamcode', group: 'Autos',
};
const PEDRO_EXAMPLE = {
	path: { start: [-56, -61], parts: [
		{ ctrl: [], end: [-48, -50], heading: { mode: 'keep', a: 0 }, maxSpeed: null },
		{ ctrl: [[-44, -36]], end: [-30, -30], heading: { mode: 'turn', a: 0, b: 45 }, maxSpeed: null }], holdSec: 5 },
	start: { on: true, note: 'Against our wall, in the left corner tile, facing the field', h: 0, noteAuto: false },
	label: 'To the goal', name: 'TO_GOAL',
	actions: [{ kind: 'fraction', value: 0.5, what: 'command', label: 'Intake on', code: 'robot.intake::in' }],
	timeoutSec: null, mode: 'follow', power: 0.7, mirror: false, opmode: 'My Auto',
};
const NEW_ACTION = { what: 'command', label: 'Intake on', code: 'robot.intake::in' };

let B = {
	open: false, mode: 'steps', alliance: 'RED', robotClass: '', edit3d: true,
	add: 'drive', addPedro: 'line', sel: null, selPart: 0, selAction: null, view: 'file',
	steps: { plan: structuredClone(STEPS_EXAMPLE), speeds: { ...S.DEFAULT_SPEEDS }, tolIn: 4, tolDeg: 10, recorded: false },
	pedro: { ...structuredClone(PEDRO_EXAMPLE), tolIn: 4, tolDeg: 10, recorded: false },
};
try {
	const saved = JSON.parse(localStorage.getItem(STORE) || 'null');
	if (saved && saved.steps?.plan?.start && saved.pedro?.path?.start) B = { ...B, ...saved };
} catch { /* no storage: the examples */ }
const save = () => { try { localStorage.setItem(STORE, JSON.stringify(B)); } catch { /* fine */ } };

const undoList = [];
function change(fn) {
	undoList.push(JSON.stringify(B));
	if (undoList.length > 100) undoList.shift();
	fn();
	render();
}

// The simulator's side: the field (neutral names), the Robot classes, the last state.
let field = null, obs = [], half = 70.585;
let robots = [], guess = '';
let sim = null;          // the last state (10 a second)
let lastRun = null;      // { b: the plan as it ran, tl: its timeline, id: the run's number, sent: page time }
let runError = '';
let preview = null;      // { t0: page seconds when the preview started } while "Preview" plays

async function loadSimInfo() {
	try {
		const f = await (await fetch('/field', { cache: 'no-store' })).json();
		field = C.neutralField(f);
		obs = P.obstacles(field);
		half = field.field.insideWallSize / 2;
	} catch { field = null; }
	try {
		const a = await (await fetch('/auto', { cache: 'no-store' })).json();
		robots = a.robots || [];
		guess = a.guess || '';
	} catch { robots = []; }
	render();
}

// The Robot class to run with: the one picked, or the best fit for the robot file. Among Robot classes that
// fit equally well (the same device names), a Pedro path prefers one with a follower, and Simple steps one without.
function bestFit() {
	if (!robots.length) return guess;
	const top = Math.max(...robots.map((r) => r.score ?? 0));
	const tied = robots.filter((r) => (r.score ?? 0) === top);
	const want = B.mode === 'pedro';
	return (tied.find((r) => !!r.follower === want) || tied.find((r) => r.name === guess) || tied[0]).name;
}
const robotInfo = () => robots.find((r) => r.name === (B.robotClass || bestFit())) || null;
/** The robot's size from the simulator's robot file: [front to back, side to side], inches. */
function robotLW() {
	const r = sim && sim.robot;
	return [Math.max(1, +(r && r.length) || 18), Math.max(1, +(r && r.width) || 18)];
}
const mirrorNow = () => (B.mode === 'pedro' ? !!B.pedro.mirror : !!B.steps.plan.mirror);

// ------------------------------------------------------------------ the panel

const panel = $('builder');
panel.innerHTML = `
<div class="b-head">
  <b>Auto builder</b>
  <span class="b-tabs" role="tablist">
    <button role="tab" id="bTabSteps" title="Any robot, tank or mecanum">Simple steps</button><button role="tab" id="bTabPedro" title="Mecanum with odometry">Pedro path</button>
  </span>
  <button id="bClose" class="b-x" title="Close the auto builder" aria-label="Close">&times;</button>
</div>
<p class="muted b-intro">Build an autonomous on the field, press <b>Run</b>: the simulator runs it with your real code
  (the <i>Built Auto</i>). Then copy the Java. <a href="https://github.com/crossbotics/ftc-helper-lib/blob/main/docs/guides/simulator.md#the-auto-builder" target="_blank" rel="noopener">Guide</a></p>
<details class="b-sec" open><summary>Robot and alliance</summary>
  <div class="row"><label>Robot code <select id="bRobot" aria-label="Robot class"></select></label></div>
  <div class="muted" id="bRobotText"></div>
  <div class="row"><span class="muted">Run as</span>
    <span class="b-seg" role="group" aria-label="Alliance"><button id="bRed" class="b-red">RED</button><button id="bBlue" class="b-blue">BLUE</button></span></div>
  <div class="row"><label title="mirrorForRed() in the Java: false on a field turned half a turn between the alliances (BIOBUZZ)">Your auto's <code>mirrorForRed()</code> <select id="bMirror"><option value="false">false (turned field, like BIOBUZZ)</option><option value="true">true (mirrored field)</option></select></label></div>
</details>
<div class="b-sec">
  <div class="row b-tools" id="bTools"></div>
  <svg id="bMap" viewBox="-82 -80 164 168" role="img" aria-label="The field from your drivers' wall: drag the points, tap to add"></svg>
  <div class="row"><button id="bPreview" title="The plan robot drives the plan (a plan, not physics)">Preview</button>
    <input type="range" id="bTime" min="0" max="1000" value="0" aria-label="Time along the plan"><span class="muted" id="bTimeText"></span></div>
  <label class="muted"><input type="checkbox" id="bEdit3d"> Edit in the 3D view too (drag the points; a tap adds)</label>
</div>
<details class="b-sec" open><summary>Start spot</summary>
  <div class="row" id="bPresetRow"><label>Spot <select id="bPreset"></select></label></div>
  <div class="row"><label>x <input type="number" step="0.5" id="bSx"></label><label>y <input type="number" step="0.5" id="bSy"></label><label>heading <input type="number" step="5" id="bSh"></label></div>
  <label class="b-note">Note for the drive team <input type="text" id="bNote" maxlength="120" placeholder="Our wall, left corner tile, facing the field"></label>
  <div class="row"><button id="bWrite" title="Write the note from the spot: the wall, the tile, the way it faces">Write it for me</button>
    <button id="bCard" title="One page for the drive team: the note, the spot on the field, red and blue">Print setup card</button></div>
  <div class="muted" id="bNoteHelp"></div>
  <label class="muted" id="bStartOnRow"><input type="checkbox" id="bStartOn"> s.start(...) line in the Java (the note and the spot)</label>
</details>
<div class="b-sec" id="bStepsPane">
  <h4 class="b-h">Steps</h4>
  <div class="row b-addrow" id="bAdd">
    <button data-add="move">+ Drive</button><button data-add="strafe" id="bAddStrafe">+ Strafe</button><button data-add="turn">+ Turn</button>
    <button data-add="turnTo">+ Turn to</button><button data-add="wait">+ Wait</button><button data-add="run">+ Start something</button>
    <button data-add="action">+ Action</button><button data-add="park">+ Park from here</button></div>
  <div id="bList"></div>
  <div class="row"><button id="bClear" title="Remove every step">Remove all steps</button><button id="bExample" title="The steps from lesson 6">Load the lesson's example</button></div>
</div>
<div class="b-sec" id="bPedroPane">
  <h4 class="b-h">Path</h4>
  <div class="row"><label><input type="radio" name="bPMode" value="follow"> follow (Pedro)</label><label><input type="radio" name="bPMode" value="driveTo"> driveTo steps</label>
    <label id="bPowerBox">power <input type="number" step="0.05" min="0.05" max="1" id="bPower"></label></div>
  <div class="row"><label>Step label <input type="text" id="bLabel"></label><label>Path name <input type="text" id="bName"></label></div>
  <div class="row"><label><input type="checkbox" id="bHold"> Hold the end</label><label id="bLimitBox">Time limit <input type="number" step="1" min="1" id="bTimeout" placeholder="10"> s</label></div>
  <div id="bParts"></div>
  <div class="row"><button id="bPClear" title="Remove every part">Remove all parts</button><button id="bPExample" title="The Pedro example robot's path">Load the Pedro lesson's example</button></div>
  <h4 title="PathAt in the Java">Actions along the path <button id="bAddAct" class="small">+ Add one</button></h4>
  <div id="bActions"></div>
</div>
<div class="b-sec"><h4 class="b-h">Checks</h4><ul id="bChecks" class="b-checks"></ul></div>
<div class="b-sec b-run">
  <div class="row"><button id="bRun" class="b-go">Run in the simulator</button><button id="bStop">Stop</button>
    <span class="b-clock" id="bClock">0.0 s</span></div>
  <div class="muted" id="bRunText"></div>
  <div id="bResult"></div>
</div>
<details class="b-sec" open><summary>Java and scenario</summary>
  <div class="row" id="bNames"></div>
  <div class="row"><button id="bCopy" class="b-go">Copy the Java</button><span class="muted" id="bCopied"></span>
    <label class="muted" id="bViewRow"><select id="bView"><option value="file">the whole file</option><option value="steps">only the steps</option></select></label></div>
  <div class="row"><button id="bSaveSim">Save the scenario</button><label class="muted"><input type="checkbox" id="bRecorded"> with the end from the last run</label></div>
  <div class="row"><label>Close enough <input type="number" step="0.5" min="0.5" id="bTolIn"> in</label><label><input type="number" step="1" min="1" id="bTolDeg"> degrees</label></div>
  <details><summary class="muted">Show the Java</summary><pre id="bCode" class="b-code"></pre></details>
  <details><summary class="muted">Show the scenario</summary><pre id="bSim" class="b-code"></pre></details>
</details>`;

// ------------------------------------------------------------------ open and close

function setOpen(on) {
	B.open = on;
	$('builderSection').hidden = !on;
	document.body.classList.toggle('building', on);
	$('builderBtn').classList.toggle('on', on);
	$('builderBtn').setAttribute('aria-pressed', on ? 'true' : 'false');
	if (on && !field) loadSimInfo();
	save();
	page.resize3d();
	render();
}
$('builderBtn').onclick = () => setOpen(!B.open);
$('bClose').onclick = () => setOpen(false);
$('bTabSteps').onclick = () => { B.mode = 'steps'; B.sel = null; render(); };
$('bTabPedro').onclick = () => { B.mode = 'pedro'; render(); };

// ------------------------------------------------------------------ everything

let view = null;   // what the last render worked out: the checks, the timeline, the measure

function render() {
	if (!B.open) { draw3d(); return; }
	const pedro = B.mode === 'pedro';
	$('bTabSteps').setAttribute('aria-selected', String(!pedro));
	$('bTabPedro').setAttribute('aria-selected', String(pedro));
	$('bStepsPane').hidden = pedro;
	$('bPedroPane').hidden = !pedro;
	syncRobot();
	syncNote();
	view = work();
	renderRobot();
	renderTools();
	renderMap();
	renderStart();
	if (pedro) renderPedro(); else renderSteps();
	renderChecks();
	renderRun();
	renderCode();
	draw3d();
	save();
}

// The drive comes from the Robot code (tank or mecanum); the robot's size from the robot file.
function syncRobot() {
	const info = robotInfo();
	if (info && info.drive) B.steps.plan.drive = info.drive;
}

// A note the builder wrote follows the spot; a note the team wrote stays as it is.
function syncNote() {
	const lw = robotLW();
	const st = B.steps.plan.start;
	if (st.noteAuto) st.note = S.autoNote(st, lw, half);
	const ps = B.pedro.start;
	if (ps.noteAuto) ps.note = S.autoNote({ x: B.pedro.path.start[0], y: B.pedro.path.start[1], h: +ps.h || 0 }, lw, half);
}

function work() {
	const lw = robotLW();
	const tl = C.timeline(B, B.steps.speeds);
	if (B.mode !== 'pedro') {
		const checked = S.checkPlan(B.steps.plan, { field, obs: field ? obs : null, half, robot: lw, speeds: B.steps.speeds, startDelay: 0, showOther: false });
		return { tl, sim: checked.sim, checks: checked.list };
	}
	const st = B.pedro, path = st.path;
	const driven = st.mode === 'driveTo' ? C.drivenPath(st) : path;
	const samples = P.sample(driven);
	const m = P.measure(driven);
	const mine = field ? P.checkHits(samples, obs, half, lw[0], lw[1]) : { hitAt: [], hits: [] };
	const checks = [];
	for (const h of mine.hits) checks.push({ level: 'bad', text: `Part ${h.part + 1}: the robot touches ${h.name} near (${fmt(h.x)}, ${fmt(h.y)}).` });
	for (const w of P.warnings(path, P.sample(path), st.start.on ? +st.start.h || 0 : null)) checks.push({ level: 'warn', text: w });
	if (st.mirror && field && /BIOBUZZ/.test(field.season)) checks.push({ level: 'warn', text: 'BIOBUZZ is a turned field: with mirrorForRed() true, the other alliance drives to the wrong places.' });
	const order = P.ordered(st.actions, m);
	for (const p of P.checkActions(st.actions, m, { step: st.mode, label: st.label })) {
		checks.push({ level: p.level, text: `Action ${order.indexOf(st.actions[p.i]) + 1}: ${p.text}`, act: p.i });
	}
	if (field) for (const c of S.startChecks({ x: path.start[0], y: path.start[1], h: +st.start.h || 0 }, lw, field, obs, half)) checks.push(c);
	if (!path.parts.length) checks.push({ level: 'bad', text: 'Tap the map to add the first part of the path.' });
	return { tl, samples, m, mine, checks };
}

// ------------------------------------------------------------------ robot and alliance

function renderRobot() {
	const sel = $('bRobot');
	const key = robots.map((r) => r.name).join('|') + '#' + bestFit();
	if (sel.dataset.key !== key) {
		sel.dataset.key = key;
		sel.innerHTML = robots.length
			? `<option value="">${esc('Best fit: ' + (robots.find((r) => r.name === bestFit())?.short || bestFit()))}</option>` + robots.map((r) => `<option value="${esc(r.name)}">${esc(r.short)}</option>`).join('')
			: '<option value="">(none found)</option>';
	}
	if (document.activeElement !== sel) sel.value = B.robotClass && robots.some((r) => r.name === B.robotClass) ? B.robotClass : '';
	const info = robotInfo();
	const [L, W] = robotLW();
	$('bRobotText').textContent = info
		? `${info.name}: ${info.drive || '?'} drive${info.odometry ? ', odometry' : ''}${info.follower ? ', Pedro follower' : ''}. Size from the robot file: ${fmt(L)} x ${fmt(W)} in.`
		: (robots.length ? '' : 'Looking for your Robot class...');
	$('bRed').classList.toggle('on', B.alliance === 'RED');
	$('bBlue').classList.toggle('on', B.alliance === 'BLUE');
	$('bRed').setAttribute('aria-pressed', String(B.alliance === 'RED'));
	$('bBlue').setAttribute('aria-pressed', String(B.alliance === 'BLUE'));
	$('bMirror').value = String(mirrorNow());
}
$('bRobot').onchange = () => change(() => { B.robotClass = $('bRobot').value; });
$('bRed').onclick = () => change(() => { B.alliance = 'RED'; });
$('bBlue').onclick = () => change(() => { B.alliance = 'BLUE'; });
$('bMirror').onchange = () => change(() => {
	const v = $('bMirror').value === 'true';
	if (B.mode === 'pedro') B.pedro.mirror = v; else B.steps.plan.mirror = v;
});

/** What stops a run before it starts (the robot cannot do this mode), or ''. */
function cannotRun() {
	const info = robotInfo();
	if (!robots.length) return 'No Robot class found in the code: the Built Auto runs your Robot.java.';
	if (B.mode === 'pedro') {
		if (!info) return '';
		const st = B.pedro;
		if (info.drive !== 'mecanum') return `Pedro mode needs a mecanum drive with odometry: ${info.short} has a ${info.drive} drive. Use Simple steps (any drive).`;
		if (!info.odometry) return `Pedro mode needs odometry (an odometry(...) line in Robot.java): ${info.short} has none. Use Simple steps.`;
		if (st.mode === 'follow' && !info.follower) return `follow needs Pedro (a follower(...) line in Robot.java): ${info.short} has none. Choose driveTo steps (odometry only), or Simple steps.`;
		if (!st.path.parts.length) return 'Add a part to the path first.';
	}
	return '';
}

// ------------------------------------------------------------------ the map (the panel's SVG)

const svg = $('bMap');
const TOOLS_STEPS = [['drive', 'drive there'], ['face', 'turn to face'], ['none', 'nothing']];
const TOOLS_PEDRO = [['line', 'a line'], ['curve', 'a curve'], ['action', 'an action'], ['none', 'nothing']];

function renderTools() {
	const list = B.mode === 'pedro' ? TOOLS_PEDRO : TOOLS_STEPS;
	const now = B.mode === 'pedro' ? B.addPedro : B.add;
	$('bTools').innerHTML = '<span class="muted">Tap adds:</span><span class="b-seg" role="group" aria-label="What a tap on the map adds">'
		+ list.map(([v, t]) => `<button data-tool="${v}" aria-pressed="${v === now}" class="${v === now ? 'on' : ''}">${t}</button>`).join('')
		+ '</span><button id="bUndo" title="Undo the last change">Undo</button>';
	for (const b of $('bTools').querySelectorAll('[data-tool]')) {
		b.onclick = () => { if (B.mode === 'pedro') B.addPedro = b.dataset.tool; else B.add = b.dataset.tool; render(); };
	}
	$('bUndo').onclick = () => { if (undoList.length) { B = { ...JSON.parse(undoList.pop()), open: true }; render(); } };
	$('bEdit3d').checked = !!B.edit3d;
}
$('bEdit3d').onchange = () => { B.edit3d = $('bEdit3d').checked; render(); };

/** The editable points, in plan numbers: [{ id, x, y, kind: 'start' | 'end' | 'ctrl' | 'point' | 'move', label }]. */
function handles() {
	const out = [];
	if (B.mode === 'pedro') {
		const path = B.pedro.path;
		path.parts.forEach((part, i) => {
			if (part.heading.mode === 'point') out.push({ id: `p${i}`, x: part.heading.x, y: part.heading.y, kind: 'point' });
			part.ctrl.forEach((c, j) => out.push({ id: `c${i}.${j}`, x: c[0], y: c[1], kind: 'ctrl' }));
		});
		path.parts.forEach((part, i) => out.push({ id: `e${i}`, x: part.end[0], y: part.end[1], kind: 'end', label: String(i + 1) }));
		out.push({ id: 's', x: path.start[0], y: path.start[1], kind: 'start' });
		return out;
	}
	const plan = B.steps.plan;
	plan.steps.forEach((s, i) => {
		if (s.kind !== 'move' || !view?.sim?.ends[i]) return;
		const e = view.sim.ends[i];
		out.push({ id: `m${i}`, x: e.x, y: e.y, kind: 'move', label: String(i + 1) });
	});
	out.push({ id: 's', x: +plan.start.x, y: +plan.start.y, kind: 'start' });
	return out;
}

// The pose before step i of a Simple steps plan (the start for step 0).
function poseBefore(i) {
	const st = B.steps.plan.start;
	if (i <= 0) return { x: +st.x, y: +st.y, h: P.wrap(+st.h || 0) };
	return view.sim.ends[i - 1];
}

/** Moves a point to (x, y), plan numbers (a drag on the panel's map or in 3D). */
function moveHandle(id, x, y) {
	[x, y] = clampXY(x, y);
	if (B.mode === 'pedro') {
		const path = B.pedro.path;
		const xy = [snap(x), snap(y)];
		if (id === 's') { path.start = xy; return; }
		const m = /^([ecp])(\d+)(?:\.(\d+))?$/.exec(id);
		const part = path.parts[+m[2]];
		if (!part) return;
		if (m[1] === 'e') part.end = xy;
		else if (m[1] === 'c') part.ctrl[+m[3]] = xy;
		else { part.heading.x = xy[0]; part.heading.y = xy[1]; }
		return;
	}
	const plan = B.steps.plan;
	if (id === 's') { plan.start.x = snap(x); plan.start.y = snap(y, 0.1); return; }
	const i = +id.slice(1), s = plan.steps[i];
	if (!s || s.kind !== 'move') return;
	const p = poseBefore(i);
	if (plan.drive === 'mecanum') {
		const [n] = stepsTo(p, x, y);
		if (n) { s.inches = n.inches; s.dir = n.dir; }
	} else {
		// Tank: along the robot's front only (forward or back).
		const f = [-Math.sin(p.h * Math.PI / 180), Math.cos(p.h * Math.PI / 180)];
		const along = snap((x - p.x) * f[0] + (y - p.y) * f[1]);
		s.inches = Math.abs(along); s.dir = along < 0 ? 180 : 0;
	}
}

// The steps that drive the robot from pose p to (x, y): mecanum one move in any direction; tank a turn
// (unless it is straight ahead or behind), then a move forward or back. (The web builder's rule.)
function stepsTo(p, x, y) {
	const dx = x - p.x, dy = y - p.y, dist = snap(Math.hypot(dx, dy));
	if (dist < 0.5) return [];
	const a = P.wrap(P.headingOf(dx, dy) - p.h);
	const name = `To (${fmt(x)}, ${fmt(y)})`;
	if (B.steps.plan.drive === 'mecanum') return [{ kind: 'move', label: name, inches: dist, dir: ((Math.round(-a) % 360) + 360) % 360, power: 0.6 }];
	if (Math.abs(a) <= 1) return [{ kind: 'move', label: name, inches: dist, dir: 0, power: 0.6 }];
	if (Math.abs(a) >= 179) return [{ kind: 'move', label: name, inches: dist, dir: 180, power: 0.6 }];
	return [{ kind: 'turn', label: `Face (${fmt(x)}, ${fmt(y)})`, deg: Math.round(a), power: 0.5 },
		{ kind: 'move', label: name, inches: dist, dir: 0, power: 0.6 }];
}

// Where a new curve's control points go: off to the side of the straight line, so the bend shows.
function curveControls(a, b, n) {
	const dx = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(dx, dy) || 1;
	const off = Math.min(12, len / 3), nx = -dy / len * off, ny = dx / len * off;
	return Array.from({ length: n }, (_, k) => {
		const t = (k + 1) / (n + 1);
		return clampXY(snap(a[0] + dx * t + nx), snap(a[1] + dy * t + ny));
	});
}

/** A tap on the field (not a drag), plan numbers: adds a step or a part, with the tool chosen. */
function tapAt(x, y) {
	if (Math.abs(x) > LIMIT + 2 || Math.abs(y) > LIMIT + 2) return;
	[x, y] = clampXY(snap(x), snap(y));
	if (B.mode === 'pedro') {
		const st = B.pedro, path = st.path;
		if (B.addPedro === 'none') return;
		if (B.addPedro === 'action') {
			if (!path.parts.length) return;
			const near = P.project(view.m, x, y);
			if (near.off > 12) return;
			change(() => {
				const a = { kind: near.in > view.m.total / 2 ? 'inchesLeft' : 'inchesDone', value: 0, ...NEW_ACTION };
				setPlace(a, near.in, view.m);
				st.actions.push(a);
				B.selAction = st.actions.length - 1;
			});
			return;
		}
		change(() => {
			const from = path.parts.length ? path.parts[path.parts.length - 1].end : path.start;
			const last = path.parts[path.parts.length - 1];
			let heading = last ? structuredClone(last.heading) : { mode: 'path' };
			if (heading.mode === 'turn') heading = { mode: 'keep', a: heading.b };
			path.parts.push({ ctrl: B.addPedro === 'curve' ? curveControls(from, [x, y], 1) : [], end: [x, y], heading, maxSpeed: null });
			B.selPart = path.parts.length - 1;
		});
		return;
	}
	if (B.add === 'none') return;
	change(() => {
		const plan = B.steps.plan;
		const i = B.sel != null ? B.sel + 1 : plan.steps.length, p = poseBefore(i);
		let add;
		if (B.add === 'face') {
			const a = Math.round(P.wrap(P.headingOf(x - p.x, y - p.y) - p.h));
			add = a ? [{ kind: 'turn', label: `Face (${fmt(x)}, ${fmt(y)})`, deg: a, power: 0.5 }] : [];
		} else add = stepsTo(p, x, y);
		plan.steps.splice(i, 0, ...add);
		if (add.length) B.sel = i + add.length - 1;
	});
}

// Sets a PathAt's number so it starts d inches along the path, keeping its kind (the web builder's rule).
function setPlace(a, d, m) {
	const T = m.total;
	d = Math.max(0, Math.min(T, d));
	if (a.kind === 'inchesDone') a.value = snap(d);
	else if (a.kind === 'inchesLeft') a.value = snap(T - d);
	else if (a.kind === 'fraction') a.value = T > 0 ? Math.round(d / T * 100) / 100 : 0;
	else if (a.kind === 'part') {
		let best = 0;
		m.partStartIn.forEach((s, k) => { if (Math.abs(s - d) < Math.abs(m.partStartIn[best] - d)) best = k; });
		a.value = best + 1;
	}
}

function renderMap() {
	const lw = robotLW();
	const r = handleRadius(svg);
	svg.replaceChildren();
	drawField(el('g', {}, svg), field);
	const g = el('g', {}, svg);
	const bad = (B.mode === 'pedro' ? [] : view.checks).filter((c) => c.level === 'bad' && c.step >= 0).map((c) => c.step);
	const tl = view.tl;
	// The sweep (a light footprint every few inches) and the route.
	tl.samples.forEach((s, k) => {
		const isBad = B.mode === 'pedro' ? !!view.mine?.hitAt[k] : bad.includes(s.step);
		if (isBad || k % 3 === 0 || k === tl.samples.length - 1) {
			el('polygon', { points: pts(P.footprint(s.x, s.y, s.h, lw[0], lw[1])), fill: isBad ? BAD : PATH, 'fill-opacity': isBad ? 0.12 : 0.05, stroke: isBad ? BAD : PATH, 'stroke-opacity': isBad ? 0.5 : 0.25, 'stroke-width': 0.2 }, g);
		}
	});
	if (B.mode === 'pedro') {
		const path = B.pedro.path;
		path.parts.forEach((part, i) => {
			if (part.ctrl.length) el('polyline', { points: pts(P.partPoints(path, i)), fill: 'none', stroke: PATH, 'stroke-width': 0.3, 'stroke-dasharray': '1.2 1' }, g);
		});
		if (B.pedro.mode === 'driveTo') el('polyline', { points: pts(P.sample(path).map((s) => [s.x, s.y])), fill: 'none', stroke: PATH, 'stroke-width': 0.4, 'stroke-dasharray': '1 1' }, g);
	}
	el('polyline', { points: pts(tl.samples.map((s) => [s.x, s.y])), fill: 'none', stroke: PATH, 'stroke-width': 0.8, 'stroke-linejoin': 'round' }, g);
	// Where the robot really went in the last run (the simulator's trail), in plan numbers.
	const trail = realTrail();
	for (const piece of trail) el('polyline', { points: pts(piece), fill: 'none', stroke: '#2da44e', 'stroke-width': 0.7 }, g);
	// The plan robot at the slider's time, or where the run is now.
	const at = ghostPose() || C.poseAtTime(tl, +$('bTime').value / 1000 * tl.total);
	if (at) drawRobot(el('g', { 'pointer-events': 'none' }, svg), at, GHOST, 0.12, 0.6, lw);
	// The PathAt markers (Pedro): where each starts.
	if (B.mode === 'pedro' && B.pedro.path.parts.length) {
		const m = view.m;
		P.ordered(B.pedro.actions, m).forEach((a, n) => {
			const place = P.actionPlace(a, m);
			const i = B.pedro.actions.indexOf(a);
			const grp = el('g', { class: 'bact', 'data-a': i, style: 'cursor:grab' }, svg);
			const d = r * 1.1;
			el('polygon', { points: pts([[place.x, place.y + d], [place.x + d, place.y], [place.x, place.y - d], [place.x - d, place.y]]), fill: place.when === 'never' ? '#fff' : ACT, stroke: i === B.selAction ? '#ffe14d' : ACT, 'stroke-width': r * 0.3 }, grp);
			el('text', { x: place.x + r * 1.4, y: -place.y + r * 2.4, 'font-size': r * 2, fill: ACT, 'font-weight': 700, 'paint-order': 'stroke', stroke: '#fff', 'stroke-width': r * 0.5, 'pointer-events': 'none' }, svg)
				.textContent = `${n + 1} ${(a.what === 'action' ? a.code.replace(/^Actions\./, '').replace(/\(.*$/, '') : a.label) || '?'}`;
			el('title', {}, grp).textContent = `${P.pathAtCode(a)} (drag along the path)`;
		});
	}
	// The points to drag.
	const hg = el('g', {}, svg);
	for (const h of handles()) {
		const grp = el('g', { class: 'bh', 'data-h': h.id, style: 'cursor:grab' }, hg);
		el('circle', { cx: h.x, cy: -h.y, r: r * 1.8, fill: 'transparent' }, grp);
		const sel = (B.mode !== 'pedro' && h.id === `m${B.sel}`) || (B.mode === 'pedro' && h.id === `e${B.selPart}`);
		const fill = { start: START, end: PATH, move: PATH, ctrl: '#fff', point: '#fff' }[h.kind];
		el('circle', { cx: h.x, cy: -h.y, r: r * (h.kind === 'ctrl' ? 0.85 : 1.1), fill, stroke: sel ? '#ffe14d' : h.kind === 'ctrl' || h.kind === 'point' ? PATH : '#fff', 'stroke-width': r * (sel ? 0.5 : 0.3) }, grp);
		if (h.label) el('text', { x: h.x + r * 1.4, y: -h.y - r * 1.2, 'font-size': r * 2.1, fill: '#101c45', 'font-weight': 700, 'paint-order': 'stroke', stroke: '#fff', 'stroke-width': r * 0.5, 'pointer-events': 'none' }, hg).textContent = h.label;
		el('title', {}, grp).textContent = { start: 'Start spot: drag to move', end: 'End of a part: drag to move', move: 'End of a drive: drag to change it', ctrl: 'Control point: the path bends toward it', point: 'The point the robot faces' }[h.kind];
	}
	const t = at && at.t != null ? at.t : 0;
	$('bTimeText').textContent = `${fmt(t)} s of ${fmt(tl.total)}`;
}

// The last run's trail (the simulator's green line) in plan numbers.
function realTrail() {
	if (!sim || !sim.trail || !lastRun) return [];
	const a = (sim.built && sim.built.alliance) || B.alliance;
	return sim.trail.map((piece) => piece.map(([x, y]) => { const p = C.fieldToPlan({ x, y, h: 0 }, a, mirrorOf(lastRun.b)); return [p.x, p.y]; }));
}
const mirrorOf = (b) => (b.mode === 'pedro' ? !!b.pedro.mirror : !!b.steps.plan.mirror);

// Dragging on the panel's map: a point, a PathAt marker; a tap adds.
let drag = null;
svg.addEventListener('pointerdown', (evt) => {
	const h = evt.target.closest('.bh'), mk = evt.target.closest('.bact');
	drag = { x0: evt.clientX, y0: evt.clientY, moved: false, at: toField(svg, evt), before: JSON.stringify(B) };
	if (h) drag.id = h.dataset.h;
	else if (mk) { drag.act = +mk.dataset.a; B.selAction = drag.act; }
	if (drag.id && /^m\d+$/.test(drag.id)) B.sel = +drag.id.slice(1);
	if (drag.id && /^e\d+$/.test(drag.id)) B.selPart = +drag.id.slice(1);
	svg.setPointerCapture(evt.pointerId);
});
svg.addEventListener('pointermove', (evt) => {
	if (!drag) return;
	if (Math.hypot(evt.clientX - drag.x0, evt.clientY - drag.y0) > 5) drag.moved = true;
	if (!drag.moved) return;
	const [x, y] = toField(svg, evt);
	if (drag.id) moveHandle(drag.id, x, y);
	else if (drag.act != null) {
		const a = B.pedro.actions[drag.act];
		if (a.kind === 'near') { [a.x, a.y] = clampXY(snap(x), snap(y)); } else setPlace(a, P.project(view.m, x, y).in, view.m);
	} else return;
	quickRender();
});
svg.addEventListener('pointerup', () => {
	if (!drag) return;
	const d = drag;
	drag = null;
	if (d.moved && (d.id || d.act != null)) { undoList.push(d.before); render(); return; }
	if (d.id || d.act != null) { render(); return; }
	if (!d.moved) tapAt(d.at[0], d.at[1]);
});
svg.addEventListener('pointercancel', () => { drag = null; });

// While a point is dragged: only the map, the 3D lines and the code (the forms follow on release).
function quickRender() {
	syncNote();
	view = work();
	renderMap();
	renderCode();
	draw3d();
	if (document.activeElement !== $('bNote')) $('bNote').value = B.mode === 'pedro' ? B.pedro.start.note : B.steps.plan.start.note;
}

$('bTime').oninput = () => { preview = null; renderMap(); draw3d(); };
$('bPreview').onclick = () => {
	preview = preview ? null : { t0: performance.now() / 1000 };
	$('bPreview').textContent = preview ? 'Stop preview' : 'Preview';
};

// ------------------------------------------------------------------ the start spot and the note

function startNow() {
	if (B.mode === 'pedro') return { x: B.pedro.path.start[0], y: B.pedro.path.start[1], h: +B.pedro.start.h || 0, note: B.pedro.start.note, noteAuto: B.pedro.start.noteAuto };
	return B.steps.plan.start;
}
function setVal(id, v) {
	const e = $(id);
	if (document.activeElement !== e) e.value = typeof v === 'number' ? fmt(v) : (v ?? '');
}

function renderStart() {
	const st = startNow();
	const lw = robotLW();
	const presets = field ? S.startPresets(lw, field, obs, half) : [];
	const sel = $('bPreset');
	if (document.activeElement !== sel) {
		sel.innerHTML = '<option value="">By hand (drag the green dot)</option>' + presets.map((p, k) => `<option value="${k}">${esc(p.name)} (${fmt(p.x)}, ${fmt(p.y)})</option>`).join('');
		const k = presets.findIndex((p) => p.x === +st.x && p.y === +st.y && p.h === (+st.h || 0));
		sel.value = k >= 0 ? String(k) : '';
	}
	sel.onchange = () => {
		const p = presets[+sel.value];
		if (!p) return;
		change(() => {
			if (B.mode === 'pedro') { B.pedro.path.start = [p.x, p.y]; B.pedro.start.h = p.h; } else Object.assign(B.steps.plan.start, { x: p.x, y: p.y, h: p.h });
		});
	};
	setVal('bSx', +st.x); setVal('bSy', +st.y); setVal('bSh', +st.h || 0);
	setVal('bNote', st.note);
	const len = String(st.note ?? '').trim().length;
	$('bNoteHelp').innerHTML = `Shown on the Driver Station at INIT as <b>"Place robot : ${esc(st.note ?? '')}"</b>: the drive team reads it at match setup. `
		+ `<span class="${len > S.NOTE_MAX ? 'b-warn' : ''}">${len} of about ${S.NOTE_MAX} characters (one line).</span> `
		+ (st.noteAuto ? 'Written from the spot: it follows the robot until you type your own.' : '');
	$('bStartOnRow').hidden = B.mode !== 'pedro';
	$('bStartOn').checked = !!B.pedro.start.on;
}
const onNum = (id, fn) => $(id).addEventListener('change', () => { const v = parseFloat($(id).value); if (Number.isFinite(v)) change(() => fn(v)); });
onNum('bSx', (v) => { if (B.mode === 'pedro') B.pedro.path.start = clampXY(v, B.pedro.path.start[1]); else B.steps.plan.start.x = clampXY(v, 0)[0]; });
onNum('bSy', (v) => { if (B.mode === 'pedro') B.pedro.path.start = clampXY(B.pedro.path.start[0], v); else B.steps.plan.start.y = clampXY(0, v)[1]; });
onNum('bSh', (v) => { if (B.mode === 'pedro') B.pedro.start.h = P.wrap(v); else B.steps.plan.start.h = P.wrap(v); });
$('bNote').oninput = () => {
	const st = B.mode === 'pedro' ? B.pedro.start : B.steps.plan.start;
	st.note = $('bNote').value; st.noteAuto = false;
	renderCode();
};
$('bNote').addEventListener('change', () => render());
$('bWrite').onclick = () => change(() => { (B.mode === 'pedro' ? B.pedro.start : B.steps.plan.start).noteAuto = true; });
$('bStartOn').onchange = () => change(() => { B.pedro.start.on = $('bStartOn').checked; });
$('bCard').onclick = () => {
	const st = startNow(), lw = robotLW();
	let first = '', title, warn = '';
	if (B.mode === 'pedro') {
		const s = B.pedro, p0 = s.path.parts[0];
		title = s.opmode || ('Path: ' + (s.label || 'My path'));
		first = !p0 ? '' : s.mode === 'driveTo'
			? `${s.path.parts.length === 1 ? s.label : s.label + ' 1'}: driveTo (${P.num(p0.end[0])}, ${P.num(p0.end[1])})`
			: `${s.label}: follow the path (${s.path.parts.length} part${s.path.parts.length === 1 ? '' : 's'})`;
		if (!s.start.on) warn = 'The s.start(...) line is off: turn it on, or the Driver Station does not show this note.';
	} else {
		title = B.steps.plan.opmode || 'My Auto';
		const s0 = B.steps.plan.steps[0];
		first = s0 ? S.stepWords(s0) : '';
	}
	openSetupCard({ title, note: st.note, x: +st.x, y: +st.y, h: +st.h || 0, robot: lw, mirror: mirrorNow(), spot: B.mode === 'pedro' || st.spot !== false,
		delay: B.mode === 'pedro' ? null : 0, first, warn, field });
};

// ------------------------------------------------------------------ Simple steps: the list

const KIND_TEXT = { move: 'Drive', turn: 'Turn', turnTo: 'Turn to', wait: 'Wait', run: 'Start something', action: 'Action', alongside: 'Action', park: 'Park from here' };
const DIRS = [[0, 'forward'], [180, 'back'], [90, 'right (strafe)'], [270, 'left (strafe)']];
const NEW = {
	move: () => ({ kind: 'move', label: 'Drive', inches: 24, dir: 0, power: 0.6 }),
	strafe: () => ({ kind: 'move', label: 'Strafe', inches: 12, dir: 90, power: 0.6 }),
	turn: () => ({ kind: 'turn', label: 'Turn', deg: 90, power: 0.5 }),
	turnTo: () => ({ kind: 'turnTo', label: 'Square up', heading: 0, power: 0.5 }),
	wait: () => ({ kind: 'wait', label: 'Wait', sec: 1 }),
	run: () => ({ kind: 'run', label: 'Hold the piece', code: 'robot.claw::close' }),
	action: () => ({ kind: 'action', code: 'Actions.scoreHigh(robot)', timeout: 5 }),
	park: () => ({ kind: 'park', sec: 3 }),
};
for (const b of $('bAdd').querySelectorAll('[data-add]')) {
	b.onclick = () => change(() => {
		const steps = B.steps.plan.steps;
		const i = B.sel != null ? B.sel + 1 : steps.length;
		steps.splice(i, 0, NEW[b.dataset.add]());
		B.sel = i;
	});
}

function stepForm(s, i) {
	const e = view.sim.ends[i];
	const name = (ph) => `<label class="grow">Name <input type="text" data-k="label" value="${esc(s.label ?? '')}" placeholder="${ph}"></label>`;
	const power = `<label>power <input type="number" min="0.1" max="1" step="0.05" data-k="power" value="${fmt(+s.power)}"></label>`;
	const code = (ph) => `<label class="grow">${s.kind === 'run' ? 'Command' : 'Action'} <input type="text" class="code" data-k="code" spellcheck="false" autocapitalize="off" value="${esc(s.code ?? '')}" placeholder="${ph}" list="${s.kind === 'run' ? 'bCommands' : 'bActionList'}"></label>`;
	switch (s.kind) {
		case 'move': {
			const known = DIRS.some(([d]) => d === +s.dir);
			const opts = DIRS.filter(([d]) => B.steps.plan.drive !== 'tank' || d === 0 || d === 180 || d === +s.dir)
				.map(([d, t]) => `<option value="${d}"${d === +s.dir ? ' selected' : ''}>${t}</option>`).join('')
				+ (B.steps.plan.drive !== 'tank' || !known ? `<option value="other"${known ? '' : ' selected'}>another angle</option>` : '');
			return `<div class="row">${name('To the goal')}</div><div class="row"><label><input type="number" step="0.5" data-k="inches" value="${fmt(+s.inches)}"> in</label>
				<select data-k="dirSel" aria-label="Direction">${opts}</select>${known ? '' : `<label><input type="number" step="5" data-k="dir" value="${fmt(+s.dir)}"> deg</label>`}${power}</div>`;
		}
		case 'turn': return `<div class="row">${name('Face the goal')}</div><div class="row"><label>by <input type="number" step="5" data-k="deg" value="${fmt(+s.deg)}"> deg</label>${power}</div><p class="muted">+ left, - right. Ends facing ${fmt(e.h)}.</p>`;
		case 'turnTo': return `<div class="row">${name('Square up')}</div><div class="row"><label>to <input type="number" step="5" data-k="heading" value="${fmt(+s.heading)}"> deg</label>${power}</div><p class="muted">0 = the start heading. Ends facing ${fmt(e.h)} on the field.</p>`;
		case 'wait': return `<div class="row">${name('Let it settle')}<label><input type="number" min="0" step="0.1" data-k="sec" value="${fmt(+s.sec)}"> s</label></div>`;
		case 'run': return `<div class="row">${name('Hold the piece')}</div><div class="row">${code('robot.claw::close')}</div>`;
		case 'action': case 'alongside':
			return `<div class="row">${code('Actions.scoreHigh(robot)')}</div><div class="row"><label><input type="checkbox" data-k="wait"${s.kind === 'action' ? ' checked' : ''}> wait until done</label>
				${s.kind === 'action' ? `<label>at most <input type="number" min="0.5" step="0.5" data-k="timeout" value="${fmt(+s.timeout)}"> s</label>` : ''}</div>`;
		case 'park': return `<div class="row"><label>Keep the last <input type="number" min="0.5" step="0.5" data-k="sec" value="${fmt(+s.sec)}"> s for the steps below</label></div>`;
		default: return '';
	}
}

function renderSteps() {
	const box = $('bList');
	const act = document.activeElement;
	const focused = act?.closest?.('#bList [data-i]') ? [act.closest('[data-i]').dataset.i, act.dataset.k, act.selectionStart] : null;
	const steps = B.steps.plan.steps;
	if (B.sel != null && !steps[B.sel]) B.sel = null;
	box.innerHTML = steps.length ? '' : '<p class="muted">No steps yet. Tap the map, or press a button above.</p>';
	const tank = B.steps.plan.drive === 'tank';
	$('bAddStrafe').hidden = tank;
	steps.forEach((s, i) => {
		const e = view.sim.ends[i];
		const div = document.createElement('div');
		div.className = 'b-step' + (i === B.sel ? ' sel' : '') + (MOVING.has(s.kind) ? '' : ' mark');
		div.dataset.i = i;
		const mine = view.checks.filter((c) => c.step === i);
		div.innerHTML = `<div class="b-top"><b data-sel><span class="b-num">${i + 1}</span>${KIND_TEXT[s.kind] ?? s.kind}</b>
			<span class="muted">${fmt(e.t0)}-${fmt(e.t1)} s</span>
			<span class="b-btns"><button class="small" data-up title="Move up"${i === 0 ? ' disabled' : ''}>&#8593;</button><button class="small" data-down title="Move down"${i === steps.length - 1 ? ' disabled' : ''}>&#8595;</button><button class="small" data-del title="Remove">&times;</button></span></div>
			${stepForm(s, i)}
			${mine.length ? `<ul class="b-checks">${mine.map((c) => `<li class="${c.level}">${esc(c.text)}</li>`).join('')}</ul>` : ''}`;
		div.querySelector('[data-sel]').onclick = () => { B.sel = B.sel === i ? null : i; render(); };
		div.querySelector('[data-del]').onclick = () => change(() => { steps.splice(i, 1); B.sel = null; });
		div.querySelector('[data-up]').onclick = () => change(() => { steps.splice(i - 1, 0, steps.splice(i, 1)[0]); B.sel = i - 1; });
		div.querySelector('[data-down]').onclick = () => change(() => { steps.splice(i + 1, 0, steps.splice(i, 1)[0]); B.sel = i + 1; });
		div.addEventListener('change', (evt) => stepInput(i, evt.target));
		div.addEventListener('input', (evt) => {
			const k = evt.target.dataset.k;
			if (k !== 'label' && k !== 'code') return;
			s[k] = evt.target.value;
			renderCode();
		});
		div.addEventListener('focusin', () => { B.sel = i; });
		box.appendChild(div);
	});
	if (focused) {
		const e = box.querySelector(`[data-i="${focused[0]}"] [data-k="${focused[1]}"]`);
		if (e && e.type !== 'checkbox') { e.focus({ preventScroll: true }); try { if (focused[2] != null) e.setSelectionRange(focused[2], focused[2]); } catch { /* number inputs */ } }
	}
}

function stepInput(i, t) {
	const k = t.dataset.k;
	if (!k) return;
	const v = parseFloat(t.value);
	change(() => {
		const s = B.steps.plan.steps[i];
		B.sel = i;
		if (k === 'dirSel') { if (t.value !== 'other') s.dir = +t.value; else if (DIRS.some(([d]) => d === +s.dir)) s.dir = 45; }
		else if (k === 'wait') { if (t.checked) { s.kind = 'action'; s.timeout = s.timeout ?? 5; } else s.kind = 'alongside'; }
		else if (k === 'label' || k === 'code') s[k] = t.value;
		else if (Number.isFinite(v)) s[k] = v;   // out of range stays: the checks say what is wrong
	});
}

// ------------------------------------------------------------------ Pedro: parts and PathAt

const HEADINGS = [['path', 'Face along the path'], ['back', 'Drive backwards along it'], ['keep', 'Keep one heading'], ['turn', 'Turn from one heading to another'], ['point', 'Face a point']];
const AKIND = { inchesLeft: 'Inches left', inchesDone: 'Inches done', fraction: 'Part of the path (0 to 1)', part: 'Start of part', near: 'Near a point' };

function renderPedro() {
	const st = B.pedro;
	for (const r of document.querySelectorAll('input[name=bPMode]')) r.checked = r.value === st.mode;
	$('bPowerBox').hidden = st.mode !== 'driveTo';
	$('bLimitBox').hidden = st.mode !== 'follow';
	setVal('bPower', st.power); setVal('bLabel', st.label); setVal('bName', st.name || ''); setVal('bTimeout', st.timeoutSec ?? '');
	$('bName').placeholder = P.constName(st.label);
	$('bHold').checked = st.path.holdSec > 0;
	const box = $('bParts');
	const focusK = document.activeElement?.closest?.('#bParts') ? [document.activeElement.closest('[data-i]')?.dataset.i, document.activeElement.dataset.k] : null;
	box.innerHTML = st.path.parts.length ? '' : '<p class="muted">No parts yet. Tap the map: each tap adds a part from the last end (the green dot first).</p>';
	const samples = P.sample(st.path);
	st.path.parts.forEach((part, i) => {
		const ends = P.partEnds(samples, i);
		const shape = part.ctrl.length === 0 ? 'line' : part.ctrl.length === 1 ? 'curve1' : part.ctrl.length === 2 ? 'curve2' : 'curveN';
		const h = part.heading;
		const div = document.createElement('div');
		div.className = 'b-step' + (i === B.selPart ? ' sel' : '');
		div.dataset.i = i;
		div.innerHTML = `<div class="b-top"><b data-sel><span class="b-num">${i + 1}</span>Part</b><span class="muted">to (${fmt(part.end[0])}, ${fmt(part.end[1])}), facing ${fmt(ends.start)} then ${fmt(ends.end)}</span>
			<span class="b-btns"><button class="small" data-del title="Remove">&times;</button></span></div>
			<div class="row"><select data-k="shape" aria-label="Shape"><option value="line">Straight line</option><option value="curve1">Curve, 1 control point</option><option value="curve2">Curve, 2 control points</option>${shape === 'curveN' ? `<option value="curveN">Curve, ${part.ctrl.length} control points</option>` : ''}</select>
			<label>end x <input type="number" step="0.5" data-k="ex" value="${fmt(part.end[0])}"></label><label>y <input type="number" step="0.5" data-k="ey" value="${fmt(part.end[1])}"></label></div>
			<div class="row"><select data-k="mode" aria-label="Heading">${HEADINGS.map(([v, t]) => `<option value="${v}"${v === h.mode ? ' selected' : ''}>${t}</option>`).join('')}</select>
			${h.mode === 'keep' ? `<label><input type="number" step="5" data-k="a" value="${fmt(h.a)}"> deg</label>` : ''}
			${h.mode === 'turn' ? `<label>from <input type="number" step="5" data-k="a" value="${fmt(h.a)}"></label><label>to <input type="number" step="5" data-k="b" value="${fmt(h.b)}"></label>` : ''}
			${h.mode === 'point' ? `<label>x <input type="number" step="0.5" data-k="px" value="${fmt(h.x)}"></label><label>y <input type="number" step="0.5" data-k="py" value="${fmt(h.y)}"></label>` : ''}
			<label>max speed <input type="number" min="0.05" max="1" step="0.05" data-k="speed" value="${part.maxSpeed ?? ''}" placeholder="full"></label></div>`;
		div.querySelector('select[data-k=shape]').value = shape;
		div.querySelector('[data-sel]').onclick = () => { B.selPart = i; render(); };
		div.querySelector('[data-del]').onclick = () => change(() => { st.path.parts.splice(i, 1); B.selPart = Math.max(0, Math.min(B.selPart, st.path.parts.length - 1)); });
		div.addEventListener('change', (evt) => partInput(i, evt.target));
		box.appendChild(div);
	});
	if (focusK && focusK[0] != null) box.querySelector(`[data-i="${focusK[0]}"] [data-k="${focusK[1]}"]`)?.focus({ preventScroll: true });
	renderActions();
}

function partInput(i, t) {
	const k = t.dataset.k;
	if (!k) return;
	const v = parseFloat(t.value);
	change(() => {
		const path = B.pedro.path, part = path.parts[i];
		B.selPart = i;
		if (k === 'shape') {
			const n = { line: 0, curve1: 1, curve2: 2 }[t.value];
			if (n != null) part.ctrl = n === 0 ? [] : curveControls(P.partStart(path, i), part.end, n);
		} else if (k === 'mode') {
			const ends = P.partEnds(P.sample(path), i);
			const mode = t.value;
			part.heading = mode === 'keep' ? { mode, a: P.round1(ends.start) } : mode === 'turn' ? { mode, a: P.round1(ends.start), b: P.round1(ends.end) }
				: mode === 'point' ? { mode, x: 0, y: 0 } : { mode };
		} else if (k === 'speed') part.maxSpeed = Number.isFinite(v) && v > 0 ? Math.min(1, v) : null;
		else if (Number.isFinite(v)) {
			if (k === 'ex') part.end = clampXY(v, part.end[1]);
			else if (k === 'ey') part.end = clampXY(part.end[0], v);
			else if (k === 'a') part.heading.a = v;
			else if (k === 'b') part.heading.b = v;
			else if (k === 'px') part.heading.x = v;
			else if (k === 'py') part.heading.y = v;
		}
	});
}

const UNIT = { inchesLeft: 'in before the end', inchesDone: 'in from the start', fraction: 'of the whole path (0.5 = halfway)', part: '(part 1 = at once)' };
function renderActions() {
	const st = B.pedro, m = view.m;
	const box = $('bActions');
	box.innerHTML = st.actions.length ? '' : '<p class="muted">None. "+ Add", or choose "an action" above and tap near the path.</p>';
	if (B.selAction != null && !st.actions[B.selAction]) B.selAction = null;
	P.ordered(st.actions, m).forEach((a, n) => {
		const i = st.actions.indexOf(a);
		const place = P.actionPlace(a, m);
		const div = document.createElement('div');
		div.className = 'b-step' + (i === B.selAction ? ' sel' : '');
		div.dataset.i = i;
		const value = Number.isFinite(+a.value) ? fmt(+a.value) : '';
		div.innerHTML = `<div class="b-top"><b data-sel><span class="b-num">${n + 1}</span>${esc((a.what === 'action' ? a.code : a.label) || '(no name)')}</b>
			<span class="muted">${place.when === 'never' ? 'never starts' : `at ${fmt(place.in)} in`}</span><span class="b-btns"><button class="small" data-del title="Remove">&times;</button></span></div>
			<div class="row"><select data-k="kind" aria-label="When">${P.KINDS.map((k) => `<option value="${k}"${k === a.kind ? ' selected' : ''}>${AKIND[k]}</option>`).join('')}</select>
			${a.kind === 'near' ? `<label>x <input type="number" step="0.5" data-k="x" value="${fmt(a.x)}"></label><label>y <input type="number" step="0.5" data-k="y" value="${fmt(a.y)}"></label><label>within <input type="number" step="0.5" data-k="value" value="${value}"> in</label>`
				: `<label><input type="number" step="${a.kind === 'fraction' ? 0.05 : a.kind === 'part' ? 1 : 0.5}" data-k="value" value="${value}"> ${UNIT[a.kind] || ''}</label>`}</div>
			<div class="row"><label><input type="radio" name="bWhat${i}" value="command" data-k="what"${a.what !== 'action' ? ' checked' : ''}> name + command</label>
				<label><input type="radio" name="bWhat${i}" value="action" data-k="what"${a.what === 'action' ? ' checked' : ''}> an action</label></div>
			${a.what === 'action' ? `<div class="row"><label class="grow">Action <input type="text" class="code" data-k="code" spellcheck="false" value="${esc(a.code)}" list="bActionList"></label></div>`
				: `<div class="row"><label class="grow">Name <input type="text" data-k="label" value="${esc(a.label)}"></label></div><div class="row"><label class="grow">Command <input type="text" class="code" data-k="code" spellcheck="false" value="${esc(a.code)}" list="bCommands"></label></div>`}`;
		div.querySelector('[data-sel]').onclick = () => { B.selAction = i; render(); };
		div.querySelector('[data-del]').onclick = () => change(() => { st.actions.splice(i, 1); B.selAction = null; });
		div.addEventListener('change', (evt) => actInput(i, evt.target));
		div.addEventListener('input', (evt) => { const k = evt.target.dataset.k; if (k === 'label' || k === 'code') { a[k] = evt.target.value; renderCode(); } });
		box.appendChild(div);
	});
}

function actInput(i, t) {
	const k = t.dataset.k;
	if (!k) return;
	const v = parseFloat(t.value);
	change(() => {
		const a = B.pedro.actions[i], m = view.m;
		B.selAction = i;
		if (k === 'kind') {
			const place = P.actionPlace(a, m);
			a.kind = t.value;
			if (a.kind === 'near') { a.x = snap(place.x); a.y = snap(place.y); a.value = 6; } else { delete a.x; delete a.y; setPlace(a, place.in, m); }
		} else if (k === 'what') {
			a.what = t.value;
			if (a.what === 'action' && !/^Actions\./.test(a.code)) a.code = 'Actions.scoreHigh(robot)';
			if (a.what === 'command' && /^Actions\./.test(a.code)) a.code = NEW_ACTION.code;
		} else if (k === 'label' || k === 'code') a[k] = t.value;
		else if (Number.isFinite(v)) a[k] = v;
	});
}

$('bClear').onclick = () => change(() => { B.steps.plan.steps = []; B.sel = null; });
$('bExample').onclick = () => change(() => { B.steps.plan = structuredClone(STEPS_EXAMPLE); B.sel = null; });
$('bPClear').onclick = () => change(() => { B.pedro.path.parts = []; B.pedro.path.holdSec = 0; B.pedro.actions = []; B.selPart = 0; });
$('bPExample').onclick = () => change(() => { Object.assign(B.pedro, structuredClone(PEDRO_EXAMPLE)); B.selPart = 1; });
$('bAddAct').onclick = () => change(() => {
	const st = B.pedro;
	st.actions.push({ kind: 'fraction', value: 0.5, ...NEW_ACTION });
	B.selAction = st.actions.length - 1;
});
for (const r of document.querySelectorAll('input[name=bPMode]')) r.onchange = () => change(() => { B.pedro.mode = r.value; });
onNum('bPower', (v) => { B.pedro.power = Math.max(0.05, Math.min(1, v)); });
$('bTimeout').addEventListener('change', () => { const v = parseFloat($('bTimeout').value); change(() => { B.pedro.timeoutSec = Number.isFinite(v) && v > 0 ? v : null; }); });
$('bHold').onchange = () => change(() => { B.pedro.path.holdSec = $('bHold').checked ? P.DEFAULT_HOLD_SEC : 0; });
$('bLabel').oninput = () => { B.pedro.label = $('bLabel').value; $('bName').placeholder = P.constName(B.pedro.label); renderCode(); };
$('bName').oninput = () => { B.pedro.name = $('bName').value.replace(/[^A-Za-z0-9_$]/g, ''); renderCode(); };
for (const id of ['bLabel', 'bName']) $(id).addEventListener('change', () => render());

// ------------------------------------------------------------------ checks

function renderChecks() {
	const out = [];
	const no = cannotRun();
	if (no) out.push(`<li class="bad">${esc(no)}</li>`);
	const order = { bad: 0, warn: 1, good: 2 };
	const list = view.checks;
	for (const c of [...list].sort((a, b) => order[a.level] - order[b.level])) out.push(`<li class="${c.level}">${esc(c.text)}</li>`);
	if (!field) out.push('<li class="muted">Loading the field...</li>');
	else if (!list.some((c) => c.level === 'bad') && !no) out.push('<li class="good">The plan stays on the field and clears every field part.</li>');
	out.push(`<li class="muted">About ${fmt(view.tl.total)} s by the plan${B.mode === 'pedro' ? ` (at ${C.PEDRO_SPEED_IN_PER_SEC} in/s)` : ` (${fmt(B.steps.speeds.inPerSec)} in/s, ${fmt(B.steps.speeds.degPerSec)} deg/s at full power)`}. Run it to see the real time.</li>`);
	$('bChecks').innerHTML = out.join('');
}

// ------------------------------------------------------------------ run

$('bRun').onclick = async () => {
	const no = cannotRun();
	if (no) { $('bRunText').textContent = no; return; }
	const info = robotInfo();
	const program = C.toProgram(B, info ? info.name : '');
	runError = '';
	$('bRunText').textContent = 'Sending the plan...';
	preview = null; $('bPreview').textContent = 'Preview';
	try {
		const r = await (await fetch('/auto', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ program, alliance: B.alliance }) })).json();
		if (r.error) { runError = r.error; $('bRunText').textContent = 'Not run: ' + r.error; return; }
		const b = structuredClone(B);
		lastRun = { b, tl: C.timeline(b, b.steps.speeds), id: ((sim && sim.built && sim.built.id) || 0) + 1, robotClass: r.robotClass, sent: performance.now() };
		page.command({ action: 'clearPaths' });
		$('bRunText').textContent = `INIT with ${r.robotClass}, as ${B.alliance}: START comes once the robot is on its spot.`;
	} catch (e) {
		$('bRunText').textContent = 'The simulator did not answer.';
	}
};
$('bStop').onclick = () => page.command({ action: 'stop' });

/** The plan robot's pose now while the run goes (in plan numbers), or while "Preview" plays; else null. */
function ghostPose(simT) {
	const tl = view?.tl;
	if (!tl) return null;
	const b = sim && sim.built;
	if (lastRun && b && b.id === lastRun.id && b.phase === 'running' && b.t0 != null) {
		const t = (simT != null ? simT : sim.t) - b.t0;
		return { ...C.poseAtTime(lastRun.tl, C.planTimeAt(lastRun.b, lastRun.tl, b, t)), t };
	}
	if (preview) {
		const t = performance.now() / 1000 - preview.t0;
		if (t > tl.total + 1) { preview = null; $('bPreview').textContent = 'Preview'; return null; }
		return { ...C.poseAtTime(tl, t), t };
	}
	return null;
}

function renderRun() {
	const b = sim && sim.built;
	const mine = lastRun && b && b.id === lastRun.id;
	const running = mine && b.phase === 'running';
	$('bRun').disabled = !!cannotRun();
	$('bStop').disabled = !(mine && (b.phase === 'running' || b.phase === 'init'));
	const secs = running ? (sim.runSeconds || 0) : mine && b.elapsed != null ? b.elapsed : 0;
	$('bClock').textContent = `${secs.toFixed(1)} s of 30`;
	$('bClock').classList.toggle('on', !!running);
	if (!lastRun) { $('bResult').innerHTML = ''; return; }
	if (!mine) { return; }
	const error = sim.opmode === 'Built Auto' ? sim.error : '';
	if (b.phase === 'init') $('bRunText').textContent = error ? 'INIT stopped: see below.' : 'INIT: placing the robot...';
	else if (running) $('bRunText').textContent = `Running as ${b.alliance || B.alliance}: the purple robot is the plan, the real one is your code.`;
	else if (b.phase === 'done') $('bRunText').textContent = error ? 'Stopped with an error.' : `Finished in ${fmt(b.elapsed ?? secs)} s.`;
	const rows = C.compare(lastRun.b, b, lastRun.tl);
	const tolIn = lastRun.b.mode === 'pedro' ? lastRun.b.pedro.tolIn : lastRun.b.steps.tolIn;
	const fx = C.fixes(lastRun.b, b, rows, error, tolIn);
	const score = page.game && page.game.score;
	const out = [];
	if (rows.length) {
		out.push('<table class="b-table"><thead><tr><th>#</th><th>Step</th><th>Report</th><th class="num">Off</th></tr></thead><tbody>');
		rows.forEach((r) => {
			const off = r.off != null && r.moving ? `${fmt(r.off)} in` : '';
			const cls = /STALLED|TIMED|NO_ODO|skipped|cut short|not fired|timed out/.test(r.word) ? 'bad' : r.off > tolIn && r.moving ? 'warn' : '';
			out.push(`<tr class="${cls}"><td>${r.i + 1}</td><td>${esc(r.name)}</td><td>${esc(r.word || (running ? '...' : 'not run'))}</td><td class="num" title="${r.real ? `plan (${fmt(r.plan.x)}, ${fmt(r.plan.y)}, ${fmt(r.plan.h)}°), real (${fmt(r.real.x)}, ${fmt(r.real.y)}, ${fmt(r.real.h)}°)` : ''}">${off}</td></tr>`);
		});
		out.push('</tbody></table>');
	}
	if (b.phase === 'done' && score && score.red) out.push(`<p class="muted">Score: <b class="sr">RED ${Math.round(score.red.total || 0)}</b> &middot; <b class="sb">BLUE ${Math.round(score.blue?.total || 0)}</b> (Match panel: where the points come from)</p>`);
	if (fx.length) {
		out.push('<ul class="b-checks">');
		fx.forEach((f, k) => out.push(`<li class="${f.level}">${esc(f.text)}${f.fix ? ` <button class="small" data-fix="${k}">Apply</button>` : ''}</li>`));
		out.push('</ul>');
	}
	const key = out.join('');
	if ($('bResult').dataset.key !== key) {
		$('bResult').dataset.key = key;
		$('bResult').innerHTML = key;
		for (const btn of $('bResult').querySelectorAll('[data-fix]')) {
			btn.onclick = () => {
				const f = fx[+btn.dataset.fix];
				// The fix goes into the plan being edited (the same mode as the run).
				change(() => { B.mode = lastRun.b.mode; C.applyFix(B, f.fix); });
				$('bRunText').textContent = 'Changed: press Run again to try it.';
			};
		}
	}
}

// ------------------------------------------------------------------ Java, scenario, setup card

function renderNames() {
	const box = $('bNames');
	const key = B.mode;
	if (box.dataset.key === key) return;
	box.dataset.key = key;
	box.innerHTML = B.mode === 'pedro'
		? '<label>Your autonomous\'s name (for the scenario) <input type="text" id="bOpmode"></label>'
		: '<label>OpMode <input type="text" id="bOpmode"></label><label>TeleOp next <input type="text" id="bTeleop"></label><label>package <input type="text" id="bPkg"></label>';
	$('bOpmode').oninput = () => { if (B.mode === 'pedro') B.pedro.opmode = $('bOpmode').value; else B.steps.plan.opmode = $('bOpmode').value; renderCode(); };
	if ($('bTeleop')) {
		$('bTeleop').oninput = () => { B.steps.plan.teleop = $('bTeleop').value; renderCode(); };
		$('bPkg').oninput = () => { B.steps.plan.pkg = $('bPkg').value.trim(); renderCode(); };
	}
}

function javaNow() {
	if (B.mode === 'pedro') {
		const st = B.pedro;
		// The web builder's Pedro output, letter for letter (pathcore.toJava).
		return st.path.parts.length ? P.toJava(st.path, { label: st.label || 'My path', name: st.name || undefined, start: st.start, mode: st.mode, power: st.power,
			actions: st.actions, timeoutSec: st.mode === 'follow' ? st.timeoutSec : null }).all : '// Add a part first.';
	}
	const plan = B.steps.plan;
	const j = S.toJava({ ...plan, className: plan.className || undefined });
	return B.view === 'steps' ? '// In steps(Steps s, Robot robot):\n' + j.steps : j.file;
}

function simFileName() {
	const name = B.mode === 'pedro' ? B.pedro.opmode : B.steps.plan.opmode;
	return (name || 'my auto').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') + '.sim';
}

function scenarioNow() {
	const s = B.mode === 'pedro' ? B.pedro : B.steps;
	let text = B.mode === 'pedro'
		? C.pedroScenario(B.pedro, { tolIn: s.tolIn, tolDeg: s.tolDeg, fileName: simFileName() })
		: S.toScenario(B.steps.plan, { tolIn: s.tolIn, tolDeg: s.tolDeg, fileName: simFileName(), speeds: B.steps.speeds });
	const rec = recordedEnd();
	if (s.recorded && rec) text = C.recordedScenario(text, rec, mirrorNow(), new Date().toISOString().slice(0, 10));
	return text;
}

// The end pose of the last finished run of THIS plan (plan numbers), or null (no run, or the plan changed since).
function recordedEnd() {
	const b = sim && sim.built;
	if (!lastRun || !b || b.id !== lastRun.id || b.phase !== 'done' || !b.end) return null;
	const same = B.mode === lastRun.b.mode && JSON.stringify(B.mode === 'pedro' ? B.pedro.path : B.steps.plan.steps) === JSON.stringify(lastRun.b.mode === 'pedro' ? lastRun.b.pedro.path : lastRun.b.steps.plan.steps);
	if (!same) return null;
	return C.fieldToPlan({ x: b.end[0], y: b.end[1], h: b.end[2] }, b.alliance || 'RED', mirrorOf(lastRun.b));
}

function renderCode() {
	renderNames();
	const s = B.mode === 'pedro' ? B.pedro : B.steps;
	setVal('bOpmode', B.mode === 'pedro' ? B.pedro.opmode : B.steps.plan.opmode);
	if ($('bTeleop')) { setVal('bTeleop', B.steps.plan.teleop); setVal('bPkg', B.steps.plan.pkg); }
	$('bViewRow').hidden = B.mode === 'pedro';
	$('bView').value = B.view;
	setVal('bTolIn', s.tolIn); setVal('bTolDeg', s.tolDeg);
	$('bRecorded').checked = !!s.recorded;
	$('bRecorded').disabled = !recordedEnd();
	$('bRecorded').parentElement.title = recordedEnd() ? 'The scenario checks the end the last run really reached' : 'Run this plan first (it must finish)';
	$('bCode').textContent = javaNow();
	$('bSim').textContent = scenarioNow();
}
$('bView').onchange = () => { B.view = $('bView').value; renderCode(); save(); };
$('bRecorded').onchange = () => { (B.mode === 'pedro' ? B.pedro : B.steps).recorded = $('bRecorded').checked; renderCode(); save(); };
onNum('bTolIn', (v) => { (B.mode === 'pedro' ? B.pedro : B.steps).tolIn = Math.max(0.1, v); });
onNum('bTolDeg', (v) => { (B.mode === 'pedro' ? B.pedro : B.steps).tolDeg = Math.max(1, v); });

async function copy(text, out, pre) {
	try { await navigator.clipboard.writeText(text); out.textContent = 'Copied.'; } catch {
		pre.closest('details').open = true;
		const r = document.createRange(); r.selectNodeContents(pre);
		const sel = getSelection(); sel.removeAllRanges(); sel.addRange(r);
		out.textContent = 'Selected: press Ctrl+C (or Cmd+C) to copy.';
	}
	setTimeout(() => { out.textContent = ''; }, 3000);
}
$('bCopy').onclick = () => copy(javaNow(), $('bCopied'), $('bCode'));
$('bSaveSim').onclick = () => {
	const a = document.createElement('a');
	a.href = URL.createObjectURL(new Blob([scenarioNow()], { type: 'text/plain' }));
	a.download = simFileName();
	a.click();
	setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};

// Names teams often use, for the code boxes' suggestions (their own names go here too).
document.body.insertAdjacentHTML('beforeend', `<datalist id="bCommands">${S.COMMON_COMMANDS.map((c) => `<option value="${esc(c)}">`).join('')}</datalist>`
	+ `<datalist id="bActionList">${S.COMMON_ACTIONS.map((c) => `<option value="${esc(c)}">`).join('')}</datalist>`);

// ------------------------------------------------------------------ 3D: the plan line, the points, the plan robot

const T = () => window.THREE;
const g3 = { group: null, ghost: null, key: '', handles: [] };

// Plan numbers -> the simulator's field numbers for the alliance chosen (the one Run uses).
const toFieldNow = (p) => C.planToField(p, B.alliance, mirrorNow());

function draw3d() {
	const v3 = page.v3;
	if (!v3 || !v3.ready || !T()) return;
	const THREE = T();
	if (!g3.group) {
		g3.group = new THREE.Group();
		v3.scene.add(g3.group);
		g3.ghost = new THREE.Group();
		const box = new THREE.Mesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshBasicMaterial({ color: GHOST, transparent: true, opacity: 0.22, depthWrite: false }));
		const edges = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(1, 1, 1)), new THREE.LineBasicMaterial({ color: GHOST }));
		const arrow = new THREE.Mesh(new THREE.ShapeGeometry(new THREE.Shape([new THREE.Vector2(0, 0.5), new THREE.Vector2(-0.22, 0.2), new THREE.Vector2(0.22, 0.2)])),
			new THREE.MeshBasicMaterial({ color: GHOST, side: THREE.DoubleSide }));
		g3.ghost.add(box, edges, arrow);
		g3.ghost.userData = { box, edges, arrow };
		v3.scene.add(g3.ghost);
	}
	const on = B.open && view;
	g3.group.visible = !!on;
	g3.ghost.visible = false;
	if (!on) return;
	const tl = view.tl;
	const key = JSON.stringify([tl.samples.map((s) => [P.round1(s.x), P.round1(s.y)]), handles(), B.alliance, mirrorNow(), robotLW()]);
	if (key === g3.key) return;
	g3.key = key;
	g3.group.traverse((o) => { if (o.geometry) o.geometry.dispose(); if (o.material) o.material.dispose(); });
	g3.group.clear();
	g3.handles = [];
	// The plan line, purple and dashed, just above the tiles (the simulator's green trail is where it really went).
	const line = new THREE.Line(new THREE.BufferGeometry().setFromPoints(tl.samples.map((s) => { const f = toFieldNow(s); return new THREE.Vector3(f.x, f.y, 0.8); })),
		new THREE.LineDashedMaterial({ color: PLAN, dashSize: 2.5, gapSize: 1.5 }));
	line.computeLineDistances();
	g3.group.add(line);
	// The start footprint.
	const lw = robotLW(), st = startNow();
	const fp = P.footprint(+st.x, +st.y, +st.h || 0, lw[0], lw[1]).map(([x, y]) => toFieldNow({ x, y, h: 0 }));
	g3.group.add(new THREE.LineLoop(new THREE.BufferGeometry().setFromPoints(fp.map((p) => new THREE.Vector3(p.x, p.y, 0.7))), new THREE.LineBasicMaterial({ color: START })));
	// The points: a ball each (grab one to drag it when "Edit in the 3D view" is on).
	for (const h of handles()) {
		const f = toFieldNow(h);
		const color = { start: START, end: PATH, move: PATH, ctrl: 0xffffff, point: ACT }[h.kind];
		const ball = new THREE.Mesh(new THREE.SphereGeometry(h.kind === 'ctrl' ? 1.4 : 1.9, 16, 12), new THREE.MeshBasicMaterial({ color }));
		ball.position.set(f.x, f.y, 2);
		ball.userData.id = h.id;
		g3.group.add(ball);
		g3.handles.push(ball);
	}
}

// Each frame of the 3D view: the plan robot at the same moment as the real one (on the simulator's
// clock, so they start together), or the preview.
function frame(smp) {
	if (!g3.ghost) return;
	const p = B.open ? ghostPose(smp ? smp.t : null) : null;
	if (!p) { g3.ghost.visible = false; return; }
	const f = toFieldNow(p);
	const [L, W] = robotLW();
	const { box, edges, arrow } = g3.ghost.userData;
	const H = 6;
	box.scale.set(W, L, H); box.position.z = H / 2;
	edges.scale.set(W, L, H); edges.position.z = H / 2;
	arrow.scale.set(Math.min(W, L), L, 1); arrow.position.z = H + 0.1;
	g3.ghost.position.set(f.x, f.y, 0);
	g3.ghost.rotation.z = f.h * Math.PI / 180;
	g3.ghost.visible = true;
}

// Dragging the points in 3D (capture phase: a grab on a point takes the pointer before the view turns).
function setup3dDrag() {
	const stage = $('stage');
	const THREE = T();
	const ray = new THREE.Raycaster(), floor = new THREE.Plane(new THREE.Vector3(0, 0, 1), 0);
	const at = (ev) => {
		const v3 = page.v3, b = v3.renderer.domElement.getBoundingClientRect();
		ray.setFromCamera(new THREE.Vector2((ev.clientX - b.left) / b.width * 2 - 1, -(ev.clientY - b.top) / b.height * 2 + 1), v3.camera);
		const p = new THREE.Vector3();
		return ray.ray.intersectPlane(floor, p) ? p : null;
	};
	const toPlan = (p) => C.fieldToPlan({ x: p.x, y: p.y, h: 0 }, B.alliance, mirrorNow());
	const g = { id: null, x0: 0, y0: 0, moved: false, before: null, tap: null, sentAt: 0 };
	const editing = () => B.open && B.edit3d && !(sim && sim.phase === 'RUNNING');
	stage.addEventListener('pointerdown', (ev) => {
		if (!editing() || ev.button !== 0) return;
		const p = at(ev);
		if (!p) return;
		g3.group.updateMatrixWorld();   // balls made since the last frame have their places too
		const hit = ray.intersectObjects(g3.handles, false)[0];
		g.x0 = ev.clientX; g.y0 = ev.clientY; g.moved = false;
		if (hit) {
			g.id = hit.object.userData.id;
			g.before = JSON.stringify(B);
			page.v3.controls.enabled = false;
			stage.setPointerCapture(ev.pointerId);
			ev.stopPropagation(); ev.preventDefault();
		} else {
			g.id = null;
			g.tap = toPlan(p);
		}
	}, true);
	stage.addEventListener('pointermove', (ev) => {
		if (!g.id) return;
		if (Math.hypot(ev.clientX - g.x0, ev.clientY - g.y0) > 4) g.moved = true;
		const p = at(ev);
		if (!p || !g.moved) return;
		const q = toPlan(p);
		moveHandle(g.id, q.x, q.y);
		if (performance.now() - g.sentAt > 40) { g.sentAt = performance.now(); quickRender(); }
		ev.stopPropagation();
	}, true);
	const end = (ev) => {
		if (g.id) {
			if (g.moved) undoList.push(g.before);
			g.id = null;
			page.v3.controls.enabled = true;
			render();
			ev.stopPropagation();
			return;
		}
		// A tap on the field (no drag, away from the real robot): add a step or a part, as on the map.
		if (g.tap && editing() && Math.hypot(ev.clientX - g.x0, ev.clientY - g.y0) < 4) {
			const r = sim && sim.robot;
			const pr = r ? C.fieldToPlan({ x: r.x, y: r.y, h: 0 }, B.alliance, mirrorNow()) : null;
			if (!pr || Math.hypot(pr.x - g.tap.x, pr.y - g.tap.y) > 12) tapAt(g.tap.x, g.tap.y);
		}
		g.tap = null;
	};
	stage.addEventListener('pointerup', end, true);
	stage.addEventListener('pointercancel', () => { g.id = null; g.tap = null; if (page.v3.controls) page.v3.controls.enabled = true; }, true);
}

// The Control Hub's field map (the Robot panel): the plan line and the plan robot there too.
function map2d(ctx, X, Y) {
	if (!B.open || !view) return;
	const tl = view.tl;
	ctx.save();
	ctx.strokeStyle = PLAN; ctx.lineWidth = 2; ctx.setLineDash([5, 4]);
	ctx.beginPath();
	tl.samples.forEach((s, i) => { const f = toFieldNow(s); if (i) ctx.lineTo(X(f.x), Y(f.y)); else ctx.moveTo(X(f.x), Y(f.y)); });
	ctx.stroke(); ctx.setLineDash([]);
	const p = ghostPose();
	if (p) {
		const f = toFieldNow(p), [L, W] = robotLW();
		const fp = P.footprint(f.x, f.y, f.h, L, W);
		ctx.globalAlpha = 0.35; ctx.fillStyle = GHOST;
		ctx.beginPath(); fp.forEach(([x, y], i) => (i ? ctx.lineTo(X(x), Y(y)) : ctx.moveTo(X(x), Y(y)))); ctx.closePath(); ctx.fill();
		ctx.globalAlpha = 1;
	}
	ctx.restore();
}

// ------------------------------------------------------------------ the page's hooks

page.hooks.state = (s) => {
	const before = sim;
	sim = s;
	if (!B.open) return;
	// The run's table and clock 10 times a second; the rest only when the run's step count changes.
	const b = s.built, ob = before && before.built;
	const moved = !ob || ob.id !== b.id || ob.phase !== b.phase || (ob.report || []).length !== (b.report || []).length
		|| (before.robot && s.robot && (before.robot.length !== s.robot.length || before.robot.width !== s.robot.width));
	if (moved) render(); else { renderRun(); if (preview || b.phase === 'running') renderMap(); }
};
page.hooks.frame = frame;
page.hooks.map2d = map2d;
page.hooks.ready3d = () => { setup3dDrag(); g3.key = ''; draw3d(); };
if (page.v3 && page.v3.ready) page.hooks.ready3d();

setOpen(!!B.open);
if (!B.open) loadSimInfo();
