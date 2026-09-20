// Postcards drawn after the handoff, with its own helpers (R, RR, E, C, PG, ARCH, L, SKYL, rep,
// hero, lamp, tree) and its rules: three planes (depth 0 far · 1 the building · 2 the foreground),
// two faces on a building, windows and columns by rule, two lamps, a tree, the traveller.
// A location's colours are tokens; the exporter writes them into ExtraScenePalettes.kt, nothing is copied by hand.
const EXTRA_LOCPAL = {
  prague: { stone: '#D8C39A', stoneLit: '#E8D7B2', stoneShade: '#B39B72', stoneBase: '#C2AC82', trim: '#F2ECD8', roof: '#5E7A74', roofLit: '#7C9A92', dark: '#3A3040', statue: '#8A8478' },
  leipzig: { plaster: '#EFE7D6', plasterShade: '#D2C7B0', roofTile: '#9A4B3A', roofTileLit: '#B8614C', roofTileShade: '#74372B', copper: '#5E8F80', copperLit: '#7FAF9F', dark: '#3A3040', stoneBase: '#B9AE98' },
  berlin: { ochre: '#D9A441', ochreLit: '#ECC062', ochreShade: '#B07F2C', ochreDark: '#8C6420', glass: '#6F7F93', concrete: '#CFC8BC', concreteShade: '#ABA498', dark: '#3A3040' },
  amsterdam: { brick: '#A85C44', brickLit: '#C0735A', brickShade: '#82432F', stone: '#EDE3CF', stoneShade: '#CFC3AA', roof: '#4E5560', roofLit: '#6A727E', gold: '#E2B74E', dark: '#3A3040', quay: '#6E5A4E', quayLit: '#8A7466', boat: '#3E5A4C', boatLit: '#C9B48A' },
  paris: { stone: '#DCCDB0', stoneLit: '#ECDFC4', stoneShade: '#B8A685', stoneBase: '#C9B995', trim: '#F3ECDA', copper: '#5F9485', copperLit: '#84B5A4', gold: '#E2B74E', dark: '#3A3040' },
  spb: { wall: '#DDB653', wallLit: '#EBCB74', wallShade: '#B3933F', wallBase: '#C4A148', trim: '#F6F1E4', roof: '#7A8084', roofLit: '#979DA1', gold: '#E2B74E', dark: '#3A3040' },
  moscow: { wall: '#EAD9A8', wallLit: '#F5E8C2', wallShade: '#C4B281', wallBase: '#D3C293', trim: '#F8F4EA', roof: '#6F8278', roofLit: '#8DA096', bronze: '#3F4E47', plinth: '#8F4A3C', plinthLit: '#A96152', dark: '#3A3040' },
  newyork: { brick: '#B5683C', brickLit: '#CC7F50', brickShade: '#8A4C2A', trim: '#DDBB92', marquee: '#2E2A38', taxi: '#F2C230', taxiShade: '#C99A16', taxiDark: '#2A2430', dark: '#3A3040' },
  buenosaires: { stone: '#E4D6BC', stoneLit: '#F1E6CF', stoneShade: '#BBAA8A', stoneBase: '#CDBE9F', trim: '#F7F1E2', roof: '#7D6A5A', roofLit: '#9A8675', dark: '#3A3040' },
  tokyo: { tile: '#B9A48C', tileLit: '#CDB9A0', tileShade: '#8F7C66', glass: '#6F8AA0', steel: '#9AA3AD', steelLit: '#BCC5CE', steelShade: '#6E7781', towerRed: '#D8452E', towerWhite: '#F2EFE6', blossom: '#F2B6C8', blossomLit: '#FAD3DE', blossomShade: '#D48BA4', gold: '#E2B74E', dark: '#3A3040' },
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
  l.push(L('quayLit', R(0, 190, 412, 10), 2), L('quay', R(0, 200, 412, 8), 2), L('water', R(0, 208, 412, 30), 2), ...rep(9, i => L('waterLit', R(14 + (i * 97) % 370, 212 + (i * 7) % 24, 18 + (i * 29) % 34, 1.4), 2, { op: .45 })), ...(eve ? rep(5, i => L('rgba(255,217,138,.2)', R(166 + i * 19.6, 210, 6, 16), 2)) : []));
  l.push(L('boat', PG([[60, 222], [126, 222], [118, 232], [68, 232]]), 2), L('boatLit', R(78, 214, 30, 8), 2), L('window', R(82, 216, 6, 4), 2), L('window', R(92, 216, 6, 4), 2));
  l.push(L('quayLit', R(0, 238, 412, 4), 2), L('ground', R(0, 242, 412, 18), 2), ...tree(30, 204, .8, 2), ...tree(384, 204, .8, 2), ...lamp(132, 204, .7, 2, eve), ...lamp(288, 204, .7, 2, eve), ...hero(250, 257, .9, 2));
  return l;
};

const GROUND = (x0, x1) => [L('groundLit', R(0, 190, 412, 14), 2), L('ground', R(0, 204, 412, 56), 2), L('groundShade', R(0, 203, 412, 1.5), 2), L('rgba(20,16,30,.18)', PG([[x0, 192], [x1, 192], [x1 + 22, 206], [x0 + 16, 206]]), 2)];

// Paris — the Opéra Garnier: an arcade of seven arches, the loggia of paired columns, the gilded
// groups on the corners of the attic, the green copper dome and the gable of the stage behind it.
const PARIS = (eve = true) => {
  const l = [...SKYL];
  const block = (x, w, hh, lit) => [L(lit ? 'farLit' : 'far', R(x, 190 - hh, w, hh), 0), L('far', PG([[x, 190 - hh], [x + w, 190 - hh], [x + w - 5, 180 - hh], [x + 5, 180 - hh]]), 0)];
  l.push(...block(0, 42, 50, false), ...block(372, 40, 56, true));
  l.push(L('stoneShade', PG([[126, 84], [286, 84], [206, 28]])), L('stone', PG([[126, 84], [206, 84], [206, 28]])), L('copper', `M146 84A60 42 0 0 1 266 84Z`), L('copperLit', `M146 84A60 42 0 0 1 206 42L206 84Z`), L('gold', R(201, 32, 10, 10)), L('gold', C(206, 30, 5)), L('gold', R(205.2, 16, 1.6, 12)));
  l.push(L('stoneShade', PG([[356, 84], [378, 92], [378, 190], [356, 190]])), L('stone', R(56, 84, 300, 106)), L('stoneLit', R(56, 84, 84, 106)), L('stoneBase', R(56, 152, 300, 38)), L('gold', R(56, 82, 300, 2.5)), L('trim', R(52, 98, 308, 3)));
  l.push(...[62, 350].flatMap(x => [L('gold', PG([[x - 9, 84], [x, 58], [x + 9, 84]])), L('gold', PG([[x - 15, 68], [x, 62], [x + 15, 68], [x, 74]])), L('gold', C(x, 56, 3))]));
  l.push(...rep(7, i => [L('window', ARCH(76 + i * 40, 106, 22, 42)), L('gold', C(87 + i * 40, 103.5, 1.8)), ...(eve ? [L('rgba(255,217,138,.16)', R(72 + i * 40, 148, 30, 5))] : [])]), ...rep(8, i => [L('trim', R(62 + i * 40, 102, 4, 46)), L('trim', R(68 + i * 40, 102, 4, 46)), L('stoneShade', R(72 + i * 40, 102, 1.4, 46))]));
  l.push(L('trim', R(52, 148, 308, 4)), ...rep(7, i => L('dark', ARCH(74 + i * 40, 158, 26, 32))), L('trim', R(50, 188, 312, 3)), L('trim', R(44, 191, 324, 3)));
  l.push(...GROUND(44, 378), ...lamp(30, 238, 1, 2, eve), ...lamp(150, 238, 1, 2, eve), ...lamp(270, 238, 1, 2, eve), ...lamp(388, 238, 1, 2, eve), ...hero(224, 254, 1, 2));
  return l;
};

// St Petersburg — the Philharmonia: Petersburg yellow and white, eight columns over a rusticated
// ground floor; the gilded dome of St Isaac's and the needle of the Admiralty far behind.
const SPB = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', R(0, 160, 50, 30), 0), L('far', R(8, 136, 34, 26), 0), L('gold', `M10 136A15 20 0 0 1 40 136Z`, 0), L('gold', R(23.6, 104, 2.8, 14), 0), L('far', R(374, 160, 38, 30), 0), L('far', R(388, 140, 12, 22), 0), L('gold', PG([[392, 140], [396, 140], [394, 84]]), 0));
  l.push(L('wallShade', PG([[362, 88], [386, 96], [386, 190], [362, 190]])), L('wall', R(50, 88, 312, 102)), L('wallLit', R(50, 88, 96, 102)), L('wallBase', R(50, 156, 312, 34)), ...rep(3, i => L('wallShade', R(50, 164 + i * 8, 312, 1), 1, { op: .5 })), L('trim', R(50, 154, 312, 2.5)));
  l.push(L('roof', PG([[50, 82], [362, 82], [350, 72], [62, 72]])), L('roofLit', PG([[50, 82], [146, 82], [146, 72], [62, 72]])), L('trim', R(46, 82, 320, 6)));
  l.push(...[60, 276].flatMap(x0 => rep(4, i => [L('trim', R(x0 - 2 + i * 20, 96, 14, 2)), L('window', R(x0 + i * 20, 98, 10, 22)), L('window', R(x0 + i * 20, 128, 10, 20)), L('dark', R(x0 + i * 20, 164, 10, 18))])));
  l.push(L('rgba(20,16,30,.22)', R(266, 88, 5, 102)), L('wallLit', R(146, 88, 120, 68)), L('wall', R(150, 68, 112, 14)), L('trim', R(148, 66, 116, 3)), L('trim', R(142, 82, 128, 8)));
  l.push(...rep(7, i => [L('window', ARCH(158.3 + i * 15.6, 98, 6, 26)), L('window', R(158.3 + i * 15.6, 132, 6, 18))]), ...rep(8, i => [L('trim', R(150 + i * 15.6, 92, 6, 64)), L('wallShade', R(154.6 + i * 15.6, 92, 1.4, 64), 1, { op: .6 }), L('trim', R(148.5 + i * 15.6, 90, 9, 3))]));
  l.push(...rep(3, i => L('dark', ARCH(176 + i * 22, 164, 14, 26))), L('dark', R(166, 160, 80, 3)), ...rep(7, i => L('lampGlass', C(172 + i * 11.4, 164.5, 1.3))));
  l.push(...GROUND(50, 386), ...tree(24, 236, 1, 2), ...lamp(110, 238, 1, 2, eve), ...lamp(302, 238, 1, 2, eve), ...tree(396, 234, .9, 2), ...hero(236, 254, 1, 2));
  return l;
};

// Moscow — the Great Hall of the Conservatory: a bowed front between two wings, tall arched
// windows of the hall; Tchaikovsky sits before it, one hand in the air.
const MOSCOW = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', R(0, 150, 36, 40), 0), L('far', PG([[8, 150], [28, 150], [18, 120]]), 0), L('far', R(376, 146, 36, 44), 0), L('farLit', R(376, 146, 12, 44), 0));
  l.push(...[[40, false], [282, true]].flatMap(([x, shade]) => [L(shade ? 'wallShade' : 'wall', R(x, 108, 90, 82)), L('wallBase', R(x, 160, 90, 30)), L('roof', PG([[x - 4, 108], [x + 94, 108], [x + 84, 96], [x + 6, 96]])), L('trim', R(x - 2, 106, 94, 3)), ...rep(4, i => [L('window', R(x + 10 + i * 20, 118, 10, 28)), L('dark', R(x + 10 + i * 20, 166, 10, 16))])]), L('wallShade', PG([[372, 108], [390, 114], [390, 190], [372, 190]])));
  l.push(L('roof', `M130 78A76 24 0 0 1 282 78Z`), L('roofLit', `M130 78A76 24 0 0 1 206 54L206 78Z`), L('wall', R(130, 78, 152, 112)), L('wallLit', R(130, 78, 40, 112)), L('wallShade', R(244, 78, 38, 112)), L('wallBase', R(130, 160, 152, 30)), L('trim', R(126, 74, 160, 6)), L('trim', R(130, 156, 152, 3)));
  l.push(...rep(9, i => { const ph = (-64 + i * 16) * Math.PI / 180, x = 206 + 80 * Math.sin(ph), w = 12 * Math.cos(ph); return [L('trim', ARCH(x - w * .7, 90, w * 1.4, 62)), L('window', ARCH(x - w / 2, 94, w, 56)), L('dark', R(x - w / 2, 166, w, 20))]; }));
  l.push(L('trim', R(122, 188, 168, 3)), ...GROUND(40, 390));
  l.push(L('rgba(20,16,30,.28)', E(210, 240, 20, 3.4), 2), L('plinth', R(194, 216, 28, 24), 2), L('plinthLit', R(194, 216, 10, 24), 2), L('bronze', PG([[196, 216], [220, 216], [218, 204], [212, 194], [202, 195], [198, 204]]), 2), L('bronze', C(208, 190, 3.8), 2), L('bronze', PG([[203, 199], [190, 193], [191, 190.5], [204, 195]]), 2));
  l.push(...fir(24, 238, .95, 2), ...fir(392, 240, 1, 2), ...lamp(96, 238, 1, 2, eve), ...lamp(320, 238, 1, 2, eve), ...hero(268, 254, 1, 2));
  return l;
};

// New York — Carnegie Hall: Roman brick the colour of rust, five great arched windows over five
// doors under a marquee of bulbs, the studio tower behind; towers of midtown, a yellow cab.
const NEWYORK = (eve = true) => {
  const l = [...SKYL];
  const tower = (x, y, w, lit) => [L(lit ? 'farLit' : 'far', R(x, y, w, 190 - y), 0), ...rep(Math.floor((180 - y) / 12), i => rep(Math.floor(w / 9), j => (i * 5 + j * 3) % 4 === 0 ? [] : [L('window', R(x + 3 + j * 9, y + 6 + i * 12, 4, 6), 0, { op: eve ? .7 : .5 })]).flat())];
  l.push(...tower(0, 56, 44, false), ...tower(46, 104, 22, true), ...tower(372, 84, 40, false), L('far', R(20, 30, 2, 26), 0));
  l.push(L('brickShade', PG([[346, 24], [366, 32], [366, 190], [346, 190]])), L('brick', R(262, 24, 84, 166)), L('trim', R(258, 20, 92, 5)), ...rep(10, i => rep(4, j => L((i * 3 + j) % 5 === 0 ? 'dark' : 'window', R(271 + j * 19, 32 + i * 15, 9, 9)))));
  l.push(L('brick', R(60, 74, 212, 116)), L('brickLit', R(60, 74, 72, 116)), L('rgba(20,16,30,.22)', R(272, 74, 5, 116)), L('trim', R(56, 68, 220, 7)), L('trim', R(60, 112, 212, 2.5)), L('trim', R(60, 150, 212, 2.5)));
  l.push(...rep(9, i => L('window', ARCH(70 + i * 22.4, 80, 10, 26))), ...rep(5, i => [L('trim', ARCH(72 + i * 39, 114, 30, 36)), L('window', ARCH(75 + i * 39, 118, 24, 32)), L('dark', ARCH(77 + i * 39, 160, 20, 30))]));
  l.push(L('marquee', R(66, 152, 200, 6)), ...rep(16, i => [...(eve ? [L('rgba(255,196,110,.5)', C(73 + i * 12.4, 159.5, 4), 1, { glow: true })] : []), L('lampGlass', C(73 + i * 12.4, 159.5, 1.4))]));
  l.push(...GROUND(60, 366));
  l.push(L('rgba(20,16,30,.28)', E(92, 240, 34, 3.4), 2), L('taxi', RR(58, 224, 68, 13, 3), 2), L('taxiShade', R(58, 232, 68, 5), 2), L('taxi', PG([[72, 224], [79, 213], [106, 213], [113, 224]]), 2), L('taxiDark', PG([[76, 223], [81, 215.5], [91, 215.5], [91, 223]]), 2), L('taxiDark', PG([[94, 223], [94, 215.5], [104, 215.5], [109, 223]]), 2), L('taxi', R(88, 209, 9, 4), 2), L('taxiDark', C(72, 238, 5), 2), L('taxiDark', C(112, 238, 5), 2), L('lampGlass', R(122, 227, 4, 3), 2), ...(eve ? [L('GLOW', C(128, 229, 14), 2)] : []));
  l.push(...lamp(164, 238, 1, 2, eve), ...lamp(316, 238, 1, 2, eve), ...tree(394, 236, .95, 2), ...hero(232, 254, 1, 2));
  return l;
};

// Buenos Aires — the Teatro Colón: a pale eclectic front, columns over a rusticated ground floor,
// a gable on top of the middle; palms on the square.
const palm = (x, y, s, depth = 2) => { const tx = x + 1.5 * s, ty = y - 52 * s; return [L('rgba(20,16,30,.28)', E(x + 3 * s, y + 1, 12 * s, 3 * s), depth), L('trunk', PG([[x - 2.2 * s, y], [x + 2.2 * s, y], [x + 2.6 * s, ty], [x + .4 * s, ty]]), depth), ...[[-27, -2], [-20, -18], [-7, -26], [9, -26], [21, -17], [28, -1]].map(([ex, ey], i) => L(i % 2 ? 'foliage' : 'foliageShade', `M${tx} ${ty}Q${tx + ex * s * .45} ${ty + ey * s * 1.25 - 5 * s} ${tx + ex * s} ${ty + ey * s + 8 * s}Q${tx + ex * s * .5} ${ty + ey * s * .55 + 1 * s} ${tx} ${ty}Z`, depth)), L('foliageLit', C(tx, ty, 2.6 * s), depth)]; };
const BUENOSAIRES = (eve = true) => {
  const l = [...SKYL];
  l.push(L('far', R(0, 146, 44, 44), 0), L('farLit', R(0, 146, 14, 44), 0), L('far', R(370, 136, 42, 54), 0), L('far', R(384, 118, 14, 20), 0));
  l.push(L('stoneShade', PG([[362, 78], [386, 86], [386, 190], [362, 190]])), L('stone', R(50, 78, 312, 112)), L('stoneLit', R(50, 78, 96, 112)), L('stoneBase', R(50, 152, 312, 38)), ...rep(4, i => L('stoneShade', R(50, 158 + i * 8, 312, 1), 1, { op: .45 })), L('roof', PG([[60, 72], [352, 72], [338, 58], [74, 58]])), L('roofLit', PG([[60, 72], [146, 72], [146, 58], [74, 58]])), L('trim', R(46, 72, 320, 6)), L('trim', R(50, 100, 312, 2.5)), L('trim', R(50, 149, 312, 3)));
  l.push(...[58, 274].flatMap(x0 => rep(4, i => [L('dark', R(x0 + 2 + i * 21, 84, 8, 10)), L('window', ARCH(x0 + i * 21, 108, 12, 34)), L('dark', ARCH(x0 + i * 21, 160, 12, 30))])));
  l.push(L('rgba(20,16,30,.22)', R(266, 78, 5, 112)), L('stoneLit', R(146, 78, 120, 74)), L('trim', R(142, 72, 128, 6)), L('stone', PG([[146, 72], [266, 72], [206, 46]])), L('stoneShade', PG([[158, 70], [254, 70], [206, 51]])), L('trim', C(206, 62, 4)));
  l.push(...rep(5, i => [L('dark', R(165 + i * 20.4, 84, 8, 10)), L('window', ARCH(162 + i * 20.4, 106, 14, 40)), L('dark', ARCH(161 + i * 20.4, 158, 16, 32))]), ...rep(6, i => [L('trim', R(150 + i * 20.4, 102, 6, 48)), L('stoneShade', R(154.6 + i * 20.4, 102, 1.4, 48), 1, { op: .6 }), L('trim', R(148.5 + i * 20.4, 100, 9, 3))]));
  l.push(L('trim', R(140, 188, 132, 3)), ...GROUND(50, 386), ...palm(34, 240, 1.15, 2), ...palm(84, 232, .8, 2), ...palm(384, 240, 1.1, 2), ...lamp(128, 238, 1, 2, eve), ...lamp(296, 238, 1, 2, eve), ...hero(238, 254, 1, 2));
  return l;
};

// Tokyo — Suntory Hall: a low hall under one long curve of a roof, a glass entrance lit from
// within; the towers of Ark Hills over it, Tokyo Tower far off, cherries in blossom on the plaza.
const blossom = (x, y, s, depth = 2) => [L('rgba(20,16,30,.28)', E(x + 4 * s, y + 1, 18 * s, 4 * s), depth), L('trunk', PG([[x - 2.5 * s, y], [x + 2.5 * s, y], [x + 1.5 * s, y - 24 * s], [x - 1.5 * s, y - 24 * s]]), depth), L('trunk', PG([[x, y - 18 * s], [x + 12 * s, y - 32 * s], [x + 10 * s, y - 33 * s], [x - 1 * s, y - 21 * s]]), depth), L('blossomShade', C(x + 9 * s, y - 38 * s, 13 * s), depth), L('blossomShade', C(x - 8 * s, y - 34 * s, 14 * s), depth), L('blossom', C(x, y - 44 * s, 16 * s), depth), L('blossomLit', C(x - 6 * s, y - 50 * s, 7 * s), depth), ...[[-16, -6], [14, -2], [4, 3], [-5, -1]].map(([dx, dy]) => L('blossomLit', E(x + dx * s, y + dy * s, 1.6 * s, .8 * s), depth))];
const TOKYO = (eve = true) => {
  const l = [...SKYL];
  l.push(...rep(5, i => { const y0 = 44 + i * 29.2, y1 = y0 + 29.2, w = y => 1.6 + (y - 44) * .13; return L(i % 2 ? 'towerWhite' : 'towerRed', PG([[336 - w(y0), y0], [336 + w(y0), y0], [336 + w(y1), y1], [336 - w(y1), y1]]), 0); }), L('towerRed', R(335.2, 14, 1.6, 30), 0), L('towerWhite', R(325, 118, 22, 5), 0), L('towerWhite', R(331, 76, 10, 3.5), 0), L('far', R(372, 130, 40, 60), 0), L('farLit', R(372, 130, 13, 60), 0), L('far', R(0, 150, 40, 40), 0));
  l.push(L('steel', R(60, 30, 66, 160)), L('steelLit', R(60, 30, 22, 160)), ...rep(12, i => L(eve && i % 3 === 1 ? 'window' : 'glass', R(64, 38 + i * 12.4, 58, 5.5))), L('steelShade', R(134, 64, 52, 126)), L('steel', R(134, 64, 16, 126)), ...rep(9, i => L(eve && i % 4 === 2 ? 'window' : 'glass', R(138, 72 + i * 12.4, 44, 5.5))));
  l.push(L('tileShade', PG([[340, 132], [362, 140], [362, 190], [340, 190]])), L('tile', R(110, 130, 230, 60)), L('tileLit', R(110, 130, 72, 60)), L('steel', `M102 132Q225 92 348 132Z`), L('steelLit', `M102 132Q164 112 225 112L225 132Z`), L('steelShade', R(102, 131, 246, 2.5)));
  l.push(L('glass', R(168, 150, 114, 40)), ...(eve ? [L('window', R(170, 152, 110, 38), 1, { op: .85 })] : []), ...rep(10, i => L('steelShade', R(168 + i * 12.55, 150, 1.1, 40))), L('steelShade', R(168, 168, 114, 1.1)), L('steel', R(158, 145, 134, 5)), ...rep(7, i => L('gold', R(213 + i * 3.6, 136 - (3 - Math.abs(i - 3)) * 2.4, 2.2, 5 + (3 - Math.abs(i - 3)) * 2.4))), ...rep(3, i => [L('dark', R(120 + i * 15, 158, 8, 18)), L('dark', R(296 + i * 15, 158, 8, 18))]));
  l.push(...GROUND(110, 362), ...blossom(30, 238, 1.15, 2), ...blossom(388, 236, 1, 2), ...lamp(132, 238, 1, 2, eve), ...lamp(316, 238, 1, 2, eve), ...hero(244, 254, 1, 2));
  return l;
};

const EXTRA_SCENES = {
  prague: { fn: PRAGUE, loc: 'prague' },
  leipzig: { fn: LEIPZIG, loc: 'leipzig' },
  berlin: { fn: BERLIN, loc: 'berlin' },
  amsterdam: { fn: AMSTERDAM, loc: 'amsterdam' },
  paris: { fn: PARIS, loc: 'paris' },
  spb: { fn: SPB, loc: 'spb' },
  moscow: { fn: MOSCOW, loc: 'moscow' },
  newyork: { fn: NEWYORK, loc: 'newyork' },
  buenosaires: { fn: BUENOSAIRES, loc: 'buenosaires' },
  tokyo: { fn: TOKYO, loc: 'tokyo' },
};
