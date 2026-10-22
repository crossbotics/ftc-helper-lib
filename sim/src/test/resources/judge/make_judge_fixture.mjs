// Makes turret-judge-shots.json: a shared set of shots and the verdict the turret shot simulator's judge() gives each
// one, for SimCellJudgeTest (the Java port must agree on every shot). Like the turret's validate.mjs, but across
// languages. Run it from anywhere with the turret repository's files:
//   node make_judge_fixture.mjs <path>/turret/v7/sim/sim_core.js <path>/turret/v7/out/sim/sim_geom.json \
//        <path>/turret/v7/out/sim/shoot_map.json > turret-judge-shots.json
// Verdicts: 0 clean entry, 1 miss, 2 touches the rim, 3 hits the other HIVE first, 4 hits the frame / our own CELL.
import { createRequire } from "module";
import fs from "fs";
import path from "path";
const require = createRequire(import.meta.url);
const [corePath, geomPath, mapPath] = process.argv.slice(2);
const { make } = require(path.resolve(corePath));
const G = JSON.parse(fs.readFileSync(geomPath, "utf8"));
const M = JSON.parse(fs.readFileSync(mapPath, "utf8"));
const S = make(G);

// A seeded random number (xorshift), so the fixture is the same every time.
let seed = 20261022;
const rnd = () => { seed ^= seed << 13; seed >>>= 0; seed ^= seed >>> 17; seed ^= seed << 5; seed >>>= 0; return (seed + 0.5) / 4294967296; };

const rows = M.rows;                       // spots around the HIVE (distance d, bearing b), including frame spots
const byWhy = { 0: [], 1: [], 2: [], 3: [], 4: [] };
const want = 120;                          // shots kept per verdict
for (let tries = 0; tries < 400000 && Object.values(byWhy).some(l => l.length < want); tries++) {
  const r = rows[Math.floor(rnd() * rows.length)];
  const b = r.b * Math.PI / 180, xu = [r.d * Math.sin(b), G.uc + r.d * Math.cos(b)];
  const ball = rnd() < 0.5 ? "Pollen" : "Nectar", state = rnd() < 0.5 ? "down" : "up";
  const elev = 45 + rnd() * 43, speed = 4.5 + rnd() * 7.5;
  const spinRpm = rnd() < 0.3 ? Math.round(rnd() * 900) : 0;
  const yaw = (rnd() - 0.5) * 30 * Math.PI / 180;             // aimed up to 15 deg off the opening's centre
  const d = S.aimDir(xu), dir = [d[0] * Math.cos(yaw) - d[1] * Math.sin(yaw), d[0] * Math.sin(yaw) + d[1] * Math.cos(yaw)];
  const L = S.launchDir(xu, elev, speed, dir, 0.2 + rnd() * 0.4);
  const why = S.judge(L.p0, L.v0, ball, state, 1e-3, 2.0, spinRpm);
  if (byWhy[why].length < want) byWhy[why].push({ p0: L.p0, v0: L.v0, ball, state, spinRpm, why });
}
const shots = [].concat(...Object.values(byWhy));
const keep = ["rho", "cd", "g", "balls", "pivot_z", "B", "pent", "s0", "a_in", "a_out", "frame_tube", "neigh_x", "neigh_bars", "rods", "min_desc"];
const geom = {}; keep.forEach(k => geom[k] = G[k]);
const counts = Object.fromEntries(Object.entries(byWhy).map(([k, l]) => [k, l.length]));
process.stdout.write(JSON.stringify({
  source: "turret v7 sim_core.js judge() (Octobots 2026 repository), made by make_judge_fixture.mjs",
  made: new Date().toISOString().slice(0, 10), counts, geom, shots }) + "\n");
console.error("verdict counts", counts);
