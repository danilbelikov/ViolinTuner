import base64,re
S='/private/tmp/claude-501/-Users-danil-AndroidStudioProjects-ViolinTuner/56aae823-2298-4947-86c2-1cd8e46905ed/scratchpad/'
src=open(S+'src.html').read()
head=src[:src.index('</style>')]
head=head.replace('<title>Violin Journey · Занятия</title>','<title>Листы Занятий</title>')
svg=src[src.index('<svg width="0" height="0"'):src.index('</svg>')+6]
scr=src[src.index('<script>'):src.index('</script>')]
tog=scr[scr.index('(function(){\n  var root='):]
css=open(S+'sheets/extra.css').read()
room='data:image/jpeg;base64,'+base64.b64encode(open(S+'room.jpg','rb').read()).decode()
css=css.replace('%%ROOM%%',room)
body=open(S+'sheets/body.html').read()
body=body.replace('%%CONTRAST%%',open(S+'sheets/contrast.txt').read().strip())
out=head+css+'</style>\n</head>\n<body>\n'+svg+'\n'+open(S+'sheets/sym.svg').read()+'\n'+body+'\n<script>\n'+open(S+'sheets/app.js').read()+'\n'+tog+'</script>\n</body>\n</html>\n'
open('/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/practice-sheets.html','w').write(out)
print(len(out))
