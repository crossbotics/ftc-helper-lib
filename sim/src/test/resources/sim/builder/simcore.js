// The simulator's auto builder: its logic with no page code (the page is builder.js; the node test is
// tools/check_builder.mjs). The plan model, the checks, the Java and the scenario are the web builder's
// own (stepscore.js and pathcore.js, shared with crossbotics.com's path builder: see docs/guides/simulator.md,
// "The auto builder"). This file adds only what a simulator run needs:
//   - the field file with neutral names ("Your LOADING ZONE"), as the web builder's copy has them;
//   - the plan as the Built Auto reads it (toProgram): the same Steps calls the Java makes;
//   - plan numbers <-> the simulator's field numbers, for each alliance (the Java is written for BLUE);
//   - the plan's own timeline (where the "plan robot" is at each second), for the ghost robot;
//   - plan versus real after a run, and the fixes to offer;
//   - a scenario for a Pedro path, and a scenario with a recorded end pose.
//
// Frame: the builder's (FTC-Helper-Lib's), seen from your drivers' wall, x right, y toward the far wall,
// heading 0 = facing away, counterclockwise +. The simulator's field numbers are seen from the RED wall.
import * as P from './pathcore.js';
import * as S from './stepscore.js';

export const PEDRO_SPEED_IN_PER_SEC = 30;   // the ghost's pace along a Pedro path (a plan, not physics)
export const PEDRO_DONE_IN = 2.5;           // Pedro's "close enough" at the end (docs/modules/pedro.md)

// ------------------------------------------------------------------ the field

/**
 * The simulator's field file (GET /field) as the builder reads it: the parts' names from your wall
 * (the file names them from the RED wall: "Red LOADING ZONE" is yours as RED). The same rules as the
 * site's scripts/sync-path-builder.mjs, which makes the web builder's field.json from this file.
 */
export function neutralField(f) {
	if (!f || !f.elements) return null;
	const r2 = (v) => Math.round(v * 100) / 100;
	const neutralName = (e) => {
		const cx = e.polygon.reduce((s, p) => s + p[0], 0) / e.polygon.length;
		const cy = e.polygon.reduce((s, p) => s + p[1], 0) / e.polygon.length;
		const nearFar = cy < 0 ? 'your' : 'far';
		const side = (v) => (v < 0 ? 'left' : 'right');
		const end = / (lip|back)$/.exec(e.name)?.[0] ?? '';
		if (/^FLOWER/.test(e.name)) {
			const where = Math.abs(cx) > Math.abs(cy) ? `${side(cx)} wall, ${nearFar} half` : `${nearFar} wall, ${side(cx)} half`;
			return `FLOWER (${where})${end}`;
		}
		if (/\((red|blue) side\)/.test(e.name)) return e.name.replace(/\((red|blue) side\)/, `(${nearFar} side)`);
		if (/^(Red|Blue) /.test(e.name)) return e.name.replace(/^(Red|Blue) /, cy < 0 ? 'Your ' : 'Far ');
		return e.name;
	};
	return {
		season: f.season,
		driversLabel: 'your drivers',
		field: { size: f.field.size, insideWallSize: f.field.insideWallSize },
		elements: f.elements.filter((e) => e.polygon && e.polygon.length >= 2).map((e) => ({ name: neutralName(e), kind: e.kind, color: e.color,
			zMin: e.zMin, zMax: e.zMax, polygon: e.polygon.map(([x, y]) => [r2(x), r2(y)]) })),
	};
}

// ------------------------------------------------------------------ plan numbers <-> field numbers

/**
 * A plan pose (written for BLUE, seen from your wall) as the simulator's field pose, for an alliance:
 * RED with mirrorForRed() true flips x and the heading first (EasyAuto); then BLUE's wall is the far one,
 * so BLUE turns half a turn (MatchMemory.fieldPose).
 */
export function planToField(p, alliance, mirror) {
	let { x, y, h } = p;
	h = +h || 0;
	if (alliance === 'RED' && mirror) { x = -x; h = -h; }
	if (alliance === 'BLUE') return { ...p, x: -x, y: -y, h: P.wrap(h + 180) };
	return { ...p, x, y, h: P.wrap(h) };
}

/** The other way: a field pose (the simulator's) as a plan pose for that alliance. */
export function fieldToPlan(p, alliance, mirror) {
	let { x, y, h } = p;
	h = +h || 0;
	if (alliance === 'BLUE') { x = -x; y = -y; h = h + 180; }
	if (alliance === 'RED' && mirror) { x = -x; h = -h; }
	return { ...p, x, y, h: P.wrap(h) };
}

// ------------------------------------------------------------------ the plan as the Built Auto reads it

/**
 * The Built Auto's program (BuiltAuto.Program in the simulator): the start and the steps, in the
 * order and with the numbers the Java file has. b: the builder's state ({ mode, steps: {plan}, pedro }).
 * For a Pedro path: one follow step (follow mode), or one driveTo step per part (driveTo mode), each
 * with its PathAt things, exactly as pathcore.toJava writes them.
 */
export function toProgram(b, robotClass = '') {
	if (b.mode === 'pedro') {
		const st = b.pedro;
		const path = st.path;
		const out = { name: st.opmode || 'Built Auto', mode: 'pedro', mirror: !!st.mirror, robotClass,
			start: { note: st.start.note || 'Place the robot here', x: path.start[0], y: path.start[1], h: +st.start.h || 0, spot: true }, steps: [] };
		const label = st.label || 'My path';
		if (st.mode === 'driveTo') {
			const samples = P.sample(path);
			const split = P.splitForDriveTo(st.actions || [], path);
			const power = st.power ?? 0.7;
			path.parts.forEach((part, i) => {
				out.steps.push({ kind: 'driveTo', label: path.parts.length === 1 ? label : `${label} ${i + 1}`, x: part.end[0], y: part.end[1],
					h: +P.num(P.partEnds(samples, i).end), power: +P.num(part.maxSpeed != null ? Math.min(power, part.maxSpeed) : power),
					along: split[i].map(along) });
			});
		} else if (path.parts.length) {
			out.steps.push({ kind: 'follow', label, timeoutSec: st.timeoutSec != null && st.timeoutSec !== '' && Number.isFinite(+st.timeoutSec) ? +st.timeoutSec : null,
				path: { parts: path.parts.map((p, i) => ({ pts: P.partPoints(path, i), heading: p.heading, maxSpeed: p.maxSpeed ?? null })), holdSec: path.holdSec || 0 },
				along: P.ordered(st.actions || [], P.measure(path)).map(along) });
		}
		return out;
	}
	const plan = b.steps.plan;
	const out = { name: plan.opmode || 'Built Auto', mode: 'steps', mirror: !!plan.mirror, robotClass,
		start: { note: plan.start.note || 'Place the robot here', x: +plan.start.x, y: +plan.start.y, h: +plan.start.h || 0, spot: plan.start.spot !== false },
		steps: (plan.steps || []).map((s) => ({ ...s })) };
	return out;
}

// A PathAt as the program has it: the numbers as the Java writes them (2 decimals at most).
function along(a) {
	const o = { kind: a.kind, value: +P.num(+a.value), what: a.what === 'action' ? 'action' : 'command', label: a.label || '', code: a.code || '' };
	if (a.kind === 'near') { o.x = +P.num(+a.x); o.y = +P.num(+a.y); }
	return o;
}

// ------------------------------------------------------------------ the plan's timeline (the ghost)

/**
 * Where the plan robot is over time: { samples: [{ x, y, h, t, step }], total, ends: [{ x, y, h, t1 }] }
 * in plan numbers. Simple steps: stepscore's dead reckoning with its speed numbers. A Pedro path: along
 * the path at an even pace (PEDRO_SPEED_IN_PER_SEC times the part's speed limit).
 */
export function timeline(b, speeds = S.DEFAULT_SPEEDS) {
	if (b.mode !== 'pedro') {
		const sim = S.simulate(b.steps.plan, speeds);
		return { samples: sim.samples, total: sim.total, ends: sim.ends };
	}
	const st = b.pedro, path = st.path;
	const driven = st.mode === 'driveTo' ? drivenPath(st) : path;
	const pts = P.sample(driven);
	if (!pts.length) {
		const s0 = { x: path.start[0], y: path.start[1], h: +st.start.h || 0, t: 0, step: -1 };
		return { samples: [s0], total: 0, ends: [] };
	}
	let t = 0;
	const samples = [];
	const ends = [];
	pts.forEach((p, k) => {
		if (k > 0) {
			const q = pts[k - 1];
			const part = driven.parts[p.part];
			const v = PEDRO_SPEED_IN_PER_SEC * (part.maxSpeed != null ? Math.max(0.1, part.maxSpeed) : 1);
			t += Math.hypot(p.x - q.x, p.y - q.y) / v;
		}
		samples.push({ x: p.x, y: p.y, h: p.h, t, step: st.mode === 'driveTo' ? p.part : 0, part: p.part });
		if (k === pts.length - 1 || pts[k + 1].part !== p.part) ends.push({ x: p.x, y: p.y, h: p.h, t0: ends.length ? ends[ends.length - 1].t1 : 0, t1: t });
	});
	return { samples, total: t, ends: st.mode === 'driveTo' ? ends : [{ ...ends[ends.length - 1], t0: 0 }] };
}

/** The path driveTo drives: straight to each part's end, turning from the heading before to the part's end heading. */
export function drivenPath(st) {
	const samples = P.sample(st.path);
	let h = +st.start.h || 0;
	const parts = st.path.parts.map((p, i) => {
		const end = P.partEnds(samples, i).end;
		const part = { ctrl: [], end: p.end, heading: { mode: 'turn', a: h, b: end }, maxSpeed: null };
		h = end;
		return part;
	});
	return { start: st.path.start, parts, holdSec: 0 };
}

/**
 * Where along the plan the plan robot should be t seconds after START, kept in step with the real run:
 * each step starts in the plan when the real one starts (when the step before it ended in the report), and
 * goes at the plan's pace until the plan's step is over. So a step that is faster or slower for real (an
 * action that ends before its time limit) does not leave the plan robot behind for the rest of the run.
 * built: the simulator's run (its ends: [x, y, h, seconds since START] per report line).
 */
export function planTimeAt(b, tl, built, t) {
	const steps = reportSteps(b);
	const report = (built && built.report) || [];
	const ends = (built && built.ends) || [];
	const lead = /^Start delay:/.test(report[1] || '') ? 2 : 1;
	let since = ends[lead - 1] ? ends[lead - 1][3] : 0;
	let k = 0;
	for (; k < steps.length; k++) {
		const e = ends[lead + k];
		if (!e || e[3] > t) break;
		since = e[3];
	}
	if (k >= steps.length) return tl.total;
	const e = tl.ends[steps[k].i];
	if (!e) return Math.min(tl.total, Math.max(0, t));
	return Math.min(e.t1, e.t0 + Math.max(0, t - since));
}

/** The pose in a timeline at t seconds (the start before 0, the end after the last). */
export function poseAtTime(tl, t) {
	const s = tl.samples;
	if (!s.length) return null;
	if (t <= s[0].t) return { ...s[0] };
	for (let k = 1; k < s.length; k++) {
		if (s[k].t >= t) {
			const a = s[k - 1], c = s[k];
			const f = c.t > a.t ? (t - a.t) / (c.t - a.t) : 1;
			return { x: a.x + (c.x - a.x) * f, y: a.y + (c.y - a.y) * f, h: P.wrap(a.h + P.wrap(c.h - a.h) * f), step: c.step, t };
		}
	}
	return { ...s[s.length - 1], t };
}

// ------------------------------------------------------------------ plan versus real

/**
 * The plan's steps that make a report line, in order: [{ i (index in the plan's step list, or the
 * Pedro step), name, moving }]. The report's first line is "Start" (and "Start delay" with a delay);
 * a park step has no line (it only marks where the park steps start).
 */
export function reportSteps(b) {
	if (b.mode === 'pedro') {
		const prog = toProgram(b);
		return prog.steps.map((s, i) => ({ i, name: s.label, moving: true }));
	}
	const out = [];
	(b.steps.plan.steps || []).forEach((s, i) => {
		if (s.kind === 'park') return;
		out.push({ i, name: S.stepName(s), moving: s.kind === 'move' || s.kind === 'turn' || s.kind === 'turnTo' });
	});
	return out;
}

/**
 * Plan versus real, one row per reported step: { i, name, moving, word (the report's word: done,
 * STALLED...), plan: {x, y, h}, real: {x, y, h} (both plan numbers), off (inches), offDeg }.
 * built: the simulator's run (state.built); alliance and mirror: how it ran.
 */
export function compare(b, built, tl) {
	if (!built || !built.report) return [];
	const steps = reportSteps(b);
	const report = built.report;
	const lead = report.length && /^Start delay:/.test(report[1] || '') ? 2 : 1;   // "Start", maybe "Start delay"
	const rows = [];
	steps.forEach((st, k) => {
		const line = report[lead + k];
		const end = built.ends?.[lead + k];
		if (line == null) { rows.push({ ...st, word: '', plan: planEnd(b, tl, st.i), real: null }); return; }
		const word = line.includes(': ') ? line.slice(line.indexOf(': ') + 2) : line;
		// The report's own name for the step (an action's name, "Fire all", in place of its code).
		const name = line.includes(': ') ? line.slice(0, line.indexOf(': ')).replace(/ \(alongside\)$/, '') : st.name;
		const real = end ? fieldToPlan({ x: end[0], y: end[1], h: end[2] }, built.alliance || 'RED', mirrorOf(b)) : null;
		const plan = planEnd(b, tl, st.i);
		const row = { ...st, name, word, plan, real, t: end ? end[3] : null };
		if (real && plan) { row.off = Math.hypot(real.x - plan.x, real.y - plan.y); row.offDeg = Math.abs(P.wrap(real.h - plan.h)); }
		rows.push(row);
	});
	return rows;
}

const mirrorOf = (b) => (b.mode === 'pedro' ? !!b.pedro.mirror : !!b.steps.plan.mirror);

function planEnd(b, tl, i) {
	const e = tl.ends[i];
	return e ? { x: e.x, y: e.y, h: e.h } : null;
}

/**
 * What to change, from a run: [{ level: 'good' | 'warn' | 'bad', text, fix: { kind, at, ... } | null }].
 * A fix the page can apply with one click: { kind: 'wait', at } (a 0.3 s wait before plan step at),
 * { kind: 'hold' } (hold the Pedro path's end), { kind: 'slowLast' } (the last part at 0.7 speed).
 * rows: compare(...); error: the simulator's error text (INIT stopped); tolIn: "near enough" inches.
 */
export function fixes(b, built, rows, error, tolIn = 4) {
	const out = [];
	if (error) {
		out.push({ level: 'bad', text: `It stopped with an error: ${error.split('\n')[0]}`, fix: null });
		if (/no part called|no public method|does not take|not a plain value|Actions/.test(error)) {
			out.push({ level: 'warn', text: 'Check the names in the steps against your Robot.java and Actions.java (the message lists the ones there are).', fix: null });
		}
		return out;
	}
	if (!built || built.phase !== 'done' || !rows.length) return out;
	const steps = b.mode === 'pedro' ? [] : b.steps.plan.steps;
	let flagged = false;
	rows.forEach((r, k) => {
		const word = r.word || '';
		if (!word) {
			out.push({ level: 'bad', text: `"${r.name}" did not run: the 30 s timer (or STOP) ended the autonomous first.`, fix: null });
			return;
		}
		if (/STALLED/.test(word)) out.push({ level: 'bad', text: `"${r.name}": STALLED. The robot was stuck (a wall or a field part in the way?). Look at the green line on the map where it stopped.`, fix: null });
		else if (/TIMED_OUT|timed out/.test(word)) out.push({ level: 'warn', text: `"${r.name}": ${word}. It ran out of time: split it, or give it more power or a longer limit.`, fix: null });
		else if (/skipped \(time for park\)|cut short/.test(word)) out.push({ level: 'warn', text: `"${r.name}": ${word}. The steps before the park need more time: fewer steps, more power, or a shorter park time.`, fix: null });
		else if (/NO_ODOMETRY/.test(word)) out.push({ level: 'bad', text: `"${r.name}": NO_ODOMETRY. Check the odometry lines in the robot file and Robot.java.`, fix: null });
		if (/not fired: (.*)/.test(word)) out.push({ level: 'warn', text: `"${r.name}": ${word}. Those PathAt things never started: put them earlier on the path.`, fix: null });
		// A drive that rolled on into the next step: the robot ends off the plan right after a turn or a
		// new direction that follows a drive with no wait (the web builder warns about it before a run too).
		if (!flagged && b.mode !== 'pedro' && r.real && r.off != null && k > 0) {
			const prevOff = rows[k - 1].off ?? 0;
			const s = steps[r.i], prev = steps[r.i - 1];
			const way = (st) => (+st.dir || 0) + (+st.inches < 0 ? 180 : 0);
			const after = prev?.kind === 'move' && Math.abs(+prev.inches) >= 0.5
				&& (s.kind === 'turn' || s.kind === 'turnTo' || (s.kind === 'move' && Math.abs(P.wrap(way(s) - way(prev))) > 10));
			if (after && r.off - prevOff > 1.5) {
				flagged = true;
				out.push({ level: 'warn', text: `"${r.name}" ended ${P.num(P.round1(r.off))} in off the plan: it started while the robot still rolled from "${rows[k - 1].name}". Add a 0.3 s wait before it.`,
					fix: { kind: 'wait', at: r.i } });
			}
		}
	});
	const last = [...rows].reverse().find((r) => r.real && r.moving);
	if (last) {
		if (b.mode === 'pedro') {
			const st = b.pedro;
			if (last.off > PEDRO_DONE_IN + 1 && !(st.path.holdSec > 0)) {
				out.push({ level: 'warn', text: `The path ended ${P.num(P.round1(last.off))} in from its end point. Hold the end (holdEnd) so Pedro keeps correcting while the next steps run.`, fix: { kind: 'hold' } });
			} else if (last.off > PEDRO_DONE_IN + 1) {
				const lp = st.path.parts[st.path.parts.length - 1];
				if (!(lp?.maxSpeed <= 0.7)) out.push({ level: 'warn', text: `The path ended ${P.num(P.round1(last.off))} in from its end point. Slow the last part (max speed 0.7) so it brakes in time.`, fix: { kind: 'slowLast' } });
			}
		}
		if (last.off <= tolIn && (last.offDeg ?? 0) <= 10 && !out.some((o) => o.level !== 'good')) {
			out.push({ level: 'good', text: `It ended ${P.num(P.round1(last.off))} in and ${P.num(Math.round(last.offDeg ?? 0))}° from the plan: near enough (within ${P.num(tolIn)} in). Save the scenario so a test keeps it that way.`, fix: null });
		} else if (last.off > tolIn) {
			out.push({ level: 'warn', text: `It ended ${P.num(P.round1(last.off))} in from the plan's end. The table shows where it first went off.`, fix: null });
		}
	}
	return out;
}

/** Applies a fix from fixes() to the builder's state (b is changed). True if it changed anything. */
export function applyFix(b, fix) {
	if (!fix) return false;
	if (fix.kind === 'wait' && b.mode !== 'pedro') {
		b.steps.plan.steps.splice(fix.at, 0, { kind: 'wait', label: 'Settle', sec: 0.3 });
		return true;
	}
	if (fix.kind === 'hold' && b.mode === 'pedro') {
		b.pedro.path.holdSec = P.DEFAULT_HOLD_SEC;
		return true;
	}
	if (fix.kind === 'slowLast' && b.mode === 'pedro') {
		const parts = b.pedro.path.parts;
		if (!parts.length) return false;
		parts[parts.length - 1].maxSpeed = 0.7;
		return true;
	}
	return false;
}

// ------------------------------------------------------------------ scenarios

const simString = (s) => '"' + String(s).replace(/"/g, "'") + '"';
const r1 = (v) => P.num(Math.round(v * 10) / 10);

/**
 * A scenario for a Pedro path (the web builder writes one only for Simple steps): INIT as RED, check
 * the note and the start spot, START, wait for the end, each step "done", and the end near the path's
 * end (Pedro stops within 2.5 in; tolIn adds room). st: the builder's Pedro state; opts: { tolIn, tolDeg, fileName }.
 */
export function pedroScenario(st, opts = {}) {
	const tolIn = opts.tolIn ?? 4, tolDeg = opts.tolDeg ?? 10;
	const file = opts.fileName || 'my-path.sim';
	const name = st.opmode || 'My Auto';
	const b = { mode: 'pedro', pedro: st };
	const prog = toProgram(b);
	const tl = timeline(b);
	const endPlan = tl.samples[tl.samples.length - 1];
	const e = planToField(endPlan, 'RED', !!st.mirror);
	const s0 = planToField({ x: st.path.start[0], y: st.path.start[1], h: +st.start.h || 0 }, 'RED', !!st.mirror);
	const out = [
		`# ${name}: does the robot follow the path to its end?`,
		'# Made by the simulator\'s auto builder (Pedro path). Pedro stops within about 2.5 in of the end:',
		`# within ${P.num(tolIn)} in and ${P.num(tolDeg)} degrees here.`,
		`# Save it as simulator/scenarios/${file} in your robot project, then from the ftc-helper-lib folder:`,
		`#   ./gradlew :sim:scenario -Pfile=../simulator/scenarios/${file}`,
		'# The simulator\'s field numbers are seen from the RED wall. ' + (st.mirror
			? 'With mirrorForRed() true, RED drives the route mirrored: the numbers below are mirrored.'
			: 'With mirrorForRed() false (BIOBUZZ), RED drives the numbers as written.'),
		`init ${simString(name)}`,
		'press g1.b                             # RED',
	];
	if (st.start.on) {
		const note = String(st.start.note || 'Place the robot here').split('"')[0].trimEnd();
		if (note.length >= 3) out.push(`expect screen has ${simString('Place robot : ' + note)}   # the note at INIT`);
		out.push(`expect robot x = ${r1(s0.x)} 0.5                # on the start spot`, `expect robot y = ${r1(s0.y)} 0.5`);
	} else {
		out.push(`set pose ${r1(s0.x)} ${r1(s0.y)} ${r1(s0.h)}   # the code gives no spot: put the robot there`);
	}
	out.push('start', `expect within ${S.AUTO_SEC + 2} screen has "Autonomous finished"`);
	const seen = new Set();
	for (const s of prog.steps) {
		const label = String(s.label ?? '').trim();
		if (!label || /["#]/.test(label) || seen.has(label)) continue;
		seen.add(label);
		out.push(`expect screen has ${simString(label + ': done')}`);
	}
	out.push(`expect robot x = ${r1(e.x)} ${P.num(tolIn)}`, `expect robot y = ${r1(e.y)} ${P.num(tolIn)}`);
	if (Math.abs(e.h) <= 180 - tolDeg - 5) out.push(`expect robot heading = ${r1(e.h)} ${P.num(tolDeg)}`);
	else out.push(`# heading: about ${r1(e.h)} (near 180, where the number jumps to -180): check it on the map`);
	out.push('expect error none', '');
	return out.join('\n');
}

/**
 * A scenario with the end pose a real run gave (the robot's own end in the simulator, as RED would end)
 * in place of the plan's: for a route that is right as it drives, so a later change that moves the end
 * is caught. text: a scenario from toScenario or pedroScenario; end: { x, y, h } in plan numbers.
 */
export function recordedScenario(text, end, mirror, when = '') {
	const e = planToField(end, 'RED', !!mirror);
	const lines = text.split('\n');
	const out = [];
	let tolDeg = 10;
	for (const l of lines) {
		const mx = /^expect robot x = \S+ (\S+)$/.exec(l);
		if (mx && !/start spot/.test(l)) { out.push(`# Recorded from a simulator run${when ? ' on ' + when : ''}: the robot really ended here (the plan said otherwise by a little).`); out.push(`expect robot x = ${r1(e.x)} ${mx[1]}`); continue; }
		const my = /^expect robot y = \S+ (\S+)$/.exec(l);
		if (my && !/start spot/.test(l) && out.some((o) => o.startsWith('# Recorded'))) { out.push(`expect robot y = ${r1(e.y)} ${my[1]}`); continue; }
		const mh = /^expect robot heading = \S+ (\S+)$/.exec(l);
		if (mh) { tolDeg = +mh[1]; out.push(Math.abs(e.h) <= 180 - tolDeg - 5 ? `expect robot heading = ${r1(e.h)} ${mh[1]}` : `# heading: about ${r1(e.h)} (near 180): check it on the map`); continue; }
		if (/^# heading: about/.test(l)) { out.push(Math.abs(e.h) <= 180 - tolDeg - 5 ? `expect robot heading = ${r1(e.h)} ${P.num(tolDeg)}` : `# heading: about ${r1(e.h)} (near 180): check it on the map`); continue; }
		out.push(l);
	}
	return out.join('\n');
}
