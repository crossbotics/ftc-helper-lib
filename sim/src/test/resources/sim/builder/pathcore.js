// The path builder's logic, with no page code: the path model, Pedro-like sampling, the robot's
// footprint along the path, hits on field parts, the other alliance's path, Java out and Java in.
// The page (app.js) and the test (scripts/test-path-builder.mjs) both use it.
//
// Frame (FTC-Helper-Lib's): seen from your drivers' wall, x inches right of the centre line, y inches
// away from the centre toward the far wall, heading degrees, 0 = facing away, counterclockwise +.
//
// A path: { start: [x, y], parts: [part...], holdSec: 0 }. Each part starts where the one before ends.
// A part: { ctrl: [[x, y]...] (0 = a line, 1 or more = a curve), end: [x, y],
//           heading: { mode: 'path' | 'back' | 'keep' | 'turn' | 'point', a, b, x, y }, maxSpeed: null | 0..1 }
// The sampling follows ftc-helper-lib's PedroFollower.sample (one point about every inch, at most 200
// a part) and Pedro 3.0.2's heading rules (turnTo takes the short way round).

export const DEFAULT_HOLD_SEC = 5;          // PedroPath.DEFAULT_HOLD_SEC
export const MAX_POINTS = 200;              // PedroFollower.MAX_PLANNED_POINTS

// ------------------------------------------------------------------ small helpers

export const wrap = (deg) => {
	let d = deg % 360;
	if (d > 180) d -= 360;
	if (d <= -180) d += 360;
	return d;
};
const diff = (from, to) => wrap(to - from);
export const round1 = (v) => Math.round(v * 10) / 10;
/** A number as Java code: at most 2 decimals, no trailing zeros ("-56", "0.5"). */
export const num = (v) => {
	const r = Math.round(v * 100) / 100;
	return String(Object.is(r, -0) ? 0 : r);
};
/** Heading (degrees) of a direction (dx, dy): 0 = +y, counterclockwise +. */
export const headingOf = (dx, dy) => wrap(Math.atan2(-dx, dy) * 180 / Math.PI);

export function partStart(path, i) {
	return i === 0 ? path.start : path.parts[i - 1].end;
}

export function partPoints(path, i) {
	const p = path.parts[i];
	return [partStart(path, i), ...p.ctrl, p.end];
}

// A Bezier curve at t: the point and the direction (de Casteljau).
function bez(pts, t) {
	let q = pts.map((p) => [p[0], p[1]]);
	let dir = [0, 0];
	while (q.length > 1) {
		if (q.length === 2) dir = [q[1][0] - q[0][0], q[1][1] - q[0][1]];
		q = q.slice(1).map((b, k) => [q[k][0] + (b[0] - q[k][0]) * t, q[k][1] + (b[1] - q[k][1]) * t]);
	}
	return { x: q[0][0], y: q[0][1], dx: dir[0], dy: dir[1] };
}

export function curveLength(pts) {
	let len = 0, last = bez(pts, 0);
	for (let k = 1; k <= 60; k++) {
		const p = bez(pts, k / 60);
		len += Math.hypot(p.x - last.x, p.y - last.y);
		last = p;
	}
	return len;
}

// The facing at t for a part's heading rule. tangentAt: the path's own direction there.
function headingAt(h, t, x, y, tangent) {
	switch (h.mode) {
		case 'keep': return wrap(h.a);
		case 'turn': return wrap(h.a + diff(h.a, h.b) * t);
		case 'point': return Math.hypot(h.x - x, h.y - y) < 1e-6 ? tangent : headingOf(h.x - x, h.y - y);
		case 'back': return wrap(tangent + 180);
		default: return tangent;
	}
}

/**
 * Points along the path: [{ x, y, h, part, t }], about one every inch (the first is the start).
 * Each part's points include its start, so a join appears twice (once per part).
 */
export function sample(path) {
	const out = [];
	path.parts.forEach((part, i) => {
		const pts = partPoints(path, i);
		const len = curveLength(pts);
		const n = Math.max(2, Math.min(MAX_POINTS, Math.ceil(len) + 1));
		// A curve's direction at an end can be zero (a control point on the end): look just inside.
		let lastTan = null;
		for (let k = 0; k < n; k++) {
			const t = k / (n - 1);
			const p = bez(pts, t);
			let tan;
			if (Math.hypot(p.dx, p.dy) > 1e-9) tan = headingOf(p.dx, p.dy);
			else {
				const q = bez(pts, Math.min(1, Math.max(0, t < 0.5 ? t + 1e-3 : t - 1e-3)));
				tan = Math.hypot(q.dx, q.dy) > 1e-9 ? headingOf(q.dx, q.dy) : (lastTan ?? 0);
			}
			lastTan = tan;
			out.push({ x: p.x, y: p.y, h: headingAt(part.heading, t, p.x, p.y, tan), part: i, t });
		}
	});
	return out;
}

/** The heading a part starts and ends with (from its samples). */
export function partEnds(samples, i) {
	const s = samples.filter((p) => p.part === i);
	return s.length ? { start: s[0].h, end: s[s.length - 1].h } : null;
}

// ------------------------------------------------------------------ the robot's footprint

/** The four corners of the robot at (x, y, h): length front to back, width side to side, inches. */
export function footprint(x, y, h, length = 18, width = 18) {
	const r = h * Math.PI / 180;
	const f = [-Math.sin(r), Math.cos(r)], s = [Math.cos(r), Math.sin(r)];
	const corner = (a, b) => [x + f[0] * a + s[0] * b, y + f[1] * a + s[1] * b];
	return [corner(length / 2, -width / 2), corner(length / 2, width / 2), corner(-length / 2, width / 2), corner(-length / 2, -width / 2)];
}

function segCross(a, b, c, d) {
	const o = (p, q, r) => (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]);
	const d1 = o(c, d, a), d2 = o(c, d, b), d3 = o(a, b, c), d4 = o(a, b, d);
	return ((d1 > 0) !== (d2 > 0)) && ((d3 > 0) !== (d4 > 0));
}

export function pointInPoly(p, poly) {
	let inside = false;
	for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
		const [xi, yi] = poly[i], [xj, yj] = poly[j];
		if ((yi > p[1]) !== (yj > p[1]) && p[0] < (xj - xi) * (p[1] - yi) / (yj - yi) + xi) inside = !inside;
	}
	return inside;
}

/** True if two polygons overlap (any shape). */
export function polysOverlap(a, b) {
	for (let i = 0; i < a.length; i++) {
		for (let j = 0; j < b.length; j++) {
			if (segCross(a[i], a[(i + 1) % a.length], b[j], b[(j + 1) % b.length])) return true;
		}
	}
	return pointInPoly(a[0], b) || pointInPoly(b[0], a);
}

/**
 * The field parts a robot can hit, from the simulator's field file (sim/fields/*.json): the solids
 * taller than a tile seam (not a FLOWER's 0.35 in lip) that are inside the walls.
 */
export function obstacles(field) {
	const half = (field.field?.insideWallSize ?? 141.17) / 2;
	return (field.elements || []).filter((e) => e.kind === 'solid' && (e.zMax ?? 1) > 1
		&& e.polygon.some(([x, y]) => Math.abs(x) < half && Math.abs(y) < half))
		.map((e) => ({ name: e.name.replace(/ back$/, ''), polygon: e.polygon,
			box: [Math.min(...e.polygon.map((p) => p[0])), Math.min(...e.polygon.map((p) => p[1])),
				Math.max(...e.polygon.map((p) => p[0])), Math.max(...e.polygon.map((p) => p[1]))] }));
}

/**
 * Checks the robot's footprint at every sample: which field part or wall it touches. Returns
 * { hitAt: [bool per sample], hits: [{ part, name, x, y }] } (one hit per part and field part).
 */
export function checkHits(samples, obs, half, length = 18, width = 18) {
	const hitAt = [], hits = [], seen = new Set();
	for (const s of samples) {
		const fp = footprint(s.x, s.y, s.h, length, width);
		const bx = [Math.min(...fp.map((p) => p[0])), Math.min(...fp.map((p) => p[1])), Math.max(...fp.map((p) => p[0])), Math.max(...fp.map((p) => p[1]))];
		const names = [];
		if (bx[0] < -half || bx[1] < -half || bx[2] > half || bx[3] > half) names.push('the wall');
		for (const o of obs) {
			if (bx[2] < o.box[0] || bx[0] > o.box[2] || bx[3] < o.box[1] || bx[1] > o.box[3]) continue;
			if (polysOverlap(fp, o.polygon)) names.push(o.name);
		}
		hitAt.push(names.length > 0);
		for (const name of names) {
			const key = s.part + '|' + name;
			if (!seen.has(key)) { seen.add(key); hits.push({ part: s.part, name, x: s.x, y: s.y }); }
		}
	}
	return { hitAt, hits };
}

// ------------------------------------------------------------------ the other alliance

/**
 * Where the other alliance's robot drives the same code, seen from your wall. Rotated field
 * (mirrorForRed() false, like BIOBUZZ): the same numbers from the far wall = turned half a turn.
 * Mirrored field (mirrorForRed() true): x and heading flip in their frame, then the half turn.
 */
export function otherAlliance(p, mirror) {
	return mirror ? { ...p, x: p.x, y: -p.y, h: wrap(180 - p.h) } : { ...p, x: -p.x, y: -p.y, h: wrap(p.h + 180) };
}

// ------------------------------------------------------------------ warnings

/** Plain warnings: turns at a join, a start heading that does not match, a part with no length. */
export function warnings(path, samples, startHeading) {
	const out = [];
	path.parts.forEach((part, i) => {
		const ends = partEnds(samples, i);
		if (!ends) return;
		if (curveLength(partPoints(path, i)) < 0.5) out.push(`Part ${i + 1} has no length: its end is on its start.`);
		const before = i === 0 ? startHeading : partEnds(samples, i - 1)?.end;
		if (before != null && Math.abs(diff(before, ends.start)) > 5) {
			const where = i === 0 ? 'The start spot faces' : `Part ${i} ends facing`;
			out.push(`${where} ${num(before)}, but part ${i + 1} starts facing ${num(ends.start)}: the robot turns fast there.`);
		}
	});
	return out;
}

// ------------------------------------------------------------------ things to do along the path (PathAt)

// An action: { kind: 'inchesDone' | 'inchesLeft' | 'fraction' | 'part' | 'near', value, x, y,
//              what: 'command' | 'action', label, code }
// value: inches (inchesDone, inchesLeft), 0..1 (fraction), the part number (part), how close (near).
// x, y: near's field point (written for BLUE, like driveTo). what 'command': a label and a command
// (robot.intake::in); what 'action': an action from Actions.java (Actions.scoreHigh(robot)), no label.
export const KINDS = ['inchesDone', 'inchesLeft', 'fraction', 'part', 'near'];

/**
 * The path as ftc-helper-lib's PedroFollower.measure sees it: points about every inch along each part
 * (no cap), the distance along the whole path to each, and where each part starts.
 * { pts: [{ x, y, in, part }], partStartIn: [inches], total, parts }
 */
export function measure(path) {
	const pts = [], partStartIn = [];
	let along = 0;
	path.parts.forEach((part, k) => {
		const cp = partPoints(path, k);
		const n = Math.max(2, Math.ceil(curveLength(cp)) + 1);
		partStartIn.push(along);   // where the last part ended (0 for the first)
		for (let i = k === 0 ? 0 : 1; i < n; i++) {   // its first point is the last part's end
			const p = bez(cp, i / (n - 1));
			if (pts.length) { const l = pts[pts.length - 1]; along += Math.hypot(p.x - l.x, p.y - l.y); }
			pts.push({ x: p.x, y: p.y, in: along, part: k });
		}
	});
	if (!pts.length) pts.push({ x: path.start[0], y: path.start[1], in: 0, part: 0 });
	return { pts, partStartIn, total: along, parts: path.parts.length };
}

/** The part (from 1) at a distance along the path, like PedroFollower.partNow(). */
export function partAt(m, d) {
	let part = 1;
	for (let i = 1; i < m.partStartIn.length; i++) if (d >= m.partStartIn[i] - 1e-9) part = i + 1;
	return part;
}

/** The point at a distance along the path: { x, y, in, part (from 1) }. */
export function pointAt(m, d) {
	const p = m.pts;
	d = Math.max(0, Math.min(m.total, d));
	let k = 0;
	while (k + 1 < p.length - 1 && p[k + 1].in < d) k++;
	const a = p[k], b = p[Math.min(k + 1, p.length - 1)];
	const t = b.in > a.in ? (d - a.in) / (b.in - a.in) : 0;
	return { x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t, in: d, part: partAt(m, d) };
}

/** The distance along the path of the point on it closest to (x, y), and how far off that is. */
export function project(m, x, y) {
	const p = m.pts;
	let best = { in: 0, off: Math.hypot(x - p[0].x, y - p[0].y) };
	for (let i = 0; i + 1 < p.length; i++) {
		const dx = p[i + 1].x - p[i].x, dy = p[i + 1].y - p[i].y, len2 = dx * dx + dy * dy;
		const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, ((x - p[i].x) * dx + (y - p[i].y) * dy) / len2));
		const off = Math.hypot(x - (p[i].x + t * dx), y - (p[i].y + t * dy));
		if (off < best.off - 1e-9) best = { in: p[i].in + t * (p[i + 1].in - p[i].in), off };
	}
	return best;
}

/**
 * Where an action starts along the path, as the library decides it:
 * { in, x, y, part, when: 'on' | 'start' (at once) | 'never' }.
 * inchesDone v: inches done >= v. inchesLeft v: inches left <= v. fraction f: done / total >= f.
 * part(n): where part n starts (part 1 = at once). near: the first place the robot's centre is
 * within the distance of the point (the robot drives the path exactly; really it may pass a few inches off).
 */
export function actionPlace(a, m) {
	const T = m.total;
	let d = 0, when = 'on';
	const v = +a.value;
	switch (a.kind) {
		case 'inchesDone': d = Math.min(Math.max(v, 0), T); if (v > T + 1e-6) when = 'never'; break;
		case 'inchesLeft': d = Math.max(0, T - Math.max(v, 0)); if (v >= T - 1e-6) when = 'start'; break;
		case 'fraction': d = Math.max(0, Math.min(1, v)) * T; break;
		case 'part': {
			const n = Math.round(v);
			if (n <= 1) d = 0;
			else if (n > m.parts) { d = T; when = 'never'; } else d = m.partStartIn[n - 1];
			break;
		}
		case 'near': {
			// The first point along the path within the distance (a circle cut on each short line).
			const p = m.pts, r = v;
			const inside = (q) => Math.hypot(q.x - a.x, q.y - a.y) <= r;
			if (inside(p[0])) { d = 0; break; }
			let found = null;
			for (let i = 0; i + 1 < p.length && found == null; i++) {
				const dx = p[i + 1].x - p[i].x, dy = p[i + 1].y - p[i].y;
				const fx = p[i].x - a.x, fy = p[i].y - a.y;
				const A = dx * dx + dy * dy, B = 2 * (fx * dx + fy * dy), C = fx * fx + fy * fy - r * r;
				const disc = B * B - 4 * A * C;
				if (A === 0 || disc < 0) continue;
				const t = (-B - Math.sqrt(disc)) / (2 * A);
				if (t >= 0 && t <= 1) found = p[i].in + t * (p[i + 1].in - p[i].in);
			}
			if (found == null) { d = project(m, a.x, a.y).in; when = 'never'; } else d = found;
			break;
		}
		default: when = 'never';
	}
	const at = pointAt(m, d);
	return { ...at, in: d, when };
}

/** The actions in the order they come along the path (ties keep their order). */
export function ordered(actions, m) {
	return actions.map((a, i) => ({ a, i, d: actionPlace(a, m).in }))
		.sort((p, q) => p.d - q.d || p.i - q.i).map((p) => p.a);
}

const METHOD_TEXT = (a) => 'PathAt.' + a.kind;

/**
 * The mistakes the library stops INIT for ('bad'), and places that do not do what one would think
 * ('warn'). Each: { i (index in actions), level, text }. opts: { step: 'follow' | 'driveTo', label }.
 */
export function checkActions(actions, m, opts = {}) {
	const out = [];
	const step = opts.step || 'follow', label = opts.label || 'My path';
	const T = m.total;
	actions.forEach((a, i) => {
		const what = METHOD_TEXT(a), v = +a.value;
		const bad = (text) => out.push({ i, level: 'bad', text });
		const warn = (text) => out.push({ i, level: 'warn', text });
		if (!KINDS.includes(a.kind)) { bad(`${what}: there is no such PathAt.`); return; }
		if (!String(a.code ?? '').trim() || String(a.code).trim() === 'null') bad(`${what}: give something to do (it is null)`);
		else if (a.what !== 'action' && !String(a.label ?? '').trim()) bad(`${what}: give a name for the report, e.g. "Spin up"`);
		if (!Number.isFinite(v)) { bad(`${what}: give a number`); return; }
		if (a.kind === 'inchesDone' || a.kind === 'inchesLeft') {
			if (v < 0) bad(`${what}: give 0 inches or more (not ${num(v)})`);
			else if (a.kind === 'inchesLeft' && v >= T && T > 0) warn(`${what}(${num(v)}): the path is only ${num(T)} in long, so it starts at once.`);
			else if (a.kind === 'inchesDone' && v > T) warn(`${what}(${num(v)}): the path is only ${num(T)} in long, so it never starts.`);
		} else if (a.kind === 'fraction') {
			if (!(v >= 0 && v <= 1)) bad(`PathAt.fraction: give a part of the path from 0 to 1, e.g. 0.5 = halfway (not ${num(v)})`);
		} else if (a.kind === 'part') {
			if (!Number.isInteger(v)) bad(`PathAt.part: give a whole number (not ${num(v)})`);
			else if (v < 1) bad(`PathAt.part: parts count from 1 (not ${num(v)})`);
			else if (v > m.parts) {
				bad(step === 'follow'
					? `Steps: follow("${label}"): PathAt.part(${num(v)}), but the path has ${m.parts} ${m.parts === 1 ? 'part' : 'parts'}`
					: `PathAt.part(${num(v)}): there are only ${m.parts} driveTo ${m.parts === 1 ? 'step' : 'steps'}`);
			}
		} else if (a.kind === 'near') {
			if (!(v > 0)) bad(`PathAt.near: give a distance more than 0 inches (not ${num(v)})`);
			else if (![a.x, a.y].every(Number.isFinite)) bad('PathAt.near: give the point\'s x and y');
			else if (actionPlace(a, m).when === 'never') {
				warn(`${what}(${num(a.x)}, ${num(a.y)}, ${num(v)}): the path never comes that close, so it does not start (the report says "not fired").`);
			}
		}
	});
	return out;
}

/** One PathAt as Java: PathAt.inchesLeft(12, "Spin up", robot.launcher::spinUp). */
export function pathAtCode(a) {
	const code = String(a.code ?? '').trim() || 'null';
	const what = a.what === 'action' ? code : `${javaString(a.label)}, ${code}`;
	const lead = a.kind === 'near' ? `${num(a.x)}, ${num(a.y)}, ${num(a.value)}` : num(a.value);
	return `PathAt.${a.kind}(${lead}, ${what})`;
}

// The straight lines driveTo drives (one a part), for measuring driveTo steps.
function straightPath(path) {
	return { start: path.start, parts: path.parts.map((p) => ({ ctrl: [], end: p.end, heading: { mode: 'path' }, maxSpeed: null })), holdSec: 0 };
}

/**
 * driveTo mode: each action goes on the driveTo step of the part where it starts, with its number
 * counted along that step (each driveTo is its own straight "path"). Returns [[action...] per step].
 */
export function splitForDriveTo(actions, path) {
	const m = measure(straightPath(path));
	const steps = path.parts.map(() => []);
	if (!steps.length) return steps;
	for (const a of ordered(actions, m)) {
		const place = actionPlace(a, m);
		const d = place.in;
		let k;
		if (a.kind === 'part') k = Math.max(1, Math.min(steps.length, Math.round(+a.value))) - 1;
		else if (a.kind === 'inchesLeft') {   // the step it is before the end of: at a join, the earlier step
			k = 0;
			for (let i = 1; i < steps.length; i++) if (d > m.partStartIn[i] + 1e-9) k = i;
		} else {
			k = 0;
			for (let i = 1; i < steps.length; i++) if (d >= m.partStartIn[i] - 1e-9) k = i;
			if (a.kind === 'near' && place.when === 'never') k = steps.length - 1;
		}
		const s = m.partStartIn[k], L = (k + 1 < steps.length ? m.partStartIn[k + 1] : m.total) - s;
		const b = { ...a };
		if (a.kind === 'inchesDone') b.value = d - s;
		else if (a.kind === 'inchesLeft') b.value = s + L - d;
		else if (a.kind === 'fraction') b.value = L > 0 ? (d - s) / L : 0;
		else if (a.kind === 'part') b.value = Math.round(+a.value) > steps.length || !(+a.value >= 1) ? a.value : 1;   // a wrong part stays wrong
		// Out-of-range numbers stay as written, so the step's own check says the same thing.
		if (a.kind === 'inchesDone' && place.when === 'never') b.value = +a.value - s;
		if (a.kind === 'inchesLeft' && place.when === 'start') b.value = +a.value - (m.total - s - L);
		if (a.kind === 'fraction' && !(+a.value >= 0 && +a.value <= 1)) b.value = a.value;
		steps[k].push(b);
	}
	return steps;
}

// The other way: a driveTo step's PathAt (counted along step k) as a PathAt of the whole path.
function joinFromDriveTo(a, k, m) {
	const s = m.partStartIn[k], L = (k + 1 < m.parts ? m.partStartIn[k + 1] : m.total) - s;
	const b = { ...a };
	if (a.kind === 'inchesDone') b.value = a.value + s;
	else if (a.kind === 'inchesLeft') b.value = a.value + (m.total - s - L);
	else if (a.kind === 'fraction') b.value = m.total > 0 && a.value >= 0 && a.value <= 1 ? (s + a.value * L) / m.total : a.value;
	else if (a.kind === 'part') b.value = a.value === 1 ? k + 1 : a.value;
	return b;
}

// ------------------------------------------------------------------ Java out

const camel = (s) => s.replace(/[^A-Za-z0-9 ]/g, ' ').trim();
/** A Java constant name from a label: "To the goal" -> TO_THE_GOAL. */
export function constName(label) {
	const c = camel(label || '').toUpperCase().split(/\s+/).filter(Boolean).join('_');
	return c ? (/^[0-9]/.test(c) ? 'P_' + c : c) : 'MY_PATH';
}
export const javaString = (s) => '"' + String(s ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"';

function headingCode(h) {
	switch (h.mode) {
		case 'keep': return `.keepHeading(${num(h.a)})`;
		case 'turn': return `.turnTo(${num(h.a)}, ${num(h.b)})`;
		case 'back': return '.facePathBackwards()';
		case 'point': return `.facePoint(${num(h.x)}, ${num(h.y)})`;
		default: return '';   // facePath() is the default
	}
}

function partCode(path, i) {
	const pts = partPoints(path, i);
	const xy = pts.map((p) => `${num(p[0])}, ${num(p[1])}`).join(',  ');
	const make = pts.length === 2 ? `PedroPath.line(${xy})` : `PedroPath.curve(${xy})`;
	const part = path.parts[i];
	return make + headingCode(part.heading) + (part.maxSpeed != null ? `.maxSpeed(${num(part.maxSpeed)})` : '');
}

/**
 * The Java for the path. opts: { label, name, start: { on, note, h }, mode: 'follow' | 'driveTo',
 * power (driveTo's maxPower), actions (PathAt, see KINDS), timeoutSec (follow's time limit, or null) }.
 * Returns { constant, steps, all }.
 */
export function toJava(path, opts = {}) {
	const label = opts.label || 'My path';
	const name = opts.name || constName(label);
	const start = opts.start || { on: false };
	const lines = [];
	const actions = opts.actions || [];
	// A step's PathAt lines: one a line, lined up under the step's first argument (\u0001 = that indent).
	const along = (list) => list.map((a) => ',\n\u0001' + pathAtCode(a)).join('');
	if (start.on) lines.push(`s.start(${javaString(start.note || 'Place the robot here')}, ${num(path.start[0])}, ${num(path.start[1])}, ${num(start.h || 0)})`);
	let constant = '';
	if (opts.mode === 'driveTo') {
		const samples = sample(path);
		const power = opts.power ?? 0.7;
		const split = splitForDriveTo(actions, path);
		path.parts.forEach((part, i) => {
			const h = partEnds(samples, i).end;
			const p = part.maxSpeed != null ? Math.min(power, part.maxSpeed) : power;
			const stepLabel = path.parts.length === 1 ? label : `${label} ${i + 1}`;
			lines.push(`.driveTo(${javaString(stepLabel)}, ${num(part.end[0])}, ${num(part.end[1])}, ${num(h)}, ${num(p)}${along(split[i])})`);
		});
	} else if (path.parts.length) {
		const body = path.parts.map((_, i) => (i === 0 ? partCode(path, i) : `.then(${partCode(path, i)})`));
		if (path.holdSec > 0) body.push(path.holdSec === DEFAULT_HOLD_SEC ? '.holdEnd()' : `.holdEnd(${num(path.holdSec)})`);
		constant = `static final PedroPath ${name} = ` + body.join('\n        ') + ';';
		const limit = opts.timeoutSec != null && opts.timeoutSec !== '' && Number.isFinite(+opts.timeoutSec) ? `, ${num(+opts.timeoutSec)}` : '';
		lines.push(`.follow(${javaString(label)}, ${name}${limit}${along(ordered(actions, measure(path)))})`);
	}
	let steps = lines.map((l, i) => (i === 0 && l.startsWith('.') ? 's' + l : (i === 0 ? l : ' ' + l)))
		.map((l) => l.replace(/\u0001/g, ' '.repeat(l.indexOf('(') + 1))).join('\n');
	if (steps) steps += ';';
	const all = (constant ? '// With your other fields, at the top of the class:\n' + constant + '\n\n' : '')
		+ '// In steps(Steps s, Robot robot):\n' + steps;
	return { constant, steps, all };
}

// ------------------------------------------------------------------ Java in

// Tokens keep where they are in the source (at, end), so code that is not numbers (a command like
// robot.intake::in) can be copied back out as written. Comments become spaces of the same length.
export function tokenize(src) {
	src = src.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (c) => c.replace(/[^\n]/g, ' '));
	const out = [];
	const re = /\s+|("(?:[^"\\]|\\.)*")|([A-Za-z_$][\w$]*)|(\d+\.?\d*(?:[eE][+-]?\d+)?[dDfF]?|\.\d+(?:[eE][+-]?\d+)?[dDfF]?)|(.)/g;
	let m;
	while ((m = re.exec(src))) {
		const pos = { at: m.index, end: m.index + m[0].length };
		if (m[1]) out.push({ s: JSON.parse(m[1].replace(/\\'/g, "'")), ...pos });
		else if (m[2]) out.push({ id: m[2], ...pos });
		else if (m[3]) out.push({ n: parseFloat(m[3].replace(/[dDfF]$/, '')), ...pos });
		else if (m[4]) out.push({ p: m[4], ...pos });
	}
	out.src = src;
	return out;
}

// Reads the arguments of a call that starts at tokens[i] = '('. Plain number math only (-56, 30 + 6,
// 0.5 * 2); anything else (a name, a call) is kept as its text so the importer can say what it could not read.
export function readArgs(tk, i) {
	const args = [];
	let depth = 0, cur = [];
	for (let k = i; k < tk.length; k++) {
		const t = tk[k];
		if (t.p === '(') { if (depth++ > 0) cur.push(t); continue; }
		if (t.p === ')') {
			if (--depth === 0) { if (cur.length) args.push(cur); return { args, next: k + 1 }; }
			cur.push(t); continue;
		}
		if (t.p === ',' && depth === 1) { args.push(cur); cur = []; continue; }
		cur.push(t);
	}
	throw new Error('a ( with no )');
}

export function evalNum(toks) {
	const text = toks.map((t) => t.n ?? t.p ?? t.id ?? JSON.stringify(t.s)).join('');
	if (toks.some((t) => t.id === 'new' && toks.some((u) => u.id === 'FieldPose'))) return { pose: toks };
	if (!toks.length || toks.some((t) => t.id || t.s != null || (t.p && !'+-*/()'.includes(t.p)))) return { bad: text };
	// eslint-disable-next-line no-new-func
	const v = Function('"use strict";return (' + text + ')')();
	return Number.isFinite(v) ? { v } : { bad: text };
}

// Splits a follow/driveTo step's arguments: the plain ones, then the PathAt ones (read as actions).
function readAlong(args, tk, problems) {
	const text = (toks) => (toks.length ? tk.src.slice(toks[0].at, toks[toks.length - 1].end).replace(/\s*\n\s*/g, ' ').trim() : '');
	const k = args.findIndex((a) => a[0]?.id === 'PathAt' && a[1]?.p === '.');
	const plain = k < 0 ? args : args.slice(0, k), actions = [];
	for (const arg of k < 0 ? [] : args.slice(k)) {
		const kind = arg[2]?.id;
		if (!(arg[0]?.id === 'PathAt' && arg[1]?.p === '.' && KINDS.includes(kind) && arg[3]?.p === '(')) {
			problems.push(`${text(arg)}: could not read it. Use PathAt.inchesDone, inchesLeft, fraction, part or near.`);
			continue;
		}
		const { args: inner } = readArgs(arg, 3);
		const nNums = kind === 'near' ? 3 : 1;
		const what = `PathAt.${kind}`;
		if (inner.length !== nNums + 1 && inner.length !== nNums + 2) {
			problems.push(`${what}: give ${kind === 'near' ? 'x, y, how close' : 'a number'}, then a name and a command (or an action). Found ${inner.length} things in ${text(arg)}.`);
			continue;
		}
		const nums = inner.slice(0, nNums).map(evalNum);
		const bad = nums.find((v) => v.bad != null);
		if (bad) { problems.push(`${what}: could not read "${bad.bad}". Use plain numbers.`); continue; }
		const rest = inner.slice(nNums);
		const a = { kind, value: nums[nNums - 1].v, what: rest.length === 2 ? 'command' : 'action', label: '', code: text(rest[rest.length - 1]) };
		if (kind === 'near') { a.x = nums[0].v; a.y = nums[1].v; a.value = nums[2].v; }
		if (rest.length === 2) {
			if (rest[0].length === 1 && rest[0][0].s != null) a.label = rest[0][0].s;
			else { problems.push(`${what}: write the name in quotes, e.g. "Spin up" (found ${text(rest[0])}).`); a.label = text(rest[0]); }
		}
		actions.push(a);
	}
	return { plain, actions };
}

/**
 * Reads Java with PedroPath paths (or driveTo steps) and gives back what it found:
 * { paths: [{ name, label, path, actions, timeoutSec }], start: { note, x, y, h } | null,
 *   driveTo: { label, power, path, actions } | null, problems: [text], notes: [text] }.
 * actions: the follow step's PathAt lines (see KINDS); timeoutSec: its time limit, or null.
 * Each PedroPath.line/curve that does not follow ".then(" starts a new path. A path's settings apply
 * to its last part so far, as in the library.
 */
export function fromJava(src) {
	const tk = tokenize(src);
	const paths = [], problems = [], notes = [];
	let start = null, cur = null, lastName = null;
	const followLabels = {};
	const drives = [];
	const need = (args, n, what) => {
		const vals = args.map(evalNum);
		const bad = vals.find((v) => v.bad != null);
		if (bad) { problems.push(`${what}: could not read "${bad.bad}". Use plain numbers.`); return null; }
		if (n != null && vals.length !== n) { problems.push(`${what}: needs ${n} numbers, found ${vals.length}.`); return null; }
		return vals.map((v) => v.v);
	};
	for (let i = 0; i < tk.length; i++) {
		const t = tk[i];
		// static final PedroPath NAME =
		if (t.id === 'PedroPath' && tk[i + 1]?.id && tk[i + 2]?.p === '=') { lastName = tk[i + 1].id; continue; }
		if (t.id === 'PedroPath' && tk[i + 1]?.p === '.' && (tk[i + 2]?.id === 'line' || tk[i + 2]?.id === 'curve')) {
			const kind = tk[i + 2].id;
			const { args, next } = readArgs(tk, i + 3);
			// ".then(" right before: after a path's own code (".then(" follows ")"), or after a path's
			// name (NAME.then(...): a new path made of NAME's parts and this one).
			const chained = tk[i - 1]?.p === '(' && tk[i - 2]?.id === 'then' && tk[i - 3]?.p === '.';
			const onName = chained && tk[i - 4]?.id ? tk[i - 4].id : null;
			const before = problems.length;
			let pts = null;
			if (kind === 'line' && args.length === 2) {
				// line(new FieldPose(x, y, h), new FieldPose(...)): a line turning from one heading to the other.
				const poses = args.map((a) => {
					const k = a.findIndex((u) => u.p === '(');
					return k < 0 || !a.some((u) => u.id === 'FieldPose') ? null : need(readArgs(a, k).args, 3, 'FieldPose');
				});
				if (poses.every(Boolean)) {
					pts = { xy: [[poses[0][0], poses[0][1]], [poses[1][0], poses[1][1]]], turn: [poses[0][2], poses[1][2]] };
				}
			} else {
				const v = need(args, kind === 'line' ? 4 : null, `PedroPath.${kind}`);
				if (v && kind === 'curve' && (v.length < 6 || v.length % 2)) problems.push('PedroPath.curve: give x, y pairs: start, at least one control point, end.');
				else if (v) pts = { xy: Array.from({ length: v.length / 2 }, (_, k) => [v[2 * k], v[2 * k + 1]]) };
			}
			i = next - 1;
			if (!pts) {
				// Never leave a part out without saying so.
				const text = args.map((a) => a.map((u) => u.n ?? u.p ?? u.id ?? JSON.stringify(u.s)).join('')).join(', ');
				if (problems.length === before) problems.push(`PedroPath.${kind}(${text}): could not read it. Use plain numbers, or new FieldPose(x, y, heading) for a line.`);
				problems.push(`PedroPath.${kind}(${text}) was left out, so ${chained && cur ? cur.name : 'its path'} is not complete.`);
				if (!chained) cur = null;   // its settings (.turnTo...) must not land on another path's part
				lastName = null;
				continue;
			}
			const part = { ctrl: pts.xy.slice(1, -1), end: pts.xy[pts.xy.length - 1],
				heading: pts.turn ? { mode: 'turn', a: pts.turn[0], b: pts.turn[1] } : { mode: 'path' }, maxSpeed: null };
			let base = null;     // the path this part goes after
			if (onName) {
				const named = paths.find((p) => p.name === onName);
				if (named) {
					// PedroPath never changes: NAME.then(...) is a new path; NAME stays as it was.
					base = { name: lastName || `Path ${paths.length + 1}`, label: null, path: JSON.parse(JSON.stringify(named.path)) };
					paths.push(base);
				} else {
					problems.push(`${onName}.then(...): paste ${onName}'s code too, so its parts can be read.`);
				}
			} else if (chained && cur) base = cur;
			if (base) {
				const e = base.path.parts.length ? base.path.parts[base.path.parts.length - 1].end : base.path.start;
				if (Math.hypot(e[0] - pts.xy[0][0], e[1] - pts.xy[0][1]) > 0.5) {
					problems.push(`${base.name}: part ${base.path.parts.length + 1} starts at (${num(pts.xy[0][0])}, ${num(pts.xy[0][1])}), not where part ${base.path.parts.length} ends (${num(e[0])}, ${num(e[1])}). The library stops INIT on this.`);
				}
				base.path.parts.push(part);
				cur = base;
			} else {
				cur = { name: lastName || `Path ${paths.length + 1}`, label: null, path: { start: pts.xy[0], parts: [part], holdSec: 0 } };
				paths.push(cur);
			}
			lastName = null;
			continue;
		}
		// .method(args) on the current path
		if (t.p === '.' && tk[i + 1]?.id && tk[i + 2]?.p === '(' && cur) {
			const m = tk[i + 1].id;
			const last = cur.path.parts[cur.path.parts.length - 1];
			const known = ['turnTo', 'keepHeading', 'facePath', 'facePathBackwards', 'facePoint', 'maxSpeed', 'holdEnd'];
			if (known.includes(m)) {
				const { args, next } = readArgs(tk, i + 2);
				i = next - 1;
				const want = { turnTo: 2, keepHeading: 1, facePath: 0, facePathBackwards: 0, facePoint: 2, maxSpeed: 1, holdEnd: null }[m];
				const v = need(args, want, `.${m}`);
				if (!v) continue;
				if (m === 'turnTo') last.heading = { mode: 'turn', a: v[0], b: v[1] };
				else if (m === 'keepHeading') last.heading = { mode: 'keep', a: v[0] };
				else if (m === 'facePath') last.heading = { mode: 'path' };
				else if (m === 'facePathBackwards') last.heading = { mode: 'back' };
				else if (m === 'facePoint') last.heading = { mode: 'point', x: v[0], y: v[1] };
				else if (m === 'maxSpeed') {
					if (v[0] > 0 && v[0] <= 1) last.maxSpeed = v[0];
					else problems.push(`.maxSpeed(${num(v[0])}): give a part of the top speed, more than 0 and at most 1.`);
				} else if (m === 'holdEnd') cur.path.holdSec = v.length ? v[0] : DEFAULT_HOLD_SEC;
				continue;
			}
		}
		// s.start("note", x, y, h): only on the steps object (s or steps), not any other .start(...)
		if (t.p === '.' && tk[i + 1]?.id === 'start' && tk[i + 2]?.p === '(' && /^(s|steps)$/.test(tk[i - 1]?.id ?? '') && tk[i - 2]?.p !== '.') {
			const { args, next } = readArgs(tk, i + 2);
			if (args.length === 4 && args[0].length === 1 && args[0][0].s != null) {
				const v = need(args.slice(1), 3, 's.start');
				if (v) start = { note: args[0][0].s, x: v[0], y: v[1], h: v[2] };
				i = next - 1;
			}
			continue;
		}
		// .follow("label", NAME[, timeoutSec][, PathAt...])
		if (t.p === '.' && tk[i + 1]?.id === 'follow' && tk[i + 2]?.p === '(') {
			const { args: all } = readArgs(tk, i + 2);
			const { plain: args, actions } = readAlong(all, tk, problems);
			let timeoutSec = null;
			if (args.length >= 3) {
				const v = need(args.slice(2, 3), 1, '.follow time limit');
				if (v) timeoutSec = v[0];
			}
			const info = { label: args[0]?.length === 1 && args[0][0].s != null ? args[0][0].s : null, timeoutSec, actions };
			if (info.label != null && args[1]?.length === 1 && args[1][0].id) followLabels[args[1][0].id] = info;
			else if (info.label != null) followLabels['\u0000next'] = info;
			continue;
		}
		// .driveTo("label", x, y, h, power[, tolerance])
		if (t.p === '.' && tk[i + 1]?.id === 'driveTo' && tk[i + 2]?.p === '(') {
			const { args: all, next } = readArgs(tk, i + 2);
			const { plain: args, actions } = readAlong(all, tk, problems);
			if (args[0]?.length === 1 && args[0][0].s != null && (args.length === 5 || args.length === 6)) {
				const v = need(args.slice(1), null, '.driveTo');
				if (v) drives.push({ label: args[0][0].s, x: v[0], y: v[1], h: v[2], power: v[3], actions });
				for (const a of actions) {
					if (a.kind === 'part' && a.value > 1) problems.push(`Steps: driveTo("${args[0][0].s}"): PathAt.part(${num(a.value)}), but the path has 1 part (driveTo is one straight line)`);
				}
			}
			i = next - 1;
		}
	}
	const take = (p, f) => { p.label = f?.label ?? null; p.actions = f?.actions ?? []; p.timeoutSec = f?.timeoutSec ?? null; };
	for (const p of paths) take(p, followLabels[p.name]);
	// An inline path (.follow("label", PedroPath...)) takes the label written before it.
	if (paths.length && !paths[0].label && followLabels['\u0000next']) take(paths[0], followLabels['\u0000next']);
	let driveTo = null;
	if (drives.length) {
		// A driveTo chain as lines: each turns from the heading before to its own (driveTo turns on the way).
		const from = start ? [start.x, start.y] : null;
		if (!from) problems.push('driveTo steps need an s.start(...) line to know where the first one starts.');
		else {
			let h = start.h;
			const parts = drives.map((d) => {
				const part = { ctrl: [], end: [d.x, d.y], heading: { mode: 'turn', a: h, b: d.h }, maxSpeed: null };
				h = d.h;
				return part;
			});
			const base = drives[0].label.replace(/ \d+$/, '');
			driveTo = { label: base, power: drives[0].power, path: { start: from, parts, holdSec: 0 } };
			// Each step's PathAt counts along that step: as places along the whole path.
			const m = measure(straightPath(driveTo.path));
			driveTo.actions = drives.flatMap((d, k) => d.actions.map((a) => joinFromDriveTo(a, k, m)));
		}
		if (paths.length) notes.push(`Also found ${drives.length} driveTo step${drives.length > 1 ? 's' : ''}; the PedroPath was read.`);
	}
	if (!paths.length && !driveTo && !problems.length) problems.push('No PedroPath.line(...), PedroPath.curve(...) or driveTo(...) found.');
	return { paths, start, driveTo, problems, notes };
}
