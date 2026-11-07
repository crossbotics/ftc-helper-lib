// The setup card: one page for the drive team at match setup (print it, or open it on a phone): the
// auto's name, where the robot goes in words (the same note the Driver Station shows at INIT as
// "Place robot : ..."), the field from above with the robot's start footprint and an arrow for its
// heading, red and blue, the start delay and the first step. Both builders use it.
// The auto sheet (openAutoSheet) is its bigger brother, for other teams: the whole route and every step.
import { footprint } from './pathcore.js';
import { el, pts, drawField } from './mapdraw.js';

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const n1 = (v) => String(Math.round(v * 10) / 10);

/**
 * Opens the card over the page. c: { title, note, x, y, h, robot: [L, W], mirror, spot (the code
 * gives x, y), delay (seconds, or null to leave it out), first (the first step in words), warn (text or '') , field }.
 */
export function openSetupCard(c) {
	let box = document.getElementById('setupCard');
	if (!box) {
		box = document.createElement('div');
		box.id = 'setupCard';
		box.setAttribute('role', 'dialog');
		box.setAttribute('aria-modal', 'true');
		box.setAttribute('aria-label', 'Setup card');
		document.body.appendChild(box);
	}
	const alliance = c.mirror
		? 'written for BLUE. On RED the spot is mirrored: left and right swap, and the heading turns the other way.'
		: 'the same spot for both, seen from your own drivers\' wall (the field is turned half a turn between the alliances).';
	box.innerHTML = `
		<div class="sc-page">
			<div class="sc-tools"><button class="primary" data-print>Print</button><button data-close>Close</button></div>
			<p class="sc-kicker">Setup card</p>
			<h2 class="sc-title">${esc(c.title)}</h2>
			<p class="sc-label">Place robot</p>
			<p class="sc-note">${esc(c.note || '(no note yet)')}</p>
			${c.warn ? `<p class="sc-warn">${esc(c.warn)}</p>` : ''}
			<div class="sc-grid">
				<svg class="sc-map" viewBox="-82 -80 164 168" role="img" aria-label="The field from your drivers' wall, with the robot's start spot"></svg>
				<div class="sc-facts">
					<p><b>Red and blue:</b> ${esc(alliance)}</p>
					${c.delay != null ? `<p><b>Start delay:</b> ${esc(n1(c.delay))} s (set in the INIT menu)</p>` : ''}
					${c.first ? `<p><b>First step:</b> ${esc(c.first)}</p>` : ''}
					<p class="sc-small">Spot: x ${esc(n1(c.x))} in, y ${esc(n1(c.y))} in, heading ${esc(n1(c.h))}° (from the field's centre, seen from your wall; 0° = facing away, + = left).${c.spot === false ? ' The code gives no spot: this is the planned one.' : ''}</p>
					<ol class="sc-check">
						<li>Choose the auto, press INIT, and read the menu out loud.</li>
						<li>Check the Driver Station says <b>Place robot : ${esc(c.note || '')}</b></li>
						<li>Place the robot; a second person checks it against this card.</li>
					</ol>
				</div>
			</div>
		</div>`;
	const svg = box.querySelector('svg');
	drawField(el('g', {}, svg), c.field);
	const g = el('g', {}, svg);
	const fp = footprint(c.x, c.y, c.h, c.robot[0], c.robot[1]);
	el('polygon', { points: pts(fp), fill: '#101c45', 'fill-opacity': 0.25, stroke: '#101c45', 'stroke-width': 0.8 }, g);
	// The heading: an arrow from the centre out past the front.
	const r = c.h * Math.PI / 180, f = [-Math.sin(r), Math.cos(r)], s = [Math.cos(r), Math.sin(r)];
	const L = c.robot[0] / 2 + 6;
	const tip = [c.x + f[0] * L, c.y + f[1] * L];
	el('line', { x1: c.x, y1: -c.y, x2: tip[0] - f[0] * 3, y2: -(tip[1] - f[1] * 3), stroke: '#c8322a', 'stroke-width': 1.4 }, g);
	el('polygon', { points: pts([tip, [tip[0] - f[0] * 4 + s[0] * 2.4, tip[1] - f[1] * 4 + s[1] * 2.4], [tip[0] - f[0] * 4 - s[0] * 2.4, tip[1] - f[1] * 4 - s[1] * 2.4]]), fill: '#c8322a' }, g);
	el('circle', { cx: c.x, cy: -c.y, r: 1.2, fill: '#c8322a' }, g);
	const close = () => { box.remove(); document.body.classList.remove('carding'); };
	box.querySelector('[data-close]').onclick = close;
	box.querySelector('[data-print]').onclick = () => window.print();
	box.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
	document.body.classList.add('carding');
	box.querySelector('[data-print]').focus();
}

// ------------------------------------------------------------------ the auto sheet

/**
 * The step numbers to draw on the map: steps that end within `near` inches of each other share one marker
 * ("2, 3"), so a turn or an action at the same spot does not cover the drive before it.
 * steps: [{ x, y }] in order. Returns [{ x, y, nums: [1, 2] }] (numbers count from 1).
 */
export function sheetMarkers(steps, near = 3) {
	const out = [];
	steps.forEach((s, i) => {
		if (!Number.isFinite(+s.x) || !Number.isFinite(+s.y)) return;
		const m = out.find((o) => Math.hypot(o.x - s.x, o.y - s.y) <= near);
		if (m) m.nums.push(i + 1);
		else out.push({ x: +s.x, y: +s.y, nums: [i + 1] });
	});
	return out;
}

/**
 * The poses to outline: where each step that moves or turns the robot ends ([{ x, y, h, step }], step counts
 * from 1). A step that leaves the robot where it was (a wait, an action) adds none.
 */
export function sheetOutlines(steps, start) {
	const out = [];
	let prev = start;
	steps.forEach((s, i) => {
		if (!Number.isFinite(+s.x) || !Number.isFinite(+s.y)) return;
		const h = Number.isFinite(+s.h) ? +s.h : prev.h;
		const dh = Math.abs(((h - prev.h) % 360 + 540) % 360 - 180);
		if (Math.hypot(s.x - prev.x, s.y - prev.y) > 0.5 || dh > 1) out.push({ x: +s.x, y: +s.y, h, step: i + 1 });
		prev = { x: +s.x, y: +s.y, h };
	});
	return out;
}

/** Step numbers as short text: [1, 2, 3, 5] -> "1-3, 5". */
export function numsLabel(nums) {
	const parts = [];
	for (let i = 0; i < nums.length; i++) {
		let j = i;
		while (j + 1 < nums.length && nums[j + 1] === nums[j] + 1) j++;
		parts.push(j - i >= 2 ? `${nums[i]}-${nums[j]}` : j > i ? `${nums[i]}, ${nums[j]}` : `${nums[i]}`);
		i = j;
	}
	return parts.join(', ');
}

/**
 * Where to draw each marker's label so no two overlap: [{ ...marker, w (label width), lx, ly (label centre),
 * moved (true when it had to move off its spot: draw a thin line back to it) }]. A label that would cover one
 * already placed moves down, then up, in label-height steps.
 */
export function placeMarkers(markers) {
	const H = 5.6, placed = [], EDGE = 70;
	// Kept inside the field, so a long label never runs off the map.
	const inside = (x, w) => Math.max(-EDGE + w / 2, Math.min(EDGE - w / 2, x));
	const hits = (a, b) => Math.abs(a.lx - b.lx) < (a.w + b.w) / 2 + 0.6 && Math.abs(a.ly - b.ly) < H + 0.6;
	for (const m of markers) {
		const label = numsLabel(m.nums);
		const w = Math.max(5, 2.6 * label.length + 2.4);
		const lx = inside(m.x, w);
		let p = { ...m, label, w, lx, ly: m.y, moved: lx !== m.x };
		// Kept inside the field top to bottom too: a label near the top or bottom wall moves inward.
		const okY = (y) => y - H / 2 >= -EDGE && y + H / 2 <= EDGE;
		for (let k = 1; k <= 24 && (placed.some((q) => hits(p, q)) || !okY(p.ly)); k++) {
			const off = Math.ceil(k / 2) * (H + 1) * (k % 2 ? -1 : 1);
			p = { ...m, label, w, lx, ly: m.y + off, moved: true };
		}
		placed.push(p);
	}
	return placed;
}

/** "0.0-1.8 s" for a step's planned time (one number when it takes no time). */
export function sheetTime(t0, t1) {
	if (!Number.isFinite(+t0) || !Number.isFinite(+t1)) return '';
	return Math.abs(t1 - t0) < 0.05 ? `${n1(t0)} s` : `${n1(t0)}-${n1(t1)} s`;
}

/**
 * Opens the auto sheet over the page: one page to explain an autonomous to another team (print it, or save it
 * as a PDF from the print dialog). The field from above with the start spot, the whole route and a number at
 * the end of each step, and the steps in words with their planned times. Black and white friendly.
 * c: { title, note, x, y, h (the start), robot: [L, W], mirror, field, warn,
 *      route: [[x, y], ...], steps: [{ text, t0, t1, x, y, h }], total (seconds), kind ('steps' | 'path') }.
 */
export function openAutoSheet(c) {
	let box = document.getElementById('setupCard');
	if (!box) {
		box = document.createElement('div');
		box.id = 'setupCard';
		box.setAttribute('role', 'dialog');
		box.setAttribute('aria-modal', 'true');
		box.setAttribute('aria-label', 'Auto sheet');
		document.body.appendChild(box);
	}
	const alliance = c.mirror
		? 'drawn for BLUE. On RED it is mirrored: left and right swap.'
		: 'the same route for both, seen from your own drivers\' wall (the field is turned half a turn between the alliances).';
	const steps = c.steps || [];
	const rows = steps.map((s, i) => `<tr><td style="padding:2px 8px 2px 0;font-weight:700;vertical-align:top">${i + 1}</td>`
		+ `<td style="padding:2px 8px 2px 0;vertical-align:top">${esc(s.text)}</td>`
		+ `<td style="padding:2px 0;vertical-align:top;white-space:nowrap;color:#4a5262">${esc(sheetTime(s.t0, s.t1))}</td></tr>`).join('');
	box.innerHTML = `
		<div class="sc-page">
			<div class="sc-tools"><button class="primary" data-print>Print or save as PDF</button><button data-close>Close</button></div>
			<p class="sc-kicker">Auto sheet</p>
			<h2 class="sc-title">${esc(c.title)}</h2>
			${c.warn ? `<p class="sc-warn">${esc(c.warn)}</p>` : ''}
			<style>#setupCard .sc-grid.sc-wide { grid-template-columns: minmax(0, 2.2fr) minmax(0, 1fr); }
				@media (max-width: 640px) { #setupCard .sc-grid.sc-wide { grid-template-columns: minmax(0, 1fr); } }</style>
			<div class="sc-grid sc-wide">
				<svg class="sc-map" viewBox="-80 -78 160 164" role="img" aria-label="The field from your drivers' wall, with the start spot, the route and the step numbers"></svg>
				<div class="sc-facts">
					<p><b>Start:</b> ${esc(c.note || '(no note yet)')}</p>
					<p><b>Red and blue:</b> ${esc(alliance)}</p>
					${Number.isFinite(+c.total) && steps.length ? `<p><b>Planned time:</b> about ${esc(n1(c.total))} s of the 30 s (a plan from the builder's speeds: the real robot may differ)</p>` : ''}
					<p class="sc-small">Grey box: the start, with an arrow for the front. Thin boxes: the robot after each step that moves it. Dashed box: where it ends. Line: the route. Numbers: where each step ends.</p>
				</div>
			</div>
			<p class="sc-label" style="margin-top:10px">${c.kind === 'path' ? 'The path' : 'The steps'}</p>
			${steps.length ? `<table style="border-collapse:collapse;width:100%;font-size:.95rem"><tbody>${rows}</tbody></table>` : '<p>(no steps yet)</p>'}
		</div>`;
	const svg = box.querySelector('svg');
	drawField(el('g', {}, svg), c.field);
	const g = el('g', {}, svg);
	// The start footprint and its front arrow (as on the setup card).
	el('polygon', { points: pts(footprint(c.x, c.y, c.h, c.robot[0], c.robot[1])), fill: '#6a7282', 'fill-opacity': 0.3, stroke: '#1d2330', 'stroke-width': 0.8 }, g);
	const r = c.h * Math.PI / 180, f = [-Math.sin(r), Math.cos(r)], s = [Math.cos(r), Math.sin(r)];
	const L = c.robot[0] / 2 + 6;
	const tip = [c.x + f[0] * L, c.y + f[1] * L];
	el('line', { x1: c.x, y1: -c.y, x2: tip[0] - f[0] * 3, y2: -(tip[1] - f[1] * 3), stroke: '#1d2330', 'stroke-width': 1.2 }, g);
	el('polygon', { points: pts([tip, [tip[0] - f[0] * 4 + s[0] * 2.4, tip[1] - f[1] * 4 + s[1] * 2.4], [tip[0] - f[0] * 4 - s[0] * 2.4, tip[1] - f[1] * 4 - s[1] * 2.4]]), fill: '#1d2330' }, g);
	// The route, then where it ends (the last pose), then the step numbers on top.
	const route = (c.route || []).filter((p) => Number.isFinite(p[0]) && Number.isFinite(p[1]));
	if (route.length > 1) el('polyline', { points: pts(route), fill: 'none', stroke: '#1d2330', 'stroke-width': 1, 'stroke-linejoin': 'round' }, g);
	// The robot's outline where each step that moves it ends (the last one dashed: where it ends).
	const outlines = sheetOutlines(steps, { x: c.x, y: c.y, h: c.h });
	outlines.forEach((o, k) => {
		const last = k === outlines.length - 1;
		el('polygon', { points: pts(footprint(o.x, o.y, o.h, c.robot[0], c.robot[1])), fill: 'none', stroke: '#1d2330',
			'stroke-width': last ? 0.6 : 0.4, 'stroke-opacity': last ? 1 : 0.6, ...(last ? { 'stroke-dasharray': '2 1.5' } : {}) }, g);
	});
	if (!outlines.length && c.end) el('polygon', { points: pts(footprint(c.end.x, c.end.y, c.end.h, c.robot[0], c.robot[1])), fill: 'none', stroke: '#1d2330', 'stroke-width': 0.6, 'stroke-dasharray': '2 1.5' }, g);
	for (const m of placeMarkers(sheetMarkers(steps))) {
		if (m.moved) el('line', { x1: m.x, y1: -m.y, x2: m.lx, y2: -m.ly, stroke: '#1d2330', 'stroke-width': 0.4 }, g);
		el('rect', { x: m.lx - m.w / 2, y: -m.ly - 2.8, width: m.w, height: 5.6, rx: 2.8, fill: '#1d2330', stroke: '#fff', 'stroke-width': 0.5 }, g);
		const t = el('text', { x: m.lx, y: -m.ly + 1.5, 'text-anchor': 'middle', 'font-size': 4.2, 'font-weight': 700, fill: '#fff', 'font-family': 'system-ui, sans-serif' }, g);
		t.textContent = m.label;
	}
	const close = () => { box.remove(); document.body.classList.remove('carding'); };
	box.querySelector('[data-close]').onclick = close;
	box.querySelector('[data-print]').onclick = () => window.print();
	box.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
	document.body.classList.add('carding');
	box.querySelector('[data-print]').focus();
}
