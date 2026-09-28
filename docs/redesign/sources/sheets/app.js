// Фоны «Занятий» (вариант A) под листами и календарь с метками событий.
(function(){
  function ic(id,cls){return '<svg class="ic'+(cls?' '+cls:'')+'"><use href="#'+id+'"/></svg>'}
  function tabbar(run){
    return '<nav class="tabbar">'+
      '<div class="tab is-active"><div class="pill">'+ic('i-watch-f')+(run?'<span class="run-dot"></span>':'')+'</div>Занятия</div>'+
      '<div class="tab"><div class="pill">'+ic('i-string')+'</div>Live</div>'+
      '<div class="tab"><div class="pill">'+ic('i-sheet')+'</div>Репертуар</div>'+
      '<div class="tab"><div class="pill">'+ic('i-tape')+'</div>Записи</div></nav>';
  }
  var path='<div class="path-row"><div class="lvl-ring" style="--p:94%"><b>5</b></div><div class="path-main"><div class="path-total tnum">47 ч 17 мин</div><div class="path-sub">Уровень 5 · Гаммы · до 6-го 2 ч 43 мин</div></div>'+ic('i-chev-r','chev')+'</div><div class="gap"></div>';
  var bars=[39,56,22,72,44,100];
  function week(todayH,run){
    var d=['Пн','Вт','Ср','Чт','Пт','Сб'],h='<div class="week">';
    bars.forEach(function(b,i){h+='<div class="wd"><div class="bar"><i style="height:'+b+'%"></i></div><span>'+d[i]+'</span></div>'});
    return h+'<div class="wd is-today"><div class="bar today'+(run?' run':'')+'"><i style="height:'+todayH+'%"></i></div><span>Вс</span></div></div>';
  }
  var remind='<div class="remind"><div class="dot">'+ic('g-lesson','ic-s')+'</div><div style="flex:1"><b>Завтра в 17:00 — урок</b><small>Анна Сергеевна</small></div>'+ic('i-chev-r','chev')+'</div><div class="gap"></div>';
  var win='<div class="window"><div class="pic room"><span class="place">'+ic('i-home')+'Дома</span></div><div class="row"><span class="go">Хватает до Праги — в путь</span><span class="tacts tnum">'+ic('i-tact')+'47 884</span></div></div>';
  var today='<div class="card"><div class="today-head"><span class="label">Сегодня</span><span class="streak">'+ic('i-flame')+'8 дней</span></div><div class="today-num tnum">45 мин</div>'+week(50)+'<div class="week-total"><span>Неделя</span><b class="tnum">5 ч 45 мин</b></div></div><div class="gap"></div>';
  var running='<div class="run-card"><div class="today-head"><span class="run-live"><i></i>Занятие идёт</span><span class="streak">'+ic('i-flame')+'8 дней</span></div><div class="timer tnum">47:12</div><div class="caption tnum">Сегодня вместе с ним — 1 ч 32 мин</div></div><div class="gap"></div>';
  var start='<div class="dock"><button class="start">'+ic('i-watch')+'Начать занятие</button></div>';
  var stop='<div class="dock"><div class="stop-row"><button class="btn-out">'+ic('i-flag')+'Закончить занятие</button></div></div>';

  // сентябрь 2026: 1-е — вторник; сегодня 27; метки видов
  var tones={1:2,2:3,3:2,4:1,5:3,6:2,7:2,8:3,9:1,11:3,12:2,13:3,14:1,15:3,16:2,17:3,18:2,20:4,21:2,22:3,23:1,24:3,25:2,26:4,27:2};
  var K={L:['sq','--k-lesson'],R:['ci','--k-reh'],P:['tri','--k-perf'],O:['dia','--k-other'],X:['hex','--k-orch']};
  var ev={5:'X',7:'L',12:'XRLO',13:'P',14:'L',17:'O',19:'XO',21:'L',24:'R',26:'X',28:'L'};
  function cal(sel){
    var h='<div class="cal cal-ev">';['Пн','Вт','Ср','Чт','Пт','Сб','Вс'].forEach(function(d){h+='<div class="dn">'+d+'</div>'});
    h+='<div></div>';
    for(var d=1;d<=30;d++){
      var c='cd'; if(tones[d]) c+=' l'+tones[d]; if(d>27) c+=' fut'; if(d===27) c+=' tod'; if(d===sel) c+=' sel';
      var m=''; if(ev[d]){var ks=ev[d].split(''); m='<span class="mk">'; ks.slice(0,3).forEach(function(k){m+='<s class="'+K[k][0]+'" style="--k:var('+K[k][1]+')"></s>'}); if(ks.length>3) m+='<em>+</em>'; m+='</span>';}
      h+='<div class="'+c+'"><i>'+d+'</i>'+m+'</div>';
    }
    h+='</div><div class="legend">';
    [['L','Урок'],['R','Репетиция'],['P','Выступление'],['X','Оркестр'],['O','Другое']].forEach(function(p){h+='<span><s class="'+K[p[0]][0]+'" style="--k:var('+K[p[0]][1]+')"></s>'+p[1]+'</span>'});
    return h+'</div>';
  }
  var calHead='<div class="cal-head" style="margin-top:8px"><button class="nav-btn">'+ic('i-chev-l')+'</button><div class="cal-title" style="text-align:center">Сентябрь<small class="tnum">17 ч 27 мин · 24 дня</small></div><button class="nav-btn">'+ic('i-chev-r')+'</button></div>';

  function bg(kind,sel){
    if(kind==='run') return '<div class="app-scroll">'+path+running+remind+win+'</div>'+stop+tabbar(true);
    if(kind==='cal') return '<div class="app-scroll">'+calHead+cal(sel)+'</div>'+start+tabbar(false);
    return '<div class="app-scroll">'+path+today+remind+win+'</div>'+start+tabbar(false);
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-bg]'),function(el){el.innerHTML=bg(el.getAttribute('data-bg'),+el.getAttribute('data-sel')||0)});
  Array.prototype.forEach.call(document.querySelectorAll('[data-cal]'),function(el){el.innerHTML=cal(+el.getAttribute('data-cal')||0)});
})();
