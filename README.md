# EnZona · App Android

App móvil de EnZona (Kotlin + Jetpack Compose) con los cuatro actores del
diagrama de casos de uso —**asistente**, **organizador**, **validador** y
**administrador**—, conectada a la API REST del equipo, **EnZona_Api**
(Spring Boot 3 + Java 21 + PostgreSQL, https://github.com/WilliamSosa895/EnZona_Api).

## v6 · Conexión con EnZona_Api (15 de octubre de 2026)

- **`RepositorioRemoto` habla el contrato de EnZona_Api**: rutas, cuerpos,
  cabeceras (`Idempotency-Key`, paginación) y errores RFC 9457 con `codigo`. La
  correspondencia completa está en `docs/notas_api_enzona.md`.
- **Registro** con aceptación del aviso de privacidad (LFPDPPP) y **verificación en
  dos pasos**: código del correo y, si se dio teléfono, código del teléfono.
- **Pago con tarjeta** mediante token de un solo uso de la pasarela (sandbox:
  aprobado o rechazado). Un pago rechazado renueva la clave de idempotencia para
  poder reintentar.
- **Validación sin conexión** contra el manifiesto de la API (código y firma) y
  sincronización del lote con conflictos y duplicados.
- **Resumen de administración (RF-18)**: pestaña «Resumen» con indicadores de la
  plataforma (usuarios, eventos, boletos emitidos, accesos validados, ocupación e
  ingresos) y gráficas de cuentas por estado, eventos por estado, categoría y mes,
  ingresos y ocupación por evento. Se calcula con las listas de administración y el
  reporte de cada evento de la API (`calcularEstadisticas`, función pura probada).
  Mockup en Figma: sección «Resumen de administración (RF-18)» del archivo
  «EnZona v3 · Mockups» (`docs/figma_v3/README.md`).
- **Portada de los eventos**: el organizador elige una imagen al crear o editar
  el evento, la endereza según su orientación EXIF, la encuadra en 16:9
  (arrastrar, acercar con dos dedos o el control deslizante, girar 90°) y la
  app recorta exactamente lo que se ve, la reduce (1600 px, JPEG) y la sube a
  `POST /api/eventos/{id}/imagen`. La API la guarda en su almacén (local en
  desarrollo o Cloudinary con `CLOUDINARY_URL`, siempre desde el servidor) y
  devuelve `imagenUrl`; las portadas se cargan con Coil en el catálogo y el
  detalle. Este endpoint vive en la rama `feature/imagenes-evento` propuesta a
  EnZona_Api (`docs/notas_api_enzona.md`).
- **Guías interactivas de la primera vez**: al entrar por primera vez a Inicio,
  el detalle de un evento, el plano de asientos, un boleto, y para el organizador
  en Mis eventos, el panel de cada evento y el formulario de nuevo evento,
  la app oscurece la pantalla, ilumina el control que explica y le apunta con
  una flecha dibujada a mano junto a un texto corto en blanco; se avanza tocando
  la pantalla (Omitir / Atrás / Siguiente abajo). Se recuerda por teléfono
  (`data/Guias.kt`, preferencias locales) y se puede repetir desde Perfil →
  «Volver a ver las guías». Componente reutilizable `ui/components/GuiaInteractiva.kt`:
  `AnfitrionDeGuias` envuelve la app, `Modifier.objetivoDeGuia("clave")` marca
  el objetivo y `GuiaDePantalla(clave, pasos)` declara el recorrido; el foco se
  trae a la vista si está fuera de pantalla.
- **Validador pensado para la puerta**: quien entra por el acceso de personal ve
  solo «Escanear» y «Perfil» (sin Inicio ni Mis boletos). La cámara ocupa la
  pantalla, con linterna; el resultado llena la zona en verde «PUEDE PASAR» (se
  cierra solo a los 3 s y vibra) o rojo «NO PUEDE PASAR» con el asiento en
  grande; arriba quedan el evento, el chip «Con internet / Sin conexión», el
  botón «Enviar N» de validaciones pendientes y los contadores de la jornada;
  «Escribir código» abre un diálogo y «Últimos accesos» se despliega.
- **Identidad visual v1.2**: ícono de la app nuevo (adaptable, fondo azul marino
  `#002746`, `mipmap-anydpi-v26`) y logo nuevo en Login e Inicio como vector
  (`drawable/enzona_logo.xml`, convertido del SVG del diseñador con
  `docs/figma_v3/svg_a_vector.py`: la máscara de muescas pasa a un path evenOdd,
  las líneas punteadas a trazos cortos y la inclinación del texto se aplica a las
  coordenadas; el lienzo se amplía a 1382 porque el viewBox original cortaba la «a»).
- **Animación de conexión**: al abrir la app, mientras se recupera la sesión con
  la API, el logo se arma por partes sobre azul muy claro (`AzulCielo`): se traza
  el contorno del pin, se llena el disco, el boleto aparece con un rebote y entra
  el nombre; el ciclo se repite mientras dure la espera y se deja terminar al
  menos una vez (`ui/components/AnimacionConexion.kt`, línea de tiempo pura
  `fasesConexion` con pruebas). El splash del sistema (Android 12+) usa el mismo
  azul para que el paso sea continuo.
- **Inicio**: título «Eventos cerca de ti» un punto más pequeño y la campana de
  notificaciones de vuelta, dibujada como boleto (`drawable/ic_campana_boleto.xml`:
  azul EnZona, muescas y línea de perforación). Con la API de EnZona no hay centro
  de notificaciones, así que la pantalla explica que los avisos llegan por correo.
- **Dirección del evento**: al tocar o arrastrar el marcador del mapa, la dirección
  de ese punto sustituye lo escrito en «Dirección» (antes había que borrar el campo
  a mano); al abrir un evento existente se respeta la dirección guardada, y si se
  edita a mano después aparece el chip «Usar la dirección del mapa».
- **Correo real con Resend**: el API envía por Resend (SMTP) el código de
  verificación, los boletos con su QR y los avisos de cambio o cancelación; la
  app no habla con Resend, pero se adaptó al correo real: el registro avisa que
  llegará un código y ya no pide teléfono (`Capacidades.verificacionPorTelefono`,
  false con la API porque no hay SMS y la cuenta quedaría pendiente), la pantalla
  de verificación cuenta 60 s antes de permitir «Reenviar código» (la API
  descarta en silencio los reenvíos más seguidos), separa el error de reenvío del
  de verificación y explica si la cuenta tiene un teléfono pendiente; el detalle
  del boleto y la ayuda indican revisar la carpeta de no deseados. Configuración
  del API en `docs/CORREO_RESEND.md` de EnZona_Api (rama `feature/correo-resend`).
- **`Capacidades`**: lo que la API no implementa (métodos de pago guardados,
  OXXO/SPEI, centro de notificaciones, comisión de cancelación, roles por
  administrador, «marcar como finalizado») se declara y la interfaz lo oculta.
- **Pruebas**: 13 de `RepositorioRemoto` contra MockWebServer con las respuestas
  que documenta la API; flujo completo probado en el emulador contra la API en
  la PC (`docs/capturas_api/`).
- El backend propio de v5 (`backend/`) se conserva como prototipo de referencia;
  la app ya no usa su contrato.

## La API (EnZona_Api)

Requisitos: JDK 21, Maven 3.9 y Docker (PostgreSQL 15). Se clona junto a este
repositorio, nunca dentro:

```
git clone https://github.com/WilliamSosa895/EnZona_Api.git
cd EnZona_Api
cp .env.example .env        # llena la base y genera cada clave con: openssl rand -base64 32
docker run -d --name enzona-api-db -e POSTGRES_DB=enzona -e POSTGRES_USER=enzona \
  -e POSTGRES_PASSWORD=<la misma de .env> -p 5435:5432 postgres:15-alpine
set -a; source .env; set +a  # DB_URL=jdbc:postgresql://localhost:5435/enzona
mvn -DskipTests package && java -jar target/enzona-api-0.1.0.jar
```

| Dirección | Contenido |
|---|---|
| `http://localhost:8080/api/categorias` | Catálogo público: responde cuando la API está lista |
| `http://localhost:8080/swagger-ui.html` | Documentación interactiva |

Con `NOTIFICATIONS_MODE=log` el código de verificación del registro aparece en la
consola de la API (la app no lo muestra: la API nunca lo devuelve). Con
`PAYMENT_PROVIDER=sandbox` el pago con tarjeta se simula.

**Base de Aiven:** la base compartida tiene hoy el esquema del prototipo
`backend/`; EnZona_Api espera el suyo (Flyway V1 a V5) y no arranca contra ella.
Hay que acordar en el equipo qué esquema se conserva antes de apuntar la API a
Aiven (detalle en `docs/notas_api_enzona.md`).

## v5 · Prototipo de backend propio (29 de septiembre de 2026)

- **Backend** en `backend/`: monolito modular por capas (auth, usuarios, eventos,
  ordenes, boletos, validacion, notificaciones, admin, common), 57 operaciones
  REST, JWT, errores RFC 7807, migraciones Flyway y OpenAPI.
- **Base real** en Aiven (PostgreSQL 18.6): las migraciones V2 a V7 aplicaron lo que
  faltaba sobre el esquema v2 y el sembrador cargó los datos de demostración.
- **App**: las pantallas hablan con `RepositorioEnZona`. Con `enzona.api.url` usan la
  API (`RepositorioRemoto`); sin ella, los datos de prueba (`RepositorioMock`) con la
  etiqueta «Datos de prueba». Estados visibles de cargando, error y reintento.
- **Simulaciones**, rotuladas como tales: pasarela de pago, correo y envío del código
  de verificación.
- **Pruebas**: 49 del backend (Testcontainers, con concurrencia real), 68 unitarias de
  Android y 7 de Compose.

Detalle en `docs/notas_v5_backend.md`; contrato en `docs/openapi_enzona_v5.json`;
capturas del flujo contra la base real en `docs/capturas_v5/`.

### El prototipo `backend/`

Sigue en el repositorio como referencia (JDK 21, `./mvnw spring-boot:run` desde
`backend/`, con su propio `backend/.env` no versionado). Desde v6 la app no habla
su contrato, así que no sirve para probar la app; su esquema es el que está hoy en
la base de Aiven.

## v4 · Modelo relacional, notificaciones y ajustes de interfaz (22 de septiembre de 2026)

- **Modelo alineado al diagrama ER**: `Rol` con `id` (tabla `rol`), vista `usuario_rol`,
  catálogo `Categoria` y `evento.categoria_id`, `usuario.contrasena_hash` (nunca la
  contraseña en claro), `telefono_verificado`, `fecha_registro`, `evento.fecha_creacion`
  y `validacion_acceso.boleto_id`. Pruebas `ModeloRelacionalTest` (CP-MODELO-01..05).
- **Notificaciones** (campana en Inicio con contador): recordatorio de eventos próximos
  con boleto, evento cancelado, compra confirmada y cambio de fecha. Pantalla con pestañas
  Todas / Próximos / Cancelados / Compras. Pruebas `NotificacionesTest` (CP-NOTIF-01..05).
- **Calendario**: corregido el campo de fecha que no abría el selector.
- **Inicio**: los filtros «Gratis» y «De pago» van juntos.
- **Boletos en horizontal**: los tipos de boleto del detalle y los N boletos de la orden se
  deslizan en un carrusel.

Nota técnica `docs/notas_v4_modelo_notificaciones.md`; migración
`docs/migracion_v4_incremental.sql` (no ejecutada); capturas en `docs/capturas_v4/`.

## v3 · Cancelación con regla de negocio, rol de organizador, mapa y fecha/hora (17 de septiembre de 2026)

- **Cancelar evento (RF-14).** Solo antes del inicio. Gratuito: se anulan las confirmaciones y
  se avisa a los asistentes. De pago con ventas: el organizador acepta una **comisión del 10 %**
  sobre lo cobrado («Cancelar y aceptar comisión»); los asistentes reciben el **100 %** de
  reembolso y la comisión queda como cargo pendiente al organizador. Sin ventas: comisión 0.
  Iniciado o realizado: no se cancela, solo se marca como finalizado. El administrador cancela
  por moderación con motivo obligatorio y sin comisión. La regla vive en
  `data/model/Cancelacion.kt` (`politicaCancelacion`) y la aplica `MockRepository.cancelarEvento`.
  El asistente ve «Evento cancelado · reembolso en proceso» y el validador rechaza el QR con
  «Evento cancelado».
- **Rol de organizador.** «Convertirme en organizador» pide confirmación en un diálogo y muestra
  un Snackbar; en Perfil existe «Dejar de ser organizador» (bloqueado si hay eventos publicados
  con boletos vigentes, si es el único rol o si la cuenta es administradora).
- **Ubicación en mapa.** El formulario fija la ubicación exacta con OpenStreetMap (osmdroid, sin
  clave de API); publicar exige coordenadas, un borrador no. El detalle muestra una miniatura y
  «Cómo llegar» (intent `geo:`). Los eventos de muestra traen coordenadas de Orizaba.
- **Fecha y hora.** Calendario Material 3 (sin fechas pasadas) y hora en 24 h con entrada
  numérica; `Evento.fecha` es `LocalDateTime` (zona America/Mexico_City), la API usa ISO-8601 con
  zona y todo se muestra con `FechaEvento` («Vie 25 Sep · 19:00»).

Detalles, contratos REST pendientes, migración `docs/migracion_v3_incremental.sql` (no
ejecutada) y pruebas `CP-CANCEL-*`, `CP-ROL-*`, `CP-UBIC-*`, `CP-FECHA-*`, `CP-HORA-*` en
`docs/notas_v3_cancelacion_rol_mapa_fecha.md`; capturas en `docs/capturas_v3/`. Pruebas de
Compose: `gradlew.bat connectedDebugAndroidTest` con un emulador encendido.

## v2 · Compra múltiple y paleta de la plataforma (13 de septiembre de 2026)

Un asistente puede comprar 1, 2, 3 o más boletos en una sola orden (hasta la disponibilidad
real), elegir sus asientos en el plano y recibir un QR por boleto ("Boleto k de N"). La causa
del límite anterior, los archivos modificados, el contrato de API propuesto, la migración
incremental (`docs/migracion_v2_incremental.sql`, no ejecutada) y las pruebas `CP-MULTI-*`
están en `docs/notas_v2_compra_multiple.md`; las capturas en `docs/capturas_v2/`. La app usa el
tema claro de `docs/WhatsApp Image 2026-09-11 at 11.25.23.jpeg` (azules de marca y coral) y el
área de estudio es Orizaba.

**No ejecutes `docs/esquema_enzona_v2.sql` contra una base con datos**: es un script de
reconstrucción con `DROP TABLE … CASCADE`.

## Rediseño de la app (etapas 1 y 2)

El tema, los componentes compartidos y el flujo del asistente (Inicio, filtros, detalle,
asientos, confirmación gratuita, pago, Mis boletos y boleto QR) se rediseñaron en
septiembre de 2026. Las decisiones, las diferencias de negocio registradas y la evidencia
de verificación están en `docs/notas_rediseno_etapas_1_2.md`; las capturas del emulador en
`docs/capturas/`. Login, registro, OTP, perfil, organizador, validador y administración
conservan sus pantallas originales sobre el nuevo tema.

Para compilar desde la terminal en Windows hace falta JDK 17:

```
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat assembleDebug testDebugUnitTest
```

## Cómo abrirla en Android Studio

1. Android Studio → **Open** → selecciona la carpeta `EnZona`.
2. Espera el *Gradle Sync* (la primera vez descarga CameraX y ML Kit).
3. Ejecuta con **Run ▶** en un emulador o en tu teléfono.

## Cuentas de prueba

Contraseña para todas: **enzona123**

| Correo | Rol | Qué puede hacer |
|---|---|---|
| `asistente@uv.mx` | Asistente | Descubrir eventos, comprar varios boletos/confirmar, ver sus boletos QR |
| `organizador@uv.mx` | Organizador | Crear, publicar, editar y cancelar eventos; ver reportes |
| `validador@uv.mx` | Validador | Escanear los QR en la puerta |
| `admin@uv.mx` | Administrador | Gestionar usuarios, roles y eventos |

El **acceso del personal** (organizador, validador, admin) está separado del de
asistentes: en la pantalla de login, la opción *«Acceso para organizadores y
personal»*. La barra inferior se arma según los roles de cada cuenta.

Como un mismo usuario puede tener varios roles, el destino tras iniciar sesión
depende del acceso que se usó: por el acceso normal se entra a *Inicio*, y por
el acceso del personal al panel que corresponde al rol.

## Cualquier usuario puede crear eventos

El documento de visión (sección 6) dice que *«un mismo usuario puede tener a la
vez el rol de asistente y de organizador»*. Por eso el alta como organizador es
**autogestionada**: en *Perfil* aparece la tarjeta «¿Quieres publicar tus
propios eventos?» con el botón **Convertirme en organizador**. Al pulsarlo se
agrega el rol (fila en `usuario_rol`), la pestaña *Mis eventos* aparece al
instante y la cuenta conserva su rol de asistente. No hace falta que el
administrador intervenga.

## El organizador designa personal de acceso

También de la sección 6: el organizador *«designa personal de acceso»*. En el
panel de cada evento hay un apartado **Personal de acceso** donde se escribe el
correo de una cuenta ya registrada y se le concede el rol de validador, con la
lista de designados y la opción de quitarlos. Se apoya en la misma relación
`usuario_rol` del esquema, sin inventar tablas nuevas.

## Cobertura de los casos de uso

| Caso de uso | Requisito | Dónde está |
|---|---|---|
| Registrarse | RF-01, RF-02 | `auth/RegisterScreen` + `auth/OtpScreen` |
| Iniciar sesión | RF-03 | `auth/LoginScreen` (normal y de personal) |
| Gestión de perfil | RF-04 | `ProfileScreen` |
| Buscar y filtrar eventos | RF-05/06/07 | `HomeScreen` |
| Confirmar asistencia | RF-10 | `EventDetailScreen` |
| Comprar boleto | RF-11 | `CheckoutScreen` |
| Selección de asiento | RF-09 | `SeatMapScreen` |
| Recibir boleto QR | RF-12 | `TicketDetailScreen` (+ envío por correo) |
| Crear y publicar evento | RF-08 | `organizer/EventFormScreen` |
| Modificar o cancelar evento | RF-13, RF-14 | `organizer/EventFormScreen`, `EventStatsScreen` |
| Gestionar boletos y aforo | RF-16 | `MockRepository.comprarOConfirmar` |
| Consultar panel y reportes | RF-17 | `organizer/EventStatsScreen` |
| Validar acceso (QR) | RF-15, RNF-08 | `validator/ScannerScreen` |
| Gestionar usuarios y eventos | RF-18 | `admin/AdminScreen` |

Casos de prueba cubiertos por la app: CP-01 a CP-16, salvo CP-09/CP-10 (pago
real con pasarela) y CP-11 (concurrencia, que debe probarse contra el backend).

## El QR y la firma

El contenido del QR es `codigo.firma`, con la firma HMAC-SHA256 del código
(RNF-02). El validador **verifica la firma antes de consultar la base**, así que
un QR inventado se rechaza aunque no haya conexión.

Con la API, **la clave HMAC vive solo en el backend**: el servidor firma al emitir
y verifica al validar. Con datos de prueba la firma se calcula en el teléfono para
que el flujo sea completo. El envío del boleto por correo (RF-12) está detrás de
una interfaz cuya implementación actual solo escribe en el log; el botón "Enviar
con mi app de correo" abre la app de correo del teléfono.

## Modo sin conexión del validador (RNF-08, CP-14)

El validador elige el evento y activa *«Modo sin conexión»* mientras todavía tiene
red: la app descarga el manifiesto del evento, con la huella SHA-256 de cada
boleto. Sin red, cada escaneo se comprueba por pertenencia al manifiesto y queda
marcado como *sin sincronizar*. Al recuperar la conexión, **Sincronizar ahora**
envía el lote y el servidor aplica la regla de un solo uso e informa cuáles
resultaron duplicadas.

Sin conexión no se verifica la firma, porque la clave HMAC nunca sale del
servidor.

## Cómo probar el escaneo en el emulador

La cámara del emulador no puede leer un QR de otra ventana. Usa
**«Capturar código manualmente»** en la pantalla del validador y pega el
contenido del QR (aparece en el correo que genera el botón "Enviar a mi correo",
o cámbialo por el código visible bajo el QR más su firma).

Para la prueba real: abre el boleto en un teléfono y escanéalo con otro.

## Conectar la app con la API

La app elige de dónde salen los datos con `enzona.api.url` en `local.properties`
(archivo no versionado):

```
enzona.api.url=http://10.0.2.2:8080/
```

| Valor | Resultado |
|---|---|
| `http://10.0.2.2:8080/` | Emulador: `10.0.2.2` es la PC donde corre EnZona_Api |
| `http://<IP de la PC>:8080/` | Teléfono físico en la misma red local (abre el puerto 8080 en el firewall) |
| vacío | Datos de prueba en memoria, con la etiqueta «Datos de prueba» |

Después de cambiarla hay que recompilar. El tráfico HTTP en claro solo se permite
en la compilación `debug`; `release` exige HTTPS.

La API firma los boletos, calcula precios y totales, descuenta el inventario de
forma atómica (RNF-05) y decide si un boleto da acceso. La app propone y el
servidor dispone. Lo que la API todavía no implementa se declara en
`Capacidades` y la app no lo ofrece (lista en `docs/notas_api_enzona.md`).

## Estructura

```
app/src/main/java/com/uv/enzona/
├── MainActivity.kt              # Navegación y barra inferior por rol
├── data/
│   ├── model/Models.kt          # Usuario, Rol, Evento, Boleto, ValidacionAcceso
│   ├── MockRepository.kt        # Datos de prueba y toda la lógica de negocio
│   ├── SessionManager.kt        # Sesión y rol activo
│   └── remote/ApiService.kt     # Retrofit listo para el backend
├── ui/
│   ├── theme/                   # Paleta EnZona
│   ├── components/Comunes.kt    # Campos y botones de la marca
│   └── screens/
│       ├── auth/                # Login (normal y personal), registro, OTP
│       ├── organizer/           # Panel, alta/edición y reportes de eventos
│       ├── validator/           # Escáner QR + cámara
│       ├── admin/               # Usuarios y eventos
│       └── …                    # Home, detalle, boletos, boleto QR, perfil
└── util/                        # Generación de QR y validaciones (CURP…)
```

## Asientos numerados (RF-09)

El interruptor **«¿Este evento requiere asientos?»** está en dos lugares:

- **Organizador**: al crear o editar el evento, junto con el número de filas y
  de asientos por fila. El aforo se calcula solo (filas × asientos por fila).
- **Administrador**: en la pestaña *Eventos*, un interruptor por evento. Al
  activarlo genera el mapa en filas de 12 butacas a partir del aforo actual.

En ambos casos el interruptor se bloquea cuando el evento ya tiene boletos
emitidos, porque cambiar el mapa dejaría boletos apuntando a butacas
inexistentes.

Cuando el evento requiere asientos, el asistente ve el **mapa del recinto**:
escenario, secciones, filas con curvatura y pasillo central, con las butacas
ocupadas en gris con «×» (no seleccionables), las libres con borde azul y su
número, y las elegidas en coral con una palomita. Se eligen tantos asientos como
boletos («Seleccionados X de N»); hay zoom, leyenda y selección por sección,
fila y número.

Eventos de demostración con asientos: *Concierto: Orquesta Universitaria*
(Planta baja 10×18 + Balcón 5×14) y *Obra: La Casa de Bernarda Alba*
(Luneta 8×16). Traen butacas ocupadas de muestra para que el mapa se vea real.

## Pago y métodos guardados (RF-11)

Los eventos de pago pasan por la pantalla de **Pago**, que muestra el resumen
de la orden (tipo, cantidad y asientos), el desglose por unidad y el total
final, y la selección del método (tarjetas guardadas, OXXO o SPEI). Una compra
aprobada crea una orden, un pago y N boletos con QR propio; si se rechaza, no se
emite nada ni se descuenta inventario. El total es el final, sin cargos añadidos
después ni tarifas por demanda (RNF-09).

Esa pantalla incluye un interruptor **«Simular pago rechazado»** que reproduce
el caso de prueba CP-10: la orden queda cancelada, el pago se registra como
rechazado y no se emite boleto ni se descuenta el aforo.

En *Mi cuenta → Métodos de pago guardados* se pueden agregar y borrar tarjetas y
elegir la predeterminada. **De la tarjeta solo se guardan la marca, los últimos
cuatro dígitos y el token de la pasarela**, nunca el número ni el CVV, porque el
Documento de Visión y Alcance establece que la plataforma no almacena datos de
tarjetas. El archivo `esquema_metodo_pago.sql` trae la tabla `metodo_pago` lista
para añadirse al esquema, con esa restricción reflejada en el propio DDL.

## Mi cuenta

El perfil está organizado como pantalla de ajustes, con tres bloques:

- **Ajustes de localización**: ciudad, país e interruptor de contenido según
  ubicación (el enfoque hiperlocal del proyecto).
- **Preferencias**: editar mis datos, métodos de pago guardados y seguridad
  (cambio de contraseña y estado de verificación de la cuenta).
- **Ayuda**: preguntas frecuentes y contacto, aviso de privacidad con los
  derechos ARCO (LFPDPPP) y apartado legal con la política de reembolsos y la
  transparencia de precios.

## Fidelidad al modelo relacional

Las clases de `data/model/Models.kt` reproducen las tablas del diagrama ER
(`esquema_enzona_v2.sql`): `usuario` (con `contrasena_hash`, `telefono_verificado`,
`fecha_registro`), `rol` (enum con `id`), `usuario_rol` (vista `usuarioRoles()`),
`categoria` (catálogo `MockRepository.categorias`, FK `evento.categoriaId`), `evento`,
`tipo_boleto`, `asiento`, `orden`, `pago`, `boleto` y `validacion_acceso` (FK `boletoId`).

Una compra crea la cadena completa **orden → pago → boleto**, y el boleto
referencia `tipo_boleto_id` y `asiento_id` como en el esquema. Las pantallas
consumen `BoletoDetalle`, que es la consulta con JOIN ya resuelta (no es una
tabla). Cancelar un evento (según la regla de RF-14 v3) anula los boletos,
libera los asientos, pasa las órdenes a REEMBOLSADA y los pagos a REEMBOLSADO,
y registra fecha, autor, motivo y comisión en `evento`.

## Qué falta respecto al sistema completo

- Gestión de categorías y de eventos reportados por el administrador
  (el documento las menciona en la sección 6; hoy las categorías son una
  lista fija en el formulario y no hay reporte de eventos).
- Pagos reales con pasarela y reembolsos: la pasarela del backend es una simulación.
- Proveedor de correo y de SMS: el código de verificación y los correos son simulados.
- La web React, que consumirá el mismo contrato. CP-20 no se puede cerrar sin ella.
- Persistencia local (Room) para consultar boletos sin red.
