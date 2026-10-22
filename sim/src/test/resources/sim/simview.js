// Drawing helpers for the simulator page (GET /simview.js). Plain JavaScript, no other library needed
// (the few that draw take three.js as their first argument). Kept apart from the page so another page can
// load it too: the turret shot simulator could use SimView.Buffer to glide its balls, SimView.ballTexture for
// its striped balls and SimView.label for its notes.
//
// Units are whatever the caller uses (the simulator: inches, seconds). Nothing here knows about a season.
"use strict";
(function (root) {
  const SimView = {};

  // ---------------------------------------------------------------- gliding between updates
  // The simulator moves everything 100 times a second, but the page hears about it only 50 times a second
  // (10 if it has to ask for /state), and not exactly evenly. Drawing each update the moment it arrives makes
  // things jump. Instead the page keeps the last few updates and draws the moment "a little in the past"
  // (about one update ago), in between the two updates around it. That costs a few hundredths of a second
  // of delay and makes every frame move smoothly.
  //
  // Each update needs t: the simulator's own clock (seconds). The buffer works out how the simulator's clock
  // lines up with this page's clock, and how far back to draw so there is (almost) always a newer update.
  class Buffer {
    constructor(opts) {
      opts = opts || {};
      this.keep = opts.keepSeconds || 2;          // updates older than this are dropped
      this.minDelay = opts.minDelay || 0.025;     // seconds behind the newest update, at least
      this.maxDelay = opts.maxDelay || 0.25;
      this.frames = [];
      this.offset = null;                         // page clock - simulator clock (the smallest seen: fastest arrival)
      this.late = 0;
      this.gap = 0.02;                            // the usual time between updates (smoothed)
      this.jitter = 0.005;                        // how unevenly they arrive (smoothed)
      this.delay = 0.06;
      this.count = 0;
    }
    /** Adds an update f (f.t = simulator seconds) that arrived at page time now (seconds). */
    push(f, now) {
      const last = this.frames[this.frames.length - 1];
      if (last && f.t <= last.t) {
        if (f.t < last.t - 1) this.reset();       // the simulator started again: begin afresh
        else return;                              // an old or repeated update
      }
      const off = now - f.t;
      if (this.offset === null || off < this.offset) { this.offset = off; this.late = 0; }
      else if (off - this.offset > 0.3) {
        // Every update is now much later than before (the simulator's clock fell behind, e.g. the laptop
        // was busy): after a few, take the new timing instead of waiting for updates that will not come.
        if (++this.late > 5) { this.offset = off; this.late = 0; }
      } else {
        this.late = 0;
        this.offset += (off - this.offset) * 0.002;   // follow a slow drift between the two clocks
        this.jitter += (Math.abs(off - this.offset) - this.jitter) * 0.05;
      }
      if (last) this.gap += (Math.min(0.5, f.t - last.t) - this.gap) * 0.1;
      this.delay = Math.max(this.minDelay, Math.min(this.maxDelay, this.gap * 1.2 + this.jitter * 2 + 0.008));
      this.frames.push(f);
      this.count++;
      while (this.frames.length > 2 && this.frames[0].t < f.t - this.keep) this.frames.shift();
    }
    reset() { this.frames = []; this.offset = null; this.late = 0; }
    /** The newest update (or null). */
    newest() { return this.frames[this.frames.length - 1] || null; }
    /**
     * What to draw at page time now (seconds): {a, b, u} = the updates before and after the moment drawn, and
     * how far along between them (0..1). a === b (u 0) when there is only one, or the moment is past the newest.
     */
    sample(now) {
      const n = this.frames.length;
      if (!n) return null;
      const t = now - this.offset - this.delay;
      if (n === 1 || t >= this.frames[n - 1].t) return { a: this.frames[n - 1], b: this.frames[n - 1], u: 0, t };
      if (t <= this.frames[0].t) return { a: this.frames[0], b: this.frames[0], u: 0, t };
      let i = n - 2;
      while (i > 0 && this.frames[i].t > t) i--;
      const a = this.frames[i], b = this.frames[i + 1];
      return { a, b, u: (t - a.t) / Math.max(1e-6, b.t - a.t), t };
    }
  }
  SimView.Buffer = Buffer;

  const lerp = (a, b, u) => a + (b - a) * u;
  SimView.lerp = lerp;
  /** Degrees, the short way round (179 to -179 passes 180). */
  SimView.lerpDeg = (a, b, u) => a + ((((b - a) % 360) + 540) % 360 - 180) * u;
  /** Two turns as quaternions [w, x, y, z], blended the short way (normalized linear blend: plenty for 1/50 s). */
  SimView.blendQuat = (p, q, u) => {
    const s = p[0] * q[0] + p[1] * q[1] + p[2] * q[2] + p[3] * q[3] < 0 ? -1 : 1;
    const r = [0, 1, 2, 3].map(k => lerp(p[k], s * q[k], u));
    const n = Math.hypot(r[0], r[1], r[2], r[3]) || 1;
    return r.map(v => v / n);
  };
  /**
   * Blends two lists of numbers item by item (each item a list too), when they match in length; otherwise the
   * newer one. angleAt: indexes that are angles in degrees (blended the short way).
   */
  SimView.lerpRows = (a, b, u, angleAt) => {
    if (!a || !b || a.length !== b.length) return b;
    return b.map((row, i) => {
      const ra = a[i];
      if (!ra || ra.length !== row.length) return row;
      return row.map((v, k) => (angleAt && angleAt.includes(k)) ? SimView.lerpDeg(ra[k], v, u) : lerp(ra[k], v, u));
    });
  };
  /**
   * Pieces that carry a key (the same piece in both updates keeps its key): blended when it is in both and did
   * not jump (faster than maxSpeed per second: a reset or a teleport); a new piece shows from the newer update,
   * one that went away is gone. rows: [key, type, x, y, z, qw, qx, qy, qz].
   */
  SimView.lerpKeyed = (a, b, u, dt, maxSpeed) => {
    if (!a || a === b) return b ? b.slice() : [];
    const old = new Map();
    for (const r of a) old.set(r[0], r);
    return b.map(r => {
      const o = old.get(r[0]);
      if (!o) return r;
      const d = Math.hypot(r[2] - o[2], r[3] - o[3], r[4] - o[4]);
      if (d > maxSpeed * Math.max(dt, 0.01) + 2) return u < 0.5 ? o : r;      // a jump: no smear across it
      const q = SimView.blendQuat(o.slice(5, 9), r.slice(5, 9), u);
      return [r[0], r[1], lerp(o[2], r[2], u), lerp(o[3], r[3], u), lerp(o[4], r[4], u), q[0], q[1], q[2], q[3]];
    });
  };

  // ---------------------------------------------------------------- pictures
  const textures = {};
  /**
   * A ball's texture: its color with two darker stripes and a few spots, so you can see it roll and spin
   * (the same idea as the turret shot simulator's balls).
   */
  SimView.ballTexture = (THREE, color) => {
    if (textures[color]) return textures[color];
    const c = document.createElement("canvas"); c.width = 128; c.height = 64;
    const g = c.getContext("2d"), col = new THREE.Color(color), dark = col.clone().multiplyScalar(0.55);
    g.fillStyle = "#" + col.getHexString(); g.fillRect(0, 0, 128, 64);
    g.fillStyle = "#" + dark.getHexString(); g.fillRect(0, 0, 10, 64); g.fillRect(64, 0, 10, 64);
    for (const [x, y] of [[32, 20], [96, 44], [40, 50], [100, 14]]) { g.beginPath(); g.arc(x, y, 5, 0, 7); g.fill(); }
    const t = new THREE.CanvasTexture(c);
    t.encoding = THREE.sRGBEncoding;
    return (textures[color] = t);
  };
  /**
   * A short text label that always faces the viewer (a sprite), heightIn tall. Dispose its material's map
   * and the material when you remove it.
   */
  SimView.label = (THREE, text, opts) => {
    opts = opts || {};
    const px = 40, pad = 10, font = `600 ${px}px system-ui, sans-serif`;
    const c = document.createElement("canvas"), g = c.getContext("2d");
    g.font = font;
    const w = Math.ceil(g.measureText(text).width) + 2 * pad;
    c.width = w; c.height = px + 2 * pad;
    g.font = font;
    g.fillStyle = opts.background || "rgba(15,20,25,.78)";
    const r = 14;
    g.beginPath(); g.moveTo(r, 0); g.arcTo(w, 0, w, c.height, r); g.arcTo(w, c.height, 0, c.height, r);
    g.arcTo(0, c.height, 0, 0, r); g.arcTo(0, 0, w, 0, r); g.fill();
    g.fillStyle = opts.color || "#fff"; g.textBaseline = "middle"; g.fillText(text, pad, c.height / 2 + 1);
    const tex = new THREE.CanvasTexture(c);
    tex.encoding = THREE.sRGBEncoding;
    const s = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false, transparent: true }));
    const h = opts.heightIn || 2.4;
    s.scale.set(h * w / c.height, h, 1);
    s.renderOrder = 10;
    return s;
  };

  // ---------------------------------------------------------------- camera sight words
  // The simulator says why a camera does or does not see each tag; these are the words and colors the page
  // uses for each reason (SimCamera.sight in the simulator: the same rules the OpMode's camera follows).
  SimView.SIGHT = {
    seen: { words: "seen", color: 0x2ecc71, solid: true },
    blocked: { words: "blocked?", color: 0xd63384, solid: false },
    slanted: { words: "too slanted", color: 0xf08c00, solid: false },
    small: { words: "too small", color: 0xe5534b, solid: false },
    left: { words: "out of view (left)", color: 0x9aa3ab, solid: false },
    right: { words: "out of view (right)", color: 0x9aa3ab, solid: false },
    above: { words: "out of view (above)", color: 0x9aa3ab, solid: false },
    below: { words: "out of view (below)", color: 0x9aa3ab, solid: false },
    behind: { words: "behind the camera", color: 0x6a7282, solid: false },
    light: { words: "lighting setting", color: 0xb07ce8, solid: false },
  };

  // ---------------------------------------------------------------- remembered choices
  /** Settings remembered in this browser (best effort: private windows may not keep them). */
  SimView.prefs = (key, defaults) => {
    let v = {};
    try { v = JSON.parse(localStorage.getItem(key)) || {}; } catch (e) { v = {}; }
    const out = Object.assign({}, defaults, v);
    out.save = function () {
      const copy = Object.assign({}, this); delete copy.save;
      try { localStorage.setItem(key, JSON.stringify(copy)); } catch (e) { /* not kept: fine */ }
    };
    return out;
  };

  root.SimView = SimView;
})(typeof window !== "undefined" ? window : this);
