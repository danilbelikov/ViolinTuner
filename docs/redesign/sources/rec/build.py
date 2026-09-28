import base64
s=open('src.html').read()
def d(p): return 'data:image/jpeg;base64,'+base64.b64encode(open(p,'rb').read()).decode()
s=s.replace('%%CSS%%',open('base.css').read()).replace('%%SVG%%',open('svg.html').read()).replace('%%SCRIPT%%',open('script.html').read())
s=s.replace('%%REC_TOP%%',d('rec_top.jpg')).replace('%%REC_LIST%%',d('rec_list.jpg'))
s=s.replace('«в строе» #6FD39B — 9.3 : 1','«в строе» #6FD39B — 9.1 : 1').replace('— 5.7 : 1.</li>','— 5.8 : 1.</li>',1)
open('/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/records.html','w').write(s)
