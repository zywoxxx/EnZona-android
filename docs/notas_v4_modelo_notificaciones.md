# EnZona v4 · Modelo relacional, notificaciones y ajustes de interfaz

Fecha: 22 de septiembre de 2026. Alcance: cinco peticiones sobre la app Android
(sin backend en el repositorio; todo se implementa en `MockRepository` y se
documentan los contratos REST pendientes en `data/remote/ApiService.kt`).

## 1. Hallazgos iniciales

| Petición | Dónde estaba | Estado encontrado | Acción |
|---|---|---|---|
| Diagrama ER implementado en la app | `data/model/Models.kt` | Faltaban `rol.id`/`usuario_rol`, `categoria` como tabla y `evento.categoria_id`, `usuario.contrasena_hash`, `telefono_verificado`, `fecha_registro`, `evento.fecha_creacion`, `validacion_acceso.boleto_id` | Modelo alineado tabla por tabla (sección 2) |
| Filtros «Gratis» y «De pago» juntos | `HomeScreen.FilaFiltros` | «Gratis» era el primer chip y «De pago» el último, con Fecha/Lugar/Tipo en medio | Van adyacentes al inicio de la fila |
| Varios boletos en horizontal | `EventDetailScreen` (tipos de boleto) y `OrderSummaryScreen` (boletos de la orden) | Listas verticales que alargaban la pantalla | Carrusel `LazyRow` con tarjetas de ancho fijo y texto «desliza para verlos» |
| El calendario no se abría | `ui/components/FechaHoraSelector.kt · CampoSelector` | La capa que capturaba el toque usaba `fillMaxSize` dentro de un `Box` sin alto: medía 0 dp y el toque llegaba al campo de solo lectura, que no hace nada | El toque se detecta con el `interactionSource` del propio campo (`PressInteraction.Release`) |
| Botón de notificaciones | No existía; `NotificacionAsistente` solo se creaba al cancelar | Sin pantalla, sin contador, sin recordatorios ni avisos de compra | Campana en Inicio con contador, pantalla con pestañas, cuatro tipos de aviso |

## 2. Diagrama ER → clases de la app

| Tabla (diagrama) | Clase / campo en la app | Notas |
|---|---|---|
| `usuario` | `Usuario(id, nombre, correo, telefono, curp, contrasenaHash, estado, correoVerificado, telefonoVerificado, fechaRegistro)` | `contrasena_hash`: la demo guarda SHA-256; en producción BCrypt en el servidor (RNF-01). La app nunca guarda la contraseña en claro. |
| `rol` | `enum Rol(id)`: ASISTENTE 1, ORGANIZADOR 2, VALIDADOR 3, ADMIN 4; `nombreBd` | Catálogo fijo. |
| `usuario_rol` | `Usuario.roles: Set<Rol>` → `Usuario.usuarioRoles` / `MockRepository.usuarioRoles()` devuelve `UsuarioRol(usuarioId, rolId)` | PK compuesta; `asignarRol` inserta/borra la fila. |
| `categoria` | `Categoria(id, nombre)`; `MockRepository.categorias`, `categoriaId(nombre)` | `nombre` único. El formulario usa el catálogo. |
| `evento` | `Evento` + `categoriaId`, `fechaCreacion`; `fecha` = `fecha_hora_inicio`, `fechaFin` = `fecha_hora_fin` | Conserva las columnas v3 de cancelación y las v2 de ciudad/coordenadas. `precioDesde`, `disponibles`, `colorSemilla` son derivados de presentación, no columnas. |
| `tipo_boleto` | `TipoBoleto` | Sin cambios. |
| `asiento` | `Asiento(id, eventoId, seccion, fila, numero, estado)` | `numero` es `Int` en la app y `VARCHAR(10)` en SQL; se convierte al serializar. |
| `orden` | `Orden(id, asistenteId, eventoId, total, estado, fechaCreacion)` | Sin cambios. |
| `pago` | `Pago(id, ordenId, monto, moneda, estado, referenciaPasarela, fecha)` | Sin campos de tarjeta; el reembolso se registra contra `referencia_pasarela`. |
| `boleto` | `Boleto(id, ordenId, tipoBoletoId, asientoId, codigo, qrFirma, estado, fechaEmision, fechaUso)` | Sin cambios. |
| `validacion_acceso` | `ValidacionAcceso` + `boletoId` | `boletoId` es null solo cuando el código escaneado no existe (bitácora local); en SQL la FK es NOT NULL, así que esos rechazos no se sincronizan como fila de esa tabla. |

No hace falta migración para el diagrama: las columnas ya existen en
`esquema_enzona_v2.sql`. La migración v4 solo amplía `notificacion_asistente`.

## 3. Notificaciones

Modelo `data/model/Notificacion.kt`: `TipoNotificacion` (COMPRA_CONFIRMADA,
RECORDATORIO, EVENTO_CANCELADO, FECHA_CAMBIADA) y `NotificacionAsistente(id,
usuarioId, eventoId, tipo, texto, fecha, leida, boletoId)`.

Origen de cada aviso en `MockRepository`:

| Tipo | Cuándo se crea | Al tocarlo abre |
|---|---|---|
| COMPRA_CONFIRMADA | Al final de `comprar` (también en confirmaciones gratuitas) | El primer boleto de la orden |
| RECORDATORIO | `generarRecordatorios(usuarioId, ahora)`: boletos VALIDO de eventos PUBLICADO cuyo inicio cae en (ahora, ahora + 7 días]; una sola vez por usuario y evento | El boleto |
| EVENTO_CANCELADO | `cancelarEvento`, un aviso por asistente con orden pagada | El evento (muestra «Evento cancelado») |
| FECHA_CAMBIADA | `modificarEvento` cuando cambia `fecha`, un aviso por asistente con boleto vigente (RF-13) | El boleto |

Consulta y lectura: `notificacionesDe(usuarioId)` (genera recordatorios y ordena de
más reciente a más antigua), `notificacionesNoLeidas`, `marcarNotificacionLeida(id)`,
`marcarNotificacionesLeidas(usuarioId)`.

Interfaz: `BotonNotificaciones` (campana con `Badge` del contador; icono activo
cuando hay pendientes) en el encabezado de Inicio y `NotificacionesScreen` con
pestañas Todas / Próximos / Cancelados / Compras, punto coral en las no leídas,
«Marcar todas como leídas» y estados vacíos por pestaña. Ruta `Rutas.NOTIFICACIONES`.

Contratos REST pendientes: `GET api/usuarios/me/notificaciones`,
`POST api/usuarios/me/notificaciones/{id}/leida`, `POST api/usuarios/me/notificaciones/leidas`
(`NotificacionDto`). En producción el RECORDATORIO lo genera un job diario del
backend (consulta de referencia en la migración) y los avisos se envían además
por push/correo; la app solo los lista.

## 4. Corrección del calendario

`CampoSelector` es un `OutlinedTextField` de solo lectura. Un campo así no
recibe `onClick`, y la capa transparente que se le superponía medía 0 dp (un
`fillMaxSize` dentro de un `Box` sin alto fijo no ocupa nada). Ahora el campo
recibe un `MutableInteractionSource` y un `LaunchedEffect` abre el selector al
soltar el toque (`PressInteraction.Release`). Aplica a los cuatro campos (fecha y
hora de inicio y de fin). Verificado en el emulador: el calendario se abre y no
permite fechas pasadas.

## 5. Ajustes de interfaz

- Inicio: «Gratis» y «De pago» son las dos opciones del mismo filtro y van
  juntas al inicio de la fila de chips; siguen Fecha, Lugar y Tipo.
- Detalle del evento: `TarjetaTipoBoleto` (168 dp) en un `LazyRow`; cuando hay
  más de un tipo aparece «Desliza para ver los N tipos de boleto».
- Resumen de compra: `TarjetaBoletoDeOrden` (220 dp) en un `LazyRow`
  con el título «Tus boletos · desliza para verlos».

## 6. Verificación

Comandos (JDK 17): `gradlew.bat assembleDebug testDebugUnitTest`.

| Caso | Prueba | Resultado |
|---|---|---|
| CP-MODELO-01 catálogos rol y categoria | `ModeloRelacionalTest` | verde |
| CP-MODELO-02 filas de usuario_rol | ídem | verde |
| CP-MODELO-03 evento.categoria_id y fecha_creacion | ídem | verde |
| CP-MODELO-04 FKs orden/pago/boleto/validacion_acceso | ídem | verde |
| CP-MODELO-05 contrasena_hash y autenticación | ídem | verde |
| CP-NOTIF-01 compra → aviso con boleto | `NotificacionesTest` | verde |
| CP-NOTIF-02 recordatorio a 7 días, sin duplicar | ídem | verde |
| CP-NOTIF-03 cancelación → aviso con reembolso | ídem | verde |
| CP-NOTIF-04 cambio de fecha → aviso solo a quien tiene boleto | ídem | verde |
| CP-NOTIF-05 marcar leídas y orden cronológico | ídem | verde |
| Regresión v1–v3 (48 casos) | resto de suites | verde |

Total unitarias: 58/58. Compose (`DialogosTest`, emulador API 37): 7/7.

Recorrido manual en el emulador (Pixel 10 Pro XL), capturas en `docs/capturas_v4/`:

| Archivo | Qué muestra |
|---|---|
| 01_inicio_campana_y_filtros_juntos | Inicio con la campana y los chips «Gratis» y «De pago» adyacentes |
| 02_inicio_campana_con_2_sin_leer | Contador de la campana tras confirmar asistencia (compra + recordatorio) |
| 03_detalle_tipos_boleto_horizontal | Tipos General / Palco en carrusel con «Desliza para ver los 2 tipos» |
| 04 / 05_orden_boletos_carrusel | Compra de 3 boletos: tarjetas «Boleto k de 3» deslizables |
| 06_notificaciones_todas | Pantalla con pestañas y tres avisos sin leer |
| 07_calendario_se_abre_al_tocar_fecha | Formulario: el calendario se abre al tocar «Fecha de inicio» |
| 08_notificaciones_evento_cancelado | Pestaña Cancelados tras la cancelación del Torneo por el organizador |

## 7. Pendiente

- Backend: endpoints de notificaciones, job diario de recordatorios, envío push
  o correo. La app hoy genera los recordatorios en el teléfono al abrir Inicio.
- Hash de contraseña real (BCrypt con sal) en el servidor; la app deberá enviar
  la contraseña por HTTPS y dejar de calcular hashes.
- Sincronización de `validacion_acceso` sin `boleto_id` (códigos inexistentes):
  decidir si se guardan en otra bitácora o se descartan.
- Aplicar `docs/migracion_v4_incremental.sql` en la base real (no ejecutada).
