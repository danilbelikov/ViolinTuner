// Фото нот (бумага, системы, ноты) и нарисованная гамма — чтобы макет не был из заглушек.
(function(){
  function rnd(seed){var s=seed*9301+49297;return function(){s=(s*9301+49297)%233280;return s/233280}}
  function sheet(seed,big){
    var W=big?360:76,H=big?510:98,r=rnd(seed),o='<svg viewBox="0 0 '+W+' '+H+'" xmlns="http://www.w3.org/2000/svg"><rect width="'+W+'" height="'+H+'" fill="#F1ECE1"/>';
    var k=W/360, top=big?44:10;
    if(big){o+='<text x="180" y="26" text-anchor="middle" font-family="Georgia,serif" font-size="15" fill="#2a2522">Concerto in A minor</text><text x="340" y="38" text-anchor="end" font-family="Georgia,serif" font-size="9" fill="#2a2522">A. Vivaldi</text>';}
    var systems=big?8:8, gap=(H-top-8)/systems;
    for(var sy=0;sy<systems;sy++){
      var y0=top+sy*gap, sp=gap*0.09;
      for(var l=0;l<5;l++){var y=y0+l*sp;o+='<line x1="'+(12*k)+'" y1="'+y+'" x2="'+(W-12*k)+'" y2="'+y+'" stroke="#3b3430" stroke-width="'+(big?0.7:0.35)+'"/>';}
      var bars=4;for(var b=0;b<=bars;b++){var x=12*k+(W-24*k)*b/bars;o+='<line x1="'+x+'" y1="'+y0+'" x2="'+x+'" y2="'+(y0+4*sp)+'" stroke="#3b3430" stroke-width="'+(big?0.8:0.4)+'"/>';}
      var n=big?28:24;for(var i=0;i<n;i++){
        var x=(30+i*(320/n))*k+r()*2*k, y=y0+(r()*10-2)*sp/2, rx=sp*0.62;
        o+='<ellipse cx="'+x+'" cy="'+y+'" rx="'+rx+'" ry="'+(rx*0.72)+'" fill="#221d1a" transform="rotate(-20 '+x+' '+y+')"/>';
        if(big) o+='<line x1="'+(x+rx*0.9)+'" y1="'+y+'" x2="'+(x+rx*0.9)+'" y2="'+(y-sp*3.2)+'" stroke="#221d1a" stroke-width="0.8"/>';
      }
    }
    return o+'</svg>';
  }
  function scale(systems,sharps){
    var W=320,sysH=62,H=systems*sysH+10,o='<svg viewBox="0 0 '+W+' '+H+'" xmlns="http://www.w3.org/2000/svg">';
    var steps=[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14], down=[13,12,11,10,9,8,7,6,5,4,3,2,1,0];
    var seq=steps.concat(down), per=Math.ceil(seq.length/systems);
    for(var s=0;s<systems;s++){
      var y0=12+s*sysH, sp=6;
      for(var l=0;l<5;l++){var y=y0+l*sp;o+='<line x1="8" y1="'+y+'" x2="'+(W-8)+'" y2="'+y+'" stroke="#8A8699" stroke-width="0.8"/>';}
      o+='<text x="10" y="'+(y0+27)+'" font-family="Apple Symbols,Noto Music,serif" font-size="40" fill="#E6E4EE">𝄞</text>';
      if(sharps) o+='<text x="40" y="'+(y0+8)+'" font-family="Apple Symbols,serif" font-size="15" fill="#E6E4EE">♯</text>';
      var chunk=seq.slice(s*per,(s+1)*per);
      chunk.forEach(function(st,i){
        var x=62+i*((W-80)/per), y=y0+4*sp+(5-st)*sp/2; // G3 = 0, E4 — нижняя линейка
        // добавочные линейки
        for(var ly=y0+5*sp; ly<=y+0.1; ly+=sp) o+='<line x1="'+(x-7)+'" y1="'+ly+'" x2="'+(x+7)+'" y2="'+ly+'" stroke="#8A8699" stroke-width="0.8"/>';
        for(var ly2=y0-sp; ly2>=y-0.1; ly2-=sp) o+='<line x1="'+(x-7)+'" y1="'+ly2+'" x2="'+(x+7)+'" y2="'+ly2+'" stroke="#8A8699" stroke-width="0.8"/>';
        o+='<ellipse cx="'+x+'" cy="'+y+'" rx="4.3" ry="3.2" fill="#E6E4EE" transform="rotate(-20 '+x+' '+y+')"/>';
        var up=y>y0+2*sp; o+='<line x1="'+(up?x+4:x-4)+'" y1="'+y+'" x2="'+(up?x+4:x-4)+'" y2="'+(up?y-19:y+19)+'" stroke="#E6E4EE" stroke-width="1"/>';
      });
      if(s===systems-1){o+='<line x1="'+(W-12)+'" y1="'+y0+'" x2="'+(W-12)+'" y2="'+(y0+4*sp)+'" stroke="#E6E4EE" stroke-width="1"/><line x1="'+(W-8)+'" y1="'+y0+'" x2="'+(W-8)+'" y2="'+(y0+4*sp)+'" stroke="#E6E4EE" stroke-width="2.5"/>';}
    }
    return o+'</svg>';
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-sheet]'),function(el){el.insertAdjacentHTML('afterbegin',sheet(+el.getAttribute('data-sheet'),el.hasAttribute('data-big')))});
  Array.prototype.forEach.call(document.querySelectorAll('[data-scale]'),function(el){el.innerHTML=scale(+el.getAttribute('data-scale'),el.hasAttribute('data-sharp'))});
})();
