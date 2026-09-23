// Локации во весь экран — серия 31.
//
// The handoff drew the postcards in the strip 0…260. The owner opens a location full screen and wants
// what the Дом already gives: the picture fills the phone. So every scene is continued in one grammar:
//
//   frame  −420 … 600  (412 wide), the postcard 0…260 in the middle of it;
//   above  the high sky (outside) or the ceiling over our heads (inside);
//   below  the same ground plane coming to the viewer.
//
// The rule that makes the continuation cheap: EVERY scene has one horizon line (the vanishing height
// of its floor plane). Below it, anything on that plane grows with (y − horizon); the picture never
// needs a second drawing, only the same plane carried on. Above it, the sky or the ceiling opens out
// with the same slopes the postcard already uses.
//
// Nothing in the layer format changes. One new fill token is asked for: `SKYH` — a vertical gradient
// skyHigh → sky over −420…0 (the twin of `SKY`, which is sky → skyLow over 0…190).
// Layers with fill `star` / `cloud` are what the app's own rule draws: the exporter should skip them,
// they are here so the design can be judged.

export const FRAME = { top: -420, bottom: 600, w: 412, strip: [0, 260], centre: 130 };
export const SCREENS = { portrait: [412, 915], small: [360, 640], tall: [412, 961], land: [915, 412], card: [412, 260] };

// ---------------------------------------------------------------------------------------------
// the handoff's own code, run as the exporter runs it: one source of truth, nothing copied by hand
let _art;
export async function art() {
  if (_art) return _art;
  const get = async p => { const r = await fetch(p); if (!r.ok) throw new Error(p + ': ' + r.status); return r.text(); };
  const [html, extra, stage] = await Promise.all([get(encodeURI('Путешествие.dc.html')), get('extra-scenes.js'), get('stage-scenes.js')]);
  const cut = (a, b) => { const i = html.indexOf(a), j = html.indexOf(b, i); if (i < 0 || j < 0) throw new Error(`the handoff has changed: «${a}» … «${b}»`); return html.slice(i, j); };
  const src = [
    cut('const hex2 =', 'let gid = 0;'),
    cut('const SCENES =', 'const scene ='),
    extra, stage,
    'return { hex2, toC, mix, BASE, LOCPAL, palOf, AER, R, RR, E, C, PG, ARCH, L, SKYL, hero, lamp, tree, fir, rep, BIRD, flock, moving, ride, EXTRA_INSERTS, EXTRA_LOCPAL, SCENES: { ...SCENES, ...EXTRA_SCENES } };',
  ].join('\n');
  _art = new Function(src)();
  return _art;
}

// ---------------------------------------------------------------------------------------------
let _sys;
export async function system() { return _sys || (_sys = make(await art())); }

function make(A) {
  const { R, RR, E, C, PG, ARCH, L, rep, lamp, tree, fir, BIRD, mix, BASE, LOCPAL, EXTRA_LOCPAL, AER, SCENES, EXTRA_INSERTS } = A;

  // ---- colours the continued frame needs (merged over palOf; `skyHigh` is the one new sky token)
  const PAL_EXT = {
    eve: { skyHigh: '#171436', star: '#FFF6DC', cloud: '#453E6E', cloudLit: '#5B5286', bird: '#221D31', birdSky: '#6A5F94', moon: '#F6EFD8', haze: 'rgba(224,138,99,.20)' },
    day: { skyHigh: '#3D7FD4', star: 'rgba(255,255,255,0)', cloud: '#FFFFFF', cloudLit: '#FFFFFF', bird: '#41404E', birdSky: '#55627A', moon: 'rgba(255,255,255,.55)', haze: 'rgba(211,229,245,.35)' },
  };
  // the handoff's palOf() knows only LOCPAL: the places drawn after it live in EXTRA_LOCPAL, as the exporter
  // merges them. PAL_EXT goes under the location, not over it: a hall may already own a token called `cloud`.
  const pal = (loc, mode) => ({ ...BASE[mode], ...PAL_EXT[mode], ...(LOCPAL[loc] || {}), ...(EXTRA_LOCPAL[loc] || {}) });

  // ---- deterministic scatter, so a sky is the same sky every time
  const rnd = s => () => (s = (s * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;

  // =============================================================================================
  // ВЫСОКОЕ НЕБО  (outside, −420 … 0)
  // =============================================================================================
  // the gradient. Stars and clouds are the app's rule — the numbers are in the dev section.
  const SKYH = () => [L('SKYH', R(-600, -460, 1600, 460), 0)];

  // stars: the whole high sky, −430…40, thinning towards the horizon; 44 of them, three sizes
  const stars = (n = 44) => { const r = rnd(7); return rep(n, i => { const y = -430 + 470 * Math.pow(r(), 1.45), x = -20 + 452 * r(), s = .7 + 1.5 * r() * (1 - (y + 430) / 560); return L('star', C(+x.toFixed(1), +y.toFixed(1), +s.toFixed(2)), 0, { op: +(.35 + .6 * r()).toFixed(2), anim: `blink:${(2.4 + 3.6 * r()).toFixed(1)}` }); }); };
  // clouds: five, drifting, the higher the paler and the slower; ry stays flat, they are seen edge-on
  const CLOUDS = [[-340, 96, 150, .55], [-238, 74, 118, .7], [-150, 60, 96, .85], [-62, 46, 74, 1], [128, 34, 52, 1]];
  const clouds = (eve, only) => CLOUDS.filter((c, i) => !only || only.includes(i)).flatMap(([y, rx, sp, k], i) => {
    const x = 60 + ((i * 137) % 300), t = eve ? 'cloud' : 'cloud';
    return A.moving([L(t, E(x, y, rx, rx * .22), 0, { op: +(.5 + .3 * k).toFixed(2) }), L('cloudLit', E(x - rx * .22, y - rx * .07, rx * .55, rx * .15), 0, { op: +(.35 * k).toFixed(2) })], `ride:${sp / 60}:${-(x + rx + 30)}:${412 - x + rx + 30}`);
  });
  const moon = (x, y, r) => [L('rgba(255,244,214,.45)', C(x, y, r * 3.2), 0, { glow: true }), L('moon', C(x, y, r), 0), L('rgba(20,16,30,.08)', C(x + r * .42, y - r * .28, r * .78), 0)];
  // a flock high over the city: crosses the frame and comes back. Высоко птицы светлее — иначе их не видно
  const high = (spots, s = 1.9) => spots.map(([x, y, k]) => ({ ...BIRD(x, y, (k || 1) * s, -40, 492, 0), fill: 'birdSky' }));
  // a tapering spire that goes on up out of the postcard
  const spire = (x, y0, y1, w, f, fl) => [L(f, PG([[x - w, y0], [x + w, y0], [x + w * .18, y1], [x - w * .18, y1]]), 0), ...(fl ? [L(fl, PG([[x - w, y0], [x, y0], [x, y1], [x - w * .18, y1]]), 0)] : []), L(f, R(x - .7, y1 - 16, 1.4, 16), 0)];
  // A far landmark always stands on the ground: its body runs down to the horizon and the postcard's
  // own building covers what of it should not be seen. Nothing hangs in the air.
  const domeFar = (x, y, r, f, fl) => [L(f, R(x - r * .82, y, r * 1.64, 190 - y), 0), L(f, `M${x - r} ${y}a${r} ${r * 1.08} 0 0 1 ${2 * r} 0Z`, 0), L(fl, `M${x - r} ${y}a${r} ${r * 1.08} 0 0 1 ${r * .9} ${-r * .82}Z`, 0, { op: .55 }), L(f, R(x - 2, y - r - 14, 4, 15), 0), L(f, C(x, y - r - 17, 3.4), 0)];
  const slab = (x, y, w, f, win, lit) => [L(f, R(x, y, w, 190 - y), 0), ...(lit ? [L(lit, R(x, y, w * .3, 190 - y), 0, { op: .45 })] : []),
    ...(win ? rep(Math.max(1, Math.min(9, Math.round((40 - y) / 34))), r => rep(Math.max(1, Math.round(w / 20)), c => L('window', R(x + 6 + c * 20, y + 12 + r * 34, 7, 11), 0, { op: .5, anim: `flick:${5 + ((r + c) % 3)}` }))) : [])];

  // =============================================================================================
  // ПЕРЕДНИЙ ПЛАН  (outside, 260 … 600) — the same ground plane, carried to the viewer
  // =============================================================================================
  const HZ = 190;                                     // the horizon of every outdoor postcard
  const at = (X, y, hz = HZ) => 206 + (X - 206) * (y - hz) / (600 - hz);   // where a bottom-edge point X is at height y
  const rows = (y0, gap0, grow = 1.26) => { const o = []; let y = y0, g = gap0; while (y < 640) { o.push(+y.toFixed(1)); y += g; g *= grow; } return o; };
  // the plane, its seams running to the horizon and its courses across it
  const plane = (o = {}) => {
    const y0 = o.y0 ?? 258, f = o.fill || 'ground', sh = o.shade || 'groundShade', n = o.n ?? 13;
    const l = [L(f, R(-320, y0, 1052, 640 - y0), 2)];
    if (o.seams !== false) l.push(...rep(n + 1, i => { const X = -320 + i * (1052 / n); return L(sh, PG([[+at(X, y0).toFixed(1), y0], [+at(X + 3.4, y0).toFixed(1), y0], [+at(X + 26, 640).toFixed(1), 640], [+at(X, 640).toFixed(1), 640]]), 2, { op: .45 }); }));
    if (o.courses !== false) l.push(...rows(y0 + (o.gap0 ?? 13), o.gap0 ?? 13).map(y => L(sh, R(-320, y, 1052, 1.2 + (y - y0) / 150), 2, { op: .4 })));
    l.push(L('rgba(255,255,255,.05)', R(-320, y0, 1052, 26), 2), L('rgba(20,16,30,.16)', R(-320, 470, 1052, 170), 2));
    return l;
  };
  // water coming to the viewer: long lights drifting across it
  const nearWater = (o = {}) => {
    const y0 = o.y0 ?? 258, l = [L(o.fill || 'water', R(-320, y0 - 4, 1052, 644 - y0), 1)];
    l.push(...rows(y0 + 16, 16, 1.3).flatMap((y, i) => rep(3, j => { const w = 60 + ((i * 53 + j * 97) % 160) * (1 + (y - y0) / 220); const x = -140 + ((i * 211 + j * 331) % 620); return L(o.lit || 'waterLit', RR(x, y, w, 1.6 + (y - y0) / 260, 1), 2, { op: +(.16 + .2 * ((i + j) % 3) / 2).toFixed(2), anim: `sway:${(6 + (i % 4) * 2)}:${(7 + (j * 2))}` }); })));
    if (o.moon) l.push(...rows(y0 + 22, 22, 1.28).map((y, i) => L('rgba(255,217,138,.22)', RR(206 - (26 + i * 9), y, 52 + i * 18, 2.4 + i * .5, 1.4), 2, { anim: `sway:${5 + i}:8` })));
    return l;
  };
  // a stone parapet along the water, seen from above: the top slab, the balusters, the shadow under it
  const parapet = (y, o = {}) => [L(o.fill || 'groundLit', R(-320, y, 1052, 9), 2), L(o.shade || 'groundShade', R(-320, y + 9, 1052, 5), 2),
    ...rep(23, i => L(o.fill || 'groundLit', R(-320 + i * 46, y + 14, 14, 20), 2, { op: .9 })), L(o.shade || 'groundShade', R(-320, y + 34, 1052, 7), 2), L('rgba(20,16,30,.22)', R(-320, y + 41, 1052, 9), 2)];
  // people who cross the foreground and come back from the other side
  const walker = (x, y, s, sp, o = {}) => A.moving([L('rgba(20,16,30,.30)', E(x + 2 * s, y + 1, 10 * s, 2.6 * s), 2),
    L(o.coat || 'hero', PG([[x - 6 * s, y], [x - 7.4 * s, y - 22 * s], [x + 7.4 * s, y - 22 * s], [x + 6 * s, y]]), 2),
    L(o.coat || 'hero', R(x - 5 * s, y - 4 * s, 3.4 * s, 4 * s), 2), L(o.coat || 'hero', R(x + 1.6 * s, y - 4 * s, 3.4 * s, 4 * s), 2),
    L(o.hair || 'heroHair', C(x, y - 26.5 * s, 4.4 * s), 2)], `ride:${sp}:${sp > 0 ? -(x + 40 * s) : -(x + 40 * s)}:${412 - x + 40 * s}`);
  // pigeons on the pavement: they peck and shuffle (new movement, see dev)
  const pigeons = (spots, s = 1) => spots.flatMap(([x, y, k = 1], i) => A.moving([
    L('rgba(20,16,30,.26)', E(x, y + 1.4, 6 * s * k, 1.6 * s * k), 2),
    L('bird', E(x, y - 4 * s * k, 6.2 * s * k, 4 * s * k), 2), L('bird', C(x - 5.4 * s * k, y - 8.6 * s * k, 2.6 * s * k), 2),
    L('bird', PG([[x - 8.4 * s * k, y - 8.6 * s * k], [x - 5.6 * s * k, y - 9.6 * s * k], [x - 5.6 * s * k, y - 7.6 * s * k]]), 2),
    L('rgba(255,255,255,.22)', E(x + 1.6 * s * k, y - 5.4 * s * k, 3 * s * k, 1.6 * s * k), 2),
    L('bird', R(x - .8 * s * k, y - 1.4 * s * k, 1.4 * s * k, 1.6 * s * k), 2)], `peck:${(2.6 + .7 * i).toFixed(1)}:14`));
  const bench = (x, y, s, o = {}) => [L('rgba(20,16,30,.26)', E(x, y + 2, 34 * s, 5 * s), 2),
    L(o.wood || 'trunk', R(x - 34 * s, y - 20 * s, 68 * s, 5 * s), 2), L(o.wood || 'trunk', R(x - 34 * s, y - 12 * s, 68 * s, 5 * s), 2),
    L(o.wood || 'trunk', R(x - 34 * s, y - 30 * s, 68 * s, 5 * s), 2), L(o.metal || 'lamp', R(x - 30 * s, y - 20 * s, 4 * s, 22 * s), 2), L(o.metal || 'lamp', R(x + 26 * s, y - 20 * s, 4 * s, 22 * s), 2),
    L(o.metal || 'lamp', PG([[x - 32 * s, y - 34 * s], [x - 28 * s, y - 34 * s], [x - 26 * s, y - 12 * s], [x - 30 * s, y - 12 * s]]), 2), L(o.metal || 'lamp', PG([[x + 28 * s, y - 34 * s], [x + 32 * s, y - 34 * s], [x + 30 * s, y - 12 * s], [x + 26 * s, y - 12 * s]]), 2)];
  const bollard = (x, y, s) => [L('rgba(20,16,30,.26)', E(x, y + 1, 7 * s, 2 * s), 2), L('lamp', RR(x - 5 * s, y - 26 * s, 10 * s, 26 * s, 4 * s), 2), L('rgba(255,255,255,.14)', R(x - 3 * s, y - 22 * s, 2 * s, 16 * s), 2)];
  // the lamps of the postcard, but near: the glow is what lights the foreground in the evening
  const bigLamp = (x, y, s, eve) => [...lamp(x, y, s, 2, eve), ...(eve ? [L('rgba(255,196,110,.30)', E(x, y + 6, 58 * s, 16 * s), 2, { anim: 'flick:5' })] : [])];
  const puddle = (x, y, rx, eve) => eve ? [L('rgba(20,16,30,.24)', E(x, y, rx, rx * .30), 2), L('rgba(255,196,110,.30)', E(x, y, rx * .5, rx * .12), 2, { anim: 'sway:4:9' })] : [L('rgba(255,255,255,.10)', E(x, y, rx, rx * .30), 2)];
  // what a lamp throws towards the viewer: the foreground is never a flat field
  const cast = (x, y, len, w) => [L('rgba(20,16,30,.16)', PG([[x - w, y], [x + w, y], [+at(206 + (x - 206) * 2.1 + w * 5, y + len).toFixed(1), y + len], [+at(206 + (x - 206) * 2.1 - w * 5, y + len).toFixed(1), y + len]]), 2)];
  const rails = (x0, o = {}) => rep(2, i => { const X = x0 + i * 300; return [L(o.fill || 'groundLit', PG([[+at(X - 2, 262).toFixed(1), 262], [+at(X + 2, 262).toFixed(1), 262], [+at(X + 5, 640).toFixed(1), 640], [+at(X - 5, 640).toFixed(1), 640]]), 2, { op: .7 })]; });

  // =============================================================================================
  // ПОТОЛОК И ПАРТЕР  (inside)
  // =============================================================================================
  // A shoebox: the ceiling of the postcard runs from the back wall (150…262 at y 78) to the frame
  // edge at y 0 — carried on it opens over our heads. Half-width at y: 206 + 1.923·(−y).
  const ceilHall = (o = {}) => {
    const hw = y => 206 + 1.923 * (-y);
    const l = [L(o.fill || 'ceil', R(-620, -460, 1652, 462), 1)];
    // the beams across, the gap between them growing towards us
    let y = -6, g = 24;
    const ys = []; while (y > -440) { ys.push(y); y -= g; g *= 1.42; }
    ys.forEach((yy, i) => { const w = hw(yy), t = 2.6 + i * 1.5; l.push(L(o.shade || 'ceilShade', R(206 - w, yy - t, 2 * w, t), 1, { op: .5 })); });
    // the lengthwise ribs, running to the corners of the back wall
    l.push(...rep(o.ribs ?? 7, i => { const X = -560 + i * (1120 / ((o.ribs ?? 7) - 1)); const k = y => 206 + (X - 206) * (-y) / 420; return L(o.shade || 'ceilShade', PG([[+k(-420).toFixed(1), -420], [+(k(-420) + 26).toFixed(1), -420], [206 + (X - 206) * .012 + 2.2, 0], [206 + (X - 206) * .012 - 2.2, 0]]), 1, { op: .34 }); }));
    // the coffers: squares between beam and beam, shrinking away
    if (o.coffers !== false) ys.slice(0, 5).forEach((yy, i) => { const w = hw(yy) * .92, n = 6 + i * 2, y2 = ys[i + 1] ?? -440; rep(n, c => l.push(L(o.shade || 'ceilShade', R(+(206 - w + c * (2 * w / n) + 6).toFixed(1), +(yy - (yy - y2) * .82).toFixed(1), +(2 * w / n - 12).toFixed(1), +((yy - y2) * .62).toFixed(1)), 1, { op: .22 }))); });
    l.push(L('rgba(20,16,30,.16)', R(-620, -460, 1652, 150), 1));
    return l;
  };
  // the rod of a chandelier now has somewhere to hang from: it reaches the ceiling above
  const chandNear = (x, y, r, o = {}) => [L(o.rod || 'railShade', R(x - .9, -460, 1.8, y - r + 460), 1),
    L('rgba(255,196,110,.5)', C(x, y, r * 3.2), 1, { glow: true, anim: `flick:${o.p || 4}` }),
    ...(o.crystal ? [L(o.body || 'chandelier', PG([[x - r, y - r * .4], [x + r, y - r * .4], [x, y + r * 1.5]]), 1), L(o.body || 'chandelier', E(x, y - r * .4, r, r * .45), 1)]
      : [L(o.body || 'chandelier', C(x, y, r), 1), L(o.body || 'chandelier', E(x, y - r * .4, r * 1.15, r * .5), 1)]),
    ...rep(o.drops ?? 7, i => L('rgba(255,255,255,.85)', C(x + (i - (o.drops ?? 7 - 1) / 2) * r * .42, y + r * (o.crystal ? .9 : .7), r * .13), 1, { anim: `flash:${(3 + i * .6).toFixed(1)}` })),
    L('rgba(255,255,255,.8)', C(x - r * .32, y - r * .34, r * .3), 1)];
  // the stalls carried to the viewer: the same floor plane, the same rule as the postcard's rows —
  // the row at y stands (y − vy) / (y0 − vy) times as large as the last row of the postcard
  const stallsNear = (o = {}) => {
    const vy = o.vy ?? 134, y0 = o.y0 ?? 252, hw0 = o.hw0 ?? 198, hh0 = o.hh0 ?? 9, ratio = 1 + (o.gap ?? 14) / (y0 - vy);
    const l = [L(o.floor || 'floor', R(-320, y0 - 14, 1052, 680), 2)];
    let y = y0;
    for (let i = 0; i < 16 && y < 660; i++) {
      y = vy + (y - vy) * ratio;
      const s = (y - vy) / (y0 - vy), hw = hw0 * s, hh = hh0 * s, n = Math.min(18, Math.round(2 * hw / (16 * s)));
      l.push(L(o.seat || 'seat', R(+(206 - hw).toFixed(1), +(y - hh).toFixed(1), +(2 * hw).toFixed(1), +hh.toFixed(1)), 2),
        L(o.dark || 'seatDark', R(+(206 - hw).toFixed(1), +(y - hh).toFixed(1), +(2 * hw).toFixed(1), +(hh * .28).toFixed(1)), 2),
        L('rgba(255,255,255,.07)', R(+(206 - hw).toFixed(1), +(y - hh * .72).toFixed(1), +(2 * hw).toFixed(1), +(hh * .18).toFixed(1)), 2),
        ...rep(n, j => L(o.dark || 'seatDark', R(+(206 - hw + (j + .5 * (i % 2)) * (2 * hw / n)).toFixed(1), +(y - hh).toFixed(1), +(1.4 * s).toFixed(1), +hh.toFixed(1)), 2)),
        ...(o.aisle ? [L(o.floor || 'floor', R(+(206 - 10 * s).toFixed(1), +(y - hh).toFixed(1), +(20 * s).toFixed(1), +(hh * 2.4).toFixed(1)), 2)] : []));
    }
    l.push(L('rgba(20,16,30,.20)', R(-320, 500, 1052, 160), 2));
    return l;
  };
  // a row of seats bent round the stage (the grammar of the halls seen from the stage)
  const arcRow = (y, hw, hh, rise) => `M${(206 - hw).toFixed(1)} ${y.toFixed(1)}q${hw.toFixed(1)} ${(-2 * rise).toFixed(1)} ${(2 * hw).toFixed(1)} 0v${(-hh).toFixed(1)}q${(-hw).toFixed(1)} ${(-2 * rise).toFixed(1)} ${(-2 * hw).toFixed(1)} 0Z`;
  // the boards under our feet, when the postcard is seen from the stage
  const boardsNear = (o = {}) => {
    const l = [L(o.dark || 'hallDark', R(-320, 256, 1052, 390), 2), L(o.board || 'wood', R(-320, 262, 1052, 384), 2)];
    l.push(...rep(13, i => { const X = -320 + i * 84; return L(o.shade || 'woodShade', PG([[+at(X, 262, 150).toFixed(1), 262], [+at(X + 4, 262, 150).toFixed(1), 262], [+at(X + 30, 640, 150).toFixed(1), 640], [+at(X, 640, 150).toFixed(1), 640]]), 2, { op: .5 }); }));
    l.push(L('rgba(255,255,255,.06)', R(-320, 262, 1052, 6), 2), L('rgba(20,16,30,.22)', R(-320, 520, 1052, 126), 2));
    return l;
  };
  // dust in the beam of light — the one thing that says an empty hall is not a photograph
  const motes = (x, y, w, hh, n = 16, s = 5) => { const r = rnd(s); return rep(n, i => L('rgba(255,244,214,.55)', C(+(x + r() * w).toFixed(1), +(y + r() * hh).toFixed(1), +(.7 + r() * 1.1).toFixed(2)), 1, { op: +(.25 + .5 * r()).toFixed(2), anim: `rise:${(3 + 5 * r()).toFixed(1)}:${(14 + 26 * r()).toFixed(0)}` })); };
  const beam = (x0, y0, x1, y1, w) => [L('rgba(255,244,214,.10)', PG([[x0 - w * .3, y0], [x0 + w * .3, y0], [x1 + w, y1], [x1 - w, y1]]), 1)];

  // =============================================================================================
  // ЧТО У КАЖДОГО ГОРОДА СВОЁ
  // =============================================================================================
  // high: what stands in the high sky of this city · fore: the kind of ground that comes to us
  const stem = (x, w, top, f = 'far', lit) => [L(f, R(x - w / 2, top, w, 190 - top), 0), ...(lit ? [L(lit, R(x - w / 2, top, w * .32, 190 - top), 0, { op: .45 }) ] : [])];
  const OUT = {
    vienna: { fore: 'street', tram: true, high: (eve) => [
      ...(eve ? moon(330, -300, 26) : []),
      ...domeFar(74, -46, 40, 'far', 'farLit'), ...spire(74, -86, -150, 5, 'far'),                     // Karlskirche over the roofs
      ...slab(292, -120, 30, 'far', true, 'farLit'), ...spire(307, -120, -196, 7, 'far', 'farLit'),          // the Rathaus tower, far behind
      ...high([[150, -230], [166, -218, .8], [136, -212, .9], [352, -168, .7]]) ] },
    cremonaOut: { fore: 'plaza', high: () => [
      ...stem(88, 30, 34, 'brick', 'brickLit'), ...spire(88, 34, -196, 15, 'brick', 'brickLit'), L('marble', R(71, 22, 34, 7), 0), L('gold', C(88, -200, 4), 0),   // the Torrazzo goes on up
      ...rep(4, i => L('brickShade', R(76, -40 - i * 36, 24, 1.6), 0, { op: .5 })),
      ...high([[236, -252], [252, -240, .8], [222, -234, .9], [120, -300, .7]]) ] },
    milanOut: { fore: 'street', tram: true, high: (eve) => [
      ...(eve ? moon(66, -286, 22) : []),
      L('far', PG([[238, 190], [412, 190], [412, -78], [330, -120], [246, -74]]), 0, { op: .92 }),
      ...rep(9, i => { const x = 250 + i * 18, hh = 120 + ((i * 47) % 70); return spire(x, -70 - ((i * 23) % 24), -70 - hh, 4.6 - i * .14, 'far', i % 3 ? null : 'farLit'); }),  // the spires of the Duomo
      L('gold', C(322, -196, 4.6), 0),
      ...high([[112, -214], [128, -202, .8], [98, -196, .9]]) ] },
    austria: { fore: 'meadow', high: (eve) => [
      ...(eve ? moon(300, -252, 24) : []),
      L('far', PG([[-40, 70], [40, -150], [120, -60], [190, -196], [286, -40], [340, -120], [452, 70]]), 0, { op: .96 }),         // the range above the postcard
      L('farLit', PG([[190, -196], [222, -140], [206, -142], [196, -152], [182, -140]]), 0, { op: .9 }),
      L('farLit', PG([[40, -150], [66, -108], [52, -112], [44, -120], [30, -108]]), 0, { op: .85 }),
      L('mount', PG([[286, -40], [340, -120], [382, -64], [412, -84], [452, 70], [286, 70]]), 0, { op: .85 }),
      L('mountShade', PG([[212, 80], [308, 80], [292, -98], [230, -98]]), 0, { op: .85 }),
      L('plaster', PG([[236, -104], [284, -104], [284, -134], [260, -146], [236, -134]]), 0), L('roofGreen', PG([[232, -134], [288, -134], [260, -152]]), 0), ...spire(260, -152, -186, 4, 'roofGreen'),   // Hohensalzburg on its rock
      ...high([[126, -246], [142, -234, .8], [112, -228, .9], [332, -196, .7]]) ] },
    prague: { fore: 'plaza', high: (eve) => [
      ...(eve ? moon(346, -268, 22) : []),
      ...rep(2, i => spire(104 + i * 14, 72 - i * 4, -168 + i * 26, 6.5 - i * 1.6, 'far', i ? null : 'farLit')),          // St Vitus, from the castle hill
      L('far', PG([[86, 130], [140, 130], [136, -54], [112, -70], [90, -54]]), 0, { op: .92 }),
      ...high([[250, -218], [266, -206, .8], [236, -200, .9], [60, -164, .7], [44, -176, .6]]) ] },
    leipzig: { fore: 'plaza', high: () => [
      ...spire(110, 4, -150, 5.4, 'copper', 'copperLit'), L('gold', C(110, -156, 4), 0), L('gold', PG([[110, -168], [120, -160], [110, -152], [100, -160]]), 0, { anim: 'sway:8:11' }),   // the weathervane
      ...high([[230, -206], [246, -194, .8], [218, -188, .9], [330, -250, .7], [342, -238, .6]]) ] },
    berlin: { fore: 'street', high: (eve) => [
      ...(eve ? moon(92, -314, 20) : []),
      ...stem(333.5, 7, -140), L('far', C(333.5, -166, 20), 0), L('farLit', C(328, -172, 9), 0, { op: .6 }),              // the television tower
      ...spire(333.5, -186, -300, 3.4, 'far'), L('rgba(255,120,90,.6)', C(333.5, -268, 2.4), 0, { anim: 'blink:2.2' }),
      ...rep(3, i => slab(30 + i * 54, -96 + i * 18, 34, 'far', true, 'farLit')),
      ...high([[176, -222], [192, -210, .8], [162, -204, .9], [240, -270, .7], [254, -258, .6]]) ] },        // Berlin gets its birds
    amsterdam: { fore: 'quay', gulls: true, high: (eve) => [
      ...(eve ? moon(64, -276, 22) : []),
      L('brickShade', R(316, -110, 22, 300), 0), L('brick', R(306, -110, 10, 300), 0), L('stone', R(302, -118, 40, 8), 0),
      ...spire(320, -118, -224, 8, 'roof', 'roofLit'), L('gold', C(320, -230, 4.6), 0), L('gold', PG([[320, -246], [328, -238], [320, -230], [312, -238]]), 0, { anim: 'sway:7:12' }),   // the Westerkerk crown
      ...rep(4, i => [L('far', R(34 + i * 48, 6, 42, 184), 0, { op: .55 }), L('far', PG([[30 + i * 48, 6], [80 + i * 48, 6], [55 + i * 48, -26 - ((i * 13) % 16)]]), 0, { op: .55 }), L('far', R(52 + i * 48, -18, 6, 24), 0, { op: .55 })]).flat(),
      ...high([[150, -196], [166, -184, .8], [136, -178, .9]]) ] },
    paris: { fore: 'street', high: (eve) => [
      ...(eve ? moon(74, -292, 24) : []),
      L('far', PG([[288, 190], [390, 190], [352, -120], [320, -120]]), 0, { op: .82 }), L('far', PG([[320, -120], [352, -120], [344, -212], [328, -212]]), 0, { op: .82 }),
      ...spire(336, -212, -300, 7, 'far', 'farLit'), L('far', R(300, -84, 72, 4.4), 0, { op: .82 }), L('far', R(316, -150, 40, 3.4), 0, { op: .82 }),   // the Eiffel tower across the roofs
      ...high([[130, -226], [146, -214, .8], [116, -208, .9], [258, -262, .7]]) ] },
    london: { fore: 'park', high: (eve) => [      ...(eve ? moon(330, -262, 22) : []),
      ...stem(96, 26, -30, 'dome', 'domeLit'), ...spire(96, -30, -210, 11, 'dome', 'domeLit'), L('gold', C(96, -216, 5), 0), L('gold', C(96, -128, 7), 0, { op: .8 }),   // the Albert Memorial
      ...high([[192, -206], [208, -194, .8], [178, -188, .9], [300, -248, .7]]) ] },
    spb: { fore: 'quay', gulls: true, high: (eve) => [
      ...(eve ? moon(346, -300, 24) : []),
      L('far', R(66, -96, 22, 286), 0), L('farLit', R(66, -96, 8, 286), 0), L('gold', PG([[62, -96], [92, -96], [86, -122], [68, -122]]), 0, { op: .85 }),
      ...spire(77, -122, -282, 7, 'gold'), L('gold', C(77, -290, 4), 0),                                                 // the Peter and Paul spire
      ...rep(3, i => slab(250 + i * 56, -40 - ((i * 23) % 26), 40, 'far', true)),
      ...high([[176, -232], [192, -220, .8], [162, -214, .9], [300, -186, .7], [314, -174, .6]]) ] },
    moscow: { fore: 'plaza', high: (eve) => [
      ...(eve ? moon(80, -284, 22) : []),
      L('far', R(316, -104, 54, 294), 0), L('far', R(328, -170, 30, 66), 0), ...spire(343, -170, -300, 9, 'far', 'farLit'),
      L('gold', C(343, -308, 5), 0), L('rgba(255,120,90,.7)', C(343, -256, 2.6), 0, { anim: 'blink:2.6' }),               // a high-rise of the seven
      ...rep(3, i => [L('far', R(60 + i * 40, -10, 20, 200), 0, { op: .85 }), L('roof', `M${58 + i * 40} ${-10}a${13} ${15} 0 0 1 ${26} 0Z`, 0, { op: .85 }), L('gold', R(70 + i * 40, -42, 2, 18), 0), L('gold', R(65 + i * 40, -36, 12, 2), 0)]).flat(),
      ...high([[196, -226], [212, -214, .8], [182, -208, .9]]) ] },
    newyork: { fore: 'street', high: (eve) => [
      ...(eve ? moon(200, -330, 18) : []),
      ...slab(-10, -300, 92, 'far', true, 'farLit'), ...slab(330, -250, 96, 'far', true, 'farLit'), ...slab(96, -170, 62, 'far', true),
      L('far', R(24, -348, 16, 48), 0), ...spire(32, -348, -420, 4.4, 'far'), L('rgba(255,120,90,.7)', C(32, -404, 2.6), 0, { anim: 'blink:2' }),   // the towers go out of the frame
      ...high([[222, -214], [238, -202, .8], [208, -196, .9], [268, -252, .7]]) ] },       // New York gets its birds
    buenosaires: { fore: 'plaza', high: (eve) => [
      ...(eve ? moon(92, -272, 22) : []),
      L('stoneBase', PG([[318, 190], [342, 190], [336, -230], [324, -230]]), 0), L('stoneLit', PG([[318, 190], [330, 190], [330, -230], [324, -230]]), 0, { op: .45 }),
      L('stoneBase', PG([[322, -230], [338, -230], [330, -262]]), 0),                                                    // the Obelisco down the avenue
      ...rep(4, i => slab(30 + i * 56, -110 + ((i * 29) % 40), 40, 'far', true)),
      ...high([[186, -206], [202, -194, .8], [172, -188, .9], [264, -244, .7]]) ] },
    tokyo: { fore: 'plaza', petals: true, high: (eve) => [
      ...(eve ? moon(84, -298, 24) : []),
      L('towerRed', PG([[312, 190], [364, 190], [352, -104], [326, -104]]), 0, { op: .9 }), L('towerWhite', PG([[320, -44], [358, -44], [356, -62], [322, -62]]), 0, { op: .7 }),
      L('towerRed', PG([[330, -104], [350, -104], [345, -182], [335, -182]]), 0, { op: .9 }), ...spire(340, -182, -246, 3.6, 'towerRed', 'towerWhite'),
      L('rgba(255,120,90,.8)', C(340, -226, 2.4), 0, { anim: 'blink:2.4' }),                                             // Tokyo Tower
      ...rep(3, i => slab(28 + i * 60, -132 + ((i * 37) % 44), 44, 'far', true)),
      ...high([[166, -218], [182, -206, .8], [152, -200, .9], [244, -258, .7]]) ] },       // Tokyo gets its birds too
    sydney: { fore: 'harbour', gulls: true, hz: 168, high: (eve) => [
      ...(eve ? moon(66, -268, 22) : []),
      L('far', `M236 -14Q316 -168 412 -120`, 0, { stroke: 'far', sw: 9, fillNone: true }), ...stem(239.5, 9, -22), ...stem(303.5, 9, -140),   // the Harbour Bridge
      ...rep(7, i => L('far', R(248 + i * 22, -60 - i * 14, 3.4, 40 + i * 12), 0, { op: .6 })),
      ...stem(154, 8, -96), L('far', E(154, -104, 17, 9), 0), ...spire(154, -112, -212, 4, 'far'),                        // Sydney Tower
      ...high([[96, -214], [112, -202, .8], [82, -196, .9], [340, -250, .7]]) ] },
  };

  // what comes to the viewer, by kind
  const foreOf = (key, eve) => {
    const o = OUT[key] || {}, k = o.fore || 'plaza', l = [];
    if (k === 'plaza') {
      l.push(...plane({ gap0: 12 }));
      l.push(...cast(44, 352, 200, 5), ...cast(374, 344, 210, 5));
      l.push(...bigLamp(44, 352, 1.9, eve), ...bigLamp(374, 344, 1.8, eve));
      l.push(...bench(300, 470, 1.5), ...pigeons([[120, 430, 1.5], [170, 452, 1.7], [96, 486, 2]]));
      l.push(...walker(250, 560, 2.2, 9), ...walker(60, 300, 1.3, -6, { coat: 'heroHair' }));
      if (eve) l.push(...puddle(196, 500, 74, eve), ...puddle(340, 402, 40, eve));
    } else if (k === 'park') {
      l.push(...plane({ fill: 'foliage', shade: 'foliageShade', gap0: 16, n: 9 }));
      l.push(...rep(6, i => L('foliageLit', E(-30 + i * 96, 320 + ((i * 53) % 74), 56, 8), 2, { op: .3 })));
      l.push(L('groundLit', PG([[+at(96, 262).toFixed(1), 262], [+at(316, 262).toFixed(1), 262], [+at(-60, 640).toFixed(1), 640], [+at(472, 640).toFixed(1), 640]]), 2, { op: .9 }));   // the gravel walk of the park
      l.push(...rows(276, 16).map(y => L('groundShade', R(-320, y, 1052, 1 + (y - 262) / 180), 2, { op: .25 })));
      l.push(...cast(50, 372, 190, 5), ...bigLamp(50, 372, 1.9, eve), ...bigLamp(368, 364, 1.8, eve));
      l.push(...tree(120, 392, 2, 2), ...bench(288, 486, 1.5));
      l.push(...pigeons([[168, 452, 1.7], [196, 486, 2]]), ...walker(250, 570, 2.3, 9), ...walker(74, 318, 1.4, -6, { coat: 'heroHair' }));
    } else if (k === 'street') {
      l.push(...plane({ gap0: 0, courses: false, seams: false }), L('rgba(20,16,30,.10)', R(-320, 258, 1052, 382), 2));
      l.push(...rails(96), ...rails(216));
      l.push(...rep(7, i => L('groundLit', PG([[+at(-40 + i * 84, 300).toFixed(1), 300], [+at(10 + i * 84, 300).toFixed(1), 300], [+at(24 + i * 84, 360).toFixed(1), 360], [+at(-52 + i * 84, 360).toFixed(1), 360]]), 2, { op: .55 })));  // the crossing
      l.push(L('groundLit', R(-320, 424, 1052, 10), 2, { op: .7 }), L('groundShade', R(-320, 434, 1052, 8), 2));         // the kerb of the near pavement
      l.push(...plane({ y0: 442, gap0: 20, fill: 'groundLit', shade: 'groundShade' }));
      l.push(...cast(40, 520, 130, 6), ...cast(378, 498, 150, 6));
      l.push(...bigLamp(40, 520, 2.4, eve), ...bigLamp(378, 498, 2.2, eve));
      l.push(...bollard(150, 530, 1.8), ...bollard(268, 524, 1.8));
      l.push(...walker(300, 596, 2.6, 11), ...walker(90, 470, 1.6, -7, { coat: 'heroHair' }));
      if (eve) l.push(...puddle(220, 392, 66, eve));
    } else if (k === 'quay') {
      l.push(...parapet(262, {}));
      l.push(...nearWater({ y0: 304, moon: eve }));
      l.push(...rep(4, i => L('rgba(20,16,30,.22)', E(-40 + i * 160, 320 + i * 30, 36 + i * 10, 5), 2)));
      l.push(...bigLamp(48, 300, 1.5, eve), ...bigLamp(368, 300, 1.5, eve));
      l.push(...rep(3, i => L('lamp', C(60 + i * 148, 274, 4.6), 2)), ...rep(3, i => L('lamp', R(58 + i * 148, 274, 4, 12), 2)));   // mooring rings
      l.push(...A.moving([L('boat', PG([[120, 470], [292, 470], [274, 508], [138, 508]]), 2), L('rgba(255,255,255,.14)', R(134, 474, 144, 5), 2), L('lamp', R(198, 430, 4, 40), 2)], 'ride:7:-460:520'));
      l.push(...pigeons([[300, 286, 1.1]]));
    } else if (k === 'harbour') {
      l.push(...nearWater({ y0: 262, moon: eve }));
      l.push(...rep(3, i => L('platformShade', PG([[-40 + i * 190, 470 + i * 24], [70 + i * 190, 470 + i * 24], [70 + i * 190, 640], [-40 + i * 190, 640]]), 2, { op: .55 })));
      l.push(L('platform', R(-320, 520, 1052, 26), 2), ...rep(26, i => L('platformShade', R(-320 + i * 42, 520, 4, 26), 2, { op: .6 })), L('platformShade', R(-320, 546, 1052, 8), 2));   // the boards of the quay
      l.push(...plane({ y0: 554, gap0: 26, fill: 'platform', shade: 'platformShade' }));
      l.push(...bigLamp(52, 600, 2.2, eve), ...bigLamp(360, 596, 2.1, eve));
      l.push(...pigeons([[236, 592, 2.2], [166, 578, 2]]));
    } else if (k === 'meadow') {
      l.push(...plane({ fill: 'meadow', shade: 'meadowLit', gap0: 15, n: 9 }));
      l.push(...rep(5, i => L('meadowLit', E(-20 + i * 120, 330 + ((i * 47) % 60), 60, 9), 2, { op: .35 })));
      l.push(...fir(58, 420, 2.4, 2), ...fir(368, 468, 3, 2), ...tree(120, 372, 1.8, 2));
      l.push(...bench(268, 470, 1.4, { wood: 'wood', metal: 'woodShade' }));
      l.push(...pigeons([[196, 520, 2]]), ...walker(320, 580, 2.4, -8, { coat: 'heroHair' }));
    } else { l.push(...plane({})); }
    if ((OUT[key] || {}).gulls) l.push(...[[80, 246, 1.2], [300, 234, 1], [200, 262, 1.4]].map(([x, y, s]) => BIRD(x, y, s * 1.6, -40, 492, 2)));
    if ((OUT[key] || {}).petals) l.push(...rep(9, i => L('blossom', E(30 + i * 44, 300 + ((i * 71) % 260), 3.4, 2.2), 2, { op: .8, anim: `fall:14:${280 + ((i * 53) % 90)}:640` })));
    return l;
  };

  // =============================================================================================
  // ЧТО ДЕЛАЕМ С КАЖДОЙ СЦЕНОЙ
  // =============================================================================================
  const INSIDE = {
    salzburgInt: { type: 'hall', organ: true, motes: true },
    pragueInt: { type: 'hall', organ: true, motes: true },
    leipzigInt: { type: 'nave' },
    berlinInt: { type: 'vineyard' },
    amsterdamInt: { type: 'hall', organ: true, aisle: true },
    parisInt: { type: 'horseshoe' },
    londonInt: { type: 'horseshoe', mushrooms: true },
    spbInt: { type: 'hall', crystal: true, motes: true },
    moscowInt: { type: 'hall', motes: true },
    newyorkInt: { type: 'horseshoe' },
    buenosairesInt: { type: 'horseshoe' },
    tokyoInt: { type: 'vineyard' },
    sydneyInt: { type: 'hall' },
    viennaInt: { type: 'hall', vienna: true, motes: true },
    milan: { type: 'horseshoe' },
    cremona: { type: 'room' },
    home: { type: 'skip' },
  };

  const insideLayers = (key, cfg) => {
    const t = cfg.type, l = [];
    if (t === 'hall') {
      l.push(...ceilHall({ fill: cfg.vienna ? 'cream' : 'ceil', shade: cfg.vienna ? 'creamShade' : 'ceilShade', coffers: true }));
      const hangs = cfg.crystal ? [[86, -96, 20], [326, -96, 20], [140, -250, 30], [272, -250, 30]] : [[96, -86, 17], [316, -86, 17], [150, -232, 27], [262, -232, 27]];
      hangs.forEach(([x, y, r]) => l.push(...chandNear(x, y, r, { crystal: cfg.crystal, body: cfg.vienna ? 'goldLit' : 'chandelier', rod: cfg.vienna ? 'goldShade' : 'railShade', drops: 9 })));
      if (cfg.motes) l.push(...beam(150, -420, 176, 130, 40), ...motes(120, -300, 170, 400, 22, key.length));
      return { above: l, below: stallsNear({ vy: 134, aisle: cfg.aisle, seat: cfg.vienna ? 'velvet' : 'seat', dark: cfg.vienna ? 'velvetDark' : 'seatDark', floor: cfg.vienna ? 'velvetDark' : 'floor' }) };
    }
    if (t === 'horseshoe') {
      const a = [L('ceil', R(-320, -460, 1052, 470), 1), L('ceilShade', C(206, 14, 176), 1)];
      a.push(...rep(16, i => { const th = Math.PI * (1 + i / 15); return L('ceilShade', PG([[206 + Math.cos(th) * 150, 14 + Math.sin(th) * 150], [206 + Math.cos(th) * 460, 14 + Math.sin(th) * 460], [206 + Math.cos(th + .03) * 460, 14 + Math.sin(th + .03) * 460], [206 + Math.cos(th + .03) * 150, 14 + Math.sin(th + .03) * 150]]), 1, { op: .25 }); }));
      a.push(L('rail', C(206, 14, 158), 1, { fillNone: true, stroke: 'rail', sw: 5 }), L('rail', C(206, 14, 186), 1, { fillNone: true, stroke: 'rail', sw: 2, op: .6 }));
      a.push(...rep(24, i => L('rail', C(206 + Math.cos(Math.PI * (1 + i / 23)) * 172, 14 + Math.sin(Math.PI * (1 + i / 23)) * 172, 3.4), 1, { op: .7, anim: `flash:${(3 + (i % 5) * .7).toFixed(1)}` })));
      a.push(L('rgba(20,16,30,.09)', R(-320, -460, 1052, 300), 1), L('rgba(20,16,30,.09)', R(-320, -460, 1052, 190), 1), L('rgba(20,16,30,.09)', R(-320, -460, 1052, 100), 1));
      if (cfg.mushrooms) a.push(...rep(6, i => { const x = 40 + i * 70, y = -120 - ((i * 53) % 90); return [L('ceilShade', R(x - .6, -460, 1.2, y + 460), 1, { op: .5 }), L('mushroomShade', E(x, y + 5, 34, 10), 1), L('mushroom', E(x, y, 34, 9), 1)]; }));
      const b = boardsNear({ board: 'wood', shade: 'seatDark', dark: 'hallDark' });
      b.push(L('seatDark', R(-320, 258, 1052, 5), 2), L('rail', R(-320, 256, 1052, 2), 2, { op: .5 }));
      b.push(L('lamp', R(78, 300, 3, 150), 2), L('lamp', PG([[52, 316], [110, 300], [110, 312], [52, 328]]), 2), L('rgba(255,255,255,.10)', PG([[54, 318], [108, 303], [108, 306], [54, 321]]), 2));   // the stand we play from
      return { above: a, below: b };
    }
    if (t === 'vineyard') {
      const a = [L('ceil', R(-320, -460, 1052, 466), 1)];
      a.push(...rep(9, i => { const X = -300 + i * 128; return L('ceilShade', PG([[X, -460], [X + 30, -460], [206 + (X + 15 - 206) * .18 + 3, 6], [206 + (X + 15 - 206) * .18 - 3, 6]]), 1, { op: .3 }); }));
      a.push(...rep(5, i => { const y = -30 - i * 66 * 1.3; return L('ceilLit', R(-320, y, 1052, 4 + i * 2), 1, { op: .28 }); }));
      a.push(...rep(7, i => { const x = 24 + i * 62, y = -66 - ((i * 61) % 130); return [L('ceilLit', R(x - .5, -460, 1, y + 460), 1, { op: .4 }), L('cloudShade', E(x, y + 4, 30, 8), 1), L('cloud', E(x, y, 30, 7), 1)]; }));   // the hanging «clouds», over our heads now
      a.push(...rep(10, i => { const x = 18 + i * 42, y = -230 - ((i * 37) % 110); return [L('rgba(255,196,110,.5)', C(x, y, 26), 1, { glow: true, anim: `flick:${4 + (i % 3)}` }), L('chandelier', C(x, y, 5), 1)]; }));
      // the terrace we sit in, coming to us: the same rings, cut into rows bent round the stage
      const b = [L('hallDark', R(-320, 250, 1052, 400), 2), L('terraceShade', R(-320, 256, 1052, 394), 2)];
      let y = 262, hw = 230, rise = 16;
      for (let i = 0; i < 9 && y < 660; i++) {
        const hh = 9 + i * 3.4;
        b.push(L(i % 2 ? 'terrace' : 'terraceShade', arcRow(y + hh, hw, hh, rise), 2),
          L('seat', arcRow(y + hh * .52, hw * .98, hh * .5, rise), 2),
          ...rep(Math.min(20, Math.round(hw / (13 + i * 2.4))), j => { const n = Math.min(20, Math.round(hw / (13 + i * 2.4))), x = 206 - hw + (j + .5) * (2 * hw / n), u = (x - 206) / hw; return L('terraceShade', R(+(x - 1).toFixed(1), +(y - rise * (1 - u * u)).toFixed(1), 2, +(hh * 1.1).toFixed(1)), 2); }));
        y += 14 + i * 7.5; hw += 26 + i * 9; rise += 3;
      }
      b.push(L('rgba(20,16,30,.22)', R(-320, 520, 1052, 130), 2));
      return { above: a, below: b };
    }
    if (t === 'nave') {
      const V = [206, 124], frame = s => { const hw = 214 * s, floorY = V[1] + 150 * s, spring = V[1] - 26 * s, apex = V[1] - 132 * s; return { hw, floorY, apex, spring, d: `M${206 - hw} ${floorY}V${spring}Q${206 - hw} ${apex + 40 * s} 206 ${apex}Q${206 + hw} ${apex + 40 * s} ${206 + hw} ${spring}V${floorY}Z` }; };
      const a = [], b = [];
      [1.64, 2.28, 3.17, 4.4].forEach((s, k) => { const f = frame(s); a.unshift(L(k % 2 ? 'vaultLit' : 'vault', f.d, 1), L('rib', f.d, 1, { stroke: 'rib', sw: 3.2 * s + .6, fillNone: true })); });
      a.unshift(L('vault', R(-320, -460, 1052, 1120), 1));
      a.push(...rep(2, k => { const y = -40 - k * 150, r = 12 + k * 7; return [L('pierShade', R(205.4, -460, 1.2, y + 460), 1, { op: .5 }), L('rgba(255,196,110,.5)', C(206, y, r * 3), 1, { glow: true, anim: `flick:${4 + k}` }), L('chandelier', E(206, y, r * 1.5, r * .5), 1), ...rep(7, i => L('chandelier', C(206 + (i - 3) * r * .6, y - r * .5, r * .2), 1, { anim: `flash:${(3 + i * .5).toFixed(1)}` }))]; }));
      a.push(...beam(96, -460, 168, 200, 46), ...motes(70, -360, 180, 460, 24, 11));
      // a bird at the high window, as the brief asks
      a.push(...[[330, -210, 1], [348, -196, .8]].map(([x, y, s]) => BIRD(x, y, s * 1.7, 250, 180, 1)));
      const end = frame(4.4);
      b.push(L('floor', R(-320, 260, 1052, 380), 2), L('aisle', PG([[170, 260], [242, 260], [300, 640], [112, 640]]), 2));
      b.push(...rep(4, k => { const s = [1.5, 2.1, 2.9, 4][k], y = 300 + k * 84 * 1.28, inner = 38 * s + 3, outer = 176 * s + 12;
        return [-1, 1].flatMap(sg => [L('woodShade', PG([[206 + sg * inner, y], [206 + sg * outer, y], [206 + sg * outer, y - 13 * s], [206 + sg * inner, y - 13 * s]]), 2), L('wood', PG([[206 + sg * inner, y - 13 * s], [206 + sg * outer, y - 13 * s], [206 + sg * outer, y - 17 * s], [206 + sg * inner, y - 17 * s]]), 2)]); }));
      return { above: a, below: b, replaceTop: end };
    }
    if (t === 'room') {   // the workshop of Cremona: a ceiling with beams, the floor to our feet
      const a = [L('plaster', R(-320, -460, 1052, 464), 1), ...rep(6, i => { const y = -14 - i * 30 * 1.42; return L('woodShade', R(-320, y, 1052, 10 + i * 4), 1, { op: .7 }); }),
        L('woodDark', R(-320, -460, 1052, 120), 1, { op: .3 }),
        ...chandNear(206, -110, 16, { body: 'varnishRaw', rod: 'metal', drops: 0 }),
        ...beam(150, -460, 190, 200, 44), ...motes(120, -380, 180, 480, 26, 3)];
      // the pendulum of the workshop clock, and the cat on the floor
      a.push(L('woodDark', RR(66, -200, 54, 150, 6), 1), L('plaster', C(93, -168, 20), 1), L('woodDark', C(93, -168, 17), 1, { fillNone: true, stroke: 'woodDark', sw: 2 }),
        L('woodDark', R(92.4, -180, 1.2, 13), 1), L('woodDark', R(92.4, -169, 9, 1.2), 1),
        ...A.moving([L('metal', R(92.2, -140, 1.6, 34), 1), L('varnishRaw', C(93, -102, 6.4), 1)], 'swing:9:2.4'));
      const b = [L('floor', R(-320, 260, 1052, 380), 2), ...rep(11, i => L('floorShade', PG([[+at(-300 + i * 100, 262, 200).toFixed(1), 262], [+at(-297 + i * 100, 262, 200).toFixed(1), 262], [+at(-280 + i * 100, 640, 200).toFixed(1), 640], [+at(-300 + i * 100, 640, 200).toFixed(1), 640]]), 2, { op: .5 })),
        L('rgba(20,16,30,.18)', R(-320, 470, 1052, 170), 2),
        L('wood', R(-60, 300, 320, 18), 2), L('woodShade', R(-60, 318, 320, 10), 2), L('woodDark', R(-40, 328, 14, 120), 2), L('woodDark', R(226, 328, 14, 120), 2),
        ...A.moving([L('rgba(20,16,30,.30)', E(330, 560, 54, 10), 2), L('woodDark', E(330, 540, 46, 20), 2), L('woodDark', C(372, 524, 15), 2), L('woodDark', PG([[362, 534], [360, 512], [372, 522]]), 2), L('woodDark', PG([[384, 534], [386, 512], [374, 522]]), 2), L('varnishRaw', E(366, 524, 3, 2), 2), L('varnishRaw', E(378, 524, 3, 2), 2), L('woodDark', RR(284, 528, 34, 7, 3.5), 2)], 'bob:3:6.5')];
      return { above: a, below: b };
    }
    return { above: [], below: [] };
  };

  // =============================================================================================
  // СБОРКА
  // =============================================================================================
  // A hall drawn after the handoff names its parts `ceil` / `rail` / `hallDark`; the handoff's own rooms
  // name them `cream` / `gold` / `velvetDark`. The continuation is one grammar, so it asks for the part
  // by its common name and this table finds what the location actually has — no magenta, nothing renamed.
  const FALLBACK = {
    ceil: ['cream', 'plaster', 'vault', 'vaultLit', 'stone', 'ceilLit'], ceilShade: ['creamShade', 'plasterShade', 'vaultShade', 'stoneShade'], ceilLit: ['cream', 'ceil', 'vaultLit', 'plaster'],
    hallDark: ['velvetDark', 'boxDark', 'dark', 'floor', 'seatDark'], rail: ['gold', 'goldLit', 'trim', 'railLit'], railShade: ['goldShade', 'goldDark', 'rail', 'gold'],
    seat: ['velvet', 'seat'], seatDark: ['velvetDark', 'boxDark', 'seatDark'], floor: ['floor', 'velvetDark', 'wood'],
    wood: ['wood', 'woodShade', 'floor'], woodShade: ['woodShade', 'woodDark', 'floorShade'],
    tier: ['cream', 'tier'], tierShade: ['creamShade', 'tierShade'], box: ['boxDark', 'box', 'dark'],
    terrace: ['terrace', 'wood', 'cream'], terraceShade: ['terraceShade', 'woodShade', 'creamShade'],
    chandelier: ['chandelier', 'goldLit', 'cream'], mushroom: ['mushroom', 'cream'], mushroomShade: ['mushroomShade', 'creamShade'],
    cloud: ['cloud', 'cream', 'ceilLit', 'ceil'], cloudShade: ['cloudShade', 'creamShade', 'ceilShade', 'ceil'], pier: ['pier', 'cream', 'ceil'], pierShade: ['pierShade', 'creamShade', 'ceilShade'],
    rib: ['rib', 'gold'], vault: ['vault', 'cream'], vaultLit: ['vaultLit', 'cream'], aisle: ['aisle', 'floor'],
    varnishRaw: ['varnishRaw', 'gold'], metal: ['metal', 'lamp'], plaster: ['plaster', 'cream'], woodDark: ['woodDark', 'floorShade'], floorShade: ['floorShade', 'woodShade'],
    gold: ['gold', 'goldLit', 'trim', 'bronze', 'copperLit', 'marble', 'farLit'], boat: ['boat', 'dark', 'lamp'],
    platform: ['platform', 'ground'], platformShade: ['platformShade', 'groundShade'], meadow: ['meadow', 'foliage'], meadowLit: ['meadowLit', 'foliageLit'],
    blossom: ['blossom', 'trim'], dome: ['dome', 'roof', 'far'], domeLit: ['domeLit', 'roofLit', 'farLit'], copper: ['copper', 'roof'], copperLit: ['copperLit', 'roofLit'],
    marble: ['marble', 'trim', 'stone'], stone: ['stone', 'plaster', 'far'], stoneBase: ['stoneBase', 'stone', 'far'], stoneLit: ['stoneLit', 'stone', 'farLit'],
    brick: ['brick', 'wall', 'far'], brickLit: ['brickLit', 'wallLit', 'farLit'], brickShade: ['brickShade', 'wallShade', 'far'],
    towerRed: ['towerRed', 'far'], towerWhite: ['towerWhite', 'farLit'], snow: ['snow', 'farLit'], mount: ['mount', 'far'], mountShade: ['mountShade', 'far'], roofGreen: ['roofGreen', 'roof', 'far'],
  };
  const resolver = p => n => (n.startsWith('#') || n.startsWith('rgba') || n === 'SKY' || n === 'SKYH' || n === 'GLOW' || p[n] !== undefined) ? n : ((FALLBACK[n] || []).find(x => p[x] !== undefined) || n);

  // ---- the halls from the stage keep their own frame (−240…460). Six of them have a flat ceiling where
  // the second Vienna has a box: the coffers shrink towards the back wall, the ribs run to the corners.
  const STAGE_CEIL = ['salzburgStage', 'pragueStage', 'amsterdamStage', 'spbStage', 'moscowStage', 'sydneyStage'];
  const STAGE_QUIET = ['berlinStage', 'leipzigStage', 'londonStage', 'newyorkStage', 'sydneyStage'];   // nothing moved in these
  const hwAt = y => 158 + (64 - y) * (78 / 304);
  const stageCeil = (o = {}) => {
    const l = [L(o.fill || 'hcream', PG([[-30, -240], [442, -240], [364, 64], [48, 64]]), 1)];
    let y = 58, g = 24; const ys = [];
    while (y > -246) { ys.push(y); y -= g; g *= 1.36; }
    ys.forEach((yy, i) => { const w = hwAt(yy), t = 2 + i * 1.4; l.push(L(o.shade || 'hcreamSh', R(+(206 - w).toFixed(1), +(yy - t).toFixed(1), +(2 * w).toFixed(1), +t.toFixed(1)), 1, { op: .5 })); });
    l.push(...rep(5, i => { const X = -30 + i * 118, x1 = 48 + i * 79; return L(o.shade || 'hcreamSh', PG([[X, -240], [X + 16, -240], [x1 + 2.2, 64], [x1 - 2.2, 64]]), 1, { op: .34 }); }));
    ys.slice(0, 4).forEach((yy, i) => { const w = hwAt(yy) * .9, n = 4 + i, y2 = ys[i + 1] ?? -246; rep(n, c => l.push(L(o.shade || 'hcreamSh', R(+(206 - w + c * (2 * w / n) + 5).toFixed(1), +(yy - (yy - y2) * .8).toFixed(1), +(2 * w / n - 10).toFixed(1), +((yy - y2) * .58).toFixed(1)), 1, { op: .2 }))); });
    l.push(L('rgba(20,16,30,.14)', PG([[-30, -240], [442, -240], [400, -60], [12, -60]]), 1));
    return l;
  };
  // the one thing a rehearsal hall always has: dust in the working light
  const stageQuiet = seed => [...beam(128, -240, 178, 200, 44), ...motes(96, -200, 190, 380, 18, seed)];

  const isOut = k => !!OUT[k];
  const after = (layers, pred, add) => { const i = layers.findIndex(pred); return i < 0 ? [...add, ...layers] : [...layers.slice(0, i + 1), ...add, ...layers.slice(i + 1)]; };

  function build(key, mode = 'eve') {
    const s = SCENES[key]; if (!s) throw new Error('нет сцены ' + key);
    const eve = mode === 'eve';
    let l = (EXTRA_INSERTS[key] || (x => x))(s.fn(eve), eve);
    if (isOut(key)) {
      const o = OUT[key], T = resolver(pal(s.loc, mode));
      const fix = a => a.map(x => ({ ...x, fill: T(x.fill), ...(x.stroke ? { stroke: T(x.stroke) } : {}) }));
      const sky = fix([...SKYH(), ...(eve ? stars() : clouds(eve)), ...(eve ? clouds(eve, [3, 4]) : []), ...o.high(eve)]);
      l = after(l, x => x.fill === 'SKY', sky);
      l = [...l, ...fix(foreOf(key, eve))];
    } else if (INSIDE[key] && INSIDE[key].type !== 'skip') {
      const { above, below } = insideLayers(key, INSIDE[key]);
      const T = resolver(pal(s.loc, mode));
      const fix = a => a.map(x => ({ ...x, fill: T(x.fill), ...(x.stroke ? { stroke: T(x.stroke) } : {}) }));
      l = [...fix(above), ...l, ...fix(below)];
    } else if (key.endsWith('Stage')) {
      const T = resolver(pal(s.loc, mode));
      const add = [...(STAGE_CEIL.includes(key) ? stageCeil() : []), ...(STAGE_QUIET.includes(key) ? stageQuiet(key.length + 3) : [])];
      if (add.length) l = [l[0], ...add.map(x => ({ ...x, fill: T(x.fill) })), ...l.slice(1)];
    }
    return l;
  }

  // ---- the halls from the stage keep their own frame (−240…460): only their ceilings are mended
  const stageKeys = Object.keys(SCENES).filter(k => k.endsWith('Stage'));

  // =============================================================================================
  // РИСОВАЛКА (предпросмотр)
  // =============================================================================================
  let ANIM = {};
  const css = (name, body) => { if (!ANIM[name]) ANIM[name] = body; return name; };
  const nm = s => 'a' + s.replace(/[^a-z0-9]/gi, '_');
  function animClass(spec) {
    const out = [];
    spec.split('+').forEach(one => {
      const [k, ...p] = one.split(':'), n = p.map(Number), id = nm(one);
      if (k === 'ride') { const [sp, x0, x1] = n, d = Math.abs((x1 - x0) / (sp || 1)), f = (0 - x0) / (x1 - x0); css(id, `@keyframes ${id}{from{transform:translateX(${x0}px)}to{transform:translateX(${x1}px)}}.${id}{animation:${id} ${d.toFixed(1)}s linear infinite;animation-delay:${(-f * d).toFixed(1)}s}`); }
      else if (k === 'bird') { const [left, span] = n, d = span / 16; css(id, `@keyframes ${id}{0%{transform:translateX(${left}px) translateY(0);opacity:0}12%{opacity:1}88%{opacity:1}100%{transform:translateX(${left + span}px) translateY(-14px);opacity:0}}@keyframes ${id}w{0%,100%{transform:scaleY(1)}50%{transform:scaleY(.35)}}.${id}{animation:${id} ${d.toFixed(1)}s linear infinite,${id}w .42s ease-in-out infinite;transform-box:fill-box;transform-origin:center}`); }
      else if (k === 'flick') css(id, `@keyframes ${id}{0%,100%{opacity:1}42%{opacity:.68}68%{opacity:.92}}.${id}{animation:${id} ${(n[0] || 4)}s ease-in-out infinite}`);
      else if (k === 'blink') css(id, `@keyframes ${id}{0%,100%{opacity:1}50%{opacity:.18}}.${id}{animation:${id} ${(n[0] || 3)}s ease-in-out infinite}`);
      else if (k === 'flash') css(id, `@keyframes ${id}{0%,92%,100%{opacity:.55}96%{opacity:1}}.${id}{animation:${id} ${(n[0] || 3)}s linear infinite}`);
      else if (k === 'sway') { const [amp, per] = n; css(id, `@keyframes ${id}{0%,100%{transform:translateX(${-amp}px)}50%{transform:translateX(${amp}px)}}.${id}{animation:${id} ${(per || 8)}s ease-in-out infinite}`); }
      else if (k === 'bob') { const [amp, per] = n; css(id, `@keyframes ${id}{0%,100%{transform:translateY(0)}50%{transform:translateY(${-amp}px)}}.${id}{animation:${id} ${(per || 6)}s ease-in-out infinite}`); }
      else if (k === 'swing') { const [ang, per] = n; css(id, `@keyframes ${id}{0%,100%{transform:rotate(${-ang}deg)}50%{transform:rotate(${ang}deg)}}.${id}{animation:${id} ${(per || 2.4)}s ease-in-out infinite;transform-box:view-box;transform-origin:93px -140px}`); }
      else if (k === 'rise') { const [per, dist] = n; css(id, `@keyframes ${id}{0%{transform:translateY(0);opacity:0}20%{opacity:.8}100%{transform:translateY(${-dist}px);opacity:0}}.${id}{animation:${id} ${(per || 5)}s linear infinite}`); }
      else if (k === 'fall') { const [sp, y0, y1] = n, d = (y1 - y0) / (sp || 10); css(id, `@keyframes ${id}{0%{transform:translateY(0) translateX(0);opacity:0}10%{opacity:.9}100%{transform:translateY(${(y1 - y0).toFixed(0)}px) translateX(34px);opacity:0}}.${id}{animation:${id} ${d.toFixed(1)}s linear infinite}`); }
      else if (k === 'peck') { const [per, ang] = n; css(id, `@keyframes ${id}{0%,58%,100%{transform:rotate(0) translateX(0)}66%{transform:rotate(${ang || 14}deg)}74%{transform:rotate(0)}82%{transform:translateX(4px)}}.${id}{animation:${id} ${(per || 3)}s ease-in-out infinite;transform-box:fill-box;transform-origin:70% 100%}`); }
      else return;
      out.push(id);
    });
    return out.join(' ');
  }

  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;');
  function svgOf(layers, p, o = {}) {
    ANIM = {};                                       // each picture carries only its own keyframes
    const aerial = o.aerial !== false, id = o.id || 'g' + Math.random().toString(36).slice(2, 7);
    const col = (t, depth) => { const c = t.startsWith('#') || t.startsWith('rgba') ? t : (p[t] ?? '#FF00AA'); return aerial && depth < 2 && c.startsWith('#') ? mix(c, p.skyLow, AER[depth]) : c; };
    const body = layers.map(l => {
      if (o.still === false && false) return '';
      const fill = l.fill === 'SKY' ? `url(#${id}s)` : l.fill === 'SKYH' ? `url(#${id}h)` : l.fill === 'GLOW' ? `url(#${id}g)` : l.glow ? `url(#${id}w)` : col(l.fill, l.depth);
      const a = [`d="${l.d}"`, `fill="${l.fillNone ? 'none' : fill}"`];
      if (l.op !== undefined) a.push(`opacity="${l.op}"`);
      if (l.stroke) a.push(`stroke="${col(l.stroke, l.depth)}"`, `stroke-width="${l.sw}"`, 'stroke-linecap="round"');
      if (l.tx !== undefined) a.push(`transform="translate(${l.tx} ${l.ty}) scale(${l.sc || 1})"`);
      if (l.anim && o.anim !== false) { const c = animClass(l.anim); if (c) a.push(`class="${c}"`); }
      return `<path ${a.join(' ')}/>`;
    }).join('');
    const stop = (off, c) => { const m = String(c).match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/); return m ? `<stop offset="${off}" stop-color="rgb(${m[1]},${m[2]},${m[3]})" stop-opacity="${m[4]}"/>` : `<stop offset="${off}" stop-color="${c}"/>`; };
    const defs = `<defs><linearGradient id="${id}s" x1="0" y1="0" x2="0" y2="1">${stop(0, p.sky)}${stop(1, p.skyLow)}</linearGradient>`
      + `<linearGradient id="${id}h" x1="0" y1="0" x2="0" y2="1">${stop(0, p.skyHigh)}${stop(1, p.sky)}</linearGradient>`
      + `<radialGradient id="${id}g">${stop(0, p.glow)}${stop(1, 'rgba(255,196,110,0)')}</radialGradient>`
      + `<radialGradient id="${id}w">${stop(0, 'rgba(255,196,110,.55)')}${stop(1, 'rgba(255,196,110,0)')}</radialGradient></defs>`;
    const style = Object.values(ANIM).join('');
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${o.viewBox}" width="${o.w}" height="${o.h}" preserveAspectRatio="${o.par || 'xMidYMid slice'}" style="display:block${o.style ? ';' + esc(o.style) : ''}">`
      + `<style>${style}</style>${defs}${body}</svg>`;
  }

  // the view a phone shows: the whole width, centred on the postcard, clamped to the drawn frame
  function viewBox(w, h) {
    const unitH = h * (412 / w);                      // how many units of the grid the screen is tall
    let top = FRAME.centre - unitH / 2;
    if (unitH >= FRAME.bottom - FRAME.top) top = FRAME.top - (unitH - (FRAME.bottom - FRAME.top)) / 2;
    else top = Math.max(FRAME.top, Math.min(FRAME.bottom - unitH, top));
    return `0 ${top.toFixed(1)} 412 ${unitH.toFixed(1)}`;
  }

  function render(key, mode, o = {}) {
    const s = SCENES[key], layers = o.raw ? (EXTRA_INSERTS[key] || (x => x))(s.fn(mode === 'eve'), mode === 'eve') : build(key, mode);
    const whole = key.endsWith('Stage');
    return svgOf(layers, pal(s.loc, mode), { aerial: s.aerial !== false, viewBox: o.viewBox || (whole ? '0 -240 412 700' : viewBox(o.w || 412, o.h || 915)), w: o.w || 412, h: o.h || 915, par: o.par, anim: o.anim, style: o.style });
  }

  const count = (key, mode = 'eve') => build(key, mode).length;
  return { build, render, svgOf, viewBox, pal, OUT, INSIDE, SCENES, stageKeys, STAGE_CEIL, STAGE_QUIET, count, FRAME, PAL_EXT, helpers: A };
}
