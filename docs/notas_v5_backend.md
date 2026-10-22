# EnZona v5 · Backend REST y app Android conectada a la API

Fecha: 29 de septiembre de 2026.

Hasta v4 la app Android trabajaba con `MockRepository` (todo en memoria). En v5
existe el backend que describe el Anexo E, conectado a la base de datos real en
Aiven, y la app consume esa API. El flujo registro → verificación → sesión →
catálogo → compra de N boletos → QR → validación en puerta funciona contra la
base real. La web React no forma parte de este repositorio; el contrato queda
listo para ella en `docs/openapi_enzona_v5.json`.

## 1. Base de datos real

| Dato | Valor comprobado |
|---|---|
| Motor | **PostgreSQL 18.6** (`SELECT version()`), servicio gestionado en Aiven |
| Límite de conexiones | 20 (`max_connections`) |
| Zona del servidor | GMT; el backend trabaja en UTC |
| Latencia desde la PC de desarrollo | 80 a 400 ms por consulta; 1.2 s para abrir una conexión |

La documentación menciona PostgreSQL 11 (scripts SQL) y 15 (Anexo E y
bibliografía). La versión real es la 18.6: conviene unificar los documentos con
ese dato. Todo lo usado (enum, índices parciales, `generate_series`, `LATERAL`,
`FILTER`, `ON CONFLICT`) existe desde versiones muy anteriores, así que el
esquema no depende de la 18.

HikariCP queda con `maximum-pool-size: 5` y `minimum-idle: 1`.

### 1.1 Hallazgos: esquema real frente al documentado

La base tenía las 11 tablas del esquema v2 y ningún dato salvo los catálogos.
**Ninguna migración incremental se había aplicado.**

| Elemento | Documentado en | Estado encontrado | Acción (migración Flyway) |
|---|---|---|---|
| `orden.clave_operacion` + índice único por asistente | migracion_v2 | No existía | V2 |
| `orden_detalle` | migracion_v2 | No existía | V2 |
| `ux_boleto_asiento_vigente` (índice único parcial) | migracion_v2 | No existía | V2 |
| `ck_evento_coordenadas_pares` | migracion_v2 | No existía (los CHECK de rango sí, con nombre automático) | V2 |
| `estado_pago` con `PENDIENTE` | migracion_v2 (comentado) | Solo APROBADO, RECHAZADO, REEMBOLSADO | V3, fuera de transacción |
| Columnas de cancelación en `evento` | migracion_v3 | No existían | V4 |
| `cargo_organizador` | migracion_v3 | No existía | V4 |
| `pago.referencia_reembolso`, `fecha_reembolso` | migracion_v3 | No existían | V4 |
| `notificacion_asistente` con `tipo`, `leida`, `boleto_id` | migracion_v3 + v4 | No existía | V4 (las dos en un paso) |
| `uq_notificacion_recordatorio` | migracion_v4 | No existía | V4 |
| `metodo_pago`, `pago.metodo_pago_id` | esquema_metodo_pago.sql | No existían | V5 (además `pago.medio`) |
| Estados del asiento y del boleto | El encargo dice LIBRE y VIGENTE | El enum real usa **DISPONIBLE** y **VALIDO** | Se respetan los reales |
| Rol de administración | Requisitos: «administrador» | En `rol` se llama **ADMIN** | Se respeta; la API también acepta la autoridad `ADMINISTRADOR` |
| `resultado_acceso` | Encargo: seis resultados | El enum solo tiene PERMITIDO y RECHAZADO | V7 añade `motivo` con el detalle; el enum no se toca |
| `validacion_acceso.boleto_id` | NOT NULL | Impide registrar un QR falso o inexistente | V7 lo hace opcional y guarda `codigo_leido` |
| `boleto.tipo_boleto_id` | NOT NULL | La app emitía boletos gratuitos sin tipo | Todo evento gratuito nace con el tipo «Entrada general» de precio 0 |
| `usuario.curp` UNIQUE | Anexo E: CURP cifrada | El cifrado con vector aleatorio no permite comprobar unicidad | V6 añade `curp_hash` (índice ciego) |
| Catálogo `categoria` | App: Académico, Música, Tecnología, Teatro, Deportes, Cultural | Base: Música, Deportivo, Cultural, Taller, Gastronómico, Otro | El sembrador añade las que faltan; no borra ninguna |
| `ck_evento_cancelacion_completa` | migracion_v3 | Exigía `cancelado_por`, que es `ON DELETE SET NULL` | V4 exige solo `fecha_cancelacion` |

Tablas nuevas que ningún documento preveía: `otp_verificacion` (V6) y
`evento_personal_acceso` (V7).

### 1.2 Migraciones Flyway

En `backend/src/main/resources/db/migration/`. Todas son idempotentes y ninguna
contiene `DROP` ni `\echo`.

| Versión | Contenido |
|---|---|
| V1 | Línea base: el esquema v2 escrito sin `DROP`. En Aiven **no se ejecutó**: `baseline-on-migrate` la marcó como aplicada porque las tablas ya existían. En una base vacía sí crea el esquema |
| V2 | Compra múltiple, idempotencia y líneas de orden |
| V3 | `estado_pago` admite PENDIENTE (archivo `.conf` con `executeInTransaction=false`) |
| V4 | Cancelación, cargos del organizador y notificaciones |
| V5 | Métodos de pago guardados |
| V6 | OTP de verificación y CURP cifrada con índice ciego |
| V7 | Bitácora de validación detallada y personal de acceso por evento |

V1 no es un archivo vacío, como sugería el encargo, por una razón práctica: las
pruebas de integración parten de una base vacía y necesitan el esquema. En Aiven
el resultado es el mismo que con un archivo vacío.

`docs/esquema_enzona_v2.sql` no se ejecutó en ningún momento.

### 1.3 Datos de demostración

`SembradorDemo` es un `CommandLineRunner` del perfil `seed`, que `dev` incluye.
Se eligió en lugar de `R__seed_demo.sql` porque tres datos dependen de secretos
del servidor: el hash BCrypt de la contraseña, la CURP cifrada con AES-GCM y su
índice HMAC. Es re-ejecutable: busca cada fila por su clave natural y solo crea
lo que falta. La segunda ejecución creó 0 filas.

Contenido: cuatro cuentas (`asistente@uv.mx`, `organizador@uv.mx`,
`validador@uv.mx`, `admin@uv.mx`, contraseña `enzona123`), seis categorías, cinco
eventos con coordenadas de Orizaba, sus tipos de boleto y los planos de asientos
del Concierto (250 butacas) y de la Obra (128).

Dos diferencias deliberadas con `MockRepository`:

- **Fechas.** La Feria era el 25 de septiembre, ya pasado. El sembrador recorre
  por semanas completas cualquier fecha vencida; la Feria quedó el viernes 2 de
  octubre a las 10:00. Conserva día de la semana y hora.
- **Ocupación ficticia.** El mock marcaba asientos ocupados y lugares vendidos
  sin boletos que los respaldaran. En la base real el inventario nace completo.

## 2. Arquitectura implementada frente al Anexo E

Spring Boot 3.5.16, Java 21, Maven Wrapper. Monolito modular por capas en
`backend/src/main/java/mx/uv/enzona/`.

| Módulo | Responsabilidad | Requisitos |
|---|---|---|
| `auth` | Registro, OTP, sesión con JWT | RF-01 a RF-03 |
| `usuarios` | Perfil, contraseña, roles, cargos | RF-04 |
| `eventos` | Catálogo con distancia, ciclo de vida, asientos, personal de acceso, cancelación | RF-05 a RF-09, RF-13, RF-14, RF-19 |
| `ordenes` | Compra de N boletos, pasarela, métodos de pago | RF-10, RF-11, RF-16 |
| `boletos` | Emisión, firma HMAC y consulta | RF-12 |
| `validacion` | Escaneo, manifiesto y sincronización por lote | RF-15 |
| `notificaciones` | Avisos y recordatorio diario; puerto de correo | RF-12, RF-14 |
| `admin` | Usuarios, roles, categorías, eventos | RF-18 |
| `common` | Seguridad, errores RFC 7807, cifrado, formato | Transversal |

Diferencias con el Anexo E:

| Tema | Anexo E | Implementado | Motivo |
|---|---|---|---|
| Rutas de acceso | `/api/auth/sesion`, `/api/auth/verificacion` | `/api/auth/login`, `/api/auth/otp/verificar` | Son las del encargo v5. Las del Anexo se conservan como alias |
| Sincronización | `/api/validaciones/sincronizar` | `/api/validaciones/lote` | Ídem, con alias |
| Aviso de la pasarela | `POST /api/pagos/webhook` con firma | `POST /api/pagos/{id}/confirmar` | No hay proveedor. El endpoint simula el aviso y está rotulado como simulación |
| Roles en el token | Se comprueban desde el token | El token prueba la identidad; roles y estado se leen de la base en cada petición | Conceder un rol o bloquear una cuenta surte efecto de inmediato |
| Manifiesto | «Firmado»; el dispositivo verifica el HMAC | Huellas SHA-256 de `codigo.firma`; se valida por pertenencia | La clave HMAC no debe salir del servidor |
| Compra | Descuento y emisión en una transacción | Reserva, cobro fuera de transacción y cierre | Es lo que pide la tabla E.8: no mantener bloqueos durante la llamada a la pasarela |
| Firma del boleto | HMAC-SHA256 | HMAC-SHA256 truncado a 32 hexadecimales | QR más legible; 128 bits siguen siendo inviables de falsificar |
| Clave HMAC por evento | Contemplada | Una sola clave | Pendiente |
| Paginación del catálogo | Desde el primer incremento | Parámetro `limite` (100 por defecto, 200 máximo) | Pendiente la paginación con cursor |

### 2.1 Decisiones de seguridad

- **Contraseñas:** BCrypt, factor 10.
- **CURP (RNF-03):** cifrada en reposo con AES-256-GCM. El índice ciego permite
  unicidad e inicio de sesión por CURP sin descifrar. La API solo la devuelve
  enmascarada.
- **JWT:** firmado con `ENZONA_JWT_SECRET`, expira a las 12 horas, con `sub`,
  `roles` y `nombre`.
- **Métodos de pago:** el servidor recibe marca, últimos 4, vencimiento y token.
  Rechaza con 400 cualquier campo que parezca un número de tarjeta o un CVV.
- **Errores:** `application/problem+json` con `detail` y `codigo`. 400
  validación, 401, 403, 404, 409 conflicto de inventario o estado, 422 regla de
  negocio.
- **Secretos:** solo en variables de entorno o en `backend/.env`, que git ignora.

### 2.2 Concurrencia

| Regla | Mecanismo |
|---|---|
| No sobrevender | `UPDATE tipo_boleto SET cantidad_disponible = cantidad_disponible - :n WHERE id = :id AND cantidad_disponible >= :n`; 0 filas es 409 |
| Un asiento, un boleto | `UPDATE asiento ... WHERE estado = 'DISPONIBLE'`; si las filas afectadas no coinciden con lo pedido, se revierte todo. Respaldo: `ux_boleto_asiento_vigente` |
| Idempotencia | Índice único `(asistente_id, clave_operacion)` |
| Un solo uso del boleto | `UPDATE boleto SET estado = 'USADO' ... WHERE estado = 'VALIDO'` y exactamente una fila |
| Compra frente a cancelación | La compra lee el evento con `FOR SHARE`; la cancelación lo bloquea con `FOR UPDATE` |
| Una confirmación gratuita por persona | Candado consultivo por (evento, persona) |

## 3. Qué es simulación

| Pieza | Comportamiento actual | Para que sea real |
|---|---|---|
| Pasarela de pago | `PasarelaSimulada`: tarjeta aprobada salvo token terminado en `-rechazo`; OXXO y SPEI quedan pendientes. Referencias con prefijo `SIM-` | Otra implementación de `PasarelaPagos` y el aviso con firma verificada |
| Reembolsos | Se registran en `pago` con una referencia simulada | Llamada real a la pasarela |
| Correo | `ServicioCorreoSimulado` solo escribe en el log | Adaptador SMTP o de un proveedor |
| Código de verificación | Se escribe en el log y, solo en `dev`, va en la respuesta | Proveedor de correo o SMS |
| Tokenización de tarjeta | La app deduce marca y últimos 4 y genera un token `tok_sim_…` | SDK de la pasarela |
| Cobro de la comisión | Queda como cargo pendiente en `cargo_organizador` | Proceso de cobro al organizador |

## 4. Cómo levantar el backend

Requisitos: JDK 21. Docker solo hace falta para las pruebas.

1. Crear `backend/.env` a partir de `backend/.env.example`.
2. Desde `backend/`:

```
./mvnw spring-boot:run
```

Sin perfil indicado se activan `dev` y `seed`. La primera vez Flyway aplica las
migraciones y el sembrador crea los datos de demostración.

| Dirección | Contenido |
|---|---|
| `http://localhost:8080/actuator/health` | Estado del servicio |
| `http://localhost:8080/swagger-ui.html` | Documentación interactiva (solo `dev`) |
| `http://localhost:8080/v3/api-docs` | OpenAPI en JSON |

Producción: `--spring.profiles.active=prod`. Sin OTP en las respuestas, sin
Swagger, sin datos de demostración y sin confirmación simulada para el titular.

## 5. Cómo apuntar la app

En `local.properties`, que git ignora:

```
enzona.api.url=http://10.0.2.2:8080/
```

- Emulador: `10.0.2.2` es la PC donde corre el backend.
- Teléfono físico: la IP de la PC en la red local.
- Vacía: la app usa `MockRepository` y muestra «Datos de prueba · sin conexión
  al servidor».

El tráfico HTTP en claro solo está permitido en la compilación `debug`
(`app/src/debug/res/xml/network_security_config.xml`). En `release` se exige
HTTPS.

### 5.1 Cambios en la app

| Archivo | Cambio |
|---|---|
| `data/RepositorioEnZona.kt` | Interfaz con operaciones `suspend` y `ErrorEnZona` |
| `data/RepositorioMock.kt` | Adaptador sobre `MockRepository` |
| `data/remote/RepositorioRemoto.kt` | Retrofit y OkHttp, interceptor del token, errores, modo sin conexión |
| `data/remote/ApiService.kt` | Contrato completo: 49 operaciones |
| `data/remote/Mapeo.kt` | DTO a dominio, fechas ISO a la zona del evento |
| `data/remote/AlmacenSesion.kt` | Token en `EncryptedSharedPreferences` |
| `data/Repositorio.kt` | Elige la implementación; `Preferencias` del teléfono |
| `ui/components/Carga.kt` | Estados cargando, error con reintento y operación en curso |
| Las 19 pantallas | Piden sus datos al repositorio y muestran los tres estados |

`MockRepository` no implementa la interfaz directamente porque Kotlin no admite
una función `suspend` y otra normal con la misma firma. Conserva sus funciones
síncronas, que usan las pruebas unitarias y los previews.

Otros cambios visibles:

- **Cerca de mí (RF-19, RNF-11).** Pide el permiso solo al tocarlo. Con permiso
  la lista llega ordenada por distancia del servidor; sin él se filtra por
  ciudad. La ubicación vive en memoria y se borra al retirar el consentimiento.
- **Pago pendiente.** Con OXXO o SPEI el resumen muestra la referencia y un
  botón de demostración para simular la confirmación.
- **Validador.** Elige el evento. El modo sin conexión descarga el manifiesto,
  valida por pertenencia y sincroniza el lote.
- **Sesión.** Se restaura al abrir la app. Si el token expira, la app vuelve al
  acceso y lo explica.
- **Fechas.** Abreviaturas fijas en la app y en el servidor. Las de la
  plataforma cambian entre versiones («sep» y «sept»).

## 6. Pruebas ejecutadas

### 6.1 Backend: 49 de 49

`./mvnw test` con Testcontainers y PostgreSQL 18. Flyway construye el esquema
desde V1 en cada ejecución. Las pruebas nunca tocan Aiven: la conexión se fija
con la del contenedor y se comprueba antes de cada prueba.

| Suite | Casos | Cubre |
|---|---|---|
| `AccesoIT` | 8 | CP-01 a CP-06, CURP cifrada, cuenta bloqueada |
| `ComprasIT` | 12 | CP-09, CP-10, CP-11, CP-MULTI-01/02/03/05/06/07/08/09/11, OXXO pendiente |
| `ValidacionIT` | 4 | CP-12, CP-13, CP-14, CP-MULTI-10, firma alterada |
| `CancelacionIT` | 8 | CP-16, CP-CANCEL-01 a 07, reserva pendiente al cancelar |
| `EventosYRolesIT` | 12 | CP-07, CP-08, CP-15, CP-17, CP-ROL-01 a 04, métodos de pago, notificaciones, RF-18 |
| `ReglasTest` | 5 | Política de cancelación, redondeo, firma, cifrado, formato |

Concurrencia real con hilos:

- Ocho personas compiten por los últimos tres lugares: tres compran y cinco
  reciben 409.
- Dos compras piden el mismo asiento: una lo obtiene y la otra no se queda con
  su segundo asiento.
- Dos peticiones simultáneas con la misma clave devuelven la misma orden.
- Seis terminales escanean el mismo QR: una lo admite.
- La misma cuenta confirma dos veces a la vez un evento gratuito: una prospera.

Las pruebas encontraron dos defectos que se corrigieron: la confirmación
gratuita simultánea emitía dos boletos, y el mes salía como «Sept».

### 6.2 Android

| Suite | Resultado |
|---|---|
| Unitarias sobre `MockRepository` (58) | 58 de 58 |
| `RepositorioRemotoTest` con MockWebServer (10) | 10 de 10 |
| Compose en emulador, `DialogosTest` (7) | 7 de 7 |

`RepositorioRemotoTest` cubre el interceptor del token, la restauración de la
sesión, el mapeo de fechas y distancia, la solicitud de compra, los errores 401,
409 y 422, la tarjeta sin número ni CVV y el modo sin conexión.

### 6.3 Flujo completo en el emulador contra la base real

Capturas en `docs/capturas_v5/`.

| Archivo | Qué muestra |
|---|---|
| 01 | Acceso sin la etiqueta de datos de prueba |
| 02, 03 | Registro y verificación con el código simulado |
| 04 | Catálogo cargado de la API |
| 05, 06 | Permiso de ubicación al tocar «Cerca de mí» y CP-18 al negarlo |
| 07 | CP-17: catálogo ordenado por distancia |
| 08 | Detalle con tipos de boleto |
| 09 | Tarjeta guardada con solo los últimos 4 |
| 10 | CP-10: pago rechazado con el token `-rechazo` |
| 11, 12, 13 | Compra de 2 boletos, QR firmado y notificación |
| 14 a 18 | Validador: elige evento, boleto de otro evento, CP-12, CP-13 y firma alterada |
| 19 a 21 | CP-14: manifiesto, validación con la red del teléfono cortada y sincronización con duplicados |
| 22, 23 | Panel del organizador |
| 24, 25 | Cancelación con comisión calculada en el servidor |
| 26 a 28 | Plano de asientos, pago OXXO pendiente y confirmación simulada |
| 29 | Sin URL: datos de prueba con su etiqueta |

En CP-14 la red del teléfono se cortó de verdad. Mientras tanto otra terminal
con conexión admitió el mismo boleto. Al sincronizar, el servidor marcó como
duplicados los dos escaneos sin conexión de ese boleto.

### 6.4 Casos que no se pueden cerrar

- **CP-20, paridad web y móvil.** No hay web en el repositorio. El contrato
  OpenAPI y el cálculo en servidor la hacen posible, pero no está verificada.
- **CP-19** quedó verificado en emulador con Android 17, no en teléfono físico.
- **CP-04 en el emulador.** Está cubierto por `AccesoIT`; la captura 03 muestra
  el estado de carga, no el mensaje de error.

## 7. Datos de prueba que quedaron en la base real

El recorrido dejó estos registros en Aiven, además de los de demostración:

- Tres cuentas de prueba: dos `flujo-…@uv.mx` y una `maria.…@uv.mx`.
- Seis órdenes con sus boletos, varios ya usados.
- El evento «Recital de prueba v5», cancelado, con su cargo de 10.00 MXN.
- Una tarjeta de prueba en `metodo_pago`.

## 8. Pendientes

- **Rotar la contraseña de la base.** Se compartió en el chat y en una captura
  de pantalla. Conviene cambiarla en Aiven y actualizar `backend/.env`.
- Web React sobre el mismo contrato.
- Pasarela real, aviso con firma verificada y cobro de comisiones.
- Proveedor de correo y de SMS.
- Despliegue: contenedor, HTTPS, perfil `prod`, respaldos.
- Límite de intentos en el inicio de sesión. El OTP ya lo tiene.
- Paginación con cursor y clave HMAC por evento.
- Latencia: una compra tarda unos 4 segundos desde la PC de desarrollo por la
  distancia a la base. Desplegar la API cerca de la base la reduce.
- En modo de datos de prueba la Feria sigue fechada el 25 de septiembre.
- Unificar en los documentos la versión de PostgreSQL y los nombres de los
  estados.
