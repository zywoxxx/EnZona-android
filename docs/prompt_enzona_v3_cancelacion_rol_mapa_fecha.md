# EnZona v3 · Cancelación de eventos, confirmación de rol, ubicación en mapa y fecha/hora con selectores

Trabaja sobre el repositorio actual de EnZona (Android: Kotlin + Jetpack Compose + Material 3), el que ya incluye la compra múltiple, la paleta clara v2 y las 24 pruebas de `CompraMultipleTest.kt`. No partas de ningún ZIP anterior. Lee primero `docs/notas_v2_compra_multiple.md`, el README y el historial de Git para conservar lo que ya funciona.

Son cinco cambios que pidió la profesora en la revisión. Impleméntalos de extremo a extremo (modelo, repositorio, pantallas, navegación, pruebas y documentación); no termines con un diagnóstico. Compila con el Gradle Wrapper y el JDK del proyecto y ejecuta las pruebas.

## Fuentes y restricciones vigentes

Lee en `docs/` (o donde estén en el repositorio): `vision_y_alcance_v2.docx`, `requisitos_y_pruebas_v2.1.docx`, `Anexo_E_Arquitectura_EnZona.docx`, `esquema_enzona_v2.sql`, `migracion_v2_incremental.sql` y `notas_v2_compra_multiple.md`.

- No cambies versiones de Gradle, AGP, Kotlin, Compose, JDK ni `minSdk` (26, Android 8.0). Cualquier dependencia nueva debe ser compatible con API 26 y con el catálogo actual.
- Conserva la paleta v2 y sus alias de nombres antiguos; usa los tokens del tema, no colores escritos en las pantallas. Verifica 4.5:1 en cualquier texto nuevo (diálogos, mapa, selectores).
- No ejecutes `esquema_enzona_v2.sql` contra datos existentes: es un script con `DROP ... CASCADE`. Los cambios de esquema van como sentencias incrementales añadidas a `docs/migracion_v2_incremental.sql` (o un archivo nuevo `migracion_v3_incremental.sql`), sin `\echo`.
- La plataforma nunca almacena datos de tarjeta. Los reembolsos se registran contra el `pago` y su `token_pasarela`; no añadas campos de tarjeta.
- Si el backend Spring Boot no está en el repositorio accesible, implementa la lógica en el `MockRepository`/estado de la app, deja el contrato REST documentado en `ApiService.kt` y márcalo como pendiente de backend. No declares integración real.
- Mantén la navegación por rol, el acceso separado del personal y la compra múltiple sin regresiones.

Entrega al inicio una tabla de hallazgos (cambio, archivo/función donde vive hoy, estado, acción), igual que en la tarea anterior.

## Cambio 1 · Regla de negocio para cancelar eventos (RF-14)

Hoy `MockRepository.cancelarEvento()` cancela cualquier evento sin distinguir si es gratuito o de pago, anula boletos, libera asientos y marca órdenes como REEMBOLSADA y pagos como REEMBOLSADO. La profesora pide una regla explícita:

**Regla:**

1. **Evento gratuito** (todos sus `tipo_boleto` con precio 0, o sin tipos de pago): el organizador puede cancelarlo mientras no haya comenzado. Las confirmaciones de asistencia se anulan y los asistentes reciben aviso.
2. **Evento de pago** (al menos un tipo de boleto con precio > 0): **solo puede cancelarse aceptando una comisión de cancelación del 10 %**, calculada sobre el importe total cobrado en órdenes aprobadas de ese evento. Los asistentes reciben el **100 %** de reembolso; la comisión la asume el organizador y queda registrada como cargo pendiente a su cuenta. Si el evento de pago no tiene ninguna orden aprobada, la comisión es 0 y la cancelación procede como si fuera gratuito, pero indícalo en el diálogo.
3. Un evento que ya comenzó o terminó no puede cancelarse por ningún rol; solo puede marcarse como finalizado. Un evento en borrador se cancela sin comisión.
4. La cancelación por parte del **administrador** (moderación, RF-18) reembolsa igual al 100 % a los asistentes y exige un motivo, pero **no** cobra comisión al organizador; registra quién la ejecutó y por qué.

**Implementación:**

- Añade en el modelo una función pura tipo `politicaCancelacion(evento, ordenesAprobadas): PoliticaCancelacion` que devuelva si se puede cancelar, el importe cobrado, la comisión (10 % con decimales exactos, `BigDecimal`, MXN, redondeo a 2 decimales `HALF_UP`) y el motivo si está bloqueada. Es la única fuente de verdad para UI y repositorio; no dupliques la regla en la pantalla.
- En `EventStatsScreen` (organizador) y `AdminScreen`, el botón «Cancelar evento» abre un diálogo que muestra la política aplicable: para gratuito, «Se cancelará el evento y se anularán N confirmaciones»; para pago, «Se reembolsará $X a N asistentes. Se aplicará una comisión de cancelación del 10 % ($Y) a tu cuenta». El botón de confirmar debe decir explícitamente «Cancelar y aceptar comisión» en el caso de pago; para el administrador, exige capturar el motivo. Cuando la política bloquea (evento iniciado), el botón se deshabilita con el motivo visible.
- La operación debe ser completa o no hacer nada: boletos → CANCELADO, asientos liberados, órdenes → REEMBOLSADA, pagos → REEMBOLSADO, más el registro de la cancelación. No dejes eventos cancelados con boletos VIGENTES.
- Persistencia (migración incremental): en `evento` añade `fecha_cancelacion TIMESTAMPTZ`, `cancelado_por BIGINT REFERENCES usuario(id)`, `motivo_cancelacion VARCHAR(255)` y `comision_cancelacion NUMERIC(12,2) NOT NULL DEFAULT 0` con `CHECK (comision_cancelacion >= 0)`. Si prefieres una tabla `cancelacion_evento` con esos campos, justifícalo; no crees ambas.
- Contrato REST pendiente: `POST /api/eventos/{id}/cancelacion` con `{ motivo, aceptaComision }`, respuesta con importe reembolsado, comisión y número de boletos anulados; `GET /api/eventos/{id}/politica-cancelacion` para que web y Android muestren el mismo cálculo (RNF-10). Documenta errores: 409 evento iniciado, 422 comisión no aceptada, 403 rol sin permiso.
- El asistente debe ver en *Mis boletos* el boleto como «Evento cancelado · reembolso en proceso» y no como válido; el validador debe rechazar el QR de un evento cancelado con ese mensaje.
- Añade la regla en `README.md` y prepara la redacción para RF-14 en la nota técnica (ver «Documentación»).

## Cambio 2 · Confirmación antes de convertirse en organizador

Hoy `ProfileScreen` llama a `MockRepository.activarRolOrganizador(usuario.id)` y `SessionManager.refrescar()` en cuanto se pulsa «Convertirme en organizador». Un toque accidental convierte al usuario sin posibilidad de volver atrás.

- Muestra un `AlertDialog` de Material 3 antes de activar el rol: título «¿Convertirte en organizador?», texto que explique qué cambia (aparece la pestaña *Mis eventos*, podrás crear y publicar eventos y designar personal de acceso, conservas tu cuenta de asistente) y botones «Cancelar» y «Sí, convertirme en organizador». El botón de confirmar no debe ser el que está enfocado o resaltado en coral por defecto para evitar el doble toque; usa el coral para la acción y el azul marino/texto para cancelar, respetando el contraste.
- Solo al confirmar se llama a `activarRolOrganizador` y se refresca la sesión. Muestra un `Snackbar` «Ahora eres organizador» al terminar.
- Añade la operación inversa: en *Perfil*, la sección de organizador debe incluir «Dejar de ser organizador», también con diálogo de confirmación. Permitido solo si el usuario no tiene eventos publicados con boletos vigentes; si los tiene, deshabilita la opción y muestra por qué. Al quitar el rol se elimina la fila de `usuario_rol` correspondiente, se conserva el rol de asistente y, si el rol activo era organizador, la sesión vuelve a asistente y a *Inicio*.
- No se puede dejar de ser organizador si es el único rol de la cuenta ni si la cuenta es administrador (ese caso lo gestiona el administrador en RF-18).
- Contrato REST pendiente: `POST /api/usuarios/me/roles/organizador` y `DELETE /api/usuarios/me/roles/organizador` (409 si tiene eventos vigentes).

## Cambio 3 · Ubicación exacta en mapa al crear el evento (RF-08 + RF-19)

En `EventFormScreen` la ubicación son dos campos de texto (`lugar`, `direccion`). El esquema v2 ya tiene `evento.ciudad`, `evento.latitud` y `evento.longitud` con rangos válidos; comprueba si el modelo `Evento` y el `MockRepository` ya los usan y complétalo.

- Añade un selector de ubicación en mapa. **Elige la biblioteca según lo que ya haya en el repositorio:** si existe una clave de Google Maps configurada, usa `maps-compose`; si no, usa **osmdroid** (OpenStreetMap, sin clave, compatible con API 26) y declara la atribución exigida por OSM. No dejes una clave de API vacía ni la incrustes en el código fuente; si hace falta, va en `local.properties`/`secrets` documentado en el README.
- Comportamiento: el mapa abre centrado en **Orizaba, Veracruz** (aprox. 18.851, −97.100, zoom de ciudad) o en la ubicación del usuario si ya dio consentimiento (RNF-11). El organizador toca o arrastra un marcador para fijar la ubicación exacta; se muestran las coordenadas con 5 decimales y, si `Geocoder` está disponible, la dirección aproximada como sugerencia editable. Añade un botón «Usar mi ubicación actual» que pida el permiso solo al pulsarlo y que funcione sin él (permiso denegado = solo selección manual, CP-18).
- Los campos `lugar` (nombre del recinto) y `direccion` se conservan como texto; `latitud`/`longitud` se guardan juntas o ninguna, nunca 0/0 por defecto. Publicar un evento exige coordenadas; guardar un borrador no.
- Valida que la coordenada esté dentro de los rangos del esquema y, si queda fuera del área de estudio, muestra un aviso sin bloquear (el alcance es Orizaba, pero el esquema no lo restringe).
- En `EventDetailScreen` muestra un mapa estático o miniatura con el marcador y un botón «Cómo llegar» que abra un intent `geo:` genérico; no dependas de una app concreta. Los eventos sin coordenadas muestran solo la dirección, sin distancias inventadas (mantén lo ya definido para CP-17).
- El `Evento` de demostración debe traer coordenadas reales de Orizaba para que el mapa se vea poblado.

## Cambio 4 · Fecha con calendario (no texto libre)

Hoy la fecha del evento es una cadena libre con ayuda «Ejemplo: Vie 25 Sep · 19:00» (`CampoEnZona(fecha, ...)`) y `Evento.fecha` es `String`. El SQL v2 usa `TIMESTAMPTZ`.

- Sustituye el campo de texto por un campo de solo lectura que abre `DatePickerDialog` de Material 3 (`rememberDatePickerState`). No se puede escribir la fecha a mano.
- Restricciones: fecha mínima hoy (usa `selectableDates` para deshabilitar el pasado); fecha de fin opcional que no puede ser anterior al inicio. Si el evento ya tiene boletos emitidos, cambiar la fecha debe pedir confirmación (impacta a los asistentes, RF-13).
- Cambia el modelo: `Evento.fecha` pasa a un tipo temporal real (`java.time.LocalDateTime` o `Instant` con zona `America/Mexico_City`; `java.time` está disponible desde API 26 sin desugaring). Ajusta `MockRepository`, los datos de prueba, `ApiService`/DTO (ISO‑8601 `2026-09-25T19:00:00-06:00`, alineado con `TIMESTAMPTZ`) y todos los usos. Mantén una función de formato única para mostrar «Vie 25 Sep · 19:00» con `Locale("es","MX")`; las pantallas no deben formatear por su cuenta.
- Revisa el orden por fecha y los filtros de *Inicio* (hoy, esta semana, etc.): con el tipo real deben funcionar por comparación temporal, no por texto.

## Cambio 5 · Hora solo numérica

La hora se captura hoy dentro del mismo texto libre, así que acepta letras.

- Captura la hora con `TimePickerDialog`/`TimeInput` de Material 3 en formato 24 h; si se ofrece entrada manual, usa `KeyboardType.Number` con máscara `HH:mm`, límite de 4 dígitos y validación de rango (00–23, 00–59). No debe ser posible introducir texto.
- Combina fecha y hora en un único valor temporal del modelo (cambio 4). Muestra un resumen «Vie 25 Sep 2026 · 19:00» bajo los dos selectores.
- Hora de fin opcional con la misma validación; si el fin es el mismo día debe ser posterior al inicio.

## Documentación que debes actualizar

Prepara el texto (en la nota técnica y en `README.md`) para que yo lo pase a los documentos Word; conserva los identificadores existentes:

- **RF-08** (crear evento): fecha y hora con selectores, ubicación exacta en mapa con coordenadas obligatorias para publicar.
- **RF-14** (cancelar evento): la regla gratuito / pago con comisión del 10 % / iniciado no cancelable / cancelación administrativa con motivo.
- **RF-04** o el requisito de roles: confirmación explícita para adquirir el rol de organizador y posibilidad de renunciar a él.
- **CU-05** (Crear y publicar evento) y el caso de cancelación: flujos alternativos con la comisión y con el rechazo por evento iniciado.
- Casos de prueba nuevos con prefijos propios, sin renumerar CP-01..CP-20 ni CP-MULTI-*:

| Caso | Resultado esperado |
| --- | --- |
| CP-CANCEL-01 · Cancelar evento gratuito con 3 confirmaciones | Evento CANCELADO, 3 boletos CANCELADO, comisión 0, asistentes notificados. |
| CP-CANCEL-02 · Cancelar evento de pago con $1,500 cobrados | Diálogo muestra reembolso $1,500 y comisión $150; al aceptar, órdenes REEMBOLSADA, pagos REEMBOLSADO, `comision_cancelacion` = 150.00. |
| CP-CANCEL-03 · Rechazar la comisión | No cambia nada; el evento sigue PUBLICADO con sus boletos VIGENTES. |
| CP-CANCEL-04 · Evento de pago sin ventas | Cancelación procede con comisión 0 y el diálogo lo indica. |
| CP-CANCEL-05 · Evento ya iniciado | Botón deshabilitado con motivo; el repositorio también rechaza la llamada. |
| CP-CANCEL-06 · Validar QR de evento cancelado | Acceso rechazado con «Evento cancelado». |
| CP-CANCEL-07 · Cancelación por administrador | Reembolso 100 %, motivo obligatorio, sin comisión al organizador, registro de quién canceló. |
| CP-ROL-01 · Pulsar «Convertirme en organizador» y cancelar el diálogo | El usuario sigue siendo solo asistente; no aparece *Mis eventos*. |
| CP-ROL-02 · Confirmar | Rol añadido, pestaña *Mis eventos* visible, Snackbar mostrado. |
| CP-ROL-03 · Dejar de ser organizador sin eventos vigentes | Rol eliminado, sesión vuelve a asistente. |
| CP-ROL-04 · Dejar de ser organizador con un evento publicado con boletos | Opción deshabilitada con motivo. |
| CP-UBIC-01 · Fijar marcador en el mapa | `latitud`/`longitud` guardadas con 5 decimales, dentro de rango. |
| CP-UBIC-02 · Publicar sin ubicación | Bloqueado con mensaje; guardar borrador sí permitido. |
| CP-UBIC-03 · Permiso de ubicación denegado | El mapa sigue usable de forma manual centrado en Orizaba. |
| CP-UBIC-04 · Detalle del evento | Miniatura con marcador y «Cómo llegar»; sin coordenadas, solo dirección. |
| CP-FECHA-01 · Intentar escribir la fecha | El campo no acepta texto; solo abre el calendario. |
| CP-FECHA-02 · Elegir una fecha pasada | Deshabilitada en el calendario. |
| CP-FECHA-03 · Fin anterior al inicio | Error de validación, no se guarda. |
| CP-HORA-01 · Escribir «siete pm» | Imposible: teclado numérico/selector; solo 00–23 y 00–59. |
| CP-HORA-02 · Guardar evento | `fecha` se serializa ISO‑8601 con zona y se muestra «Vie 25 Sep · 19:00». |

Escribe pruebas unitarias para la política de cancelación (gratuito, pago, sin ventas, iniciado, redondeo de comisión), para la reversión del rol y para la validación de fecha/hora; y pruebas de Compose para los tres diálogos (cancelar evento, convertirse en organizador, dejar de serlo). Indica cuáles casos requieren backend (reembolso real con pasarela, concurrencia) y no los marques como aprobados contra el mock.

## Resultado final solicitado

Tabla de hallazgos inicial; archivos y contratos modificados; migración incremental; pruebas ejecutadas con su resultado; capturas de los diálogos, el mapa y los selectores en `docs/capturas_v3/`; y una nota técnica `docs/notas_v3_cancelacion_rol_mapa_fecha.md` con la regla de negocio final, los cambios de RF/CU/CP redactados y los pendientes concretos (backend, pasarela, clave de mapas si aplica). Conserva todo lo que ya funcionaba.
