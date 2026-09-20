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

// ---------------------------------------------------------------------------------------------
// Second views: the halls from inside. Four builders, one grammar — a single vanishing point,
// everything that repeats placed by rule — and each hall its own sign: an organ, columns, portraits,
// petals, clouds, a painted ceiling. A room has no air and no time of day: `aerial: false`, `eve` unused.

// A shoebox hall seen from the stalls towards the stage (the grammar of the handoff's VIENNA_INT).
const HALL = (o = {}) => {
  const l = [];
  l.push(L('ceil', PG([[0, 0], [412, 0], [262, 78], [150, 78]])), ...(o.ribs ? rep(9, i => { const x = 20 + i * 46.5; return L('ceilShade', PG([[x, 0], [x + 10, 0], [206 + (x + 5 - 206) * .27 + 1.4, 78], [206 + (x + 5 - 206) * .27 - 1.4, 78]])); }) : rep(3, i => L('ceilShade', PG([[40 + i * 100, 6], [130 + i * 100, 6], [244 - (2 - i) * 6, 74], [166 + i * 28, 74]]), 1, { op: .5 }))));
  l.push(L('back', R(150, 78, 112, 90)), L('wallL', PG([[0, 0], [150, 78], [150, 168], [0, 260]])), L('wallR', PG([[412, 0], [262, 78], [262, 168], [412, 260]])), L('floor', PG([[0, 260], [150, 168], [262, 168], [412, 260]])));
  // what stands along the side walls, by rule: t 0 near … 1 at the stage
  const along = (n, fn) => rep(n, k => { const t = (k + .5) / n, x0 = 8 + 134 * t, sc = 1 - .52 * t, top = 78 * (x0 / 150), bottom = 260 - 92 * (x0 / 150); return [false, true].flatMap(right => fn(right ? 412 - x0 : x0, top, bottom, sc, right)); });
  if (o.windows) l.push(...along(5, (x, top, bottom, sc) => [L('chandelier', ARCH(x - 5 * sc, top + 14 * sc, 10 * sc, 34 * sc), 1, { op: .85 })]));
  if (o.medallions) l.push(...along(5, (x, top, bottom, sc) => [L('trim', E(x, top + 30 * sc, 7 * sc, 9.5 * sc)), L('portrait', E(x, top + 30 * sc, 5.2 * sc, 7.6 * sc)), L('trim', C(x, top + 27 * sc, 1.8 * sc), 1, { op: .7 })]));
  l.push(L('rail', PG([[0, 150], [150, 128], [150, 135], [0, 166]])), L('railShade', PG([[412, 150], [262, 128], [262, 135], [412, 166]])), ...(o.gallery ? [L('rail', PG([[0, 56], [150, 103], [150, 108], [0, 72]])), L('railShade', PG([[412, 56], [262, 103], [262, 108], [412, 72]]))] : []));
  if (o.columns) l.push(...along(6, (x, top, bottom, sc, right) => [L('columnShade', R(x - 4 * sc, top, 8 * sc, bottom - top)), L(right ? 'columnShade' : 'column', R(x - 3 * sc, top, 6 * sc, bottom - top)), L('column', R(x - 3 * sc, top, 2.4 * sc, bottom - top)), L('column', R(x - 5.5 * sc, top, 11 * sc, 4 * sc)), L('column', R(x - 5.5 * sc, bottom - 5 * sc, 11 * sc, 5 * sc))]));
  // the organ on the back wall
  if (o.organ) l.push(L('organCase', ARCH(160, 82, 92, 84)), L('organCase', R(156, 150, 100, 18)), ...rep(11, i => { const hh = 58 - Math.abs(i - 5) * (o.organ === 'crown' ? -4 : 5) - (o.organ === 'crown' ? 20 : 0); return [L('pipe', R(165 + i * 7.6, 148 - hh, 5, hh)), L('pipeShade', R(168.4 + i * 7.6, 148 - hh, 1.6, hh)), L('organCase', R(165 + i * 7.6, 140, 5, 2))]; }));
  if (o.petals) l.push(...rep(7, i => { const x = 128 + i * 26, y = 58 - Math.abs(i - 3) * 5; return [L('pipeShade', R(x - .4, y - 40, .8, 40), 1, { op: .5 }), L('petal', E(x, y, 12, 3.6)), L('petalLit', E(x - 2, y - 1, 7, 1.6))]; }));
  // chandeliers, by the handoff's rule
  l.push(...rep(o.chandeliers ?? 4, k => { const n = (o.chandeliers ?? 4) - 1, t = n ? k / n : 0, y = 26 + 50 * t, dx = 112 - 72 * t, r = (o.crystal ? 11 : 8) - 5 * t; return [-1, 1].flatMap(sg => [L('railShade', R(206 + sg * dx - .6, 0, 1.2, y - r)), L('rgba(255,196,110,.5)', C(206 + sg * dx, y, r * 3.2), 1, { glow: true }), L('chandelier', o.crystal ? PG([[206 + sg * dx - r, y - r * .4], [206 + sg * dx + r, y - r * .4], [206 + sg * dx, y + r * 1.3]]) : C(206 + sg * dx, y, r)), L('chandelier', E(206 + sg * dx, y - r * .4, r, r * .45)), L('rgba(255,255,255,.8)', C(206 + sg * dx - r * .3, y - r * .4, r * .3))]); }));
  // the stage: a piano and a lamp, as in Vienna
  l.push(L('wood', PG([[150, 168], [262, 168], [262, 176], [150, 176]])), L('hero', PG([[190, 168], [222, 168], [222, 152], [212, 148], [190, 152]])), L('hero', R(196, 168, 2, 6)), L('hero', R(216, 168, 2, 6)), L('lamp', R(233, 140, 1.4, 28)), L('lamp', PG([[228, 142], [240, 142], [238, 132], [230, 132]])));
  l.push(...rep(8, k => { const t = k / 7, y = 252 - 76 * t, hw = 198 - 84 * t, hh = 9 * (1 - .5 * t), n = Math.round(2 * hw / (16 * (1 - .4 * t))); return [L('seat', R(206 - hw, y - hh, 2 * hw, hh)), L('seatDark', R(206 - hw, y - hh, 2 * hw, hh * .3)), ...rep(n, i => L('seatDark', R(206 - hw + i * (2 * hw / n), y - hh, 1.2, hh))), ...(o.aisle ? [L('floor', PG([[206 - 10 * (1 - .55 * t), y - hh], [206 + 10 * (1 - .55 * t), y - hh], [206 + 10 * (1 - .55 * t), y], [206 - 10 * (1 - .55 * t), y]]))] : [])]; }));
  return l;
};

// A horseshoe of tiers seen from the stage (the grammar of the handoff's MILAN).
const HORSESHOE = (o = {}) => {
  const tiers = o.tiers ?? 5;
  const l = [L('hallDark', R(0, 0, 412, 260))];
  l.push(L('ceil', C(206, 14, 150)), L('ceilShade', C(206, 14, 120)));
  if (o.painted) l.push(...rep(o.painted.length, i => { const a0 = Math.PI * (i / o.painted.length), a1 = Math.PI * ((i + 1) / o.painted.length); return L(o.painted[i], `M206 14L${206 + 96 * Math.cos(a0)} ${14 + 96 * Math.sin(a0)}A96 96 0 0 1 ${206 + 96 * Math.cos(a1)} ${14 + 96 * Math.sin(a1)}Z`, 1, { op: .85 }); }), L('rail', `M110 14a96 96 0 0 0 192 0`, 1, { stroke: 'rail', sw: 2.4, fillNone: true }), L('ceil', C(206, 14, 34)));
  else l.push(L('rail', C(206, 14, 92), 1, { op: .5 }));
  if (o.mushrooms) l.push(...rep(9, i => { const x = 96 + i * 27.5, y = 74 + ((i * 7) % 3) * 9 - Math.abs(i - 4) * 3; return [L('ceilShade', R(x - .4, 0, .8, y), 1, { op: .6 }), L('mushroomShade', E(x, y + 1.6, 13, 4)), L('mushroom', E(x, y, 13, 3.6))]; }));
  else l.push(L('rgba(255,196,110,.55)', C(206, 40, 64), 1, { glow: true }), L('railLit', R(205, 0, 2, 22)), L('chandelier', E(206, 40, 24, 15)), L('railLit', E(206, 30, 14, 6)), ...rep(9, i => L('chandelier', C(206 + Math.cos(Math.PI * (1 + i / 8)) * 30, 52 + Math.sin(Math.PI * (i / 8)) * 8, 1.8))));
  rep(tiers, k => { const rx = 280 - (80 / tiers) * k, ry = 250 - (170 / tiers) * k; l.push(L(k % 2 ? 'tierShade' : 'tier', E(206, 330, rx, ry)), L('rail', `M${206 - rx} 330a${rx} ${ry} 0 0 1 ${2 * rx} 0`, 1, { stroke: 'rail', sw: 2.2, fillNone: true })); rep(o.open ? 25 : 11, j => { const th = Math.PI * (1.08 + .84 * j / (o.open ? 24 : 10)), px = 206 + rx * Math.cos(th), py = 330 + ry * Math.sin(th); if (o.open) l.push(...rep(2, m => L('seat', R(px - 5 + m * 5.4, py + 7, 4.2, 7)))); else l.push(L('box', RR(px - 9, py + 4, 18, 20, 3)), L('seat', R(px - 9, py + 18, 18, 6))); if (!o.open) l.push(L('railLit', R(px - 10, py + 24, 20, 1.4))); }); });
  if (o.organ) l.push(L('organCase', R(176, 96, 60, 40)), ...rep(9, i => [L('pipe', R(180 + i * 6, 100 + Math.abs(i - 4) * 3, 4, 34 - Math.abs(i - 4) * 3)), L('pipeShade', R(182.8 + i * 6, 100 + Math.abs(i - 4) * 3, 1.2, 34 - Math.abs(i - 4) * 3))]));
  l.push(L('hallDark', E(206, 330, 200, 112)), ...rep(5, k => { const t = k / 4, y = 254 - 30 * t, hw = 196 - 40 * t, hh = 6 - 2 * t, n = Math.round(hw / 6); return [L('seat', R(206 - hw, y - hh, 2 * hw, hh)), ...rep(n, i => L('seatDark', R(206 - hw + i * (2 * hw / n), y - hh, 1, hh)))]; }));
  l.push(L('wood', R(0, 256, 412, 4), 2));
  if (o.curtain !== false) l.push(L('curtain', PG([[0, 0], [46, 0], [30, 260], [0, 260]]), 2), L('curtain', PG([[412, 0], [366, 0], [382, 260], [412, 260]]), 2), ...rep(4, i => [L('seatDark', PG([[6 + i * 10, 0], [10 + i * 10, 0], [6 + i * 7, 260], [4 + i * 7, 260]]), 2, { op: .5 }), L('seatDark', PG([[406 - i * 10, 0], [402 - i * 10, 0], [406 - i * 7, 260], [408 - i * 7, 260]]), 2, { op: .5 })]));
  else l.push(L('tier', PG([[0, 0], [26, 0], [18, 260], [0, 260]]), 2), L('tier', PG([[412, 0], [386, 0], [394, 260], [412, 260]]), 2), L('rail', R(18, 0, 2, 260), 2), L('rail', R(392, 0, 2, 260), 2));
  return l;
};

// A «vineyard»: terraces of seats on every side of a stage in the middle, under a tent of a ceiling.
const VINEYARD = (o = {}) => {
  const l = [L('hallDark', R(0, 0, 412, 260))];
  l.push(L('ceil', PG([[0, 0], [412, 0], [300, 66], [120, 66]])), L('ceilShade', PG([[0, 0], [120, 66], [0, 118]])), L('ceilLit', PG([[412, 0], [300, 66], [412, 118]])), L('ceilShade', PG([[120, 66], [300, 66], [330, 96], [90, 96]]), 1, { op: .55 }), ...rep(6, i => L('ceilShade', PG([[40 + i * 66, 0], [44 + i * 66, 0], [206 + (42 + i * 66 - 206) * .45 + 1, 66], [206 + (42 + i * 66 - 206) * .45 - 1, 66]]), 1, { op: .6 })));
  l.push(...rep(10, i => { const x = 60 + i * 32.4, y = 20 + Math.abs(i - 4.5) * 3; return [L('rgba(255,196,110,.5)', C(x, y, 9), 1, { glow: true }), L('chandelier', C(x, y, 1.8))]; }));
  if (o.organ === 'centre') l.push(L('organCase', R(168, 70, 76, 52)), ...rep(13, i => { const hh = 44 - Math.abs(i - 6) * 3.6; return [L('pipe', R(172 + i * 5.4, 118 - hh, 3.6, hh)), L('pipeShade', R(174.4 + i * 5.4, 118 - hh, 1.2, hh))]; }));
  if (o.organ === 'side') l.push(L('organCase', R(292, 78, 58, 44)), ...rep(9, i => { const hh = 22 + i * 2.2; return [L('pipe', R(296 + i * 5.6, 118 - hh, 3.8, hh)), L('pipeShade', R(298.6 + i * 5.6, 118 - hh, 1.2, hh))]; }));
  // terraces: rings round the stage, cut into blocks that stand at different heights
  rep(4, q => { const r = 3 - q, rx = 70 + 52 * r, ry = 20 + 25 * r, cy = 184 + 5 * r; rep(8, b => { const a0 = Math.PI * 2 * (b / 8) + .2 * r, a1 = a0 + Math.PI * 2 / 8 - .07, lift = ((b * 5 + r * 3) % 4) * 2.5 - 3; const pt = (a, k) => [206 + rx * k * Math.cos(a), cy + lift + ry * k * Math.sin(a)]; const steps = 5, outer = rep(steps + 1, s => [pt(a0 + (a1 - a0) * s / steps, 1)]), inner = rep(steps + 1, s => [pt(a1 - (a1 - a0) * s / steps, .72)]); l.push(L((b + r) % 2 ? 'terrace' : 'terraceShade', PG([...outer, ...inner]))); rep(4, row => { const k = .76 + row * .065; rep(steps, s => { const [x, y] = pt(a0 + (a1 - a0) * (s + .5) / steps, k); l.push(L('seat', R(x - 2.2, y - 1.1, 4.4, 2.2))); }); }); }); });
  l.push(L('stageShade', E(206, 188, 66, 21)), L('stage', E(206, 185, 64, 19)), L('stageLit', E(200, 182, 40, 11), 1, { op: .6 }), L('hero', PG([[192, 184], [220, 184], [220, 172], [211, 169], [192, 172]])), L('hero', R(197, 184, 1.8, 5)), L('hero', R(214, 184, 1.8, 5)), ...rep(7, i => L('hero', C(176 + i * 10, 194 - Math.abs(i - 3) * 1.4, 1.6))));
  if (o.clouds) l.push(...rep(8, i => { const x = 122 + i * 24, y = 96 + ((i * 5) % 3) * 9 - Math.abs(i - 3.5) * 4; return [L('ceilLit', R(x - .3, 40, .6, y - 40), 1, { op: .5 }), L('cloudShade', E(x, y + 1.4, 12, 3.4)), L('cloud', E(x, y, 12, 3))]; }));
  return l;
};

// A Gothic nave: a tunnel of pointed arches to one vanishing point, red ribs on white vaults,
// coloured glass at the end, pews either side of the aisle.
const NAVE = () => {
  const V = [206, 124], l = [L('vaultShade', R(0, 0, 412, 260))];
  const frame = s => { const hw = 214 * s, floorY = V[1] + 150 * s, spring = V[1] - 26 * s, apex = V[1] - 132 * s; return { hw, floorY, d: `M${206 - hw} ${floorY}V${spring}Q${206 - hw} ${apex + 40 * s} 206 ${apex}Q${206 + hw} ${apex + 40 * s} ${206 + hw} ${spring}V${floorY}Z` }; };
  const scales = rep(7, k => [1.18 * Math.pow(.72, k)]);
  scales.forEach((s, k) => { const f = frame(s); l.push(L(k % 2 ? 'vault' : 'vaultLit', f.d), L('rib', f.d, 1, { stroke: 'rib', sw: 3.2 * s + .6, fillNone: true })); if (k < 6) { const n = frame(scales[k + 1]); l.push(L('pier', R(206 - f.hw, V[1] - 26 * s, (f.hw - n.hw) * .34, f.floorY - V[1] + 26 * s)), L('pierShade', R(206 + f.hw - (f.hw - n.hw) * .34, V[1] - 26 * s, (f.hw - n.hw) * .34, f.floorY - V[1] + 26 * s))); } });
  const end = frame(scales[6]);
  l.push(L('vaultShade', end.d), L('rgba(255,196,110,.55)', C(206, 116, 30), 1, { glow: true }), ...[[-9, '#4C7FD0', '#D9574A'], [0, '#E2B74E', '#4C7FD0'], [9, '#D9574A', '#5FA36A']].flatMap(([dx, a, b]) => [L(a, ARCH(206 + dx - 3.4, 98, 6.8, 34)), L(b, R(206 + dx - 3.4, 112, 6.8, 8)), L('rib', R(206 + dx - .4, 98, .8, 34))]), L('wood', R(196, 136, 20, 8)), L('chandelier', R(204.4, 128, 3.2, 8)));
  l.push(L('floor', PG([[0, 260], [206 - end.hw, end.floorY], [206 + end.hw, end.floorY], [412, 260]])), L('aisle', PG([[170, 260], [206 - end.hw * .22, end.floorY], [206 + end.hw * .22, end.floorY], [242, 260]])), ...rep(7, k => { const t = k / 6, y = 254 - (254 - end.floorY - 6) * t, sc = 1 - .8 * t, inner = 38 * sc + 3, outer = 176 * sc + 12; return [-1, 1].flatMap(sg => [L('woodShade', PG([[206 + sg * inner, y], [206 + sg * outer, y], [206 + sg * outer, y - 13 * sc], [206 + sg * inner, y - 13 * sc]])), L('wood', PG([[206 + sg * inner, y - 13 * sc], [206 + sg * outer, y - 13 * sc], [206 + sg * outer, y - 16 * sc], [206 + sg * inner, y - 16 * sc]]))]); }));
  l.push(...rep(3, k => { const t = k / 2, y = 60 + 34 * t, r = 7 - 4 * t; return [L('pierShade', R(205.5, 0, 1, y), 1, { op: .5 }), L('rgba(255,196,110,.5)', C(206, y, r * 3), 1, { glow: true }), L('chandelier', E(206, y, r * 1.5, r * .5)), ...rep(5, i => L('chandelier', C(206 + (i - 2) * r * .7, y - r * .5, r * .22)))]; }));
  return l;
};

const RED_HALL = { seat: '#8E2F3F', seatDark: '#5A1E2A', wood: '#A9713F' };
const INT_LOCPAL = {
  salzburgInt: { ...RED_HALL, ceil: '#F3EEE2', ceilShade: '#DDD5C2', back: '#E8E0CE', wallL: '#F0E9D9', wallR: '#CFC6B1', floor: '#5A1E2A', rail: '#E2B74E', railShade: '#B08A45', organCase: '#7A5B3A', pipe: '#E9C98D', pipeShade: '#B08A45' },
  pragueInt: { ceil: '#EFE6D0', ceilShade: '#D8CCAE', back: '#C9B78F', wallL: '#E3D6B6', wallR: '#BBAA82', floor: '#2F3A55', rail: '#E2B74E', railShade: '#B08A45', column: '#F6F1E4', columnShade: '#CFC6B1', organCase: '#5E4630', pipe: '#D7DCE2', pipeShade: '#9AA3AD', seat: '#3E5A8A', seatDark: '#27395C', wood: '#A9713F' },
  leipzigInt: { vault: '#EDE7DA', vaultLit: '#F7F3EA', vaultShade: '#CFC7B6', rib: '#A8503E', pier: '#F2ECDF', pierShade: '#BFB6A2', floor: '#8A7F72', aisle: '#B3A898', wood: '#6E4B2E', woodShade: '#4E341F' },
  berlinInt: { hallDark: '#2B2622', ceil: '#E9DFC8', ceilShade: '#C7BA9C', ceilLit: '#F5EDDA', terrace: '#C9A15C', terraceShade: '#A9834A', seat: '#5E4A30', stage: '#D9B27A', stageLit: '#F0D6A6', stageShade: '#8E6E36', cloud: '#F7F2E6', cloudShade: '#BDB39C', organCase: '#8E6E36', pipe: '#D7DCE2', pipeShade: '#9AA3AD' },
  amsterdamInt: { ...RED_HALL, ceil: '#F4EFE3', ceilShade: '#DED6C3', back: '#E6DDC9', wallL: '#EFE8D8', wallR: '#CCC3AE', floor: '#5A1E2A', rail: '#F8F4EA', railShade: '#CFC6B1', organCase: '#6B3F2A', pipe: '#E9C98D', pipeShade: '#B08A45' },
  parisInt: { hallDark: '#4A1622', ceil: '#E9C98D', ceilShade: '#C9A15C', tier: '#B8863B', tierShade: '#9A6E2C', rail: '#E2B74E', railLit: '#F3D98C', box: '#3A1220', seat: '#9B2C3E', seatDark: '#5A1E2A', curtain: '#7A2030', wood: '#A9713F' },
  londonInt: { hallDark: '#3A1820', ceil: '#C9CFD6', ceilShade: '#9FA8B2', tier: '#D9C7A6', tierShade: '#BCA780', rail: '#E2B74E', railLit: '#F3D98C', box: '#4A1E28', seat: '#A8323F', seatDark: '#5A1E2A', mushroom: '#F2EFE6', mushroomShade: '#9A9488', organCase: '#5E4630', pipe: '#D7DCE2', pipeShade: '#9AA3AD', wood: '#A9713F' },
  spbInt: { ...RED_HALL, ceil: '#F7F4EC', ceilShade: '#E0DBCF', back: '#EDE8DC', wallL: '#F4F0E6', wallR: '#D2CCBE', floor: '#5A1E2A', rail: '#F8F4EA', railShade: '#CFC6B1', column: '#FFFFFF', columnShade: '#D2CCBE', organCase: '#6B4A30', pipe: '#D7DCE2', pipeShade: '#9AA3AD' },
  moscowInt: { ceil: '#F3EEE0', ceilShade: '#DCD4C0', back: '#E4DAC2', wallL: '#EFE7D2', wallR: '#CBC1A8', floor: '#6E5A3E', rail: '#F8F4EA', railShade: '#CFC6B1', trim: '#F8F4EA', portrait: '#4E4438', organCase: '#5E4630', pipe: '#D7DCE2', pipeShade: '#9AA3AD', seat: '#A98B5A', seatDark: '#7A6340', wood: '#A9713F' },
  newyorkInt: { hallDark: '#3A2A24', ceil: '#F5F0E4', ceilShade: '#DDD5C2', tier: '#F3EEE2', tierShade: '#DAD2BF', rail: '#E2B74E', railLit: '#F3D98C', box: '#4A2A24', seat: '#A8323F', seatDark: '#5A1E2A', wood: '#A9713F' },
  buenosairesInt: { hallDark: '#3F1A22', ceil: '#E6D6B8', ceilShade: '#C6B48F', tier: '#E9DDC4', tierShade: '#CBBE9F', rail: '#E2B74E', railLit: '#F3D98C', box: '#4A1E2A', seat: '#B0485A', seatDark: '#6A2636', curtain: '#8A2A3A', wood: '#A9713F' },
  tokyoInt: { hallDark: '#2A2018', ceil: '#E8D3AE', ceilShade: '#C5AC80', ceilLit: '#F4E3C2', terrace: '#B98A54', terraceShade: '#976C3C', seat: '#7A2E36', stage: '#E2C08A', stageLit: '#F5DDB0', stageShade: '#8E6A3C', organCase: '#8E6A3C', pipe: '#E1E5EA', pipeShade: '#9AA3AD' },
  sydneyInt: { ceil: '#EAD9B8', ceilShade: '#C9B48A', back: '#D9C49A', wallL: '#E2CFA6', wallR: '#BFA97C', floor: '#3A2A36', rail: '#F2E6CC', railShade: '#C9B48A', organCase: '#A98458', pipe: '#E1E5EA', pipeShade: '#9AA3AD', petal: '#C2307E', petalLit: '#E66AAE', seat: '#8C2A6E', seatDark: '#5A1A48', wood: '#C99A5E' },
};
Object.assign(EXTRA_LOCPAL, INT_LOCPAL);

const CHAGALL = ['#D9574A', '#E2B74E', '#5FA36A', '#4C7FD0', '#F2EFE6', '#D9574A', '#4C7FD0'];
const SOLDI = ['#C9A27A', '#9DB3C9', '#D9B8A0', '#8FA9B8', '#C9A27A', '#B7C4CF'];
const inside = (key, fn) => { EXTRA_SCENES[key] = { fn, loc: key, aerial: false }; };
inside('salzburgInt', () => HALL({ organ: true, chandeliers: 3, gallery: true }));          // the Great Hall of the Mozarteum: white and gold, an organ
inside('pragueInt', () => HALL({ organ: true, columns: true, chandeliers: 2 }));             // the Dvořák Hall: a colonnade round the hall
inside('leipzigInt', () => NAVE());                                                          // St Thomas: the nave Bach worked in
inside('berlinInt', () => VINEYARD({ clouds: true, organ: 'side' }));                        // the first vineyard, with its hanging «clouds»
inside('amsterdamInt', () => HALL({ organ: 'crown', chandeliers: 4, gallery: true, aisle: true })); // the Grote Zaal: the great organ over the stage
inside('parisInt', () => HORSESHOE({ tiers: 5, painted: CHAGALL }));                         // red and gold under Chagall's ceiling
inside('londonInt', () => HORSESHOE({ tiers: 4, mushrooms: true, open: true, curtain: false }));  // the round hall from the stage, the acoustic «mushrooms» under the dome
inside('spbInt', () => HALL({ columns: true, chandeliers: 4, crystal: true, organ: true })); // white columns and eight crystal chandeliers
inside('moscowInt', () => HALL({ medallions: true, organ: true, chandeliers: 2, windows: false })); // portraits of composers along the walls
inside('newyorkInt', () => HORSESHOE({ tiers: 4, open: true, curtain: false }));             // white and gold balconies, no curtain: a concert hall
inside('buenosairesInt', () => HORSESHOE({ tiers: 6, painted: SOLDI }));                     // six tiers and Soldi's painted dome
inside('tokyoInt', () => VINEYARD({ organ: 'centre' }));                                     // a vineyard of warm wood, the organ in the middle
inside('sydneyInt', () => HALL({ ribs: true, petals: true, organ: true, chandeliers: 0 }));  // birch ribs, the magenta petals, the grand organ
