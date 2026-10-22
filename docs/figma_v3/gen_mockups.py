# -*- coding: utf-8 -*-
"""Genera los mockups SVG de EnZona v3 (marco Android 412x917) con la paleta v2."""
import os, random, json
from xml.sax.saxutils import escape

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "svg")
W, H = 412, 917

# ---------------------------------------------------------------- paleta v2
C = dict(
    marino='#1F3A5F', enzona='#2E86AB', profundo='#24708F', degIni='#274C78',
    coral='#F2643B', coralOsc='#D64E2A', coralSuave='#FDE9E2',
    t50='#EAF0F7', t100='#D7E1EE', t200='#B9C9DD',
    tinta='#1B2432', gris='#5D6D7E', suave='#9AA6B6', linea='#D9DFEA',
    fondo='#EEF1F6', sup='#FFFFFF', supBaja='#F6F8FB', supAlta='#E6EBF3',
    exito='#2E9E5B', exitoC='#E5F3EC', sobreExito='#1E7A45',
    aviso='#F1B434', avisoC='#FBF0D5',
    error='#C0392B', errorC='#F9E3E0', sobreError='#8E2A1F',
    info='#2E86AB', infoC='#E3F0F6', sobreInfo='#1B4F63', blanco='#FFFFFF',
)

# ---------------------------------------------------------------- iconos (Material, 24 dp)
ICON = {
 'back': "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
 'home': "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
 'person': "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
 'place': "M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z",
 'search': "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
 'calendar': "M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11z",
 'clock': "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z",
 'edit': "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
 'warning': "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z",
 'info': "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z",
 'error': "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z",
 'cancel': "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z",
 'group': "M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z",
 'ticket': "M22 10V6c0-1.11-.9-2-2-2H4c-1.1 0-1.99.89-1.99 2v4c1.1 0 1.99.9 1.99 2s-.89 2-2 2v4c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2v-4c-1.1 0-2-.9-2-2s.9-2 2-2zm-9 7.5h-2v-2h2v2zm0-4.5h-2v-2h2v2zm0-4.5h-2v-2h2v2z",
 'mylocation': "M12 8c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4zm8.94 3c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06C6.83 3.52 3.52 6.83 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c4.17-.46 7.48-3.77 7.94-7.94H23v-2h-2.06zM12 19c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
 'shield': "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z",
 'chevron': "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z",
 'dropdown': "M7 10l5 5 5-5z",
 'check': "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z",
 'music': "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z",
 'school': "M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3L1 9l11 6 11-6-11-6z",
 'seat': "M4 18v3h3v-3h10v3h3v-6H4zm15-8h3v3h-3zM2 10h3v3H2zm15 3H7V5c0-1.1.9-2 2-2h6c1.1 0 2 .9 2 2v8z",
 'card': "M20 4H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V6c0-1.11-.89-2-2-2zm0 14H4v-6h16v6zm0-10H4V6h16v2z",
 'globe': "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z",
 'bell': "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
 'personadd': "M15 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm-9-2V7H4v3H1v2h3v3h2v-3h3v-2H6zm9 4c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z",
 'cloudoff': "M19.35 10.04C18.67 6.59 15.64 4 12 4c-1.48 0-2.85.43-4.01 1.17l1.46 1.46C10.21 6.23 11.08 6 12 6c3.04 0 5.5 2.46 5.5 5.5v.5H19c1.66 0 3 1.34 3 3 0 1.13-.64 2.11-1.56 2.62l1.45 1.45C23.16 18.16 24 16.68 24 15c0-2.64-2.05-4.78-4.65-4.96zM3 5.27l2.75 2.74C2.56 8.15 0 10.77 0 14c0 3.31 2.69 6 6 6h11.73l2 2L21 20.73 4.27 4 3 5.27zM7.73 10l8 8H6c-2.21 0-4-1.79-4-4s1.79-4 4-4h1.73z",
 'qrscan': "M9.5 6.5v3h-3v-3h3M11 5H5v6h6V5zm-1.5 9.5v3h-3v-3h3M11 13H5v6h6v-6zm6.5-6.5v3h-3v-3h3M19 5h-6v6h6V5zm-6 8h1.5v1.5H13V13zm1.5 1.5H16V16h-1.5v-1.5zM16 13h1.5v1.5H16V13zm-3 3h1.5v1.5H13V16zm1.5 1.5H16V19h-1.5v-1.5zM16 16h1.5v1.5H16V16zm1.5-1.5H19V16h-1.5v-1.5zm0 3H19V19h-1.5v-1.5zM22 7h-2V4h-3V2h5v5zm0 15v-5h-2v3h-3v2h5zM2 22h5v-2H4v-3H2v5zM2 2v5h2V4h3V2H2z",
 'campaign': "M18 11v2h4v-2h-4zm-2 6.61c.96.71 2.21 1.65 3.2 2.39.4-.53.8-1.07 1.2-1.6-.99-.74-2.24-1.68-3.2-2.4-.4.54-.8 1.08-1.2 1.61zM20.4 5.6c-.4-.53-.8-1.07-1.2-1.6-.99.74-2.24 1.68-3.2 2.4.4.53.8 1.07 1.2 1.6.96-.72 2.21-1.65 3.2-2.4zM4 9c-1.1 0-2 .9-2 2v2c0 1.1.9 2 2 2h1v4h2v-4h1l5 3V6L8 9H4zm11.5 3c0-1.33-.58-2.53-1.5-3.35v6.69c.92-.81 1.5-2.01 1.5-3.34z",
 'gps_off': "M20.94 11c-.46-4.17-3.77-7.48-7.94-7.94V1h-2v2.06c-1.13.12-2.19.46-3.16.97l1.5 1.5C10.16 5.19 11.06 5 12 5c3.87 0 7 3.13 7 7 0 .94-.19 1.84-.52 2.65l1.5 1.5c.5-.96.84-2.02.97-3.15H23v-2h-2.06zM3 4.27l2.04 2.04C3.97 7.62 3.25 9.23 3.06 11H1v2h2.06c.46 4.17 3.77 7.48 7.94 7.94V23h2v-2.06c1.77-.2 3.38-.91 4.69-1.98L19.73 21 21 19.73 4.27 3 3 4.27zm13.27 13.27C15.09 18.45 13.61 19 12 19c-3.87 0-7-3.13-7-7 0-1.61.55-3.09 1.46-4.27l9.81 9.81z",
 'backspace': "M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-3 12.59L17.59 17 14 13.41 10.41 17 9 15.59 12.59 12 9 8.41 10.41 7 14 10.59 17.59 7 19 8.41 15.41 12 19 15.59z",
 'menu': "M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-5h18V6H3v2z",
 'smile': "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm3.5-9c.83 0 1.5-.67 1.5-1.5S16.33 8 15.5 8 14 8.67 14 9.5s.67 1.5 1.5 1.5zm-7 0c.83 0 1.5-.67 1.5-1.5S9.33 8 8.5 8 7 8.67 7 9.5 7.67 11 8.5 11zm3.5 6.5c2.33 0 4.31-1.46 5.11-3.5H6.89c.8 2.04 2.78 3.5 5.11 3.5z",
 'tabnext': "M11.78 12 6.63 6.85l1.06-1.06L13.9 12l-6.21 6.21-1.06-1.06L11.78 12z",
 'enter': "M19 7v4H5.83l3.58-3.59L8 6l-6 6 6 6 1.41-1.41L5.83 13H21V7z",
}

def esc(s): return escape(str(s), {'"': '&quot;'})

# ---------------------------------------------------------------- primitivas
def rect(x, y, w, h, fill, rx=0, stroke=None, sw=1, op=None, name=None):
    a = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}"'
    if stroke: a += f' stroke="{stroke}" stroke-width="{sw}"'
    if op is not None: a += f' opacity="{op}"'
    if name: a += f' id="{esc(name)}"'
    return a + '/>'

def circle(cx, cy, r, fill, stroke=None, sw=1, op=None):
    a = f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"'
    if stroke: a += f' stroke="{stroke}" stroke-width="{sw}"'
    if op is not None: a += f' opacity="{op}"'
    return a + '/>'

def line(x1, y1, x2, y2, stroke, sw=1, cap='butt'):
    return f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" stroke-width="{sw}" stroke-linecap="{cap}"/>'

def text(x, y, s, size=14, weight=400, fill=None, anchor='start', ls=None, name=None):
    fill = fill or C['tinta']
    a = f'<text x="{x}" y="{y}" font-family="Roboto" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}"'
    if ls is not None: a += f' letter-spacing="{ls}"'
    if name: a += f' id="{esc(name)}"'
    return a + f'>{esc(s)}</text>'

def tw(s, size, weight=400):
    """Ancho estimado del texto (Roboto)."""
    k = 0.50 if weight < 500 else (0.53 if weight < 700 else 0.56)
    return len(s) * size * k

def wrap(s, width, size, weight=400):
    words, lines, cur = s.split(' '), [], ''
    for w in words:
        t = (cur + ' ' + w).strip()
        if tw(t, size, weight) <= width or not cur: cur = t
        else: lines.append(cur); cur = w
    if cur: lines.append(cur)
    return lines

def para(x, y, s, width, size=14, weight=400, fill=None, lh=None, anchor='start'):
    lh = lh or round(size * 1.4)
    out, yy = [], y
    for ln in wrap(s, width, size, weight):
        out.append(text(x, yy, ln, size, weight, fill, anchor)); yy += lh
    return ''.join(out), yy - lh

def icon(name, x, y, size=24, fill=None):
    fill = fill or C['tinta']
    return f'<g transform="translate({x} {y}) scale({size/24:.4f})"><path d="{ICON[name]}" fill="{fill}"/></g>'

def g(name, *parts):
    return f'<g id="{esc(name)}">' + ''.join(parts) + '</g>'

# ---------------------------------------------------------------- componentes
def status_bar(t, dark=False):
    fg = C['blanco'] if dark else C['tinta']
    return g('Barra de estado',
        text(16, 30, t, 14, 500, fg),
        ''.join(rect(340 + i*4, 31 - (i+1)*2.5, 2.5, (i+1)*2.5, fg) for i in range(4)),
        f'<path d="M362 24a9 9 0 0 1 12 0l-1.5 1.7a6.8 6.8 0 0 0-9 0zM365 27.2a5 5 0 0 1 6 0l-1.5 1.7a2.8 2.8 0 0 0-3 0zM368 30.6l1.5 1.6-1.5 1.6-1.5-1.6z" fill="{fg}"/>',
        rect(380, 21, 18, 10, 'none', 2.5, fg, 1.5), rect(382, 23, 14, 6, fg, 1), rect(398.5, 24, 1.5, 4, fg, 0.5))

def nav_handle():
    return rect(156, 905, 100, 4, C['tinta'], 2, name='Indicador de navegación')

def top_bar(title, y=68, action=None, dark=False):
    fg = C['blanco'] if dark else C['tinta']
    p = [icon('back', 16, y, 24, fg)]
    if title: p.append(text(52, y + 18, title, 20, 500, fg))
    if action: p.append(icon(action, 372, y, 24, C['coralOsc']))
    return g('Barra superior', *p)

def bottom_nav(items, active):
    n = len(items); cw = W / n
    p = [rect(0, 837, W, 80, C['sup']), line(0, 837, W, 837, C['linea'])]
    for i, (ic, lb) in enumerate(items):
        cx = cw * i + cw / 2
        if i == active:
            p.append(rect(cx - 28, 848, 56, 32, C['marino'], 16))
            p.append(icon(ic, cx - 12, 852, 24, C['blanco']))
            p.append(text(cx, 897, lb, 12, 500, C['marino'], 'middle'))
        else:
            p.append(icon(ic, cx - 12, 852, 24, C['gris']))
            p.append(text(cx, 897, lb, 12, 500, C['gris'], 'middle'))
    return g('Barra inferior', *p)

NAV_ASIST = [('home', 'Inicio'), ('ticket', 'Mis boletos'), ('person', 'Perfil')]
NAV_ORG = [('home', 'Inicio'), ('ticket', 'Mis boletos'), ('calendar', 'Mis eventos'), ('person', 'Perfil')]
NAV_VAL = [('qrscan', 'Escanear'), ('person', 'Perfil')]
NAV_ADM = [('home', 'Inicio'), ('ticket', 'Mis boletos'), ('shield', 'Admin'), ('person', 'Perfil')]

def card(x, y, w, h, fill=None, rx=20, stroke=None):
    return rect(x, y, w, h, fill or C['sup'], rx, stroke)

def field(x, y, w, h, label, value=None, icon_name=None, helper=None, focused=False, floating=False):
    p = [rect(x, y, w, h, C['sup'], 12, C['marino'] if focused else C['suave'], 2 if focused else 1)]
    tx = x + 16
    if icon_name:
        p.append(icon(icon_name, x + 14, y + h/2 - 10, 20, C['gris'])); tx = x + 44
    if floating:
        p.append(rect(x + 12, y - 8, tw(label, 12) + 8, 14, C['sup']))
        p.append(text(x + 16, y + 3, label, 12, 400, C['marino'] if focused else C['gris']))
        if value: p.append(text(tx, y + h/2 + 6, value, 16, 400, C['tinta']))
    else:
        if value: p.append(text(tx, y + h/2 + 6, value, 16, 400, C['tinta']))
        else: p.append(text(tx, y + h/2 + 6, label, 16, 400, C['gris']))
    if helper: p.append(text(x + 16, y + h + 16, helper, 12, 400, C['gris']))
    return g('Campo · ' + label, *p)

def btn(x, y, w, h, label, bg, fg, weight=500, size=15, outline=None):
    p = [rect(x, y, w, h, bg, h/2, outline, 1)]
    if label: p.append(text(x + w/2, y + h/2 + 5, label, size, weight, fg, 'middle'))
    return g('Botón · ' + (label or 'icono'), *p)

def chip(x, y, label, selected=False, icon_name=None, w=None, dropdown=False):
    ww = w or (tw(label, 13, 500) + 28 + (22 if icon_name else 0) + (18 if dropdown else 0))
    bg = C['t100'] if selected else C['sup']
    p = [rect(x, y, ww, 34, bg, 10, None if selected else C['suave'], 1)]
    tx = x + 14
    if icon_name: p.append(icon(icon_name, x + 12, y + 8, 18, C['marino'])); tx += 22
    p.append(text(tx, y + 22, label, 13, 500, C['marino']))
    if dropdown: p.append(icon('dropdown', x + ww - 26, y + 6, 22, C['marino']))
    return g('Chip · ' + label, *p), ww

def switch(x, y, on=False, disabled=False):
    if on:
        p = [rect(x, y, 52, 32, C['marino'], 16), circle(x + 36, y + 16, 12, C['blanco'])]
    else:
        col = C['t200'] if disabled else C['supAlta']
        p = [rect(x, y, 52, 32, col, 16, C['suave'], 2), circle(x + 16, y + 16, 8, C['gris'])]
    return g('Interruptor', *p)

def scrim(): return rect(0, 0, W, H, '#000000', 0, op=0.42, name='Scrim')

def dialog(x, y, w, h):
    return rect(x, y, w, h, C['sup'], 28, name='Fondo del diálogo')

def banner(x, y, w, h, kind, title, body, icon_name=None):
    bg, fg = {'aviso': (C['avisoC'], C['marino']), 'error': (C['errorC'], C['sobreError']),
              'info': (C['infoC'], C['sobreInfo']), 'exito': (C['exitoC'], C['sobreExito'])}[kind]
    ic = icon_name or {'aviso': 'warning', 'error': 'error', 'info': 'info', 'exito': 'check'}[kind]
    p = [rect(x, y, w, h, bg, 12), icon(ic, x + 14, y + 14, 22, fg)]
    yy = y + 30
    if title:
        p.append(text(x + 44, yy, title, 14, 500, fg)); yy += 20
    t, _ = para(x + 44, yy, body, w - 60, 13, 400, fg, 18)
    p.append(t)
    return g('Aviso · ' + (title or kind), *p)

def status_pill(x, y, label, kind, right=False):
    bg, fg = {'error': (C['errorC'], C['sobreError']), 'exito': (C['exitoC'], C['sobreExito']),
              'neutro': (C['supAlta'], C['gris'])}[kind]
    ww = tw(label, 12, 500) + 20
    xx = x - ww if right else x
    return g('Estado · ' + label, rect(xx, y, ww, 24, bg, 12), text(xx + ww/2, y + 16, label, 12, 500, fg, 'middle')), ww

def map_art(x, y, w, h, seed=3, marker=None, zoom=False, rx=16):
    """Mapa estilizado (OpenStreetMap) con calles, parques y marcador."""
    rnd = random.Random(seed)
    cid = f'clip{seed}_{x}_{y}'
    p = [f'<clipPath id="{cid}"><rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}"/></clipPath>',
         f'<g clip-path="url(#{cid})">', rect(x, y, w, h, '#F2EFE9')]
    for i in range(4):
        px, py = round(x + rnd.uniform(0, w)), round(y + rnd.uniform(0, h))
        pw, ph = round(rnd.uniform(40, 110)), round(rnd.uniform(30, 80))
        p.append(rect(px, py, pw, ph, '#CDEBB0', 8))
    for i in range(10):
        px, py = round(x + rnd.uniform(0, w)), round(y + rnd.uniform(0, h))
        p.append(rect(px, py, round(rnd.uniform(14, 30)), round(rnd.uniform(10, 22)), '#E0DCD5', 2))
    for i in range(1, 7):
        p.append(line(x, round(y + h * i / 7 + rnd.uniform(-8, 8)), x + w, round(y + h * i / 7 + rnd.uniform(-8, 8)), '#FFFFFF', 3))
    for i in range(1, 8):
        p.append(line(round(x + w * i / 8 + rnd.uniform(-8, 8)), y, round(x + w * i / 8 + rnd.uniform(-8, 8)), y + h, '#FFFFFF', 3))
    p.append(f'<path d="M{x} {round(y + h*0.62)} Q{round(x + w*0.4)} {round(y + h*0.45)} {x + w} {round(y + h*0.55)}" fill="none" stroke="#F6D3A8" stroke-width="7"/>')
    p.append(f'<path d="M{round(x + w*0.7)} {y} Q{round(x + w*0.6)} {round(y + h*0.5)} {round(x + w*0.78)} {y + h}" fill="none" stroke="#E892A2" stroke-width="5"/>')
    p.append('</g>')
    if marker:
        mx, my = marker
        p.append(f'<g id="Marcador" transform="translate({mx - 14} {my - 40}) scale(1.1667)">'
                 f'<path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="{C["exito"]}" stroke="{C["sobreExito"]}" stroke-width="1"/>'
                 f'<circle cx="12" cy="9" r="3" fill="#FFFFFF"/></g>')
    if zoom:
        p.append(rect(x + w/2 - 40, y + h - 46, 80, 34, C['sup'], 17, C['linea'], 1))
        p.append(rect(x + w/2 - 26, y + h - 30, 14, 2.5, C['tinta']))
        p.append(rect(x + w/2 + 12, y + h - 30, 14, 2.5, C['tinta'])); p.append(rect(x + w/2 + 17.75, y + h - 36, 2.5, 14, C['tinta']))
    p.append(text(x + 8, y + h - 8, '© OpenStreetMap contributors', 10, 400, C['tinta']))
    return g('Mapa', *p)

def qr(x, y, size, modules=25, seed=7, fill=None):
    fill = fill or C['tinta']
    rnd = random.Random(seed); m = size / modules
    p = []
    def finder(cx, cy):
        p.append(rect(round(x + cx*m, 2), round(y + cy*m, 2), round(7*m, 2), round(7*m, 2), fill))
        p.append(rect(round(x + (cx+1)*m, 2), round(y + (cy+1)*m, 2), round(5*m, 2), round(5*m, 2), C['blanco']))
        p.append(rect(round(x + (cx+2)*m, 2), round(y + (cy+2)*m, 2), round(3*m, 2), round(3*m, 2), fill))
    for r in range(modules):
        for c in range(modules):
            if (r < 8 and c < 8) or (r < 8 and c >= modules - 8) or (r >= modules - 8 and c < 8): continue
            if rnd.random() < 0.46: p.append(rect(round(x + c*m, 2), round(y + r*m, 2), round(m, 2), round(m, 2), fill))
    finder(0, 0); finder(modules - 7, 0); finder(0, modules - 7)
    return g('Código QR', *p)

def snackbar(msg, y=770):
    return g('Snackbar', rect(16, y, W - 32, 48, C['tinta'], 8), text(32, y + 29, msg, 14, 400, C['blanco']))

def frame(name, *parts, bg=None):
    body = ''.join(parts)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
            f'<g id="{esc(name)}">' + rect(0, 0, W, H, bg or C['fondo'], name='Fondo') + body + '</g></svg>')

# ---------------------------------------------------------------- pantallas reutilizables
BOLETOS = [('EZ-5071-6505-9838', 'Planta baja · Fila A · Asiento 5'), ('EZ-9963-2660-4656', 'Planta baja · Fila A · Asiento 6'), ('EZ-9952-6215-8549', 'Planta baja · Fila A · Asiento 7')]

def personal_acceso(y):
    return [card(16, y, 380, 300), icon('personadd', 32, y + 18, 22, C['coralOsc']), text(62, y + 35, 'Personal de acceso', 16, 500),
            para(32, y + 60, 'Quien designes podrá escanear los códigos QR en la puerta desde su propia cuenta.', 350, 13, 400, C['gris'], 18)[0],
            field(32, y + 102, 348, 52, 'Correo de la persona'),
            btn(32, y + 168, 348, 44, 'Designar como validador', C['t100'], C['marino']),
            text(32, y + 236, 'Designados', 12, 500, C['gris']), text(32, y + 260, 'Luis Puerta', 14, 400), text(32, y + 276, 'validador@uv.mx', 12, 400, C['gris']),
            text(380, y + 262, 'Quitar', 12, 500, C['coralOsc'], 'end')]

def screen_panel_evento(cancelado=False):
    p = [status_bar('3:57'), top_bar('Panel del evento', 112, action='edit')]
    for i, (v, l) in enumerate([('88', 'Asistentes'), ('0', 'Validados'), ('$10560', 'Ingresos')]):
        x = 16 + i * 130
        p += [rect(x, 150, 120, 52, C['supAlta'], 12), text(x + 60, 174, v, 18, 700, C['coralOsc'], 'middle'), text(x + 60, 194, l, 12, 400, C['gris'], 'middle')]
    p += [text(16, 232, 'Ocupación', 13, 400, C['gris']), text(396, 232, '88 / 250', 13, 400, C['gris'], 'end'),
          rect(16, 240, 380, 6, C['linea'], 3), rect(16, 240, 134, 6, C['coralOsc'], 3),
          text(16, 272, 'Asistencia validada', 13, 400, C['gris']), text(396, 272, '0 / 88', 13, 400, C['gris'], 'end'),
          rect(16, 280, 380, 6, C['linea'], 3)]
    p.append(text(16, 318, 'Boletos emitidos', 16, 500))
    for i, (code, seat) in enumerate(BOLETOS):
        y = 344 + i * 40
        p += [text(16, y, code, 13, 400), text(16, y + 16, seat, 11, 400, C['gris'])]
        p.append(text(396, y, 'Cancelado' if cancelado else 'Sin usar', 12, 500, C['coralOsc'] if cancelado else C['gris'], 'end'))
    p += personal_acceso(476)
    return p

def screen_nuevo_evento(t='4:00'):
    return [status_bar(t), top_bar('Nuevo evento', 68),
         field(16, 116, 380, 56, 'Nombre del evento'),
         rect(16, 188, 380, 100, C['sup'], 12, C['suave'], 1), text(32, 214, 'Descripción', 16, 400, C['gris']),
         card(16, 302, 380, 246), text(32, 330, 'Fecha y hora', 16, 500),
         field(32, 348, 210, 52, 'Fecha de inicio', icon_name='calendar'), field(254, 348, 126, 52, 'Hora', icon_name='clock'),
         text(32, 416, 'Elige la fecha en el calendario y la hora en formato 24 h.', 11.5, 400, C['gris']),
         field(32, 438, 210, 52, 'Fecha de fin', icon_name='calendar'), field(254, 438, 126, 52, 'Hora de fin', icon_name='clock'),
         text(32, 528, 'Opcional', 11.5, 400, C['gris']),
         card(16, 564, 380, 360), text(32, 592, 'Ubicación', 16, 500),
         field(32, 610, 348, 52, 'Lugar (nombre del recinto)'), field(32, 676, 348, 52, 'Dirección'),
         text(32, 744, 'Puedes editar la dirección sugerida por el mapa.', 11.5, 400, C['gris']),
         map_art(32, 760, 348, 160, seed=11)]

def screen_admin(t='4:05'):
    return [status_bar(t), text(16, 90, 'Administración', 24, 700), text(16, 112, 'Usuarios y eventos de la plataforma', 14, 400, C['gris']),
         text(103, 158, 'Usuarios', 15, 500, C['gris'], 'middle'), text(309, 158, 'Eventos', 15, 500, C['coralOsc'], 'middle'),
         line(0, 176, W, 176, C['linea']), rect(206, 173, 206, 3, C['coralOsc'])]

def admin_event_card(y, h, name, org, place, estado, kind, sw_on=False, sw_dis=False, note=None, note_col=None, footer=None, footer_col=None):
    p = [card(16, y, 380, h), text(32, y + 28, name, 16, 500)]
    pill, _ = status_pill(380, y + 14, estado, kind, right=True); p.append(pill)
    p += [text(32, y + 47, org, 12.5, 400, C['gris'])]
    t, yy = para(32, y + 64, place, 330, 12.5, 400, C['gris'], 17); p.append(t)
    yy += 12
    p += [rect(32, yy, 348, 66, C['supAlta'], 12), icon('seat', 44, yy + 10, 20, C['gris']), text(72, yy + 25, '¿Requiere asientos?', 14, 400),
          switch(316, yy + 8, sw_on, sw_dis)]
    if note: p.append(text(44, yy + 50, note, 11.5, 400, note_col or C['gris']))
    yy += 82
    if footer:
        t2, yy = para(32, yy, footer, 340, 12.5, 500, footer_col, 17); p.append(t2)
    return g('Evento · ' + name, *p)

def perfil_base(t, org=True):
    p = [status_bar(t), text(16, 134, 'Mi cuenta', 24, 700),
         card(16, 160, 380, 76), circle(56, 198, 24, C['profundo']), text(56, 205, 'A', 20, 500, C['blanco'], 'middle'),
         text(96, 190, 'Ana Estudiante', 16, 500), text(96, 208, 'asistente@uv.mx', 12.5, 400, C['gris']),
         circle(101, 224, 3, C['exito']), text(108, 228, 'Identidad verificada', 12, 400, C['gris']),
         chip(16, 254, 'Asistente', True)[0]]
    if org: p.append(chip(107, 254, 'Organizador', True)[0])
    return p

def perfil_ajustes(nav, active):
    return [text(16, 500, 'Ajustes de localización', 16, 500),
     card(16, 512, 380, 140), icon('place', 32, 534, 20, C['gris']), text(60, 548, 'Ciudad', 14, 400), text(380, 548, 'Orizaba, Veracruz', 13, 400, C['gris'], 'end'),
     line(32, 566, 380, 566, C['linea']), icon('globe', 32, 580, 20, C['gris']), text(60, 594, 'País', 14, 400), text(380, 594, 'México', 13, 400, C['gris'], 'end'),
     line(32, 612, 380, 612, C['linea']), icon('mylocation', 32, 626, 20, C['gris']), text(60, 640, 'Contenido según ubicación', 14, 400), switch(332, 618, True),
     text(16, 690, 'Preferencias', 16, 500),
     card(16, 702, 380, 120), icon('person', 32, 720, 20, C['gris']), text(60, 732, 'Editar mis datos', 14, 400), text(60, 748, 'Nombre y teléfono', 12, 400, C['gris']), icon('chevron', 372, 722, 22, C['gris']),
     line(32, 762, 380, 762, C['linea']), icon('card', 32, 778, 20, C['gris']), text(60, 790, 'Métodos de pago guardados', 14, 400), text(60, 806, 'Ninguno guardado', 12, 400, C['gris']), icon('chevron', 372, 780, 22, C['gris']),
     bottom_nav(nav, active), nav_handle()]

# ---------------------------------------------------------------- las 16 pantallas
S = []

# 01 · Detalle con minimapa y «Cómo llegar»
S.append(('01_detalle_mapa_como_llegar', 'Detalle del evento · minimapa y «Cómo llegar»', frame('01 Detalle · mapa',
    status_bar('3:55'), top_bar(None, 68),
    rect(16, 108, 380, 210, C['t100'], 20), icon('music', 300, 200, 90, C['t200']), text(28, 302, 'Música', 13, 500, C['marino']),
    text(16, 358, 'Concierto: Orquesta Universitaria', 20, 700),
    chip(16, 372, 'Música', True)[0], chip(84, 372, 'Asientos numerados', True, 'seat')[0],
    card(16, 424, 380, 176),
    icon('calendar', 32, 440, 20, C['gris']), text(60, 450, 'Sáb 3 Oct · 19:00 – 21:30', 15, 400), text(60, 468, 'Hora local del evento (Orizaba)', 12, 400, C['gris']),
    icon('place', 32, 482, 20, C['gris']), text(60, 492, 'Teatro Ignacio de la Llave', 15, 400), text(60, 510, 'Av. Oriente 6 esq. Sur 5, Centro, Orizaba', 12, 400, C['gris']),
    icon('person', 32, 524, 20, C['gris']), text(60, 534, 'Nancy Cruz', 15, 400), text(60, 552, 'Organiza', 12, 400, C['gris']),
    icon('group', 32, 566, 20, C['gris']), text(60, 576, '165 lugares disponibles', 15, 400), text(60, 594, '165 asientos libres de 250', 12, 400, C['gris']),
    card(16, 612, 380, 176),
    map_art(16, 612, 380, 132, seed=5, marker=(206, 700)),
    icon('place', 28, 754, 18, C['profundo']), text(52, 767, 'Cómo llegar', 14, 500, C['profundo']), text(380, 767, 'Lat 18.85118 · Lon -97.10012', 11, 400, C['gris'], 'end'),
    g('Barra de compra', rect(0, 796, W, 121, C['sup']), line(0, 796, W, 796, C['linea']),
      text(16, 824, '$120.00 MXN · 1 boleto · total final', 14, 500),
      btn(16, 836, 380, 56, 'Elegir asiento', C['coral'], C['tinta'], 500, 16)),
    nav_handle(),
)))

# 02 · Diálogo cancelar (de pago con comisión)
p = screen_panel_evento(False)
p += [btn(16, 792, 380, 48, 'Cancelar evento', C['sup'], C['coralOsc'], outline=C['linea']), nav_handle(), scrim(), dialog(60, 220, 292, 480),
      text(84, 262, '¿Cancelar el evento?', 22, 500), text(84, 296, 'Concierto: Orquesta Universitaria', 15, 500, C['gris'])]
p.append(para(84, 324, 'Se reembolsarán $360.00 MXN a 3 asistentes. Se aplicará una comisión de cancelación del 10 % ($36.00 MXN) a tu cuenta.', 246, 13.5, 400, C['tinta'], 19)[0])
p += [rect(84, 402, 244, 140, C['avisoC'], 12), icon('warning', 96, 412, 20, C['marino']),
      text(124, 426, 'Comisión de cancelación:', 13.5, 500, C['marino']), text(124, 444, '$36.00 MXN', 13.5, 500, C['marino']),
      para(124, 462, 'Es el 10 % de $360.00 MXN cobrados. Los asistentes reciben el 100 % de reembolso; la comisión se carga a tu cuenta de organizador.', 196, 12.5, 400, C['marino'], 17)[0]]
p.append(para(84, 566, 'Esta acción no se puede deshacer. Se avisará a los asistentes.', 246, 12, 400, C['gris'], 16)[0])
p += [btn(122, 614, 206, 40, 'Cancelar y aceptar comisión', C['coralOsc'], C['blanco'], 500, 13),
      text(328, 682, 'Conservar evento', 13.5, 500, C['marino'], 'end')]
S.append(('02_dialogo_cancelar_pago_comision', 'Diálogo del organizador · cancelar evento de pago (reembolso $360 · comisión $36)', frame('02 Diálogo cancelar · comisión', *p)))

# 03 · Panel cancelado con cargo pendiente
p = [status_bar('3:57'), top_bar('Panel del evento', 112), text(16, 140, 'Boletos emitidos', 16, 500)]
for i, (code, seat) in enumerate(BOLETOS):
    y = 166 + i * 40
    p += [text(16, y, code, 13, 400), text(16, y + 16, seat, 11, 400, C['gris']), text(396, y, 'Cancelado', 12, 500, C['coralOsc'], 'end')]
p += personal_acceso(300)
p += [banner(16, 616, 380, 74, 'error', 'Evento cancelado', 'Cancelado el Jue 17 Sep 2026 · 09:57. Comisión de cancelación cargada a tu cuenta: $36.00 MXN.'),
      banner(16, 702, 380, 74, 'aviso', 'Cargo pendiente en tu cuenta', 'Comisión de cancelación (10 %) · Concierto: Orquesta Universitaria: $36.00 MXN (pendiente)'),
      para(16, 806, 'Evento cancelado. Se anularon 3 boletos y se avisó a 1 asistentes. Comisión: $36.00 MXN.', 380, 13, 500, C['sobreExito'], 18)[0],
      nav_handle()]
S.append(('03_panel_evento_cancelado_cargo', 'Panel del evento cancelado · cargo pendiente al organizador', frame('03 Panel cancelado · cargo', *p)))

# 04 · Formulario fecha y hora
S.append(('04_form_fecha_hora', 'Nuevo evento · selectores de fecha y hora (sin texto libre)', frame('04 Nuevo evento · fecha y hora', *(screen_nuevo_evento('4:00') + [nav_handle()]))))

# 05 · Calendario sin fechas pasadas
p = screen_nuevo_evento('4:00') + [nav_handle(), scrim(), dialog(40, 210, 332, 530),
     text(64, 244, 'Select date', 12, 500, C['gris']), text(64, 302, 'Sep 17, 2026', 30, 400),
     line(40, 322, 372, 322, C['linea']),
     text(64, 356, 'September 2026', 14, 500), icon('dropdown', 170, 344, 22, C['gris']),
     f'<path d="M296 349l-6 6 6 6" fill="none" stroke="{C["tinta"]}" stroke-width="1.6"/>', f'<path d="M340 349l6 6-6 6" fill="none" stroke="{C["tinta"]}" stroke-width="1.6"/>']
cols = [75, 120, 165, 210, 255, 300, 345]
for i, d in enumerate('S M T W T F S'.split()):
    p.append(text(cols[i], 406, d, 13, 400, C['tinta'], 'middle'))
day = 1
for r, y in enumerate([452, 497, 542, 587, 632]):
    for c in range(7):
        if r == 0 and c < 2: continue
        if day > 30: break
        if day == 17:
            p.append(circle(cols[c], y - 5, 18, C['marino'])); p.append(text(cols[c], y, str(day), 14, 500, C['blanco'], 'middle'))
        else:
            p.append(text(cols[c], y, str(day), 14, 400, C['suave'] if day < 17 else C['tinta'], 'middle'))
        day += 1
p += [text(284, 722, 'Cancelar', 14, 500, C['marino'], 'end'), text(348, 722, 'Aceptar', 14, 500, C['marino'], 'end')]
S.append(('05_calendario_sin_pasado', 'Calendario Material 3 · fechas pasadas deshabilitadas', frame('05 Calendario sin pasado', *p)))

# 06 · Hora 24 h
def teclado_lateral(icono_medio):
    return [rect(0, 360, 48, 250, C['t50'], 12), icon('gps_off', 12, 372, 24, C['gris']), icon('backspace', 12, 420, 24, C['tinta']),
            rect(6, 460, 36, 36, C['t200'], 10), icon(icono_medio, 12, 466, 24, C['marino']), icon('smile', 12, 520, 24, C['gris']), icon('menu', 12, 568, 24, C['tinta'])]
p = screen_nuevo_evento('4:01') + [nav_handle(), scrim(), dialog(60, 340, 292, 275),
     text(84, 386, 'Hora', 20, 500),
     rect(110, 408, 90, 66, C['t100'], 8, C['marino'], 2), text(155, 455, '19', 36, 400, C['marino'], 'middle'),
     text(206, 450, ':', 32, 400, C['tinta'], 'middle'),
     rect(212, 408, 90, 66, C['supAlta'], 8), text(257, 455, '00', 36, 400, C['tinta'], 'middle'),
     text(110, 494, 'Hour', 12, 400, C['gris']), text(212, 494, 'Minute', 12, 400, C['gris']),
     text(206, 524, 'Formato de 24 horas (00–23 : 00–59).', 11.5, 400, C['gris'], 'middle'),
     text(268, 578, 'Cancelar', 14, 500, C['marino'], 'end'), text(328, 578, 'Aceptar', 14, 500, C['marino'], 'end')] + teclado_lateral('tabnext')
S.append(('06_hora_24h', 'Selector de hora en 24 h con entrada numérica', frame('06 Hora 24 h', *p)))

# 07 · Selector de ubicación con marcador
p = [status_bar('4:01'), top_bar('Nuevo evento', 68),
     rect(16, 96, 380, 436, C['sup'], 20),
     text(32, 112, 'Puedes editar la dirección sugerida por el mapa.', 11.5, 400, C['gris']),
     map_art(32, 124, 348, 250, seed=21, marker=(210, 244), zoom=True),
     text(32, 396, 'Lat 18.85106, Lon -97.10000', 14, 400), text(32, 414, 'Aprox.: Norte 12, 104, Centro, Orizaba', 12, 400, C['gris']),
     btn(32, 430, 348, 44, '', C['sup'], C['marino'], outline=C['linea']), icon('mylocation', 118, 442, 20, C['marino']), text(146, 457, 'Usar mi ubicación actual', 14, 500, C['marino']),
     text(32, 494, 'Mapa © OpenStreetMap contributors (ODbL).', 11.5, 400, C['gris']),
     text(32, 512, 'Ubicación fijada. Los asistentes verán el mapa y «Cómo llegar».', 11.5, 400, C['gris']),
     text(16, 560, 'Categoría', 16, 500),
     chip(16, 578, 'Académico', True)[0], chip(112, 578, 'Música')[0], chip(186, 578, 'Tecnología')[0], chip(282, 578, 'Teatro')[0], chip(354, 578, 'Deporte')[0],
     card(16, 632, 380, 66), text(32, 660, 'Evento de pago', 15, 400), text(32, 678, 'Solo confirmación de asistencia', 12, 400, C['gris']), switch(332, 649, False),
     card(16, 710, 380, 66), icon('seat', 32, 733, 20, C['gris']), text(60, 738, '¿Este evento requiere asientos?', 15, 400), text(60, 756, 'Entrada libre: solo se cuenta el aforo', 12, 400, C['gris']), switch(332, 727, False),
     field(16, 792, 380, 56, 'Aforo (lugares totales)'),
     rect(16, 866, 380, 40, C['t100'], 20), nav_handle()]
S.append(('07_mapa_marcador', 'Selector de ubicación (osmdroid) · marcador y dirección aproximada', frame('07 Mapa con marcador', *p)))

# 08 · Diálogo dejar de ser organizador (perfil)
p = perfil_base('4:03') + [
     card(16, 308, 380, 150), text(32, 338, 'Cuenta de organizador', 16, 500),
     para(32, 360, 'Puedes crear y publicar eventos, definir aforo y asientos y designar personal de acceso.', 340, 12.5, 400, C['gris'], 17)[0],
     btn(32, 400, 348, 44, 'Dejar de ser organizador', C['sup'], C['coralOsc'], outline=C['coralOsc'])] + perfil_ajustes(NAV_ORG, 3) + [
     scrim(), dialog(60, 306, 300, 350),
     text(84, 348, '¿Dejar de ser organizador?', 20, 500),
     text(84, 380, 'Se quitará el rol de organizador de tu', 13.5, 400), text(84, 399, 'cuenta:', 13.5, 400),
     text(84, 426, '• Desaparece la pestaña Mis eventos.', 13, 400),
     text(84, 449, '• Tus eventos en borrador o realizados se', 13, 400), text(84, 467, 'conservan en el historial.', 13, 400),
     text(84, 492, '• Sigues siendo asistente y puedes volver a', 13, 400), text(84, 510, 'activar el rol cuando quieras.', 13, 400),
     btn(128, 540, 208, 40, 'Sí, dejar de ser organizador', C['coralOsc'], C['blanco'], 500, 13),
     text(336, 616, 'Cancelar', 13.5, 500, C['marino'], 'end')]
S.append(('08_dialogo_dejar_organizador', 'Perfil · diálogo «Dejar de ser organizador»', frame('08 Diálogo dejar organizador', *p)))

# 09 · Mis boletos con evento cancelado
p = [status_bar('4:02'), text(16, 84, 'Mis boletos', 24, 700), text(16, 116, 'Anteriores · 3', 13, 500, C['gris'])]
for i in range(3):
    y = 132 + i * 126
    p += [card(16, y, 380, 112), rect(28, y + 14, 48, 56, C['t50'], 12), text(52, y + 44, '3', 20, 700, C['marino'], 'middle'), text(52, y + 60, 'oct', 11, 500, C['marino'], 'middle'),
          text(90, y + 24, 'Concierto: Orquesta Universitaria', 13.5, 500), text(90, y + 40, f'Boleto {i+1} de 3', 12, 500, C['profundo']),
          text(90, y + 56, '19:00 · Teatro Ignacio de la Llave', 11.5, 400, C['gris']), text(90, y + 71, f'Planta baja · Planta baja A{5+i}', 11.5, 400, C['gris']),
          icon('qrscan', 366, y + 44, 22, C['suave']),
          rect(90, y + 82, 254, 22, C['errorC'], 11), icon('error', 98, y + 86, 14, C['sobreError']), text(116, y + 97, 'Evento cancelado · reembolso en proceso', 11, 500, C['sobreError'])]
p += [bottom_nav(NAV_ASIST, 1), nav_handle()]
S.append(('09_mis_boletos_evento_cancelado', 'Mis boletos · «Evento cancelado · reembolso en proceso»', frame('09 Mis boletos · cancelado', *p)))

# 10 · Tu boleto (evento cancelado)
p = [status_bar('4:02'), top_bar('Tu boleto', 68),
     circle(40, 132, 22, C['t100']), icon('back', 28, 120, 24, C['suave']), circle(372, 132, 22, C['supAlta']),
     f'<path d="M366 132h12m-4-4l4 4-4 4" fill="none" stroke="{C["tinta"]}" stroke-width="1.8"/>',
     text(206, 137, 'Boleto 1 de 3', 15, 500, C['tinta'], 'middle'),
     rect(16, 168, 380, 640, C['sup'], 20),
     f'<path d="M16 188a20 20 0 0 1 20-20h340a20 20 0 0 1 20 20v226H16z" fill="{C["marino"]}"/>',
     icon('ticket', 32, 188, 18, C['coral']), text(58, 202, 'Boleto 1 de 3', 12.5, 500, C['blanco']),
     rect(300, 186, 82, 24, C['errorC'], 12), icon('cancel', 308, 191, 14, C['sobreError']), text(326, 202, 'Cancelado', 12, 500, C['sobreError']),
     text(32, 242, 'Concierto: Orquesta Universitaria', 19, 700, C['blanco']),
     icon('calendar', 32, 262, 14, C['t200']), text(52, 273, 'Fecha', 11.5, 400, C['t200']), text(52, 292, 'Sáb 3 Oct · 19:00', 15, 400, C['blanco']),
     icon('place', 32, 306, 14, C['t200']), text(52, 317, 'Lugar', 11.5, 400, C['t200']), text(52, 336, 'Teatro Ignacio de la Llave', 15, 400, C['blanco']),
     icon('ticket', 32, 350, 14, C['t200']), text(52, 361, 'Tipo', 11.5, 400, C['t200']), text(52, 380, 'Planta baja', 15, 400, C['blanco']),
     icon('seat', 206, 350, 14, C['t200']), text(226, 361, 'Asiento', 11.5, 400, C['t200']), text(226, 380, 'Planta baja', 15, 400, C['blanco']), text(226, 400, 'Fila A · 5', 15, 400, C['blanco']),
     circle(16, 414, 12, C['fondo']), circle(396, 414, 12, C['fondo']),
     f'<line x1="36" y1="414" x2="376" y2="414" stroke="{C["linea"]}" stroke-width="1" stroke-dasharray="6 5"/>',
     rect(80, 440, 252, 252, C['blanco'], 8), qr(96, 456, 220, 25, 7, C['suave']),
     text(206, 732, 'EZ-5071-6505-9838', 18, 500, C['tinta'], 'middle', 1), text(206, 752, 'Código de referencia', 12, 400, C['gris'], 'middle'),
     banner(16, 792, 380, 66, 'error', None, 'Evento cancelado · reembolso en proceso. Recibirás el 100 % de tu compra por el mismo medio de pago.'),
     rect(16, 878, 380, 30, C['sup'], 12, C['linea'], 1), nav_handle()]
S.append(('10_boleto_evento_cancelado', 'Tu boleto · QR anulado por evento cancelado', frame('10 Boleto cancelado', *p)))

# 11 · Diálogo convertirse en organizador
p = perfil_base('4:02', org=False) + [
     card(16, 308, 380, 150), icon('campaign', 32, 326, 22, C['coralOsc']), text(62, 343, '¿Quieres publicar tus propios eventos?', 15, 500),
     para(32, 368, 'Como organizador podrás crear eventos gratuitos o de pago, definir aforo y asientos y designar personal de acceso. Sigues siendo asistente.', 340, 12.5, 400, C['gris'], 17)[0],
     btn(32, 410, 348, 40, 'Convertirme en organizador', C['coralOsc'], C['blanco'])] + perfil_ajustes(NAV_ASIST, 2) + [
     scrim(), dialog(56, 282, 300, 400),
     text(80, 322, '¿Convertirte en', 20, 500), text(80, 348, 'organizador?', 20, 500),
     text(80, 380, 'Al confirmar, en tu cuenta cambia esto:', 13, 400),
     text(80, 404, '• Aparece la pestaña Mis eventos.', 13, 400),
     text(80, 427, '• Podrás crear y publicar eventos gratuitos', 13, 400), text(80, 445, 'o de pago, definir aforo y asientos.', 13, 400),
     text(80, 470, '• Podrás designar personal de acceso para', 13, 400), text(80, 488, 'tus eventos.', 13, 400),
     text(80, 513, '• Conservas tu cuenta de asistente y podrás', 13, 400), text(80, 531, 'dejar de ser organizador más adelante.', 13, 400),
     btn(104, 562, 228, 40, 'Sí, convertirme en organizador', C['coral'], C['tinta'], 500, 13),
     text(332, 640, 'Cancelar', 13.5, 500, C['marino'], 'end')]
S.append(('11_dialogo_convertirse', 'Perfil · diálogo «¿Convertirte en organizador?»', frame('11 Diálogo convertirse', *p)))

# 12 · Inicio con Snackbar «Ahora eres organizador»
p = [status_bar('4:03'),
     g('Logo EnZona', circle(28, 78, 12, C['enzona']), icon('place', 20, 70, 16, C['blanco']), text(46, 84, 'En', 17, 700, C['marino']), text(70, 84, 'Zona', 17, 700, C['enzona']), text(46, 96, 'EVENTOS CERCA DE TI', 6, 500, C['gris'], 'start', 0.5)),
     text(16, 124, 'Eventos cerca de ti', 24, 700),
     icon('place', 16, 148, 18, C['profundo']), text(40, 162, 'Orizaba, Veracruz', 15, 500), icon('dropdown', 162, 150, 20, C['tinta']),
     rect(16, 190, 380, 52, C['sup'], 12, C['suave'], 1), icon('search', 32, 204, 22, C['gris']), text(66, 222, 'Buscar evento o lugar', 15, 400, C['gris']),
     chip(16, 260, 'Gratis')[0], chip(84, 260, 'Fecha', dropdown=True)[0], chip(180, 260, 'Lugar', dropdown=True)[0], chip(276, 260, 'Tipo', dropdown=True)[0], chip(366, 260, 'De pago', dropdown=True)[0],
     text(16, 328, '4 eventos publicados', 13, 400, C['gris']),
     card(16, 344, 380, 350), f'<path d="M16 364a20 20 0 0 1 20-20h340a20 20 0 0 1 20 20v210H16z" fill="{C["exitoC"]}"/>',
     icon('school', 306, 500, 100, '#B9DCC5'),
     rect(32, 360, 40, 52, C['sup'], 10), text(52, 384, '25', 18, 700, C['marino'], 'middle'), text(52, 400, 'sep', 11, 500, C['marino'], 'middle'),
     text(32, 560, 'Académico', 13, 500, C['sobreExito']),
     text(32, 600, 'Feria de Emprendimiento FCAS', 16, 500),
     icon('calendar', 32, 612, 16, C['gris']), text(56, 625, 'Vie 25 Sep · 10:00', 13, 400, C['gris']),
     icon('place', 32, 634, 16, C['gris']), text(56, 647, 'Facultad de Negocios y Tecnologías', 13, 400, C['gris']),
     status_pill(32, 664, 'Gratis', 'exito')[0], text(380, 680, '142 lugares', 12.5, 400, C['gris'], 'end'),
     card(16, 710, 380, 120), rect(32, 726, 40, 52, C['sup'], 10), text(52, 750, '9', 18, 700, C['marino'], 'middle'), text(52, 766, 'oct', 11, 500, C['marino'], 'middle'),
     bottom_nav(NAV_ORG, 0), snackbar('Ahora eres organizador', 774), nav_handle()]
S.append(('12_snackbar_ahora_organizador', 'Inicio con pestaña «Mis eventos» y Snackbar «Ahora eres organizador»', frame('12 Snackbar organizador', *p)))

# 13 · Validador rechaza QR de evento cancelado
p = [status_bar('4:06'), text(16, 90, 'Validar acceso', 24, 700), text(16, 112, 'Escanea el código QR del asistente', 14, 400, C['gris']),
     card(16, 130, 380, 66), icon('cloudoff', 32, 151, 22, C['gris']), text(64, 170, 'Modo sin conexión', 15, 400), switch(332, 147, False),
     rect(16, 214, 380, 300, C['errorC'], 20, C['error'], 2),
     circle(206, 270, 28, C['error']), '<path d="M196 260l20 20m0-20l-20 20" stroke="#FFFFFF" stroke-width="4" stroke-linecap="round"/>',
     text(206, 330, 'Evento cancelado', 18, 500, C['sobreError'], 'middle'),
     text(206, 352, 'El organizador canceló «Concierto: Orquesta Universitaria».', 12, 400, C['tinta'], 'middle'), text(206, 369, 'Este boleto ya no da acceso.', 12, 400, C['tinta'], 'middle'),
     text(206, 400, 'Planta baja · Fila A · Asiento 5', 16, 500, C['tinta'], 'middle'),
     rect(146, 414, 120, 22, C['supAlta'], 11), text(206, 429, 'EZ-5071-6505-9838', 11, 500, C['gris'], 'middle'),
     btn(36, 454, 340, 44, 'Escanear siguiente', C['marino'], C['blanco']),
     text(16, 556, 'Últimos accesos', 16, 500),
     circle(20, 582, 4, C['error']), text(32, 586, 'EZ-5071-6505-9838', 13, 400), text(32, 602, 'Evento cancelado · 17/09/2026 16:06:10', 11.5, 400, C['gris']),
     bottom_nav(NAV_VAL, 0), nav_handle()]
S.append(('13_validador_evento_cancelado', 'Validador · QR rechazado con «Evento cancelado»', frame('13 Validador · cancelado', *p)))

# 14/15 · Admin diálogo con motivo (vacío → capturado)
def admin_dialog(motivo_capturado):
    p = screen_admin('4:05') + [
        admin_event_card(196, 200, 'Feria de Emprendimiento FCAS', 'Organiza: Nancy Cruz', 'Facultad de Negocios y Tecnologías · Vie 25 Sep · 10:00 · 158/300 lugares', 'Publicado', 'exito', note='Al activarlo se genera un mapa de butacas en filas de 12.', footer='Cancelar evento', footer_col=C['coralOsc']),
        admin_event_card(412, 200, 'Concierto: Orquesta Universitaria', 'Organiza: Nancy Cruz', 'Teatro Ignacio de la Llave · Sáb 3 Oct · 19:00 · 88/250 lugares', 'Publicado', 'exito', sw_dis=True, note='Bloqueado: el evento ya tiene boletos emitidos.', note_col='#B07A00', footer='Cancelar evento', footer_col=C['coralOsc']),
        admin_event_card(628, 200, 'Hackathon UV 2026', 'Organiza: Nancy Cruz', 'Centro de Cómputo UV · Vie 9 Oct · 17:00 · 83/120 lugares', 'Publicado', 'exito', note='Al activarlo se genera un mapa de butacas en filas de 12.', footer='Cancelar evento', footer_col=C['coralOsc']),
        bottom_nav(NAV_ADM, 2), nav_handle(), scrim(), dialog(60, 186, 292, 560),
        text(84, 226, 'Cancelar evento', 21, 500), text(84, 252, '(administración)', 21, 500),
        text(84, 282, 'Feria de Emprendimiento FCAS', 15, 500, C['gris']),
        para(84, 308, 'Se cancelará el evento y se anularán sin confirmaciones ni boletos emitidos.', 246, 13, 400, C['tinta'], 18)[0],
        banner(84, 350, 244, 118, 'info', None, 'Cancelación por moderación: los asistentes reciben el 100 % de reembolso y no se cobra comisión al organizador. Se registrará quién canceló y por qué.'),
    ]
    if motivo_capturado:
        p += [field(84, 496, 244, 64, 'Motivo de la cancelación', value='Contenido', focused=True, floating=True)]
    else:
        p += [field(84, 496, 244, 64, 'Motivo de la cancelación'), text(84, 578, 'Escribe el motivo (mínimo 5 caracteres).', 11.5, 400, C['gris'])]
    p += [para(84, 604, 'Esta acción no se puede deshacer. Se avisará a los asistentes.', 246, 12, 400, C['gris'], 16)[0]]
    if motivo_capturado:
        p += [btn(184, 650, 144, 40, 'Cancelar evento', C['coralOsc'], C['blanco'], 500, 13.5)]
    else:
        p += [btn(184, 650, 144, 40, 'Cancelar evento', C['supAlta'], C['suave'], 500, 13.5)]
    p += [text(328, 722, 'Conservar evento', 13.5, 500, C['marino'], 'end')]
    if motivo_capturado:
        p += teclado_lateral('enter')
    return p
S.append(('14_admin_dialogo_motivo', 'Administración · cancelación por moderación, motivo obligatorio (botón deshabilitado)', frame('14 Admin · motivo vacío', *admin_dialog(False))))
S.append(('15_admin_motivo_capturado', 'Administración · motivo capturado, botón habilitado', frame('15 Admin · motivo capturado', *admin_dialog(True))))

# 16 · Admin resultado
p = screen_admin('4:05') + [
    admin_event_card(196, 236, 'Feria de Emprendimiento FCAS', 'Organiza: Nancy Cruz', 'Facultad de Negocios y Tecnologías · Vie 25 Sep · 10:00 · 158/300 lugares', 'Cancelado', 'error', sw_dis=True, note='Al activarlo se genera un mapa de butacas en filas de 12.',
                     footer='Cancelado por Admin EnZona · Contenido', footer_col=C['coralOsc']),
    para(32, 388, 'Cancelado: 0 boletos anulados, $0.00 MXN reembolsados, sin comisión al organizador.', 340, 12.5, 500, C['sobreExito'], 17)[0],
    admin_event_card(444, 200, 'Concierto: Orquesta Universitaria', 'Organiza: Nancy Cruz', 'Teatro Ignacio de la Llave · Sáb 3 Oct · 19:00 · 88/250 lugares', 'Cancelado', 'error', sw_dis=True, note='Bloqueado: el evento ya tiene boletos emitidos.', note_col='#B07A00',
                     footer='Cancelado por Nancy Cruz', footer_col=C['coralOsc']),
    admin_event_card(656, 200, 'Hackathon UV 2026', 'Organiza: Nancy Cruz', 'Centro de Cómputo UV · Vie 9 Oct · 17:00 · 83/120 lugares', 'Publicado', 'exito', note='Al activarlo se genera un mapa de butacas en filas de 12.', footer='Cancelar evento', footer_col=C['coralOsc']),
    bottom_nav(NAV_ADM, 2), nav_handle()]
S.append(('16_admin_resultado', 'Administración · evento cancelado por moderación (sin comisión)', frame('16 Admin · resultado', *p)))

# ---------------------------------------------------------------- portada y guía de estilo
COVER_W, COVER_H = 1200, 640
cover = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{COVER_W}" height="{COVER_H}" viewBox="0 0 {COVER_W} {COVER_H}"><g id="Portada">'
    f'<defs><linearGradient id="gcov" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{C["degIni"]}"/><stop offset="1" stop-color="{C["enzona"]}"/></linearGradient></defs>'
    + rect(0, 0, COVER_W, COVER_H, 'url(#gcov)', 24)
    + circle(980, 520, 260, '#FFFFFF', op=0.06) + circle(1100, 120, 160, '#FFFFFF', op=0.05)
    + g('Logo', circle(96, 120, 28, C['blanco']), icon('place', 78, 102, 36, C['enzona']), text(140, 132, 'EnZona', 40, 700, C['blanco']), text(142, 156, 'EVENTOS CERCA DE TI', 12, 500, C['t200'], 'start', 2))
    + text(72, 300, 'Mockups · v3', 72, 700, C['blanco'])
    + text(72, 350, 'Cancelación con regla de negocio · rol de organizador · ubicación en mapa · fecha y hora', 22, 400, C['t100'])
    + text(72, 430, 'App Android · Kotlin + Jetpack Compose + Material 3 · marco 412 × 917', 16, 400, C['t200'])
    + text(72, 456, '16 pantallas de docs/capturas_v3 · paleta v2 de la plataforma · 27 de septiembre de 2026', 16, 400, C['t200'])
    + rect(72, 520, 160, 48, C['coral'], 24) + text(152, 550, 'RF-14 · RF-04', 16, 500, C['tinta'], 'middle')
    + rect(248, 520, 160, 48, '#FFFFFF', 24, op=0.15) + text(328, 550, 'RF-08 · RF-19', 16, 500, C['blanco'], 'middle')
    + rect(424, 520, 240, 48, '#FFFFFF', 24, op=0.15) + text(544, 550, 'CU-05 · CP-CANCEL / ROL / UBIC', 16, 500, C['blanco'], 'middle')
    + '</g></svg>')

sw = []
swatches = [('Azul marino', 'marino', 'primary'), ('Azul EnZona', 'enzona', 'acento / surfaceTint'), ('Azul profundo', 'profundo', 'secondary · enlaces'), ('Coral', 'coral', 'tertiary · acción destacada'),
            ('Coral oscuro', 'coralOsc', 'presionado · destructivo'), ('Coral suave', 'coralSuave', 'tertiaryContainer'), ('Tinta', 'tinta', 'onSurface'), ('Gris texto', 'gris', 'onSurfaceVariant'),
            ('Gris suave', 'suave', 'outline'), ('Línea', 'linea', 'outlineVariant'), ('Fondo', 'fondo', 'background'), ('Superficie', 'sup', 'surface'),
            ('Superficie alta', 'supAlta', 'surfaceVariant'), ('Tinte 50', 't50', 'secondaryContainer'), ('Tinte 100', 't100', 'primaryContainer'), ('Tinte 200', 't200', 'inversePrimary'),
            ('Éxito', 'exito', 'semántico'), ('Éxito contenedor', 'exitoC', 'semántico'), ('Advertencia', 'aviso', 'semántico'), ('Advertencia cont.', 'avisoC', 'semántico'),
            ('Error', 'error', 'error'), ('Error contenedor', 'errorC', 'errorContainer'), ('Información cont.', 'infoC', 'semántico'), ('Sobre información', 'sobreInfo', 'semántico')]
SW_W, SW_H = 1200, 780
sw.append(rect(0, 0, SW_W, SW_H, C['sup'], 24))
sw.append(text(48, 64, 'Guía de estilo · Paleta v2 y tipografía', 28, 700))
sw.append(text(48, 92, 'Tokens de ui/theme/Color.kt y Theme.kt · Roboto · formas 6 / 10 / 12 / 20 / 28 dp', 14, 400, C['gris']))
for i, (n, k, role) in enumerate(swatches):
    x = 48 + (i % 8) * 140; y = 120 + (i // 8) * 130
    sw.append(g('Muestra · ' + n, rect(x, y, 124, 70, C[k], 12, C['linea'], 1), text(x, y + 88, n, 12.5, 500), text(x, y + 104, C[k], 11.5, 400, C['gris']), text(x, y + 118, role, 10.5, 400, C['gris'])))
ty = 540
sw.append(text(48, ty, 'Tipografía', 18, 700))
for i, (n, sz, wt) in enumerate([('headlineMedium 26 Bold', 26, 700), ('titleLarge 20 Medium', 20, 500), ('titleMedium 17 Medium', 17, 500), ('bodyLarge 16 Regular', 16, 400), ('bodyMedium 14 Regular', 14, 400), ('labelMedium 12.5 Medium', 12.5, 500)]):
    sw.append(text(48 + (i % 3) * 380, ty + 40 + (i // 3) * 46, n, sz, wt))
sw.append(text(48, ty + 140, 'Componentes', 18, 700))
sw.append(btn(48, ty + 156, 200, 48, 'Acción destacada', C['coral'], C['tinta']))
sw.append(btn(264, ty + 156, 200, 48, 'Botón general', C['marino'], C['blanco']))
sw.append(btn(480, ty + 156, 200, 48, 'Destructivo', C['coralOsc'], C['blanco']))
sw.append(btn(696, ty + 156, 200, 48, 'Secundario', C['t100'], C['marino']))
sw.append(chip(912, ty + 163, 'Chip seleccionado', True)[0])
sw.append(chip(1064, ty + 163, 'Chip', False)[0])
styleguide = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{SW_W}" height="{SW_H}" viewBox="0 0 {SW_W} {SW_H}"><g id="Guía de estilo">' + ''.join(sw) + '</g></svg>')

# ---------------------------------------------------------------- escribir
os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, '00_portada.svg'), 'w', encoding='utf-8') as f: f.write(cover)
with open(os.path.join(OUT, '00_guia_de_estilo.svg'), 'w', encoding='utf-8') as f: f.write(styleguide)
manifest = [{'file': '00_portada.svg', 'name': 'Portada', 'desc': 'EnZona v3 · Mockups', 'section': 'Portada', 'w': COVER_W, 'h': COVER_H},
            {'file': '00_guia_de_estilo.svg', 'name': 'Guía de estilo', 'desc': 'Paleta v2, tipografía y componentes', 'section': 'Portada', 'w': SW_W, 'h': SW_H}]
SECTION = {'01': 'Ubicación en mapa (RF-08 · RF-19)', '07': 'Ubicación en mapa (RF-08 · RF-19)',
           '02': 'Cancelación de eventos (RF-14)', '03': 'Cancelación de eventos (RF-14)', '09': 'Cancelación de eventos (RF-14)', '10': 'Cancelación de eventos (RF-14)', '13': 'Cancelación de eventos (RF-14)',
           '14': 'Cancelación administrativa (RF-18)', '15': 'Cancelación administrativa (RF-18)', '16': 'Cancelación administrativa (RF-18)',
           '04': 'Fecha y hora con selectores (RF-08)', '05': 'Fecha y hora con selectores (RF-08)', '06': 'Fecha y hora con selectores (RF-08)',
           '08': 'Rol de organizador (RF-04)', '11': 'Rol de organizador (RF-04)', '12': 'Rol de organizador (RF-04)'}
for fname, desc, svg in S:
    with open(os.path.join(OUT, fname + '.svg'), 'w', encoding='utf-8') as f: f.write(svg)
    manifest.append({'file': fname + '.svg', 'name': fname[:2] + ' · ' + fname[3:].replace('_', ' ').capitalize(), 'desc': desc, 'section': SECTION[fname[:2]], 'w': W, 'h': H})
with open(os.path.join(OUT, 'manifest.json'), 'w', encoding='utf-8') as f: json.dump(manifest, f, ensure_ascii=False, indent=1)
print('generados', len(manifest), 'svg en', OUT)
