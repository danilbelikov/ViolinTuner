// Geometry for Violin Journey icon directions v2 (108 grid). build() -> {a,b,c: {defs, fg, mono}}
function build(){
const r1=n=>{let v=Math.round(n*10)/10; if(Object.is(v,-0)) v=0; return String(v);};
const pp=p=>r1(p[0])+' '+r1(p[1]);
const poly=(pts,close=true)=>'M'+pts.map(pp).join('L')+(close?'Z':'');
const bez=(p0,p1,p2,p3,t)=>{const u=1-t;return[u*u*u*p0[0]+3*u*u*t*p1[0]+3*u*t*t*p2[0]+t*t*t*p3[0],u*u*u*p0[1]+3*u*u*t*p1[1]+3*u*t*t*p2[1]+t*t*t*p3[1]];};
const circ=(cx,cy,r)=>`M${r1(cx-r)} ${r1(cy)}a${r1(r)} ${r1(r)} 0 1 0 ${r1(2*r)} 0a${r1(r)} ${r1(r)} 0 1 0 ${r1(-2*r)} 0Z`;
const ell=(cx,cy,rx,ry)=>`M${r1(cx-rx)} ${r1(cy)}a${r1(rx)} ${r1(ry)} 0 1 0 ${r1(2*rx)} 0a${r1(rx)} ${r1(ry)} 0 1 0 ${r1(-2*rx)} 0Z`;
const RECT='M-18 -18H126V126H-18Z';
const stops=s=>s.map(([o,c,a])=>`<stop offset="${o}" stopColor="${c}"${a!=null?` stopOpacity="${a}"`:''}></stop>`).join('');
const LG=(id,x1,y1,x2,y2,s)=>`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}">${stops(s)}</linearGradient>`;
const RG=(id,cx,cy,r,s)=>`<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${cx}" cy="${cy}" r="${r}">${stops(s)}</radialGradient>`;
const pa=(d,fill,x='')=>`<path d="${d}" fill="${fill}"${x}></path>`;
const st=(d,col,w,x='')=>`<path d="${d}" fill="none" stroke="${col}" strokeWidth="${w}" strokeLinecap="round" strokeLinejoin="round"${x}></path>`;
const spark=(x,y,s)=>{const k=s*0.17;return `M${r1(x)} ${r1(y-s)}Q${r1(x+k)} ${r1(y-k)} ${r1(x+s)} ${r1(y)}Q${r1(x+k)} ${r1(y+k)} ${r1(x)} ${r1(y+s)}Q${r1(x-k)} ${r1(y+k)} ${r1(x-s)} ${r1(y)}Q${r1(x-k)} ${r1(y-k)} ${r1(x)} ${r1(y-s)}Z`;};
// violin body, right half, normalized to height 1
const BODY=[[[0.12,0],[0.235,0.04],[0.235,0.17]],[[0.235,0.27],[0.205,0.31],[0.214,0.376]],[[0.17,0.386],[0.148,0.43],[0.148,0.49]],[[0.148,0.55],[0.176,0.595],[0.228,0.606]],[[0.222,0.66],[0.292,0.69],[0.292,0.79]],[[0.292,0.925],[0.14,1],[0,1]]];
function bodyD(cx,top,H){const T=(p,s)=>[cx+s*p[0]*H,top+p[1]*H];let d='M'+pp([cx,top]);const starts=[[0,0]];BODY.forEach(s=>starts.push(s[2]));BODY.forEach(([a,b,e])=>{d+='C'+[T(a,1),T(b,1),T(e,1)].map(pp).join(' ');});for(let i=BODY.length-1;i>=0;i--){const[a,b]=BODY[i];d+='C'+[T(b,-1),T(a,-1),T(starts[i],-1)].map(pp).join(' ');}return d+'Z';}
function bodyPts(cx,top,H,n=14){const T=(p,s)=>[cx+s*p[0]*H,top+p[1]*H];const R=[];let s=[0,0];BODY.forEach(([a,b,e],i)=>{for(let k=i?1:0;k<=n;k++)R.push(bez(s,a,b,e,k/n));s=e;});const right=R.map(p=>T(p,1));const left=R.slice(1,-1).reverse().map(p=>T(p,-1));return right.concat(left);}
function cat(pts,n=10){const o=[];for(let i=0;i<pts.length-1;i++){const p0=pts[Math.max(0,i-1)],p1=pts[i],p2=pts[i+1],p3=pts[Math.min(pts.length-1,i+2)];for(let k=i?1:0;k<=n;k++){const t=k/n,t2=t*t,t3=t2*t;o.push([0,1].map(j=>0.5*(2*p1[j]+(-p0[j]+p2[j])*t+(2*p0[j]-5*p1[j]+4*p2[j]-p3[j])*t2+(-p0[j]+3*p1[j]-3*p2[j]+p3[j])*t3)));}}return o;}
function ribbon(c,wf){const L=[],R=[],N=c.length;for(let i=0;i<N;i++){const a=c[Math.max(0,i-1)],b=c[Math.min(N-1,i+1)];let tx=b[0]-a[0],ty=b[1]-a[1];const l=Math.hypot(tx,ty)||1;tx/=l;ty/=l;const w=wf(c[i],i/(N-1))/2;L.push([c[i][0]-ty*w,c[i][1]+tx*w]);R.push([c[i][0]+ty*w,c[i][1]-tx*w]);}return{L,R};}
const ribD=rb=>poly(rb.L.concat(rb.R.slice().reverse()));
function spiral(cx,cy,ra,rb,th0,turns,dir=-1,n=64){const o=[];for(let i=0;i<=n;i++){const t=i/n,th=th0+dir*turns*2*Math.PI*t,r=ra+(rb-ra)*t;o.push([cx+r*Math.cos(th),cy+r*Math.sin(th)]);}return o;}
const segI=(a,b,c,d)=>{const rx=b[0]-a[0],ry=b[1]-a[1],sx=d[0]-c[0],sy=d[1]-c[1],den=rx*sy-ry*sx;if(Math.abs(den)<1e-12)return null;const qx=c[0]-a[0],qy=c[1]-a[1];const t=(qx*sy-qy*sx)/den,u=(qx*ry-qy*rx)/den;return(t>=0&&t<=1&&u>=0&&u<=1)?[a[0]+t*rx,a[1]+t*ry]:null;};
const hit=(E,P)=>{for(let i=0;i<E.length-1;i++)for(let j=0;j<P.length;j++){const h=segI(E[i],E[i+1],P[j],P[(j+1)%P.length]);if(h)return{i,j,p:h};}return null;};
// violin head (front-ish view: pegs both sides, scroll on top). base = nut
function head(bx,by,s,o={}){const X=x=>bx+x*s,Y=y=>by+y*s;const hw0=o.hw0??3.3,hw1=o.hw1??3.9,ph=o.ph??24;
  const pegbox=poly([[X(-hw0),Y(0)],[X(hw0),Y(0)],[X(hw1),Y(-ph)],[X(-hw1),Y(-ph)]]);
  const sc=[X(o.scx??0.5),Y(o.scy??-29)],R=(o.R??8.5)*s;const scroll=circ(sc[0],sc[1],R);
  const groove=poly(spiral(sc[0]+0.8*s,sc[1]+0.4*s,R*0.84,R*0.15,0.9,1.75,-1,72),false);
  const pegs=[];(o.pegY??[[-21,-1],[-16.5,1],[-12,-1],[-7.5,1]]).forEach(([py,sx])=>{pegs.push(poly([[X(sx*3.3),Y(py-1.05)],[X(sx*6.4),Y(py-0.85)],[X(sx*6.4),Y(py+0.85)],[X(sx*3.3),Y(py+1.05)]]),ell(X(sx*8.6),Y(py),2.9*s,2.15*s));});
  return{pegbox,scroll,groove,pegs,sc,R};}
const I={};
// ───────── A · окно-скрипка
(()=>{const cx=54,top=21,H=66,hz=59.6;const bd=bodyD(cx,top,H);
  const defs=[LG('a-sky',0,top,0,hz,[[0,'#463A8A'],[0.5,'#7F5590'],[0.84,'#D98470'],[1,'#F4A870']]),
    RG('a-sunglow',54,hz,21,[[0,'#FFD98A',0.9],[0.35,'#F6A872',0.4],[1,'#F6A872',0]]),
    LG('a-road',0,hz,0,86,[[0,'#FFE6B0'],[1,'#D9C8F0']]),
    RG('a-wall',54,56,74,[[0,'#3B2F75'],[0.4,'#241D48'],[1,'#131318']])].join('');
  const rc=cat([[54,60.1],[55,61.3],[56.4,63.6],[51,68.5],[48.5,76],[55.5,84],[58,93]],10);
  const road=ribD(ribbon(rc,p=>Math.max(0.3,0.56*(p[1]-hz))));
  const far='M20 60.4C30 57.2 37 55.4 42.5 56C47 56.5 50 58.8 52 60.4L56 60.4C58 58.6 61 55.4 65.5 55.2C71 55 78 57.6 88 60.4V126H20Z';
  const mid='M20 67C30 63 40 62.4 48 64C54 65.2 60 62.8 68 62.6C76 62.4 82 64.4 88 65.6V126H20Z';
  const near='M20 76C30 71.6 38 70.8 46 72.6C52 74 58 72.2 66 71.4C74 70.8 82 72.6 88 73.6V126H20Z';
  const fg=[pa(RECT,'url(#a-sky)'),pa(spark(61.5,31,2.6),'#FFF1D6'),pa(circ(46.5,28.5,0.75),'#FFF1D6'),pa(circ(50.5,39.5,0.6),'#FFF1D6',' fillOpacity=".8"'),
    pa(circ(54,hz,21),'url(#a-sunglow)'),pa(circ(54,hz,6.6),'#FFE7A8'),pa(far,'#9A6790'),pa(mid,'#6E5088'),pa(near,'#4A3B72'),pa(road,'url(#a-road)'),
    pa(RECT+bd,'url(#a-wall)',' fillRule="evenodd"'),st(bd,'#C4ADFF',1.5,' strokeOpacity=".85"')].join('');
  const BP=bodyPts(cx,top,H,16);const mc=cat([[54,57.5],[55.4,60],[56.6,63.4],[51,68.5],[48.5,76],[55.5,84],[58,93]],10);
  const mr=ribbon(mc,p=>Math.max(1,0.42*(p[1]-56.5)));const hR=hit(mr.R,BP),hL=hit(mr.L,BP);
  const out=[hR.p],n=BP.length;let k=hR.j;for(let g=0;g<n+2;g++){out.push(BP[k]);if(k===(hL.j+1)%n)break;k=(k-1+n)%n;}
  out.push(hL.p);for(let i=hL.i;i>=0;i--)out.push(mr.L[i]);for(let i=0;i<=hR.i;i++)out.push(mr.R[i]);
  I.a={defs,fg,mono:[pa(bd,'currentColor'),st(poly(cat([[54,62.5],[56.2,64.4],[51,68.5],[48.5,76],[55.5,84],[58,93]],10),false),'#000',3.4,' class="cut"'),pa(circ(54,56.4,4.2),'#000',' class="cut"')].join(''),bodyD:bd};})();
// ───────── B · гриф-дорога (вид скрипача)
(()=>{const hz=62;
  const defs=[LG('b-sky',0,0,0,hz,[[0,'#2A2857'],[0.55,'#6E4A78'],[1,'#E08A63']]),
    RG('b-glow',54,54,52,[[0,'#FFD98A',0.55],[0.5,'#F2A070',0.18],[1,'#F2A070',0]]),
    RG('b-sun',50,44,30,[[0,'#FFF1CC'],[0.65,'#FFD98A'],[1,'#F6BC76']]),
    LG('b-ground',0,hz,0,108,[[0,'#4E3D6C'],[1,'#1B1627']]),
    LG('b-board',0,hz,0,108,[[0,'#2C2248'],[1,'#0E0D13']]),
    LG('b-str',0,hz,0,108,[[0,'#FFE3AA'],[1,'#E9DDFF']]),
    LG('b-head',0,24,0,hz,[[0,'#241B3A'],[1,'#1A1428']])].join('');
  const Hd=head(54,hz,0.92);
  const strings=[0,1,2,3].map(i=>{const xt=54+(i-1.5)*1.45,xb=54+(i-1.5)*15.5,wt=0.45,wb=2.5;return poly([[xt-wt/2,hz],[xt+wt/2,hz],[xb+wb/2,126],[xb-wb/2,126]]);});
  const board=poly([[18,126],[90,126],[57.05,hz],[50.95,hz]]);
  const hillL='M-18 62V56C-6 55 6 53.6 14 54.6C21 55.4 27 57.6 33 59.4C37 60.6 41 61.5 45 62Z';
  const hillR='M126 62V54.6C116 53.8 105 52.4 96 53.8C89 54.8 83 57.4 76 60C72.6 61 68.8 61.6 64 62Z';
  const fg=[pa(RECT,'url(#b-sky)'),pa(spark(24,20,2.2),'#FFF1D6'),pa(circ(84,15,0.8),'#FFF1D6'),pa(circ(14,36,0.6),'#FFF1D6',' fillOpacity=".7"'),
    pa(circ(54,54,52),'url(#b-glow)'),pa(circ(54,52,23),'url(#b-sun)'),pa(hillL,'#5A4A7E'),pa(hillR,'#5A4A7E'),
    pa('M-18 62H126V126H-18Z','url(#b-ground)'),pa(board,'url(#b-board)'),...strings.map(d=>pa(d,'url(#b-str)')),
    pa(Hd.pegbox,'url(#b-head)'),...Hd.pegs.map(d=>pa(d,'url(#b-head)')),pa(Hd.scroll,'url(#b-head)'),st(Hd.groove,'#F2B57A',1.15,' strokeOpacity=".9"')].join('');
  const cxr=Math.sqrt(21.5*21.5-100);
  const mStr=[0,1,2,3].map(i=>{const xt=54+(i-1.5)*1.45,xb=54+(i-1.5)*14,wt=0.8,wb=3.4;return poly([[xt-wt/2,hz],[xt+wt/2,hz],[xb+wb/2,100],[xb-wb/2,100]]);});
  const mono=[`<path d="M${r1(54-cxr)} 62A21.5 21.5 0 1 1 ${r1(54+cxr)} 62" fill="none" stroke="currentColor" strokeWidth="3.2"></path>`,st('M25 62H83','currentColor',2.2),
    pa(Hd.pegbox,'currentColor'),...Hd.pegs.map(d=>pa(d,'currentColor')),pa(circ(Hd.sc[0],Hd.sc[1],Hd.R),'currentColor'),st(Hd.groove,'#000',1.5,' class="cut"'),...mStr.map(d=>pa(d,'currentColor'))].join('');
  I.b={defs,fg,mono};})();
// ───────── C · колки-указатель
(()=>{const defs=[LG('c-sky',0,0,0,76,[[0,'#2A2857'],[0.5,'#6E4A78'],[1,'#E08A63']]),
    RG('c-glow',26,72,46,[[0,'#FFD98A',0.7],[0.4,'#F2A070',0.25],[1,'#F2A070',0]]),
    LG('c-ground',0,74,0,108,[[0,'#4C4676'],[1,'#221C30']]),
    LG('c-road',0,75,0,108,[[0,'#FFE6B8'],[1,'#D9C8F0']]),
    LG('c-wood',50.3,0,57.7,0,[[0,'#DDA062'],[0.45,'#B46E3A'],[1,'#7E4120']]),
    RG('c-scroll',51.5,25.5,12,[[0,'#E6A868'],[0.55,'#B46E3A'],[1,'#7E4120']])].join('');
  const wf=p=>Math.max(0.5,0.42*(p[1]-74.6));
  const rm=ribD(ribbon(cat([[54.5,81.5],[55.2,90],[56.5,100],[57,114]],10),wf));
  const rl=ribD(ribbon(cat([[35,75.6],[40,76.6],[47,78.6],[54.5,82.2]],10),wf));
  const rr=ribD(ribbon(cat([[76,75.2],[70,76.4],[62,78.4],[54.5,82.2]],10),wf));
  const far='M-18 76V66C-4 64 10 61 22 62.5C32 63.8 40 67 50 68C62 69.2 72 64.5 86 63.5C100 62.5 114 65 126 66V76Z';
  const ground='M-18 126V78C0 75.6 24 74.4 44 75.2C64 76 90 73.8 126 75.6V126Z';
  const post=poly([[50.8,83],[57.2,83],[57.2,52],[50.8,52]]);
  const pbox=poly([[50.6,53],[57.4,53],[58,37.5],[50,37.5]]);
  const sc=[54.8,30.4],R=7.8;
  const groove=poly(spiral(sc[0]+0.8,sc[1]+0.3,R*0.84,R*0.15,0.9,1.75,-1,72),false);
  const brd=(y,dir,len)=>{const h=6.6,x0=dir<0?51:57,x1=x0+dir*len,tip=x1+dir*4.6;
    return[`M${x0} ${y}H${x1}L${r1(tip)} ${r1(y+h/2)}L${x1} ${y+h}H${x0}Z`,`M${x0} ${y}H${x1}L${r1(x1+dir*3.9)} ${r1(y+2.6)}L${x1} ${r1(y+5.2)}H${x0}Z`];};
  const B=[brd(38,-1,17),brd(46.4,1,17),brd(54.8,-1,14)];
  const fg=[pa(RECT,'url(#c-sky)'),pa(spark(80,20,2.2),'#FFF1D6'),pa(circ(20,28,0.7),'#FFF1D6'),pa(circ(90,40,0.6),'#FFF1D6',' fillOpacity=".7"'),
    pa(circ(26,72,46),'url(#c-glow)'),pa(circ(26,71,9),'#FFE3A0'),pa(far,'#6A5E8C'),pa(ground,'url(#c-ground)'),pa(rl,'url(#c-road)'),pa(rr,'url(#c-road)'),pa(rm,'url(#c-road)'),
    pa(ell(55,83.2,7.5,1.5),'#1C1530',' fillOpacity=".5"'),pa(post,'url(#c-wood)'),pa(pbox,'url(#c-wood)'),pa(circ(sc[0],sc[1],R),'url(#c-scroll)'),st(groove,'#5E3016',1.2),
    ...B.flatMap(([full,face])=>[pa(full,'#A792E0'),pa(face,'#EDE4FF')])].join('');
  const mono=[pa(post,'currentColor'),pa(pbox,'currentColor'),pa(circ(sc[0],sc[1],R),'currentColor'),st(groove,'#000',1.5,' class="cut"'),...B.map(([full])=>pa(full,'currentColor'))].join('');
  I.c={defs,fg,mono};})();
return I;}
