#!/usr/bin/env node
// Renders the promo video: promo.html, captured frame by frame by headless Chrome over the DevTools protocol, then
// encoded with the music by ffmpeg.
//
//   node tools/store/promo/render.js <lang> <frames dir> <music.wav> <out.mp4> [--only 4.5,6.2]
//
// <frames dir> holds live/ practice/ journey/ home/ analysis/ (0001.jpg …, 760 wide, the Dynamic Island painted over) and
// live-zones.json — docs/store/listing.md, «Видео», says how they are made. --only renders those moments as stills
// (<out>-<t>.jpg) to look at, without the video.
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn, execFileSync } = require('child_process');

const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const ROOT = path.resolve(__dirname, '..', '..', '..');
const PORT = 9333;
const FPS = 30;
const SECONDS = 15;

const [lang, framesDir, music, outFile] = process.argv.slice(2);
const onlyArg = process.argv.indexOf('--only');
const only = onlyArg > 0 ? process.argv[onlyArg + 1].split(',').map(Number) : null;

const sleep = ms => new Promise(r => setTimeout(r, ms));

async function main() {
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'promo-chrome-'));
  const chrome = spawn(CHROME, ['--headless=new', `--remote-debugging-port=${PORT}`, '--hide-scrollbars', '--allow-file-access-from-files',
    '--force-device-scale-factor=1', '--window-size=1080,1920', `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });
  try {
    let targets;
    for (let i = 0; i < 100 && !targets; i++) {
      try { targets = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json(); } catch { await sleep(100); }
    }
    const page = targets.find(t => t.type === 'page');
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    await new Promise(r => ws.addEventListener('open', r));
    let id = 0;
    const waiting = new Map();
    ws.addEventListener('message', e => {
      const m = JSON.parse(e.data);
      if (m.id && waiting.has(m.id)) { waiting.get(m.id)(m); waiting.delete(m.id); }
    });
    const send = (method, params = {}) => new Promise((resolve, reject) => {
      const n = ++id;
      waiting.set(n, m => (m.error ? reject(new Error(`${method}: ${m.error.message}`)) : resolve(m.result)));
      ws.send(JSON.stringify({ id: n, method, params }));
    });
    const evaluate = async expression => {
      const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
      if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
      return r.result.value;
    };

    await send('Emulation.setDeviceMetricsOverride', { width: 1080, height: 1920, deviceScaleFactor: 1, mobile: false });
    const icon = 'file://' + path.join(ROOT, 'docs/store/icon-512.png');
    const url = 'file://' + path.join(__dirname, 'promo.html') +
      `?lang=${lang}&frames=${encodeURIComponent('file://' + path.resolve(framesDir))}&icon=${encodeURIComponent(icon)}`;
    await send('Page.navigate', { url });
    for (let i = 0; i < 100; i++) {
      if (await evaluate('document.readyState === "complete" && !!window.render')) break;
      await sleep(100);
    }
    const zones = fs.readFileSync(path.join(framesDir, 'live-zones.json'), 'utf8');
    await evaluate(`window.ZONES = ${zones}; window.ready`);

    const shot = async (t, file) => {
      await evaluate(`render(${t})`);
      const { data } = await send('Page.captureScreenshot', { format: 'jpeg', quality: 95 });
      fs.writeFileSync(file, Buffer.from(data, 'base64'));
    };

    if (only) {
      for (const t of only) await shot(t, `${outFile.replace(/\.mp4$/, '')}-${t}.jpg`);
      return;
    }
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'promo-frames-'));
    const total = FPS * SECONDS;
    for (let f = 0; f < total; f++) {
      await shot(f / FPS, path.join(tmp, `${String(f).padStart(4, '0')}.jpg`));
      if (f % 75 === 0) process.stdout.write(`${f}/${total}\n`);
    }
    const ffmpeg = execFileSync('python3', ['-c', 'import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())']).toString().trim();
    execFileSync(ffmpeg, ['-y', '-loglevel', 'error', '-framerate', String(FPS), '-i', path.join(tmp, '%04d.jpg'), '-i', music,
      '-vf', 'scale=out_range=tv:out_color_matrix=bt709,format=yuv420p', '-colorspace', 'bt709', '-color_primaries', 'bt709',
      '-color_trc', 'bt709', '-color_range', 'tv', '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-profile:v', 'high',
      '-c:a', 'aac', '-b:a', '192k', '-shortest', '-movflags', '+faststart', outFile]);
    fs.rmSync(tmp, { recursive: true, force: true });
    console.log(outFile);
  } finally {
    const gone = new Promise(r => chrome.once('exit', r));
    chrome.kill();
    await gone;
    fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  }
}

main().catch(e => { console.error(e); process.exit(1); });
