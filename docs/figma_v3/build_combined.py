# -*- coding: utf-8 -*-
"""Une todos los mockups en un solo SVG (por secciones) para pegarlo de una vez en Figma."""
import json, os, re, xml.dom.minidom
BASE = os.path.dirname(os.path.abspath(__file__)); D = os.path.join(BASE, 'svg')
m = json.load(open(os.path.join(D, 'manifest.json'), encoding='utf-8'))
def inner(svg): return re.sub(r'^<svg[^>]*>|</svg>$', '', svg.strip(), flags=re.S)
def label(x, y, s, size, w, fill): return f'<text x="{x}" y="{y}" font-family="Roboto" font-size="{size}" font-weight="{w}" fill="{fill}">{s}</text>'
GAP = 72; parts = []; y = 0; maxw = 1200
for e in m:
    if e['section'] != 'Portada': continue
    parts.append(f'<g id="{e["name"]}" transform="translate(0 {y})">{inner(open(os.path.join(D, e["file"]), encoding="utf-8").read())}</g>'); y += e['h'] + GAP
secs = []
for e in m:
    if e['section'] != 'Portada' and e['section'] not in secs: secs.append(e['section'])
for s in secs:
    items = [e for e in m if e['section'] == s]; g = [label(0, 36, s, 28, 700, '#1F3A5F')]; x = 0
    for e in items:
        g.append(label(x, 84, e['name'], 16, 500, '#1F3A5F')); g.append(label(x, 106, e['desc'][:70].replace('&', '&amp;'), 12, 400, '#5D6D7E'))
        g.append(f'<g id="{e["name"]}" transform="translate({x} 120)">{inner(open(os.path.join(D, e["file"]), encoding="utf-8").read())}</g>'); x += e['w'] + GAP
    maxw = max(maxw, x - GAP)
    parts.append(f'<g id="{s}" transform="translate(0 {y})">{"".join(g)}</g>'); y += 120 + 917 + GAP * 2
svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{maxw}" height="{y}" viewBox="0 0 {maxw} {y}"><g id="EnZona v3 · Mockups">{"".join(parts)}</g></svg>'
xml.dom.minidom.parseString(svg.encode('utf-8'))
open(os.path.join(BASE, 'enzona_v3_mockups_todo.svg'), 'w', encoding='utf-8').write(svg)
print('combinado', len(svg), 'bytes', maxw, 'x', y)
