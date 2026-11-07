// Checks the simulator's auto builder page code (sim/src/test/resources/sim/builder). Run from the repo root:
//
//   node tools/check_builder.mjs
//
// It fails (exit 1) when:
//   1. the Java or the scenario for a shared fixture plan is not, letter for letter, what the shared files wrote
//      for it when the fixtures were made (sim/src/test/resources/builder/fixtures.json, made with
//      --write-fixtures). This is a snapshot: it finds a change, not whether the first output was right. The
//      plan with numbers worked out by hand (below) checks that;
//   2. simcore.js (the simulator-only part) gets something wrong: the plan as the Built Auto reads it,
//      plan <-> field numbers for both alliances, the field's neutral names, plan versus real, the fixes,
//      the Pedro scenario, the recorded scenario;
//   3. a copy of the shared files on the web site differs from the release the site names in its
//      src/lib-release.json (only checked when a site checkout is found: SITE_BUILDER_DIR, or
//      ../site/public/ftc-helper/path-builder; it says so when it finds none, as on GitHub). The library
//      may be ahead of the site between releases: that is no problem, since the site copies from the release.
//
// Which way the files go: the LIBRARY is the source of pathcore.js, stepscore.js, mapdraw.js and
// setupcard.js. Change them here and run this check. A release takes them to the site (its
// scripts/sync-path-builder.mjs copies them, and the field file, from the release the site names).
//
// To make the fixtures again (only when the output changes on purpose), from the library's own files
// (sim/src/test/resources/sim/builder: the source) or from the site's once it has copied them:
//   node tools/check_builder.mjs --write-fixtures sim/src/test/resources/sim/builder
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import os from 'node:os';
import { spawnSync } from 'node:child_process';

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

// The auto sheet (setupcard.js openAutoSheet): its words for other teams and its step numbers on the map.
{
	const SC = await importFrom(dir, 'setupcard.js');
	ok(S.actionWords('robot.launcher.fireAllAction()') === 'Fire all pieces', 'actionWords: fireAllAction()');
	ok(S.actionWords('robot.launcher.fireAllAction(2525)') === 'Fire all pieces at 2525 RPM', 'actionWords: fireAllAction(2525)');
	ok(S.actionWords('robot.launcher.fireAction(1)') === 'Fire 1 piece', 'actionWords: fireAction(1)');
	ok(S.actionWords('Actions.deployIntake(robot)') === 'Deploy intake', 'actionWords: Actions.deployIntake(robot) -> ' + S.actionWords('Actions.deployIntake(robot)'));
	ok(S.dirWords(0) === 'forward' && S.dirWords(-90) === 'left' && S.dirWords(308) === 'forward and left (308°)' && S.dirWords(135) === 'back and right (135°)', 'dirWords');
	ok(S.sheetWords({ kind: 'move', label: 'Off the wall', inches: 42.8, dir: 308 }) === 'Off the wall: drive 42.8 in forward and left (308°)', 'sheetWords: a move');
	ok(S.sheetWords({ kind: 'action', code: 'robot.launcher.fireAllAction()', timeout: 12 }) === 'Fire all pieces (up to 12 s)', 'sheetWords: an action');
	ok(S.sheetWords({ kind: 'turn', label: 'Turn', deg: 90 }) === S.stepWords({ kind: 'turn', label: 'Turn', deg: 90 }), 'sheetWords: other steps as stepWords');
	const marks = SC.sheetMarkers([{ x: 0, y: 0 }, { x: 1, y: 1 }, { x: 20, y: 0 }, { x: NaN, y: 0 }, { x: 20.5, y: 0 }]);
	ok(marks.length === 2 && marks[0].nums.join() === '1,2' && marks[1].nums.join() === '3,5', 'sheetMarkers: steps at one spot share a marker, ' + JSON.stringify(marks));
	const placed = SC.placeMarkers([{ x: 0, y: 0, nums: [1, 2, 3] }, { x: 0, y: 2, nums: [5] }, { x: 40, y: 0, nums: [6] }]);
	ok(!placed[0].moved && placed[1].moved && Math.abs(placed[1].ly - placed[0].ly) >= 5.6 && !placed[2].moved, 'placeMarkers: a label that would cover another moves, ' + JSON.stringify(placed));
	const outl = SC.sheetOutlines([{ x: 0, y: 0, h: 0 }, { x: 0, y: 0, h: 0 }, { x: 10, y: 0, h: 0 }, { x: 10, y: 0, h: 90 }, { x: 10, y: 0, h: 90 }], { x: 0, y: 0, h: 0 });
	ok(outl.map((o) => o.step).join() === '3,4', 'sheetOutlines: only steps that move or turn the robot, ' + JSON.stringify(outl));
	ok(SC.numsLabel([1, 2, 3, 5]) === '1-3, 5' && SC.numsLabel([1, 2]) === '1, 2' && SC.numsLabel([4]) === '4' && SC.numsLabel([1, 2, 3, 4, 5, 6, 7]) === '1-7', 'numsLabel');
	const edge = SC.placeMarkers([{ x: -69, y: 0, nums: [1, 2, 3, 4, 5, 6, 7, 9] }]);
	ok(edge[0].lx - edge[0].w / 2 >= -70.01, 'placeMarkers: a label stays inside the field, ' + JSON.stringify(edge));
	ok(SC.sheetTime(0, 12) === '0-12 s' && SC.sheetTime(12, 12) === '12 s' && SC.sheetTime(undefined, 1) === '', 'sheetTime');
}

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
	// A dragged start spot: kept inside the walls (also past a corner), against a near wall, a side on a seam.
	const R = [15.1, 16.8], tile = 2 * half / 6;
	for (const [x, y, h] of [[80, -80, 0], [59, -63.9, 0], [-75, -50, 30], [0, -20, 90]]) {
		const p = { ...S.snapStart({ x, y, h }, R, half), h };
		ok(S.startChecks(p, R, n, obs, half).every((c) => !/past the field wall/.test(c.text)), `snapStart (${x}, ${y}, ${h}): inside the walls`);
	}
	const back = S.snapStart({ x: 10, y: -60, h: 0 }, R, half);
	ok(near(back.y, -half + 0.5 + R[0] / 2, 0.06), 'snapStart: 2 in off our wall goes against it');
	const seam = S.snapStart({ x: 32, y: -40, h: 0 }, R, half);   // the side 0.07 in past the seam (the teeth edges are 0.4 in off)
	const wallY = -half + 0.5 + R[0] / 2;
	ok(near(seam.x - R[1] / 2, -half + 4 * tile, 0.06) && near(seam.y, wallY, 0.06), 'snapStart: a side near a tile seam goes onto it; away from every wall it goes to the nearest (G304)');
	const free = S.snapStart({ x: 30.5, y: -60, h: 0 }, R, half, false);
	ok(free.x === 30.5 && near(free.y, wallY, 0.06), 'snapStart off: no tile snapping, but it still ends touching a wall (G304)');
	const side = S.snapStart({ x: -55, y: 0, h: 0 }, R, half, false);
	ok(near(side.x - R[1] / 2, -half + 0.5, 0.06) && side.y === 0, 'snapStart: nearest the left wall goes against it');
	const teeth = S.snapStart({ x: -half + 2 * tile + S.TEETH_IN + R[1] / 2 + 0.3, y: -62, h: 0 }, R, half);
	ok(near(teeth.x - R[1] / 2, -half + 2 * tile + S.TEETH_IN, 0.06), 'snapStart: a side near the teeth\'s edge goes onto it');
	const mid = S.startChecks({ x: 0, y: -30, h: 0 }, R, n, obs, half);
	ok(mid.some((c) => c.level === 'bad' && /G304/.test(c.text) && /touch the field wall/.test(c.text)), 'startChecks: away from the wall is refused (G304)');
	{
		const sx = half - 0.5 - R[1] / 2, c4 = -half + 3.5 * tile;
		const p = S.snapStart({ x: sx, y: c4 + 1, h: 0 }, R, half);
		ok(near(p.y, c4, 0.06) && near(p.x, sx, 0.06), 'snapStart: against a side wall it lines up along that wall too');
		ok(S.autoNote({ x: -half + 2.5 * tile, y: wallY, h: 0 }, R, half) === 'Our wall, tile 3 from left, centred, facing field', 'autoNote: says the robot is centred in the tile');
		ok(/left edge on seam/.test(S.autoNote({ x: -half + 2 * tile + R[1] / 2, y: wallY, h: 0 }, R, half)), 'autoNote: an edge on the seam');
		ok(/left edge at teeth/.test(S.autoNote({ x: -half + 2 * tile + S.TEETH_IN + R[1] / 2, y: wallY, h: 0 }, R, half)), 'autoNote: an edge at the teeth');
		ok(S.autoNote({ x: -sx, y: -half + 3 * tile + R[0] / 2, h: 0 }, R, half) === 'Left wall, 4th tile out, near edge on seam, facing field', 'autoNote: along a side wall, the wall and the place stay');
	}
	{
		// The rest of an auto: setup lines stay and run; anything else is listed, never dropped silently.
		const auto = `public class A extends Auto<Robot> {
    @Override protected void atInit(Robot robot) { robot.lift.home(); }
    @Override protected void steps(Steps s, Robot robot) {
        robot.deployAtStart = false;   // the front is on the wall
        robot.reach = -2.5;
        robot.intake.in();
        // a note
        s.start("Spot", 0, -61, 0)
         .move("Off", 4, 180, 0.3);   // slowly
    }
    @Override protected boolean mirrorForRed() { return false; }
}`;
		const a = S.fromJava(auto);
		ok(a.plan.setup.length === 2 && a.plan.setup[0].field === 'deployAtStart' && a.plan.setup[0].value === false && a.plan.setup[1].value === -2.5, 'fromJava: reads robot.NAME = value; setup lines');
		ok(a.problems.some((t) => /Not carried over.*robot\.intake\.in\(\)/.test(t)), 'fromJava: lists a line it cannot keep');
		ok(a.problems.some((t) => /atInit\(\) in your auto/.test(t)) && !a.problems.some((t) => /steps\(\) in your|mirrorForRed\(\) in your/.test(t)), 'fromJava: lists an override it does not write');
		ok(a.notes.some((t) => /^2 comments inside steps/.test(t)), 'fromJava: counts the comments it will not keep');
		const aj = S.toJava({ ...a.plan, drive: 'tank' });
		ok(/robot\.deployAtStart = false;   \/\/ the front is on the wall\n/.test(aj.steps) && aj.steps.indexOf('deployAtStart') < aj.steps.indexOf('s.start'), 'toJava: the setup line comes before the steps, with its comment');
		ok(/        robot\.deployAtStart = false;/.test(aj.file), 'toJava: the setup line is in the whole file too');
		const aback = S.fromJava(aj.file);
		ok(JSON.stringify(aback.plan.setup.map((s) => s.value)) === '[false,-2.5]' && !aback.problems.length, 'the setup lines survive reading the Java again');
		const ap = C.toProgram({ mode: 'steps', steps: { plan: a.plan } });
		ok(JSON.stringify(ap.setup) === '[{"field":"deployAtStart","value":false},{"field":"reach","value":-2.5}]', 'toProgram: the Run gets the setup lines');
		ok(S.fromJava('s.start("x", 0, -61, 0).move("Out", 10, 0, 0.5);').plan.setup.length === 0, 'fromJava: only steps, no setup and no warning');
	}
	{
		const src = 's.start("Spot", START_X, -61, 0).move("Off", Tunables.Auto.offWallInches, 180, 0.3).turnTo("Turn", turnToField(PARK_HEADING), 0.5).parkWithSeconds(Tunables.Auto.parkSeconds);';
		const r0 = S.fromJava(src);
		ok(r0.unread.join('|') === 'START_X|Tunables.Auto.offWallInches|turnToField(PARK_HEADING)|Tunables.Auto.parkSeconds', 'fromJava: lists the names it could not read');
		const r = S.fromJava(src, { values: { START_X: 52, 'Tunables.Auto.offWallInches': 4, 'turnToField(PARK_HEADING)': 142.2, 'Tunables.Auto.parkSeconds': 6 } });
		const j = S.toJava({ ...r.plan, drive: 'tank' }).steps;
		ok(r.problems.length === 0 && r.plan.steps.length === 3, 'fromJava with values: every step read');
		ok(/start\("Spot", 52, -61, 0\)/.test(j) && /move\("Off", Tunables\.Auto\.offWallInches, 180, 0\.3\)/.test(j) && /turnTo\("Turn", 142\.2, 0\.5\)/.test(j) && /parkWithSeconds\(Tunables\.Auto\.parkSeconds\)/.test(j),
			'toJava: Tunables names stay, the auto\'s own names become numbers');
		r.plan.steps[0].inches = 5;
		ok(/move\("Off", 5, 180/.test(S.toJava({ ...r.plan, drive: 'tank' }).steps), 'toJava: a changed number is written as the number');
	}
	ok(/Centred on tile 4 from the left\.$/.test(S.startInches({ x: -half + 3.5 * tile, y: wallY, h: 0 }, R, half)), 'startInches: names the tile it is centred on');
	ok(/Left side on the seam 2\|3/.test(S.startInches({ x: -half + 2 * tile + R[1] / 2, y: wallY, h: 0 }, R, half)), 'startInches: a side on a seam');
	{
		const plan = { drive: 'tank', start: { x: 0, y: wallY, h: 0 }, steps: [{ kind: 'aim', label: 'Aim', timeout: 3 }] };
		ok(S.stepCode(plan.steps[0]) === 'aimAtGoal("Aim", 3)', 'aim step: the Java');
		const back2 = S.fromJava('s.start("x", 0, -61, 0).aimAtGoal("Aim", 2.5).move("Out", 10, 0, 0.5);');
		ok(back2.plan && back2.plan.steps[0].kind === 'aim' && back2.plan.steps[0].timeout === 2.5 && back2.plan.steps.length === 2, 'aim step: read back from Java');
		ok(S.stepCode({ kind: 'ready', label: 'Ready', timeout: 3 }) === 'readyToLaunch("Ready", 3)', 'ready step: the Java');
		ok(S.stepCode({ kind: 'aim', label: 'Aim', timeout: 4, sweep: 45 }) === 'aimAtGoal("Aim", 4, 45)', 'aim step with a sweep: the Java');
		const badSteps = { drive: 'mecanum', start: { x: 0, y: wallY, h: 0 }, steps: [{ kind: 'aim', label: 'A', timeout: 3, sweep: -5 }, { kind: 'back', label: 'B', power: 0 }] };
		const badChecks = S.checkPlan(badSteps).list.filter((c) => c.level === 'bad').map((c) => c.text).join(' | ');
		ok(/sweep is 0 to 180/.test(badChecks) && /power from 0.1 to 1/.test(badChecks), 'checkPlan: a bad sweep and a bad turn-back power are refused: ' + badChecks);
		ok(S.stepCode({ kind: 'remember', label: 'R' }) === 'rememberHeading("R")' && S.stepCode({ kind: 'back', label: 'B', power: 0.5 }) === 'turnBack("B", 0.5)', 'remember / turn back: the Java');
		const back4 = S.fromJava('s.start("x", 0, -61, 0).aimAtGoal("Aim", 4, 45).rememberHeading("R").turn("T", 90, 0.5).turnBack("B", 0.4);');
		ok(back4.plan && back4.plan.steps[0].sweep === 45 && back4.plan.steps[1].kind === 'remember' && back4.plan.steps[3].kind === 'back' && back4.plan.steps[3].power === 0.4, 'sweep, remember, turn back: read back from Java');
		const back3 = S.fromJava('s.start("x", 0, -61, 0).readyToLaunch("Ready", 2).move("Out", 10, 0, 0.5);');
		ok(back3.plan && back3.plan.steps[0].kind === 'ready' && back3.plan.steps[0].timeout === 2 && back3.plan.steps.length === 2, 'ready step: read back from Java');
	}
	ok(/^Left side .* from the left wall, right side against the right wall, back against our wall\.$/.test(S.startInches(S.snapStart({ x: 80, y: -80, h: 0 }, R, half), R, half)), 'startInches: the corner reads "against"');
	ok(S.wallName(-70, 10) === 'the left wall' && S.wallName(60, -71) === 'our wall' && S.wallName(71, 5) === 'the right wall' && S.wallName(0, 71) === 'the far wall', 'wallName');
	// The same as the web builder's copy where there is one.
	const site = siteDir();
	if (site && fs.existsSync(path.join(site, 'field.json'))) {
		const web = JSON.parse(fs.readFileSync(path.join(site, 'field.json'), 'utf8'));
		// The site's field comes from its release, and this checkout may be ahead of that.
		const tag = siteRelease(site);
		const atTag = tag && spawnSync('git', ['show', `${tag}:sim/fields/biobuzz-2026.json`], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
		const released = atTag && atTag.status === 0 ? C.neutralField(JSON.parse(atTag.stdout)) : n;
		ok(JSON.stringify(web.elements) === JSON.stringify(released.elements), 'neutral field: the same parts and names as the site\'s field.json');
	}
}

// A plan with its numbers worked out by hand: start at (0, 0) facing away from the wall (y up), 24 in
// forward, a quarter turn left, 12 in forward. The fixtures above cannot find a wrong first answer; this can.
{
	const plan = { drive: 'mecanum', mirror: false, start: { x: 0, y: 0, h: 0, note: '', spot: true },
		steps: [{ kind: 'move', label: 'A', inches: 24, dir: 0, power: 0.5 }, { kind: 'turn', label: 'B', deg: 90, power: 0.5 },
			{ kind: 'move', label: 'C', inches: 12, dir: 0, power: 0.5 }] };
	const e = S.simulate(plan).ends;
	ok(near(e[0].x, 0, 1e-6) && near(e[0].y, 24, 1e-6), 'by hand: 24 in forward ends at (0, 24)');
	ok(near(e[1].x, 0, 1e-6) && near(e[1].y, 24, 1e-6) && near(e[1].h, 90, 1e-6), 'by hand: a turn stays in place, heading 90');
	ok(near(e[2].x, -12, 1e-6) && near(e[2].y, 24, 1e-6), 'by hand: 12 in forward facing left ends at (-12, 24)');
}

// The timeline: the steps plan's dead reckoning; a Pedro path at an even pace to its end.
{
	const b = { mode: 'steps', steps: { plan: STEP_PLANS[1].plan } };
	const tl = C.timeline(b);
	const sim = S.simulate(STEP_PLANS[1].plan);
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
	// A fire action's own flywheel speed (the "flywheel" box): read and written back, other code untouched.
	{
		const fs = S.fireSpeed, ws = S.withFireSpeed;
		ok(fs('robot.launcher.fireAllAction()').rpm === null && fs('robot.launcher.fireAllAction(2250)').rpm === 2250
			&& fs('robot.launcher.fireAction(2)').rpm === null && fs('robot.shooter.fireAction(2, 2250)').rpm === 2250,
			'fire speed: read from fireAction / fireAllAction, any launcher part name');
		ok(fs('Actions.fireAll(robot)') === null && fs('robot.launcher.fireAction(n)') === null && fs('robot.launcher.ejectAction()') === null,
			'fire speed: not for other actions or values that are not numbers');
		ok(ws('robot.launcher.fireAllAction()', 2250) === 'robot.launcher.fireAllAction(2250)'
			&& ws('robot.launcher.fireAction(2)', 2249.6) === 'robot.launcher.fireAction(2, 2250)'
			&& ws('robot.launcher.fireAction(2, 2250)', null) === 'robot.launcher.fireAction(2)'
			&& ws('robot.launcher.fireAllAction(2250)', '') === 'robot.launcher.fireAllAction()'
			&& ws('Actions.fireAll(robot)', 2250) === 'Actions.fireAll(robot)',
			'fire speed: written into the code (whole RPM), removed when empty, other code unchanged');
	}
	// "Drive there" uses the way the robot already faces: a spot more behind than ahead backs up (the smaller turn).
	{
		const ahead = S.stepsToPoint('tank', { x: 0, y: 0, h: 0 }, 5, 20), behind = S.stepsToPoint('tank', { x: 0, y: 0, h: 0 }, 5, -20);
		const small = (r) => r[0].kind === 'turn' && Math.abs(r[0].deg) <= 90;
		ok(small(ahead) && ahead[1].dir === 0 && small(behind) && behind[1].dir === 180,
			'tank: "drive there" turns the front to a spot ahead (forward), the back to a spot behind (backs up)');
		const e = S.simulate({ drive: 'tank', start: { x: 0, y: 0, h: 0 }, steps: behind }).end;
		ok(Math.hypot(e.x - 5, e.y + 20) < 0.6, 'tank: backing up to a spot behind ends on it');
		const mb = S.stepsToPoint('mecanum', { x: 0, y: 0, h: 0 }, 0, -20);
		ok(mb.length === 1 && mb[0].dir === 180, 'mecanum: a spot straight behind: it backs up, no turn');
		const path = { start: [0, 0], parts: [] };
		ok(P.newPartHeading(path, 0, 20, 0).mode === 'path' && P.newPartHeading(path, 2, -20, 0).mode === 'back',
			'Pedro: a new part to a spot ahead faces along it, to a spot behind drives backwards');
		const one = { start: [0, 0], parts: [{ ctrl: [], end: [0, 24], heading: { mode: 'path' }, maxSpeed: null }] };
		ok(P.newPartHeading(one, 0, 0, 0).mode === 'back' && P.newPartHeading(one, 0, 48, 0).mode === 'path',
			'Pedro: after a part, back along it is backwards, on ahead is forward');
		const keep = { start: [0, 0], parts: [{ ctrl: [], end: [0, 24], heading: { mode: 'keep', a: 30 }, maxSpeed: null }] };
		ok(P.newPartHeading(keep, 0, 0, 0).mode === 'keep', 'Pedro: a part that keeps a heading: the new part keeps it too');
	}
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
	// Pasted Java on a tank drive: each strafe (and the moveWithTurn) listed and turned into turns and drives.
	const javaIn = S.toJava(STEP_PLANS[0].plan).file.replace('.parkWithSeconds(3)', '.parkWithSeconds(3)\n        .moveWithTurn("Swing", 10, 0, 90, 0.5)');
	const tin = S.fromJava(javaIn, { drive: 'tank' });
	ok(tin.plan && strafes(tin.plan.steps).length === 0 && tin.drive === null && !tin.plan.steps.some((s) => s.kind === 'moveTurn'), 'fromJava, tank: no strafe or moveWithTurn read in, the drive stays tank');
	ok(tin.tank.length === 2 && tin.tank.some((t) => /Park.*not possible on a tank drive/.test(t)) && tin.tank.some((t) => /Swing.*mecanum/.test(t)), 'fromJava, tank: the strafe and the moveWithTurn are listed');
	const min = S.fromJava(javaIn);
	const swing = min.plan.steps.find((s) => s.kind === 'moveTurn');
	ok(min.tank.length === 0 && strafes(min.plan.steps).length === 1 && swing && swing.inches === 10 && swing.dir === 0 && swing.heading === 90 && swing.power === 0.5,
		'fromJava, mecanum: the strafe stays; moveWithTurn is read as "drive and turn"');
	ok(S.toJava(min.plan).steps.includes('.moveWithTurn("Swing", 10, 0, 90, 0.5)'), 'toJava: moveWithTurn written back the same');
	// Drive and turn at once (mecanum): it drives a line fixed on the field and turns on the way.
	{
		const plan = { drive: 'mecanum', start: { x: 0, y: -60, h: 0 }, steps: [{ kind: 'turn', label: 'Face', deg: 90, power: 0.5 }, { kind: 'moveTurn', label: 'Out', inches: 20, dir: 90, heading: 45, power: 0.6 }] };
		const e = S.simulate(plan).ends[1];
		ok(near(e.x, 20, 1e-6) && near(e.y, -60, 1e-6) && near(e.h, 45, 1e-6), 'moveTurn: the direction counts from the START heading (90 = right), it ends at its heading');
		ok(S.checkPlan({ ...plan, drive: 'tank' }).list.some((c) => c.level === 'bad' && c.step === 1 && /mecanum/.test(c.text)), 'checkPlan: moveTurn on a tank drive is bad');
		const t = S.tankSteps(plan.steps);
		const tank = S.simulate({ ...plan, steps: t.steps }).end;
		ok(t.steps.every((s) => s.kind !== 'moveTurn') && near(tank.x, 20, 0.01) && near(tank.y, -60, 0.01) && near(tank.h, 45, 0.01), 'tankSteps: moveTurn becomes turn, drive, turn: the same end');
		// turn while driving: a turn then a move become one moveTurn and back, with the same end
		const two = { drive: 'mecanum', start: { x: 0, y: -60, h: 0 }, steps: [{ kind: 'move', label: 'A', inches: 10, dir: 0, power: 0.6 }, { kind: 'turn', label: 'T', deg: 30, power: 0.5 }, { kind: 'move', label: 'B', inches: 20, dir: 45, power: 0.6 }] };
		const before = S.simulate(two).end;
		const on = S.turnWhileDriving(two, 2, true);
		const onEnd = S.simulate({ ...two, steps: on.steps }).end;
		ok(on.steps.length === 2 && on.i === 1 && on.steps[1].kind === 'moveTurn' && near(onEnd.x, before.x, 0.1) && near(onEnd.y, before.y, 0.1) && near(onEnd.h, before.h, 0.1),
			'turnWhileDriving on: the turn before goes into the drive, the same end');
		const off = S.turnWhileDriving({ ...two, steps: on.steps }, 1, false);
		const offEnd = S.simulate({ ...two, steps: off.steps }).end;
		ok(off.steps.map((s) => s.kind).join() === 'move,move,turnTo' && near(offEnd.x, before.x, 0.1) && near(offEnd.y, before.y, 0.1) && near(offEnd.h, before.h, 0.1),
			'turnWhileDriving off: drive, then turn, the same end');
		// a split drive-and-turn keeps the route and the end
		const sp = S.splitMove({ ...two, steps: on.steps }, 1, 8);
		const spEnd = S.simulate({ ...two, steps: sp }).end;
		ok(sp.length === 3 && sp[1].kind === 'moveTurn' && sp[2].kind === 'moveTurn' && near(spEnd.x, before.x, 0.1) && near(spEnd.y, before.y, 0.1) && near(spEnd.h, before.h, 0.1),
			'splitMove: a drive-and-turn splits in two with the same end');
		// a dragged drive-and-turn ends where dropped, still facing its heading
		const dr = S.dragPoint({ ...two, steps: on.steps }, 1, 5, -30);
		const dEnd = S.simulate({ ...two, steps: dr.steps }).ends[dr.i];
		ok(near(dEnd.x, 5, 0.6) && near(dEnd.y, -30, 0.6) && near(dEnd.h, before.h, 0.1), 'dragPoint: a drive-and-turn ends where dropped, at its heading');
	}
	// The turn knob: a turn, turnTo or moveTurn ends facing the heading given.
	{
		const plan = { drive: 'tank', start: { x: 0, y: -60, h: 30 }, steps: [{ kind: 'move', label: 'A', inches: 10, dir: 0, power: 0.6 }, { kind: 'turn', label: 'T', deg: 10, power: 0.5 }, { kind: 'turnTo', label: 'U', heading: 0, power: 0.5 }] };
		const a = S.setTurnHeading(plan, 1, -45), b = S.setTurnHeading(plan, 2, 120);
		ok(near(S.simulate({ ...plan, steps: a }).ends[1].h, -45) && a[1].deg === -75, 'setTurnHeading: a turn turns by what it needs');
		ok(near(S.simulate({ ...plan, steps: b }).ends[2].h, 120) && b[2].heading === 90, 'setTurnHeading: a turnTo gets the heading (from the start heading)');
		ok(S.setTurnHeading(plan, 0, 10) === null, 'setTurnHeading: not a drive');
		// the next point stays (its drive is aimed again), except a turn or turnTo right before its drive, which aims it
		for (const drive of ['tank', 'mecanum']) {
			const p2 = { drive, start: { x: 0, y: -60, h: 0 }, steps: [{ kind: 'move', label: 'A', inches: 20, dir: 0, power: 0.6 }, { kind: 'turnTo', label: 'U', heading: 0, power: 0.5 },
				{ kind: 'turn', label: 'G', deg: 0, power: 0.5 }, { kind: 'move', label: 'B', inches: 20, dir: 0, power: 0.6 }, { kind: 'turn', label: 'F', deg: 90, power: 0.5 }, { kind: 'move', label: 'C', inches: 10, dir: 0, power: 0.6 }] };
			const old = S.simulate(p2).ends;
			const a2 = S.setTurnHeading(p2, 1, 45), e2 = S.simulate({ ...p2, steps: a2 }).ends;
			const bi = a2.findIndex((st) => st.label === 'B');
			ok(near(e2[bi].x, old[3].x, 0.6) && near(e2[bi].y, old[3].y, 0.6), `${drive}: a turned knob keeps the next point`);
			const c2 = S.setTurnHeading(p2, 4, 0), e3 = S.simulate({ ...p2, steps: c2 }).ends;
			ok(c2[4].deg === 0 && near(e3[5].y, old[3].y + 10, 0.01), `${drive}: a turn right before its drive swings that drive`);
			// a turnTo right before its drive swings it too (no opposite turn added: Fable's review)
			const p3 = { drive, start: { x: 0, y: 0, h: 0 }, steps: [{ kind: 'move', label: 'A', inches: 20, dir: 0, power: 0.6 }, { kind: 'turnTo', label: 'U', heading: 0, power: 0.5 }, { kind: 'move', label: 'B', inches: 20, dir: 0, power: 0.6 }] };
			const t3 = S.setTurnHeading(p3, 1, 45), e4 = S.simulate({ ...p3, steps: t3 }).end;
			ok(t3.length === 3 && t3[1].heading === 45 && near(e4.h, 45) && !near(e4.x, 0, 1), `${drive}: a turnTo right before its drive swings that drive`);
			// a drag through a turnTo keeps the point after it (Codex's review)
			const d3 = S.dragPoint(p3, 0, 10, 20), e5 = S.simulate({ ...p3, steps: d3.steps });
			ok(near(e5.end.x, 0, 0.6) && near(e5.end.y, 40, 0.6), `${drive}: a drag keeps the next point across a turnTo`);
		}
	}
	// "An action" tapped on the map: on a line the drive splits there; at a point, right after the drive that arrives.
	{
		const plan = { drive: 'tank', start: { x: 0, y: -60, h: 0 }, steps: [{ kind: 'move', label: 'A', inches: 20, dir: 0, power: 0.6 },
			{ kind: 'turn', label: 'T', deg: -90, power: 0.5 }, { kind: 'move', label: 'B', inches: 20, dir: 0, power: 0.6 }] };
		const act = { kind: 'action', code: 'Actions.fireAll(robot)', timeout: 10 };
		const mid = S.actionAt(plan, 1, -50, act);
		const e = S.simulate({ ...plan, steps: mid.steps });
		ok(mid.i === 1 && mid.steps[1].kind === 'action' && mid.steps.length === 5 && near(e.ends[0].y, -50) && near(e.end.x, 20) && near(e.end.y, -40),
			'actionAt: on a line, the drive splits there and the route stays');
		const at = S.actionAt(plan, 0.5, -40.5, act);
		ok(at.i === 1 && at.steps[1].kind === 'action' && at.steps[2].kind === 'turn', 'actionAt: at a point, right after the drive (before the turn there)');
		ok(S.actionAt(plan, 0, -61, act).i === 0, 'actionAt: at the start, first');
		ok(S.actionAt(plan, 60, 60, act) === null, 'actionAt: far from the route, nothing');
		ok(plan.steps.length === 3, 'actionAt: the plan given is not changed');
	}
	// Pedro: reviews' findings. Turn to face starts from the heading the robot arrives with; a split never
	// makes an empty stretch; a bad stretch is found; a stretch on a curve follows the distance (like Pedro).
	{
		const two = { start: [0, 0], parts: [{ ctrl: [], end: [0, 20], heading: { mode: 'keep', a: 45 }, maxSpeed: null }, { ctrl: [], end: [20, 20], heading: { mode: 'path' }, maxSpeed: null }], holdSec: 0 };
		ok(P.facePart(two, 1, 20, 40)[1].heading.a === 45, 'facePart: the turn starts where the part before ended (no snap at the join)');
		ok(P.facePart(two, 0, 0, 40, 30)[0].heading.a === 30, 'facePart: part 1 starts from the start heading');
		const late = { start: [0, 0], parts: [{ ctrl: [], end: [0, 40], heading: { mode: 'turn', a: 0, b: 90, from: 0.7, to: 1 }, maxSpeed: null }], holdSec: 0 };
		const sp = P.splitPart(late, 0, 0.701).parts.map((q) => q.heading);
		ok(sp.every((h) => !P.badSpan(h)), 'splitPart: no empty stretch after rounding');
		ok(P.badSpan({ mode: 'turn', a: 0, b: 90, from: 0.5, to: 0.5 }) && !P.badSpan({ mode: 'turn', a: 0, b: 90 }) && !P.badSpan(late.parts[0].heading), 'badSpan: from >= to is found');
		const curve = { start: [0, 0], parts: [{ ctrl: [[0, 10]], end: [100, 10], heading: { mode: 'turn', a: 0, b: 90, from: 0.7, to: 1 }, maxSpeed: null }], holdSec: 0 };
		const cs = P.sample(curve);
		let run = 0;
		const len = cs.slice(1).reduce((acc, q, k) => acc + Math.hypot(q.x - cs[k].x, q.y - cs[k].y), 0);
		const before = cs.filter((q, k) => { if (k) run += Math.hypot(q.x - cs[k - 1].x, q.y - cs[k - 1].y); return run / len < 0.69; });
		ok(before.every((q) => near(q.h, 0, 1e-6)), 'Pedro curve: no turn before 70% of the distance');
	}
	// Pedro "turn to face": the part ends facing the point.
	{
		const path = { start: [0, -60], parts: [{ ctrl: [], end: [0, -30], heading: { mode: 'path' }, maxSpeed: null }], holdSec: 0 };
		const f = P.facePart(path, 0, -30, -30);
		const smp = P.sample({ ...path, parts: f });
		ok(f[0].heading.mode === 'turn' && near(smp[smp.length - 1].h, 90) && near(smp[0].h, 0), 'facePart: turns from its start heading to face the point');
		const g = P.facePart({ ...path, parts: [{ ...path.parts[0], heading: { mode: 'turn', a: 0, b: 10, from: 0.7, to: 1 } }] }, 0, 30, -30);
		ok(g[0].heading.b === -90 && g[0].heading.from === 0.7, 'facePart: a turn keeps its "when"');
		ok(P.facePart(path, 3, 0, 0) === null, 'facePart: no such part');
	}
	// Pedro: a part splits in two with the same shape; a turn on one stretch.
	{
		const path = { start: [0, -60], parts: [{ ctrl: [[30, -40]], end: [0, -20], heading: { mode: 'turn', a: 0, b: 90 }, maxSpeed: 0.5 }], holdSec: 0 };
		const r = P.splitPart(path, 0, 0.5, [{ kind: 'part', value: 1 }, { kind: 'part', value: 2 }]);
		const before = P.sample(path), after = P.sample({ ...path, parts: r.parts });
		const atHalf = before[Math.round((before.length - 1) / 2)];
		ok(r.parts.length === 2 && near(r.parts[0].end[0], 15, 0.1) && near(r.parts[0].end[1], -40, 0.1) && r.parts[1].maxSpeed === 0.5, 'splitPart: the new point is on the curve, the speed limit stays');
		ok(near(after[after.length - 1].h, 90) && near(P.partEnds(after, 0).end, 45, 0.1) && near(atHalf.h, 45, 1), 'splitPart: the turn is shared out (45 at the split)');
		ok(r.actions[0].value === 1 && r.actions[1].value === 3, 'splitPart: "start of part" numbers after it move up');
		ok(P.splitPart(path, 0, 0.01) === null, 'splitPart: not on top of an end');
		const last = { ...path, parts: [{ ...path.parts[0], heading: { mode: 'turn', a: 0, b: 90, from: 0.7, to: 1 } }] };
		const ls = P.sample(last);
		ok(near(ls[Math.round((ls.length - 1) * 0.5)].h, 0) && near(ls[ls.length - 1].h, 90) && P.turnWhen(last.parts[0].heading) === 'last', 'Pedro: "drive, then turn" keeps the heading until 70%');
		const j = P.toJava(last, { label: 'Go', start: { on: false } }).all;
		ok(/\.turnTo\(0, 90, 0\.7, 1\)/.test(j), 'Pedro Java: turnTo(a, b, from, until)');
		const back = P.fromJava(j).paths?.[0]?.path?.parts?.[0]?.heading ?? P.fromJava(j).path?.parts?.[0]?.heading;
		ok(back && back.from === 0.7 && back.to === 1, 'Pedro Java in: turnTo with 4 numbers keeps the stretch');
		const sl = P.splitPart(last, 0, 0.5);
		ok(sl.parts[0].heading.mode === 'keep' && sl.parts[1].heading.from === 0.4 && sl.parts[1].heading.to === 1, 'splitPart: a late turn stays in the second part');
	}
	// The map's edits: a drag moves one point (the turn before it follows; the next point and every point
	// after it stay), and a tap on a line splits that drive in two. Both drives.
	for (const drive of ['tank', 'mecanum']) {
		const plan = { drive, start: { x: 0, y: -60, h: 0 }, steps: [] };
		for (const [x, y] of [[0, -30], [30, -30], [30, 0], [10, 20]]) plan.steps.push(...S.stepsToPoint(drive, S.simulate(plan).end, x, y));
		const moves = (pl) => S.simulate(pl).ends.filter((_, k) => pl.steps[k].kind === 'move');
		const before = moves(plan);
		let hitOk = true, restOk = true, tankOk = true, headOk = true;
		plan.steps.forEach((s, i) => {
			if (s.kind !== 'move') return;
			const n = plan.steps.slice(0, i + 1).filter((t) => t.kind === 'move').length - 1;   // which point
			for (const [dx, dy] of [[12, 0], [-9, 7], [0, -8], [15, 15], [-20, -3]]) {
				const want = { x: before[n].x + dx, y: before[n].y + dy };
				const r = S.dragPoint(plan, i, want.x, want.y);
				const pl = { ...plan, steps: r.steps }, after = moves(pl);
				if (r.steps[r.i].kind !== 'move' || Math.hypot(after[n].x - want.x, after[n].y - want.y) > 1) hitOk = false;
				if (after.length !== before.length || before.some((e, k) => k !== n && Math.hypot(e.x - after[k].x, e.y - after[k].y) > 0.6)) restOk = false;
				if (n < before.length - 2 && Math.abs(P.wrap(S.simulate(pl).end.h - S.simulate(plan).end.h)) > 0.5) headOk = false;
				if (drive === 'tank' && r.steps.some((t) => t.kind === 'move' && !S.TANK_DIRS.includes(t.dir))) tankOk = false;
			}
		});
		ok(hitOk, `${drive}: a dragged point ends where it was dropped (the turn before it follows)`);
		ok(restOk, `${drive}: a drag moves only that point: the others stay`);
		ok(headOk, `${drive}: a drag keeps the heading at the end (the last drive and the one before it may turn it)`);
		if (drive === 'tank') ok(tankOk, 'tank: a drag never makes a strafe');
		const firstMove = plan.steps.findIndex((s) => s.kind === 'move');
		const split = S.splitMove(plan, firstMove, 10);
		const sp = moves({ ...plan, steps: split });
		ok(split.length === plan.steps.length + 1 && sp.length === before.length + 1 && near(sp[0].y, -50, 1e-6)
			&& before.every((e, k) => near(e.x, sp[k + 1].x, 1e-6) && near(e.y, sp[k + 1].y, 1e-6)), `${drive}: a tap on a line puts a new point there; the route stays`);
		ok(S.splitMove(plan, firstMove, 0.2) === null && S.splitMove(plan, firstMove, 29.8) === null, `${drive}: no new point on top of an end`);
	}
	ok(S.dragPoint({ drive: 'tank', start: { x: 0, y: 0, h: 0 }, steps: [{ kind: 'move', label: 'Out', inches: 20, dir: 0 }] }, 0, 10, 20).steps[1].label === 'Out',
		'a drag keeps the team\'s own step name');
	// Every page file parses (builder.js is never imported here: it needs the page). One syntax error stops the
	// whole module in the browser, and the Auto builder button then does nothing.
	const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'builder-check-'));
	for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.js'))) {
		const copy = path.join(tmp, f.replace(/\.js$/, '.mjs'));
		fs.copyFileSync(path.join(dir, f), copy);
		const r = spawnSync(process.execPath, ['--check', copy], { encoding: 'utf8' });
		ok(r.status === 0, `${f} parses` + (r.status === 0 ? '' : ': ' + ((r.stderr || '').match(/SyntaxError[^\n]*/) || [r.stderr])[0]));
	}
	fs.rmSync(tmp, { recursive: true, force: true });
	// Both pages use these (no copy of their own) and run tankSteps on every render.
	const page = fs.readFileSync(path.join(dir, 'builder.js'), 'utf8');
	ok(/S\.stepsToPoint\(/.test(page) && /S\.dragPoint\(/.test(page) && /S\.setTurnHeading\(/.test(page) && /S\.actionAt\(/.test(page) && /P\.facePart\(/.test(page) && /P\.splitPart\(/.test(page) && /S\.turnWhileDriving\(/.test(page) && /S\.tankSteps\(/.test(page) && /syncRobot\(\);\s*enforceTank\(\);/.test(page), 'builder.js: drive there, drag and every render use the shared tank rules');
	const sitePage = siteDir() && path.join(siteDir(), 'steps.js');
	if (sitePage && fs.existsSync(sitePage)) {
		const sp = fs.readFileSync(sitePage, 'utf8');
		const good = /S\.stepsToPoint\(/.test(sp) && /S\.(dragMove|dragPoint)\(/.test(sp) && /S\.tankSteps\(/.test(sp) && /fromJava\(.*\{ drive: state\.plan\.drive \}/.test(sp);
		ok(good, 'the site\'s steps.js: drive there, drag, every render and Java in use the shared tank rules');   // dragPoint calls dragMove
	}
}

// ------------------------------------------------------------------ 3. the site's copies

function siteDir() {
	const want = process.env.SITE_BUILDER_DIR;
	if (want) return fs.existsSync(path.join(want, 'stepscore.js')) ? want : null;
	const d = path.resolve(root, '../site/public/ftc-helper/path-builder');
	return fs.existsSync(path.join(d, 'stepscore.js')) ? d : null;
}
// The release the site's folder names (<site>/src/lib-release.json), or null.
function siteRelease(d) {
	const release = path.resolve(d, '../../../src/lib-release.json');
	return fs.existsSync(release) ? JSON.parse(fs.readFileSync(release, 'utf8')).tag : null;
}
const site = siteDir();
if (site) {
	// The site's copies must be what its release holds.
	const tag = siteRelease(site);
	ok(!!tag, `the site names its library release (${site})`);
	for (const f of tag ? SHARED : []) {
		const r = spawnSync('git', ['show', `${tag}:sim/src/test/resources/sim/builder/${f}`], { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 });
		ok(r.status === 0, `${f}: this checkout has the site's release ${tag} (git fetch --tags)`);
		if (r.status === 0) ok(r.stdout === fs.readFileSync(path.join(site, f), 'utf8'), `${f}: the site's copy (${site}) is not what ${tag} holds. Run the site's scripts/sync-path-builder.mjs`);
	}
} else {
	console.log('No site checkout found (SITE_BUILDER_DIR): the shared files were not compared with the site\'s.');
}

console.log(`${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
