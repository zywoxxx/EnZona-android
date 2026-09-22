# Notas técnicas del rediseño · Etapas 1 y 2

Fecha: 11 de septiembre de 2026. Alcance: tema y componentes compartidos (etapa 1) y
flujo del asistente (etapa 2): Inicio, filtros, detalle, asientos, confirmación gratuita,
pago, Mis boletos y boleto QR. Las etapas 3 (identidad y cuenta) y 4 (personal) siguen con
sus pantallas originales; solo reciben el tema y los componentes compartidos.

Diferencia de alcance registrada: los documentos académicos describen una aplicación
web React + Vite y excluyen una app nativa. Por decisión del propietario, este trabajo se
hizo sobre la app Android existente en Kotlin y Jetpack Compose. Spring Boot y PostgreSQL
son el backend previsto; no se ejecutan en el teléfono.

## 1. Fuentes revisadas

| Fuente | Estado | Uso |
| --- | --- | --- |
| `docs/DOC_MAESTRO_UV.docx` | Leído (texto y 3 imágenes: logo UV, portada) | Contexto y arquitectura. El logo de la Universidad Veracruzana no se usó como marca de EnZona. |
| `docs/vision_y_alcance.docx` | Leído | Actores, alcance, restricciones (no almacenar tarjetas, Profeco, LFPDPPP). |
| `docs/requisitos_y_pruebas_EnZona (1).docx` | Leído, con sus 4 diagramas (casos de uso, dos de actividad, estados del boleto) | RF-01 a RF-18, RNF-01 a RNF-09, CP-01 a CP-16. |
| `docs/WhatsApp Image 2026-09-01 at 07.36.23.jpeg` | Leído | Diagrama relacional: usuario, rol, usuario_rol, evento, categoria, tipo_boleto, asiento, orden, pago, boleto, validacion_acceso. |
| `README.md`, `esquema_metodo_pago.sql`, `data/remote/ApiService.kt` | Leídos | Decisiones de la demo, extensión `metodo_pago`, contrato Retrofit (no conectado). |
| `docs/prompt_enzona_diseno_android.md` | Leído | Brief de esta tarea. |

No había `esquema_enzona.sql` en el repositorio; el modelo se tomó de la imagen y de
`data/model/Models.kt`. No existe repositorio Git en la carpeta, así que no había cambios
previos que preservar; se guardó una copia del código original en el scratchpad de la
sesión antes de editar.

## 2. Decisiones visuales

- **Paleta.** Se conservó la identidad de los mockups (noche morada `#14092B`, naranja
  `#FF7A3C`, amarillo `#FFC145`). Ajustes de contraste: el texto sobre naranja pasó de blanco
  (2.6:1, insuficiente) a `#2A1200` (6.8:1); el texto secundario subió a `#B9AEDA` (8:1 sobre
  superficie). Se añadieron contenedores y tonos "sobre" para cada color.
- **Semántico ≠ marca.** Éxito (verde), aviso (amarillo) y error (rojo) viven en
  `EnZonaTema.semanticos` y en el `ColorScheme`; los precios ya no se pintan de amarillo ni la
  disponibilidad de verde.
- **Tema único oscuro.** No se implementó tema claro ni color dinámico: la marca debe verse
  igual en todos los teléfonos. El tema claro queda pendiente y documentado aquí.
- **Tipografía.** Roboto del sistema con escala propia (nada por debajo de 11.5 sp). No se
  incorporaron fuentes descargables para no depender de Google Play Services ni de red.
- **Formas.** Campos 12 dp, tarjetas 20 dp, hojas 28 dp, botones píldora; el boleto usa una
  forma propia con muescas. Se eliminaron los gradientes decorativos.
- **Portadas.** Ni `evento` ni `EventoDto` tienen imagen. `PortadaEvento` acepta `imagen`
  (Painter) y `cargando`, y mientras no haya datos dibuja un panel plano por categoría con la
  fecha como bloque tipográfico. Cuando el backend entregue una URL habrá que añadir un
  cargador (p. ej. Coil), no incluido para no ampliar el stack sin necesidad.
- **Copy.** Español de México, MXN, sin urgencia inventada, sin mayúsculas sostenidas,
  acciones nombradas por lo que hacen ("Confirmar asistencia", "Pagar $220.00 MXN").

## 3. Archivos principales

Nuevos:
- `ui/theme/Dimensiones.kt` — espaciado y tamaños de control.
- `ui/components/Etiquetas.kt` — `Etiqueta`, `EtiquetaEstadoBoleto`, `EtiquetaAgotado`, `Tono`.
- `ui/components/Superficies.kt` — `TarjetaEnZona`, `Aviso`, `ZonaDemostracion`, `FilaDato`, `FilaImporte`.
- `ui/components/Portada.kt` — `PortadaEvento`, `BloqueFecha`.
- `ui/components/Estados.kt` — `BarraSuperiorEnZona`, `EstadoVacio`, `IndicadorCarga`, `SelectorDesplegable`.
- `ui/preview/Fixtures.kt` — datos de `@Preview` (identificados como fixtures).
- `data/FiltrosEventos.kt` — lógica pura de búsqueda y filtros (RF-06, RF-07).
- `util/Formato.kt`, `util/FechaEvento.kt` — precios MXN y lectura tolerante de la fecha.
- `app/src/test/.../FiltrosEventosTest.kt`, `FechaYFormatoTest.kt` — 12 pruebas unitarias.

Reescritos: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/components/Comunes.kt` (mismas
firmas públicas, ahora leen del tema), `ui/screens/HomeScreen.kt`, `EventDetailScreen.kt`,
`SeatMapScreen.kt`, `CheckoutScreen.kt`, `TicketsScreen.kt`, `TicketDetailScreen.kt`.

`MainActivity.kt`: la barra inferior toma colores del tema; se añadieron dos callbacks
mínimos (`onVerBoletos` en el detalle y `onExplorar` en Mis boletos). Rutas y permisos por
rol no cambian.

Sin cambios: `MockRepository`, `SessionManager`, `Models`, `ApiService`, `QrGenerator`
(el contenido del QR sigue siendo `codigo.firma`), todas las pantallas de auth, ajustes,
organizador, validador y administración.

## 4. Comportamiento conservado y qué cambió en cada pantalla

- **Inicio.** Zona visible y cambiable a mano (sin GPS). Búsqueda por texto; chips Gratis,
  Fecha, Lugar, Tipo, De pago; contador y "Limpiar filtros". Los filtros se guardan con
  `rememberSaveable` y sobreviven al ir al detalle y volver (comprobado en emulador).
- **Detalle.** Portada, título, etiquetas, filas de dato (fecha, sede y dirección,
  organizador, cupo), descripción, tipos de boleto solo en eventos de pago, una sola acción
  fija abajo con el total final. Estados: cancelado, realizado, agotado, "ya tienes boleto".
- **Confirmación gratuita (RF-10).** Ahora hay un diálogo explícito con evento, fecha,
  lugar, cupo y la frase "no se realiza ningún cobro". El resultado se muestra abriendo el
  boleto; el error queda dentro del diálogo y es recuperable.
- **Asientos (RF-09).** Se conservó el plano existente (escenario, secciones, curvatura,
  pasillo, un scroll por sección). Se añadieron: leyenda con forma + color + glifo
  (número / × / ✓), controles de zoom de 48 dp con "restablecer", selección alternativa por
  sección, fila y número (solo asientos libres), semántica por butaca para lectores de
  pantalla y aviso recuperable si la disponibilidad cambia. Elegir en pantalla no reserva:
  se dice en la propia pantalla.
- **Pago (RF-11).** Resumen de orden (evento, fecha, lugar, tipo, asiento, cantidad),
  desglose y total final, métodos reales del repositorio (tarjetas guardadas, OXXO, SPEI),
  estados listo → procesando → aprobado | rechazado con reintento que conserva la selección.
  El interruptor "Simular pago rechazado" quedó dentro de una "Zona de demostración"
  delimitada.
- **Mis boletos y boleto QR (RF-12).** Lista en "Vigentes" y "Anteriores" con estado por
  icono + texto + color. El boleto es un talón con muescas: datos arriba, QR sobre blanco con
  margen y sin superposiciones abajo, código de referencia y estado. El botón dice "Enviar con
  mi app de correo" y, tras usarlo, un aviso aclara que se abrió la app de correo y que
  EnZona aún no envía boletos por sí misma. No se muestran datos personales.

## 5. Diferencias de negocio y servicios registradas (no se modificó el comportamiento)

- **OTP.** RF-02 pide verificar correo y teléfono; Visión y Alcance presenta el teléfono
  como opcional. Se conservó el flujo actual (OTP visible en pantalla como demo). Aclarar
  el requisito antes de tocar la activación de cuentas.
- **Personal por evento.** `usuario_rol` asigna roles globales; no vincula validador con
  evento. Designar o quitar personal desde el panel concede o revoca el rol VALIDADOR global.
  Falta un contrato de autorización por evento.
- **Métodos guardados.** `metodo_pago` no está en la imagen del esquema; existe como
  extensión en `esquema_metodo_pago.sql` (marca, últimos 4, vencimiento y token; sin número
  ni CVV). La UI de pago la consume tal cual.
- **HMAC y offline.** La demo firma en el teléfono con una clave de prueba. La clave de
  producción debe vivir solo en el backend; verificar HMAC en el dispositivo requeriría
  compartirla. La estrategia offline compatible con el backend sigue pendiente.
- **Uso único offline (CP-14).** El "modo sin conexión" evita repetidos en un dispositivo;
  no demuestra prevención global entre varios validadores desconectados.
- **Estado de pago PENDIENTE.** `EstadoPago` solo tiene APROBADO, RECHAZADO y REEMBOLSADO.
  En la demo, OXXO y SPEI se aprueban al instante; en producción, generar una referencia no
  es un pago aprobado y el boleto se emite al confirmar la pasarela. Se dejó el
  comportamiento y se rotuló como simulación; se necesitará una pantalla de pago pendiente
  cuando exista ese estado en el contrato.
- **Tipo de boleto vs sección.** `tipo_boleto` no está ligado a `asiento.seccion`: hoy se
  puede comprar "Balcón" y elegir una butaca de "Planta baja" (comportamiento previo). Además
  los contadores de `tipo_boleto` de la demo no descuentan las butacas ocupadas de muestra
  (Planta baja muestra 180 disponibles con 165 asientos libres en total). Ambas cosas son
  del modelo o de los datos de muestra, no de la UI.
- **Fecha del evento.** `Evento.fecha` es texto libre ("Sáb 3 Oct · 19:00"); el esquema
  tiene `fecha_hora_inicio` y `fecha_hora_fin`. `FechaEvento` lo interpreta de forma
  tolerante solo para filtrar; si el texto no se entiende, el evento no participa del filtro
  de fecha. Con la API real conviene usar fechas reales y la zona horaria del evento.
- **Ciudad del evento.** No hay columna de ciudad; la "zona" elegida en Inicio se guarda como
  preferencia (`MockRepository.ciudad`) y no filtra. El filtro de lugar usa la sede.
- **Imagen de portada.** No existe en el esquema ni en `EventoDto` (ver §2).
- **Envío por correo.** No hay servicio; el botón abre la app de correo del teléfono y la UI
  lo dice. No se escribe "Correo enviado".
- **Idempotencia.** Deshabilitar el botón durante el proceso evita dobles toques, pero no
  demuestra idempotencia ni control de concurrencia del servidor (RNF-05, CP-11).
- **Rotación durante el pago simulado.** El cobro corre en el `rememberCoroutineScope` de la
  pantalla; una rotación en mitad de los 1.4 s de simulación lo cancela sin cobrar y sin
  aviso. Con un ViewModel y el backend real esto debe manejarse fuera del composable.
- **Servicios incompletos.** Todo vive en memoria: sin Room, sin JWT, sin pasarela, sin
  reembolsos reales ni transacción de aforo. Al cerrar la app se reinician los datos.

## 6. Verificación

Comandos (Windows, JDK 17 porque el JDK 11 del sistema y el JBR 25 de Android Studio no
sirven para AGP 8.7 / Gradle 8.14):

```
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat assembleDebug testDebugUnitTest
```

Resultado: BUILD SUCCESSFUL; 12 pruebas unitarias en verde (`FiltrosEventosTest`,
`FechaYFormatoTest`). No hay pruebas instrumentadas: añadirlas requeriría dependencias de
`androidx.test` que el proyecto no tiene.

Comprobado en emulador (Pixel 10 Pro XL, API 35, 1344×2992, 480 dpi), capturas en
`docs/capturas/`:

| Captura | Qué se comprobó |
| --- | --- |
| 01_login | Pantalla de etapa 3 con los componentes compartidos nuevos (sin regresión). |
| 02_inicio, 03_filtro_lugar, 04_filtro_gratis, 05_zona | Lista, menú de lugar, chip activo con contador y "Limpiar", diálogo de zona manual. |
| 06_detalle_gratis, 07_dialogo_confirmar, 08_boleto_qr | CP-08 (parte del asistente): confirmación explícita, boleto emitido, aforo 142 → 141. |
| 09_inicio_tras_volver | Filtro "Gratis" conservado al volver del detalle. |
| 10_detalle_pago, 11_asientos, 12_asiento_elegido, 13_elegir_por_numero | Tipos de boleto, plano, selección por toque y por número. |
| 14_pago, 16_pago_procesando, 17_pago_rechazado | Resumen, desglose, método, procesando, rechazado (representación de CP-10: sin boleto, aforo intacto, reintento). |
| 19_boleto_con_asiento | Representación de CP-09 con la pasarela simulada; boleto con sección, fila y número. |
| 20_mis_boletos, 27_mis_boletos_usado | Lista con boletos vigentes y, tras validarlo en puerta, el mismo boleto en «Anteriores» como Usado. |
| 22_detalle_sin_hueco, 23_detalle_ya_tiene | Corrección del inset duplicado de la barra superior; estado «Ya tienes un boleto» con enlace a Mis boletos. |
| 25_validador_permitido, 26_validador_ya_usado | CP-12 y CP-13 con el validador original: el QR del boleto rediseñado se acepta una vez y se rechaza la segunda (payload `codigo.firma` intacto). |
| 28/29_texto_grande | Inicio y detalle con texto del sistema al 130 %. |
| 30/31_angosto | Inicio y detalle en 720×1600 a 320 dpi (ventana angosta). |

Simulado (no aprobado contra servicios reales): CP-09 y CP-10 usan la pasarela simulada;
CP-11 (concurrencia), CP-14 (offline con sincronización) y CP-16 (notificación y
reembolso) no se pueden dar por cubiertos con el mock. RNF-04, RNF-05 y RNF-07 requieren
evidencias propias que compilar Android no aporta.

No ejecutado: prueba en dispositivo físico, orientación horizontal (la app no la
bloquea, pero no se validó), lector de pantalla TalkBack de extremo a extremo (se añadió
semántica, no se auditó con TalkBack).

## 7. Pendiente para etapas 3 y 4

- Migrar login, registro, OTP, perfil y ajustes a `BarraSuperiorEnZona`, `TarjetaEnZona` y
  `Aviso` (hoy usan `TopAppBar` propia con colores directos; funcionan, pero suman dos veces
  el inset de la barra de estado, igual que antes del rediseño).
- Panel del organizador, validador y administración: mismas piezas; distinguir borrador,
  publicado, realizado y cancelado con `Etiqueta`; resultados del validador con icono + texto.
- `EtiquetaEstado(EstadoEvento)` sigue en `OrganizerHomeScreen.kt` porque la usa el panel;
  al llegar a la etapa 4 conviene moverla a `ui/components/Etiquetas.kt`.
- Tema claro, si el producto lo quiere.
