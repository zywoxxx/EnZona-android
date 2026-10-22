# -*- coding: utf-8 -*-
"""Construye el plugin de Figma, el tablero HTML y el README a partir de los SVG generados."""
import json, os, html

BASE = os.path.dirname(os.path.abspath(__file__))
SVG = os.path.join(BASE, 'svg'); PLUG = os.path.join(BASE, 'plugin')
os.makedirs(PLUG, exist_ok=True)
m = json.load(open(os.path.join(SVG, 'manifest.json'), encoding='utf-8'))
for e in m:
    e['svg'] = open(os.path.join(SVG, e['file']), encoding='utf-8').read()

# ------------------------------------------------------------------ plugin
manifest = {
    "name": "EnZona v3 · Importar mockups",
    "id": "enzona-v3-mockups-importer",
    "api": "1.0.0",
    "main": "code.js",
    "editorType": ["figma"],
    "documentAccess": "dynamic-page",
    "networkAccess": {"allowedDomains": ["none"]}
}
json.dump(manifest, open(os.path.join(PLUG, 'manifest.json'), 'w', encoding='utf-8'), ensure_ascii=False, indent=2)

code = r'''// EnZona v3 · Importar mockups
// Crea una página «EnZona v3 · Mockups» y coloca la portada, la guía de estilo y las 16 pantallas
// como capas vectoriales nativas (figma.createNodeFromSvg), agrupadas por sección.
const MOCKUPS = __MOCKUPS__;

const GAP = 72, LABEL_H = 56, SECTION_PAD = 48;
const C = { marino: {r:0.122,g:0.227,b:0.373}, gris: {r:0.365,g:0.427,b:0.494}, fondo: {r:0.933,g:0.945,b:0.965} };

async function loadFonts() {
  for (const style of ['Regular', 'Medium', 'Bold']) {
    try { await figma.loadFontAsync({ family: 'Roboto', style }); } catch (e) { /* Roboto viene con Figma */ }
  }
}

function label(text, size, style, color) {
  const t = figma.createText();
  t.fontName = { family: 'Roboto', style };
  t.characters = text;
  t.fontSize = size;
  t.fills = [{ type: 'SOLID', color }];
  return t;
}

async function run() {
  await loadFonts();
  const page = figma.createPage();
  page.name = 'EnZona v3 · Mockups';
  await figma.setCurrentPageAsync(page);

  const created = [];
  let y = 0;

  // Portada y guía de estilo
  const portada = MOCKUPS.filter(mk => mk.section === 'Portada');
  let x = 0;
  for (const mk of portada) {
    const node = figma.createNodeFromSvg(mk.svg);
    node.name = mk.name;
    node.x = x; node.y = y;
    page.appendChild(node);
    created.push(node);
    y += mk.h + GAP;
  }

  // Secciones con pantallas
  const sections = [];
  for (const mk of MOCKUPS) {
    if (mk.section === 'Portada') continue;
    if (!sections.includes(mk.section)) sections.push(mk.section);
  }
  for (const sec of sections) {
    const items = MOCKUPS.filter(mk => mk.section === sec);
    const title = label(sec, 28, 'Bold', C.marino);
    title.x = 0; title.y = y;
    page.appendChild(title);
    const rowY = y + 56;
    let sx = 0;
    const nodes = [];
    for (const mk of items) {
      const node = figma.createNodeFromSvg(mk.svg);
      node.name = mk.name;
      node.x = sx; node.y = rowY + LABEL_H;
      page.appendChild(node);
      const t1 = label(mk.name, 16, 'Medium', C.marino);
      t1.x = sx; t1.y = rowY;
      const t2 = label(mk.desc, 12, 'Regular', C.gris);
      t2.x = sx; t2.y = rowY + 24;
      t2.textAutoResize = 'HEIGHT'; t2.resize(mk.w, 28);
      page.appendChild(t1); page.appendChild(t2);
      nodes.push(node, t1, t2);
      created.push(node);
      sx += mk.w + GAP;
    }
    // Sección de Figma (si la API la soporta)
    try {
      const section = figma.createSection();
      section.name = sec;
      section.x = -SECTION_PAD; section.y = y - SECTION_PAD;
      section.resizeWithoutConstraints(sx - GAP + SECTION_PAD * 2, 56 + LABEL_H + 917 + SECTION_PAD * 2);
      section.fills = [{ type: 'SOLID', color: C.fondo, opacity: 0.6 }];
      page.appendChild(section);
      for (const n of [title, ...nodes]) section.appendChild(n);
      // appendChild reubica respecto a la sección
      title.x = SECTION_PAD; title.y = SECTION_PAD;
      let k = 0, cx = SECTION_PAD;
      for (const mk of items) {
        const node = nodes[k], t1 = nodes[k + 1], t2 = nodes[k + 2];
        t1.x = cx; t1.y = SECTION_PAD + 56;
        t2.x = cx; t2.y = SECTION_PAD + 56 + 24;
        node.x = cx; node.y = SECTION_PAD + 56 + LABEL_H;
        cx += mk.w + GAP; k += 3;
      }
    } catch (e) { /* versiones sin secciones: quedan sueltos en la página */ }
    y += 56 + LABEL_H + 917 + GAP + SECTION_PAD;
  }

  figma.viewport.scrollAndZoomIntoView(created);
  figma.closePlugin(`EnZona v3: ${created.length} mockups importados en la página «${page.name}»`);
}

run().catch(e => figma.closePlugin('Error al importar: ' + e.message));
'''
payload = json.dumps([{k: e[k] for k in ('file', 'name', 'desc', 'section', 'w', 'h', 'svg')} for e in m], ensure_ascii=False)
open(os.path.join(PLUG, 'code.js'), 'w', encoding='utf-8').write(code.replace('__MOCKUPS__', payload))

# ------------------------------------------------------------------ tablero HTML
sections = []
for e in m:
    if e['section'] not in sections: sections.append(e['section'])
blocks = []
for sec in sections:
    items = [e for e in m if e['section'] == sec]
    cards = ''.join(
        f'<figure class="card" style="--w:{e["w"]}px"><div class="shot">{e["svg"]}</div>'
        f'<figcaption><b>{html.escape(e["name"])}</b><span>{html.escape(e["desc"])}</span><code>{html.escape(e["file"])}</code></figcaption></figure>'
        for e in items)
    blocks.append(f'<section><h2>{html.escape(sec)}</h2><div class="row">{cards}</div></section>')
board = f'''<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>EnZona v3 · Mockups</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link href="https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap" rel="stylesheet">
<style>
:root{{--bg:#EEF1F6;--sup:#fff;--tinta:#1B2432;--gris:#5D6D7E;--marino:#1F3A5F;--linea:#D9DFEA}}
@media (prefers-color-scheme:dark){{:root:not([data-theme="light"]){{--bg:#141A24;--sup:#1E2734;--tinta:#EEF1F6;--gris:#9AA6B6;--marino:#B9C9DD;--linea:#2E3948}}}}
:root[data-theme="dark"]{{--bg:#141A24;--sup:#1E2734;--tinta:#EEF1F6;--gris:#9AA6B6;--marino:#B9C9DD;--linea:#2E3948}}
*{{box-sizing:border-box}}body{{margin:0;background:var(--bg);color:var(--tinta);font-family:Roboto,system-ui,sans-serif;padding:24px 16px 64px}}
header{{max-width:1400px;margin:0 auto 24px}}h1{{font-size:28px;margin:0 0 6px}}header p{{color:var(--gris);margin:0;font-size:14px}}
section{{max-width:1400px;margin:0 auto 40px}}h2{{font-size:18px;color:var(--marino);margin:0 0 12px;border-bottom:1px solid var(--linea);padding-bottom:8px}}
.row{{display:flex;gap:20px;overflow-x:auto;padding:4px 4px 12px}}
.card{{margin:0;flex:0 0 auto;background:var(--sup);border:1px solid var(--linea);border-radius:16px;padding:12px}}
.shot{{width:min(var(--w),82vw);overflow:hidden;border-radius:12px}}.shot svg{{width:100%;height:auto;display:block}}
figcaption{{display:flex;flex-direction:column;gap:2px;margin-top:10px;max-width:min(var(--w),82vw)}}figcaption b{{font-size:14px}}figcaption span{{font-size:12px;color:var(--gris)}}figcaption code{{font-size:11px;color:var(--gris)}}
</style></head><body>
<header><h1>EnZona v3 · Mockups</h1><p>Portada, guía de estilo y 16 pantallas (marco Android 412 × 917) generadas desde <code>docs/capturas_v3</code> con la paleta v2. Los mismos SVG se importan a Figma con el plugin de <code>docs/figma_v3/plugin</code>.</p></header>
{''.join(blocks)}
</body></html>'''
open(os.path.join(BASE, 'tablero.html'), 'w', encoding='utf-8').write(board)

# ------------------------------------------------------------------ README
tabla = '\n'.join(f'| `{e["file"]}` | {e["section"]} | {e["desc"]} |' for e in m)
readme = f'''# Mockups de EnZona v3 para Figma

Mockups de las 16 pantallas de la revisión v3 (cancelación con regla de negocio, rol de
organizador, ubicación en mapa y fecha/hora), más portada y guía de estilo. Se generan desde
las capturas `docs/capturas_v3/` con la paleta v2 (`ui/theme/Color.kt`) en un marco Android de
412 × 917 (preajuste «Android Compact» de Figma). Cada SVG conserva capas con nombre (barra de
estado, tarjetas, botones, diálogos), texto editable en Roboto y colores exactos de la paleta.

## Cómo llevarlos a Figma

**Opción A · Plugin (recomendada, todo en un clic).** Requiere la app de escritorio de Figma
(instalada con `winget install Figma.Figma`).

1. Abre Figma Desktop e inicia sesión. Crea o abre un archivo de diseño.
2. Menú **Plugins → Development → Import plugin from manifest…** y elige
   `docs/figma_v3/plugin/manifest.json`.
3. **Plugins → Development → EnZona v3 · Importar mockups**. El plugin crea la página
   «EnZona v3 · Mockups» con la portada, la guía de estilo y una sección por requisito
   (Cancelación RF-14, Cancelación administrativa RF-18, Rol RF-04, Ubicación RF-08/RF-19,
   Fecha y hora RF-08), cada pantalla con su título y descripción.

**Opción B · Arrastrar los SVG.** Selecciona todos los archivos de `docs/figma_v3/svg/` y
suéltalos sobre el lienzo de Figma (o **Shift+Ctrl+K → Place image**). Figma los importa como
marcos vectoriales editables; la carpeta `manifest.json` indica el orden y la sección de cada uno.

**Opción C · Pegar.** Abre un SVG en un editor de texto, copia todo su contenido y pégalo con
Ctrl+V en el lienzo: Figma reconoce el SVG del portapapeles.

## Vista previa sin Figma

Abre `docs/figma_v3/tablero.html` en el navegador: muestra los 18 mockups agrupados por sección.

## Archivos

| Archivo | Sección | Qué muestra |
|---|---|---|
{tabla}

## Regenerar

Los mockups se construyen con un script de Python (sin dependencias externas). Si cambia una
pantalla, ajusta el script y vuelve a ejecutarlo; después ejecuta el constructor del plugin para
regenerar `plugin/code.js` y `tablero.html`.

```
python docs/figma_v3/gen_mockups.py
python docs/figma_v3/build_plugin.py
```

Los diálogos siguen los textos reales de `ui/components/DialogosEvento.kt` y `DialogosRol.kt`;
los importes ($360.00 reembolso, $36.00 comisión) son los de la captura
`02_dialogo_cancelar_pago_comision.png` (CP-CANCEL-02 con los datos de muestra).
'''
open(os.path.join(BASE, 'README.md'), 'w', encoding='utf-8').write(readme)
print('plugin, tablero y README listos;', len(m), 'mockups;', os.path.getsize(os.path.join(PLUG, 'code.js')), 'bytes en code.js')
