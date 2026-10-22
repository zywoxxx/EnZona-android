# Mockups de EnZona v3 para Figma

Mockups de las 16 pantallas de la revisión v3 (cancelación con regla de negocio, rol de
organizador, ubicación en mapa y fecha/hora), más portada y guía de estilo. Se generan desde
las capturas `docs/capturas_v3/` con la paleta v2 (`ui/theme/Color.kt`) en un marco Android de
412 × 917 (preajuste «Android Compact» de Figma). Cada SVG conserva capas con nombre (barra de
estado, tarjetas, botones, diálogos), texto editable en Roboto y colores exactos de la paleta.

## Archivo en Figma

Ya importados (28 de septiembre de 2026) en el archivo **EnZona v3 · Mockups** de la carpeta del
equipo: <https://www.figma.com/design/pwOaAmFr6hCRHbxTcC0hWg/EnZona-v3-%C2%B7-Mockups>.
Estructura: marco «EnZona v3 · Mockups» → grupos por sección (Cancelación de eventos RF-14,
Cancelación administrativa RF-18, Rol de organizador RF-04, Ubicación en mapa RF-08/RF-19, Fecha y
hora RF-08, Resumen de administración RF-18) → una pantalla por grupo de 412 × 917 con capas nombradas y texto editable (Roboto).
Se importó pegando `enzona_v3_mockups_todo.svg` (generado por `build_combined.py`) en el lienzo.

## Resumen de administración (RF-18) · añadido el 19 de octubre de 2026

La pestaña **Resumen** del panel de administración (indicadores, cuentas, eventos por estado,
categoría y mes, ingresos y ocupación; `ui/screens/admin/EstadisticasAdminScreen.kt`) se dibujó
directamente en el archivo de Figma, no desde SVG: es una sección nueva «Resumen de administración
(RF-18)» al final del marco «EnZona v3 · Mockups», construida con auto layout, texto Roboto
editable y los colores de la paleta v2, para que el equipo pueda cambiar cifras y etiquetas sin
regenerar nada.

| Pantalla | Qué muestra |
|---|---|
| 17 · Admin resumen (indicadores) | Cabecera y pestañas, seis indicadores (usuarios, eventos, ingresos, boletos, accesos, ocupación), barra segmentada de cuentas y eventos por estado |
| 18 · Admin resumen (gráficas) | La misma pantalla desplazada: eventos por categoría, barras por mes de inicio, ingresos por evento y ocupación de los publicados |

Las cifras son de muestra (12 usuarios, 6 eventos, $4,320.00 de ingresos); las reales de la demo
están en `docs/capturas_api/21_admin_resumen.png` y `21b_admin_ingresos.png`. Captura de la
sección: `17_18_admin_resumen_figma.png`. Esta sección no forma parte de `gen_mockups.py`, así que
`build_plugin.py` y `build_combined.py` no la regeneran ni la borran.

## Cómo llevarlos a Figma (si hay que repetirlo)

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

**Opción C · Pegar (la usada).** Copia el contenido de `enzona_v3_mockups_todo.svg` (o de un SVG
suelto) al portapapeles y pégalo con Ctrl+V en el lienzo: Figma reconoce el SVG del portapapeles y
crea todo de una vez. Si aparece «Falta la fuente Roboto», elige Roboto en el reemplazo.

## Vista previa sin Figma

Abre `docs/figma_v3/tablero.html` en el navegador: muestra los 18 mockups agrupados por sección.

## Archivos

| Archivo | Sección | Qué muestra |
|---|---|---|
| `00_portada.svg` | Portada | EnZona v3 · Mockups |
| `00_guia_de_estilo.svg` | Portada | Paleta v2, tipografía y componentes |
| `01_detalle_mapa_como_llegar.svg` | Ubicación en mapa (RF-08 · RF-19) | Detalle del evento · minimapa y «Cómo llegar» |
| `02_dialogo_cancelar_pago_comision.svg` | Cancelación de eventos (RF-14) | Diálogo del organizador · cancelar evento de pago (reembolso $360 · comisión $36) |
| `03_panel_evento_cancelado_cargo.svg` | Cancelación de eventos (RF-14) | Panel del evento cancelado · cargo pendiente al organizador |
| `04_form_fecha_hora.svg` | Fecha y hora con selectores (RF-08) | Nuevo evento · selectores de fecha y hora (sin texto libre) |
| `05_calendario_sin_pasado.svg` | Fecha y hora con selectores (RF-08) | Calendario Material 3 · fechas pasadas deshabilitadas |
| `06_hora_24h.svg` | Fecha y hora con selectores (RF-08) | Selector de hora en 24 h con entrada numérica |
| `07_mapa_marcador.svg` | Ubicación en mapa (RF-08 · RF-19) | Selector de ubicación (osmdroid) · marcador y dirección aproximada |
| `08_dialogo_dejar_organizador.svg` | Rol de organizador (RF-04) | Perfil · diálogo «Dejar de ser organizador» |
| `09_mis_boletos_evento_cancelado.svg` | Cancelación de eventos (RF-14) | Mis boletos · «Evento cancelado · reembolso en proceso» |
| `10_boleto_evento_cancelado.svg` | Cancelación de eventos (RF-14) | Tu boleto · QR anulado por evento cancelado |
| `11_dialogo_convertirse.svg` | Rol de organizador (RF-04) | Perfil · diálogo «¿Convertirte en organizador?» |
| `12_snackbar_ahora_organizador.svg` | Rol de organizador (RF-04) | Inicio con pestaña «Mis eventos» y Snackbar «Ahora eres organizador» |
| `13_validador_evento_cancelado.svg` | Cancelación de eventos (RF-14) | Validador · QR rechazado con «Evento cancelado» |
| `14_admin_dialogo_motivo.svg` | Cancelación administrativa (RF-18) | Administración · cancelación por moderación, motivo obligatorio (botón deshabilitado) |
| `15_admin_motivo_capturado.svg` | Cancelación administrativa (RF-18) | Administración · motivo capturado, botón habilitado |
| `16_admin_resultado.svg` | Cancelación administrativa (RF-18) | Administración · evento cancelado por moderación (sin comisión) |

## Regenerar

Los mockups se construyen con un script de Python (sin dependencias externas). Si cambia una
pantalla, ajusta el script y vuelve a ejecutarlo; después ejecuta el constructor del plugin para
regenerar `plugin/code.js` y `tablero.html`.

```
python docs/figma_v3/gen_mockups.py
python docs/figma_v3/build_plugin.py
python docs/figma_v3/build_combined.py
```

Los diálogos siguen los textos reales de `ui/components/DialogosEvento.kt` y `DialogosRol.kt`;
los importes ($360.00 reembolso, $36.00 comisión) son los de la captura
`02_dialogo_cancelar_pago_comision.png` (CP-CANCEL-02 con los datos de muestra).
