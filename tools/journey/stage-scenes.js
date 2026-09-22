// ---------------------------------------------------------------------------------------------
// The halls from the stage (spec 3.27; handoff `venue/…/Комната и залы.dc.html`, frames 29d and 29l,
// dev «Режимы «со сцены»»). Live takes place on the stage of the city's hall: we stand on the boards and
// look into the empty hall — a rehearsal, no audience. One grammar with one vanishing point (206, 150):
// the void, the ceiling, the back wall opposite with the side walls going to it, the tiers, the rows of
// seats rising away from us, the boards under our feet. The grid is the postcard's 412 × 260 continued
// up to −240 and down to 460. Vienna, Paris, Berlin, Leipzig and Cremona are the handoff's; the other
// eleven are drawn with the same helpers, each with its own sign that can be seen from the stage.
// A hall has no time of day: `eve` is not used. Three slips of the handoff's code are mended here:
// the upper edge of a row arched the wrong way (a row thinner than nothing at Paris), the rod of a
// chandelier stopped short of it, and the rows were laid near first, so a far row covered a near one.

const HT = {
  hvoid: '#140F1C', hwall: '#3A3048', hwallLit: '#4A3E5C', hwallSh: '#241D30', hgold: '#E2B74E', hgoldLit: '#F2D488', hgoldSh: '#A8863A',
  hseat: '#8E2F3F', hseatLit: '#A8323F', hseatSh: '#5A1E2A', hcream: '#F3EEE2', hcreamSh: '#D8D0BE', hboard: '#8B5E3C', hboardSh: '#6E4A34',
  hchand: '#FFE9B0', hdark: '#1C1530', hwood: '#A9713F', hwoodSh: '#7E5230', hstone: '#CFC6B4', hstoneSh: '#A89E8C',
};
const stageOf = (key, pal, fn) => {
  EXTRA_LOCPAL[`${key}Stage`] = { ...HT, ...pal };
  EXTRA_SCENES[`${key}Stage`] = { fn, loc: `${key}Stage`, aerial: false, stage: true };
};

// how high a row stands over its ends at x: the rows are arcs round the stage, their ends come towards us
const arcY = (hw, rise, x) => { const u = (x - 206) / hw; return rise * (1 - u * u); };
const row = (y, hw, hh, rise) => `M${(206 - hw).toFixed(1)} ${y.toFixed(1)}q${hw.toFixed(1)} ${(-2 * rise).toFixed(1)} ${(2 * hw).toFixed(1)} 0v${(-hh).toFixed(1)}q${(-hw).toFixed(1)} ${(-2 * rise).toFixed(1)} ${(-2 * hw).toFixed(1)} 0Z`;
// the seats: far rows first, the near one — low and large — over them
const seats = (rise, n0 = 9, seat = 'hseat', shade = 'hseatSh') => rep(n0, j => {
  const k = n0 - 1 - j, t = k / (n0 - 1), y = 300 - 96 * t, hw = 214 - 74 * t, hh = 12 - 5.5 * t, rs = rise * (1 - .45 * t), n = Math.round(2 * hw / (18 - 7 * t));
  return [L(shade, row(y, hw, hh, rs)), L(seat, row(y - hh * .55, hw, hh * .45, rs)),
    ...rep(n - 1, i => { const x = 206 - hw + (i + 1) * (2 * hw / n); return L(shade, R(+(x - .55).toFixed(1), +(y - hh - arcY(hw, rs, x)).toFixed(1), 1.1, +hh.toFixed(1))); })];
});
const boards = (board = 'hboard', shade = 'hboardSh') => [L('hdark', R(-30, 304, 472, 6), 2), L(board, R(-30, 310, 472, 150), 2),
  ...rep(11, i => { const x0 = i * 41 - 16; return L(shade, PG([[x0, 310], [x0 + 2.4, 310], [206 + (x0 + 2.4 - 206) * 2.5, 460], [206 + (x0 - 206) * 2.5, 460]]), 2); }),
  L('rgba(255,255,255,.07)', R(-30, 310, 472, 3), 2)];
const chand = (x, y, r, body = 'hchand') => [L('hgoldSh', R(x - .6, -240, 1.2, y - r + 240)), L('rgba(255,196,110,.5)', C(x, y, r * 3.1), 1, { glow: true, anim: 'flick:4' }), L(body, C(x, y, r)), L('rgba(255,255,255,.8)', C(x - r * .3, y - r * .3, r * .34))];
// a crystal chandelier: a drop of glass instead of a ball
const crystal = (x, y, r) => [L('hgoldSh', R(x - .6, -240, 1.2, y - r + 240)), L('rgba(255,196,110,.5)', C(x, y, r * 3), 1, { glow: true, anim: 'flick:4' }), L('hchand', PG([[x - r, y - r * .4], [x + r, y - r * .4], [x, y + r * 1.4]])), L('hchand', E(x, y - r * .4, r, r * .45)), L('rgba(255,255,255,.8)', C(x - r * .3, y - r * .5, r * .3))];
const backWall = () => [L('hwall', R(48, 64, 316, 140)), L('hwallLit', R(48, 64, 106, 140), 1, { op: .28 }),
  L('hwallSh', PG([[0, -30], [48, 64], [48, 204], [0, 300]])), L('hwallSh', PG([[412, -30], [364, 64], [364, 204], [412, 300]]))];
const STAGE = o => {
  const l = [L('hvoid', R(-30, -240, 472, 720))];
  l.push(...(o.ceil ? o.ceil() : []), ...backWall(), ...(o.tiers ? o.tiers() : []), ...seats(o.rise ?? 0, o.rows, o.seat, o.seatShade), ...(o.mark ? o.mark() : []), ...boards(o.board, o.boardShade));
  return l;
};
// a tier with its boxes along the arc; [fill] and [rail] — gold by default, white in the concert halls
const tierArc = (y, hw, hh, rise, boxes, fill = 'hgoldSh', rail = 'hgold') => {
  const l = [L(fill, row(y, hw, hh, rise)), L(rail, row(y - hh * .62, hw, hh * .4, rise), 1, { op: .85 })];
  rep(boxes, j => { const t = boxes > 1 ? j / (boxes - 1) : .5, x = 206 - hw + 2 * hw * t, dy = -4 * rise * t * (1 - t); l.push(L('hdark', RR(+(x - 8).toFixed(1), +(y + dy - hh - 16).toFixed(1), 16, 16, 2)), L('hseatSh', R(+(x - 8).toFixed(1), +(y + dy - hh - 6).toFixed(1), 16, 6))); });
  return l;
};
// a ceiling of coffers: a grid of sunk squares
const coffers = (y0, y1, fill = 'hcreamSh') => rep(4, r => rep(8, c => L(fill, R(-10 + c * 54, y0 + r * (y1 - y0) / 4 + 6, 42, (y1 - y0) / 4 - 12), 1, { op: .55 })));
// columns standing on the back wall
const columns = (n, x0, x1, top, bottom, fill = 'hcream', shade = 'hcreamSh') => rep(n, i => { const x = x0 + i * (x1 - x0) / (n - 1); return [L(shade, R(x - 5, top, 10, bottom - top)), L(fill, R(x - 5, top, 6.5, bottom - top)), L(fill, R(x - 7, top - 4, 14, 4)), L(fill, R(x - 7, bottom, 14, 4))]; });
// the painted dome over the horseshoe: segments of colour in a gold ring
const dome = (r, colours, op = .55) => [L('hcreamSh', C(206, -6, r + 16)), L('hcream', C(206, -6, r)),
  ...rep(colours.length, i => { const a = Math.PI * (1.06 + .88 * i / (colours.length - 1)); return L(colours[i], E(206 + Math.cos(a) * r * .64, -6 + Math.sin(a) * r * .47, r * .22, r * .16), 1, { op }); }),
  L('hgold', C(206, -6, r + 4), 1, { fillNone: true, stroke: 'hgold', sw: 3 })];

// ── коробка — the shoebox: one straight tier along the back wall, pilasters, the ceiling ───────
stageOf('vienna', {}, () => STAGE({ rise: 6,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), ...rep(5, i => L('hgoldSh', R(-30, -230 + i * 54, 472, 5), 1, { op: .5 })), ...rep(4, i => L('hgold', R(20 + i * 98, -224, 72, 4))), ...chand(112, 34, 9), ...chand(300, 34, 9), ...chand(206, 14, 7)],
  // the caryatids of the Golden Hall carry the gallery
  tiers: () => [...tierArc(150, 176, 11, 6, 0), ...rep(8, i => L('hgold', R(56 + i * 38, 66, 9, 84))), ...rep(8, i => [L('hgoldLit', C(60.5 + i * 38, 60, 5)), L('hgoldLit', R(57.5 + i * 38, 62, 7, 14))])],
  mark: () => [L('hgoldSh', R(48, 60, 316, 5))] }));

// Salzburg — the Great Hall of the Mozarteum, white and gold: the organ is behind us now, opposite is the arcade of the gallery
stageOf('salzburg', { hwall: '#E8E0CE', hwallLit: '#F3EEE2', hwallSh: '#CFC6B1', hdark: '#5A4E44' }, () => STAGE({ rise: 6,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), ...coffers(-230, 50), ...chand(112, 30, 8), ...chand(300, 30, 8), ...chand(206, 8, 8)],
  tiers: () => [...rep(7, i => [L('hwallSh', ARCH(62 + i * 42, 74, 26, 62)), L('hdark', ARCH(65 + i * 42, 80, 20, 56), 1, { op: .75 })]), ...tierArc(152, 176, 10, 5, 0)],
  mark: () => [L('hgold', R(48, 64, 316, 4))] }));

// Prague — the Dvořák Hall of the Rudolfinum: a colonnade round the hall, blue seats
stageOf('prague', { hwall: '#C9B78F', hwallLit: '#E3D6B6', hwallSh: '#A89468', hseat: '#3E5A8E', hseatLit: '#4E6EA8', hseatSh: '#27395C' }, () => STAGE({ rise: 5,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), L('hgoldSh', R(-30, 52, 472, 8)), ...rep(3, i => L('hcreamSh', R(-30, -200 + i * 80, 472, 3))), ...chand(140, 26, 9), ...chand(272, 26, 9)],
  tiers: () => [...columns(9, 60, 352, 70, 192), ...tierArc(152, 176, 8, 4, 0)],
  mark: () => [L('hgold', R(48, 64, 316, 5))] }));

// Amsterdam — the Grote Zaal of the Concertgebouw: the great organ is behind us, opposite is the balcony with its gold rail
stageOf('amsterdam', { hwall: '#E6DDC9', hwallLit: '#EFE8D8', hwallSh: '#CCC3AE' }, () => STAGE({ rise: 5,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), ...rep(6, i => L('hcreamSh', R(-30 + i * 80, -240, 3, 300), 1, { op: .6 })), ...crystal(96, 36, 10), ...crystal(316, 36, 10), ...crystal(160, 10, 9), ...crystal(252, 10, 9)],
  tiers: () => [...tierArc(148, 178, 16, 5, 0, 'hwallSh', 'hgold'), ...rep(7, i => L('hseatSh', R(64 + i * 42, 118, 30, 12))), ...rep(8, i => L('hgoldLit', C(64 + i * 40, 84, 3)))],
  mark: () => [L('hgold', R(48, 132, 316, 3))] }));

// St Petersburg — the Grand Hall of the Philharmonia: white columns and crystal chandeliers
stageOf('spb', { hwall: '#EDE8DC', hwallLit: '#F7F4EC', hwallSh: '#D2CCBE', hcream: '#FFFFFF', hcreamSh: '#D2CCBE' }, () => STAGE({ rise: 5,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), ...coffers(-230, 40), ...[92, 164, 248, 320].flatMap(x => crystal(x, 40, 10)), ...[120, 292].flatMap(x => crystal(x, 4, 9))],
  tiers: () => [...columns(8, 62, 350, 68, 190), ...tierArc(154, 176, 8, 4, 0, 'hcreamSh', 'hgold')],
  mark: () => [L('hcreamSh', R(48, 62, 316, 6))] }));

// Moscow — the Great Hall of the Conservatory: the oval portraits of composers along the wall
stageOf('moscow', { hwall: '#E4DAC2', hwallLit: '#EFE7D2', hwallSh: '#CBC1A8', hseat: '#A98B5A', hseatLit: '#BFA06E', hseatSh: '#7A6340', hportrait: '#4E4438' }, () => STAGE({ rise: 5,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), L('hcreamSh', R(-30, 48, 472, 8)), ...chand(146, 24, 10), ...chand(266, 24, 10)],
  tiers: () => [...rep(7, i => { const x = 76 + i * 43.3; return [L('hcream', E(x, 100, 12, 15)), L('hportrait', E(x, 100, 9, 12)), L('hgold', E(x, 100, 12, 15), 1, { fillNone: true, stroke: 'hgold', sw: 1.4 })]; }), ...tierArc(160, 176, 10, 5, 0, 'hwallSh', 'hcream')],
  mark: () => [L('hcream', R(48, 64, 316, 5))] }));

// Sydney — the Concert Hall of the Opera House: ribs of white birch overhead, the magenta «petals» over the hall
stageOf('sydney', { hwall: '#D9C49A', hwallLit: '#E2CFA6', hwallSh: '#BFA97C', hseat: '#7A3E8E', hseatLit: '#9A5AAE', hseatSh: '#4E2458', hpetal: '#C2307E', hpetalLit: '#E66AAE', hrib: '#C9B48A' }, () => STAGE({ rise: 6,
  ceil: () => [L('hwall', R(-30, -240, 472, 300)), ...rep(11, i => { const x = -20 + i * 45; return L('hrib', PG([[x, -240], [x + 10, -240], [206 + (x + 5 - 206) * .3 + 1.5, 60], [206 + (x + 5 - 206) * .3 - 1.5, 60]])); }),
    ...rep(7, i => { const x = 104 + i * 34, y = 22 - Math.abs(i - 3) * 6; return [L('hgoldSh', R(x - .4, -240, .8, y + 240), 1, { op: .5 }), L('hpetal', E(x, y, 15, 4.4)), L('hpetalLit', E(x - 3, y - 1.2, 8, 2))]; })],
  tiers: () => [...tierArc(150, 178, 12, 6, 0, 'hwallSh', 'hrib')],
  mark: () => [L('hrib', R(48, 64, 316, 4))] }));

// ── подкова — the horseshoe: tiers of boxes round us, a painted dome, the great chandelier ────
stageOf('paris', {}, () => STAGE({ rise: 16, rows: 8,
  ceil: () => [L('hdark', R(-30, -240, 472, 300)), L('hcreamSh', C(206, -6, 132)), L('hcream', C(206, -6, 116)),
    ...rep(6, i => { const a = Math.PI * (1.06 + .88 * i / 5); return L(['#7A3E8E', '#3E5A8E', '#B94A4A', '#D9B26B', '#4E8E57', '#C9814A'][i], E(206 + Math.cos(a) * 74, -6 + Math.sin(a) * 54, 26, 18), 1, { op: .55 }); }),
    L('hgold', C(206, -6, 120), 1, { fillNone: true, stroke: 'hgold', sw: 3 }), ...chand(206, 46, 15)],
  tiers: () => [...tierArc(168, 196, 10, 16, 13), ...tierArc(138, 182, 9, 14, 12), ...tierArc(110, 168, 8, 12, 11), ...tierArc(84, 154, 7, 10, 10)] }));

// Milan — La Scala: red and gold, five tiers of boxes to the dome
stageOf('milan', { hwall: '#4A1622', hwallLit: '#6E2333', hwallSh: '#3A1220', hseat: '#9B2C3E', hseatSh: '#5A1E2A', hdark: '#2A0E16', hcream: '#E9C98D', hcreamSh: '#C9A15C' }, () => STAGE({ rise: 16, rows: 8,
  ceil: () => [L('hdark', R(-30, -240, 472, 300)), ...dome(112, ['#D9B26B', '#C9814A', '#E9C98D', '#B08A45', '#D9B26B', '#C9814A'], .45), ...chand(206, 52, 16)],
  tiers: () => [...tierArc(176, 198, 10, 16, 13), ...tierArc(150, 186, 9, 14, 12), ...tierArc(124, 172, 8, 12, 11), ...tierArc(100, 158, 7, 10, 10), ...tierArc(78, 144, 6, 8, 9)] }));

// London — the Royal Albert Hall: the round hall under its dome, the acoustic «mushrooms» hanging over the arena
stageOf('london', { hwall: '#3A1820', hwallLit: '#5A2A30', hwallSh: '#2A1016', hseat: '#A8323F', hcream: '#C9CFD6', hcreamSh: '#9FA8B2', hmush: '#F2EFE6', hmushSh: '#9A9488' }, () => STAGE({ rise: 14, rows: 8,
  ceil: () => [L('hdark', R(-30, -240, 472, 300)), L('hcreamSh', E(206, -60, 220, 150)), L('hcream', E(206, -64, 200, 132)),
    ...rep(12, i => { const a = Math.PI * i / 12; return L('hcreamSh', PG([[206, -64], [206 + Math.cos(a) * 200 - 1, -64 + Math.sin(a) * 132], [206 + Math.cos(a) * 200 + 1, -64 + Math.sin(a) * 132]]), 1, { op: .6 }); }),
    ...rep(9, i => { const x = 96 + i * 27.5, y = 30 + ((i * 7) % 3) * 8 - Math.abs(i - 4) * 3; return [L('hcreamSh', R(x - .4, -200, .8, y + 200), 1, { op: .6 }), L('hmushSh', E(x, y + 1.6, 13, 4)), L('hmush', E(x, y, 13, 3.6))]; })],
  tiers: () => [...tierArc(170, 198, 10, 14, 14), ...tierArc(140, 184, 9, 12, 13), ...tierArc(112, 170, 8, 10, 12), ...tierArc(86, 156, 7, 8, 11)] }));

// New York — Carnegie Hall: white and gold balconies, no boxes, a ring of lights in the oval of the ceiling
stageOf('newyork', { hwall: '#3A2A24', hwallLit: '#5A443A', hwallSh: '#2A1E1A', hseat: '#A8323F', hcream: '#F5F0E4', hcreamSh: '#DDD5C2', htier: '#F3EEE2', htierSh: '#DAD2BF' }, () => STAGE({ rise: 12, rows: 8,
  ceil: () => [L('hcream', R(-30, -240, 472, 300)), L('hcreamSh', E(206, -30, 190, 84)), L('hcream', E(206, -34, 172, 72)), L('hgold', E(206, -34, 176, 76), 1, { fillNone: true, stroke: 'hgold', sw: 2.4 }),
    ...rep(16, i => { const a = Math.PI * 2 * i / 16; return L('hchand', C(206 + Math.cos(a) * 176, -34 + Math.sin(a) * 76, 2.4)); })],
  tiers: () => [...tierArc(166, 196, 11, 12, 0, 'htierSh', 'htier'), ...tierArc(134, 180, 10, 10, 0, 'htierSh', 'htier'), ...tierArc(104, 166, 9, 8, 0, 'htierSh', 'htier'), ...tierArc(78, 152, 8, 7, 0, 'htierSh', 'htier'),
    ...[166, 134, 104, 78].map((y, k) => L('hgold', row(y - 1, 196 - k * 14.5, 1.2, 12 - k * 1.7)))] }));

// Buenos Aires — the Teatro Colón: six tiers of boxes and Soldi's painted dome
stageOf('buenosaires', { hwall: '#3F1A22', hwallLit: '#5A2A34', hwallSh: '#2A0E16', hseat: '#B0485A', hseatSh: '#6A2636', hcream: '#E6D6B8', hcreamSh: '#C6B48F' }, () => STAGE({ rise: 16, rows: 8,
  ceil: () => [L('hdark', R(-30, -240, 472, 300)), ...dome(104, ['#D98A6B', '#6B8FB5', '#C9A15C', '#8E5E8E', '#6FA88E', '#D9B27A']), ...chand(206, 54, 16)],
  tiers: () => rep(6, k => tierArc(182 - k * 21, 200 - k * 11, 9 - k * .6, 16 - k * 1.6, 14 - k)) }));

// ── виноградник — the vineyard: terraces of seats on every side, the stage in the middle ───────
stageOf('berlin', { hwall: '#4A3E38', hwallLit: '#5C4E44', hseat: '#3E6E7A', hseatLit: '#4E8899', hseatSh: '#284A54' }, () => STAGE({ rise: 4, rows: 6,
  ceil: () => [L('hdark', R(-30, -240, 472, 300)), ...rep(7, i => { const x = 32 + i * 58, y = -30 - ((i % 3) * 26); return [L('hcreamSh', E(x, y, 34, 11)), L('hcream', E(x, y - 3, 30, 8)), L('hgoldSh', R(x - .5, -240, 1, y - 8 + 240))]; })],
  tiers: () => rep(6, i => { const sg = i < 3 ? -1 : 1, k = i % 3, x = 206 + sg * (96 + k * 62), y = 176 - k * 30, w = 78 - k * 8;
    return [L('hwoodSh', PG([[x - w / 2, y], [x + w / 2, y], [x + w / 2 - 5, y - 16], [x - w / 2 + 5, y - 16]])), L('hwood', R(x - w / 2 + 5, y - 19, w - 10, 4)), ...rep(5, j => L('hseatSh', R(x - w / 2 + 9 + j * ((w - 20) / 5), y - 14, 7, 8)))]; }) }));

// Tokyo — Suntory Hall: a vineyard of warm wood under its chandeliers
stageOf('tokyo', { hwall: '#6E4A34', hwallLit: '#8A5E42', hwallSh: '#4E3424', hseat: '#7A2E36', hseatSh: '#4A1A22', hwood: '#B98A54', hwoodSh: '#976C3C', hcream: '#E8D3AE', hcreamSh: '#C5AC80' }, () => STAGE({ rise: 4, rows: 6,
  ceil: () => [L('hcreamSh', R(-30, -240, 472, 300)), ...rep(6, i => L('hcream', PG([[-30 + i * 80, -240], [30 + i * 80, -240], [206 + (30 + i * 80 - 206) * .5, 58], [206 + (-30 + i * 80 - 206) * .5, 58]]), 1, { op: .7 })), ...chand(126, 14, 11), ...chand(206, -6, 12), ...chand(286, 14, 11)],
  tiers: () => rep(8, i => { const sg = i < 4 ? -1 : 1, k = i % 4, x = 206 + sg * (74 + k * 46), y = 184 - k * 26, w = 66 - k * 6;
    return [L('hwoodSh', PG([[x - w / 2, y], [x + w / 2, y], [x + w / 2 - 5, y - 16], [x - w / 2 + 5, y - 16]])), L('hwood', R(x - w / 2 + 5, y - 19, w - 10, 4)), ...rep(4, j => L('hseatSh', R(x - w / 2 + 9 + j * ((w - 18) / 4), y - 14, 7, 8)))]; }) }));

// ── неф — the Gothic nave: pointed arcades instead of tiers, pews instead of seats, the rose window ─
stageOf('leipzig', { hwall: '#CFC6B4', hwallLit: '#E2DACA', hwallSh: '#A89E8C', hseat: '#6E5238', hseatLit: '#8B6A48', hseatSh: '#4E3A28' }, () => STAGE({ rise: 0, rows: 7,
  ceil: () => [L('hdark', R(-30, -240, 472, 220)), ...rep(4, i => { const t = i / 3, hw = 200 - 56 * t, y = -10 - t * 52; return L('hstoneSh', `M${206 - hw} ${y + 70}L${206 - hw} ${y}Q206 ${y - 78} ${206 + hw} ${y}L${206 + hw} ${y + 70}Z`, 1, { op: .5 + .12 * i }); })],
  tiers: () => [...rep(2, s => { const sg = s ? 1 : -1; return rep(3, i => { const t = i / 2, x = 206 + sg * (150 - 36 * t), y = 190 - t * 30, hh = 126 - 40 * t, w = 30 - 8 * t;
    return [L('hstoneSh', ARCH(x - w / 2, y - hh, w, hh)), L('hstone', ARCH(x - w / 2 + 3, y - hh + 4, w - 6, hh - 6), 1, { op: .35 })]; }); }),
    L('hstoneSh', C(206, 86, 44)), L('hcream', C(206, 86, 38), 1, { op: .5 }), ...rep(8, i => { const a = Math.PI * 2 * i / 8; return L('hgoldSh', R(206 + Math.cos(a) * 19 - 1.2, 86 + Math.sin(a) * 19 - 1.2, 2.4, 2.4)); }), L('hgold', C(206, 86, 40), 1, { fillNone: true, stroke: 'hgold', sw: 2.4 })] }));

// ── мастерская — Cremona is no hall: the view from the maker's bench, the rack of violins, the window ─
const violinAt = (x, y, s, body) => { const cy = y - 14 * s; return [L(body, E(x, cy + 6 * s, 8 * s, 7 * s)), L(body, E(x, cy - 7 * s, 6.4 * s, 5.6 * s)), L(body, R(x - 5 * s, cy - 6 * s, 10 * s, 12 * s)),
  L('ebony', R(x - 1.3 * s, cy - 32 * s, 2.6 * s, 30 * s)), L('varnishDark', C(x, cy - 33 * s, 2.3 * s)), L('ebony', E(x - 3.4 * s, cy + 1.5 * s, .8 * s, 2.8 * s)), L('ebony', E(x + 3.4 * s, cy + 1.5 * s, .8 * s, 2.8 * s)),
  L('paper', R(x - 3.2 * s, cy + 2 * s, 6.4 * s, 1 * s)), L('ebony', RR(x - 2 * s, cy + 6 * s, 4 * s, 6 * s, 1.5 * s))]; };
stageOf('cremona', { varnish: '#B5672F', varnishLight: '#D9B27A', varnishDark: '#6E3A1E', ebony: '#1C1A20', paper: '#F1EEE6', metal: '#6B6C78' }, () => {
  const l = [L('#E6D5B8', R(-30, -240, 472, 480)), L('#CDB891', R(-30, -240, 472, 80))];
  l.push(L('#5A3A22', R(28, 26, 128, 140)), L('SKY', R(34, 32, 116, 128)), L('#4C4676', R(42, 104, 24, 54)), L('#4C4676', R(74, 90, 28, 68)), L('#4C4676', R(110, 112, 30, 46)), L('#5A3A22', R(88, 32, 5, 128)), L('#5A3A22', R(34, 92, 116, 5)));
  l.push(L('#5A3A22', R(196, 14, 196, 6)), ...rep(5, i => { const x = 216 + i * 40, len = 16 + (i % 2) * 9, y = 20 + len; return [L('#8A7A66', R(x - .7, 20, 1.4, len)), ...violinAt(x, y + 46, .9, i % 2 ? 'varnish' : 'varnishLight')]; }));
  l.push(L('#6B6C78', R(300, -240, 2, 250)), L('GLOW', C(301, 22, 46), 1, { anim: 'flick:4' }), L('#6B6C78', PG([[282, 10], [320, 10], [312, -6], [290, -6]])), L('#FFE9B0', C(301, 14, 3.4)));
  l.push(L('#8E5E3A', R(-30, 200, 472, 260), 2), ...rep(9, i => L('#74492C', PG([[i * 52 - 20, 200], [i * 52 - 17, 200], [206 + (i * 52 - 17 - 206) * 2.4, 460], [206 + (i * 52 - 20 - 206) * 2.4, 460]]), 2)));
  l.push(L('#5A3A22', R(-20, 282, 452, 20), 2), L('#A9713F', R(-20, 266, 452, 16), 2), L('#7E5230', R(-20, 278, 452, 5), 2));
  l.push(L('#D9B27A', E(150, 262, 34, 10), 2), L('#CDB891', E(150, 260, 22, 6), 2), L('#6B6C78', R(236, 258, 34, 7), 2), L('#5A3A22', R(240, 255, 24, 3), 2), L('#6B6C78', R(292, 257, 5, 8), 2), L('#6B6C78', R(306, 255, 14, 9), 2), L('#8A7A66', E(348, 262, 16, 5), 2));
  return l;
});
