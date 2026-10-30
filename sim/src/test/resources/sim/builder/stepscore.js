// The "Simple steps" builder's logic, with no page code: an autonomous as FTC-Helper-Lib's Steps list
// (move, turn, turnTo, waitSeconds, run, action, alongside, parkWithSeconds), where the robot goes if
// every step does exactly what it says (dead reckoning: a plan, not physics), the time it takes from
// simple speed numbers, warnings, the Java (an Auto<Robot> file), a simulator scenario, and Java in.
// The page (steps.js) and the test (scripts/test-path-builder.mjs) both use it.
//
// Frame (FTC-Helper-Lib's, as in pathcore.js): seen from your drivers' wall, x inches right of the
// centre line, y inches toward the far wall, heading degrees, 0 = facing away, counterclockwise +.
// Steps are robot-relative, like the library's: move directions are degrees CLOCKWISE from forward
// (0 forward, 90 right, 180 back, 270 left); turns are COUNTERCLOCKWISE + (left); turnTo's 0 is the way
// the robot faced at the start (the IMU's heading, not the field's).
//
// A plan: { drive: 'tank' | 'mecanum', start: { x, y, h, note, spot (write x, y) },
//           steps: [step...], mirror (mirrorForRed()), opmode, teleop, className, pkg, group }
// A step:  { kind: 'move', label, inches, dir, power }      dir: clockwise from forward
//          { kind: 'turn', label, deg, power }               counterclockwise +
//          { kind: 'turnTo', label, heading, power }         0 = the start heading
//          { kind: 'wait', label, sec }
//          { kind: 'run', label, code }                      starts a command, goes on at once
//          { kind: 'action', code, timeout }                 runs an action from Actions.java and waits
//          { kind: 'aim', label, timeout }                   aimAtGoal: turns to the goal (camera / odometry)
//          { kind: 'ready', label, timeout }                 readyToLaunch: spins the launcher up, waits until ready
//          { kind: 'remember', label }                       rememberHeading: saves the heading now
//          { kind: 'back', label, power }                    turnBack: turns back to the saved heading
//          (aim: sweep = degrees each way to look for the goal; 0 or missing = no sweep)
//          { kind: 'alongside', code }                       starts an action, goes on at once
//          { kind: 'park', sec }                             parkWithSeconds: the steps after it get the last sec
import { wrap, num, round1, headingOf, footprint, checkHits, otherAlliance, pointInPoly, polysOverlap,
	javaString, tokenize, readArgs, evalNum } from './pathcore.js';

export const KINDS = ['move', 'turn', 'turnTo', 'wait', 'run', 'action', 'aim', 'ready', 'remember', 'back', 'alongside', 'park'];
export const AUTO_SEC = 30;
export const MOVE_TIMEOUT_SEC = 10;       // AutoDriveConfig.actionTimeoutSec: a move or turn longer than this ends TIMED_OUT
export const ACTION_TIMEOUT_SEC = 10;     // Steps.action(a) without a time limit
export const START_BOX_IN = 18;           // R102: the robot starts inside an 18 in cube
// Plain speed numbers for the time estimate (the page lets a team set its own).
export const DEFAULT_SPEEDS = { inPerSec: 40, degPerSec: 180, settleSec: 0.3 };

/** Names teams often give their commands and actions (the page's picker). Their own names go here. */
export const COMMON_COMMANDS = ['robot.claw::close', 'robot.claw::open', 'robot.intake::in', 'robot.intake::out', 'robot.intake::stop',
	'robot.launcher::spinUp', 'robot.launcher::stop', '() -> robot.lift.goTo("high")', '() -> robot.lift.goTo("low")',
	'() -> robot.arm.goTo("up")', '() -> robot.arm.goTo("down")'];
export const COMMON_ACTIONS = ['robot.launcher.fireAllAction()', 'robot.launcher.fireAction(1)', 'robot.launcher.ejectAction()',
	'Actions.scoreHigh(robot)', 'Actions.scoreLow(robot)', 'Actions.fireAll(robot)',
	'Actions.fireAllWithIntake(robot)', 'Actions.grab(robot)', 'Actions.stow(robot)'];

const clampPower = (p) => Math.max(0.1, Math.min(1, Number.isFinite(+p) ? +p : 0.5));   // AutoDrive clamps to 0.1..1

/** A move's direction as a field vector: dir clockwise from the robot's front, robot facing h. */
function moveVector(h, dir) {
	const r = h * Math.PI / 180, d = dir * Math.PI / 180;
	const f = [-Math.sin(r), Math.cos(r)], right = [Math.cos(r), Math.sin(r)];
	return [f[0] * Math.cos(d) + right[0] * Math.sin(d), f[1] * Math.cos(d) + right[1] * Math.sin(d)];
}

/** True if a move direction has a sideways part (a tank drive cannot do it). Like AutoDrive.move. */
export const sideways = (dir) => Math.abs(Math.sin(dir * Math.PI / 180)) > 1e-6;

/** The move directions a drive can do, for the page's picker: a tank drive only forward (0) and back (180). */
export const TANK_DIRS = [0, 180];

const MOVING_KINDS = ['move', 'turn', 'turnTo'];

/**
 * The steps for a tank drive: a tank drive cannot strafe, so every move with a sideways part becomes a
 * turn to face the way it went, the same move straight forward (or back, when that turns less), and a
 * turn back to the heading it had (left out when the next driving step is a turnTo, which sets the
 * heading anyway, or when no driving step follows). The robot drives the same route. A direction with no
 * sideways part is written 0 or 180 (360 -> 0, -180 -> 180). The steps given are not changed.
 * Returns { steps, changes: [text: what changed, step numbers as they were] }.
 */
export function tankSteps(steps) {
	const out = [], changes = [];
	(steps || []).forEach((s, i) => {
		if (s.kind !== 'move' || !Number.isFinite(+s.dir) || !Number.isFinite(+s.inches)) { out.push(s); return; }
		if (!sideways(+s.dir)) {
			const back = Math.cos(+s.dir * Math.PI / 180) < 0;
			out.push(+s.dir === (back ? 180 : 0) ? s : { ...s, dir: back ? 180 : 0 });
			return;
		}
		const inches = Math.abs(+s.inches);
		const way = +s.dir + (+s.inches < 0 ? 180 : 0);      // clockwise from the front
		let a = wrap(-way), dir = 0;                         // the turn: counterclockwise +
		if (Math.abs(a) > 90 + 1e-9) { a = wrap(a + 180); dir = 180; }
		a = Math.round(a * 10) / 10;
		const name = String(s.label ?? '').trim() || 'Drive';
		const power = 0.5;
		const turnWords = (d) => `turn ${num(Math.abs(d))}° ${d >= 0 ? 'left' : 'right'}`;
		out.push({ kind: 'turn', label: `${name} (turn)`, deg: a, power },
			{ kind: 'move', label: s.label, inches, dir, power: s.power });
		const next = (steps || []).slice(i + 1).find((x) => MOVING_KINDS.includes(x.kind));
		const turnBack = next && next.kind !== 'turnTo';
		if (turnBack) out.push({ kind: 'turn', label: `${name} (turn back)`, deg: -a, power });
		changes.push(`Step ${i + 1} (${name}): ${num(inches)} in at ${num(((way % 360) + 360) % 360)}° is a strafe, not possible on a tank drive. `
			+ `Now: ${turnWords(a)}, drive ${num(inches)} in ${dir ? 'back' : 'forward'}`
			+ (turnBack ? `, ${turnWords(-a)} back.` : next ? ' (the turnTo after it sets the heading).' : ' (it ends facing the way it drove).'));
	});
	return { steps: out, changes };
}

// ------------------------------------------------------------------ the map: drive there, drag a drive's end

const snapIn = (v) => Math.round(v / 0.5) * 0.5;   // the map's half inch

/**
 * The steps that drive the robot from pose p ({ x, y, h }) to (x, y) ("drive there" on the map): mecanum
 * one move in any direction; tank a turn (unless it is straight ahead or behind), then a move forward or
 * back, never a strafe. Both pages use it.
 */
export function stepsToPoint(drive, p, x, y) {
	const dx = x - p.x, dy = y - p.y, dist = snapIn(Math.hypot(dx, dy));
	if (dist < 0.5) return [];
	const a = wrap(headingOf(dx, dy) - p.h);   // counterclockwise from the robot's front
	const name = `To (${num(round1(x))}, ${num(round1(y))})`;
	if (drive === 'mecanum') return [{ kind: 'move', label: name, inches: dist, dir: ((Math.round(-a) % 360) + 360) % 360, power: 0.6 }];
	if (Math.abs(a) <= 1) return [{ kind: 'move', label: name, inches: dist, dir: 0, power: 0.6 }];
	if (Math.abs(a) >= 179) return [{ kind: 'move', label: name, inches: dist, dir: 180, power: 0.6 }];
	return [{ kind: 'turn', label: `Face (${num(round1(x))}, ${num(round1(y))})`, deg: Math.round(a), power: 0.5 },
		{ kind: 'move', label: name, inches: dist, dir: 0, power: 0.6 }];
}

/**
 * A drive's end dragged to (x, y), from pose p: its new { inches, dir }. Mecanum goes straight there (any
 * direction); a tank drive stays on the line through its front (forward or back only, never sideways).
 */
export function dragMove(drive, p, x, y) {
	if (drive === 'mecanum') {
		const [n] = stepsToPoint(drive, p, x, y);
		return n ? { inches: n.inches, dir: n.dir } : null;
	}
	const f = [-Math.sin(p.h * Math.PI / 180), Math.cos(p.h * Math.PI / 180)];
	const along = snapIn((x - p.x) * f[0] + (y - p.y) * f[1]);
	return { inches: Math.abs(along), dir: along < 0 ? 180 : 0 };
}

/** The heading a turn or turnTo ends at, and the signed degrees it turns (the short way, like AutoDrive). */
export function turnEnd(step, h, startH) {
	const target = step.kind === 'turn' ? wrap(h + (+step.deg || 0)) : wrap(startH + (+step.heading || 0));
	let d = wrap(target - h);
	return { target, d };
}

/** How long a step takes, seconds (a plan from the speed numbers; an action counts its whole time limit). */
export function stepSeconds(step, h, startH, speeds = DEFAULT_SPEEDS) {
	switch (step.kind) {
		case 'move': {
			const inches = Math.abs(+step.inches || 0);
			return inches < 1e-9 ? 0 : inches / (speeds.inPerSec * clampPower(step.power)) + speeds.settleSec;
		}
		case 'turn': case 'turnTo': {
			const { d } = turnEnd(step, h, startH);
			return Math.abs(d) < 1e-9 ? 0 : Math.abs(d) / (speeds.degPerSec * clampPower(step.power)) + speeds.settleSec;
		}
		case 'wait': return Math.max(0, +step.sec || 0);
		case 'action': return Math.max(0, Number.isFinite(+step.timeout) ? +step.timeout : ACTION_TIMEOUT_SEC);
		case 'aim': return Math.min(1, Math.max(0, +step.timeout || 0));   // a short turn when the goal is in view
		case 'ready': return Math.min(1.5, Math.max(0, +step.timeout || 0));   // a spin-up
		case 'remember': return 0;
		case 'back': return 1;   // a turn back (its size is only known on the robot)
		default: return 0;
	}
}

/**
 * Where the robot goes if every step does exactly what it says. Returns
 * { samples: [{ x, y, h, step, t }] (about one an inch or every 3 degrees; step -1 = the start),
 *   ends: [{ x, y, h, t0, t1 }] (the pose after each step and its start and end times),
 *   end: the last pose, total: seconds (START to the last step, with the start delay),
 *   parkAt: index of the park step or -1, parkSec }.
 */
export function simulate(plan, speeds = DEFAULT_SPEEDS, startDelay = 0) {
	const st = plan.start;
	let x = +st.x, y = +st.y, h = wrap(+st.h || 0);
	const startH = h;
	let t = Math.max(0, +startDelay || 0);
	const samples = [{ x, y, h, step: -1, t: 0 }];
	const ends = [];
	let parkAt = -1, parkSec = 0;
	(plan.steps || []).forEach((s, i) => {
		const t0 = t;
		const dt = stepSeconds(s, h, startH, speeds);
		if (s.kind === 'move') {
			const inches = +s.inches || 0;
			const v = moveVector(h, (+s.dir || 0) + (inches < 0 ? 180 : 0));
			const L = Math.abs(inches);
			const n = Math.max(1, Math.ceil(L));
			for (let k = 1; k <= n; k++) samples.push({ x: x + v[0] * L * k / n, y: y + v[1] * L * k / n, h, step: i, t: t0 + dt * k / n });
			x += v[0] * L; y += v[1] * L;
		} else if (s.kind === 'turn' || s.kind === 'turnTo') {
			const { d } = turnEnd(s, h, startH);
			const n = Math.max(1, Math.ceil(Math.abs(d) / 3));
			for (let k = 1; k <= n; k++) samples.push({ x, y, h: wrap(h + d * k / n), step: i, t: t0 + dt * k / n });
			h = wrap(h + d);
		} else {
			samples.push({ x, y, h, step: i, t: t0 + dt });
			if (s.kind === 'park') { parkAt = i; parkSec = Math.max(0, +s.sec || 0); }
		}
		t = t0 + dt;
		ends.push({ x, y, h, t0, t1: t });
	});
	return { samples, ends, end: { x, y, h }, total: t, parkAt, parkSec, startH };
}

/** The pose and step at a time (seconds from START) along the plan, for the page's slider. */
export function poseAt(sim, t) {
	const s = sim.samples;
	if (t <= s[0].t) return { ...s[0] };
	for (let k = 1; k < s.length; k++) {
		if (s[k].t >= t) {
			const a = s[k - 1], b = s[k];
			const f = b.t > a.t ? (t - a.t) / (b.t - a.t) : 1;
			return { x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f, h: wrap(a.h + wrap(b.h - a.h) * f), step: b.step, t };
		}
	}
	return { ...s[s.length - 1], t };
}

// ------------------------------------------------------------------ start spots

/** The neutral field file's zone the robot must not start in (BIOBUZZ G304.E), or null. */
function loadingZone(field) {
	return (field?.elements || []).find((e) => e.kind === 'zone' && /^Your LOADING ZONE/.test(e.name)) || null;
}

/**
 * What is wrong with a start spot: [{ level: 'bad' | 'warn', text }]. BIOBUZZ G304: touching the wall,
 * fully on your half, not in your LOADING ZONE, not touching a FLOWER; R102: inside the 18 in box.
 * obs and half: from pathcore.obstacles(field) and the inside wall size / 2.
 */
export function startChecks(start, robot, field, obs, half) {
	const out = [];
	const [L, W] = robot;
	if (L > START_BOX_IN + 1e-9 || W > START_BOX_IN + 1e-9) {
		out.push({ level: 'bad', text: `The robot is ${num(L)} x ${num(W)} in: it must start inside the 18 in box (R102). Fold it smaller for the start.` });
	}
	const fp = footprint(+start.x, +start.y, +start.h || 0, L, W);
	const hits = checkHits([{ x: +start.x, y: +start.y, h: +start.h || 0, part: 0 }], obs, half, L, W).hits.map((h) => h.name);
	for (const n of hits) {
		out.push({ level: 'bad', text: n === 'the wall'
			? 'The start spot is past the field wall: the simulator refuses it ("the start position overlaps the field wall"). Drag the green dot a little, or pick a Spot from the list.'
			: `The robot starts on ${n}: move it (the simulator refuses a start that overlaps a field part).` });
	}
	const gap = Math.min(...fp.map(([x, y]) => Math.min(half - Math.abs(x), half - Math.abs(y))));
	if (!hits.includes('the wall') && gap > 1) out.push({ level: 'bad', text: `The robot must touch the field wall at the start (G304); it is ${num(Math.round(gap * 10) / 10)} in away. Drag it to a wall (a corner or turned 45° is fine), or pick a Spot.` });
	if (fp.some(([, y]) => y > 1e-6)) out.push({ level: 'bad', text: 'The robot must start fully on your half of the field (G304.A): y below 0 for every corner.' });
	const lz = loadingZone(field);
	if (lz && polysOverlap(fp, lz.polygon)) out.push({ level: 'bad', text: 'The robot must not start in your LOADING ZONE (G304).' });
	return out;
}

/**
 * Ready-made start spots against your wall, facing the field, for this robot size: the corners, the
 * centre and right of your FLOWER, each moved to the nearest spot that passes startChecks.
 */
export function startPresets(robot, field, obs, half) {
	const [L, W] = robot;
	const y = Math.round((-half + L / 2 + 0.5) * 10) / 10;   // the back 0.5 in from the wall (like -61.1 for 18 in)
	const okAt = (x) => startChecks({ x, y, h: 0 }, [Math.min(L, 18), Math.min(W, 18)], field, obs, half)
		.filter((c) => c.level === 'bad').length === 0;
	const near = (want) => {
		for (let d = 0; d <= 80; d += 0.5) {
			for (const x of [want - d, want + d]) if (Math.abs(x) < half && okAt(x)) return x;
		}
		return null;
	};
	const list = [['Left corner', -56, 'Against our wall, in the left corner tile, facing the field'],
		['Centre', 0, 'Against our wall, in the middle, facing the field'],
		['Right of the FLOWER', 40, 'Against our wall, right of our FLOWER, facing the field'],
		['Right corner', 56, 'Against our wall, in the right corner tile, facing the field']];
	const out = [];
	for (const [name, want] of list) {
		const x = near(want);
		if (x != null && !out.some((p) => p.x === x)) out.push({ name, x, y, h: 0, note: autoNote({ x, y, h: 0 }, robot, half) });
	}
	return out;
}

/** Which wall a point is at, in the drivers' words (seen from our wall). */
export function wallName(x, y) {
	if (Math.abs(y) >= Math.abs(x)) return y < 0 ? 'our wall' : 'the far wall';
	return x < 0 ? 'the left wall' : 'the right wall';
}

/**
 * A dragged start spot made easy to copy at the field: the robot kept inside the walls, pushed back against a
 * wall that is near (3 in), and its centre or a side lined up with a tile middle or seam that is near (1.5 in).
 * Returns { x, y }. Snapping off: only kept inside.
 */
// The tiles' interlocking teeth reach about 0.4 in (10 mm) past each seam: the official field CAD's am-2499
// tiles (bounding boxes 9.6 to 11.6 mm past the 597.6 mm tile pitch). A drive team lines a robot's side up
// with the seam or with the teeth's edge.
export const TEETH_IN = 0.4;

export function snapStart(start, robot, half, on = true) {
	const [L, W] = robot, tile = 2 * half / 6, h = +start.h || 0;
	let x = +start.x, y = +start.y;
	const box = () => {
		const fp = footprint(x, y, h, L, W);
		return [Math.min(...fp.map((p) => p[0])), Math.max(...fp.map((p) => p[0])), Math.min(...fp.map((p) => p[1])), Math.max(...fp.map((p) => p[1]))];
	};
	const GAP = 0.5;   // like the presets: 0.5 in off the wall counts as touching it (G304)
	if (on) {
		// x: the centre on a tile middle or a seam, or a side on a seam
		const [x0, x1] = box(), cands = [];
		for (let j = 1; j < 12; j++) cands.push([-half + j * tile / 2, x]);
		for (let j = 1; j < 6; j++) {
			const s = -half + j * tile;
			for (const e of [s, s - TEETH_IN, s + TEETH_IN]) cands.push([e, x0], [e, x1]);
		}
		let best = null;
		for (const [line, at] of cands) { const d = line - at; if (Math.abs(d) <= 1.5 && (best == null || Math.abs(d) < Math.abs(best))) best = d; }
		if (best != null) x += best;
	}
	// inside the walls, then against a wall that is near
	let [x0, x1, y0, y1] = box();
	if (x0 < -half + GAP) x += -half + GAP - x0; else if (x1 > half - GAP) x -= x1 - (half - GAP);
	if (y0 < -half + GAP) y += -half + GAP - y0; else if (y1 > half - GAP) y -= y1 - (half - GAP);
	if (on) {
		[x0, x1, y0, y1] = box();
		if (y0 + half - GAP < 3) y -= y0 + half - GAP;
		if (x0 + half - GAP < 3) x -= x0 + half - GAP; else if (half - GAP - x1 < 3) x += half - GAP - x1;
	}
	// Against a side wall (not in a corner): line up along that wall too, like x along our wall.
	if (on) {
		[x0, x1, y0, y1] = box();
		const side = x0 + half - GAP < 0.6 || half - GAP - x1 < 0.6, ours = y0 + half - GAP < 0.6;
		if (side && !ours) {
			const cands = [];
			for (let j = 1; j < 12; j++) cands.push([-half + j * tile / 2, y]);
			for (let j = 1; j < 6; j++) {
				const sm = -half + j * tile;
				for (const e of [sm, sm - TEETH_IN, sm + TEETH_IN]) cands.push([e, y0], [e, y1]);
			}
			let best = null;
			for (const [line, at] of cands) { const d = line - at; if (Math.abs(d) <= 1.5 && (best == null || Math.abs(d) < Math.abs(best))) best = d; }
			if (best != null) y += best;
		}
	}
	// G304: the robot must touch the field wall. Not touching any: to the nearest of our wall and the side walls.
	[x0, x1, y0, y1] = box();
	const gaps = [[y0 + half - GAP, 'y', -1], [x0 + half - GAP, 'x', -1], [half - GAP - x1, 'x', 1]];
	if (gaps.every(([g]) => g > 0.05)) {
		const [g, axis, sign] = gaps.reduce((a, b) => (b[0] < a[0] ? b : a));
		if (axis === 'y') y -= g; else x += sign * g;
	}
	return { x: round1(x), y: round1(y) };
}

/** The spot measured the way a drive team checks it with a tape: the sides from the side walls, the back from our wall. */
export function startInches(start, robot, half) {
	const fp = footprint(+start.x, +start.y, +start.h || 0, robot[0], robot[1]);
	const x0 = Math.min(...fp.map((p) => p[0])), x1 = Math.max(...fp.map((p) => p[0])), y0 = Math.min(...fp.map((p) => p[1]));
	const d = (v, wall) => (v <= 0.6 ? `against ${wall}` : `${num(Math.round(v * 2) / 2)} in from ${wall}`);
	const y1 = Math.max(...fp.map((p) => p[1]));
	const side = (x0 + half <= 0.6 || half - x1 <= 0.6) && y0 + half > 0.6;
	return `Left side ${d(x0 + half, 'the left wall')}, right side ${d(half - x1, 'the right wall')}, back ${d(y0 + half, 'our wall')}.`
		+ (side ? tileWords(+start.y, y0, y1, half, ['Near', 'Far'], 'out from our wall') : tileWords(+start.x, x0, x1, half));
}

// Where the robot sits on the tiles, counted from the left as the drivers see them: " Centred on tile 3."
function tileWords(x, x0, x1, half, sides = ['Left', 'Right'], from = 'from the left') {
	const tile = 2 * half / 6, out = [];
	const k = (x + half) / tile;   // in tiles from the left wall
	if (Math.abs(k - Math.floor(k) - 0.5) < 0.02) out.push(`Centred on tile ${Math.floor(k) + 1} ${from}`);
	else if (Math.abs(k - Math.round(k)) < 0.02 && Math.round(k) > 0 && Math.round(k) < 6) out.push(`Centred on the seam between tiles ${Math.round(k)} and ${Math.round(k) + 1}`);
	for (const [v, side] of [[x0, sides[0]], [x1, sides[1]]]) {
		for (let j = 1; j < 6; j++) {
			const sm = -half + j * tile;
			if (Math.abs(v - sm) < 0.05) out.push(`${side} side on the seam ${j}|${j + 1}`);
			else if (Math.abs(Math.abs(v - sm) - TEETH_IN) < 0.05) out.push(`${side} side at the teeth's edge, seam ${j}|${j + 1}`);
		}
	}
	return out.length ? ' ' + out.join('. ') + '.' : '';
}

// ------------------------------------------------------------------ the note for the drive team

// FTC-Helper-Lib shows the note in INIT as one telemetry line, "Place robot : <note>" (EasyAuto:
// t.line("Place robot", where)). The library neither cuts nor wraps it; the Driver Station wraps a long
// line. About 50 characters of note fit one line beside "Place robot : " on a Driver Hub.
export const NOTE_MAX = 50;
const ORD = ['1st', '2nd', '3rd', '4th', '5th', '6th'];
const HIVE_YOURS = [0, -20];   // the middle of your HIVE frame (field.json, "HIVE frame base (your side)")

/** Which way the robot faces, in drivers' words: "facing the field", "facing the HIVE", "turned 45° left". */
export function facingWords(start) {
	const h = wrap(+start.h || 0);
	const hive = headingOf(HIVE_YOURS[0] - start.x, HIVE_YOURS[1] - start.y);
	if (Math.abs(h) < 0.5) return 'facing the field';
	if (Math.abs(Math.abs(h) - 180) < 0.5) return 'facing our wall';
	if (Math.abs(h - 90) < 0.5) return 'facing left';
	if (Math.abs(h + 90) < 0.5) return 'facing right';
	if (start.y < 0 && Math.abs(wrap(h - hive)) < 2) return 'facing the HIVE';
	if (Math.abs(h) < 90) return `turned ${num(Math.round(Math.abs(h)))}° ${h > 0 ? 'left' : 'right'}`;
	return `back turned ${num(Math.round(180 - Math.abs(h)))}° ${h > 0 ? 'left' : 'right'}`;
}

/**
 * A note for the drive team from the spot, in the words drivers use at the field: the wall it is
 * against, which tile (counted from the left, as the drivers see it), an edge on a tile seam, and
 * which way it faces. The longest version that fits NOTE_MAX characters.
 * half: the inside wall size / 2 (the tiles are 2 * half / 6 wide).
 */
export function autoNote(start, robot = [18, 18], half = 70.585) {
	const tile = 2 * half / 6;
	const fp = footprint(+start.x, +start.y, +start.h || 0, robot[0], robot[1]);
	const minX = Math.min(...fp.map((p) => p[0])), maxX = Math.max(...fp.map((p) => p[0]));
	const minY = Math.min(...fp.map((p) => p[1]));
	const near = (d) => d <= 1;
	const walls = [];
	if (near(minY + half)) walls.push('our wall');
	if (near(minX + half)) walls.push('the left wall');
	if (near(half - maxX)) walls.push('the right wall');
	const col = Math.max(0, Math.min(5, Math.floor((+start.x + half) / tile)));
	const row = Math.max(0, Math.min(5, Math.floor((+start.y + half) / tile)));
	// The robot's centre on a seam (within 1 in) reads better than half of one tile.
	const k = Math.round((+start.x + half) / tile);
	const onSeam = k > 0 && k < 6 && Math.abs(+start.x + half - k * tile) <= 1;
	let where;
	if (onSeam) where = k === 3 ? 'on the middle seam' : `on the ${ORD[k < 3 ? k - 1 : 5 - k]} seam from the ${k < 3 ? 'left' : 'right'}`;
	else if (col === 0 || col === 5) where = row === 0 ? `${col === 0 ? 'left' : 'right'} corner tile` : `${col === 0 ? 'left' : 'right'} column`;
	else where = col < 3 ? `${ORD[col]} tile from the left` : `${ORD[5 - col]} tile from the right`;
	if (row > 0) {
		// Against a side wall, the column is plain: say only how far out.
		const sideWall = (col === 0 && walls.includes('the left wall')) || (col === 5 && walls.includes('the right wall'));
		where = sideWall && !onSeam ? `${ORD[row]} tile out` : `${where}, ${ORD[row]} tile out`;
	}
	// Where in the tile, along the wall it is against (along our wall, or up a side wall): centred, an edge on
	// the seam, or an edge at the teeth's edge. What the drive team lines up by eye.
	const maxY = Math.max(...fp.map((p) => p[1]));
	const alongSide = !walls.includes('our wall') && (walls.includes('the left wall') || walls.includes('the right wall'));
	const [c, lo, hi, loName, hiName] = alongSide ? [+start.y, minY, maxY, 'near', 'far'] : [+start.x, minX, maxX, 'left', 'right'];
	const off = (v) => { const j = Math.round((v + half) / tile); return j > 0 && j < 6 ? Math.abs(v + half - j * tile) : 99; };
	const mid = Math.abs((c + half) / tile - Math.floor((c + half) / tile) - 0.5) * tile;
	const centreOnSeam = alongSide ? off(c) <= 1 : onSeam;
	const edge = centreOnSeam ? '' : mid <= 0.5 ? 'centred'
		: off(lo) <= 0.25 ? `${loName} edge on seam` : off(hi) <= 0.25 ? `${hiName} edge on seam`
		: Math.abs(off(lo) - TEETH_IN) <= 0.25 ? `${loName} edge at teeth` : Math.abs(off(hi) - TEETH_IN) <= 0.25 ? `${hiName} edge at teeth` : '';
	const wallText = walls.length ? `Against ${walls.join(' and ')}` : `${num(Math.round(minY + half))} in off our wall`;
	const face = facingWords(start);
	const short = (w) => w.replace(/ from the (left|right)/, ' from $1');
	const shorter = (w) => short(w).replace(/^(\d)(st|nd|rd|th) tile from/, 'tile $1 from');
	const face2 = face.replace('facing the field', 'facing field');
	const bare = wallText.replace(/^Against /, ''), bare2 = bare.replace(/^the /, '');
	// The place in the tile is kept as long as anything is: it is what the drive team lines up. The wall goes
	// last (our wall is the usual one).
	const tries = [
		[wallText, where, edge, face], [bare, where, edge, face], [bare, short(where), edge, face], [bare2, shorter(where), edge, face],
		[bare2, shorter(where), edge, face2], [shorter(where), edge, face2],
		[bare, short(where), face], [short(where), face]];
	for (const t of tries) {
		const text = t.filter(Boolean).join(', ');
		const out = text[0].toUpperCase() + text.slice(1);
		// A few characters over (the Driver Station wraps them) before the wall or the place is dropped.
		if (out.length <= NOTE_MAX || (t === tries[4] && out.length <= NOTE_MAX + 6)) return out;
	}
	const t = tries[tries.length - 1].join(', ');
	return t[0].toUpperCase() + t.slice(1);
}

/** One step in plain words, for the setup card ("Drive 24 in forward", "Turn 45° right"). */
export function stepWords(s) {
	const dirWord = (d) => ({ 0: 'forward', 180: 'back', 90: 'right', 270: 'left' }[((+d % 360) + 360) % 360] ?? `at ${num(+d)}° (clockwise from forward)`);
	switch (s.kind) {
		case 'move': return `${s.label}: drive ${num(Math.abs(+s.inches))} in ${dirWord((+s.dir || 0) + (+s.inches < 0 ? 180 : 0))}`;
		case 'turn': return `${s.label}: turn ${num(Math.abs(+s.deg))}° ${+s.deg >= 0 ? 'left' : 'right'}`;
		case 'turnTo': return `${s.label}: turn to ${num(+s.heading)}° (0 = the start heading)`;
		case 'wait': return `${s.label}: wait ${num(+s.sec)} s`;
		case 'run': return `${s.label}: start ${s.code}`;
		case 'action': return `${s.code}, up to ${num(+s.timeout)} s`;
		case 'aim': return `${s.label}: aim at the goal (up to ${num(+s.timeout)} s${+s.sweep > 0 ? `, sweep ${num(+s.sweep)} deg each way` : ''})`;
		case 'remember': return `${s.label}: remember the heading`;
		case 'back': return `${s.label}: turn back to the remembered heading`;
		case 'ready': return `${s.label}: ready to launch (up to ${num(+s.timeout)} s)`;
		case 'alongside': return `${s.code}, while the next steps run`;
		case 'park': return `Park: the last ${num(+s.sec)} s go to the steps after it`;
		default: return s.kind;
	}
}

// ------------------------------------------------------------------ warnings

const stepName = (s) => (s.kind === 'action' || s.kind === 'alongside' ? s.code : s.kind === 'park' ? 'park' : s.label) || '(no name)';
export { stepName };

/**
 * Everything the page warns about: [{ step (index or -1 = the whole plan), level: 'bad' | 'warn' | 'good', text }].
 * bad = the library refuses it, or it cannot work; warn = it works, but not as one would think.
 * opts: { field, obs, half, robot: [L, W], speeds, startDelay, showOther }.
 */
export function checkPlan(plan, opts = {}) {
	const out = [];
	const speeds = opts.speeds || DEFAULT_SPEEDS;
	const sim = simulate(plan, speeds, opts.startDelay);
	const robot = opts.robot || [18, 18];
	const add = (step, level, text) => out.push({ step, level, text });
	const steps = plan.steps || [];
	let parks = 0;
	steps.forEach((s, i) => {
		const n = `Step ${i + 1}`;
		if (!KINDS.includes(s.kind)) { add(i, 'bad', `${n}: there is no such step.`); return; }
		if ((s.kind === 'move' || s.kind === 'turn' || s.kind === 'turnTo' || s.kind === 'wait' || s.kind === 'run' || s.kind === 'aim' || s.kind === 'ready' || s.kind === 'remember' || s.kind === 'back') && !String(s.label ?? '').trim()) {
			add(i, 'bad', `${n}: give it a name for the report, e.g. "To the goal".`);
		}
		if (s.kind === 'move') {
			if (!Number.isFinite(+s.inches) || !Number.isFinite(+s.dir)) add(i, 'bad', `${n}: give the inches and the direction as numbers.`);
			else {
				if (plan.drive === 'tank' && sideways(+s.dir)) {
					add(i, 'bad', `${n} (${stepName(s)}): a strafe, not possible on a tank drive: it can only move at 0 (forward) or 180 (back), not ${num(+s.dir)}. Turn first, then move (the robot stops with "Turn first, then move").`);
				}
				if (Math.abs(+s.inches) < 0.05) add(i, 'warn', `${n} (${stepName(s)}): 0 inches, so it does nothing.`);
			}
		}
		if (s.kind === 'move' || s.kind === 'turn' || s.kind === 'turnTo') {
			if (!(+s.power > 0 && +s.power <= 1)) add(i, 'warn', `${n} (${stepName(s)}): power ${num(+s.power)} is held to 0.1..1 (${num(clampPower(s.power))}).`);
			const dt = sim.ends[i].t1 - sim.ends[i].t0;
			if (dt > MOVE_TIMEOUT_SEC) add(i, 'warn', `${n} (${stepName(s)}): about ${num(Math.round(dt * 10) / 10)} s, but a move or turn stops after ${MOVE_TIMEOUT_SEC} s (the report says TIMED_OUT). Split it, or use more power.`);
		}
		// A turn or a new direction right after a drive: the robot still rolls when the drive ends
		// (it slows only to moveMinPower), so it slides on. In FTC-Helper-Lib's simulator a mecanum robot
		// slid about 5 in this way (a turn or strafe right after a 24 in drive at 0.6); a 0.3 s wait removed it.
		const prev = steps[i - 1];
		if (prev?.kind === 'move' && Math.abs(+prev.inches) >= 0.5 && Number.isFinite(+prev.dir)) {
			const way = (st) => (+st.dir || 0) + (+st.inches < 0 ? 180 : 0);
			const turns = s.kind === 'turn' || s.kind === 'turnTo';
			const newWay = s.kind === 'move' && Math.abs(+s.inches) >= 0.5 && Math.abs(wrap(way(s) - way(prev))) > 10;
			if (turns || newWay) {
				add(i, 'warn', `${n} (${stepName(s)}): it starts while the robot still rolls from the drive before it, so it slides on a few inches (about 5 in the simulator). A short wait before it (0.3 s) makes it more exact.`);
			}
		}
		if (s.kind === 'turn' && Math.abs(+s.deg) > 180) {
			const { d } = turnEnd(s, 0, 0);
			add(i, 'warn', `${n} (${stepName(s)}): ${num(+s.deg)} degrees ends at the same heading as ${num(d)}, and the robot turns the short way (${num(d)}).`);
		}
		if (s.kind === 'wait' && !(+s.sec >= 0)) add(i, 'bad', `${n}: give the seconds to wait (0 or more).`);
		if ((s.kind === 'run' || s.kind === 'action' || s.kind === 'alongside') && !String(s.code ?? '').trim()) {
			add(i, 'bad', `${n}: give something to do, e.g. ${s.kind === 'run' ? 'robot.claw::close' : 'Actions.scoreHigh(robot)'}.`);
		}
		if ((s.kind === 'action' || s.kind === 'aim' || s.kind === 'ready') && !(+s.timeout > 0)) add(i, 'bad', `${n}: give a time limit in seconds (more than 0).`);
		if (s.kind === 'aim' && s.sweep != null && !(+s.sweep >= 0 && +s.sweep <= 180)) add(i, 'bad', `${n}: the sweep is 0 to 180 degrees each way (0 = none).`);
		if (s.kind === 'back' && !(+s.power > 0 && +s.power <= 1)) add(i, 'bad', `${n}: give a power from 0.1 to 1.`);
		if (s.kind === 'park') {
			parks++;
			if (!(+s.sec > 0)) add(i, 'bad', `${n}: give the seconds the park steps need (more than 0).`);
			if (parks === 2) add(i, 'warn', `${n}: a second park: only the last parkWithSeconds sets the time. Use one.`);
		}
	});
	// The note the drive team reads at match setup.
	const note = String(plan.start?.note ?? '').trim();
	if (!note) add(-1, 'bad', 'Write a note for the drive team: it is shown in INIT as "Place robot : ..." so they place the robot right.');
	else if (note.length > NOTE_MAX) add(-1, 'warn', `The note for the drive team is ${note.length} characters: about ${NOTE_MAX} fit one Driver Station line ("Place robot : ..."); a longer one wraps.`);
	// Time: the 30 s timer, and the park.
	const T = sim.total;
	if (sim.parkAt >= 0) {
		const main = sim.ends[sim.parkAt].t1;   // when the steps before the park end
		const cut = AUTO_SEC - sim.parkSec;
		if (main > cut + 1e-9) add(-1, 'warn', `The steps before the park take until about ${num(Math.round(main * 10) / 10)} s, but the park starts at ${num(cut)} s (30 - ${num(sim.parkSec)}): the last of them are cut short or skipped.`);
		const parkTime = T - main;
		if (parkTime > sim.parkSec + 1e-9) add(-1, 'warn', `The park steps take about ${num(Math.round(parkTime * 10) / 10)} s, but parkWithSeconds keeps only ${num(sim.parkSec)} s for them.`);
	}
	if (T > AUTO_SEC + 1e-9) add(-1, 'bad', `About ${num(Math.round(T * 10) / 10)} s: more than the 30 s autonomous. The timer stops the robot before the end.`);
	else if (T > AUTO_SEC - 2 + 1e-9) add(-1, 'warn', `About ${num(Math.round(T * 10) / 10)} s: keep it under about 28 s, real robots are slower than a plan.`);
	if (sim.parkAt < 0 && steps.length > 2) add(-1, 'warn', 'No park step: add one (parkWithSeconds) before the steps that park, so a slow step cannot eat their time.');
	// The field: walls, field parts, the other alliance's half (G402), and the start spot.
	if (opts.obs) {
		const half = opts.half ?? 70.585;
		const startList = startChecks(plan.start, robot, opts.field, opts.obs, half);
		for (const c of startList) add(-1, c.level, c.text);
		const startOut = startList.some((c) => /past the field wall/.test(c.text));
		const moving = sim.samples.filter((p) => p.step >= 0);
		const mine = checkHits(moving.map((p) => ({ ...p, part: p.step })), opts.obs, half, robot[0], robot[1]);
		for (const h of mine.hits) {
			add(h.part, 'bad', h.name === 'the wall'
				? `Step ${h.part + 1} (${stepName(steps[h.part])}): the robot leaves the field (it hits ${wallName(h.x, h.y)} at (${num(Math.round(h.x))}, ${num(Math.round(h.y))}))${startOut ? '. It starts past the wall: move the start spot first' : ''}.`
				: `Step ${h.part + 1} (${stepName(steps[h.part])}): the robot hits ${h.name} near (${num(Math.round(h.x))}, ${num(Math.round(h.y))}).`);
		}
		const crossed = new Set();
		for (const p of moving) {
			if (crossed.has(p.step)) continue;
			if (footprint(p.x, p.y, p.h, robot[0], robot[1]).some(([, y]) => y > 0.05)) {
				crossed.add(p.step);
				add(p.step, 'warn', `Step ${p.step + 1} (${stepName(steps[p.step])}): the robot crosses into the other alliance's half. During autonomous that is risky (G402: a MAJOR FOUL if it gets in their way).`);
			}
		}
		if (opts.showOther) {
			const theirs = checkHits(moving.map((p) => ({ ...otherAlliance(p, !!plan.mirror), part: p.step })), opts.obs, half, robot[0], robot[1]);
			for (const h of theirs.hits) add(h.part, 'bad', `Other alliance, step ${h.part + 1}: their robot ${h.name === 'the wall' ? 'leaves the field' : 'hits ' + h.name}.`);
		}
	}
	return { list: out, sim };
}

// ------------------------------------------------------------------ Java out

/** A Java class name from the OpMode name: "Score and Park" -> ScoreAndPark. */
export function className(name) {
	const c = String(name || '').replace(/[^A-Za-z0-9 ]/g, ' ').trim().split(/\s+/).filter(Boolean)
		.map((w) => w[0].toUpperCase() + w.slice(1)).join('');
	return c ? (/^[0-9]/.test(c) ? 'Auto' + c : c) : 'MyAuto';
}

/** One step as Java (no leading dot). */
export function stepCode(s) {
	switch (s.kind) {
		case 'move': return `move(${javaString(s.label)}, ${val(s, 'inches')}, ${val(s, 'dir')}, ${val(s, 'power')})`;
		case 'turn': return `turn(${javaString(s.label)}, ${val(s, 'deg')}, ${val(s, 'power')})`;
		case 'turnTo': return `turnTo(${javaString(s.label)}, ${val(s, 'heading')}, ${val(s, 'power')})`;
		case 'wait': return `waitSeconds(${javaString(s.label)}, ${val(s, 'sec')})`;
		case 'run': return `run(${javaString(s.label)}, ${String(s.code ?? '').trim() || 'null'})`;
		case 'action': return `action(${String(s.code ?? '').trim() || 'null'}, ${val(s, 'timeout')})`;
		case 'aim': return +s.sweep > 0 ? `aimAtGoal(${javaString(s.label)}, ${val(s, 'timeout')}, ${val(s, 'sweep')})` : `aimAtGoal(${javaString(s.label)}, ${val(s, 'timeout')})`;
		case 'remember': return `rememberHeading(${javaString(s.label)})`;
		case 'back': return `turnBack(${javaString(s.label)}, ${val(s, 'power')})`;
		case 'ready': return `readyToLaunch(${javaString(s.label)}, ${val(s, 'timeout')})`;
		case 'alongside': return `alongside(${String(s.code ?? '').trim() || 'null'})`;
		case 'park': return `parkWithSeconds(${val(s, 'sec')})`;
		default: return `/* ${s.kind}? */`;
	}
}

/** The start line: start(note, x, y, heading), or start(note, heading) without the spot. */
// A number as Java: the name it was loaded through (Tunables.Auto.parkSeconds, START_X) while it is unchanged.
function val(o, k) {
	const n = o.names?.[k];
	return n && Math.abs(+n.v - +o[k]) < 1e-9 ? n.code : num(+o[k] || 0);
}

function startCode(st) {
	const note = javaString(st.note || 'Place the robot here');
	return st.spot === false ? `start(${note}, ${val(st, 'h')})` : `start(${note}, ${val(st, 'x')}, ${val(st, 'y')}, ${val(st, 'h')})`;
}

/**
 * The Java. Returns { steps (the chain, starting "s.start(...)"), file (the whole Auto file, in the
 * lesson's style) }. The steps in the file are indented 8 spaces, the dots under the "s".
 */
export function toJava(plan) {
	const lines = ['s.' + startCode(plan.start), ...(plan.steps || []).map((s) => ' .' + stepCode(s))];
	// the setup lines read from your auto (robot.deployAtStart = false;) come before the steps, as in your file
	const steps = [...(plan.setup || []).map((s) => s.code), lines.join('\n') + ';'].join('\n');
	const codes = (plan.steps || []).map((s) => String(s.code ?? '') + ' ' + Object.values(s.names || {}).map((n) => n.code).join(' ')).join(' ')
		+ ' ' + Object.values(plan.start?.names || {}).map((n) => n.code).join(' ');
	const base = (plan.pkg || 'org.firstinspires.ftc.teamcode').replace(/\.autos$/, '');
	const name = plan.opmode || 'My Auto';
	const cls = plan.className || className(name);
	const imports = [];
	if (/\bActions\./.test(codes)) imports.push(`import ${base}.Actions;`);
	imports.push(`import ${base}.Robot;`);
	if (/\bTunables\./.test(codes)) imports.push(`import ${base}.Tunables;`);
	imports.push('import com.crossbotics.ftchelper.robot.Auto;', 'import com.crossbotics.ftchelper.robot.Steps;',
		'import com.qualcomm.robotcore.eventloop.opmode.Autonomous;');
	const mirror = plan.mirror ? [] : ['',
		'    @Override',
		'    protected boolean mirrorForRed() {',
		'        return false;   // BIOBUZZ\'s field is turned half a turn between the alliances',
		'    }'];
	const file = [`package ${base}.autos;`, '', ...imports, '',
		plan.teleop ? '// preselectTeleOp: your TeleOp\'s name, exactly as in @TeleOp(name = ...) in Controls.java'
			: '// Add preselectTeleOp = "your TeleOp\'s name" (as in @TeleOp(name = ...) in Controls.java) to choose it after this auto',
		`@Autonomous(name = ${javaString(name)}, group = ${javaString(plan.group || 'Autos')}${plan.teleop ? `, preselectTeleOp = ${javaString(plan.teleop)}` : ''})`,
		`public class ${cls} extends Auto<Robot> {`,
		...mirror,
		'',
		'    @Override',
		'    protected void steps(Steps s, Robot robot) {',
		...steps.split('\n').map((l) => '        ' + l),
		'    }',
		'}', ''].join('\n');
	return { steps, file };
}

// ------------------------------------------------------------------ the simulator scenario

const simString = (s) => '"' + String(s).replace(/"/g, "'") + '"';

/**
 * A scenario file (.sim) for FTC-Helper-Lib's simulator: INIT the autonomous as RED (the simulator's
 * field numbers are seen from the red wall), START, wait for the end, and check every step (by its number
 * in the autonomous report: moves, turns, waits, commands and actions alike) says "done", the robot ends
 * within tolIn inches (and tolDeg degrees) of the planned spot, and no error. The robot is put on the
 * start spot only when the code gives it (start(note, x, y, heading)). A plan that leaves the field gets
 * no end checks (the real robot stops at the wall): a comment says so.
 * opts: tolIn, tolDeg, fileName, speeds, madeBy (who made it, for the first lines), half (the inside wall
 * size / 2) and robot ([length, width] in inches), for the leaves-the-field test.
 */
export function toScenario(plan, opts = {}) {
	const tolIn = opts.tolIn ?? 4, tolDeg = opts.tolDeg ?? 10;
	const file = opts.fileName || 'my-auto.sim';
	const sim = simulate(plan, opts.speeds || DEFAULT_SPEEDS);
	// RED drives the same numbers on a turned field (mirrorForRed() false), mirrored ones on a mirrored field.
	const e = plan.mirror ? { x: -sim.end.x, y: sim.end.y, h: wrap(-sim.end.h) } : sim.end;
	const r1 = (v) => num(Math.round(v * 10) / 10);
	const out = [
		`# ${plan.opmode || 'My Auto'}: does the robot end where the plan says?`,
		`# Made by ${opts.madeBy || 'the crossbotics.com path builder'} (Simple steps). The plan is dead reckoning, so a`,
		`# real robot (and the simulator's) ends near it, not on it: within ${num(tolIn)} in and ${num(tolDeg)} degrees here.`,
		`# Save it as simulator/scenarios/${file} in your robot project, then from the ftc-helper-lib folder:`,
		`#   ./gradlew :sim:scenario -Pfile=../simulator/scenarios/${file}`,
		'# The simulator\'s field numbers are seen from the RED wall. ' + (plan.mirror
			? 'With mirrorForRed() true, RED drives the route mirrored: the numbers below are mirrored.'
			: 'With mirrorForRed() false (BIOBUZZ), RED drives the numbers as written.'),
		`init ${simString(plan.opmode || 'My Auto')}`,
		'press g1.b                             # RED',
	];
	// The note the drive team reads at match setup (EasyAuto shows it in INIT as "Place robot : <note>").
	const note = String(plan.start.note || 'Place the robot here').split('"')[0].trimEnd();
	if (note.length >= 3) out.push(`expect screen has ${simString('Place robot : ' + note)}   # the note at INIT`);
	if (plan.start.spot !== false) {
		const sx = plan.mirror ? -plan.start.x : plan.start.x;
		out.push(`expect robot x = ${r1(sx)} 0.5                # on the start spot`, `expect robot y = ${r1(plan.start.y)} 0.5`);
	} else {
		const sh = plan.mirror ? wrap(-plan.start.h) : wrap(+plan.start.h || 0);
		out.push(`set pose ${r1(plan.mirror ? -plan.start.x : plan.start.x)} ${r1(plan.start.y)} ${r1(sh)}   # the code gives no spot: put the robot there`);
	}
	out.push('start', `expect within ${AUTO_SEC + 2} screen has "Autonomous finished"`);
	// Every step but "park" has a line in the report, in order: check each by its number.
	let n = 0;
	for (const s of plan.steps || []) {
		if (s.kind === 'park') continue;
		n++;
		const what = String(stepName(s)).replace(/[\r\n#]/g, ' ').trim();
		// (the first waits a moment: the report is kept just after "Autonomous finished" shows)
		out.push(`expect ${n === 1 ? 'within 2 ' : ''}step ${n} done`.padEnd(39) + '# ' + what);
	}
	// Off the field (dead reckoning through a wall): the real robot stops at the wall, so no end to check.
	const half = opts.half ?? 70.585, rb = opts.robot || [18, 18];
	// (the start spot counts as inside: a robot smaller than 18 in starts nearer the wall)
	const inside = Math.max(half - Math.min(rb[0], rb[1]) / 2, Math.abs(+plan.start.x || 0), Math.abs(+plan.start.y || 0));
	const off = sim.ends.some((p) => Math.abs(p.x) > inside + 1e-6 || Math.abs(p.y) > inside + 1e-6);
	if (off) {
		out.push('# No end checks: the plan drives the robot off the field (see the builder\'s checks). Fix the plan first.');
	} else {
		out.push(`expect robot x = ${r1(e.x)} ${num(tolIn)}`, `expect robot y = ${r1(e.y)} ${num(tolIn)}`);
		if (Math.abs(e.h) <= 180 - tolDeg - 5) out.push(`expect robot heading = ${r1(e.h)} ${num(tolDeg)}`);
		else out.push(`# heading: about ${r1(e.h)} (near 180, where the number jumps to -180): check it on the map`);
	}
	out.push('expect error none', '');
	return out.join('\n');
}

// ------------------------------------------------------------------ Java in

const STEP_METHODS = { move: 4, turn: 3, turnTo: 3, waitSeconds: 2, run: 2, action: null, aimAtGoal: null, readyToLaunch: 2, rememberHeading: 1, turnBack: 2, alongside: 1, parkWithSeconds: 1 };
const OTHER_METHODS = ['menuChoice', 'menuYesNo', 'onlyIf', 'end', 'driveTo', 'follow', 'step', 'waitUntil', 'moveWithTurn'];

/**
 * Reads an Auto file, or only its steps, and gives back what it could:
 * { plan: { start, steps, mirror?, opmode?, teleop?, className?, group?, pkg? } | null,
 *   drive: 'mecanum' | null (mecanum if a move goes sideways), problems: [text], notes: [text],
 *   tank: [text] (opts.drive 'tank' only: each strafe, not possible on a tank drive, and the turn and
 *   drive it became, from tankSteps; drive is then null) }.
 * Plain numbers only, like the Pedro importer: a step with a name in place of a number
 * (Tunables.Auto.parkInches) is left out, and the problem says so.
 */
// What else is in the auto, beside the steps: setup lines in steps() (robot.deployAtStart = false;), which
// change what the robot does. A plain one is kept and run; the rest are listed, never dropped silently.
function readAround(src, tk) {
	const setup = [], skipped = [], overrides = [];
	let comments = 0;
	const text = (a, b) => tk.src.slice(tk[a].at, tk[b].end).replace(/\s*\n\s*/g, ' ').trim();
	const short = (s) => (s.length > 70 ? s.slice(0, 67) + '...' : s);
	for (let i = 0; i + 1 < tk.length; i++) {
		// @Override protected void atInit(Robot robot): every override but the two the builder writes
		if (tk[i].p === '@' && tk[i + 1].id === 'Override') {
			let k = i + 2;
			while (k + 1 < tk.length && !(tk[k].id && tk[k + 1].p === '(')) k++;
			if (tk[k] && !['steps', 'mirrorForRed'].includes(tk[k].id)) overrides.push(tk[k].id);
		}
	}
	const at = tk.findIndex((t, i) => t.id === 'void' && tk[i + 1]?.id === 'steps' && tk[i + 2]?.p === '(');
	if (at < 0) return { setup, skipped, overrides, comments };
	let open = at + 3;
	while (open < tk.length && tk[open].p !== '{') open++;
	let depth = 0, from = open + 1, close = -1;
	for (let i = open; i < tk.length; i++) {
		const p = tk[i].p;
		if (p === '{' || p === '(') depth++;
		else if (p === '}' || p === ')') depth--;
		if (depth === 0) { close = i; break; }
		const ended = depth === 1 && (p === ';' || p === '}');
		if (!ended || i < from) continue;
		const st = tk.slice(from, i + 1);
		const list = /^(s|steps)$/.test(st[0].id ?? '') && st[1]?.p === '.';
		const passesList = st.some((t, k) => /^(s|steps)$/.test(t.id ?? '') && (st[k - 1]?.p === '(' || st[k - 1]?.p === ',') && (st[k + 1]?.p === ',' || st[k + 1]?.p === ')'));
		const value = st[3]?.p === '=' && st[0].id === 'robot' && st[1].p === '.' && st[2].id && st.length <= 7
			? (st[4].id === 'true' || st[4].id === 'false') && st[5]?.p === ';' ? st[4].id === 'true'
				: st[4].n != null && st[5]?.p === ';' ? st[4].n
					: st[4].p === '-' && st[5]?.n != null && st[6]?.p === ';' ? -st[5].n : null
			: null;
		if (value !== null) {
			const eol = src.indexOf('\n', tk[i].end);
			const rest = src.slice(tk[i].end, eol < 0 ? src.length : eol).trim();
			setup.push({ field: st[2].id, value, code: text(from, i) + (rest.startsWith('//') ? '   ' + rest : '') });
		} else if (!list && !passesList) skipped.push(short(text(from, i)));
		from = i + 1;
	}
	if (close > open) {
		const kept = new Set(setup.map((s) => s.code.split('   //')[1]).filter(Boolean));
		for (let i = open; i < close; i++) {
			const gap = src.slice(tk[i].end, tk[i + 1].at);
			for (const c of gap.match(/\/\/[^\n]*|\/\*[\s\S]*?\*\//g) || []) if (!kept.has(c.slice(2).trimEnd()) && !kept.has(c.slice(2).trim())) comments++;
		}
	}
	return { setup, skipped, overrides, comments };
}

export function fromJava(src, opts = {}) {
	const tk = tokenize(src);
	const problems = [], notes = [];
	const steps = [];
	let start = null, mirror, opmode, teleop, group, cls, pkg;
	const str = (arg) => (arg?.length === 1 && arg[0].s != null ? arg[0].s : null);
	const text = (toks) => (toks?.length ? tk.src.slice(toks[0].at, toks[toks.length - 1].end).replace(/\s*\n\s*/g, ' ').trim() : '');
	// opts.values: numbers for names and formulas the code has ({"Tunables.Auto.parkSeconds": 3}), from the
	// simulator. A step read through one keeps the name: the Java written back says it again (see val()).
	const values = opts.values || {};
	const unread = new Set();
	const nums = (args, what) => {
		const names = [];
		const vals = args.map((a, i) => {
			const r = evalNum(a);
			if (r.bad == null && !r.pose) return r;
			const t = text(a);
			if (Number.isFinite(values[t])) { names[i] = t; return { v: values[t] }; }
			unread.add(t);
			return r;
		});
		const bad = vals.find((v) => v.bad != null || v.pose);
		if (bad) { problems.push(`${what}: could not read "${bad.bad ?? 'new FieldPose(...)'}". Use plain numbers. This step was left out.`); return null; }
		const out = vals.map((v) => v.v);
		out.names = names;
		return out;
	};
	// The names a step was read through: { inches: { code: "Tunables.Auto.offWallInches", v: 6 } }.
	// Only Tunables names are kept: they work in any file. The auto's own constants and methods (START_X,
	// turnToField(...)) are not in the builder's file, so their number is written.
	const own = new Set();
	const named = (obj, keys, v) => {
		keys.forEach((k, i) => {
			const c = v.names[i];
			if (!c) return;
			if (/[A-Za-z_$]/.test(c.replace(/\bTunables(\s*\.\s*[A-Za-z_$][\w$]*)+/g, ''))) { own.add(c); return; }
			(obj.names ??= {})[k] = { code: c, v: obj[k] };
		});
		return obj;
	};
	const others = new Set();
	for (let i = 0; i < tk.length; i++) {
		const t = tk[i];
		// package a.b.c;
		if (t.id === 'package' && i === 0) {
			let k = i + 1, name = '';
			while (k < tk.length && tk[k].p !== ';') name += tk[k].id ?? tk[k].p ?? '', k++;
			pkg = name; i = k; continue;
		}
		// @Autonomous(name = "...", group = "...", preselectTeleOp = "...")
		if (t.p === '@' && tk[i + 1]?.id === 'Autonomous' && tk[i + 2]?.p === '(') {
			const { args, next } = readArgs(tk, i + 2);
			for (const a of args) {
				if (a[0]?.id && a[1]?.p === '=' && a.length === 3 && a[2].s != null) {
					if (a[0].id === 'name') opmode = a[2].s;
					else if (a[0].id === 'preselectTeleOp') teleop = a[2].s;
					else if (a[0].id === 'group') group = a[2].s;
				}
			}
			i = next - 1; continue;
		}
		// class NAME extends Auto<...>
		if (t.id === 'class' && tk[i + 1]?.id && tk[i + 2]?.id === 'extends') {
			cls = tk[i + 1].id;
			if (tk[i + 3]?.id !== 'Auto') notes.push(`${cls} extends ${tk[i + 3]?.id ?? '?'}, not Auto<Robot>: the steps were read anyway.`);
			continue;
		}
		// mirrorForRed() { return false; }
		if (t.id === 'mirrorForRed' && tk[i + 1]?.p === '(' && tk[i + 2]?.p === ')' && tk[i + 3]?.p === '{' && tk[i + 4]?.id === 'return') {
			if (tk[i + 5]?.id === 'false' || tk[i + 5]?.id === 'true') mirror = tk[i + 5].id === 'true';
			continue;
		}
		if (t.p !== '.' || !tk[i + 1]?.id || tk[i + 2]?.p !== '(') continue;
		const m = tk[i + 1].id;
		const before = tk[i - 1];
		// moveWithTurn (AutoRoutine's, on any list): never read silently.
		if (m === 'moveWithTurn') { others.add(m); i = readArgs(tk, i + 2).next - 1; continue; }
		// Only the step list's own calls: s.x(...), steps.x(...), or chained after one: ...).x(...)
		const onList = before && (before.p === ')' || /^(s|steps)$/.test(before.id ?? '')) && tk[i - 2]?.p !== '.';
		if (!onList) {
			// Your own method that takes the list: Routes.scorePreload(s, robot).
			if (before?.id && tk[i + 3]?.id === 's' && (tk[i + 4]?.p === ',' || tk[i + 4]?.p === ')')) {
				const { next } = readArgs(tk, i + 2);
				problems.push(`${before.id}.${m}(s, ...): a method of your own; its steps are not read. Paste them in place of the call.`);
				i = next - 1;
			}
			continue;
		}
		const { args, next } = readArgs(tk, i + 2);
		const label = str(args[0]);
		if (m === 'start') {
			if ((args.length === 2 || args.length === 4) && label != null) {
				const v = nums(args.slice(1), 's.start');
				if (v) start = args.length === 4 ? named({ note: label, x: v[0], y: v[1], h: v[2], spot: true }, ['x', 'y', 'h'], v) : named({ note: label, h: v[0], spot: false }, ['h'], v);
				i = next - 1;
			}
			continue;
		}
		if (OTHER_METHODS.includes(m)) {
			others.add(m);
			i = next - 1;
			continue;
		}
		if (!(m in STEP_METHODS)) continue;
		i = next - 1;
		const want = STEP_METHODS[m];
		const what = `${m}(${label != null ? javaString(label) : text(args[0])})`;
		if (m === 'aimAtGoal' && args.length !== 2 && args.length !== 3) { problems.push(`${what}: needs 2 or 3 things, found ${args.length}. This step was left out.`); continue; }
		if (want != null && args.length !== want) { problems.push(`${what}: needs ${want} things, found ${args.length}. This step was left out.`); continue; }
		if (['move', 'turn', 'turnTo', 'waitSeconds', 'run', 'aimAtGoal', 'readyToLaunch', 'rememberHeading', 'turnBack'].includes(m) && label == null) {
			problems.push(`${m}(${text(args[0])}, ...): write the name in quotes, e.g. "To the goal". This step was left out.`);
			continue;
		}
		if (m === 'move') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'move', label, inches: v[0], dir: v[1], power: v[2] }, ['inches', 'dir', 'power'], v)); }
		else if (m === 'turn') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'turn', label, deg: v[0], power: v[1] }, ['deg', 'power'], v)); }
		else if (m === 'turnTo') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'turnTo', label, heading: v[0], power: v[1] }, ['heading', 'power'], v)); }
		else if (m === 'waitSeconds') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'wait', label, sec: v[0] }, ['sec'], v)); }
		else if (m === 'aimAtGoal') { const v = nums(args.slice(1), what); if (v) steps.push(v.length > 1 ? named({ kind: 'aim', label, timeout: v[0], sweep: v[1] }, ['timeout', 'sweep'], v) : named({ kind: 'aim', label, timeout: v[0] }, ['timeout'], v)); }
		else if (m === 'rememberHeading') steps.push({ kind: 'remember', label });
		else if (m === 'turnBack') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'back', label, power: v[0] }, ['power'], v)); }
		else if (m === 'readyToLaunch') { const v = nums(args.slice(1), what); if (v) steps.push(named({ kind: 'ready', label, timeout: v[0] }, ['timeout'], v)); }
		else if (m === 'run') steps.push({ kind: 'run', label, code: text(args[1]) });
		else if (m === 'alongside') steps.push({ kind: 'alongside', code: text(args[0]) });
		else if (m === 'parkWithSeconds') { const v = nums(args, 'parkWithSeconds'); if (v) steps.push(named({ kind: 'park', sec: v[0] }, ['sec'], v)); }
		else if (m === 'action') {
			if (args.length !== 1 && args.length !== 2) { problems.push(`action(...): give an action and a time limit. This step was left out.`); continue; }
			let timeout = ACTION_TIMEOUT_SEC, v = null;
			if (args.length === 2) { v = nums(args.slice(1), `action(${text(args[0])})`); if (!v) continue; timeout = v[0]; }
			else notes.push(`action(${text(args[0])}) has no time limit: the library gives it ${ACTION_TIMEOUT_SEC} s, and the code now says so.`);
			steps.push(v ? named({ kind: 'action', code: text(args[0]), timeout }, ['timeout'], v) : { kind: 'action', code: text(args[0]), timeout });
		}
	}
	for (const m of others) {
		const why = {
			menuChoice: 'INIT menu lines are not in this builder', menuYesNo: 'INIT menu lines are not in this builder',
			onlyIf: 'every step inside an onlyIf was read as if it always runs', end: 'it closes an onlyIf',
			driveTo: 'use the Pedro / driveTo builder for it', follow: 'use the Pedro / driveTo builder for it',
			step: 'your own steps are not in this builder', waitUntil: 'a wait for a condition is not in this builder',
			moveWithTurn: opts.drive === 'tank' ? 'not possible on a tank drive (it drives sideways while it turns). Use turnTo, then move'
				: 'Steps has no moveWithTurn (it is AutoRoutine\'s). Use turnTo, then move',
		}[m];
		problems.push(`${m}(...) was left out: ${why}.`);
	}
	const anything = start || steps.length;
	if (!anything) problems.push('No s.start(...) or steps (move, turn, turnTo, waitSeconds, run, action, alongside, parkWithSeconds) found.');
	if (anything && !start) notes.push('No s.start(...) line: the start spot stays as it was.');
	else if (start && start.spot === false) notes.push('s.start(note, heading) gives no spot: the robot stays where it was on the map. Drag it to the real spot (the simulator needs the spot too).');
	// A tank drive cannot strafe: each strafe becomes a turn and a drive, and the page lists them.
	let tank = [];
	if (opts.drive === 'tank') {
		const t = tankSteps(steps);
		steps.splice(0, steps.length, ...t.steps);
		tank = t.changes;
	}
	const plan = anything ? { start, steps } : null;
	const around = readAround(src, tk);
	if (plan) {
		plan.setup = around.setup;
		if (mirror !== undefined) plan.mirror = mirror;
		if (opmode !== undefined) plan.opmode = opmode;
		if (teleop !== undefined) plan.teleop = teleop;
		if (group !== undefined) plan.group = group;
		if (cls !== undefined) plan.className = cls;
		if (pkg !== undefined) plan.pkg = pkg.replace(/\.autos$/, '');
	}
	if (plan) {
		for (const s of around.setup) notes.push(`Kept, and the builder's Run does it too: ${s.code.split('   //')[0]}`);
		for (const s of around.skipped) problems.push(`Not carried over, and the builder's Run does not do it: ${s}`);
		for (const m of around.overrides) problems.push(`${m}() in your auto is not carried over, and the builder's Run does not do it.`);
		if (around.comments) notes.push(`${around.comments} comment${around.comments === 1 ? '' : 's'} inside steps() will not be in the Java.`);
	}
	if (own.size) notes.push(`Written as numbers (your auto's own names, not in the builder's file): ${[...own].join(', ')}.`);
	const drive = steps.some((s) => s.kind === 'move' && sideways(s.dir)) ? 'mecanum' : null;
	return { plan, drive, problems, notes, tank, unread: [...unread] };
}

export { pointInPoly, headingOf };
