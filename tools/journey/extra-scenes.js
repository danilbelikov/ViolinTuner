// Postcards drawn after the handoff, with its own helpers (R, RR, E, C, PG, ARCH, L, SKYL, rep,
// hero, lamp, tree) and its rules: three planes (depth 0 far · 1 the building · 2 the foreground),
// two faces on a building, windows and columns by rule, two lamps, a tree, the traveller.
// A location's colours are tokens: add them to ScenePalette.locations as well — SceneTest fails otherwise.
const EXTRA_LOCPAL = {
  prague: { stone: '#D8C39A', stoneLit: '#E8D7B2', stoneShade: '#B39B72', stoneBase: '#C2AC82', trim: '#F2ECD8', roof: '#5E7A74', roofLit: '#7C9A92', dark: '#3A3040', statue: '#8A8478' },
  leipzig: { plaster: '#EFE7D6', plasterShade: '#D2C7B0', roofTile: '#9A4B3A', roofTileLit: '#B8614C', roofTileShade: '#74372B', copper: '#5E8F80', copperLit: '#7FAF9F', dark: '#3A3040', stoneBase: '#B9AE98' },
  berlin: { ochre: '#D9A441', ochreLit: '#ECC062', ochreShade: '#B07F2C', ochreDark: '#8C6420', glass: '#6F7F93', concrete: '#CFC8BC', concreteShade: '#ABA498', dark: '#3A3040' },
  amsterdam: { brick: '#A85C44', brickLit: '#C0735A', brickShade: '#82432F', stone: '#EDE3CF', stoneShade: '#CFC3AA', roof: '#4E5560', roofLit: '#6A727E', gold: '#E2B74E', dark: '#3A3040', quay: '#6E5A4E', quayLit: '#8A7466', boat: '#3E5A4C', boatLit: '#C9B48A' },
};

// Prague — the Rudolfinum: a long sandstone front, a bowed middle with tall arched windows,
// a balustrade with statues; the castle with the spires of St Vitus on its hill far behind.
const PRAGUE = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', PG([[0, 190], [0, 150], [40, 138], [120, 128], [200, 132], [260, 146], [300, 160], [300, 190]]), 0), L('far', R(60, 118, 120, 16), 0), L('farLit', R(60, 118, 50, 16), 0), ...[[104, 84, 1], [118, 90, .8], [140, 96, .7]].flatMap(([x, y, s]) => [L('far', R(x - 4 * s, y + 12 * s, 8 * s, 30 * s), 0), L('far', PG([[x - 5 * s, y + 12 * s], [x + 5 * s, y + 12 * s], [x, y - 12 * s]]), 0)]), L('far', R(352, 150, 60, 40), 0), L('farLit', R(380, 138, 32, 52), 0));
  // wings, the right one turned away
  l.push(L('stoneShade', PG([[340, 96], [366, 104], [366, 190], [340, 190]])), L('stone', R(60, 96, 280, 94)), L('stoneLit', R(60, 96, 84, 94)), L('stoneBase', R(60, 160, 280, 30)), L('stoneShade', R(60, 159, 280, 1.5)), L('trim', R(56, 90, 288, 6)));
  l.push(...rep(4, i => [L('window', ARCH(70 + i * 19, 108, 9, 40)), L('dark', R(70 + i * 19, 166, 9, 16))]), ...rep(4, i => [L('window', ARCH(262 + i * 19, 108, 9, 40)), L('dark', R(262 + i * 19, 166, 9, 16))]));
  // the middle steps forward: pilasters, five tall arches, three doors
  l.push(L('rgba(20,16,30,.22)', R(262, 84, 6, 106)), L('stoneLit', R(150, 84, 112, 106)), L('stoneBase', R(150, 160, 112, 30)), L('trim', R(146, 78, 120, 6)), ...rep(6, i => L('trim', R(152 + i * 21.2, 86, 4, 74))), ...rep(5, i => [L('window', ARCH(159.5 + i * 21.2, 96, 11, 56)), ...(eve ? [L('rgba(255,217,138,.16)', R(156 + i * 21.2, 152, 18, 5))] : [])]), ...rep(3, i => L('dark', ARCH(178 + i * 21.2, 164, 13, 26))));
  // balustrade and statues by rule, low copper roof
  l.push(L('roof', PG([[70, 90], [330, 90], [316, 78], [84, 78]])), L('roofLit', PG([[70, 90], [150, 90], [150, 78], [84, 78]])), L('trim', R(146, 70, 120, 2)), ...rep(20, i => L('trim', R(148 + i * 6, 72, 2, 6))), ...rep(6, i => [L('statue', R(151 + i * 21.2, 58, 5, 12)), L('statue', C(153.5 + i * 21.2, 55, 2.6))]));
  l.push(L('trim', R(140, 188, 132, 3)), L('trim', R(132, 191, 148, 3)), L('groundLit', R(0, 190, 412, 14), 2), L('ground', R(0, 204, 412, 56), 2), L('groundShade', R(0, 203, 412, 1.5), 2), L('rgba(20,16,30,.18)', PG([[60, 194], [366, 194], [386, 206], [74, 206]]), 2));
  l.push(...tree(30, 236, 1.1, 2), ...tree(392, 232, .9, 2), ...lamp(108, 238, 1, 2, eve), ...lamp(304, 238, 1, 2, eve), ...hero(232, 254, 1, 2));
  return l;
};

// Leipzig — St Thomas: a white hall church under a very steep tiled roof, an octagonal tower
// with a copper cap at its west end, tall Gothic windows; roofs of the old town behind.
const LEIPZIG = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', PG([[0, 190], [0, 158], [18, 144], [36, 158], [36, 150], [58, 134], [80, 150], [80, 190]]), 0), L('far', PG([[318, 190], [318, 152], [340, 136], [362, 152], [362, 146], [388, 130], [412, 146], [412, 190]]), 0), L('farLit', PG([[340, 136], [362, 152], [362, 190], [340, 190]]), 0));
  // the nave: a lit long side, the roof nearly as tall as the wall
  l.push(L('plaster', R(150, 128, 196, 62)), L('stoneBase', R(150, 178, 196, 12)), L('roofTile', PG([[146, 128], [350, 128], [332, 62], [164, 62]])), L('roofTileLit', PG([[146, 128], [240, 128], [240, 62], [164, 62]])), L('roofTileShade', R(146, 126, 204, 2.5)), ...rep(5, i => L('roofTileShade', PG([[178 + i * 34, 96], [186 + i * 34, 96], [182 + i * 34, 88]]))));
  l.push(...rep(6, i => [L('plasterShade', R(156 + i * 32, 130, 5, 60)), L('window', ARCH(166 + i * 32, 136, 12, 40))]), L('plasterShade', PG([[346, 128], [366, 134], [366, 190], [346, 190]])), L('roofTileShade', PG([[350, 128], [368, 134], [346, 70], [332, 62]])));
  // the tower: square below, octagon above, copper cap and lantern
  l.push(L('plasterShade', R(118, 84, 14, 106)), L('plaster', R(88, 84, 34, 106)), L('stoneBase', R(88, 178, 44, 12)), L('plasterShade', R(88, 110, 44, 2)), L('dark', ARCH(98, 156, 14, 34)), L('window', ARCH(100, 120, 10, 24)), L('plaster', PG([[92, 84], [128, 84], [124, 56], [96, 56]])), L('plasterShade', PG([[118, 84], [128, 84], [124, 56], [116, 56]])), L('window', ARCH(105, 62, 8, 16)), L('copper', `M94 56Q94 36 110 28Q126 36 126 56Z`), L('copperLit', `M94 56Q94 36 110 28L110 56Z`), L('copper', R(106, 16, 8, 12)), L('copperLit', R(106, 16, 4, 12)), L('copper', PG([[104, 16], [116, 16], [110, 2]])), L('copper', R(109.4, -6, 1.2, 9)));
  l.push(L('groundLit', R(0, 190, 412, 14), 2), L('ground', R(0, 204, 412, 56), 2), L('groundShade', R(0, 203, 412, 1.5), 2), L('rgba(20,16,30,.18)', PG([[88, 192], [366, 192], [388, 206], [104, 206]]), 2));
  // Bach on his plinth, as he stands by the south side
  l.push(L('rgba(20,16,30,.28)', E(286, 238, 14, 3), 2), L('groundLit', R(278, 214, 16, 24), 2), L('lamp', PG([[281, 214], [291, 214], [290, 192], [282, 192]]), 2), L('lamp', C(286, 188, 4), 2));
  l.push(...tree(36, 236, 1.2, 2), ...tree(388, 234, 1, 2), ...lamp(70, 238, 1, 2, eve), ...lamp(330, 238, 1, 2, eve), ...hero(214, 254, 1, 2));
  return l;
};

// Berlin — the Philharmonie: the ochre tent. No two lines parallel: three sloping peaks,
// each with a lit and a shaded face, a low glazed foyer under them.
const BERLIN = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', R(0, 140, 34, 50), 0), L('farLit', R(0, 140, 12, 50), 0), L('far', R(40, 156, 40, 34), 0), L('far', R(350, 120, 26, 70), 0), L('farLit', R(350, 120, 9, 70), 0), L('far', R(380, 150, 32, 40), 0), L('far', R(362, 96, 2, 24), 0));
  // the low foyer: concrete band and a run of glass
  l.push(L('concreteShade', PG([[330, 150], [372, 160], [372, 190], [330, 190]])), L('concrete', R(40, 150, 290, 40)), L('concreteShade', R(40, 150, 290, 3)), ...rep(17, i => L('window', R(48 + i * 16.5, 160, 11, 22))), L('dark', R(176, 166, 44, 24)), L('concrete', R(168, 160, 60, 5)));
  // the tent: back peak, then the two front ones
  l.push(L('ochreShade', PG([[150, 150], [196, 48], [232, 30], [290, 150]])), L('ochreDark', PG([[232, 30], [290, 150], [262, 150]])));
  l.push(L('ochre', PG([[56, 150], [74, 104], [128, 58], [170, 96], [206, 150]])), L('ochreLit', PG([[56, 150], [74, 104], [128, 58], [128, 150]])), L('ochreShade', PG([[170, 96], [206, 150], [184, 150]])));
  l.push(L('ochre', PG([[196, 150], [236, 92], [292, 66], [338, 112], [352, 150]])), L('ochreLit', PG([[196, 150], [236, 92], [292, 66], [286, 150]])), L('ochreDark', PG([[338, 112], [352, 150], [326, 150]])));
  // seams of the cladding by rule, the strip windows of the hall
  l.push(...rep(7, i => L('ochreShade', R(70 + i * 9, 108 + i * 2, 1, 42 - i * 2), 1, { op: .5 })), ...rep(8, i => L('ochreShade', R(206 + i * 10, 100 - i * 1.5, 1, 50 + i * 1.5), 1, { op: .5 })), L('glass', PG([[96, 118], [150, 110], [150, 116], [96, 124]])), L('glass', PG([[226, 116], [300, 104], [300, 110], [226, 122]])), ...(eve ? [L('window', PG([[96, 118], [150, 110], [150, 116], [96, 124]]), 1, { op: .8 }), L('window', PG([[226, 116], [300, 104], [300, 110], [226, 122]]), 1, { op: .8 })] : []));
  l.push(L('groundLit', R(0, 190, 412, 14), 2), L('ground', R(0, 204, 412, 56), 2), L('groundShade', R(0, 203, 412, 1.5), 2), L('rgba(20,16,30,.18)', PG([[40, 192], [372, 192], [394, 206], [58, 206]]), 2));
  l.push(...tree(24, 236, 1.1, 2), ...tree(394, 234, 1, 2), ...lamp(122, 238, 1, 2, eve), ...lamp(296, 238, 1, 2, eve), ...hero(244, 254, 1, 2));
  return l;
};

// Amsterdam — the Concertgebouw: red brick and pale stone, a temple front of six columns,
// the golden lyre on the roof; gabled canal houses far behind, a canal with a boat in front.
const AMSTERDAM = (eve = true) => {
  const l = [...SKYL];
  const gable = (x, w, hh, lit) => [L(lit ? 'farLit' : 'far', R(x, 190 - hh, w, hh), 0), L(lit ? 'farLit' : 'far', PG([[x, 190 - hh], [x + w, 190 - hh], [x + w * .7, 176 - hh], [x + w * .3, 176 - hh]]), 0), L(lit ? 'farLit' : 'far', R(x + w * .38, 170 - hh, w * .24, 8), 0)];
  l.push(...gable(0, 22, 46, false), ...gable(22, 20, 56, true), ...gable(42, 24, 42, false), ...gable(346, 22, 50, false), ...gable(368, 20, 40, true), ...gable(388, 24, 54, false));
  // the body: brick, stone bands, a turned side
  l.push(L('brickShade', PG([[334, 92], [358, 100], [358, 190], [334, 190]])), L('stoneShade', R(78, 184, 256, 6)), L('brick', R(78, 92, 256, 92)), L('brickLit', R(78, 92, 70, 92)), L('stone', R(78, 150, 256, 3)), L('stone', R(78, 112, 256, 2)), L('stone', R(74, 86, 264, 6)), L('stoneShade', R(78, 176, 256, 8)));
  l.push(L('roof', PG([[78, 86], [334, 86], [316, 62], [96, 62]])), L('roofLit', PG([[78, 86], [150, 86], [150, 62], [96, 62]])));
  l.push(...rep(3, i => [L('window', ARCH(88 + i * 20, 118, 10, 28)), L('dark', R(88 + i * 20, 158, 10, 16))]), ...rep(3, i => [L('window', ARCH(274 + i * 20, 118, 10, 28)), L('dark', R(274 + i * 20, 158, 10, 16))]));
  // the temple front
  l.push(L('rgba(20,16,30,.22)', R(264, 84, 6, 100)), L('stone', R(152, 100, 112, 84)), L('stoneShade', R(152, 148, 112, 2)), ...rep(5, i => [L('window', ARCH(164.5 + i * 19.6, 106, 9, 38)), L('dark', ARCH(163 + i * 19.6, 156, 12, 28))]), ...rep(6, i => [L('stone', R(154 + i * 19.6, 100, 6, 84)), L('stoneShade', R(158.4 + i * 19.6, 100, 1.6, 84)), L('stone', R(152.5 + i * 19.6, 98, 9, 3))]), L('stone', R(148, 90, 120, 8)), L('stone', PG([[146, 90], [270, 90], [208, 60]])), L('stoneShade', PG([[158, 88], [258, 88], [208, 66]])), L('stone', R(148, 184, 120, 3)), L('stone', R(142, 187, 132, 3)));
  // the lyre
  l.push(L('gold', R(206.5, 50, 3, 10)), L('gold', `M200 50Q196 38 202 32L204 33Q200 39 203 50Z`), L('gold', `M216 50Q220 38 214 32L212 33Q216 39 213 50Z`), L('gold', R(201, 32, 14, 1.6)), L('gold', R(207.4, 33, 1.2, 17)));
  // quay, canal, reflections, a boat
  l.push(L('quayLit', R(0, 190, 412, 10), 2), L('quay', R(0, 200, 412, 8), 2), L('water', R(0, 208, 412, 30), 2), ...rep(9, i => L('waterLit', R(14 + (i * 97) % 370, 212 + (i * 7) % 24, 18 + (i * 29) % 34, 1.4), 2, { op: .45 })), ...(eve ? rep(5, i => L('waterLit', R(165 + i * 19.6, 210, 8, 16), 2, { op: .35 })) : []));
  l.push(L('boat', PG([[60, 222], [126, 222], [118, 232], [68, 232]]), 2), L('boatLit', R(78, 214, 30, 8), 2), L('window', R(82, 216, 6, 4), 2), L('window', R(92, 216, 6, 4), 2));
  l.push(L('quayLit', R(0, 238, 412, 4), 2), L('ground', R(0, 242, 412, 18), 2), ...tree(30, 204, .8, 2), ...tree(384, 204, .8, 2), ...lamp(132, 204, .7, 2, eve), ...lamp(288, 204, .7, 2, eve), ...hero(250, 257, .9, 2));
  return l;
};

const EXTRA_SCENES = {
  prague: { fn: PRAGUE, loc: 'prague' },
  leipzig: { fn: LEIPZIG, loc: 'leipzig' },
  berlin: { fn: BERLIN, loc: 'berlin' },
  amsterdam: { fn: AMSTERDAM, loc: 'amsterdam' },
};
