// A contact sheet of the stops the way their full screen opens them (spec 3.23): every view — outside
// and inside, evening and day — on a tall phone, a small one and lying down, framed as SceneFrame in
// SceneMotion.kt frames it, and beside them the scene's whole frame. Where nothing is drawn shows
// magenta, and the page counts it.
//
//   node tools/journey/export.js <dir>     the scenes into the app and their SVGs into <dir>
//   node tools/journey/sheet.js <dir>      <dir>/sheet.html — open it in a browser
//   node tools/journey/sheet.js <dir> -420,600   the same with a frame tried on every scene instead of its own:
//                                                 what is still missing for the whole screen
//
// The count without a browser window:
//   "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --virtual-time-budget=60000 \
//     --dump-dom file://<dir>/sheet.html | grep -o 'id="report">[^<]*'
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const dir = process.argv[2];
if (!dir) throw new Error('usage: node tools/journey/sheet.js <dir with the SVGs of export.js>');

// the views in the order of the route, from the route itself; home is drawn by the home, not here
const route = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/violintuner/core/domain/journey/Journey.kt'), 'utf8');
const views = [...route.matchAll(/StopView\("(\w+)", inside = (true|false)\)/g)].map(m => m[1]).filter(key => key !== 'home');

const tried = process.argv[3] ? process.argv[3].split(',').map(Number) : null;
const frameOf = key => {
  if (tried) return tried;
  const header = fs.readFileSync(path.join(root, `app/src/main/assets/journey/${key}.eve.scene`), 'utf8').split('\n')[1];
  const found = /frame=(-?[\d.]+),(-?[\d.]+)/.exec(header);
  return found ? [+found[1], +found[2]] : [0, 260];
};

// SceneFrame.openZoom and originY, in units of the grid: what the box shows when the full screen opens
const GRID_W = 412, GRID_H = 260;
const opened = ([top, bottom], w, h) => {
  const cover = Math.max(w / GRID_W, h / GRID_H);
  const k = Math.max(w / GRID_W, h / (bottom - top));
  const middle = h / 2 - GRID_H / 2 * k;
  const lowest = -top * k, highest = h - bottom * k;
  const origin = highest <= lowest ? Math.min(Math.max(middle, highest), lowest) : (lowest + highest) / 2;
  return { zoom: k / cover, box: [GRID_W / 2 - w / (2 * k), -origin / k, w / k, h / k] };
};
const SCREENS = [['телефон', 412, 915], ['маленький', 360, 640], ['лёжа', 915, 412]];
const SCALE = 0.42;

let n = 0;
const cells = [];
for (const key of views) {
  const frame = frameOf(key);
  for (const mode of ['eve', 'day']) {
    const file = path.join(dir, `${key}.${mode}.svg`);
    if (!fs.existsSync(file)) continue;
    // the gradients of every picture get their own ids: on one page the first «s» would paint every sky
    const id = `p${n++}`;
    const body = fs.readFileSync(file, 'utf8').replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')
      .replace(/id="(\w)"/g, `id="$1${id}"`).replace(/url\(#(\w)\)/g, `url(#$1${id})`);
    const svg = (box, w, h, label) => `<figure><svg class="v" data-name="${key} · ${mode} · ${label}" viewBox="${box.map(v => v.toFixed(1)).join(' ')}" width="${Math.round(w * SCALE)}" height="${Math.round(h * SCALE)}" preserveAspectRatio="none">${body}</svg><figcaption>${label}</figcaption></figure>`;
    const row = [svg([0, frame[0], GRID_W, frame[1] - frame[0]], GRID_W, frame[1] - frame[0], `кадр ${frame[0]}…${frame[1]}`)];
    for (const [label, w, h] of SCREENS) {
      const view = opened(frame, w, h);
      row.push(svg(view.box, w, h, `${label} · ×${view.zoom.toFixed(2)}`));
    }
    cells.push(`<section><h2>${key} · ${mode === 'eve' ? 'вечер' : 'день'}</h2><div class="row">${row.join('')}</div></section>`);
  }
}

// every picture drawn into a canvas and its magenta counted: the part of the box nothing covers
const count = `
const found = [];
const all = [...document.querySelectorAll('svg.v')];
Promise.all(all.map(svg => new Promise(done => {
  const w = +svg.getAttribute('width'), h = +svg.getAttribute('height');
  const image = new Image();
  image.onload = () => {
    const canvas = document.createElement('canvas'); canvas.width = w; canvas.height = h;
    const g = canvas.getContext('2d'); g.fillStyle = '#FF00FF'; g.fillRect(0, 0, w, h); g.drawImage(image, 0, 0, w, h);
    const px = g.getImageData(0, 0, w, h).data; let hole = 0;
    for (let i = 0; i < px.length; i += 4) if (px[i] > 235 && px[i + 1] < 25 && px[i + 2] > 235) hole++;
    const share = 100 * hole / (w * h);
    const caption = svg.parentNode.querySelector('figcaption');
    caption.textContent += share > 0.2 ? ' · пустоты ' + share.toFixed(1) + ' %' : ' · без пустот';
    if (share > 0.2) { caption.className = 'hole'; found.push(svg.dataset.name + ' ' + share.toFixed(1) + '%'); }
    done();
  };
  image.onerror = () => done();
  image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(svg));
}))).then(() => {
  document.getElementById('report').textContent = found.length ? 'пустоты: ' + found.length + ' — ' + found.join('; ') : 'пустот нет';
  document.title = 'done';
});`;

const html = `<!doctype html><meta charset="utf-8"><title>…</title><style>
body{margin:0;padding:16px;background:#0b0b10;color:#ddd;font:13px -apple-system,sans-serif}
h1{font-size:16px;margin:0 0 4px}h2{font-size:13px;font-weight:600;margin:14px 0 6px}
.row{display:flex;gap:10px;align-items:flex-start}figure{margin:0}svg{display:block;background:#FF00FF}
figcaption{margin-top:3px;color:#a39fb5}figcaption.hole{color:#ff6b9a}#report{margin:6px 0 10px;color:#ffd27a}</style>
<h1>Локации во весь экран</h1><div>Слева — весь кадр сцены, дальше — как открывается полный экран. Пурпур — там, где ничего не нарисовано.</div>
<div id="report">…</div>${cells.join('')}<script>${count}</script>`;
fs.writeFileSync(path.join(dir, 'sheet.html'), html);
console.log(`${cells.length} views → ${path.join(dir, 'sheet.html')}`);
