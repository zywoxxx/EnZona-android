# Notas técnicas v3 · Cancelación, rol de organizador, mapa y fecha/hora

Fecha: 17 de septiembre de 2026. Complementa `notas_v2_compra_multiple.md`. Sin repositorio Git
en la carpeta: la comparación se hizo contra las notas v2 y una copia del código previo tomada
antes de editar (`backup_v2_final` en el scratchpad de la sesión). Stack sin cambios: Gradle
8.14.3, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, JDK 17 para compilar, `minSdk 26`.

## 1. Fuentes revisadas

| Fuente | Estado | Qué aporta |
| --- | --- | --- |
| `docs/prompt_enzona_v3_cancelacion_rol_mapa_fecha.md` | Leído | Los cinco cambios de la revisión. |
| `docs/vision_y_alcance_v2.docx` | Leído y comparado con v1 | Alcance multiplataforma, Android 8.0+, ubicación con consentimiento y ciudad manual. No menciona comisión de cancelación. |
| `docs/requisitos_y_pruebas_v2.1.docx` | Leído y comparado con v2 | Historial de versiones; RF-09/11/12/16 ampliados; CP-MULTI-01..12 incorporados. RF-14 sigue diciendo solo «cancelar y notificar». |
| `docs/Anexo_E_Arquitectura_EnZona.docx` | Leído | `POST /api/eventos/{id}/cancelacion` en la tabla E.4; módulo Pagos gestiona reembolsos; reglas de integridad de `evento` (coordenadas ambas o ninguna, fin ≥ inicio). |
| `docs/esquema_enzona_v2.sql`, `docs/migracion_v2_incremental.sql` | Leídos, **no ejecutados** | Base para `migracion_v3_incremental.sql`. |
| `docs/notas_v2_compra_multiple.md`, `README.md` | Leídos | Estado previo. |

## 2. Tabla de hallazgos

| Cambio | Dónde vivía | Estado antes | Acción |
| --- | --- | --- | --- |
| 1 · Regla de cancelación | `MockRepository.cancelarEvento(eventoId): Int` cancelaba cualquier evento sin política; `EventStatsScreen` y `AdminScreen` llamaban directo (el admin sin diálogo) | **Incompleto** | `politicaCancelacion()` pura en `data/model/Cancelacion.kt`; `cancelarEvento(eventoId, ejecutadoPor, motivo, aceptaComision, administrativa)` todo o nada; diálogo compartido `DialogoCancelarEvento`. |
| 1 · Persistencia | `Evento` sin campos de cancelación | **Incompleto** | Columnas `fecha_cancelacion`, `cancelado_por`, `motivo_cancelacion`, `comision_cancelacion` en `evento` + tablas `cargo_organizador` y `notificacion_asistente` (`migracion_v3_incremental.sql`). |
| 1 · Asistente y validador | Mis boletos mostraba «Cancelado»; `validarQr` decía «Boleto cancelado» | **Incompleto** | «Evento cancelado · reembolso en proceso» en lista y detalle; validador responde «Evento cancelado». |
| 2 · Confirmación de rol | `ProfileScreen`: `BotonEnZona` llamaba a `activarRolOrganizador` + `refrescar()` al toque | **Incompatible** (toque accidental irreversible) | `DialogoConvertirseOrganizador`, Snackbar «Ahora eres organizador», `DialogoDejarOrganizador`, `motivoNoPuedeDejarOrganizador()` y `dejarRolOrganizador()`; `SessionManager.refrescar()` vuelve al acceso normal si se pierde el rol de personal. |
| 3 · Ubicación en mapa | `EventFormScreen`: solo `lugar` y `direccion` en texto; `Evento.latitud/longitud` existían (v2) pero ni el formulario ni las semillas los usaban | **Incompleto** | osmdroid (sin clave), `SelectorUbicacionMapa`, `MiniMapaEvento` + «Cómo llegar» (intent `geo:`), coordenadas reales de Orizaba en las semillas, publicar exige coordenadas. |
| 4 · Fecha con calendario | `Evento.fecha: String` («Vie 25 Sep · 19:00»), `FechaEvento.parsear` tolerante, `CampoEnZona(fecha, …)` | **Incompatible** con `TIMESTAMPTZ` | `Evento.fecha: LocalDateTime` + `fechaFin`, `DialogoFecha` con `selectableDates`, formato único `FechaEvento.corto/largo/iso`, filtros por comparación temporal. |
| 5 · Hora numérica | Dentro del mismo texto libre | **Incompatible** | `DialogoHora` con `TimeInput` 24 h; `FechaHoraEvento` valida 00–23/00–59 y combina fecha+hora. |
| Clave de mapas | Ninguna en `local.properties`, Gradle ni manifiesto | No aplica | Se eligió osmdroid; no se incrusta ninguna clave. |
| Compra múltiple, paleta, navegación por rol | v2 | Correcto | Conservados; 12 CP-MULTI siguen en verde. |

## 3. Regla de negocio final (RF-14)

1. Un evento **que ya comenzó o terminó** (`fecha ≤ ahora`, REALIZADO o ya CANCELADO) no se cancela
   por ningún rol; el organizador puede marcarlo como finalizado.
2. **Borrador**: se cancela sin comisión.
3. **Gratuito** (ningún `tipo_boleto` con precio > 0): el organizador lo cancela mientras no haya
   comenzado; las confirmaciones pasan a CANCELADO y cada asistente recibe un aviso.
4. **De pago con órdenes aprobadas**: solo se cancela aceptando una comisión del **10 %** del importe
   cobrado (suma de `orden.total` en estado PAGADA), redondeada HALF_UP a 2 decimales. Los asistentes
   reciben el **100 %** de reembolso (orden → REEMBOLSADA, pago → REEMBOLSADO contra su
   `referencia_pasarela`); la comisión se registra como **cargo pendiente** al organizador.
5. **De pago sin ventas**: comisión 0; procede como gratuito y el diálogo lo indica.
6. **Administrativa (RF-18)**: reembolso 100 %, **motivo obligatorio**, sin comisión al organizador;
   se registra quién canceló y por qué.
7. La operación es completa o no hace nada: boletos → CANCELADO, asientos liberados, órdenes →
   REEMBOLSADA, pagos → REEMBOLSADO, registro en `evento`, cargo y avisos. No quedan boletos
   VÁLIDOS en un evento cancelado.

Única fuente de verdad: `politicaCancelacion(evento, tipos, ordenesAprobadas, boletosVigentes, ahora, administrativa)`.
La UI la muestra; el repositorio (y el backend) la vuelven a evaluar antes de aplicar.

## 4. Archivos y contratos

Nuevos: `data/model/Cancelacion.kt`, `util/FechaHoraEvento.kt`, `ui/components/DialogosEvento.kt`,
`DialogosRol.kt`, `FechaHoraSelector.kt`, `MapaUbicacion.kt`, `docs/migracion_v3_incremental.sql`,
pruebas `CancelacionEventoTest`, `RolOrganizadorTest`, `androidTest/DialogosTest`.

Modificados: `Models.kt` (fecha temporal, fin, registro de cancelación), `MockRepository.kt`
(semillas con fechas y coordenadas de Orizaba, `crearEvento/modificarEvento/publicarEvento`,
`politicaCancelacionDe`, `cancelarEvento`, `marcarRealizado`, `validarQr`, rol organizador),
`SessionManager.kt`, `FiltrosEventos.kt`, `FechaEvento.kt` (ahora formateador único),
`ApiService.kt`, `EventFormScreen.kt` (reescrito), `EventStatsScreen.kt`, `AdminScreen.kt`,
`ProfileScreen.kt`, `EventDetailScreen.kt`, `TicketsScreen.kt`, `TicketDetailScreen.kt`,
`MainActivity.kt`, `Portada.kt`, `Fixtures.kt`, pruebas existentes, `build.gradle.kts`,
`libs.versions.toml` (osmdroid 6.1.20 y pruebas instrumentadas), `AndroidManifest.xml` (permisos
de ubicación), `README.md`.

Contratos REST pendientes de backend (`ApiService.kt`):
- `GET /api/eventos/{id}/politica-cancelacion` → misma regla en servidor (RNF-10).
- `POST /api/eventos/{id}/cancelacion` `{motivo, aceptaComision}` → `{importeReembolsado, comision, boletosAnulados, asistentesNotificados, canceladoPor, fechaCancelacion}`; errores 409 iniciado, 422 comisión no aceptada / motivo ausente, 403 rol.
- `POST` y `DELETE /api/usuarios/me/roles/organizador` (409 con eventos vigentes o único rol).
- `EventoDto.fechaHoraInicio/fechaHoraFin` en ISO-8601 con zona (`2026-09-25T19:00:00-06:00`).

## 5. Ubicación (cambio 3)

osmdroid 6.1.20 (OpenStreetMap, ODbL, compatible con API 26); atribución visible «© OpenStreetMap
contributors» en el mapa y bajo él. Mapa centrado en Orizaba (18.851, −97.100, zoom 14); tocar o
arrastrar el marcador fija la ubicación con 5 decimales; Geocoder sugiere una dirección editable si
está disponible; «Usar mi ubicación actual» pide `ACCESS_COARSE_LOCATION` solo al pulsarlo y sin
él el mapa sigue usable (CP-18). Publicar exige coordenadas; el borrador no. Fuera del área de
estudio (> 12 km) se avisa sin bloquear. El detalle muestra una miniatura estática con marcador y
«Cómo llegar» (`geo:lat,lon?q=…`, sin app concreta); sin coordenadas solo se muestra la dirección.
Las teselas requieren red; en el emulador se descargan de los servidores de OSM (uso de demo).

## 6. Fecha y hora (cambios 4 y 5)

`Evento.fecha: LocalDateTime` en `America/Mexico_City` (+ `fechaFin` opcional), alineado con
`fecha_hora_inicio TIMESTAMPTZ`. Formato único en `FechaEvento`: `corto` («Vie 25 Sep · 19:00»),
`largo` («Vie 25 Sep 2026 · 19:00»), `intervalo`, `iso` / `desdeIso`. Calendario Material 3 con
`selectableDates` (pasado deshabilitado); hora con `TimeInput` 24 h; fin opcional no anterior al
inicio; si el evento tiene boletos, cambiar la fecha pide confirmación. Filtros de Inicio (hoy,
semana, mes, próximo mes) comparan fechas reales.

## 7. Verificación

Comandos (JDK 17): `gradlew.bat assembleDebug testDebugUnitTest` y `gradlew.bat connectedDebugAndroidTest`.

Pruebas unitarias: 48 en verde.

| Caso | Prueba | Resultado |
| --- | --- | --- |
| CP-CANCEL-01 gratuito con 3 confirmaciones | `CancelacionEventoTest` (política + repositorio) | 3 CANCELADO, comisión 0, 3 avisos |
| CP-CANCEL-02 de pago con $1,500 | ídem | reembolso 1500.00, comisión 150.00, órdenes REEMBOLSADA, pagos REEMBOLSADO, `comision_cancelacion` = 150.00, cargo pendiente |
| CP-CANCEL-03 rechazar comisión | ídem | sin cambios, evento PUBLICADO, boletos VÁLIDO |
| CP-CANCEL-04 de pago sin ventas | ídem | comisión 0 |
| CP-CANCEL-05 iniciado | ídem | política bloqueada y repositorio rechaza; `marcarRealizado` |
| CP-CANCEL-06 QR de evento cancelado | ídem | «Evento cancelado» |
| CP-CANCEL-07 administrativa | ídem | 100 % reembolso, motivo obligatorio, sin comisión, `cancelado_por` |
| Redondeo HALF_UP | ídem | 333.35 → 33.34; 99.99 → 10.00 |
| CP-ROL-01..04 + único rol/admin | `RolOrganizadorTest` | verde |
| CP-FECHA-02/03, CP-HORA-01/02 | `FechaYFormatoTest` | verde |
| CP-UBIC-01/02 (coordenadas 5 decimales, publicar sin ubicación) | `CancelacionEventoTest` | verde |
| CP-MULTI-01..12 (regresión) | `CompraMultipleTest` | verde |

Pruebas de Compose (emulador Pixel 10 Pro XL, Android 17 / API 37): `DialogosTest` — 7/7 en verde
(`connectedDebugAndroidTest`; hizo falta fijar `espresso-core` 3.7.0, ver `libs.versions.toml`).
Informe en `app/build/reports/androidTests/connected/`.

Capturas en `docs/capturas_v3/`:

| Archivo | Qué muestra |
|---|---|
| 01_detalle_mapa_como_llegar | Detalle con minimapa y botón «Cómo llegar» |
| 02_dialogo_cancelar_pago_comision | Diálogo del organizador: reembolso $360 y comisión $36 |
| 03_panel_evento_cancelado_cargo | Panel del evento cancelado con cargo pendiente |
| 04_form_fecha_hora / 05_calendario_sin_pasado / 06_hora_24h | Formulario y selectores de fecha y hora |
| 07_mapa_marcador | Selector de ubicación con marcador y dirección aproximada |
| 08_dialogo_dejar_organizador | Diálogo «Dejar de ser organizador» |
| 09_mis_boletos_evento_cancelado / 10_boleto_evento_cancelado | Boleto con «Evento cancelado · reembolso en proceso» |
| 11_dialogo_convertirse / 12_snackbar_ahora_organizador | Confirmación de rol y Snackbar «Ahora eres organizador» |
| 13_validador_evento_cancelado | Validador rechaza con «Evento cancelado» |
| 14–16_admin_* | Cancelación administrativa con motivo obligatorio |

Requieren backend y NO se dan por aprobados contra el mock: reembolso real con la pasarela
(estado REEMBOLSADO se marca en memoria), envío real de avisos, concurrencia entre cancelación y
compra simultánea, comisión cobrada al organizador (solo queda registrada como cargo pendiente),
paridad web/móvil del cálculo de la política (RNF-10), y CP-19/CP-20.

## 8. Texto propuesto para los documentos (conservando identificadores)

**RF-08 · Creación y publicación de evento (v3).** El organizador crea un evento con nombre,
descripción, categoría, lugar, dirección, fecha y hora de inicio (calendario y selector de hora en
24 h; no se admite texto libre), fecha y hora de fin opcionales (no anteriores al inicio) y
ubicación exacta fijada en un mapa (latitud y longitud). Publicar exige ubicación; un borrador
puede guardarse sin ella.

**RF-13 · Modificación de evento (ampliación).** Cambiar la fecha de un evento con boletos
emitidos requiere confirmación explícita y aviso a los asistentes.

**RF-14 · Cancelación de evento (v3).** Un evento puede cancelarse solo antes de su inicio. Si es
gratuito, se anulan las confirmaciones y se avisa a los asistentes. Si es de pago con ventas, el
organizador debe aceptar una comisión de cancelación del 10 % sobre lo cobrado; los asistentes
reciben el 100 % de reembolso y la comisión queda como cargo al organizador. Sin ventas, la
comisión es 0. Un evento iniciado o realizado solo puede marcarse como finalizado. El
administrador puede cancelar por moderación con motivo obligatorio, reembolso del 100 % y sin
comisión; se registra quién canceló y por qué.

**RF-04 · Gestión de perfil (ampliación de roles).** Adquirir el rol de organizador requiere
confirmación explícita. El usuario puede renunciar al rol si no es administrador, si no es su
único rol y si no tiene eventos publicados con boletos vigentes; conserva el rol de asistente.

**CU-05 · Crear y publicar evento (flujos alternativos).** A1: fecha pasada o fin anterior al
inicio → el sistema lo impide en el selector. A2: sin ubicación → «Publicar» bloqueado; se
permite «Guardar como borrador». A3: permiso de ubicación denegado → selección manual en el mapa.

**Caso de cancelación (nuevo, junto a CU-05).** Flujo básico: el organizador abre el panel del
evento, pulsa «Cancelar evento», revisa la política (confirmaciones a anular, reembolso y comisión)
y confirma. A1: evento de pago con ventas → el botón dice «Cancelar y aceptar comisión»; si no
acepta, nada cambia. A2: evento iniciado → botón deshabilitado con motivo. A3: administrador →
motivo obligatorio y sin comisión.

**Casos de prueba nuevos:** CP-CANCEL-01..07, CP-ROL-01..04, CP-UBIC-01..04, CP-FECHA-01..03,
CP-HORA-01..02 (tabla del brief v3). Ninguno renumera CP-01..CP-20 ni CP-MULTI-*.

## 9. Pendientes concretos

- Backend: endpoints de cancelación y de rol, reembolso con la pasarela contra `token_pasarela`,
  cobro efectivo de la comisión al organizador, envío de avisos, `PENDIENTE` en `estado_pago`.
- Migraciones v2 y v3 sin ejecutar (no hay base en el repositorio).
- RF-19 completo: orden por cercanía en `GET /api/eventos` con lat/lng; en la app solo hay ciudad
  manual y coordenadas por evento.
- Discrepancias documentales: la v2.1 aún describe RF-14 como «cancelar y notificar»; Visión v2 y
  Anexo E no mencionan la comisión; el SQL sigue declarando PostgreSQL 11.
- El mapa necesita red para las teselas; sin conexión se ve el marcador sobre fondo vacío.
- Pantallas de etapas 3 y 4 siguen con colores por alias (no roles del tema).
