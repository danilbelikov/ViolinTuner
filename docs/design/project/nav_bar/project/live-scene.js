// Сцена Live: картина комнаты/зала, свет, вуаль, кольцо.
// Извлечено из «Комната и залы.dc.html» (кадры 29a–29l) без изменений модели.
export function buildLiveScene(React, K, opts = {}) {
  const h = React.createElement;
  const { L, R, RR, E, C, PG, ARCH, rep, compose, HOUSES, caseL, violin } = K;
    const ON='#E6E4EE', OV='#A39FB5', SURF='#131318', PR='#C4ADFF', OUT='#3A3846';
    const Z = { in:'#47C97E', near:'#E5B03C', off:'#E8565C' };
    const MAT = { ebony:'#1C1A20', wood:'#8B5E3C', woodLit:'#A9763F', woodDark:'#4E3424', bone:'#F1EEE6', boneSh:'#D8D0BE', nickel:'#6B6C78', brass:'#C9A24A', velvet:'#8E2F3F', ink:'#2A2430' };
    // ---------- colour
    const hx = c => { if (c.startsWith('rgba')) { const m=c.match(/[\d.]+/g).map(Number); return [m[0],m[1],m[2],m[3]]; } const n=parseInt(c.slice(1),16); return [n>>16&255,n>>8&255,n&255,1]; };
    const toC = ([r,g,b,a]) => a>=.999 ? '#'+[r,g,b].map(v=>Math.round(Math.max(0,Math.min(255,v))).toString(16).padStart(2,'0')).join('') : `rgba(${Math.round(r)},${Math.round(g)},${Math.round(b)},${(+a).toFixed(2)})`;
    const mix = (a,b,t) => { const A=hx(a), B=hx(b); return toC(A.map((v,i)=>v+(B[i]-v)*t)); };
    const rgba = (c,a) => { const [r,g,b]=hx(c); return `rgba(${r},${g},${b},${(+a).toFixed(3)})`; };
    const desat = (c,keep) => { const [r,g,b,a]=hx(c); const y=.2126*r+.7152*g+.0722*b; return toC([y+(r-y)*keep, y+(g-y)*keep, y+(b-y)*keep, a]); };
    // light model
    const DIM = { sat: opts.dimSat ?? .12, mix: opts.dimMix ?? .76, tint: opts.zoneTint ?? .18, lightDrop: .25 };
    const VEIL = opts.veilStrength ?? .68;
    const zScale = z => z==='off' ? .7 : 1;
    const dimC = (c, zone, glow) => { let x = desat(c, DIM.sat); x = mix(x, SURF, DIM.mix); if (zone && glow>0) x = mix(x, Z[zone], DIM.tint*glow*zScale(zone)); return x; };
    // ---------- renderer (mirrors Дом.dc.html draw + light)
    let gid = 0;
    const draw = (layers, mode, o = {}) => {
      const pal = { ...K.palOf(mode, o.over), ...(o.pal||{}) }; const id='v'+(gid++); const [vbx,vby]=(o.viewBox||'0 0').split(' ').map(Number);
      const dark = !!o.dark, zone = o.zone, glow = o.glow||0;
      const raw = t => (t.startsWith('#')||t.startsWith('rgba')||t==='none') ? t : (pal[t] ?? t);
      const col = t => { const c = raw(t); return (dark && c!=='none') ? dimC(c, zone, glow) : c; };
      const gl0 = pal.glow, sky0 = pal.sky, skyLow0 = pal.skyLow;
      const els = [h('defs',{key:'d'},
        h('linearGradient',{id:id+'s',x1:0,y1:0,x2:0,y2:1},h('stop',{offset:0,stopColor:col(sky0)}),h('stop',{offset:1,stopColor:col(skyLow0)})),
        h('radialGradient',{id:id+'g'},h('stop',{offset:0,stopColor:dark?rgba('#FFC46E',.5*DIM.lightDrop):gl0}),h('stop',{offset:1,stopColor:'rgba(255,196,110,0)'})),
        o.veil ? h('radialGradient',{id:id+'v',cx:o.veilC[0],cy:o.veilC[1],r:o.veilC[2],gradientUnits:'userSpaceOnUse'},h('stop',{offset:0,stopColor:SURF,stopOpacity:VEIL}),h('stop',{offset:.42,stopColor:SURF,stopOpacity:VEIL*.68}),h('stop',{offset:1,stopColor:SURF,stopOpacity:0})) : null,
        (zone&&glow>0&&dark) ? h('radialGradient',{id:id+'z',cx:o.veilC[0],cy:o.veilC[1],r:o.veilC[2]*1.35,gradientUnits:'userSpaceOnUse'},h('stop',{offset:0,stopColor:Z[zone],stopOpacity:.22*glow*zScale(zone)}),h('stop',{offset:.45,stopColor:Z[zone],stopOpacity:.07*glow*zScale(zone)}),h('stop',{offset:1,stopColor:Z[zone],stopOpacity:0})) : null)];
      layers.forEach((l,i) => {
        const isLight = l.fill==='GLOW' || l.glow;
        const fill = l.fill==='SKY' ? `url(#${id}s)` : l.fill==='GLOW' ? `url(#${id}g)` : col(l.fill);
        const p = { key:i, d:l.d, fill:l.fillNone?'none':fill, opacity: (l.op!=null? l.op:1) * (dark&&isLight?DIM.lightDrop:1), stroke:l.stroke?col(l.stroke):undefined, strokeWidth:l.sw, strokeLinecap:'round', strokeDasharray:l.dash };
        if (p.opacity===1) delete p.opacity;
        if (l.tx!==undefined) p.transform = `translate(${l.tx} ${l.ty}) scale(${l.sc||1})`;
        if (o.anim!==false && !dark) {
          if (l.anim) { const st={}; l.anim.split('+').forEach(a=>{ const [k,...v]=a.split(':'); const n=v.map(Number);
            if(k==='bob'){st['--a']=`${-n[0]}px`;st.animation=`bob ${n[1]}s ease-in-out infinite`;}
            else if(k==='sway'){st['--a']=`${n[0]}px`;st.animation=`sway ${n[1]}s ease-in-out infinite`;}
            else if(k==='swing'){st['--a']=`${n[0]}deg`;st['--n']=`${-n[0]}deg`;st.transformOrigin=`${n[2]-(vbx||0)}px ${n[3]-(vby||0)}px`;st.transformBox='view-box';st.animation=`swing ${n[1]}s ease-in-out infinite`;}
            else if(k==='blink')st.animation=`blink ${n[0]}s linear ${(i%4)*.7}s infinite`;
            else if(k==='rise')st.animation=`rise ${n[0]}s ease-out ${n[1]||0}s infinite`;
            else if(k==='flick')st.animation=`flick ${n[0]}s ease-in-out ${n[1]||0}s infinite`;
            else if(k==='fly'){st['--a']=`${n[0]}px`;st.animation=`fly ${n[1]}s linear infinite`;} }); p.style=st; }
          else if (l.fill==='window' && mode==='eve') p.style={animation:`flick ${4+(i%3)}s ease-in-out ${(i%5)*.6}s infinite`};
        }
        els.push(h('path',p));
      });
      if (o.veil) els.push(h('rect',{key:'vl',x:vbx,y:vby,width:o.vbw,height:o.vbh,fill:`url(#${id}v)`}));
      if (zone && glow>0 && dark) els.push(h('rect',{key:'zl',x:vbx,y:vby,width:o.vbw,height:o.vbh,fill:`url(#${id}z)`,style:{mixBlendMode:'screen'}}));
      const style={display:'block'}; if(o.cssW)style.width=o.cssW; if(o.cssH)style.height=o.cssH;
      return h('svg',{width:o.w||412,height:o.h||260,viewBox:o.viewBox||'0 0 412 260',preserveAspectRatio:o.par||'xMidYMid slice',style},els);
    };
    // ---------- room owned sets (no violin, no floor stand; case is open and empty)
    const RENT = ['wp_plum','floor_plank','window_simple','view_city','curtain_plum','desk_oak','lamp_table','chair_simple','plaid','stand_wood','rug_plum','notes','tea','books','ficus','portrait','shelf_scores','clock','cat_ginger','garland','metronome'];
    const WOOD = ['floor_plank','window_simple','view_mount','curtain_sand','desk_simple','lamp_table','chair_simple','stand_fold','rug_stripe','fireplace','notes','candles','monstera','cat_grey','poster'];
    const roomLayers = (hid, mode) => { const { l, over } = compose(hid, mode, hid==='wood'?WOOD:RENT); l.push(...caseL(340, 222, 'caseOut')); return { l, over }; };
    // ---------- halls: view from the stage
    const HT = { hvoid:'#140F1C', hwall:'#3A3048', hwallLit:'#4A3E5C', hwallSh:'#241D30', hgold:'#E2B74E', hgoldLit:'#F2D488', hgoldSh:'#A8863A', hseat:'#8E2F3F', hseatLit:'#A8323F', hseatSh:'#5A1E2A', hcream:'#F3EEE2', hcreamSh:'#D8D0BE', hboard:'#8B5E3C', hboardSh:'#6E4A34', hchand:'#FFE9B0', hdark:'#1C1530', hwood:'#A9713F', hwoodSh:'#7E5230', hstone:'#CFC6B4', hstoneSh:'#A89E8C' };
    const row = (y,hw,hh,rise) => `M${(206-hw).toFixed(1)} ${y.toFixed(1)}q${hw.toFixed(1)} ${(-2*rise).toFixed(1)} ${(2*hw).toFixed(1)} 0v${(-hh).toFixed(1)}q${(-hw).toFixed(1)} ${(2*rise).toFixed(1)} ${(-2*hw).toFixed(1)} 0Z`;
    const seats = (rise, n0=9) => rep(n0, k => { const t=k/(n0-1), y=300-96*t, hw=214-74*t, hh=12-5.5*t, rs=rise*(1-.45*t), n=Math.round(2*hw/(18-7*t));
      return [ L('hseatSh', row(y,hw,hh,rs)), L('hseat', row(y-hh*.55,hw,hh*.45,rs)),
        ...rep(n, i => L('hseatSh', R(206-hw+i*(2*hw/n), y-hh-rs*(1-Math.abs(i/(n/2)-1))*0+0, 1.1, hh))) ]; });
    const boards = () => [ L('hdark', R(-30,304,472,6), 2), L('hboard', R(-30,310,472,150), 2),
      ...rep(11, i => { const x0=(i*41-16); return L('hboardSh', PG([[x0,310],[x0+2.4,310],[206+(x0+2.4-206)*2.5,460],[206+(x0-206)*2.5,460]]), 2); }),
      L('rgba(255,255,255,.07)', R(-30,310,472,3), 2) ];
    const chand = (x,y,r) => [ L('hgoldSh', R(x-.6,-220,1.2,Math.max(2,(y-r)+220))), L('rgba(255,196,110,.5)', C(x,y,r*3.1), 1, { glow:true, anim:'flick:4' }), L('hchand', C(x,y,r)), L('rgba(255,255,255,.8)', C(x-r*.3,y-r*.3,r*.34)) ];
    // кариатида в перспективе — та же грамматика, что в VIENNA_INT, но вдоль сходящихся стен
    const cary = (x, yb, s, tok) => [ L('hgoldSh', R(x-4*s, yb-62*s, 8*s, 62*s)), L(tok, R(x-3*s, yb-60*s, 6*s, 60*s)), L(tok, E(x, yb-58*s, 7*s, 3*s)), L(tok, C(x, yb-66*s, 4*s)), L(tok, E(x, yb-70*s, 5*s, 2*s)), L(tok, R(x-6*s, yb-4*s, 12*s, 4*s)) ];
    const backWall = () => [ L('hwall', R(48,64,316,140)), L('hwallLit', R(48,64,106,140), 1, {op:.28}),
      L('hwallSh', PG([[0,-30],[48,64],[48,204],[0,300]])), L('hwallSh', PG([[412,-30],[364,64],[364,204],[412,300]])) ];
    const STAGE = (o) => { const l=[L('hvoid', R(-30,-240,472,720))];
      l.push(...(o.ceil?o.ceil():[])); l.push(...backWall()); l.push(...(o.tiers?o.tiers():[]));
      l.push(...seats(o.rise ?? 0, o.rows)); l.push(...(o.mark?o.mark():[])); l.push(...boards()); return l; };
    const tierArc = (y,hw,hh,rise,boxes) => { const l=[L('hgoldSh', row(y,hw,hh,rise)), L('hgold', row(y-hh*.62,hw,hh*.4,rise), 1, {op:.85})];
      if (boxes) rep(boxes, j => { const t=j/(boxes-1), x=206-hw+2*hw*t, dy=-2*rise*t*(1-t)*2; l.push(L('hdark', RR(x-8, y+dy-hh-16, 16, 16, 2)), L('hseatSh', R(x-8, y+dy-hh-6, 16, 6))); });
      return l; };
    const HALLS = {
      vienna: { n:'Вена · Золотой зал', type:'коробка', pal:{}, fn:()=>STAGE({ rise:6,
        // потолок коробки уходит от нас к задней стене: поперечные кессоны сжимаются, продольные рёбра сходятся к (206,150)
        ceil: ()=>{ const l=[L('hcream', R(-30,-240,472,304))];
          l.push(...rep(6, k=>{ const t=Math.pow(k/5,.8), y=-240+300*t, hw=240-84*t;
            return [ L('hgoldSh', PG([[206-hw,y],[206+hw,y],[206+hw-3,y+7],[206-hw+3,y+7]]), 1, {op:.42}),
                     L('hcreamSh', PG([[206-hw+3,y+7],[206+hw-3,y+7],[206+hw-6,y+16],[206-hw+6,y+16]]), 1, {op:.22}) ]; }));
          l.push(...[-.66,-.24,.24,.66].map(f=>L('hgoldSh', PG([[206+f*240,-240],[206+f*240+9,-240],[206+f*152+6,64],[206+f*152,64]]), 1, {op:.34})));
          l.push(L('hcreamSh', PG([[-30,-240],[26,-240],[78,64],[-30,64]]), 1, {op:.3}), L('hcreamSh', PG([[442,-240],[386,-240],[334,64],[442,64]]), 1, {op:.3}));
          l.push(...chand(118,-52,10), ...chand(294,-52,10), ...chand(206,4,7));
          return l; },
        // кариатиды стоят вдоль сходящихся стен и уменьшаются с глубиной — со сцены это и есть примета зала
        tiers: ()=>{ const l=[];
          rep(5, k=>{ const t=k/4, s=.98-.54*t, yb=202-40*t;
            [-1,1].forEach(sg=>l.push(...cary(206+sg*(202-156*t), yb, s, sg<0?'hgoldLit':'hgold'))); });
          l.push(...tierArc(156,180,11,6,0));
          return l; },
        mark: ()=>[ L('hgold', R(48,58,316,6)), L('hgoldSh', R(48,64,316,2)) ] }) },
      paris: { n:'Париж · Опера Гарнье', type:'подкова', pal:{}, fn:()=>STAGE({ rise:16, rows:8,
        ceil: ()=>[ L('hdark', R(-30,-240,472,300)), L('hcreamSh', C(206,-6,132)), L('hcream', C(206,-6,116)),
          ...rep(6, i=>{ const a=Math.PI*(1.06+.88*i/5); return L(['#7A3E8E','#3E5A8E','#B94A4A','#D9B26B','#4E8E57','#C9814A'][i], E(206+Math.cos(a)*74, -6+Math.sin(a)*54, 26, 18), 1, {op:.55}); }),
          L('hgold', C(206,-6,120), 1, {fillNone:true, stroke:'hgold', sw:3}), ...chand(206,46,15) ],
        tiers: ()=>[ ...tierArc(168,196,10,16,13), ...tierArc(138,182,9,14,12), ...tierArc(110,168,8,12,11), ...tierArc(84,154,7,10,10) ] }) },
      berlin: { n:'Берлин · Филармония', type:'виноградник', pal:{hwall:'#4A3E38',hwallLit:'#5C4E44',hseat:'#3E6E7A',hseatLit:'#4E8899',hseatSh:'#284A54'}, fn:()=>STAGE({ rise:4, rows:6,
        ceil: ()=>[ L('hdark', R(-30,-240,472,300)), ...rep(7, i=>{ const x=32+i*58, y=-30-((i%3)*26); return [L('hcreamSh', E(x,y,34,11)), L('hcream', E(x,y-3,30,8)), L('hgoldSh', R(x-.5,-240,1,y-8))]; }) ],
        tiers: ()=>rep(6, i=>{ const sg=i<3?-1:1, k=i%3, x=206+sg*(96+k*62), y=176-k*30, w=78-k*8;
          return [ L('hwoodSh', PG([[x-w/2,y],[x+w/2,y],[x+w/2-5,y-16],[x-w/2+5,y-16]])), L('hwood', R(x-w/2+5,y-19,w-10,4)),
            ...rep(5, j=>L('hseatSh', R(x-w/2+9+j*((w-20)/5), y-14, 7, 8))) ]; }) }) },
      leipzig: { n:'Лейпциг · Св. Фомы', type:'неф', pal:{hwall:'#CFC6B4',hwallLit:'#E2DACA',hwallSh:'#A89E8C',hseat:'#6E5238',hseatLit:'#8B6A48',hseatSh:'#4E3A28'}, fn:()=>STAGE({ rise:0, rows:7,
        ceil: ()=>[ L('hdark', R(-30,-240,472,220)), ...rep(4, i=>{ const t=i/3, hw=200-56*t, y=-10-t*52; return L('hstoneSh', `M${206-hw} ${y+70}L${206-hw} ${y}Q206 ${y-78} ${206+hw} ${y}L${206+hw} ${y+70}Z`, 1, {op:.5+.12*i}); }) ],
        tiers: ()=>[ ...rep(2, s=>{ const sg=s?1:-1; return rep(3, i=>{ const t=i/2, x=206+sg*(150-36*t), y=190-t*30, hh=126-40*t, w=30-8*t;
          return [ L('hstoneSh', ARCH(x-w/2, y-hh, w, hh)), L('hstone', ARCH(x-w/2+3, y-hh+4, w-6, hh-6), 1, {op:.35}) ]; }); }).flat(),
          L('hstoneSh', C(206,86,44)), L('hcream', C(206,86,38), 1, {op:.5}), ...rep(8, i=>{ const a=Math.PI*2*i/8; return L('hgoldSh', R(206+Math.cos(a)*19-1.2, 86+Math.sin(a)*19-1.2, 2.4, 2.4)); }), L('hgold', C(206,86,40), 1, {fillNone:true, stroke:'hgold', sw:2.4}) ] }) },
      cremona: { n:'Кремона · мастерская', type:'мастерская', pal:{}, fn:()=>{
        const l=[L('#E6D5B8', R(-30,-240,472,480)), L('#CDB891', R(-30,-240,472,80))];
        l.push(L('#5A3A22', R(28,26,128,140)), L('SKY', R(34,32,116,128)), L('#4C4676', R(42,104,24,54)), L('#4C4676', R(74,90,28,68)), L('#4C4676', R(110,112,30,46)), L('#5A3A22', R(88,32,5,128)), L('#5A3A22', R(34,92,116,5)));
        l.push(L('#5A3A22', R(196,14,196,6)), ...rep(5, i=>{ const x=216+i*40, len=16+(i%2)*9, y=20+len; return [L('#8A7A66', R(x-.7,20,1.4,len)), ...violin(x, y+46, .9, { body: i%2?'varnish':'varnishLight' })]; }));
        l.push(L('#6B6C78', R(300,-240,2,250)), L('GLOW', C(301,22,46), 1, {anim:'flick:4'}), L('#6B6C78', PG([[282,10],[320,10],[312,-6],[290,-6]])), L('#FFE9B0', C(301,14,3.4)));
        l.push(L('#8E5E3A', R(-30,200,472,260), 2), ...rep(9, i=>L('#74492C', PG([[i*52-20,200],[i*52-17,200],[206+(i*52-17-206)*2.4,460],[206+(i*52-20-206)*2.4,460]]), 2)));
        l.push(L('#5A3A22', R(-20,282,452,20), 2), L('#A9713F', R(-20,266,452,16), 2), L('#7E5230', R(-20,278,452,5), 2));
        l.push(L('#D9B27A', E(150,262,34,10), 2), L('#CDB891', E(150,260,22,6), 2), L('#6B6C78', R(236,258,34,7), 2), L('#5A3A22', R(240,255,24,3), 2), L('#6B6C78', R(292,257,5,8), 2), L('#6B6C78', R(306,255,14,9), 2), L('#8A7A66', E(348,262,16,5), 2));
        return l; } },
    };
    // ---------- pictures
    const FRAMES = { port:{ vb:'0 -260 412 860', w:412, h:892, vc:[206,80,300] }, land:{ vb:'0 10 412 190', w:892, h:412, vc:[98,105,150] }, small:{ vb:'0 -180 412 732', w:360, h:640, vc:[206,84,250], par:'xMidYMid meet' },
      half:{ vb:'0 -260 412 860', w:206, h:446, vc:[206,80,300] }, tiny:{ vb:'0 -260 412 860', w:165, h:357, vc:[206,80,300] } };
    const HFR = { port:{ vb:'0 -190 412 660', w:412, h:892, vc:[206,20,270] }, land:{ vb:'0 -60 412 190', w:892, h:412, vc:[98,35,150] }, small:{ vb:'0 -150 412 590', w:360, h:640, vc:[206,10,240] },
      half:{ vb:'0 -190 412 660', w:206, h:446, vc:[206,20,270] }, tiny:{ vb:'0 -190 412 660', w:165, h:357, vc:[206,20,270] } };
    const picture = (where, o={}) => {
      const fmt = o.fmt || 'port'; const isRoom = where==='rent'||where==='wood';
      const F = isRoom ? FRAMES[fmt] : HFR[fmt];
      const vb = o.vb || F.vb; const [vbx,vby,vbw,vbh] = vb.split(' ').map(Number);
      const dark = !!o.dark;
      const base = { w:o.w||F.w, h:o.h||F.h, viewBox:vb, par:o.par||F.par||'xMidYMid slice', vbw, vbh,
        dark, zone:o.zone, glow:o.glow||0, anim:o.anim, veil: !dark && o.veil!==false, veilC:[F.vc[0], vby + vbh*(o.vcy ?? .38), F.vc[2]] };
      if (isRoom) { const { l, over } = roomLayers(where, o.mode||'eve'); return draw(l, o.mode||'eve', { over, ...base }); }
      const H = HALLS[where]; return draw(H.fn(), 'eve', { pal:{ ...HT, ...H.pal }, ...base });
    };
    // ---------- ring (unchanged model, spec 5.8)
    const ring = (ringPx, zone, glow, o={}) => {
      const Rr=ringPx/2, box=ringPx*1.4, c=box/2, id='r'+(gid++);
      const colr = zone?Z[zone]:OUT, lvl=o.level??.5, haloR=Rr*1.4*(1+.04*lvl), core=mix(colr,'#FFFFFF',.45*glow);
      return h('svg',{width:box,height:box,viewBox:`0 0 ${box} ${box}`,style:{display:'block'}},[
        h('defs',{key:'d'},
          h('radialGradient',{id:id+'o'},h('stop',{offset:(Rr/haloR).toFixed(3),stopColor:colr,stopOpacity:.32*glow}),h('stop',{offset:(Rr/haloR+.12).toFixed(3),stopColor:colr,stopOpacity:.10*glow}),h('stop',{offset:'1',stopColor:colr,stopOpacity:0})),
          h('radialGradient',{id:id+'i'},h('stop',{offset:'.6',stopColor:colr,stopOpacity:0}),h('stop',{offset:'.88',stopColor:colr,stopOpacity:.07*glow}),h('stop',{offset:'1',stopColor:colr,stopOpacity:.18*glow}))),
        glow>0 && h('circle',{key:'o',cx:c,cy:c,r:haloR,fill:`url(#${id}o)`}),
        glow>0 && h('circle',{key:'i',cx:c,cy:c,r:Rr,fill:`url(#${id}i)`}),
        glow>0 && h('circle',{key:'s',cx:c,cy:c,r:Rr,fill:'none',stroke:colr,strokeWidth:6+10*glow,opacity:.25*glow}),
        h('circle',{key:'t',cx:c,cy:c,r:Rr,fill:'none',stroke:glow>0?core:OUT,strokeWidth:6,opacity:glow>0?.6+.4*glow:1}),
        ...(o.waves||[]).map((w,i)=>h('circle',{key:'w'+i,cx:c,cy:c,r:Rr*w.r,fill:'none',stroke:colr,strokeWidth:2,opacity:w.a})),
      ].filter(Boolean));
    };
  return { picture, ring, draw, HALLS, HT, FRAMES, HFR, MAT, Z, ON, OV, SURF, PR, OUT, mix, rgba, desat, dimC, roomLayers };
}
