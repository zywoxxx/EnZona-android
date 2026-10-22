# EnZona v5 · Backend Spring Boot conectado a PostgreSQL (Aiven) y app Android consumiendo la API

Trabaja sobre el repositorio actual de EnZona (`EnZona-android_3`). Hasta hoy la app Android funciona con `MockRepository` (todo en memoria). El objetivo de esta tarea es que **exista el backend REST que describen los documentos** (Anexo E: Spring Boot 3 + Java 21 + PostgreSQL, monolito modular por capas) conectado a la base de datos real en Aiven, y que la app Android deje de usar el mock y consuma esa API. La web React no forma parte de este repositorio; deja el contrato listo para ella.

No termines con un diagnóstico ni con un backend "de ejemplo": el flujo completo registro → login → ver eventos → comprar N boletos → ver QR → validar en puerta debe funcionar contra la base real. Compila, ejecuta y prueba.

## Fuentes que debes leer primero

En `docs/`: `Anexo_E_Arquitectura_EnZona.docx` (módulos, endpoints, decisiones de seguridad), `requisitos_y_pruebas_v2.1.docx`, `esquema_enzona_v2.sql`, `migracion_v2_incremental.sql`, `migracion_v3_incremental.sql`, `migracion_v4_incremental.sql`, `esquema_metodo_pago.sql` (en la raíz), `notas_v2_compra_multiple.md`, `notas_v3_cancelacion_rol_mapa_fecha.md`, `notas_v4_modelo_notificaciones.md`. En la app: `data/remote/ApiService.kt` (contrato que ya espera la app), `data/MockRepository.kt` (toda la lógica de negocio que hoy vive en el teléfono y que debe pasar al servidor), `data/model/*.kt`, `SessionManager.kt` y las pruebas de `app/src/test`.

## Credenciales y seguridad de la configuración

Las credenciales de la base están en `backend/.env` (yo lo creo a mano; **no lo generes tú con valores reales y no lo versiones**). Contiene:

```
ENZONA_DB_URL=jdbc:postgresql://<host>:<puerto>/defaultdb?sslmode=require
ENZONA_DB_USER=avnadmin
ENZONA_DB_PASSWORD=<contraseña>
ENZONA_JWT_SECRET=<cadena aleatoria de 64+ caracteres>
ENZONA_HMAC_SECRET=<otra cadena aleatoria de 64+ caracteres>
```

- Lee esas variables desde `application.yml` con `${ENZONA_DB_URL}` etc. Añade `backend/.env`, `backend/.env.*` y `backend/src/main/resources/application-local.yml` al `.gitignore`. Incluye `backend/.env.example` con placeholders.
- Antes de escribir código, **comprueba la conexión** (por ejemplo `psql "$ENZONA_DB_URL"` o un `DataSource` mínimo) y reporta la versión real del servidor (`SELECT version()`); la documentación dice PostgreSQL 11 en el SQL y 15 en la bibliografía, alinea la nota técnica con la versión real.
- Aiven limita a **20 conexiones**: configura HikariCP con `maximum-pool-size: 5` y `minimum-idle: 1`.
- Nunca imprimas la contraseña en logs ni en el resumen final. Si en algún archivo aparece la contraseña, elimínala y avísame.

## Estado de la base de datos

La base **ya tiene las tablas** (se ejecutó el esquema v2) pero **no tiene datos**. Debes:

1. Inspeccionar el esquema real (`information_schema`, tipos enum, índices) y compararlo con `esquema_enzona_v2.sql` + `migracion_v2/v3/v4_incremental.sql` + `esquema_metodo_pago.sql`. Aplica **solo lo que falte**, de forma idempotente (`IF NOT EXISTS`, `DO $$ ... $$`), como migraciones **Flyway** versionadas en `backend/src/main/resources/db/migration/`. Usa `flyway.baseline-on-migrate=true` con una `V1__baseline.sql` vacía o descriptiva, porque las tablas ya existen; no ejecutes `esquema_enzona_v2.sql` (tiene `DROP ... CASCADE`) ni uses `\echo`.
2. Verificar en particular: `orden.clave_operacion` (idempotencia), `orden_detalle`, `ux_boleto_asiento_vigente`, columnas de cancelación en `evento`, `cargo_organizador`, `notificacion_asistente` con `tipo`/`leida`/`boleto_id`, tabla `metodo_pago` y `pago.metodo_pago_id`, y que `estado_pago` incluya `PENDIENTE` (si no, añádelo con `ALTER TYPE ... ADD VALUE IF NOT EXISTS` fuera de transacción).
3. Crear una migración de **datos de demostración** (`R__seed_demo.sql` o un `CommandLineRunner` bajo el perfil `seed`, tú eliges y lo justificas) con lo mismo que hoy trae `MockRepository`: roles; las cuentas `asistente@uv.mx`, `organizador@uv.mx`, `validador@uv.mx`, `admin@uv.mx` con contraseña `enzona123` en **BCrypt**; categorías; los eventos de demostración con **coordenadas reales de Orizaba** (`ciudad`, `latitud`, `longitud`), tipos de boleto, y los mapas de asientos de *Concierto: Orquesta Universitaria* y *Obra: La Casa de Bernarda Alba*. El seed debe ser re-ejecutable sin duplicar filas.

## Backend: `backend/` (Spring Boot 3.x, Java 21, Maven Wrapper)

Estructura por capas y módulos del Anexo E: `auth`, `usuarios`, `eventos`, `ordenes` (compra/pagos), `boletos`, `validacion`, `notificaciones`, `admin`, más `common` (errores, seguridad, utilidades). Dependencias: Web, Validation, Data JPA, PostgreSQL, Flyway, Security, `jjwt` (o `spring-security-oauth2-jose`), Actuator (`/actuator/health`), springdoc-openapi (`/swagger-ui.html`), Testcontainers para pruebas. No añadas Kafka, Redis ni microservicios.

### Autenticación y roles

- `POST /api/auth/registro` (nombre, correo, teléfono, CURP validada como en `util/Validaciones.kt`, contraseña) → crea `usuario` en estado pendiente y genera OTP; `POST /api/auth/otp/verificar`, `POST /api/auth/otp/reenviar`. Mientras no haya proveedor de correo/SMS, el OTP se registra en el log del servidor y se devuelve **solo en perfil `dev`** en la respuesta, marcado como simulación.
- `POST /api/auth/login` (correo o teléfono + contraseña) → JWT firmado con `ENZONA_JWT_SECRET`, expiración 12 h, con `sub`, `roles` y `nombre`. `GET /api/usuarios/me`, `PUT /api/usuarios/me` (nombre, teléfono), `PUT /api/usuarios/me/contrasena`.
- Autorización por rol con `@PreAuthorize`: ASISTENTE, ORGANIZADOR, VALIDADOR, ADMINISTRADOR. Roles en `usuario_rol`. `POST/DELETE /api/usuarios/me/roles/organizador` según `ApiService.kt` (409 si tiene eventos publicados con boletos vigentes o es su único rol; los administradores no pueden dejar el rol por esta vía).
- Contraseñas con BCrypt. CURP: RNF-03 pide cifrado; cífrala en reposo (AES-GCM con clave en variable de entorno) o documenta explícitamente que queda pendiente. No la devuelvas completa en ningún DTO.

### Eventos (RF-05..RF-09, RF-13, RF-14, RF-19)

- `GET /api/eventos?ciudad=&categoria=&desde=&hasta=&lat=&lng=&radioKm=&q=` → publicados; si vienen `lat`/`lng`, ordena por **distancia real** (fórmula de Haversine en SQL o en servicio) y devuelve `distanciaKm`; los eventos sin coordenadas van al final sin distancia inventada (CP-17). `GET /api/eventos/{id}`, `GET /api/eventos/{id}/tipos`, `GET /api/eventos/{id}/asientos` (con estado LIBRE/OCUPADO/RESERVADO).
- Organizador: `POST /api/eventos` (borrador), `PUT /api/eventos/{id}`, `POST /api/eventos/{id}/publicar` (exige coordenadas, fecha futura y al menos un tipo de boleto), `POST /api/eventos/{id}/realizado`, `PUT /api/eventos/{id}/asientos` (activar/desactivar mapa; 409 si ya hay boletos emitidos), `GET/POST/DELETE /api/eventos/{id}/personal-acceso` (designar validadores por correo, sobre `usuario_rol`).
- Fechas: `fechaHoraInicio`/`fechaHoraFin` ISO‑8601 con zona ↔ `TIMESTAMPTZ`; el servidor trabaja en UTC y la app formatea con `America/Mexico_City`.
- `GET /api/eventos/{id}/politica-cancelacion` y `POST /api/eventos/{id}/cancelacion` exactamente como los DTO de `ApiService.kt`, reproduciendo la regla de `MockRepository.politicaCancelacionDe()`/`cancelarEvento()`: gratuito cancelable; de pago con comisión del 10 % sobre lo cobrado (`BigDecimal`, `HALF_UP`, 2 decimales) registrada en `cargo_organizador` y `evento.comision_cancelacion`; sin ventas → comisión 0; iniciado/finalizado → 409; administrativa → motivo obligatorio y sin comisión. Todo en **una transacción**: boletos → CANCELADO, asientos liberados, órdenes → REEMBOLSADA, pagos → REEMBOLSADO (con `referencia_reembolso` de la pasarela simulada), notificación `EVENTO_CANCELADO` a cada asistente.

### Órdenes, pagos e inventario (RF-10, RF-11, RF-12, RF-16, RNF-05, RNF-09)

- `POST /api/ordenes` con cabecera `Idempotency-Key` y `CrearOrdenDto` (cantidad N, `asientosIds`, método). Reglas del comentario de `ApiService.kt`: precios derivados de `tipo_boleto`, validación de pertenencia al evento, asientos distintos y libres, cupo y stock por lote, **todo o nada**. Descuento de inventario con `UPDATE tipo_boleto SET cantidad_disponible = cantidad_disponible - :n WHERE id = :id AND cantidad_disponible >= :n` y ocupación de asientos con `UPDATE asiento ... WHERE estado = 'LIBRE'` comprobando el número de filas afectadas; si no coincide, `rollback` y 409 con detalle de cuántos quedan. Idempotencia: misma clave → misma orden, sin cobrar ni emitir de nuevo (`orden.clave_operacion` único).
- Pasarela: implementa la interfaz `PasarelaPagos` con `PasarelaSimulada` (tarjeta aprobada salvo que el `token_pasarela` termine en `-rechazo`, que reproduce CP-10; OXXO/SPEI devuelven referencia y estado PENDIENTE). Los boletos se emiten solo con pago APROBADO; para pendientes se guarda `orden_detalle` y un endpoint `POST /api/pagos/{id}/confirmar` (solo `dev`/admin) simula la confirmación de la pasarela y emite. Mantén la simulación visible en logs y OpenAPI; no la presentes como pago real.
- Boletos: `codigo` único + `firma` HMAC‑SHA256 con `ENZONA_HMAC_SECRET`; el QR es `codigo.firma`. `GET /api/ordenes/{id}`, `GET /api/boletos` (los míos, con datos del evento y asiento resueltos: el `BoletoDetalle` de la app), `GET /api/boletos/{id}`. El envío por correo (RF-12) queda como interfaz `ServicioCorreo` con implementación que registra en log; no afirmes envío real.
- Métodos de pago (`metodo_pago`): `GET/POST/DELETE /api/usuarios/me/metodos-pago`, `POST .../{id}/predeterminado`. El servidor **solo** recibe `marca`, `ultimos4`, `vencimiento` y `tokenPasarela`; rechaza con 400 cualquier campo que parezca un PAN o CVV (Visión y Alcance: la plataforma no almacena datos de tarjeta).

### Validación de acceso (RF-15, RNF-02, RNF-08)

- `POST /api/validaciones` `{ contenidoQr, eventoId }` (rol VALIDADOR): verifica la firma antes de tocar la base; **un solo uso** con `UPDATE boleto SET estado = 'USADO', fecha_uso = now() WHERE codigo = :c AND estado = 'VIGENTE'` y filas afectadas = 1; registra `validacion_acceso` con resultado (PERMITIDO, YA_USADO, CANCELADO, EVENTO_CANCELADO, FIRMA_INVALIDA, NO_ENCONTRADO).
- Modo sin conexión: `GET /api/eventos/{id}/manifiesto` devuelve los códigos vigentes (hash SHA‑256 de `codigo.firma`, no la clave HMAC, que nunca sale del servidor) y `POST /api/validaciones/lote` recibe las validaciones hechas sin conexión y las reconcilia con la misma regla de un solo uso, devolviendo cuáles resultaron duplicadas. Documenta que la verificación offline es por pertenencia al manifiesto, no por firma.

### Notificaciones y administración

- `GET /api/usuarios/me/notificaciones`, `POST .../{id}/leida`, `POST .../leidas` según `ApiService.kt`. Generación: `COMPRA_CONFIRMADA` al emitir, `EVENTO_CANCELADO` y `FECHA_CAMBIADA` en sus casos, `RECORDATORIO` con `@Scheduled` diario (respetando `uq_notificacion_recordatorio`).
- Admin (RF-18): `GET /api/admin/usuarios`, `PUT /api/admin/usuarios/{id}/estado`, `PUT /api/admin/usuarios/{id}/roles`, `GET /api/admin/eventos`, cancelación administrativa por el endpoint común, `GET/POST /api/admin/categorias`.

### Transversal

- Errores en formato uniforme (`ProblemDetail` RFC 7807) con códigos: 400 validación, 401, 403, 404, 409 conflicto de inventario/estado, 422 regla de negocio. La app debe mostrar el `detail`.
- CORS para la web (`http://localhost:5173`) configurable. `server.port=8080`. Perfiles `dev` (OTP en respuesta, swagger abierto, seed) y `prod`.
- OpenAPI generado; guarda `docs/openapi_enzona_v5.json` exportado del backend para la web y para el Anexo E.

## Android: sustituir `MockRepository` por la API

- Extrae la superficie pública de `MockRepository` a una interfaz `RepositorioEnZona` (mismos nombres y tipos de dominio que hoy usan las pantallas). `MockRepository` la implementa (y sigue sirviendo a las pruebas unitarias y a `ui/preview/Fixtures.kt`); crea `RemoteRepository` que la implementa con Retrofit + OkHttp: interceptor que añade `Authorization: Bearer`, token guardado en `EncryptedSharedPreferences` o DataStore, refresco de sesión al abrir la app, `SessionManager` alimentado por `GET /api/usuarios/me`.
- Las funciones pasan a `suspend`; adapta las pantallas con `LaunchedEffect`/`rememberCoroutineScope` o ViewModels sencillos, con estados de **cargando / error / reintentar** visibles (hoy todo es síncrono en memoria). Hazlo pantalla por pantalla y compila en cada paso; no dejes la app a medias.
- Selección de implementación: `BuildConfig.ENZONA_API_URL` leído de `local.properties` (`enzona.api.url=http://10.0.2.2:8080/` para emulador; para teléfono físico la IP de mi PC en la LAN). Si la variable está vacía, la app usa `MockRepository` y muestra una etiqueta "Datos de prueba" para que sea evidente. Permite tráfico HTTP en claro solo en `debug` mediante `network_security_config.xml`; en `release` exige HTTPS.
- Completa `ApiService.kt` con los endpoints nuevos (auth, tipos, asientos, validaciones, métodos de pago, admin, personal de acceso) y elimina los comentarios "pendiente de backend" que dejen de serlo. El pago sigue pasando por `CheckoutScreen`; el interruptor «Simular pago rechazado» ahora usa el token `-rechazo` de la pasarela simulada.
- Mapa y ubicación: `GET /api/eventos` recibe `lat`/`lng` cuando el usuario dio consentimiento (RNF-11) y la lista se ordena por `distanciaKm` del servidor (CP-17); sin consentimiento, filtra por `ciudad=Orizaba`.
- Modo sin conexión del validador: descarga el manifiesto al entrar al evento, valida localmente por pertenencia y sincroniza con `/api/validaciones/lote` (CP-14 ahora sí es verificable).
- Conserva la paleta v2, los alias, la compra múltiple, la cancelación con comisión, los diálogos de rol, los selectores de fecha/hora y el mapa (incluida la corrección de que la dirección se actualiza al mover el marcador). No subas versiones de Gradle/AGP/Kotlin/Compose ni `minSdk`.

## Pruebas

- Backend: pruebas de integración con **Testcontainers PostgreSQL** (tengo Docker; si no arranca, usa un esquema `enzona_test` en Aiven y límpialo al terminar, nunca la base principal con datos de demo). Cubre: registro/OTP/login; compra de 2 sin asiento y 3 con asiento (CP-MULTI-01/02); 3 cuando quedan 2 (CP-MULTI-07); idempotencia con la misma clave (CP-MULTI-09); **concurrencia real**: dos hilos compran el mismo asiento y solo uno lo obtiene (CP-11, CP-MULTI-10); QR usado dos veces (CP-13); política de cancelación gratuito/pago/sin ventas/iniciado/administrativa (CP-CANCEL-01..07); rol organizador (CP-ROL-01..04); orden por distancia (CP-17); manifiesto + lote sin conexión (CP-14).
- Android: mantén verdes las pruebas actuales sobre `MockRepository`; añade pruebas de `RemoteRepository` con `MockWebServer` (OkHttp) para el mapeo de DTO ↔ dominio, el interceptor JWT y el manejo de 409/422.
- Ejecuta el flujo completo en emulador contra el backend levantado en mi PC y guarda capturas en `docs/capturas_v5/`. CP-20 (paridad web/móvil) sigue sin poder cerrarse sin la web: dilo explícitamente.

## Documentación y entrega

- `docs/notas_v5_backend.md`: arquitectura real implementada vs. Anexo E (módulos, endpoints, diferencias), versión de PostgreSQL real, cómo levantar el backend (`./mvnw spring-boot:run` con `.env`), cómo apuntar la app, qué es simulación (pasarela, correo, OTP) y pendientes (web React, pasarela real, despliegue).
- `README.md`: sección "Backend" y actualización de "Conectar el backend cuando esté listo" (ya no es futuro).
- Migraciones Flyway con comentarios; `docs/openapi_enzona_v5.json`.
- Resumen final con: tabla de hallazgos del esquema real vs. documentado, endpoints implementados, pruebas ejecutadas y su resultado, y lo que quedó pendiente. Sin credenciales en el resumen.
