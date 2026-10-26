"""Convierte logoenzonav-svg.svg (Descargas) a un VectorDrawable de Android.

Android no tiene <mask>, stroke-dasharray ni skewX, así que:
- el círculo con muescas se vuelve un path evenOdd (círculo + 4 circulitos);
- cada línea punteada se vuelve una serie de trazos cortos;
- el texto se "hornea": la inclinación skewX(-8) y la traslación se aplican a las
  coordenadas (los arcos se convierten antes a curvas cúbicas).
"""
import math
import re

SALIDA = r"D:\EnZona-android_3\app\src\main\res\drawable\enzona_logo.xml"

# ---------------------------------------------------------------- geometría

def arco_a_cubicas(p0, rx, ry, rot, grande, barrido, p1):
    """Arco SVG (F.6.5 de la especificación) -> lista de curvas cúbicas."""
    x1, y1 = p0
    x2, y2 = p1
    if (x1, y1) == (x2, y2):
        return []
    if rx == 0 or ry == 0:
        return [("L", x2, y2)]
    phi = math.radians(rot)
    cphi, sphi = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p = cphi * dx + sphi * dy
    y1p = -sphi * dx + cphi * dy
    rx, ry = abs(rx), abs(ry)
    lam = (x1p ** 2) / (rx ** 2) + (y1p ** 2) / (ry ** 2)
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    coef = math.sqrt(max(0, num / den)) if den else 0
    if grande == barrido:
        coef = -coef
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx
    cx = cphi * cxp - sphi * cyp + (x1 + x2) / 2
    cy = sphi * cxp + cphi * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        d = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return d

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not barrido and dt > 0:
        dt -= 2 * math.pi
    elif barrido and dt < 0:
        dt += 2 * math.pi
    segmentos = max(1, math.ceil(abs(dt) / (math.pi / 2)))
    paso = dt / segmentos
    curvas = []
    t = t1
    for _ in range(segmentos):
        a = 4 / 3 * math.tan(paso / 4)
        cos1, sin1 = math.cos(t), math.sin(t)
        cos2, sin2 = math.cos(t + paso), math.sin(t + paso)
        e1 = (cos1 - a * sin1, sin1 + a * cos1)
        e2 = (cos2 + a * sin2, sin2 - a * cos2)
        p = (cos2, sin2)

        def mapa(q):
            x = rx * q[0]
            y = ry * q[1]
            return (cphi * x - sphi * y + cx, sphi * x + cphi * y + cy)

        c1, c2, pf = mapa(e1), mapa(e2), mapa(p)
        curvas.append(("C", c1[0], c1[1], c2[0], c2[1], pf[0], pf[1]))
        t += paso
    return curvas


def parsear(d):
    """Path SVG absoluto (M H V L C A Z) -> segmentos ('M',x,y) ('L',x,y) ('C',...) ('Z',)."""
    fichas = re.findall(r"[MHVLCAZ]|-?\d*\.?\d+(?:e-?\d+)?", d)
    i = 0
    actual = (0.0, 0.0)
    inicio = actual
    salida = []
    cmd = None
    while i < len(fichas):
        f = fichas[i]
        if f.isalpha():
            cmd = f
            i += 1
            if cmd == "Z":
                salida.append(("Z",))
                actual = inicio
            continue
        def num(k):
            return float(fichas[i + k])
        if cmd == "M":
            actual = (num(0), num(1)); inicio = actual; salida.append(("M", *actual)); i += 2; cmd = "L"
        elif cmd == "L":
            actual = (num(0), num(1)); salida.append(("L", *actual)); i += 2
        elif cmd == "H":
            actual = (num(0), actual[1]); salida.append(("L", *actual)); i += 1
        elif cmd == "V":
            actual = (actual[0], num(0)); salida.append(("L", *actual)); i += 1
        elif cmd == "C":
            seg = ("C", num(0), num(1), num(2), num(3), num(4), num(5)); salida.append(seg); actual = (num(4), num(5)); i += 6
        elif cmd == "A":
            destino = (num(5), num(6))
            salida.extend(arco_a_cubicas(actual, num(0), num(1), num(2), int(num(3)), int(num(4)), destino))
            actual = destino; i += 7
        else:
            raise ValueError(f"comando {cmd} no soportado")
    return salida


def transformar(segmentos, fn):
    res = []
    for s in segmentos:
        if s[0] == "Z":
            res.append(s)
        else:
            coords = s[1:]
            nuevas = []
            for k in range(0, len(coords), 2):
                nuevas.extend(fn(coords[k], coords[k + 1]))
            res.append((s[0], *nuevas))
    return res


def a_texto(segmentos):
    partes = []
    for s in segmentos:
        if s[0] == "Z":
            partes.append("Z")
        else:
            partes.append(s[0] + " ".join(f"{v:.2f}".rstrip("0").rstrip(".") for v in s[1:]))
    return " ".join(partes)


# ---------------------------------------------------------------- el logo

TAN8 = math.tan(math.radians(-8))


def texto_xy(x, y):
    """translate(365, 58) skewX(-8): x' = x + tan(-8°)·y, luego la traslación."""
    return (x + TAN8 * y + 365, y + 58)


def circulo(cx, cy, r):
    return f"M {cx - r} {cy} A {r} {r} 0 1 0 {cx + r} {cy} A {r} {r} 0 1 0 {cx - r} {cy} Z"


def guiones(x, y0, y1, largo, hueco):
    """Línea vertical punteada como subtrazos M x a V b."""
    partes = []
    y = y0
    while y < y1:
        partes.append(f"M {x} {y:.2f} V {min(y + largo, y1):.2f}")
        y += largo + hueco
    return " ".join(partes)


def guiones_transformados(x, y0, y1, largo, hueco, fn):
    partes = []
    y = y0
    while y < y1:
        a = fn(x, y)
        b = fn(x, min(y + largo, y1))
        partes.append(f"M {a[0]:.2f} {a[1]:.2f} L {b[0]:.2f} {b[1]:.2f}")
        y += largo + hueco
    return " ".join(partes)


PIN_EXTERIOR = "M 180 6 C 274 6, 342 76, 342 168 C 342 238, 297 290, 269 322 C 245 348, 226 374, 180 374 C 134 374, 115 348, 91 322 C 63 290, 18 238, 18 168 C 18 76, 86 6, 180 6 Z"
PIN_INTERIOR = "M 180 25 C 256 25, 318 86, 318 168 C 318 228, 278 275, 255 305 C 236 330, 217 353, 180 353 C 143 353, 124 330, 105 305 C 82 275, 42 228, 42 168 C 42 86, 104 25, 180 25 Z"
DISCO = " ".join([circulo(180, 168, 128), circulo(52, 168, 22), circulo(308, 168, 22), circulo(180, 40, 7), circulo(180, 296, 7)])
BOLETO = "M 0 0 H 150 V 30 A 15 15 0 0 0 150 60 V 90 H 0 V 60 A 15 15 0 0 0 0 30 Z"

LETRA_E = "M 38 25 H 148 C 162 25, 170 33, 170 46 C 170 59, 162 67, 148 67 H 68 V 95 A 22 22 0 0 1 68 139 V 168 H 148 C 162 168, 170 176, 170 189 C 170 202, 162 210, 148 210 H 38 C 16 210, 4 196, 4 174 V 61 C 4 39, 16 25, 38 25 Z"
LETRA_N1 = "M 192 68 H 232 V 86 C 244 71, 262 64, 284 64 C 318 64, 338 86, 338 122 V 210 H 294 V 130 C 294 108, 283 98, 266 98 C 247 98, 236 110, 236 132 V 210 H 192 Z"
LETRA_Z = "M 362 25 H 492 C 506 25, 514 36, 505 50 L 422 170 H 494 C 506 170, 514 178, 514 190 C 514 202, 506 210, 494 210 H 365 C 351 210, 343 199, 352 185 L 435 65 H 362 C 350 65, 342 57, 342 45 C 342 33, 350 25, 362 25 Z"
LETRA_O = "M 598 64 C 644 64, 676 94, 676 138 C 676 188, 598 242, 598 242 C 598 242, 520 188, 520 138 C 520 94, 552 64, 598 64 Z M 598 102 A 34 34 0 1 0 598.1 102 Z"
LETRA_N2 = "M 696 68 H 736 V 86 C 748 71, 766 64, 788 64 C 822 64, 842 86, 842 122 V 210 H 798 V 130 C 798 108, 787 98, 770 98 C 751 98, 740 110, 740 132 V 210 H 696 Z"
LETRA_A = "M 932 64 C 892 64, 864 92, 864 137 C 864 182, 892 212, 932 212 C 952 212, 968 204, 978 190 V 210 H 1020 V 68 H 978 V 86 C 968 72, 952 64, 932 64 Z M 942 100 C 964 100, 978 115, 978 138 C 978 161, 964 176, 942 176 C 920 176, 906 161, 906 138 C 906 115, 920 100, 942 100 Z"

AZUL_EN = "#21416E"
AZUL_ZONA = "#3489B1"


def letra(d, color):
    return f'    <path android:fillColor="{color}" android:fillType="evenOdd" android:pathData="{a_texto(transformar(parsear(d), texto_xy))}" />\n'


# El viewBox original (1180) corta la «a»: el lienzo se ajusta al contenido real del texto
maxx = max(v for d in (LETRA_E, LETRA_N1, LETRA_Z, LETRA_O, LETRA_N2, LETRA_A)
           for seg in transformar(parsear(d), texto_xy) if seg[0] != "Z"
           for v in seg[1::2])
ANCHO = int(math.ceil(maxx)) + 6
ALTO = 380
ANCHO_DP = round(76 * ANCHO / ALTO)
print("ancho del lienzo", ANCHO, "->", ANCHO_DP, "dp")

xml = []
xml.append('<?xml version="1.0" encoding="utf-8"?>\n')
xml.append('<!-- Logo EnZona v1.2 (logoenzonav-svg.svg del diseñador) convertido a vector: la máscara de\n')
xml.append('     muescas es un path evenOdd, las líneas punteadas son trazos cortos y la inclinación del\n')
xml.append('     texto (skewX -8°) ya está aplicada a las coordenadas. Ver scratchpad svg_a_vector.py. -->\n')
xml.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n')
xml.append(f'    android:width="{ANCHO_DP}dp"' + chr(10) + '    android:height="76dp"' + chr(10) + f'    android:viewportWidth="{ANCHO}"' + chr(10) + f'    android:viewportHeight="{ALTO}">' + chr(10))
xml.append(f'    <path android:fillColor="#1B819C" android:pathData="{PIN_EXTERIOR}" />\n')
xml.append(f'    <path android:fillColor="#03223F" android:pathData="{PIN_INTERIOR}" />\n')
xml.append(f'    <path android:fillColor="#1C4E78" android:fillType="evenOdd" android:pathData="{DISCO}" />\n')
# Boleto: translate(180,168) rotate(-45) translate(-75,-45)
xml.append('    <group android:translateX="180" android:translateY="168" android:rotation="-45">\n')
xml.append('        <group android:translateX="-75" android:translateY="-45">\n')
xml.append(f'            <path android:fillColor="#FFFFFF" android:pathData="{BOLETO}" />\n')
xml.append(f'            <path android:strokeColor="#1C4E78" android:strokeWidth="4.5" android:strokeLineCap="round" android:pathData="{guiones(34, 8, 82, 6, 5)}" />\n')
xml.append(f'            <path android:strokeColor="#1C4E78" android:strokeWidth="4.5" android:strokeLineCap="round" android:pathData="{guiones(116, 8, 82, 6, 5)}" />\n')
xml.append('            <path android:strokeColor="#661C4E78" android:strokeWidth="2.5" android:pathData="M 44 14 H 106 V 76 H 44 Z" />\n')
xml.append('        </group>\n    </group>\n')
# Texto horneado
xml.append(letra(LETRA_E, AZUL_EN))
xml.append(f'    <path android:strokeColor="{AZUL_EN}" android:strokeWidth="9" android:pathData="{guiones_transformados(138, 74, 161, 10, 8, texto_xy)}" />\n')
xml.append(letra(LETRA_N1, AZUL_EN))
xml.append(letra(LETRA_Z, AZUL_ZONA))
xml.append(letra(LETRA_O, AZUL_ZONA))
xml.append(letra(LETRA_N2, AZUL_ZONA))
xml.append(letra(LETRA_A, AZUL_ZONA))
xml.append('</vector>\n')

import os
os.makedirs(os.path.dirname(SALIDA), exist_ok=True)
with open(SALIDA, "w", encoding="utf-8", newline="\n") as f:
    f.write("".join(xml))
print("escrito", SALIDA, os.path.getsize(SALIDA), "bytes")
