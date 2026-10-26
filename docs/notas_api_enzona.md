# Conexión de la app con EnZona_Api

La app Android consume la API REST del equipo, **EnZona_Api**
(https://github.com/WilliamSosa895/EnZona_Api, Spring Boot 3.3 + Java 21 +
PostgreSQL 15, versión 0.1.0). Este documento resume cómo se corre la API, qué
rutas usa cada función de la app, qué diferencias de contrato resolvió la app y
qué queda por acordar con el equipo.

El backend propio de v5 (`backend/`) se conserva como prototipo de referencia,
pero la app ya **no** habla su contrato: `RepositorioRemoto` implementa el de la
API del equipo.

## Correr la API en la PC

Requisitos: JDK 21, Maven 3.9 y Docker (para PostgreSQL 15). La API nunca se
corre desde este repositorio; se clona junto a él.

```
git clone https://github.com/WilliamSosa895/EnZona_Api.git
cd EnZona_Api
cp .env.example .env
```

En `.env` llena `DB_URL`, `DB_USER`, `DB_PASSWORD` y genera cada clave con
`openssl rand -base64 32` (`JWT_SECRET`, `TICKET_HMAC_SECRET`,
`CURP_ENCRYPTION_KEY`, `CURP_INDEX_KEY`, `PAYMENT_WEBHOOK_SECRET`). Deja
`NOTIFICATIONS_MODE=log` y `PAYMENT_PROVIDER=sandbox` para desarrollo y define
`ADMIN_EMAIL` y `ADMIN_PASSWORD` si quieres la cuenta de administración. **Ese
archivo no se versiona ni se comparte.**

Base de datos local y arranque (Git Bash):

```
docker run -d --name enzona-api-db -e POSTGRES_DB=enzona -e POSTGRES_USER=enzona \
  -e POSTGRES_PASSWORD=<la misma de .env> -p 5435:5432 postgres:15-alpine
# en .env: DB_URL=jdbc:postgresql://localhost:5435/enzona
set -a; source .env; set +a
mvn -DskipTests package && java -jar target/enzona-api-0.1.0.jar
```

Flyway crea el esquema (V1 a V5) al arrancar. Comprobaciones:

| Dirección | Contenido |
|---|---|
| `http://localhost:8080/api/categorias` | Catálogo público; responde en cuanto la API está lista |
| `http://localhost:8080/swagger-ui.html` | Documentación interactiva |
| `http://localhost:8080/actuator/health` | Responde `DOWN` en la PC porque el indicador de correo busca un SMTP que no existe; no afecta a la API |

Con `NOTIFICATIONS_MODE=log` el **código de verificación** del registro aparece
en la consola de la API (`[DEV] Código de verificación para <correo> por
CORREO: 123456`); la app ya no lo muestra porque la API nunca lo devuelve.

> **Base de Aiven.** La base compartida del equipo tiene hoy el esquema del
> prototipo `backend/` (Flyway V1 a V7 propias). EnZona_Api espera su propio
> historial (V1 a V5): si se apunta a esa base, Flyway se detiene por las
> diferencias de versiones y sumas de verificación. Antes de usar Aiven con la
> API hay que acordar en el equipo qué esquema se conserva y recrear la base.

## Correspondencia app ↔ API

| Función de la app (`RepositorioEnZona`) | Ruta de la API |
|---|---|
| `registrar` | `POST /api/auth/registro` (`aceptaAvisoPrivacidad: true`, `telefono` solo con dígitos o ausente) |
| `verificarOtp` | `POST /api/auth/verificacion` con `correo` y `canal` (CORREO y después TELEFONO si se dio teléfono) |
| `reenviarOtp` | `POST /api/auth/verificacion/reenvio` (202 sin cuerpo) |
| `iniciarSesion` | `POST /api/auth/sesion` y después `GET /api/usuarios/me` para el perfil completo |
| `restaurarSesion`, `usuarioActual` | `GET /api/usuarios/me` |
| `actualizarPerfil`, `cambiarContrasena` | `PUT /api/usuarios/me`, `PUT /api/usuarios/me/contrasena` |
| `convertirseEnOrganizador`, `dejarDeSerOrganizador` | `POST`/`DELETE /api/usuarios/me/roles/organizador` (409 `ROL_EN_USO`) |
| `motivoNoPuedeDejarOrganizador` | No existe en la API: la app aplica la misma regla con `GET /api/organizador/eventos` |
| `categorias` | `GET /api/categorias` (se guarda en memoria para traducir nombre → `categoriaId`) |
| `eventosPublicados` | `GET /api/eventos?size=100` con `ciudad=` o con `lat`, `lng` y `radioKm=50` |
| `evento`, `tiposDe` | `GET /api/eventos/{id}` (los tipos vienen dentro del detalle) |
| `asientosDe` | `GET /api/eventos/{id}/asientos` |
| `misEventos` | `GET /api/organizador/eventos` |
| `crearEvento` | `POST /api/eventos` (de pago: un tipo «General» con el precio y el aforo del formulario) |
| `modificarEvento` | `PUT /api/eventos/{id}` reenviando los tipos con su `id` para conservarlos |
| `cambiarAsientos` | `PUT /api/eventos/{id}` con `secciones` y el aforo igual al total de asientos |
| `publicarEvento` | `POST /api/eventos/{id}/publicacion` |
| `politicaCancelacion`, `cancelarEvento` | `GET /api/eventos/{id}/politica-cancelacion`, `POST /api/eventos/{id}/cancelacion` |
| `personalDeAcceso`, `designarValidador`, `quitarValidador` | `GET`/`POST /api/eventos/{id}/validadores`, `DELETE …/{usuarioId}` |
| `boletosDeEvento` (panel del organizador) | `GET /api/eventos/{id}/manifiesto` + detalle del evento para los precios |
| `comprar` | `POST /api/ordenes` con `Idempotency-Key`, `metodo` GRATIS/TARJETA y `tokenPago` |
| `orden` | `GET /api/ordenes/{id}` |
| `misBoletos`, `boleto` | `GET /api/boletos` + `GET /api/eventos/{id}` de cada evento (en paralelo) |
| `eventosParaValidar` | `GET /api/organizador/eventos`, `GET /api/admin/eventos?estado=PUBLICADO` o `GET /api/eventos`, según el rol |
| `validarQr` | `POST /api/validaciones` `{eventoId, qr}` |
| `prepararModoSinConexion` | `GET /api/eventos/{id}/manifiesto` |
| `sincronizarValidaciones` | `POST /api/validaciones/sincronizar` `{eventoId, registros[{qr, fechaHora}]}` |
| `usuarios`, `cambiarEstadoUsuario`, `todosLosEventos` | `GET /api/admin/usuarios?size=100`, `PATCH /api/admin/usuarios/{id}/estado`, `GET /api/admin/eventos?size=100` |
| `estadisticasAdmin` (pestaña «Resumen») | Las dos listas anteriores más `GET /api/eventos/{id}/reportes` de cada evento que no sea borrador, pedidos de 4 en 4 en paralelo; la agregación (`calcularEstadisticas`) se hace en el teléfono |

## Portada de los eventos (propuesta a la API)

La API 0.1.0 no tiene imágenes de evento. Se implementó en la rama
`feature/imagenes-evento` del repositorio de la API (parche en
`D:\EnZona_Api\imagenes-evento.patch`) y ya corre en la copia de la PC:

- **Migración V6**: `evento.imagen_url` (URL pública) y `evento.imagen_id`
  (identificador en el almacén, para reemplazar o borrar).
- **`POST /api/eventos/{id}/imagen`** (multipart, campo `imagen`, JPEG/PNG/WebP
  hasta `IMAGE_MAX_MB`) y **`DELETE /api/eventos/{id}/imagen`**, solo para el
  organizador dueño o un administrador. El tipo se valida por el contenido del
  archivo, no por lo que declara el cliente; el nombre del archivo lo decide el
  servidor.
- **Almacén intercambiable** (`IMAGE_PROVIDER`): `local` guarda en
  `IMAGE_LOCAL_DIR` y sirve en `GET /api/imagenes/{archivo}` (URL relativa que la
  app completa con la dirección de la API); `cloudinary` sube con la API REST
  firmada de Cloudinary usando `CLOUDINARY_URL`, que **solo vive en el
  servidor**. Así se evita el riesgo de un preset sin firma en el teléfono
  (cualquiera podría subir a la cuenta) y la app no cambia al cambiar de
  proveedor.
- **En la app**: selector de fotos del sistema (sin permisos nuevos), reducción
  a 1600 px y JPEG antes de subir, previsualización en el formulario, carga con
  Coil en catálogo y detalle; con datos de prueba la portada es la copia local.

Para activar Cloudinary: crear la cuenta, copiar el `CLOUDINARY_URL` del
panel (API Keys) a `D:\EnZona_Api\.env`, poner `IMAGE_PROVIDER=cloudinary` y
reiniciar la API. Las portadas ya guardadas en local siguen sirviéndose.

### Moderación: desnudez y violencia gráfica no llegan a los asistentes

La revisión se hace **en el servidor, antes de guardar** la imagen (una
comprobación en el teléfono se podría saltar llamando a la API con otro
cliente). `ImagenEventoService` valida tipo y tamaño, pasa los bytes por
`ModeradorImagenes` y solo si lo aprueba los entrega al almacén; una imagen
rechazada responde `422 IMAGEN_NO_PERMITIDA` con el motivo («La imagen contiene
desnudez o contenido sexual», «…violencia gráfica», «…violencia»,
«…autolesiones») y la app lo muestra en el formulario.

| `IMAGE_MODERATION` | Cómo funciona |
|---|---|
| `sightengine` | La API envía la imagen a Sightengine (`SIGHTENGINE_USER`, `SIGHTENGINE_SECRET`; plan gratuito de 2 000 revisiones al mes) con los modelos `nudity-2.1`, `gore-2.0`, `violence` y `self-harm`, y rechaza lo que supere `IMAGE_MODERATION_THRESHOLD` (0.7 por defecto). Si el servicio no responde, la imagen se rechaza: nunca se publica sin revisar. |
| `cloudinary` | Con `IMAGE_PROVIDER=cloudinary` y el add-on de moderación (AWS Rekognition) activado en la cuenta: la subida pide `moderation=aws_rek` y solo se publica lo `approved`; lo rechazado o pendiente se borra de inmediato. |
| `off` | Solo desarrollo y demostraciones: acepta todo y deja una advertencia en la bitácora. **Es el valor actual en la PC** hasta que exista una cuenta de moderación. |

Red de seguridad humana: el administrador tiene «Quitar portada» en la pestaña
de eventos de Administración (`DELETE /api/eventos/{id}/imagen`).

## Diferencias de contrato que resolvió la app

- **Verificación por correo y canal, no por id.** La API identifica la cuenta
  pendiente por `correo` y `canal`. La app guarda en el almacén cifrado la
  verificación en curso (correo y canales pendientes); la pantalla del código
  pide primero el del correo y, si se registró teléfono, el del teléfono.
- **Cuenta sin verificar al iniciar sesión.** La API responde 403
  `CUENTA_PENDIENTE` sin id. Si se entró con el correo, la app lo convierte en
  `CUENTA_NO_VERIFICADA` y ofrece ir a la pantalla del código; con la CURP solo
  informa (la verificación necesita el correo).
- **Aviso de privacidad.** El registro exige `aceptaAvisoPrivacidad: true`; la
  pantalla de registro tiene la casilla y un enlace al aviso.
- **Pago con tarjeta.** No hay métodos guardados ni OXXO/SPEI. El pago envía
  `tokenPago`; en sandbox `tok_aprobado` o `tok_rechazado` («Simular pago
  rechazado»). Un rechazo es un **201** con la orden `CANCELADA`: la app lo
  presenta como el error recuperable de siempre y renueva la clave de
  idempotencia, porque la API devuelve la misma orden cancelada si se reutiliza.
- **`Idempotency-Key`** de 8 a 64 caracteres `[A-Za-z0-9_-]`: la app usa un UUID
  y sustituye cualquier clave que no cumpla.
- **Listas paginadas.** La app pide `size=100` (máximo de la API); la paginación
  viaja en cabeceras `X-Total-Count`…
- **Tipos de boleto** dentro del detalle del evento, con `disponibles` en lugar
  de `cantidadDisponible`; `numero` de asiento es texto.
- **Boletos sin orden anidada.** `GET /api/boletos` no trae la orden ni el
  evento completo: la app lee el detalle de cada evento (en paralelo) y
  reconstruye el total de la orden con los boletos hermanos y el precio de su
  tipo; si un evento no es visible usa lo que trae el boleto (nombre, fecha,
  lugar). El asiento se reconstruye desde la etiqueta «Sección · Fila A ·
  Asiento 5».
- **Manifiesto con código y firma** (no huellas SHA-256): sin conexión se compara
  el QR completo; la clave HMAC sigue solo en el servidor.
- **Sincronización.** `registros[{qr, fechaHora}]`; en la respuesta `conflicto`
  (otro dispositivo admitió antes) y `duplicado` (ya sincronizado) se suman como
  «duplicadas» en el resumen de la app.
- **Errores de validación** llegan como `errores: [{campo, mensaje}]`; la app los
  agrega al mensaje («curp: El CURP no tiene un formato válido.»).
- **Códigos nuevos**: `CORREO_REGISTRADO`, `CURP_REGISTRADO`, `AFORO_AGOTADO`,
  `ASIENTO_NO_DISPONIBLE`, `CONFIRMACION_DUPLICADA`, `ESTADO_INVALIDO`,
  `REGLA_NEGOCIO`, `ROL_EN_USO`, `DEMASIADOS_INTENTOS`… se traducen a los
  errores que ya usaban las pantallas (`ErrorCompra.*`).
- **Fechas** con desfase de la Ciudad de México (`2026-11-20T20:00:00-06:00`);
  la app las convierte a la zona del evento como antes.
- **Sesión de 60 minutos** (`JWT_EXPIRATION_MINUTES`): al vencer, el 401
  `NO_AUTENTICADO` devuelve la app al acceso.

## Lo que la API no implementa y cómo lo trata la app

`RepositorioEnZona.capacidades` declara lo que respalda cada fuente de datos; la
interfaz oculta lo que no. Con la API:

| Función de la app | Situación en la API | En la app |
|---|---|---|
| Métodos de pago guardados | `metodoPagoId` responde 422 | Sin fila en «Mi cuenta»; el pago ofrece «Tarjeta de crédito o débito» con token de un solo uso |
| OXXO y SPEI | 422 `METODO_PAGO_NO_SOPORTADO` | No se ofrecen |
| Centro de notificaciones | Los avisos van por correo | La campana de Inicio no aparece |
| Comisión de cancelación al organizador | Reembolso del 100 %, `comision: "0.00"` | Tipo `DE_PAGO_CON_VENTAS`; el diálogo explica el reembolso sin comisión |
| Roles desde administración | No hay endpoint | Los roles se muestran sin editar |
| «Marcar como finalizado» | Una tarea del servidor lo hace al terminar el evento | Sin botón |
| Bitácora de validaciones | No se expone al validador | La app guarda lo validado desde el dispositivo (50 registros) |
| Boletos de mi evento | No hay endpoint para el organizador | El panel se arma con el manifiesto (VALIDO y USADO) |
| Eventos donde estoy designado | No hay endpoint | Un validador ve el catálogo publicado; el servidor rechaza (403) los eventos ajenos |
| Confirmar un pago pendiente | Solo por webhook firmado de la pasarela | No disponible |

## Pendientes a acordar con el equipo de la API

1. **Esquema de la base de Aiven** (ver arriba): decidir cuál se conserva.
2. **Endpoint «eventos donde estoy designado»** para el validador (hoy ve todos
   los publicados) y **bitácora de validaciones** del evento.
3. **Boletos de un evento para su organizador** (lista con asistente, estado y
   fecha de uso), además del manifiesto.
4. **Cuenta pendiente al iniciar sesión**: la respuesta no dice qué canales
   faltan; si el correo ya estaba verificado y solo falta el teléfono, el primer
   intento de la app (canal CORREO) falla con `CODIGO_VERIFICACION_INVALIDO`.
5. **CURP en el perfil**: la API no la devuelve ni enmascarada; la app muestra
   «Guardada cifrada en el servidor».
6. Métodos guardados, OXXO/SPEI, notificaciones en la app y comisión al
   organizador: fuera del alcance actual de la API (`docs/COHERENCIA_APP_MOVIL.md`).

## Pruebas

- `RepositorioRemotoTest` (13 pruebas con MockWebServer) comprueba rutas,
  cuerpos, cabeceras y mapeos con las respuestas que documenta la API:
  sesión y perfil, registro con aviso de privacidad y verificación en dos
  canales, cuenta pendiente, eventos por cercanía y ciudad, compra con
  `tokenPago`, pago rechazado como 201, confirmación gratuita, traducción de
  códigos de error, política sin comisión, designación de validador, «mis
  boletos» con eventos en paralelo, validación en línea con bitácora local y
  modo sin conexión con sincronización.
- Flujo completo en el emulador (Pixel 10 Pro XL, API 37) contra EnZona_Api
  corriendo en la PC con PostgreSQL 15 en Docker, el 15 de octubre de 2026.
  Capturas en `docs/capturas_api/`:

| Captura | Qué comprueba |
|---|---|
| `01_acceso` | Acceso sin la ayuda de cuentas de prueba (no existen en la API) |
| `02_registro`, `03_verificacion`, `03b_codigo_incorrecto` | Registro con aviso de privacidad (201), código incorrecto rechazado por la API (400 `CODIGO_VERIFICACION_INVALIDO`), código correcto leído de la consola de la API |
| `04_inicio`, `05_cerca_de_mi` | Catálogo con los 3 eventos de la API, sin campana; «Cerca de mí» ordenado por distancia con el GPS del emulador en Orizaba |
| `06_detalle`, `06b_dos_boletos`, `07_asientos` | Detalle con tipos de boleto del detalle de la API; 2 boletos; plano de 50 asientos |
| `08_pago`, `08b_pago_rechazado` | Pago solo con tarjeta (token de un solo uso); rechazo simulado devuelto como 201 `CANCELADA`/`RECHAZADO` y mostrado como error recuperable (orden #1) |
| `09_orden`, `10_boletos`, `11_qr` | Reintento con clave nueva aprobado (orden #2, $360.00), 2 boletos con asiento y QR propio |
| `12_mi_cuenta` | Perfil sin la fila de métodos de pago |
| `13_acceso_permitido`, `14_boleto_ya_utilizado` | Validador designado: `POST /api/validaciones` permite el boleto 1 y rechaza el segundo escaneo (`YA_UTILIZADO`) |
| `15_manifiesto`, `16_offline_permitido`, `16b_offline_repetido` | Manifiesto (1 vigente), red del emulador apagada, boleto 2 admitido sin conexión y rechazado al repetirlo en el mismo dispositivo |
| `17_sincronizacion` | Con red, `POST /api/validaciones/sincronizar` del lote (hecho con un boleto del taller): 2 enviadas, 1 permitida, 1 rechazada por conflicto; la bitácora local toma el veredicto del servidor registro por registro |
| `26_encuadre_antes`, `26b_encuadre_arrastrado` | Encuadre de la portada: la imagen elegida se arrastra en la vista previa 16:9 y el recorte guardado coincide con lo que se ve (el formulario avisa «Imagen lista…» y lista qué datos faltan para crear o guardar) |
| `27_encuadre_foto_enderezada`, `27b_encuadre_zoom_movido` | Una foto vertical de la cámara (EXIF orientación 6) se endereza al cargarla; con «Acercar» en 2.5× y arrastre hacia arriba la vista previa muestra la parte inferior, y la portada subida a Cloudinary (643×362) coincide con la vista previa. «Girar» rota 90° |
| `28_guia_inicio_bienvenida`, `28b_guia_inicio_filtros`, `28c_guia_nuevo_evento_ubicacion`, `28d_guia_organizador` | Guías interactivas de la primera vez, estilo mínimo: fondo oscuro, texto blanco corto y flecha curva al elemento iluminado (zona, filtros, pestaña Mis boletos, botón Nuevo evento); se avanza tocando la pantalla o «Siguiente». Se recuerdan por teléfono y se repiten desde Perfil → «Volver a ver las guías» |
| `29_validador_camara`, `29b_validador_puede_pasar`, `29c_validador_no_puede_pasar` | Validador rediseñado para la puerta (acceso de personal, barra solo con Escanear y Perfil): evento, chip de conexión y contadores arriba; la cámara ocupa la pantalla con linterna; el resultado llena la zona en verde «PUEDE PASAR» (se cierra solo a los 3 s) o rojo «NO PUEDE PASAR» con el asiento en grande; boleto real validado y luego rechazado por «ya utilizado» |
| `30_conexion_contorno`, `30b_conexion_pin`, `30c_conexion_logo_completo` | Animación de conexión al abrir la app (fondo azul muy claro): el contorno del pin se traza desde la punta, se cierra el anillo, se llena el disco y el boleto aparece con un rebote; al final entra «EnZona». Se repite mientras la API responde y dura al menos un ciclo |
| `31_login_logo_nuevo`, `31b_inicio_logo_nuevo`, `32_icono_app` | Logo v1.2 vectorial (del SVG del diseñador, `drawable/enzona_logo.xml`) en Login y en la cabecera de Inicio (54 dp), e ícono adaptable nuevo en el cajón de aplicaciones |
| `33_inicio_campana_boleto`, `33b_notificaciones_por_correo` | Cabecera de Inicio con el título más pequeño y la campana-boleto; con la API la pantalla de notificaciones explica que los avisos llegan por correo |
| `34_direccion_escrita`, `34b_direccion_del_mapa` | Formulario de evento: se escribe «Calle escrita a mano 123», se toca el mapa y el campo pasa a «Norte 12, 136, Centro» sin borrar nada a mano |
| `35_registro_sin_telefono`, `35b_verificacion_correo_real` | Registro contra la API con correo real (Resend): sin campo de teléfono y con el aviso del código; pantalla de verificación con la ayuda de no deseados y «Reenviar código (52 s)». La cuenta enzona.org@gmail.com se registró y la API envió el código por Resend sin errores en la bitácora |
| `22_portada_formulario`, `23_portada_elegida`, `24_inicio_portadas`, `25_detalle_portada` | Portada de evento: sección en el formulario, imagen elegida con el selector del sistema, subida a `POST /api/eventos/1/imagen` al guardar (almacén local de la API contra Aiven) y mostrada con Coil en el catálogo y el detalle |
| `21_admin_resumen`, `21b_admin_ingresos` | Administración contra la base de Aiven (19 de octubre): indicadores, cuentas por estado, eventos por estado, categoría y mes, ingresos por evento ($510.00) y ocupación |
| `18_mis_eventos`, `19_panel_evento`, `20_dialogo_cancelacion` | Organizador: ingresos y asistentes; panel armado con el manifiesto (2 emitidos, 2 validados, $360) y personal designado; diálogo de cancelación con reembolso del 100 % sin comisión |
