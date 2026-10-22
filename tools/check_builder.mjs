// Checks the simulator's auto builder page code (sim/src/test/resources/sim/builder). Run from the repo root:
//
//   node tools/check_builder.mjs
//
// It fails (exit 1) when:
//   1. the Java or the scenario for a shared fixture plan is not, letter for letter, what crossbotics.com's
//      web path builder wrote for it (sim/src/test/resources/builder/fixtures.json: plans and the web
//      builder's output, made with --write-fixtures);
//   2. simcore.js (the simulator-only part) gets something wrong: the plan as the Built Auto reads it,
//      plan <-> field numbers for both alliances, the field's neutral names, plan versus real, the fixes,
//      the Pedro scenario, the recorded scenario;
//   3. a copy of the shared files on the web site differs from the library's (only checked when a site
//      checkout is found: SITE_BUILDER_DIR, or ../crossbotics*/public/ftc-helper/path-builder; a warning
//      unless SITE_STRICT=1, since the site copies the library's files after a change).
//
// Which way the files go: the LIBRARY is the source of pathcore.js, stepscore.js, mapdraw.js and
// setupcard.js. Change them here, run this check, then copy them to the site (its
// scripts/sync-path-builder.mjs copies them, like the field file).
//
// To make the fixtures again (only when the output changes on purpose), from the library's own files
// (sim/src/test/resources/sim/builder: the source) or from the site's once it has copied them:
//   node tools/check_builder.mjs --write-fixtures sim/src/test/resources/sim/builder
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '..');
const dir = path.join(root, 'sim/src/test/resources/sim/builder');
const fixturesFile = path.join(root, 'sim/src/test/resources/builder/fixtures.json');
const SHARED = ['pathcore.js', 'stepscore.js', 'mapdraw.js', 'setupcard.js'];

let failed = 0, passed = 0;
const ok = (cond, what) => { if (cond) passed++; else { failed++; console.log('FAIL ' + what); } };
const near = (a, b, tol = 1e-6) => Math.abs(a - b) <= tol;

// ------------------------------------------------------------------ the fixture plans

// Plans that cover every step kind, both drives, mirroring, no spot; Pedro paths with every heading rule,
// curves, speed limits, a hold, a time limit and every PathAt kind, in follow and driveTo modes.
const STEP_PLANS = [
	{ name: 'lesson 6 example', plan: { drive: 'mecanum', mirror: false,
		start: { x: -56, y: -61.1, h: 0, note: 'Our wall, left corner tile, facing the field', spot: true },
		steps: [{ kind: 'run', label: 'Hold the piece', code: 'robot.claw::close' }, { kind: 'move', label: 'To the goal', inches: 24, dir: 0, power: 0.6 },
			{ kind: 'action', code: 'Actions.scoreHigh(robot)', timeout: 5 }, { kind: 'park', sec: 3 }, { kind: 'move', label: 'Park', inches: 20, dir: 90, power: 0.7 }],
		opmode: 'Score and Park', teleop: 'Quick Start TeleOp', pkg: 'org.firstinspires.ftc.teamcode', group: 'Autos' } },
	{ name: 'goBILDA starter bot', plan: { drive: 'tank', mirror: false,
		start: { x: -56, y: -62.95, h: 0, note: 'Our wall, left corner tile, facing the field', spot: true },
		steps: [{ kind: 'run', label: 'Spin up', code: 'robot.launcher::spinUp' }, { kind: 'move', label: 'Out from the wall', inches: 20, dir: 0, power: 0.6 },
			{ kind: 'wait', label: 'Settle', sec: 0.3 }, { kind: 'turn', label: 'Face the HIVE', deg: -45, power: 0.5 },
			{ kind: 'action', code: 'Actions.fireAll(robot)', timeout: 8 }, { kind: 'park', sec: 4 },
			{ kind: 'turnTo', label: 'Square up', heading: 0, power: 0.5 }, { kind: 'move', label: 'Leave', inches: 12, dir: 0, power: 0.7 }],
		opmode: 'Built Starter Auto', teleop: 'goBILDA Bot TeleOp', pkg: 'com.crossbotics.ftchelper.examples.gobildabot', group: 'Examples' } },
	{ name: 'mirrored, no spot, alongside', plan: { drive: 'mecanum', mirror: true,
		start: { x: 30, y: -61.1, h: 90, note: 'NEAR: "left" tile', spot: false },
		steps: [{ kind: 'alongside', code: 'Actions.fireAll(robot)' }, { kind: 'move', label: 'Strafe out', inches: 12.5, dir: 45, power: 1 },
			{ kind: 'turnTo', label: 'Face "in"', heading: -30, power: 0.4 }, { kind: 'wait', label: 'Wait', sec: 2.25 },
			{ kind: 'run', label: 'Lift', code: '() -> robot.lift.goTo("high")' }, { kind: 'move', label: 'Back', inches: -8, dir: 0, power: 0.5 }],
		opmode: '2 piece', teleop: 'Main TeleOp', pkg: 'com.team8938', group: 'Test', className: 'TwoPiece' } },
];
const PEDRO_PLANS = [
	{ name: 'Pedro example robot', st: {
		path: { start: [-56, -61], parts: [{ ctrl: [], end: [-48, -50], heading: { mode: 'keep', a: 0 }, maxSpeed: null },
			{ ctrl: [[-44, -36]], end: [-30, -30], heading: { mode: 'turn', a: 0, b: 45 }, maxSpeed: null }], holdSec: 5 },
		start: { on: true, note: 'Against our wall, in the left corner tile, facing the field', h: 0 }, label: 'To the goal', name: 'TO_GOAL',
		actions: [{ kind: 'fraction', value: 0.5, what: 'command', label: 'Intake on', code: 'robot.intake::in' }], timeoutSec: null, mode: 'follow', power: 0.7, mirror: false, opmode: 'Built Pedro Auto' } },
	{ name: 'every heading and PathAt', st: {
		path: { start: [40, -61], parts: [{ ctrl: [[50, -40], [20, -40]], end: [30, -20], heading: { mode: 'back' }, maxSpeed: 0.5 },
			{ ctrl: [], end: [10, -40], heading: { mode: 'point', x: 0, y: -20 }, maxSpeed: null },
			{ ctrl: [], end: [0, -50], heading: { mode: 'path' }, maxSpeed: 0.8 }], holdSec: 2.5 },
		start: { on: true, note: 'Right of our FLOWER, facing the field', h: 0 }, label: 'Around', name: '',
		actions: [{ kind: 'inchesLeft', value: 12, what: 'command', label: 'Spin up', code: 'robot.launcher::spinUp' },
			{ kind: 'inchesDone', value: 5, what: 'action', label: '', code: 'Actions.scoreHigh(robot)' },
			{ kind: 'part', value: 2, what: 'command', label: 'Part two', code: '() -> robot.lift.goTo("low")' },
			{ kind: 'near', value: 6, x: 12, y: -38, what: 'command', label: 'Near it', code: 'robot.intake::in' }],
		timeoutSec: 12, mode: 'follow', power: 0.7, mirror: true, opmode: 'Around' } },
	{ name: 'driveTo steps', st: {
		path: { start: [-56, -61], parts: [{ ctrl: [], end: [-48, -50], heading: { mode: 'keep', a: 0 }, maxSpeed: null },
			{ ctrl: [[-44, -36]], end: [-30, -30], heading: { mode: 'turn', a: 0, b: 45 }, maxSpeed: 0.5 }], holdSec: 0 },
		start: { on: false, note: 'Left corner', h: 0 }, label: 'Go', name: '',
		actions: [{ kind: 'fraction', value: 0.75, what: 'command', label: 'Intake on', code: 'robot.intake::in' }], timeoutSec: null, mode: 'driveTo', power: 0.6, mirror: false, opmode: 'Go' } },
];
const pedroOpts = (st) => ({ label: st.label || 'My path', name: st.name || undefined, start: st.start, mode: st.mode, power: st.power,
	actions: st.actions, timeoutSec: st.mode === 'follow' ? st.timeoutSec : null });

// The output of a web builder (its stepscore.js and pathcore.js) for every fixture plan.
function outputs(S, P) {
	return {
		steps: STEP_PLANS.map((f) => ({ name: f.name, java: S.toJava(f.plan), scenario: S.toScenario(f.plan, { tolIn: 4, tolDeg: 10, fileName: 'fixture.sim' }) })),
		pedro: PEDRO_PLANS.map((f) => ({ name: f.name, java: P.toJava(f.st.path, pedroOpts(f.st)).all })),
	};
}

const importFrom = (d, f) => import(pathToFileURL(path.join(d, f)).href);

if (process.argv[2] === '--write-fixtures') {
	const site = process.argv[3];
	if (!site || !fs.existsSync(path.join(site, 'stepscore.js'))) { console.log('Give the web builder\'s folder (with stepscore.js).'); process.exit(2); }
	const S = await importFrom(site, 'stepscore.js'), P = await importFrom(site, 'pathcore.js');
	const C = await importFrom(dir, 'simcore.js');
	const out = { from: 'the shared path builder files (' + path.relative(root, path.resolve(site)) + ')', made: new Date().toISOString().slice(0, 10), ...outputs(S, P),
		// The plans as the Built Auto reads them (simcore.toProgram): BuiltAutoTest parses each.
		programs: [...STEP_PLANS.map((f) => C.toProgram({ mode: 'steps', steps: { plan: f.plan } })), ...PEDRO_PLANS.map((f) => C.toProgram({ mode: 'pedro', pedro: f.st }))] };
	fs.mkdirSync(path.dirname(fixturesFile), { recursive: true });
	fs.writeFileSync(fixturesFile, JSON.stringify(out, null, 1) + '\n');
	console.log(`Wrote ${path.relative(root, fixturesFile)} from ${site}.`);
	process.exit(0);
}

const S = await importFrom(dir, 'stepscore.js');
const P = await importFrom(dir, 'pathcore.js');
const C = await importFrom(dir, 'simcore.js');

// ------------------------------------------------------------------ 1. the same output as the web builder

const fixtures = JSON.parse(fs.readFileSync(fixturesFile, 'utf8'));
const mine = outputs(S, P);
ok(fixtures.steps.length === STEP_PLANS.length && fixtures.pedro.length === PEDRO_PLANS.length, 'the fixtures have every plan (write them again with --write-fixtures)');
mine.steps.forEach((m, k) => {
	const f = fixtures.steps[k];
	ok(f && m.java.file === f.java.file, `steps "${m.name}": the Java file differs from the web builder's`);
	ok(f && m.java.steps === f.java.steps, `steps "${m.name}": the steps differ from the web builder's`);
	ok(f && m.scenario === f.scenario, `steps "${m.name}": the scenario differs from the web builder's`);
});
mine.pedro.forEach((m, k) => ok(fixtures.pedro[k] && m.java === fixtures.pedro[k].java, `Pedro "${m.name}": the Java differs from the web builder's`));
// The programs the Java test reads are what simcore writes now.
const programs = [...STEP_PLANS.map((f) => C.toProgram({ mode: 'steps', steps: { plan: f.plan } })), ...PEDRO_PLANS.map((f) => C.toProgram({ mode: 'pedro', pedro: f.st }))];
ok(JSON.stringify(programs) === JSON.stringify(fixtures.programs), 'the programs in fixtures.json are not what simcore.toProgram writes now (write the fixtures again)');

// ------------------------------------------------------------------ 2. simcore

// The program has the Java's calls: one step per plan step; a Pedro path is one follow, or a driveTo per part.
{
	const p0 = programs[0];
	ok(p0.mode === 'steps' && p0.steps.length === STEP_PLANS[0].plan.steps.length && p0.start.spot === true, 'steps program: one step per plan step');
	const java = S.toJava(STEP_PLANS[0].plan).steps;
	ok(p0.steps.every((s) => java.includes(S.stepCode(s))), 'steps program: every step is in the Java');
	const fol = programs[STEP_PLANS.length];
	ok(fol.steps.length === 1 && fol.steps[0].kind === 'follow' && fol.steps[0].path.parts.length === 2 && fol.steps[0].path.holdSec === 5, 'Pedro program: one follow step with both parts and the hold');
	ok(JSON.stringify(fol.steps[0].path.parts[1].pts) === JSON.stringify([[-48, -50], [-44, -36], [-30, -30]]), 'Pedro program: a part\'s points start where the last ended');
	ok(fol.steps[0].along.length === 1 && fol.steps[0].along[0].kind === 'fraction', 'Pedro program: the PathAt is there');
	const every = programs[STEP_PLANS.length + 1].steps[0];
	ok(every.timeoutSec === 12 && every.along.length === 4, 'Pedro program: the time limit and four PathAt');
	// In the order they come along the path, as the Java lists them.
	const javaEvery = P.toJava(PEDRO_PLANS[1].st.path, pedroOpts(PEDRO_PLANS[1].st)).all;
	const order = every.along.map((a) => javaEvery.indexOf(`PathAt.${a.kind}(`));
	ok(order.every((v, i) => v > 0 && (i === 0 || v > order[i - 1])), 'Pedro program: PathAt in the Java\'s order');
	const dt = programs[STEP_PLANS.length + 2];
	const javaDt = P.toJava(PEDRO_PLANS[2].st.path, pedroOpts(PEDRO_PLANS[2].st)).all;
	ok(dt.steps.length === 2 && dt.steps.every((s) => s.kind === 'driveTo'), 'driveTo program: one step per part');
	for (const s of dt.steps) ok(javaDt.includes(`.driveTo("${s.label}", ${P.num(s.x)}, ${P.num(s.y)}, ${P.num(s.h)}, ${P.num(s.power)}`), `driveTo program: "${s.label}" as in the Java`);
	ok(dt.steps[1].power === 0.5, 'driveTo program: a part\'s speed limit caps its power');
	ok(dt.start.spot === true && dt.start.x === -56, 'driveTo program: the robot is put on the path\'s start even without the s.start line');
}

// Plan <-> field numbers: the start spot as EasyAuto places it, for both alliances, both mirror settings.
{
	const p = { x: -56, y: -61, h: 30 };
	const red = C.planToField(p, 'RED', false), redM = C.planToField(p, 'RED', true), blue = C.planToField(p, 'BLUE', false), blueM = C.planToField(p, 'BLUE', true);
	ok(red.x === -56 && red.y === -61 && red.h === 30, 'RED, turned field: the same numbers');
	ok(redM.x === 56 && redM.y === -61 && redM.h === -30, 'RED, mirrored: x and heading flip');
	ok(blue.x === 56 && blue.y === 61 && near(blue.h, -150), 'BLUE: turned half a turn (the far wall)');
	ok(blueM.x === 56 && blueM.y === 61 && near(blueM.h, -150), 'BLUE, mirrored: as written, from the blue wall');
	for (const [a, m] of [['RED', false], ['RED', true], ['BLUE', false], ['BLUE', true]]) {
		const back = C.fieldToPlan(C.planToField(p, a, m), a, m);
		ok(near(back.x, p.x) && near(back.y, p.y) && near(back.h, p.h), `${a} mirror ${m}: field and back`);
	}
}

// The field from the simulator's file, with the names the web builder's copy has.
{
	const f = JSON.parse(fs.readFileSync(path.join(root, 'sim/fields/biobuzz-2026.json'), 'utf8'));
	const n = C.neutralField(f);
	ok(n.elements.some((e) => /^Your LOADING ZONE/.test(e.name)) && !n.elements.some((e) => /red|blue/i.test(e.name.replace(/HIVE.*/, ''))), 'neutral names: "Your LOADING ZONE", no red or blue');
	const obs = P.obstacles(n), half = n.field.insideWallSize / 2;
	const presets = S.startPresets([18, 18], n, obs, half);
	ok(presets.length >= 3 && presets.every((p) => S.startChecks(p, [18, 18], n, obs, half).every((c) => c.level !== 'bad')), 'start presets on the simulator\'s field pass the start checks');
	// The same as the web builder's copy where there is one.
	const site = siteDir();
	if (site && fs.existsSync(path.join(site, 'field.json'))) {
		const web = JSON.parse(fs.readFileSync(path.join(site, 'field.json'), 'utf8'));
		ok(JSON.stringify(web.elements) === JSON.stringify(n.elements), 'neutral field: the same parts and names as the site\'s field.json');
	}
}

// The timeline: the steps plan's dead reckoning; a Pedro path at an even pace to its end.
{
	const b = { mode: 'steps', steps: { plan: STEP_PLANS[1].plan } };
	const tl = C.timeline(b);
	const sim = S.simulate(STEP_PLANS[1].plan);
	ok(near(tl.total, sim.total) && tl.ends.length === sim.ends.length, 'timeline: steps = stepscore.simulate');
	const mid = C.poseAtTime(tl, sim.ends[1].t1);
	ok(near(mid.x, sim.ends[1].x, 1e-6) && near(mid.y, sim.ends[1].y, 1e-6), 'timeline: the pose at a step\'s end time is its end');
	const pb = { mode: 'pedro', pedro: PEDRO_PLANS[0].st };
	const pt = C.timeline(pb);
	const last = pt.samples[pt.samples.length - 1];
	ok(near(last.x, -30) && near(last.y, -30) && pt.total > 0 && pt.ends.length === 1, 'timeline: a Pedro path ends at its end');
	const dtl = C.timeline({ mode: 'pedro', pedro: PEDRO_PLANS[2].st });
	ok(dtl.ends.length === 2 && dtl.ends[1].t0 === dtl.ends[0].t1, 'timeline: driveTo has an end per step');
	// The plan robot keeps step with the run: step 2 (the drive) starts when the real step 1 ended.
	const built = { report: ['Start: done', 'Spin up: done', 'Out from the wall: done'], ends: [[0, 0, 0, 0.02], [0, 0, 0, 0.05], [0, 0, 0, 2.0]] };
	ok(near(C.planTimeAt(b, tl, built, 1.0), sim.ends[1].t0 + 0.95), 'planTimeAt: the drive started when "Spin up" ended');
	ok(near(C.planTimeAt(b, tl, built, 1.9), sim.ends[1].t1), 'planTimeAt: a step that runs long holds at its plan end');
	ok(near(C.planTimeAt(b, tl, built, 2.1), sim.ends[2].t0 + 0.1), 'planTimeAt: the next step starts when the real one ended');
}

// Plan versus real, and the fixes: a drive that rolls on into a turn; a STALLED step; a good run.
{
	const plan = structuredClone(STEP_PLANS[0].plan);
	plan.steps = [{ kind: 'move', label: 'Out', inches: 24, dir: 0, power: 0.6 }, { kind: 'turn', label: 'Face it', deg: 90, power: 0.5 },
		{ kind: 'park', sec: 3 }, { kind: 'move', label: 'Park', inches: 10, dir: 0, power: 0.6 }];
	const b = { mode: 'steps', steps: { plan } };
	const tl = C.timeline(b);
	const e = S.simulate(plan).ends;
	const f = (p, dx = 0, dy = 0) => { const q = C.planToField({ x: p.x + dx, y: p.y + dy, h: p.h }, 'RED', false); return [q.x, q.y, q.h, 1]; };
	const built = { id: 1, phase: 'done', alliance: 'RED', report: ['Start: done', 'Out: done', 'Face it: done', 'Park: done'],
		ends: [[-56, -61.1, 0, 0], f(e[0], 0, 0.5), f(e[1], 0, 5), f(e[3], 0, 5)], end: null };
	const rows = C.compare(b, built, tl);
	ok(rows.length === 3 && rows[0].name === 'Out' && rows[2].name === 'Park', 'compare: one row per reported step, the park has none');
	ok(near(rows[0].off, 0.5) && near(rows[1].off, 5), 'compare: how far off each step ended');
	const fx = C.fixes(b, built, rows, '', 4);
	const wait = fx.find((x) => x.fix && x.fix.kind === 'wait');
	ok(wait && wait.fix.at === 1, 'fixes: a 0.3 s wait before the turn that followed a drive');
	const b2 = structuredClone(b);
	ok(C.applyFix(b2, wait.fix) && b2.steps.plan.steps[1].kind === 'wait' && b2.steps.plan.steps[1].sec === 0.3, 'applyFix: the wait goes in before the turn');
	const stalled = C.fixes(b, { ...built, report: ['Start: done', 'Out: STALLED', 'Face it: done', 'Park: done'] }, C.compare(b, { ...built, report: ['Start: done', 'Out: STALLED', 'Face it: done', 'Park: done'] }, tl), '', 4);
	ok(stalled.some((x) => x.level === 'bad' && /STALLED/.test(x.text)), 'fixes: STALLED is a problem');
	const good = { ...built, ends: [[-56, -61.1, 0, 0], f(e[0]), f(e[1]), f(e[3], 1, 0)] };
	const gfx = C.fixes(b, good, C.compare(b, good, tl), '', 4);
	ok(gfx.length === 1 && gfx[0].level === 'good', 'fixes: a run that ends on the plan is good');
	const err = C.fixes(b, built, rows, 'java.lang.IllegalStateException: Built Auto, step 1: robot.claw::close: Robot.java has no part called claw (its parts: intake)', 4);
	ok(err[0].level === 'bad' && /no part called claw/.test(err[0].text) && err.length === 2, 'fixes: an INIT error and what to check');
	const cut = { ...built, report: ['Start: done', 'Out: done'] };
	ok(C.fixes(b, cut, C.compare(b, cut, tl), '', 4).some((x) => /did not run/.test(x.text)), 'fixes: steps the timer cut off');
	// Pedro: a path that stopped short of its end without a hold.
	const pst = structuredClone(PEDRO_PLANS[0].st);
	pst.path.holdSec = 0;
	const pb = { mode: 'pedro', pedro: pst };
	const ptl = C.timeline(pb);
	const pbuilt = { id: 1, phase: 'done', alliance: 'RED', report: ['Start: done', 'To the goal: done'], ends: [[-56, -61, 0, 0], [-30, -36, 45, 3]] };
	const pfx = C.fixes(pb, pbuilt, C.compare(pb, pbuilt, ptl), '', 4);
	ok(pfx.some((x) => x.fix && x.fix.kind === 'hold'), 'fixes: hold the Pedro path\'s end');
}

// The scenarios: a Pedro one, and one with the recorded end.
{
	const text = C.pedroScenario(PEDRO_PLANS[0].st, { fileName: 'p.sim' });
	ok(text.includes('init "Built Pedro Auto"') && text.includes('press g1.b') && /expect within 2 step 1 done +# To the goal/.test(text)
		&& text.includes('expect robot x = -30 4') && text.includes('expect robot y = -30 4') && text.includes('expect robot heading = 45 10')
		&& text.includes('expect robot x = -56 0.5'), 'pedroScenario: INIT as RED, the start, each step done, the end');
	const mirrored = C.pedroScenario(PEDRO_PLANS[1].st);
	ok(mirrored.includes('expect robot x = -40 0.5') && mirrored.includes('expect robot x = 0 4'), 'pedroScenario: mirrored for RED');
	const steps = S.toScenario(STEP_PLANS[0].plan, { fileName: 'a.sim' });
	const rec = C.recordedScenario(steps, { x: -50.04, y: -40.5, h: 3 }, false, '2026-10-22');
	ok(rec.includes('expect robot x = -50 4') && rec.includes('expect robot y = -40.5 4') && rec.includes('expect robot heading = 3 10')
		&& rec.includes('Recorded from a simulator run on 2026-10-22') && rec.includes('expect robot x = -56 0.5') && rec.includes('expect robot y = -61.1 0.5'), 'recordedScenario: the end replaced, the start kept');
	ok(rec.split('\n').length === steps.split('\n').length + 1, 'recordedScenario: one line more (the note)');
}

// The shared files' own rules: every step checked by its number (duplicate names and actions too), no
// end checks for a plan that leaves the field, who made it, Pedro's imports, no preselectTeleOp guess.
{
	const plan = structuredClone(STEP_PLANS[1].plan);
	plan.steps.splice(2, 0, { kind: 'move', label: 'Out from the wall', inches: 2, dir: 0, power: 0.6 });
	const text = S.toScenario(plan, { madeBy: 'the simulator\'s auto builder' });
	const nonPark = plan.steps.filter((x) => x.kind !== 'park').length;
	ok((text.match(/^expect (within 2 )?step \d+ done/gm) || []).length === nonPark && /^expect step 6 done +# Actions\.fireAll\(robot\)$/m.test(text),
		'toScenario: one check per step by number, actions included');
	ok(/^# Made by the simulator's auto builder/m.test(text) && !/crossbotics\.com/.test(text), 'toScenario: says who made it');
	const off = structuredClone(STEP_PLANS[1].plan);
	off.steps = [{ kind: 'move', label: 'Too far', inches: 200, dir: 0, power: 0.6 }];
	const offText = S.toScenario(off);
	ok(!/expect robot x = (?!-56 0\.5)/.test(offText) && /No end checks/.test(offText), 'toScenario: no end checks for a plan off the field');
	const noTele = S.toJava({ ...STEP_PLANS[0].plan, teleop: '' }).file;
	ok(!/preselectTeleOp =/.test(noTele.split('\n').find((l) => l.startsWith('@Autonomous'))), 'toJava: no TeleOp guessed when none is given');
	const pj = P.toJava(PEDRO_PLANS[0].st.path, pedroOpts(PEDRO_PLANS[0].st));
	ok(pj.all.includes('import com.crossbotics.ftchelper.pedro.PedroPath;') && pj.all.includes('import com.crossbotics.ftchelper.robot.PathAt;'), 'Pedro Java: the imports it needs');
}

// A tank drive never gets a strafe: "drive there", a dragged end, the drive switch (tankSteps, which the
// pages run on every change), and pasted Java (fromJava with drive 'tank'). Mecanum is unchanged.
{
	const strafes = (steps) => steps.filter((s) => s.kind === 'move' && S.sideways(+s.dir));
	let tapsOk = true, dragsOk = true, endsOk = true;
	for (const h of [0, 30, 90, -135, 180]) {
		const p = { x: -20, y: -30, h };
		for (const [x, y] of [[-20, 0], [10, -30], [-50, -30], [-20, -60], [5, -5], [-45, -55], [-19, -30]]) {
			const add = S.stepsToPoint('tank', p, x, y);
			if (strafes(add).length || add.some((s) => s.kind === 'move' && !S.TANK_DIRS.includes(s.dir))) tapsOk = false;
			const e = S.simulate({ start: p, steps: add }).end;
			if (add.length && Math.hypot(e.x - x, e.y - y) > 0.6) endsOk = false;
			const d = S.dragMove('tank', p, x, y);
			if (!d || S.sideways(d.dir) || !S.TANK_DIRS.includes(d.dir)) dragsOk = false;
		}
	}
	ok(tapsOk, 'tank: "drive there" adds a turn and a move forward or back, never a strafe');
	ok(endsOk, 'tank: "drive there" ends on the point tapped');
	ok(dragsOk, 'tank: a dragged end stays on the line through the robot\'s front (0 or 180)');
	const mec = S.stepsToPoint('mecanum', { x: 0, y: 0, h: 0 }, 20, 0);
	ok(mec.length === 1 && mec[0].dir === 90, 'mecanum: "drive there" is still one move (a strafe here)');
	ok(S.dragMove('mecanum', { x: 0, y: 0, h: 0 }, 0, -10).dir === 180 && S.dragMove('mecanum', { x: 0, y: 0, h: 0 }, -10, 0).dir === 270, 'mecanum: a dragged end goes any way');
	// The drive switch: every fixture plan as a tank plan has no strafe and drives the same route.
	for (const f of STEP_PLANS) {
		const r = S.tankSteps(f.plan.steps);
		const before = S.simulate(f.plan).ends.filter((_, i) => f.plan.steps[i].kind === 'move');
		const after = S.simulate({ ...f.plan, steps: r.steps }).ends.filter((_, i) => r.steps[i].kind === 'move');
		ok(strafes(r.steps).length === 0 && r.steps.every((s) => s.kind !== 'move' || S.TANK_DIRS.includes(s.dir)), `tankSteps "${f.name}": no strafe left, every move 0 or 180`);
		ok(r.changes.length === strafes(f.plan.steps).length && r.changes.every((t) => /not possible on a tank drive/.test(t)), `tankSteps "${f.name}": one line per strafe`);
		ok(before.length === after.length && before.every((e, k) => near(e.x, after[k].x, 1e-6) && near(e.y, after[k].y, 1e-6)), `tankSteps "${f.name}": every drive ends where it did`);
		ok(S.tankSteps(r.steps).changes.length === 0, `tankSteps "${f.name}": a second time changes nothing`);
		const tankPlan = { ...f.plan, drive: 'tank', steps: r.steps };
		ok(!S.checkPlan(tankPlan).list.some((c) => /tank drive/.test(c.text)), `tankSteps "${f.name}": the checks find no tank problem`);
	}
	ok(S.tankSteps(STEP_PLANS[1].plan.steps).steps.every((s, i) => s === STEP_PLANS[1].plan.steps[i]), 'tankSteps: a plan with no strafe stays as it is');
	// A strafe in the middle turns back (the next drive counts from the heading); a turnTo next makes it unneeded.
	const mid = S.tankSteps([{ kind: 'move', label: 'Over', inches: 10, dir: 270, power: 0.6 }, { kind: 'move', label: 'On', inches: 5, dir: 0, power: 0.6 }]);
	ok(mid.steps.map((s) => s.kind).join() === 'turn,move,turn,move' && mid.steps[0].deg === 90 && mid.steps[2].deg === -90, 'tankSteps: turn left, drive, turn back, then the next drive');
	const tt = S.tankSteps([{ kind: 'move', label: 'Over', inches: 10, dir: 90, power: 0.6 }, { kind: 'turnTo', label: 'Square', heading: 0, power: 0.5 }]);
	ok(tt.steps.map((s) => s.kind).join() === 'turn,move,turnTo', 'tankSteps: no turn back before a turnTo');
	ok(S.checkPlan({ ...STEP_PLANS[0].plan, drive: 'tank' }).list.some((c) => c.level === 'bad' && /not possible on a tank drive/.test(c.text)), 'checkPlan: a tank strafe is still bad (a last guard)');
	// Pasted Java on a tank drive: each strafe listed and turned into a turn and a drive; moveWithTurn left out, said so.
	const javaIn = S.toJava(STEP_PLANS[0].plan).file.replace('.parkWithSeconds(3)', '.parkWithSeconds(3)\n        .moveWithTurn("Swing", 10, 0, 90, 0.5, 0.5)');
	const tin = S.fromJava(javaIn, { drive: 'tank' });
	ok(tin.plan && strafes(tin.plan.steps).length === 0 && tin.drive === null, 'fromJava, tank: no strafe read in, the drive stays tank');
	ok(tin.tank.length === 1 && /Park.*not possible on a tank drive/.test(tin.tank[0]), 'fromJava, tank: the strafe is listed');
	ok(tin.problems.some((t) => /moveWithTurn.*not possible on a tank drive/.test(t)), 'fromJava, tank: moveWithTurn is left out and listed');
	const min = S.fromJava(javaIn);
	ok(min.drive === 'mecanum' && min.tank.length === 0 && strafes(min.plan.steps).length === 1 && min.problems.some((t) => /moveWithTurn/.test(t)), 'fromJava, mecanum: the strafe stays; moveWithTurn is still never read silently');
	// Both pages use these (no copy of their own) and run tankSteps on every render.
	const page = fs.readFileSync(path.join(dir, 'builder.js'), 'utf8');
	ok(/S\.stepsToPoint\(/.test(page) && /S\.dragMove\(/.test(page) && /S\.tankSteps\(/.test(page) && /syncRobot\(\);\s*enforceTank\(\);/.test(page), 'builder.js: drive there, drag and every render use the shared tank rules');
	const sitePage = siteDir() && path.join(siteDir(), 'steps.js');
	if (sitePage && fs.existsSync(sitePage)) {
		const sp = fs.readFileSync(sitePage, 'utf8');
		const good = /S\.stepsToPoint\(/.test(sp) && /S\.dragMove\(/.test(sp) && /S\.tankSteps\(/.test(sp) && /fromJava\(.*\{ drive: state\.plan\.drive \}/.test(sp);
		if (process.env.SITE_STRICT === '1') ok(good, 'the site\'s steps.js: drive there, drag, every render and Java in use the shared tank rules');
		else if (!good) console.log('WARN the site\'s steps.js does not use the shared tank rules yet');
	}
}

// ------------------------------------------------------------------ 3. the site's copies

function siteDir() {
	const want = process.env.SITE_BUILDER_DIR;
	if (want) return fs.existsSync(path.join(want, 'stepscore.js')) ? want : null;
	const code = path.resolve(root, '..');
	for (const name of fs.existsSync(code) ? fs.readdirSync(code).sort() : []) {
		if (!/^crossbotics/.test(name)) continue;
		const d = path.join(code, name, 'public/ftc-helper/path-builder');
		if (fs.existsSync(path.join(d, 'stepscore.js'))) return d;
	}
	return null;
}
const site = siteDir();
if (site) {
	// The library changes first and the site copies it later, so a site that is behind is a warning
	// (SITE_STRICT=1 makes it a failure, e.g. right before publishing the site).
	const strict = process.env.SITE_STRICT === '1';
	for (const f of SHARED) {
		const a = fs.readFileSync(path.join(dir, f), 'utf8'), b = fs.readFileSync(path.join(site, f), 'utf8');
		const what = `${f}: the site's copy (${site}) differs from the library's. The library is the source: copy it to the site (scripts/sync-path-builder.mjs there)`;
		if (strict) ok(a === b, what);
		else if (a !== b) console.log('WARN ' + what);
	}
} else {
	console.log('No site checkout found (SITE_BUILDER_DIR): the shared files were not compared with the site\'s.');
}

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
