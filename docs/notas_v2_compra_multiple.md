# Notas técnicas v2 · Compra múltiple y paleta de la plataforma

Fecha: 13 de septiembre de 2026. Complementa `notas_rediseno_etapas_1_2.md`.

## 1. Fuentes revisadas

| Fuente | Estado | Qué aporta |
| --- | --- | --- |
| `docs/prompt_enzona_v2_compra_multiple_paleta.md` | Leído | Brief de esta tarea. |
| `docs/DOC_MAESTRO_UV v2.docx` | Leído y comparado con v1 | Alcance multiplataforma (web + Android), Orizaba como área de estudio. |
| `docs/requisitos_y_pruebas_v2.docx` + 4 diagramas | Leído y comparado con v1 | RF-19, RNF-06/07/10/11, CU-01 a CU-07, CP-17 a CP-20. Los diagramas de casos de uso y de actividad de descubrimiento son nuevos; el de compra y el de estados del boleto no cambian. |
| `docs/esquema_enzona_v2.sql` | Leído, **no ejecutado** | Script de reconstrucción (`DROP TABLE … CASCADE`). `boleto.orden_id` no es único; `evento` gana `ciudad`, `latitud`, `longitud`. |
| `docs/modelo_relacional_v2.png` | Leído | Coincide con el SQL v2. |
| `docs/WhatsApp Image 2026-09-11 at 11.25.23.jpeg` | Leído | Paleta de la plataforma con hexadecimales impresos. |
| `docs/enzona_logo.png`, `docs/enzona_icono_512.png` | Leídos | Logo original; el horizontal se incorporó a la app. |
| `README.md`, `esquema_metodo_pago.sql`, `data/remote/ApiService.kt` | Leídos | Extensión `metodo_pago` (no está en el diagrama v2) y contrato Retrofit sin conectar. |

No hay `DOC_MAESTRO_UV (1).docx` con ese nombre; el archivo presente es `DOC_MAESTRO_UV v2.docx`.
Backend y web no forman parte del repositorio: los cambios de API se documentan, no se integran.
No hay repositorio Git; la comparación se hizo contra las notas de la etapa anterior y una copia
del código previo guardada antes de editar. Ningún archivo Kotlin había cambiado desde esa etapa.

## 2. Tabla de hallazgos

| Requisito | Evidencia (archivo/función) | Estado antes | Acción |
| --- | --- | --- | --- |
| RF-11/RF-16 compra de N boletos | `MockRepository.comprarOConfirmar` emitía 1 boleto y descontaba 1; `yaTieneBoleto` bloqueaba toda segunda compra | **Incompatible** (comprobado con prueba de reproducción) | Nueva `comprar(SolicitudCompra)` por lote, todo o nada. |
| RF-09 varios asientos | `SeatMapScreen`: `seleccionadoId: Long?` escalar; `seleccionar()` reemplazaba | **Incompatible** (comprobado en emulador: A5 → A6 dejaba solo A6) | Selección como lista sin duplicados con "Seleccionados X de N". |
| Rutas y callbacks | `Rutas.PAGO = pago/{eventoId}/{tipoId}/{asientoId}`; `onIrAPago(evId, tpId, asId)` | **Incompatible** (un solo asiento, sin cantidad) | Rutas con cantidad y lista de asientos. |
| API | `CrearOrdenDto(eventoId, tipoBoletoId, asientoId)` → `BoletoDto` | **Incompatible** (un boleto) | `CrearOrdenDto(cantidad, asientosIds, …)` → `OrdenCreadaDto(boletos)` + `Idempotency-Key`. Pendiente en backend. |
| Total e inventario | `total = tipo.precio`; `disponibles - 1`; `cantidadDisponible - 1` | **Incompatible** | Total = precio × N con 2 decimales; descuentos por N; `aforo` intacto. |
| Idempotencia | Solo `enabled = false` durante el proceso | **Incompleto** | Clave de operación por pantalla; el repositorio devuelve el mismo resultado si se repite. En servidor sigue pendiente. |
| Validación por boleto (CP-12/13) | `validarQr` marca solo ese `boleto` | Correcto | Sin cambios; cubierto por CP-MULTI-10 y emulador. |
| Política gratuita | `yaTieneBoleto` | Correcto (una confirmación por persona) | Conservada solo para eventos gratuitos. |
| `aforo` como capacidad | `Evento.aforo` no se modifica al vender | Correcto | Sin cambios; `disponibles` es el saldo. |
| RF-19 ubicación | No existe permiso, GPS ni distancia | **Incompleto** | Se añadió `ciudad/latitud/longitud` al modelo y filtro por ciudad elegida a mano (RNF-11 parcial). GPS y orden por cercanía pendientes. |
| RNF-07 Android 8.0 | `minSdk = 26` en `app/build.gradle.kts` | Correcto por configuración | No verificado en un dispositivo API 26. |
| RNF-10 / CP-20 paridad | Sin backend ni web en el repo | **No verificado** | Contrato documentado. |
| Paleta | Tema oscuro morado de la etapa anterior | Sustituido | Tema claro con los hexadecimales de la imagen. |

Stack comprobado y sin cambios: Gradle 8.14.3, AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01,
Navigation 2.8.5, JDK 17 para compilar, `minSdk 26`, `targetSdk 35`.

## 3. Causa comprobada del fallo

La restricción a un boleto estaba en cuatro capas, no solo en la selección visual:

1. **Repositorio.** `comprarOConfirmar` aceptaba un único `asientoId`, creaba una orden con
   un boleto y descontaba uno del cupo y del stock. Además `yaTieneBoleto` rechazaba cualquier
   segunda compra del mismo evento con "Ya tienes un boleto".
2. **Estado de pantalla.** `SeatMapScreen` guardaba `seleccionadoId: Long?`; pulsar otra butaca
   sustituía la anterior (captura `capturas_v2/00_antes_solo_un_asiento.png`).
3. **Navegación.** Las rutas `asientos/{eventoId}/{tipoId}` y `pago/{eventoId}/{tipoId}/{asientoId}`
   no transportaban cantidad ni lista de asientos, y el detalle no tenía selector de cantidad.
4. **Contrato.** `CrearOrdenDto` llevaba un `asientoId` y la respuesta era un solo `BoletoDto`.

El esquema no imponía el límite: `boleto.orden_id` no es único y no hay unicidad por asistente y evento.

## 4. Qué se implementó

**Modelo y repositorio** (`data/model/Compra.kt`, `data/MockRepository.kt`)
- `SolicitudCompra(eventoId, tipoBoletoId, cantidad, asientosIds, claveOperacion)` y
  `CompraRealizada(orden, pago, boletos)`; errores tipados en `ErrorCompra`.
- `comprar()` valida todo antes de tocar el inventario (evento publicado, cantidad > 0, tipo del
  evento, cupo y stock ≥ N, asientos distintos, del evento y libres) y luego crea una orden, un
  pago si hay cobro y N boletos con código y firma propios; descuenta N y ocupa N asientos. Si el
  pago se rechaza: orden CANCELADA, pago RECHAZADO, cero boletos, inventario intacto.
- `cupoDisponible()` = mínimo entre cupo del evento, stock del tipo y asientos libres.
- `boletosDeOrden()` para "Boleto k de N". `comprarOConfirmar` se conserva delegando con cantidad 1.
- Política: eventos de pago admiten compras adicionales mientras haya cupo; gratuitos, una
  confirmación por persona (sin cambios).

**Pantallas**
- Detalle: selector de cantidad (48 dp, máximo = disponibilidad real), total = precio × N, botón
  "Elegir N asientos" / "Comprar N boletos". Si el máximo baja al cambiar de tipo, la cantidad se
  revalida a la vista.
- Asientos: lista de seleccionados con chips removibles, "Seleccionados X de N", aviso al intentar
  un asiento de más, bloqueo del botón hasta completar N, revalidación si un asiento elegido se
  ocupa (se pide quitarlo, sin reemplazo silencioso). Zoom, leyenda y selección por número se
  conservan; la selección sobrevive al volver desde el pago (`rememberSaveable`).
- Pago: "Boleto × N (precio c/u)", total final, comprobación previa de cupo y asientos, una sola
  operación con clave de idempotencia, reintento tras rechazo con la misma selección.
- Nueva `OrderSummaryScreen` ("Tus N boletos están listos") con acceso a cada QR.
- Boleto: "Boleto k de N" con navegación anterior/siguiente; Mis boletos muestra "Boleto k de N".

**Navegación** (`MainActivity.kt`): `asientos/{eventoId}/{tipoId}/{cantidad}`,
`pago/{eventoId}/{tipoId}/{cantidad}/{asientos}` (ids separados por guion, `0` si no aplica),
`orden/{id}`. Cantidad 1 usa el mismo camino.

**API** (`data/remote/ApiService.kt`, sin backend disponible): `POST /api/ordenes` con
`Idempotency-Key` y cuerpo `{eventoId, tipoBoletoId, cantidad, asientosIds, metodoPagoId, metodo}`;
respuesta `{ordenId, estadoOrden, estadoPago, total, moneda, referenciaPago, boletos[]}`.
Errores esperados: 409 sin cupo / asiento ocupado, 422 selección inválida, 402 pago rechazado.
El servidor deriva precios de `tipo_boleto` y no confía en el total del cliente.

**Migración** (`docs/migracion_v2_incremental.sql`, no ejecutada): columnas v2 de `evento`,
índice único parcial `boleto(asiento_id) WHERE estado IN ('VALIDO','USADO')`, clave de operación
en `orden`, `PENDIENTE` en `estado_pago` (sentencia comentada por la restricción de PG < 12) y
tabla propuesta `orden_detalle` para persistir la solicitud antes de cobrar.

## 5. Paleta aplicada

Tema claro único con los hexadecimales impresos en la imagen (`ui/theme/Color.kt`, `Theme.kt`):
fondo `#EEF1F6`, superficie blanca, primario azul marino `#1F3A5F` (botones generales,
navegación, escenario, mitad superior del boleto), secundario azul profundo `#24708F` (enlaces,
bordes de selección, texto azul pequeño), terciario coral `#F2643B` con texto tinta `#1B2432`
(comprar, confirmar, asiento elegido), acento azul EnZona `#2E86AB` en `EnZonaTema.semanticos.acento`
solo para iconos y superficies. Éxito/advertencia/error/información con sus contenedores y parejas
de la tabla del brief. Sin color dinámico. Barras del sistema claras con iconos oscuros.

Parejas evitadas por contraste: blanco sobre coral (3.15:1), blanco sobre azul EnZona (4.1:1),
gris suave como texto (2.5:1). El QR sigue oscuro sobre blanco con margen. Los nombres de la
paleta anterior (`MoradoFondo`, `NaranjaAcento`, …) quedan como alias sobre la nueva para que
las pantallas de etapas 3 y 4 adopten el tema claro; conviene migrarlas a roles del tema. Se
corrigieron a mano: logo en login, avatar del perfil, FAB del organizador (coral + tinta),
tarjeta de resultado del validador. No se implementó modo oscuro.

## 6. Verificación

Compilación y pruebas (JDK 17): `gradlew.bat assembleDebug testDebugUnitTest` → BUILD SUCCESSFUL,
24 pruebas unitarias en verde: 12 `CP-MULTI-*` (`app/src/test/.../CompraMultipleTest.kt`),
7 de filtros y 5 de fecha/formato. La primera versión del archivo reprodujo la limitación
(una llamada = un boleto; segunda compra bloqueada) y pasó contra el código anterior antes de la
corrección.

| Caso | Prueba unitaria | Emulador (Pixel 10 Pro XL, API 35) |
| --- | --- | --- |
| 2 boletos sin asiento: una orden, 2 QR, cupo −2 | CP-MULTI-01 | `17_torneo_confirmada_2`, `21_inicio_cupos` (421 → 419) |
| 3 con asiento: 3 localidades distintas, misma orden | CP-MULTI-02 | `08_asientos_3_de_3`, `10_pago_3`, `12_compra_confirmada_3` |
| 3 boletos y solo 2 asientos: no continúa | CP-MULTI-03 | `06_asientos_2_de_3` (botón deshabilitado, "Faltan 1 de 3") |
| Quitar un asiento ya seleccionado | CP-MULTI-04 (duplicados) | contador 2 → 1 al tocar A6 |
| Pedir 3 con 2 disponibles | CP-MULTI-05 | no reproducido en UI (el selector no permite superar el cupo) |
| Rechazo de pago de N | CP-MULTI-06 | `16_torneo_rechazado` y reintento aprobado |
| Doble toque / misma clave | CP-MULTI-07 | botón bloqueado durante el proceso |
| Dos compras por el mismo asiento | CP-MULTI-08 | no reproducible con un solo emulador |
| Compra adicional de pago; gratuita única | CP-MULTI-09 | — |
| Validar 1 de 3 y reintentar | CP-MULTI-10 | `18_validador_permitido`, `19_validador_ya_usado`, `20_mis_boletos_uno_usado` (1 usado, 4 vigentes) |
| Decimales exactos | CP-MULTI-11 | — |
| Volver desde el pago conserva la selección | — | comprobado por diseño (`rememberSaveable`); no capturado |

Simulado, no real: pasarela, cobro, idempotencia y concurrencia son del repositorio en memoria.
CP-09/CP-10 reales, CP-11, CP-14, CP-17 (GPS), CP-19 en Android 8.0 físico y CP-20 (paridad
web/móvil) no se dan por aprobados. No se modificó ningún servicio inaccesible.

## 7. Discrepancias y pendientes

- **Tarifa ↔ sección.** El esquema no asocia `tipo_boleto` con secciones de `asiento`. La app
  no deduce compatibilidad por nombre; hoy cualquier asiento libre es elegible para cualquier
  tarifa. Se necesita un contrato (p. ej. `tipo_boleto.seccion` o tabla puente).
- **Pago pendiente.** OXXO/SPEI se dan por pagados al instante en la demo (rotulado). Requiere
  `PENDIENTE` en `estado_pago`, pantalla propia y emisión al confirmar la pasarela.
- **Reintentos.** Cada rechazo deja una orden CANCELADA con su pago RECHAZADO; el reintento crea
  una orden nueva (trazabilidad conservada, sin límite de una fila de pago por orden).
- **Compensación.** Si la pasarela confirma un cobro que el inventario ya no puede cumplir, el
  backend debe registrar el incidente y el reembolso; no existe en el mock.
- **Ubicación (RF-19, RNF-11).** Sin permiso de ubicación, GPS ni orden por distancia; existe la
  selección manual de ciudad (Orizaba por defecto) y el estado vacío ofrece "Explorar Orizaba".
- **Fechas.** `Evento.fecha` sigue siendo texto; el SQL v2 usa `TIMESTAMPTZ`.
- **Documentos.** El caso de compra y CP-09/CP-10 describen un boleto; la ampliación CP-MULTI-*
  es una propuesta de este trabajo, no está escrita en la v2. El maestro conserva "Integrante 1…"
  y recomendaciones de plantilla; el SQL declara PostgreSQL 11 y la bibliografía cita 15; el
  documento de visión sigue diciendo que la app nativa queda fuera. Limpieza editorial pendiente.
- **Personal por evento, HMAC en el teléfono, CURP, offline**: sin cambios respecto a las notas
  de la etapa anterior.
- **Etapas 3 y 4.** Funcionan con la paleta nueva por los alias, pero usan colores directos; su
  barra superior duplica el inset de la barra de estado como antes.
