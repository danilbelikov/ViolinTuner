#!/usr/bin/env python3
"""Pulls the text of a mockup page into markdown: screens, states, numbered notes, blocks and tables.
Usage: python3 extract.py ../mockups/live.html > live.md"""
import sys, re, html
from html.parser import HTMLParser

VOID={'img','input','br','hr','meta','link','source','wbr','area','col','embed','track','use','path','circle','rect'}

class P(HTMLParser):
    def __init__(s):
        super().__init__(); s.out=[]; s.stack=[]; s.buf=''; s.mode=None; s.skip=0
        s.row=[]; s.cells=None; s.cell=''; s.in_phone=0; s.tabs=None
    def flush(s):
        t=re.sub(r'\s+',' ',s.buf).strip(); s.buf=''; return t
    def handle_starttag(s,tag,a):
        a=dict(a); cls=a.get('class','') or ''
        if tag in('style','script','svg'): s.skip+=1
        if s.skip: return
        if tag in VOID: return
        if 'phone' in cls.split() and tag=='div': s.in_phone+=1; s.states=[]; s.stack.append('PHONE'); return
        if s.in_phone:
            s.stack.append(tag)
            if a.get('data-state'): s.states.append(a['data-state'])
            return
        s.stack.append(tag)
        if tag=='article' and 'screen' in cls: s.states=[]
        if tag in('h1','h2','h3','p','li','td','th','summary'): s.buf=''
        if tag=='tr': s.cells=[]
        if tag=='li' and a.get('data-n'): s.buf=f"**{a['data-n']}.** "
        if tag in('br','span'): s.buf+=' '
    def handle_endtag(s,tag):
        if tag in('style','script','svg'): s.skip-=1; return
        if s.skip: return
        if tag in VOID: return
        if s.in_phone:
            t=s.stack.pop() if s.stack else None
            if t=='PHONE':
                s.in_phone-=1
                if s.states: s.out.append('_Состояния на макете:_ '+' · '.join(s.states)+'\n')
            return
        if s.stack: s.stack.pop()
        if tag=='h1': s.out.append('# '+s.flush()+'\n')
        elif tag=='h2': s.out.append('\n## '+s.flush()+'\n')
        elif tag=='h3': s.out.append('\n### '+s.flush()+'\n')
        elif tag=='p': t=s.flush(); t and s.out.append(t+'\n')
        elif tag=='li': t=s.flush(); t and s.out.append('- '+t)
        elif tag in('td','th'): 
            if s.cells is not None: s.cells.append(s.flush().replace('|','/'))
        elif tag=='tr':
            if s.cells:
                if not s.row: s.out.append('\n| '+' | '.join(s.cells)+' |\n|'+'---|'*len(s.cells)); s.row=[1]
                else: s.out.append('| '+' | '.join(s.cells)+' |')
            s.cells=None
        elif tag=='table': s.row=[]; s.out.append('')
        elif tag in('ul','ol'): s.out.append('')
    def handle_data(s,d):
        if s.skip or s.in_phone: return
        s.buf+=d
p=P(); p.feed(open(sys.argv[1],encoding='utf-8').read())
txt='\n'.join(p.out)
txt=re.sub(r'\n{3,}','\n\n',txt)
print(txt)
