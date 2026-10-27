// Drawing the field from above, shared by both builders (app.js: Pedro paths; steps.js: simple steps).
// SVG y is down, the field's y is up: every y is drawn as -y.
import { footprint } from './pathcore.js';

const SVGNS = 'http://www.w3.org/2000/svg';

export const el = (tag, attrs = {}, parent) => {
	const e = document.createElementNS(SVGNS, tag);
	for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
	if (parent) parent.appendChild(e);
	return e;
};
export const pts = (list) => list.map(([x, y]) => `${x.toFixed(2)},${(-y).toFixed(2)}`).join(' ');

const BIOBUZZ_LABELS = [[0, -20, 'HIVE'], [0, 20, 'HIVE'], [23, -63, 'FLOWER'], [-68, -23, 'FLOWER'], [-23, 63, 'FLOWER'], [68, 23, 'FLOWER']];

/** The tiles, the field parts (from the simulator's field file, or none yet) and the labels. */
export function drawField(g, field) {
	// The real field: inside the walls 141.17 in (the field file), 6 tiles of 23.53 in. Each seam is drawn
	// with the band where the tiles' teeth interlock (0.4 in each side, from the field CAD).
	const half = field?.field?.insideWallSize ? field.field.insideWallSize / 2 : 70.585, tile = 2 * half / 6, teeth = 0.4;
	el('rect', { x: -half, y: -half, width: 2 * half, height: 2 * half, fill: '#e9ebee', stroke: '#555', 'stroke-width': 0.5 }, g);
	for (let k = 1; k < 6; k++) {
		const v = -half + k * tile;
		el('rect', { x: v - teeth, y: -half, width: 2 * teeth, height: 2 * half, fill: '#c9ccd3' }, g);
		el('rect', { x: -half, y: v - teeth, width: 2 * half, height: 2 * teeth, fill: '#c9ccd3' }, g);
		el('line', { x1: v, y1: -half, x2: v, y2: half, stroke: '#9aa0aa', 'stroke-width': 0.2 }, g);
		el('line', { x1: -half, y1: v, x2: half, y2: v, stroke: '#9aa0aa', 'stroke-width': 0.2 }, g);
	}
	for (let v = -72; v <= 72; v += 24) {   // inch numbers (where the robot is), not the tile seams
		el('text', { x: v, y: 77, 'font-size': 3.6, 'text-anchor': 'middle', fill: '#333' }, g).textContent = v;
		el('text', { x: -73.5, y: -v + 1.2, 'font-size': 3.6, 'text-anchor': 'end', fill: '#333' }, g).textContent = v;
	}
	if (!field) return;
	const order = { zone: 0, line: 1, solid: 2, overhead: 3 };
	const els = field.elements.filter((e) => e.kind in order && !e.polygon.some(([x, y]) => Math.abs(x) > 73 || Math.abs(y) > 73))
		.sort((a, b) => order[a.kind] - order[b.kind]);
	for (const e of els) {
		let p;
		if (e.kind === 'zone') p = el('polygon', { points: pts(e.polygon), fill: e.color, 'fill-opacity': 0.3 }, g);
		else if (e.kind === 'line') p = el('polygon', { points: pts(e.polygon), fill: e.color, 'fill-opacity': 0.9 }, g);
		else if (e.kind === 'overhead') p = el('polygon', { points: pts(e.polygon), fill: e.color, 'fill-opacity': 0.12, stroke: e.color, 'stroke-width': 0.4, 'stroke-dasharray': '1.5 1' }, g);
		else p = el('polygon', { points: pts(e.polygon), fill: e.color, 'fill-opacity': e.name.endsWith(' lip') ? 0.35 : 0.9 }, g);
		el('title', {}, p).textContent = e.name + (e.kind === 'overhead' ? ' (above the robot)' : '');
	}
	if (/BIOBUZZ/.test(field.season)) {
		for (const [x, y, t] of BIOBUZZ_LABELS) {
			el('text', { x, y: -y, 'font-size': 3.4, 'text-anchor': 'middle', fill: '#222', 'font-weight': 700, 'paint-order': 'stroke', stroke: '#fff', 'stroke-width': 0.8 }, g).textContent = t;
		}
	}
	el('text', { x: 0, y: 83, 'font-size': 3.8, 'text-anchor': 'middle', fill: '#c8322a', 'font-weight': 700 }, g)
		.textContent = 'Your drivers stand here';
	el('text', { x: 0, y: -75, 'font-size': 3.4, 'text-anchor': 'middle', fill: '#555' }, g).textContent = 'far wall (other alliance)';
}

/** The robot at s = { x, y, h }: its footprint and a triangle at the front. robot = [length, width]. */
export function drawRobot(g, s, color, fillOpacity, strokeWidth, robot) {
	const fp = footprint(s.x, s.y, s.h, robot[0], robot[1]);
	el('polygon', { points: pts(fp), fill: color, 'fill-opacity': fillOpacity, stroke: color, 'stroke-width': strokeWidth }, g);
	const r = s.h * Math.PI / 180, f = [-Math.sin(r), Math.cos(r)], side = [Math.cos(r), Math.sin(r)];
	const L = robot[0] / 2;
	const tip = [s.x + f[0] * L, s.y + f[1] * L];
	const b1 = [s.x + f[0] * (L - 4) + side[0] * 3, s.y + f[1] * (L - 4) + side[1] * 3];
	const b2 = [s.x + f[0] * (L - 4) - side[0] * 3, s.y + f[1] * (L - 4) - side[1] * 3];
	el('polygon', { points: pts([tip, b1, b2]), fill: color }, g);
}

/** A handle about 9 screen pixels across, whatever the map's size. */
export function handleRadius(svg) {
	const w = svg.getBoundingClientRect().width || 600;
	return Math.max(1.4, 9 * 164 / w / 2);
}

/** A pointer event's place on the field, inches. */
export function toField(svg, evt) {
	const pt = svg.createSVGPoint();
	pt.x = evt.clientX; pt.y = evt.clientY;
	const p = pt.matrixTransform(svg.getScreenCTM().inverse());
	return [p.x, -p.y];
}
