import base64,sys
s=open('src.html').read()
def d(p): return 'data:image/jpeg;base64,'+base64.b64encode(open(p,'rb').read()).decode()
s=s.replace('%%ROOM%%',d('room.jpg')).replace('%%NOW3%%',d('now3.jpg')).replace('%%NOW4%%',d('now4.jpg'))
s=s.replace('%%CONTRAST%%','основной текст 14.7 : 1, вторичный 6.5 : 1 на карточке, подписи дней 4.7 : 1, надпись кнопки 6.7–11.8 : 1 по всему перламутру, чип серии 7.4 : 1, число на самом светлом тоне календаря 7.2 : 1.')
open('/Users/danil/AndroidStudioProjects/ViolinTuner/docs/redesign/mockups/practice.html','w').write(s)
