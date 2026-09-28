st=open('base_style.txt').read().replace('</style>',open('extra.css').read()+'</style>')
svg=open('base_svg.txt').read()+'\n'+open('extra_svg.txt').read()
scr=open('base_script.txt').read()
a=scr.find('(function(){\n  var tones')
if a>=0:
    b=scr.find('})();',a)+5; scr=scr[:a]+scr[b:]
body=open('body.html').read()
SB='<div class="statusbar"><span>18:42</span><span class="sb-icons"><svg class="ic ic-s"><use href="#i-sb-signal"/></svg><svg class="ic" style="width:26px"><use href="#i-sb-battery"/></svg></span></div>'
body=body.replace('%%SB%%',SB)
def L(h):
    h=h.lstrip('#'); c=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    c=[x/12.92 if x<=0.03928 else ((x+0.055)/1.055)**2.4 for x in c]
    return .2126*c[0]+.7152*c[1]+.0722*c[2]
def cr(a,b):
    a,b=L(a),L(b); return '%.1f : 1'%((max(a,b)+.05)/(min(a,b)+.05))
body=body.replace('%%CONTRAST%%','основной текст на фоне %s, вторичный на карточке %s, подписи групп %s, акцент на мягком акценте (выбранный вариант, иконка копии) %s, надпись главной кнопки %s, надпись опасной кнопки (#3D1408 на #FFB199) %s, «Заменить» в диалоге (#FFB199 на #292833) %s, «Пропустить» на самом светлом месте неба %s.'%(cr('E6E4EE','131318'),cr('A39FB5','1E1D25'),cr('A39FB5','131318'),cr('C4ADFF','2A2352'),cr('2E1A6E','C4ADFF'),cr('3D1408','FFB199'),cr('FFB199','292833'),cr('E6E4EE','7A4E86')))
head='''<!doctype html>
<html lang="ru" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Violin Journey · Знакомство и настройки</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap" rel="stylesheet">
'''
out=head+st+'\n</head>\n<body>\n'+svg+'\n'+body+'\n'+open('scene.js').read()+'\n'+scr+'\n</body>\n</html>\n'
open('/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/start.html','w').write(out)
