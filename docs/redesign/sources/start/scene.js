<script>
// Упрощённые сцены вечера (в приложении — иллюстрации хэндоффа серии 36)
(function(){
  var W=368;
  function stars(h){var s='',p=[[30,.18],[80,.1],[140,.22],[210,.08],[260,.16],[320,.12],[350,.26],[110,.3],[300,.32]];p.forEach(function(q,i){s+='<circle cx="'+q[0]+'" cy="'+(q[1]*h)+'" r="'+(i%3?1.2:1.8)+'" fill="#fff" opacity="'+(i%2?.55:.85)+'"/>'});return s}
  function base(h,sun){
    var hz=h*0.62, sy=hz-38+sun*9, id='g'+Math.random().toString(36).slice(2,7);
    return {hz:hz,svg:'<defs><linearGradient id="'+id+'s" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2C2458"/><stop offset=".55" stop-color="#7A4E86"/><stop offset=".9" stop-color="#E29A78"/></linearGradient>'+
      '<radialGradient id="'+id+'u"><stop offset="0" stop-color="#FFF1C4"/><stop offset="1" stop-color="#F6BF72"/></radialGradient>'+
      '<linearGradient id="'+id+'g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3A2F63"/><stop offset="1" stop-color="#131318"/></linearGradient></defs>'+
      '<rect width="'+W+'" height="'+hz+'" fill="url(#'+id+'s)"/>'+stars(hz)+
      '<circle cx="'+(W/2)+'" cy="'+sy+'" r="58" fill="url(#'+id+'u)"/>'+
      '<path d="M0 '+(hz-14)+' Q90 '+(hz-40)+' 184 '+(hz-10)+' T368 '+(hz-22)+' V'+hz+' H0z" fill="#5B4A8A"/>'+
      '<rect y="'+hz+'" width="'+W+'" height="'+(h-hz)+'" fill="url(#'+id+'g)"/>'};
  }
  function neck(h,hz){
    var cx=W/2, s='<path d="M'+(cx-4)+' '+hz+' L'+(cx-70)+' '+h+' H'+(cx+70)+' L'+(cx+4)+' '+hz+'z" fill="#1B1628"/>';
    [-42,-14,14,42].forEach(function(d){s+='<path d="M'+(cx+d*0.06)+' '+hz+' L'+(cx+d)+' '+h+'" stroke="#F3E6D6" stroke-width="2" opacity=".85"/>'});
    s+='<rect x="'+(cx-9)+'" y="'+(hz-70)+'" width="18" height="72" rx="3" fill="#1B1628"/>';
    s+='<circle cx="'+cx+'" cy="'+(hz-84)+'" r="20" fill="#1B1628"/><circle cx="'+cx+'" cy="'+(hz-84)+'" r="11" fill="none" stroke="#E6A860" stroke-width="3"/><circle cx="'+cx+'" cy="'+(hz-84)+'" r="4" fill="#E6A860"/>';
    [[-1,-56],[1,-46],[-1,-34],[1,-24]].forEach(function(p){s+='<ellipse cx="'+(cx+p[0]*18)+'" cy="'+(hz+p[1])+'" rx="9" ry="6" fill="#1B1628"/>'});
    return s}
  function stand(h,hz){
    var cx=W/2, top=hz-120;
    var s='<rect x="'+(cx-3)+'" y="'+(top+130)+'" width="6" height="'+(h-top-130)+'" fill="#1B1628"/>';
    s+='<path d="M'+(cx-120)+' '+(top+20)+' L'+(cx+120)+' '+(top+20)+' L'+(cx+128)+' '+(top+132)+' L'+(cx-128)+' '+(top+132)+'z" fill="#E9E1D2"/>';
    for(var i=0;i<6;i++){var y=top+34+i*15;s+='<path d="M'+(cx-110)+' '+y+' H'+(cx-52)+' M'+(cx+52)+' '+y+' H'+(cx+110)+'" stroke="#8E8474" stroke-width="1.2"/>'}
    s+='<rect x="'+(cx-130)+'" y="'+(top+130)+'" width="260" height="8" rx="3" fill="#1B1628"/>';
    s+='<rect x="'+(cx-46)+'" y="'+(top)+'" width="92" height="150" rx="14" fill="#0E0D12"/>';
    s+='<rect x="'+(cx-40)+'" y="'+(top+6)+'" width="80" height="138" rx="10" fill="#1F5A3B"/>';
    s+='<circle cx="'+cx+'" cy="'+(top+64)+'" r="24" fill="none" stroke="#6FD39B" stroke-width="4"/><circle cx="'+cx+'" cy="'+(top+64)+'" r="5" fill="#6FD39B"/>';
    s+='<rect x="'+(cx-22)+'" y="'+(top+104)+'" width="44" height="6" rx="3" fill="#6FD39B" opacity=".8"/>';
    s+='<circle cx="'+cx+'" cy="'+(top+64)+'" r="60" fill="#6FD39B" opacity=".12"/>';
    return s}
  function road(h,hz){
    var s='<path d="M150 '+h+' C170 '+(hz+40)+' 250 '+(hz+30)+' 230 '+hz+'" stroke="#E9D9C4" stroke-width="16" fill="none" opacity=".75"/>';
    s+='<path d="M20 '+(hz+10)+' h60 v-30 l-30 -18 l-30 18z" fill="#1B1628"/><rect x="36" y="'+(hz-8)+'" width="10" height="10" fill="#F3C07A"/><rect x="56" y="'+(hz-8)+'" width="10" height="10" fill="#F3C07A"/>';
    s+='<path d="M270 '+(hz-2)+' h70 v-22 h-6 v-14 h-10 v14 h-40 v-10 l-14 -8z" fill="#2A2350"/><rect x="286" y="'+(hz-16)+'" width="6" height="8" fill="#F3C07A" opacity=".8"/><rect x="310" y="'+(hz-16)+'" width="6" height="8" fill="#F3C07A" opacity=".8"/>';
    s+='<circle cx="188" cy="'+(hz+42)+'" r="5" fill="#1B1628"/><rect x="184" y="'+(hz+47)+'" width="8" height="18" rx="3" fill="#1B1628"/><rect x="192" y="'+(hz+50)+'" width="6" height="12" rx="2" fill="#5B43B8"/>';
    [[120,.3],[250,.55],[300,.75]].forEach(function(p,i){s+='<path d="M'+p[0]+' '+(hz+p[1]*(h-hz)-6)+' v-14 l10 -3 v14" stroke="#C4ADFF" stroke-width="2.4" fill="none"/><circle cx="'+(p[0]-3)+'" cy="'+(hz+p[1]*(h-hz)-4)+'" r="4" fill="#C4ADFF"/>'});
    return s}
  function folder(h,hz){
    var cx=W/2;
    var s='<rect x="'+(cx-110)+'" y="'+(hz-58)+'" width="64" height="104" rx="12" fill="#0E0D12"/><rect x="'+(cx-105)+'" y="'+(hz-52)+'" width="54" height="92" rx="8" fill="#2A2352"/>';
    s+='<path d="M'+(cx-40)+' '+(hz-20)+' Q'+cx+' '+(hz-70)+' '+(cx+40)+' '+(hz-24)+'" stroke="#C4ADFF" stroke-width="2.5" fill="none" stroke-dasharray="5 5"/>';
    s+='<path d="M'+(cx+30)+' '+(hz-30)+' h30 l8 8 h44 v62 h-82z" fill="#C9814A"/><path d="M'+(cx+30)+' '+(hz-16)+' h82 v56 h-82z" fill="#E0A064"/>';
    s+='<rect x="'+(cx+56)+'" y="'+(hz-2)+'" width="30" height="24" rx="4" fill="#F3E6D6"/><path d="M'+(cx+64)+' '+(hz+6)+' h14 M'+(cx+64)+' '+(hz+13)+' h10" stroke="#8E8474" stroke-width="2"/>';
    return s}
  document.querySelectorAll('.scene[data-scene]').forEach(function(el){
    var h=parseFloat(el.style.height)||300, b=base(h,+el.getAttribute('data-sun')||0), k=el.getAttribute('data-scene');
    var fg={neck:neck,stand:stand,road:road,folder:folder}[k](h,b.hz);
    el.insertAdjacentHTML('beforeend','<svg viewBox="0 0 '+W+' '+h+'" preserveAspectRatio="xMidYMid slice" aria-hidden="true">'+b.svg+fg+'</svg>');
  });
})();
</script>
