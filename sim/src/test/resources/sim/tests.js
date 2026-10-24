// The simulator page's tests (start lesson 10, "Test your robot code"): record a test, choose its checks,
// save it in the robot project's simulator/scenarios folder (or download it), run it (or all of them) on the
// page, and, after a planned change, update it. The simulator does the work (SimRecorder, SimTests); this file is
// the menu, the recording bar and the dialog.
"use strict";
(() => {
  const $ = (s) => document.querySelector(s);
  const esc = (t) => String(t == null ? "" : t).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const LESSON = "https://github.com/crossbotics/ftc-helper-lib/blob/main/docs/tutorials/start/10-test-your-robot-code.md";

  // ------------------------------------------------------------ talking to the simulator
  async function getJSON(url) {
    const r = await fetch(url, { cache: "no-store" });
    return r.json();
  }
  async function postJSON(url, data) {
    const r = await fetch(url, { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams(data).toString() });
    return r.json();
  }

  // ------------------------------------------------------------ what is shown
  let info = null;        // GET /tests: can it save, where, the tests there
  let rec = null;         // the recording (steps, checks) after Stop recording
  let test = null;        // the test shown: { name, text, saved }
  let polling = 0;
  const dlg = $("#testDlg");

  function open(html) {
    dlg.innerHTML = `<div class="td-page">${html}</div>`;
    dlg.hidden = false;
    document.body.classList.add("td-open");
    const first = dlg.querySelector("[data-first]") || dlg.querySelector("button");
    if (first) first.focus();
  }
  function close() {
    dlg.hidden = true;
    dlg.innerHTML = "";
    document.body.classList.remove("td-open");
  }
  dlg.addEventListener("keydown", (e) => { if (e.key === "Escape") close(); });
  dlg.addEventListener("click", (e) => { if (e.target === dlg) close(); });

  function head(title) {
    return `<div class="td-head"><h2 id="testDlgTitle">${esc(title)}</h2><button data-close aria-label="Close">Close</button></div>`;
  }
  function wireClose() { dlg.querySelectorAll("[data-close]").forEach((b) => (b.onclick = close)); }
  function say(text, bad) {
    const el = dlg.querySelector(".td-say");
    if (el) { el.textContent = text; el.classList.toggle("bad", !!bad); }
  }

  // A file name from the OpMode's name: "goBILDA Bot TeleOp" -> "gobilda-bot-teleop".
  function slug(s) {
    return (String(s || "my-test").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 50)) || "my-test";
  }
  const NAME_OK = /^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$/;

  // ------------------------------------------------------------ the menu
  $("#testsMenu").addEventListener("click", (e) => {
    const b = e.target.closest("[data-tests]");
    if (!b) return;
    $("#testsMenu").hidden = true;
    $("#testsBtn").setAttribute("aria-expanded", "false");
    if (b.dataset.tests === "record") record("init");
    if (b.dataset.tests === "recordNow") record("now");
    if (b.dataset.tests === "open") showOpen();
    if (b.dataset.tests === "runAll") runAll();
  });

  // ------------------------------------------------------------ recording
  async function record(from) {
    const r = await postJSON("/tests/record", { action: "start", from });
    if (r.error) return open(head("Record a test") + `<p class="td-say bad">${esc(r.error)}</p>`), wireClose();
  }
  $("#recStop").onclick = async () => {
    if (bar.mode === "run") { await postJSON("/tests/stop", {}); return; }
    rec = await postJSON("/tests/record", { action: "stop" });
    info = await getJSON("/tests");
    showChecks(slug(rec.opMode));
  };
  $("#recCancel").onclick = () => postJSON("/tests/record", { action: "cancel" });

  // The bar under the 3D view's buttons: recording (red) or a test running (blue). From the state stream.
  const bar = { mode: "" };
  function state(s) {
    const t = s.tests || {};
    const mode = t.rec ? "rec" : t.run ? "run" : "";
    const el = $("#recBar");
    el.hidden = !mode;
    el.classList.toggle("run", mode === "run");
    bar.mode = mode;
    if (mode === "rec") {
      const m = Math.floor(t.sec / 60), sec = String(Math.floor(t.sec % 60)).padStart(2, "0");
      $("#recText").textContent = `Recording ${m}:${sec}`;
      $("#recHint").textContent = !s.opmode || s.phase === "IDLE"
        ? "Choose an OpMode and press INIT. Drive as in a match; the test copies what you do."
        : `${t.count} thing${t.count === 1 ? "" : "s"} recorded. When the robot has done the job: Stop recording.`;
      $("#recStop").textContent = "Stop recording";
      $("#recCancel").hidden = false;
    } else if (mode === "run") {
      $("#recText").textContent = t.all ? `Running all tests: ${t.all}` : "Running a test";
      $("#recHint").textContent = t.all
        ? "Each test runs on a fresh simulator of its own, like the Simulator tests button. The result opens when they end."
        : "Hands off: the test drives the robot. The result opens when it ends.";
      $("#recStop").textContent = "Stop the test";
      $("#recCancel").hidden = true;
    }
  }

  // ------------------------------------------------------------ choosing the checks
  // The scenario line of a check (the same as SimRecorder.Check.line()).
  function line(c) {
    const w = c.text != null ? `screen has "${String(c.text).replace(/"/g, '\\"')}"` : c.words != null ? c.words
      : `${c.subject} ${c.op} ${c.value}${c.op === "=" && String(c.tol).trim() !== "" ? " " + c.tol : ""}`;
    return "expect " + (+c.within > 0 ? `within ${c.within} ` : "") + w;
  }
  // The whole file (the same as SimRecorder.Recording.text()).
  function fileText(name) {
    const out = [`# ${name}.sim: made by the simulator's test recorder on ${rec.date}, from "${rec.opMode}".`,
      `# The steps replay what was done on the page; the "expect" lines at the end are the checks.`,
      `# Change a check's number or tolerance to make it looser or stricter (start lesson 10).`];
    if (info.runLine) out.push("# Run it: " + info.runLine.replace("NAME", name));
    if (info.canSave && info.robotLine) out.push(info.robotLine);
    out.push(...rec.steps, "# The checks");
    const on = rec.checks.filter((c) => c.on);
    if (!on.some((c) => +c.within > 0) && rec.tailWait >= 0.05) out.push("wait " + rec.tailWait);
    for (const c of on.filter((c) => +c.within > 0).concat(on.filter((c) => !(+c.within > 0)))) out.push(line(c));
    return out.join("\n") + "\n";
  }

  function checkRow(c, i) {
    let edit = "";
    if (c.within > 0) edit += `<label>wait up to <input type="number" min="0" step="0.5" data-f="within" data-i="${i}" value="${esc(c.within)}"> s</label>`;
    if (c.text != null) {
      edit += `<label>screen has <input type="text" class="wide" data-f="text" data-i="${i}" value="${esc(c.text)}"></label>`;
    } else if (c.words != null) {
      edit += `<code>${esc(c.words)}</code>`;
    } else {
      const ops = [">=", ">", "=", "<", "<="].map((o) => `<option${o === c.op ? " selected" : ""}>${esc(o)}</option>`).join("");
      edit += `<code>${esc(c.subject)}</code> <select data-f="op" data-i="${i}" aria-label="compare">${ops}</select>`
        + ` <input type="text" class="num" data-f="value" data-i="${i}" value="${esc(c.value)}" aria-label="value">`
        + (c.op === "=" ? ` <label>give or take <input type="text" class="num" data-f="tol" data-i="${i}" value="${esc(c.tol)}"></label>` : "");
    }
    return `<div class="td-check${c.on ? "" : " off"}"><input type="checkbox" id="tdc${i}" data-on="${i}"${c.on ? " checked" : ""}>`
      + `<label for="tdc${i}">${esc(c.label)}</label><div class="edit">${edit}</div></div>`;
  }

  function showChecks(name) {
    if (!rec || rec.error) {
      open(head("Record a test") + `<p class="td-say bad">${esc(rec && rec.error || "No recording.")}</p>`);
      return wireClose();
    }
    const goals = rec.checks.map((c, i) => [c, i]).filter(([c]) => c.group === "goal");
    const details = rec.checks.map((c, i) => [c, i]).filter(([c]) => c.group !== "goal");
    const where = info.canSave
      ? `Saved in <code>${esc(info.folder)}</code>, with your code.`
      : `No robot project here (<code>-PteamCode=none</code>): Save downloads the file. Put it in your robot project's <code>simulator/scenarios</code> folder.`;
    open(head("Your test: choose the checks")
      + `<p class="muted">The steps replay what you did (${rec.steps.length} lines). The checks say what must be true at the end.
         <b>Goal checks</b> are ticked: they are what the test is for. <b>Details</b> are not: tick one only if it matters,
         because a test full of details breaks with every small change. <a href="${LESSON}" target="_blank" rel="noopener">How to choose</a></p>`
      + rec.notes.map((n) => `<p class="td-note">${esc(n)}</p>`).join("")
      + `<h3>Goal checks</h3>${goals.map(([c, i]) => checkRow(c, i)).join("") || '<p class="muted">None found: tick a detail below.</p>'}`
      + `<h3>Details</h3>${details.map(([c, i]) => checkRow(c, i)).join("") || '<p class="muted">None.</p>'}`
      + `<details><summary>The whole file</summary><pre class="td-code" id="tdFile"></pre></details>`
      + `<div class="row td-name"><label>Name <input type="text" id="tdName" value="${esc(name)}" spellcheck="false" data-first></label><span class="muted">.sim &middot; ${where}</span></div>`
      + `<div class="td-actions"><button class="primary" id="tdSave">${info.canSave ? "Save" : "Download"}</button>`
      + `<button id="tdRun">Run it now</button></div><p class="td-say" role="status"></p>`);
    wireClose();
    const refresh = () => { $("#tdFile").textContent = fileText(currentName()); };
    dlg.querySelectorAll("[data-on]").forEach((b) => (b.onchange = () => {
      rec.checks[+b.dataset.on].on = b.checked;
      b.closest(".td-check").classList.toggle("off", !b.checked);
      refresh();
    }));
    dlg.querySelectorAll("[data-f]").forEach((el) => (el.oninput = el.onchange = () => {
      const c = rec.checks[+el.dataset.i];
      c[el.dataset.f] = el.dataset.f === "within" ? (el.value === "" ? 0 : +el.value) : el.value;
      if (el.dataset.f === "op") showChecksKeep(name);   // "give or take" only with =
      refresh();
    }));
    $("#tdName").oninput = refresh;
    refresh();
    $("#tdSave").onclick = () => save(currentName(), fileText(currentName()), false);
    $("#tdRun").onclick = () => run(currentName(), fileText(currentName()), false);
  }
  // Redraw the dialog with the edits kept (the name too).
  function showChecksKeep() { const n = currentName(); showChecks(n); }
  function currentName() { const el = $("#tdName"); return el ? el.value.trim() : (test && test.name) || "my-test"; }

  // ------------------------------------------------------------ save, download
  async function save(name, text, replace) {
    if (!NAME_OK.test(name)) return say("A name has letters, digits, - and _ only (e.g. main-teleop).", true);
    test = { name, text, saved: false };
    if (!info || !info.canSave) {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
      a.download = name + ".sim";
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 1000);
      return say(`Downloaded ${name}.sim. Put it in your robot project's simulator/scenarios folder.`);
    }
    const r = await postJSON("/tests/save", { name, text, replace: replace ? 1 : 0 });
    if (r.error) return say(r.error, true);
    if (r.exists) {
      say(`A test called ${name} is there already.`, true);
      const s = dlg.querySelector(".td-say");
      const b = document.createElement("button");
      b.textContent = "Replace it";
      b.className = "td-inline";
      b.onclick = () => save(name, text, true);
      s.appendChild(b);
      return;
    }
    test.saved = true;
    say(`Saved: ${r.path}. Commit it with your code.`);
  }

  // ------------------------------------------------------------ open a test
  async function showOpen() {
    info = await getJSON("/tests");
    const list = info.tests.length
      ? info.tests.map((n) => `<button class="td-pick" data-name="${esc(n)}">${esc(n)}<span class="muted">.sim</span></button>`).join("")
      : `<p class="muted">${info.canSave ? "No tests in " + esc(info.folder) + " yet: record one." : "No robot project here (-PteamCode=none): open a file from this computer."}</p>`;
    open(head("Open a test")
      + (info.canSave ? `<p class="muted">Your tests, in <code>${esc(info.folder)}</code>:</p>` : "")
      + `<div class="td-list">${list}</div>`
      + `<p><label>Or a file from this computer: <input type="file" id="tdFileIn" accept=".sim,text/plain"></label></p>`
      + `<p class="td-say" role="status"></p>`);
    wireClose();
    dlg.querySelectorAll(".td-pick").forEach((b) => (b.onclick = async () => {
      const r = await getJSON("/tests/read?name=" + encodeURIComponent(b.dataset.name));
      if (r.error) return say(r.error, true);
      test = { name: b.dataset.name, text: r.text, saved: true };
      showTest();
    }));
    $("#tdFileIn").onchange = async (e) => {
      const f = e.target.files[0];
      if (!f) return;
      test = { name: slug(f.name.replace(/\.sim$/i, "")), text: await f.text(), saved: false };
      showTest();
    };
  }
  function showTest() {
    open(head("Test: " + test.name)
      + `<pre class="td-code">${esc(test.text)}</pre>`
      + `<div class="td-actions"><button class="primary" id="tdRun" data-first>Run it now</button></div><p class="td-say" role="status"></p>`);
    wireClose();
    $("#tdRun").onclick = () => run(test.name, test.text, false);
  }

  // ------------------------------------------------------------ run it on the page
  async function run(name, text, measure) {
    test = { name, text, saved: !!(test && test.saved && test.name === name && test.text === text) };
    const r = await postJSON("/tests/run", { name, text, measure: measure ? 1 : 0 });
    if (r.error) return say(r.error, true);
    close();       // watch the robot; the result opens when it ends
    clearInterval(polling);
    polling = setInterval(async () => {
      const st = await getJSON("/tests/status").catch(() => null);
      if (!st || st.running || !st.done) return;
      clearInterval(polling);
      showResult(st);
    }, 300);
  }

  function showResult(st) {
    const passed = st.failures === 0;
    const fails = st.report.filter((l) => l.trim().startsWith("FAIL"));
    const report = st.report.map((l) => `<span class="${l.trim().startsWith("FAIL") ? "td-failline" : ""}">${esc(l)}</span>`).join("\n");
    const many = `${st.failures} check${st.failures === 1 ? "" : "s"}`;
    let body = (st.measure
      ? `<p class="td-head2">Measured: ${passed ? "every check passed, nothing to update" : many + " differ from the test"}</p>`
      : `<p class="${passed ? "td-pass" : "td-fail"}">${passed ? "PASSED" : "FAILED: " + many}</p>`)
      + (st.note ? `<p class="td-note">${esc(st.note)}</p>` : "");
    let actions = `<button id="tdRun" data-first>Run it again</button>`;
    if (st.update) {
      const u = st.update;
      body += `<h3>The test, updated from this run</h3>`
        + (u.changed.length ? `<p class="muted">New numbers for the checks that failed (the steps, the tolerances and the other checks stay):</p>`
          + `<div class="td-diff">${u.changed.map((c) => `<div>Line ${c.n}:<br><span class="old">${esc(c.old)}</span><br><span class="new">${esc(c.new)}</span></div>`).join("")}</div>`
          : `<p class="muted">No number to change.</p>`)
        + (u.stuck.length ? `<p class="td-note">These cannot be updated by a number: change or delete them by hand.</p>`
          + `<div class="td-diff">${u.stuck.map((c) => `<div>Line ${c.n}: <code>${esc(c.line)}</code><br><span class="muted">${esc(c.why)}</span></div>`).join("")}</div>` : "");
      if (u.changed.length) actions = `<button class="primary" id="tdSaveUpd" data-first>${info && info.canSave ? "Save the update" : "Download the update"}</button>` + actions;
    } else if (!passed) {
      body += `<div class="td-diff">${fails.map((l) => `<div>${esc(l.trim())}</div>`).join("")}</div>`
        + `<p>Did you change the code <b>on purpose</b> (a new speed, a new path, a new button)? Then the test is out of date,
           not the robot: update it. Did you <b>not</b> mean to change this? Then the test caught a break: fix the code.</p>`;
      actions = `<button class="primary" id="tdUpdate" data-first>The change was on purpose: update the test</button>` + actions;
    }
    body += `<details${passed ? "" : " open"}><summary>The report, line by line</summary><pre class="td-code">${report}</pre></details>`;
    open(head("Test: " + (st.name || test.name)) + body + `<div class="td-actions">${actions}</div><p class="td-say" role="status"></p>`);
    wireClose();
    $("#tdRun").onclick = () => run(test.name, test.text, false);
    const upd = $("#tdUpdate");
    if (upd) upd.onclick = () => run(test.name, test.text, true);
    const su = $("#tdSaveUpd");
    if (su) su.onclick = async () => {
      if (!info) info = await getJSON("/tests");
      const text = st.update.text.endsWith("\n") ? st.update.text : st.update.text + "\n";
      await save(test.name, text, true);
      test.text = text;
    };
  }

  // ------------------------------------------------------------ run all tests
  // Every test in simulator/scenarios, one after another, each from a fresh start (like ./gradlew :sim:scenario).
  async function runAll() {
    const r = await postJSON("/tests/runAll", {});
    if (r.error) return open(head("Run all tests") + `<p class="td-say bad">${esc(r.error)}</p>`), wireClose();
    clearInterval(polling);
    polling = setInterval(async () => {
      const st = await getJSON("/tests/status").catch(() => null);
      if (!st || !st.all || st.running || !st.done) return;
      clearInterval(polling);
      showAll(st.all);
    }, 300);
  }

  function showAll(all) {
    const rs = all.results;
    const failed = rs.filter((x) => !x.passed).length;
    const notRun = all.total - rs.length;
    const top = failed === 0 && notRun === 0
      ? `<p class="td-pass">PASSED: all ${all.total} test${all.total === 1 ? "" : "s"}</p>`
      : `<p class="td-fail">${failed ? `FAILED: ${failed} of ${all.total}` : "Stopped"}${notRun ? ` (${notRun} not run)` : ""}</p>`;
    const rows = rs.map((x) => `<div><button class="td-pick" data-name="${esc(x.name)}">${esc(x.name)}<span class="muted">.sim</span></button> `
      + `<b class="${x.passed ? "td-passline" : "td-failline"}">${x.passed ? "passed" : x.finished ? `failed: ${x.failures} check${x.failures === 1 ? "" : "s"}` : "stopped"}</b>`
      + (x.note ? `<br><span class="muted">${esc(x.note)}</span>` : "")
      + x.fails.map((l) => `<br><span class="td-failline">${esc(l)}</span>`).join("") + `</div>`).join("");
    open(head("Run all tests") + top
      + `<div class="td-diff">${rows}</div>`
      + `<p class="muted">Open a test to run it alone, or to update it after a planned change.
         Changed your code? Stop the simulator and start it again (the Run button): it builds your code and makes the robot file when it starts.</p>`
      + `<div class="td-actions"><button id="tdRunAll" data-first>Run all again</button></div><p class="td-say" role="status"></p>`);
    wireClose();
    $("#tdRunAll").onclick = runAll;
    dlg.querySelectorAll(".td-pick").forEach((b) => (b.onclick = async () => {
      const r = await getJSON("/tests/read?name=" + encodeURIComponent(b.dataset.name));
      if (r.error) return say(r.error, true);
      if (!info) info = await getJSON("/tests");
      test = { name: b.dataset.name, text: r.text, saved: true };
      showTest();
    }));
  }

  window.simTests = { state };
})();
