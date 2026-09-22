// The review sheet of the third iteration of the home (spec 3.29): every thing of the catalogue before and
// after, alive the way the app moves it (a port of SceneMotion.kt), alone as it stands on a shelf and in its
// place in the room; the room whole; the shelves; the icons. Runs from file:// — open shop-review.html.
(function () {
  const stub = { createElement: () => null };
  const B = window.buildBefore(stub);
  const A = window.buildHomeCatalog(stub);
  const $ = sel => document.querySelector(sel);
  const params = new URLSearchParams(location.search);
  let mode = params.get('mode') || 'eve';
  let house = params.get('house') || 'rent';
  const still = params.has('still');

  // ---------------------------------------------------------------- motion (SceneMotion.kt)
  const TAU = Math.PI * 2;
  const wave = (t, period, phase) => 0.5 + 0.5 * Math.sin(TAU * (t + phase) / period);
  const hash = (index, salt) => { let h = (Math.imul(index, 374761393) + Math.imul(salt, 668265263)) | 0; h = Math.imul(h ^ (h >>> 13), 1274126177); return ((h ^ (h >>> 16)) & 0xFFFF) / 65535; };
  const parseAnim = text => {
    if (!text) return null;
    const a = {};
    for (const part of String(text).split('+')) {
      const f = part.split(':'); const n = f.slice(1).map(Number);
      switch (f[0]) {
        case 'ride': a.ride = { speed: n[0], from: n[1], to: n[2] }; break;
        case 'bob': a.bob = { amp: n[0], period: n[1], delay: n[2] || 0 }; break;
        case 'fly': a.fly = { amp: n[0], period: n[1] }; break;
        case 'dash': break;
        case 'bird': a.bird = { left: n[0], span: n[1] }; break;
        case 'flash': a.flash = n[0]; break;
        case 'blink': a.blink = n[0]; break;
        case 'flick': a.flick = { period: n[0], delay: n[1] || 0 }; break;
        case 'rise': a.rise = { period: n[0], delay: n[1] || 0, distance: n[2] }; break;
        case 'sway': a.sway = { amp: n[0], period: n[1], delay: n[2] || 0 }; break;
        case 'swing': a.swing = { deg: n[0], period: n[1], px: n[2] || 0, py: n[3] || 0 }; break;
        case 'fall': a.fall = { speed: n[0], top: n[1], height: n[2] }; break;
        case 'glint': a.glint = n[0]; break;
        case 'peck': a.peck = { deg: n[1], period: n[0], px: n[2], py: n[3] }; break;
        default: console.warn('unknown movement', f[0]);
      }
    }
    return a;
  };
  const moved = (a, baseX, baseY, index, t) => {
    let dx = 0, dy = 0, flap = 1, alpha = 1, deg = 0, px = 0, py = 0;
    if (a.ride) { const span = a.ride.to - a.ride.from; if (span > 0) dx += a.ride.from + (((-a.ride.from + t * a.ride.speed) % span) + span) % span; }
    if (a.bob) dy += a.bob.amp * Math.sin(TAU * (t + a.bob.delay) / a.bob.period);
    if (a.fly) dx += a.fly.amp * ((t % a.fly.period) / a.fly.period);
    if (a.bird) {
      const speed = 8 + hash(index, 11) * 7; const w = a.bird;
      const along = (((baseX - w.left) + t * speed) % w.span + w.span) % w.span;
      dx += w.left + along - baseX; dy += 3 * Math.sin(TAU * t / (3 + hash(index, 12) * 2) + index);
      flap = 0.35 + 0.65 * wave(t, 1 / 2.6, hash(index, 13)); alpha *= Math.min(1, Math.max(0, Math.min(along, w.span - along) / 12));
    }
    if (a.flash && (t % a.flash) / a.flash > 0.35) alpha = 0;
    if (a.blink) { const at = ((t + (index % 4) * 0.7) % a.blink) / a.blink; if (at >= 0.92 && at <= 0.97) alpha = 0; }
    if (a.flick) alpha *= 1 - 0.45 * wave(t + a.flick.delay, a.flick.period, 0);
    if (a.rise) {
      const at = (((t + a.rise.delay) % a.rise.period) + a.rise.period) % a.rise.period / a.rise.period;
      if (a.rise.distance == null) { dy -= 10 * at; alpha *= 0.7 * (1 - at); } else { dy -= a.rise.distance * at; alpha *= 0.8 * (at < 0.2 ? at / 0.2 : (1 - at) / 0.8); }
    }
    if (a.glint) { const at = (t % a.glint) / a.glint; const spark = at < 0.92 ? 0 : at < 0.96 ? (at - 0.92) / 0.04 : (1 - at) / 0.04; alpha *= 0.55 + 0.45 * spark; }
    if (a.sway) dx += a.sway.amp * Math.sin(TAU * (t + a.sway.delay) / a.sway.period);
    if (a.swing) { deg = a.swing.deg * Math.sin(TAU * t / a.swing.period); px = a.swing.px; py = a.swing.py; }
    if (a.peck) {
      const at = (t % a.peck.period) / a.peck.period; const e = x => x * x * (3 - 2 * x);
      deg += a.peck.deg * (at < 0.58 || at >= 0.74 ? 0 : at < 0.66 ? e((at - 0.58) / 0.08) : e((0.74 - at) / 0.08));
      dx += 4 * (at < 0.74 ? 0 : at < 0.82 ? e((at - 0.74) / 0.08) : e((1 - at) / 0.18)); px = a.peck.px; py = a.peck.py;
    }
    return { dx, dy, flap, alpha, deg, px, py };
  };
  const WINDOWS = new Set(['window', 'windowLit', 'chandelier']);
  const evening = (l, index, t) => {
    if (mode !== 'eve') return 1;
    if (WINDOWS.has(l.fill)) return 1 - 0.32 * wave(t, 4 + index % 3, (index % 5) * 0.6);
    if (l.fill === 'GLOW' || l.glow) return 1 - 0.4 * wave(t, 3.5, index % 4 * 0.9);
    return 1;
  };

  // ---------------------------------------------------------------- drawing
  const NS = 'http://www.w3.org/2000/svg';
  const living = [];
  let uid = 0;
  const colourOf = (palette, t) => (t == null ? 'none' : t.startsWith('#') || t.startsWith('rgba') ? t : palette[t] ?? (t === 'none' ? 'none' : 'magenta'));
  const stop = (o, c) => { const m = String(c).match(/rgba\((\d+),(\d+),(\d+),([\d.]+)\)/); return m ? `<stop offset="${o}" stop-color="rgb(${m[1]},${m[2]},${m[3]})" stop-opacity="${m[4]}"/>` : `<stop offset="${o}" stop-color="${c}"/>`; };
  /** An SVG of [layers] seen through [box] (x, y, w, h in units of the grid), [w] × [h] pixels; alive unless [still]. */
  const picture = (layers, palette, box, w, h, alive = true) => {
    const id = `p${uid++}`;
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('width', w); svg.setAttribute('height', h);
    svg.setAttribute('viewBox', box.map(v => +v.toFixed(2)).join(' '));
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
    svg.innerHTML = `<defs><linearGradient id="s${id}" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="190">${stop(0, palette.sky)}${stop(1, palette.skyLow)}</linearGradient><radialGradient id="r${id}">${stop(0, palette.glow)}${stop(1, 'rgba(255,196,110,0)')}</radialGradient></defs>`;
    layers.forEach((l, index) => {
      const p = document.createElementNS(NS, 'path');
      p.setAttribute('d', l.d);
      p.setAttribute('fill', l.fillNone ? 'none' : l.fill === 'SKY' ? `url(#s${id})` : l.fill === 'GLOW' ? `url(#r${id})` : colourOf(palette, l.fill));
      if (l.stroke) { p.setAttribute('stroke', colourOf(palette, l.stroke)); p.setAttribute('stroke-width', l.sw); p.setAttribute('stroke-linecap', 'round'); p.setAttribute('stroke-linejoin', 'round'); }
      if (l.dash) p.setAttribute('stroke-dasharray', l.dash);
      const own = l.op === undefined ? 1 : l.op;
      p.setAttribute('opacity', own);
      let node = p;
      if (l.tx || l.ty || (l.sc && l.sc !== 1)) { const g = document.createElementNS(NS, 'g'); g.setAttribute('transform', `translate(${l.tx || 0} ${l.ty || 0}) scale(${l.sc || 1})`); g.appendChild(p); node = g; }
      const g = document.createElementNS(NS, 'g'); g.appendChild(node); svg.appendChild(g);
      const anim = parseAnim(l.anim);
      if (alive && !still && (anim || (mode === 'eve' && (WINDOWS.has(l.fill) || l.fill === 'GLOW')))) living.push({ l, index, anim, g, p, own });
    });
    return svg;
  };
  const tick = now => {
    const t = now / 1000;
    for (const x of living) {
      let alpha = x.own * evening(x.l, x.index, t);
      if (x.anim) {
        if (!x.box) { try { const b = x.p.getBBox(); x.box = [b.x + b.width / 2, b.y + b.height / 2]; } catch (e) { x.box = [0, 0]; } }
        const m = moved(x.anim, x.box[0], x.box[1], x.index, t);
        alpha *= m.alpha;
        let tr = `translate(${m.dx} ${m.dy})`;
        if (m.flap !== 1) tr += ` translate(${x.box[0]} ${x.box[1]}) scale(1 ${m.flap}) translate(${-x.box[0]} ${-x.box[1]})`;
        else if (m.deg) tr += ` rotate(${m.deg} ${m.px} ${m.py})`;
        x.g.setAttribute('transform', tr);
      }
      x.p.setAttribute('opacity', Math.max(0, alpha));
    }
    requestAnimationFrame(tick);
  };

  // ---------------------------------------------------------------- what is compared
  const eve = () => mode === 'eve';
  const shadow = l => typeof l.fill === 'string' && l.fill.startsWith('rgba(20,16,30');
  const itemLayers = (C, it) => {
    if (!it || !it.L) return null;
    const p = C.pos(house, it);
    return [...(it.L0 ? it.L0(p.x, p.y, eve()) : []), ...it.L(p.x, p.y, eve())];
  };
  const START = C => C.ITEMS.filter(i => i.p === 0 && i.id !== 'vln_student').map(i => i.id);
  /** The room with what it came with and [id] in its place; a pet or a thing on a chair gets what it needs around it. */
  const roomWith = (C, id) => {
    const it = C.BYID[id];
    const owned = START(C).filter(s => C.BYID[s].s !== it.s);
    return C.compose(house, mode, [...owned, id], { outside: !!it.out });
  };
  const fitted = (C, layers, w, h, alive) => {
    const shown = layers.filter(l => !shadow(l));
    const b = C.bboxOf(shown);
    const m = Math.max(b[2] - b[0], b[3] - b[1]) * 0.1 + 2;
    return picture(shown, C.palOf(mode, {}), [b[0] - m, b[1] - m, b[2] - b[0] + 2 * m, b[3] - b[1] + 2 * m], w, h, alive);
  };
  const sameArt = (id) => {
    const a = A.BYID[id], b = B.BYID[id];
    if (!b) return false;
    const la = itemLayers(A, a), lb = itemLayers(B, b);
    return JSON.stringify(la) === JSON.stringify(lb) && a.at === b.at && JSON.stringify(a.over) === JSON.stringify(b.over)
      && String(a.pat && a.pat(eve()).map(l => l.d).join()) === String(b.pat && b.pat(eve()).map(l => l.d).join()) && a.n === b.n && a.d === b.d;
  };
  const cell = (it, withBefore) => {
    const div = document.createElement('div'); div.className = 'cell';
    const head = document.createElement('div'); head.className = 'head';
    const anims = [...new Set((itemLayers(A, it) || []).map(l => l.anim).filter(Boolean).map(a => String(a).split(':')[0]))];
    head.innerHTML = `<b>${it.n}</b> <span class="id">${it.id} · ${it.p} · ${it.s}${it.at ? ' → ' + it.at : ''}</span>${anims.length ? ` <i>${anims.join(', ')}</i>` : ''}<div class="note">${it.d || ''}</div>`;
    div.appendChild(head);
    const row = document.createElement('div'); row.className = 'row';
    const add = (node, label) => { const f = document.createElement('figure'); f.appendChild(node); const c = document.createElement('figcaption'); c.textContent = label; f.appendChild(c); row.appendChild(f); };
    const la = itemLayers(A, it);
    if (withBefore) {
      const lb = itemLayers(B, B.BYID[it.id]);
      add(lb ? fitted(B, lb, 150, 130, false) : swatch(B, B.BYID[it.id], 150, 130), 'было');
    }
    add(la ? fitted(A, la, 150, 130, true) : swatch(A, it, 150, 130), 'стало');
    if (C_in(it)) {
      const r = roomWith(A, it.id);
      let box;
      if (la) {
        const b = A.bboxOf(la.filter(l => !shadow(l)));
        const cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
        const span = Math.max(b[2] - b[0], (b[3] - b[1]) * 1.25, 70) * 1.7;
        box = [cx - span / 2, cy - span * 0.4, span, span * 0.8];
      } else box = [0, -120, 412, 380];
      add(picture(r.l, A.palOf(mode, r.over), box, 210, 168, true), 'в комнате');
    }
    div.appendChild(row);
    return div;
  };
  const C_in = it => A.slotIn(it.s, house) && (!it.at || A.slotIn(it.at, house));
  /** Walls and floors are colours with a pattern: a sample of the wall or the floor, as the shop will show it. */
  const swatch = (C, it, w, h) => {
    const over = { ...(it.over || {}) };
    const floor = it.s === 'floor';
    const layers = floor
      ? [C.L('floorHome', 'M0 200h412v400H0Z'), ...(it.pat ? it.pat(eve()) : [])]
      : [C.L('wallHome', 'M0 -120h412v320H0Z'), C.L('wallLitHome', 'M0 -120h200v320H0Z'), ...(it.pat ? it.pat(eve()) : [])];
    return picture(layers, C.palOf(mode, over), floor ? [120, 200, 172, 150] : [120, -60, 172, 150], w, h, false);
  };

  // ---------------------------------------------------------------- the page
  const render = () => {
    living.length = 0;
    document.querySelectorAll('.toggle button').forEach(b => b.classList.toggle('on', b.dataset.mode === mode || b.dataset.house === house));
    const changed = A.ITEMS.filter(it => !sameArt(it.id));
    const $changed = $('#changed'); $changed.innerHTML = '';
    $('#changed-count').textContent = `${changed.length} из ${A.ITEMS.length}`;
    changed.forEach(it => $changed.appendChild(cell(it, true)));
    const $all = $('#all'); $all.innerHTML = '';
    A.ITEMS.forEach(it => $all.appendChild(cell(it, false)));
    // the room whole, furnished the way a player a month in might have it
    const $rooms = $('#rooms'); $rooms.innerHTML = '';
    const sets = [
      ['как досталась', START(A)],
      ['через месяц', [...START(A), 'vln_master', 'case_velvet', 'stand_wood', 'metronome', 'tea', 'portrait', 'clock', 'armchair', 'plaid', 'rug_persian', 'monstera', 'cat_ginger', 'window_arched', 'view_mount', 'garland', 'candles', 'wp_green']],
      ['через полгода', [...START(A), 'vln_cremona', 'case_leather', 'stand_candle', 'gramophone', 'cello', 'globe', 'guitar', 'bureau', 'rocking', 'plaid', 'rug_round', 'chandelier', 'window_stained', 'view_sea', 'wp_damask', 'floor_herring', 'aquarium', 'passport', 'curtain_velvet']],
    ];
    for (const [label, owned] of sets) {
      const r = A.compose(house, mode, owned, {});
      const f = document.createElement('figure');
      f.appendChild(picture(r.l, A.palOf(mode, r.over), [0, -140, 412, 460], 412, 460, true));
      const c = document.createElement('figcaption'); c.textContent = label; f.appendChild(c);
      $rooms.appendChild(f);
    }
    const out = A.compose(house, mode, [...START(A), 'mailbox', 'bike', 'swing', 'car_old', 'vane', 'cat_ginger'], { outside: true });
    const f = document.createElement('figure');
    f.appendChild(picture(out.l, A.palOf(mode, out.over), [0, -60, 412, 380], 412, 380, true));
    const c = document.createElement('figcaption'); c.textContent = 'снаружи'; f.appendChild(c);
    $rooms.appendChild(f);
    shelves();
  };

  // ---------------------------------------------------------------- the shelves (the app's ShopScreen, 3.29)
  const GROUPS = [['instr', 'Лавка мастера', 'wood'], ['music', 'Ноты и музыка', 'wood'], ['room', 'Стены, пол, окно', 'cloth'], ['light', 'Рынок · свет', 'cloth'], ['furn', 'Рынок · мебель', 'cloth'], ['plant', 'Цветочный ряд', 'cloth'], ['life', 'Мелочи жизни', 'cloth'], ['pet', 'Питомник', 'pet'], ['out', 'Для двора', 'cloth']];
  const shelves = () => {
    const $s = $('#shelves'); $s.innerHTML = '';
    const start = new Set(START(A));
    for (const [g, name, kind] of GROUPS) {
      const things = A.ITEMS.filter(it => it.g === g && !start.has(it.id));
      const shelf = document.createElement('div'); shelf.className = `shelf ${kind}`;
      shelf.innerHTML = `<div class="shelf-name">${name}</div>`;
      for (let i = 0; i < things.length; i += 3) {
        const row = document.createElement('div'); row.className = 'shelf-row';
        things.slice(i, i + 3).forEach(it => {
          const tile = document.createElement('div'); tile.className = 'tile';
          const pic = document.createElement('div'); pic.className = 'pic';
          const home = A.slotIn(it.s, 'rent') ? 'rent' : 'wood';
          const saved = house; house = home;
          const la = itemLayers(A, it);
          pic.appendChild(vignette(it) || (la ? thumb(la, it) : swatch(A, it, 100, 76)));
          house = saved;
          if (it.from) { const pill = document.createElement('span'); pill.className = 'pill'; pill.textContent = it.from; pic.appendChild(pill); }
          tile.appendChild(pic);
          tile.insertAdjacentHTML('beforeend', `<div class="name">${it.n}</div><div class="price">♪ ${it.p}</div>`);
          row.appendChild(tile);
        });
        shelf.appendChild(row);
      }
      $s.appendChild(shelf);
    }
  };
  /**
   * What is a colour or a part of the window is shown in its setting (the app's ItemThumbs): a wall or a floor — a corner of
   * the room with it; a window, a view, curtains — the window whole with the room's own other two, this one in its place.
   */
  const WINDOW_PARTS = { window: 'window_simple', view: 'view_city', curtain: 'curtain_plum' };
  const vignette = it => {
    if (it.s === 'wallpaper' || it.s === 'floor') {
      const r = A.compose('rent', 'eve', [it.id], {});
      return picture(r.l, A.palOf('eve', r.over), it.s === 'floor' ? [96, 170, 220, 167] : [96, 70, 220, 167], 100, 76, false);
    }
    if (WINDOW_PARTS[it.s]) {
      const owned = Object.entries(WINDOW_PARTS).map(([slot, id]) => (slot === it.s ? it.id : id));
      const r = A.compose('rent', 'eve', owned, {});
      return picture(r.l, A.palOf('eve', r.over), [298 - 92, 91 - 76, 184, 140], 100, 76, false);
    }
    return null;
  };
  /** A thing on a shelf: fitted to 100 × 76, standing on the board; a thing given its own framing (`tb`) is framed by it. */
  const thumb = (layers, it) => {
    const shown = layers.filter(l => !shadow(l));
    let b = A.bboxOf(shown);
    if (it.tb) { const p = A.pos(house, it); b = [p.x + it.tb[0], p.y + it.tb[1], p.x + it.tb[2], p.y + it.tb[3]]; }
    const w = b[2] - b[0], h = b[3] - b[1];
    const k = Math.min(100 * 0.8 / w, 72 * 0.9 / h, 3.2);
    const vw = 100 / k, vh = 76 / k;
    // standing on the board: the bottom of the thing on the bottom of the picture
    return picture(shown, A.palOf('eve', it.over || {}), [(b[0] + b[2]) / 2 - vw / 2, b[3] + 2 / k - vh, vw, vh], 100, 76, false);
  };

  // ?room=id,id — the room with what it came with and those things, whole: for things that must be seen together
  const together = params.get('room');
  if (together) {
    document.body.innerHTML = '<div id="room" style="padding:12px"></div>';
    const r = A.compose(house, mode, [...START(A).filter(s => !together.split(',').some(id => A.BYID[id].s === A.BYID[s].s)), ...together.split(',')], {});
    document.getElementById('room').appendChild(picture(r.l, A.palOf(mode, r.over), [0, -140, 412, 400], 824, 800, !still));
    requestAnimationFrame(tick);
    return;
  }
  // ?focus=id,id — those things large, before and after, alone and in the room: for drawing
  const focus = params.get('focus');
  if (focus) {
    document.body.innerHTML = '<div id="focus" style="display:flex;flex-wrap:wrap;gap:12px;padding:12px"></div>';
    const big = +(params.get('size') || 300);
    for (const id of focus.split(',')) {
      const it = A.BYID[id];
      const box = document.createElement('div'); box.className = 'cell';
      box.innerHTML = '<div class="head"><b>' + it.n + '</b> <span class="id">' + id + '</span></div>';
      const row = document.createElement('div'); row.className = 'row';
      const lb = B.BYID[id] && itemLayers(B, B.BYID[id]);
      const la = itemLayers(A, it);
      if (!params.has('nobefore')) row.appendChild(lb ? fitted(B, lb, big, big, false) : swatch(B, B.BYID[id], big, big));
      row.appendChild(la ? fitted(A, la, big, big, true) : swatch(A, it, big, big));
      if (C_in(it)) {
        const r = roomWith(A, id);
        let rb = [0, -120, 412, 380];
        if (la) { const b = A.bboxOf(la.filter(l => !shadow(l))); const cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2; const span = Math.max(b[2] - b[0], (b[3] - b[1]) * 1.25, 70) * 1.7; rb = [cx - span / 2, cy - span * 0.45, span, span * 0.9]; }
        row.appendChild(picture(r.l, A.palOf(mode, r.over), rb, big * 1.3, big * 1.17, true));
      }
      box.appendChild(row);
      document.getElementById('focus').appendChild(box);
    }
    requestAnimationFrame(tick);
    return;
  }
  document.querySelectorAll('.toggle button').forEach(b => b.addEventListener('click', () => { if (b.dataset.mode) mode = b.dataset.mode; if (b.dataset.house) house = b.dataset.house; render(); }));
  render();
  requestAnimationFrame(tick);
})();
