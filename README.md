# EnZona · App Android (Kotlin + Jetpack Compose)

App móvil de EnZona con los cuatro actores del diagrama de casos de uso:
**asistente**, **organizador**, **validador** y **administrador**.

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

En la demo la firma se calcula en el teléfono para que el flujo sea completo;
**en producción la clave HMAC vive solo en el backend**, que además envía el
boleto por correo al emitirlo (RF-12). El botón "Enviar a mi correo" de la app
es un sustituto temporal mientras no existe ese servicio.

## Modo sin conexión del validador (RNF-08, CP-14)

En la pantalla del validador hay un interruptor *«Modo sin conexión»*. Con él
activo, las validaciones se registran localmente marcadas como *sin sincronizar*
y aparece el botón **Sincronizar ahora** al recuperar la conexión. La lógica de
un solo uso funciona igual en ambos modos.

## Cómo probar el escaneo en el emulador

La cámara del emulador no puede leer un QR de otra ventana. Usa
**«Capturar código manualmente»** en la pantalla del validador y pega el
contenido del QR (aparece en el correo que genera el botón "Enviar a mi correo",
o cámbialo por el código visible bajo el QR más su firma).

Para la prueba real: abre el boleto en un teléfono y escanéalo con otro.

## Conectar el backend cuando esté listo

`data/remote/ApiService.kt` ya define el contrato Retrofit.

1. Levanta el Spring Boot en `http://localhost:8080`.
2. En el emulador, `http://10.0.2.2:8080/` apunta a tu máquina (ya configurado).
3. Crea un `RemoteRepository` con las mismas funciones que `MockRepository` y
   cámbialo en las pantallas.
4. El backend es quien firma los boletos, envía los correos y descuenta el aforo
   con una transacción atómica (RNF-05).

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

Las clases de `data/model/Models.kt` reproducen las tablas de
`esquema_enzona.sql`: `usuario`, `rol`, `evento`, `categoria`, `tipo_boleto`,
`asiento`, `orden`, `pago`, `boleto` y `validacion_acceso`.

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
- Pagos reales con pasarela y reembolsos.
- Persistencia local (Room) y token JWT en DataStore: hoy todo vive en memoria,
  así que al cerrar la app se reinician los datos de prueba.
