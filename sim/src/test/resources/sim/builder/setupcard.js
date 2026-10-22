// The setup card: one page for the drive team at match setup (print it, or open it on a phone): the
// auto's name, where the robot goes in words (the same note the Driver Station shows at INIT as
// "Place robot : ..."), the field from above with the robot's start footprint and an arrow for its
// heading, red and blue, the start delay and the first step. Both builders use it.
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
