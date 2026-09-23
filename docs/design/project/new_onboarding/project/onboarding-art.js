// ONBOARDING_ART — scenes of «Знакомство» (series 36). One evening, one sky; grid 412 × 480, horizon y = 300.
// Layer: { d, fill?, stroke?, sw?, op?, depth: 0 sky · 1 far · 2 near, anim? }. fill: '#hex' | 'g:<gradient>' | 'zone'.
// Gradients: { type: 'linear', x1, y1, x2, y2, stops } | { type: 'radial', cx, cy, r, stops }, stops [offset, color, alpha?], userSpace.
(function () {
  const W = 412, H = 480, HZ = 300;
  const r1 = n => { const v = Math.round(n * 10) / 10; return Object.is(v, -0) ? 0 : v; };
  const P = p => r1(p[0]) + ' ' + r1(p[1]);
  const poly = (pts, close = true) => 'M' + pts.map(P).join('L') + (close ? 'Z' : '');
  const ell = (cx, cy, rx, ry) => `M${r1(cx - rx)} ${r1(cy)}a${r1(rx)} ${r1(ry)} 0 1 0 ${r1(2 * rx)} 0a${r1(rx)} ${r1(ry)} 0 1 0 ${r1(-2 * rx)} 0Z`;
  const circ = (cx, cy, r) => ell(cx, cy, r, r);
  const rect = (x, y, w, h) => `M${r1(x)} ${r1(y)}h${r1(w)}v${r1(h)}h${r1(-w)}Z`;
  const rr = (x, y, w, h, r) => `M${r1(x + r)} ${r1(y)}h${r1(w - 2 * r)}a${r} ${r} 0 0 1 ${r} ${r}v${r1(h - 2 * r)}a${r} ${r} 0 0 1 ${-r} ${r}h${r1(-(w - 2 * r))}a${r} ${r} 0 0 1 ${-r} ${-r}v${r1(-(h - 2 * r))}a${r} ${r} 0 0 1 ${r} ${-r}Z`;
  const spark = (x, y, s) => { const k = s * 0.17; return `M${r1(x)} ${r1(y - s)}Q${r1(x + k)} ${r1(y - k)} ${r1(x + s)} ${r1(y)}Q${r1(x + k)} ${r1(y + k)} ${r1(x)} ${r1(y + s)}Q${r1(x - k)} ${r1(y + k)} ${r1(x - s)} ${r1(y)}Q${r1(x - k)} ${r1(y - k)} ${r1(x)} ${r1(y - s)}Z`; };
  function cat(pts, n = 10) { const o = []; for (let i = 0; i < pts.length - 1; i++) { const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)]; for (let k = i ? 1 : 0; k <= n; k++) { const t = k / n, t2 = t * t, t3 = t2 * t; o.push([0, 1].map(j => 0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2 + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3))); } } return o; }
  function ribbon(c, wf) { const L = [], R = [], N = c.length; for (let i = 0; i < N; i++) { const a = c[Math.max(0, i - 1)], b = c[Math.min(N - 1, i + 1)]; let tx = b[0] - a[0], ty = b[1] - a[1]; const l = Math.hypot(tx, ty) || 1; tx /= l; ty /= l; const w = wf(c[i]) / 2; L.push([c[i][0] - ty * w, c[i][1] + tx * w]); R.push([c[i][0] + ty * w, c[i][1] - tx * w]); } return poly(L.concat(R.reverse())); }
  function spiral(cx, cy, ra, rb, th0, turns, dir = -1, n = 64) { const o = []; for (let i = 0; i <= n; i++) { const t = i / n, th = th0 + dir * turns * 2 * Math.PI * t, r = ra + (rb - ra) * t; o.push([cx + r * Math.cos(th), cy + r * Math.sin(th)]); } return o; }
  // violin head from the icon (1b), base = nut
  function head(bx, by, s) {
    const X = x => bx + x * s, Y = y => by + y * s;
    const pegbox = poly([[X(-3.3), Y(0)], [X(3.3), Y(0)], [X(3.9), Y(-24)], [X(-3.9), Y(-24)]]);
    const sc = [X(0.5), Y(-29)], R = 8.5 * s;
    const groove = poly(spiral(sc[0] + 0.8 * s, sc[1] + 0.4 * s, R * 0.84, R * 0.15, 0.9, 1.75, -1, 72), false);
    const pegs = [];
    [[-21, -1], [-16.5, 1], [-12, -1], [-7.5, 1]].forEach(([py, sx]) => pegs.push(poly([[X(sx * 3.3), Y(py - 1.05)], [X(sx * 6.4), Y(py - 0.85)], [X(sx * 6.4), Y(py + 0.85)], [X(sx * 3.3), Y(py + 1.05)]]), ell(X(sx * 8.6), Y(py), 2.9 * s, 2.15 * s)));
    return { pegbox, scroll: circ(sc[0], sc[1], R), groove, pegs };
  }
  const lin = (x1, y1, x2, y2, stops) => ({ type: 'linear', x1, y1, x2, y2, stops });
  const rad = (cx, cy, r, stops) => ({ type: 'radial', cx, cy, r, stops });
  const L = (d, fill, depth, o = {}) => ({ d, fill, depth, ...o });
  const S = (d, stroke, sw, depth, o = {}) => ({ d, stroke, sw, depth, ...o });

  // ── shared sky and land: every page is the same evening, only the sun goes lower
  const HILL_L = 'M-82 300V276C-34 272 14 266.4 46 270.4C74 273.6 98 282.4 122 289.6C138 294.4 154 298 170 300Z';
  const HILL_R = 'M494 300V270.4C454 267.2 410 261.6 374 267.2C346 271.2 322 281.6 294 292C280.4 296 265.2 298.4 246 300Z';
  function base(o) {
    const { sunX = 206, sunY, sunR = 80, glowA = 0.55, stars = 0 } = o;
    const grads = {
      sky: lin(0, 0, 0, HZ, [[0, '#2A2857'], [0.55, '#6E4A78'], [1, '#E08A63']]),
      glow: rad(sunX, sunY + 6, 210, [[0, '#FFD98A', glowA], [0.5, '#F2A070', glowA / 3], [1, '#F2A070', 0]]),
      sun: rad(sunX - 16, sunY - 60, 150, [[0, '#FFF1CC'], [0.65, '#FFD98A'], [1, '#F6BC76']]),
      ground: lin(0, HZ, 0, H, [[0, '#4E3D6C'], [0.6, '#1B1627'], [1, '#131318']]),
      fade: lin(0, 380, 0, H, [[0, '#131318', 0], [1, '#131318', 1]]),
    };
    const sky = [
      L(rect(-240, -40, W + 480, HZ + 42), 'g:sky', 0),
      L(spark(70, 64, 9), '#FFF1D6', 0, { anim: 'twinkle:3.2:0' }),
      L(circ(334, 52, 3), '#FFF1D6', 0, { anim: 'twinkle:4:1.2' }),
      L(circ(52, 150, 2.2), '#FFF1D6', 0, { op: 0.7 }),
      L(circ(362, 138, 2), '#FFF1D6', 0, { op: 0.6, anim: 'twinkle:3.6:2.1' }),
      L(circ(252, 34, 1.6), '#FFF1D6', 0, { op: 0.8 }),
      L(spark(300, 100, 5), '#FFF1D6', 0, { op: 0.85 }),
      ...(stars ? [L(circ(128, 42, 1.8), '#FFF1D6', 0, { op: 0.8, anim: 'twinkle:3:.6' }), L(spark(390, 70, 6), '#FFF1D6', 0), L(circ(20, 90, 1.6), '#FFF1D6', 0, { op: 0.7 }), L(circ(190, 120, 1.4), '#FFF1D6', 0, { op: 0.6, anim: 'twinkle:4.4:1.8' })] : []),
    ];
    const far = [
      L(circ(sunX, sunY + 6, 210), 'g:glow', 1, o.sunAnim ? { anim: o.sunAnim } : {}),
      L(circ(sunX, sunY, sunR), 'g:sun', 1, o.sunAnim ? { anim: o.sunAnim } : {}),
      L(HILL_L, '#5A4A7E', 1), L(HILL_R, '#5A4A7E', 1),
    ];
    const ground = [L(rect(-240, HZ, W + 480, H - HZ + 40), 'g:ground', 1)];
    return { grads, sky, far, ground, fade: [L(rect(-240, 380, W + 480, 140), 'g:fade', 2, { fade: true })] };
  }
  // the fingerboard running to the horizon, strings as road markings
  function board(grads, withHead = true) {
    grads.board = lin(0, HZ, 0, H, [[0, '#2C2248'], [1, '#0E0D13']]);
    grads.str = lin(0, HZ, 0, H, [[0, '#FFE3AA'], [1, '#E9DDFF']]);
    grads.head = lin(0, 190, 0, HZ, [[0, '#241B3A'], [1, '#1A1428']]);
    const out = [L(poly([[193.8, HZ], [218.2, HZ], [311, H], [101, H]]), 'g:board', 2)];
    for (let i = 0; i < 4; i++) { const xt = 206 + (i - 1.5) * 5.8, xb = 206 + (i - 1.5) * 43.6; out.push(L(poly([[xt - 0.9, HZ], [xt + 0.9, HZ], [xb + 3.5, H], [xb - 3.5, H]]), 'g:str', 2)); }
    if (withHead) { const hd = head(206, HZ, 3.68); out.push(L(hd.pegbox, 'g:head', 2), ...hd.pegs.map(d => L(d, 'g:head', 2)), L(hd.scroll, 'g:head', 2), S(hd.groove, '#F2B57A', 4.2, 2, { op: 0.9 })); }
    return out;
  }
  // silhouette helper: a 200×120 stamp placed at ox, oy with scale s
  const stamp = (ox, oy, s) => ({ R: (x, y, w, h) => rect(ox + x * s, oy + y * s, w * s, h * s), G: pts => poly(pts.map(([x, y]) => [ox + x * s, oy + y * s])), A: (x, y, w, h) => { const X = ox + x * s, Y = oy + y * s, ww = w * s, hh = h * s; return `M${r1(X)} ${r1(Y + hh)}v${r1(-(hh - ww / 2))}a${r1(ww / 2)} ${r1(ww / 2)} 0 0 1 ${r1(ww)} 0v${r1(hh - ww / 2)}Z`; } });
  function takt(x, y, s, depth, o = {}) {
    const T = (px, py) => [x + (px - 12) * s, y + (py - 12) * s];
    const th = -20 * Math.PI / 180, c = Math.cos(th), sn = Math.sin(th), k = 0.5523, a = 5.4, b = 3.8, cx = 10.6, cy = 17.4;
    const E = (px, py) => P(T(cx + px * c - py * sn, cy + px * sn + py * c));
    const head = `M${E(a, 0)}C${E(a, k * b)} ${E(k * a, b)} ${E(0, b)}C${E(-k * a, b)} ${E(-a, k * b)} ${E(-a, 0)}C${E(-a, -k * b)} ${E(-k * a, -b)} ${E(0, -b)}C${E(k * a, -b)} ${E(a, -k * b)} ${E(a, 0)}Z`;
    const stem = `M${P(T(15.5, 15.5))}L${P(T(15.5, 9.5))}C${P(T(15.5, 6.5))} ${P(T(18, 5.5))} ${P(T(19.5, 4.2))}`;
    const flag = `M${P(T(18, 3.2))}L${P(T(20.2, 4.6))}L${P(T(18.8, 6.8))}`;
    return [L(circ(x, y, 14 * s), '#C4ADFF', depth, { ...o, op: 0.22 }), S(stem, '#E9DDFF', 2.4 * s, depth, o), S(flag, '#E9DDFF', 2.2 * s, depth, o), L(head, '#E9DDFF', depth, o)];
  }
  function hero(x, y, s, depth, o = {}) {
    return [L(ell(x + 2 * s, y + 1, 11 * s, 2.6 * s), '#140F1E', depth, { ...o, op: 0.45 }),
      L(poly([[x - 6 * s, y], [x - 7 * s, y - 23 * s], [x + 7 * s, y - 23 * s], [x + 6 * s, y]]), '#2A2140', depth, o),
      L(rect(x - 5 * s, y - 4 * s, 3.4 * s, 4 * s), '#2A2140', depth, o), L(rect(x + 1.6 * s, y - 4 * s, 3.4 * s, 4 * s), '#2A2140', depth, o),
      L(circ(x, y - 27.5 * s, 4.6 * s), '#1B1627', depth, o), L(ell(x, y - 24 * s, 4.2 * s, 1.6 * s), '#2A2140', depth, o),
      L(rr(x + 7.5 * s, y - 17 * s, 6.5 * s, 18 * s, 2 * s), '#5B43B8', depth, o), L(rect(x + 9 * s, y - 15 * s, 1.4 * s, 12 * s), '#FFFFFF', depth, { ...o, op: 0.18 })];
  }

  const SCENES = {
    // 36a — the icon unfolded into a landscape: the sun sets behind the scroll
    welcome: () => { const b = base({ sunY: 262, sunR: 80, sunAnim: 'sink:20:14' }); return { ...b, near: board(b.grads) }; },
    // setup 5a–5c — the same road, the sun lower with every step
    setup: (o = {}) => { const st = o.step || 1; const b = base({ sunY: [318, 334, 352][st - 1], glowA: [0.5, 0.42, 0.32][st - 1], stars: st >= 2 }); return { ...b, near: board(b.grads) }; },
    // 36b — the phone on the music stand, its screen the only bright spot
    live: (o = {}) => {
      const b = base({ sunY: 286 }), g = b.grads, zone = o.zone || 'zone';
      g.desk = lin(0, 176, 0, 338, [[0, '#3A2F5A'], [1, '#1E1830']]);
      g.paper = lin(0, 188, 0, 328, [[0, '#D8CCE0'], [1, '#A99BC0']]);
      g.lightZ = rad(206, 240, 220, [[0, zone, 0.5], [0.45, zone, 0.16], [1, zone, 0]]);
      g.screenZ = rad(206, 236, 120, [[0, zone, 0.55], [1, '#131318', 1]]);
      let staff = '', heads = '';
      [[90, 203], [213, 324]].forEach(([x0, x1], pg) => { for (let st = 0; st < 5; st++) { const y0 = 202 + st * 25; for (let l = 0; l < 5; l++) staff += rect(x0 + 6 - (pg ? 0 : st * 0.6), y0 + l * 3.2, x1 - x0 - 12 + (pg ? st * 0.6 : 0), 1.1); for (let n = 0; n < 5; n++) { const nx = x0 + 18 + n * 20 + ((st + n) % 2) * 4, ny = y0 + ((n * 3 + st * 2) % 5) * 3.2; heads += ell(nx, ny, 2.6, 2); } } });
      const cx = 206, cy = 236;
      const ring = circ(cx, cy, 34) + circ(cx, cy, 28);
      const dot = circ(cx, cy, 8);
      const up = poly([[cx, cy - 16], [cx + 13, cy + 2], [cx + 5, cy + 2], [cx + 5, cy + 14], [cx - 5, cy + 14], [cx - 5, cy + 2], [cx - 13, cy + 2]]);
      const down = poly([[cx, cy + 16], [cx + 13, cy - 2], [cx + 5, cy - 2], [cx + 5, cy - 14], [cx - 5, cy - 14], [cx - 5, cy - 2], [cx - 13, cy - 2]]);
      const mk = o.mark;
      const marks = mk ? [L(mk === 'dot' ? dot : mk === 'up' ? up : down, zone, 2)] : [L(dot, 'zone', 2, { anim: 'mark:dot' }), L(down, 'zone', 2, { anim: 'mark:down' }), L(up, 'zone', 2, { anim: 'mark:up' })];
      const near = [
        L(rect(199, 340, 14, 160), '#1B1627', 2),
        L(poly([[78, 176], [334, 176], [346, 338], [66, 338]]), 'g:desk', 2),
        L(poly([[90, 188], [203, 188], [203, 328], [82, 328]]), 'g:paper', 2),
        L(poly([[209, 188], [322, 188], [330, 328], [209, 328]]), 'g:paper', 2),
        L(staff, '#6E6488', 2), L(heads, '#4A4262', 2),
        L(circ(206, 240, 220), 'g:lightZ', 2),
        L(rect(60, 336, 292, 16), '#15111F', 2), L(rect(60, 336, 292, 2.5), '#4A4262', 2),
        L(rr(154, 150, 104, 188, 14), '#0E0D13', 2),
        L(rr(159, 155, 94, 178, 10), 'g:screenZ', 2),
        L(ring, zone, 2, { evenOdd: true }), ...marks,
      ];
      return { ...b, near };
    },
    // 36c — from the lit window of one's own room, a road to the halls on the horizon
    road: () => {
      const b = base({ sunX: 250, sunY: 300 }), g = b.grads;
      g.road = lin(0, HZ, 0, 414, [[0, '#FFE3AA'], [1, '#B7A6DA']]);
      g.win = rad(40, 376, 110, [[0, '#FFD98A', 0.55], [1, '#FFD98A', 0]]);
      const vi = stamp(258, 300 - 110 * 0.52, 0.52), mi = stamp(26, 300 - 110 * 0.5, 0.5), SIL = '#3B2F5E', LIT = '#FFD98A';
      const halls = [
        L(vi.R(30, 60, 140, 50) + vi.G([[26, 60], [174, 60], [162, 46], [38, 46]]) + vi.G([[80, 46], [120, 46], [100, 30]]), SIL, 1),
        L([0, 1, 2, 3, 4].map(i => vi.R(46 + i * 26, 70, 8, 26)).join(''), LIT, 1, { op: 0.75 }),
        L(mi.R(20, 50, 160, 60) + mi.R(44, 34, 112, 16) + mi.G([[70, 34], [130, 34], [100, 20]]), SIL, 1),
        L([0, 1, 2, 3, 4].map(i => mi.A(34 + i * 28, 72, 16, 30)).join(''), LIT, 1, { op: 0.7 }),
      ];
      const hs = stamp(-30, 300, 1);
      const rc = cat([[100, 416], [150, 394], [214, 368], [250, 344], [246, 322], [232, 303]], 10);
      const hop = { anim: 'walk' };
      const near = [
        L(circ(40, 376, 110), 'g:win', 2),
        L(hs.G([[40, 110], [40, 60], [100, 24], [160, 60], [160, 110]]), '#1E1830', 2),
        L(hs.G([[34, 62], [100, 20], [166, 62], [160, 66], [100, 30], [40, 66]]), '#2C2248', 2),
        L(hs.R(60, 66, 20, 20), '#FFD98A', 2), L(hs.R(69, 66, 2, 20) + hs.R(60, 75, 20, 2), '#E0A86A', 2),
        L(hs.R(120, 70, 20, 40), '#F6BC76', 2, { op: 0.9 }),
        L(ribbon(rc, p => Math.max(2, (p[1] - HZ) * 0.3)), 'g:road', 2),
        ...takt(238, 346, 1.0, 2, { anim: 'appear:.4' }), ...takt(252, 326, 0.8, 2, { anim: 'appear:1.1' }), ...takt(240, 309, 0.6, 2, { anim: 'appear:1.8' }),
        ...hero(172, 398, 1.35, 2, hop),
      ];
      return { ...b, far: [...b.far, ...halls], near };
    },
    // 36d — the phone as a small lit house: everything of one's own lies inside; the copy goes to a folder
    data: () => {
      const b = base({ sunY: 312, glowA: 0.48 }), g = b.grads;
      g.warm = rad(196, 268, 180, [[0, '#FFD98A', 0.4], [1, '#FFD98A', 0]]);
      g.win = lin(0, 164, 0, 364, [[0, '#FFF1CC'], [0.6, '#FFD98A'], [1, '#F6BC76']]);
      let lines = ''; for (let i = 0; i < 5; i++) lines += rect(160, 190 + i * 8, 34 - (i === 4 ? 12 : 0), 1.6);
      const near = [
        L(circ(196, 268, 180), 'g:warm', 2),
        L(ell(200, 388, 86, 8), '#140F1E', 2, { op: 0.5 }),
        L(rr(131, 150, 132, 236, 18), '#221A3A', 2),
        L(rr(131, 150, 120, 236, 18), '#3A2F5A', 2),
        L(rr(141, 164, 100, 200, 10), 'g:win', 2),
        L(rr(152, 178, 50, 64, 3), '#FFF8E6', 2), L(lines, '#D49A62', 2), L(ell(168, 232, 3, 2.3) + ell(182, 228, 3, 2.3), '#B5672F', 2),
        L(rr(176, 252, 56, 36, 5), '#5B43B8', 2), L(circ(191, 268, 6) + circ(217, 268, 6), '#FFE3AA', 2), L(poly([[186, 288], [189, 281], [219, 281], [222, 288]]), '#3E2C8A', 2),
        L(rr(150, 300, 62, 42, 5), '#2C2248', 2), L(poly([[175, 310], [175, 332], [192, 321]]), '#FFE3AA', 2),
        L(ell(338, 384, 52, 6), '#140F1E', 2, { op: 0.5 }),
        L('M296 318h28l8 9h46v55h-82Z', '#4A3A86', 2),
        L(rr(322, 300, 32, 40, 3), '#E9DDFF', 2, { anim: 'copy' }), L(rect(328, 309, 20, 1.8) + rect(328, 315, 20, 1.8) + rect(328, 321, 13, 1.8), '#8A78C9', 2, { anim: 'copy' }),
        L('M290 336h92l-6 46h-80Z', '#8A78C9', 2), L('M290 336h92l-.6 4.5h-91Z', '#A792E0', 2),
      ];
      const sh = d => d.replace(/([ML])([-\d.\s,]+)/g, (m, c, nums) => { const v = nums.trim().split(/[\s,]+/).map(Number); for (let i = 1; i < v.length; i += 2) v[i] = r1(v[i] + 22); return c + v.join(' '); });
      const folderAt = near.findIndex(l => l.d.startsWith('M286 384'));
      return { ...b, near: near.map((l, i) => i > 0 && i < folderAt ? { ...l, d: sh(l.d) } : l) };
    },
  };
  function scene(key, o) { const s = SCENES[key](o); return { key, grads: s.grads, layers: [...s.sky, ...s.far, ...s.ground, ...s.near, ...s.fade] }; }
  window.OnboardingArt = { W, H, HZ, SAFE: { x: 60, y: 140, w: 292, h: 300 }, scene, keys: Object.keys(SCENES) };
})();
