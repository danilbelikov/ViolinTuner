import base64
R='/private/tmp/claude-501/-Users-danil-AndroidStudioProjects-ViolinTuner/56aae823-2298-4947-86c2-1cd8e46905ed/scratchpad/rep/'
st=open(R+'style.css').read()+open(R+'extra.css').read()
sv=open(R+'svg.txt').read()+open(R+'symbols.txt').read()+'</svg>\n'
body=open(R+'body.html').read()
SB='<div class="statusbar"><span>18:42</span><span class="sb-icons"><svg class="ic ic-s"><use href="#i-sb-signal"/></svg><svg class="ic" style="width:26px"><use href="#i-sb-battery"/></svg></span></div>'
TABS='''<nav class="tabbar">
                <div class="tab"><div class="pill"><svg class="ic"><use href="#i-watch"/></svg></div>Занятия</div>
                <div class="tab"><div class="pill"><svg class="ic"><use href="#i-string"/></svg></div>Live</div>
                <div class="tab is-active"><div class="pill"><svg class="ic"><use href="#i-sheet"/></svg></div>Репертуар</div>
                <div class="tab"><div class="pill"><svg class="ic"><use href="#i-tape"/></svg></div>Записи</div>
              </nav>'''
body=body.replace('{{SB}}',SB).replace('{{TABS}}',TABS)
for n in ['rep_top','rep_section','piece_empty','piece_form']:
    body=body.replace('%%IMG_'+n+'%%','data:image/jpeg;base64,'+base64.b64encode(open(R+n+'.jpg','rb').read()).decode())
body=body.replace('%%CONTRAST%%',open(R+'contrast.txt').read().strip() if __import__('os').path.exists(R+'contrast.txt') else '…')
html='''<!doctype html>
<html lang="ru" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Violin Journey · Репертуар</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap" rel="stylesheet">
<style>'''+st+'''</style>
</head>
<body>
'''+sv+body+'<script>'+open(R+'art.js').read()+'</script>\n<script>\n'+open(R+'script.js').read()+'</script>\n</body>\n</html>\n'
open('/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/repertoire.html','w').write(html)
print(len(html))
