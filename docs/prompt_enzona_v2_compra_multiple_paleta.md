# Revisión de EnZona v2 y corrección de compra múltiple

Trabaja sobre el proyecto EnZona existente. Revisa mis cambios, conserva lo que esté bien, corrige la compra de varios boletos y aplica la paleta de la última imagen. Usa la skill `frontend-design` para el trabajo visual e implementa Android con Kotlin, Jetpack Compose y Material 3.

Prioridad: reproducir y corregir la compra de varios boletos de extremo a extremo; después aplicar la paleta y comprobar regresiones. No termines únicamente con un diagnóstico. Puedes modificar el estado, la navegación, repositorios, modelos de presentación y contratos necesarios para resolver el fallo; esto actualiza la restricción anterior de hacer cambios exclusivamente visuales.

## Fuentes y alcance vigente

Busca y lee estos archivos en `docs/` o en las carpetas de documentación accesibles:

- `DOC_MAESTRO_UV (1).docx`.
- `requisitos_y_pruebas_v2.docx` y sus diagramas incrustados.
- `esquema_enzona_v2.sql`.
- `modelo_relacional_v2.png`.
- `WhatsApp Image 2026-09-11 at 11.25.23.jpeg`, cuya cabecera dice “EnZona — Paleta de color de la plataforma”.
- Notas del rediseño, README, logos y contratos reales del repositorio.

Estas versiones actualizan los documentos anteriores: EnZona ahora incluye web React + Vite y Android Kotlin + Compose, con API REST Spring Boot y PostgreSQL compartidos. Orizaba, Veracruz, es el área de estudio. Android ya no está fuera del alcance documentado.

El requisito RF-19 incorpora ubicación; RNF-10 exige datos y servicio compartidos y RNF-11 contempla consentimiento y selección manual de ciudad. RNF-07 establece Android 8.0 o superior. CP-17 a CP-20 cubren ubicación, permiso denegado, operación Android y paridad web/móvil.

La tarea principal se realiza en el proyecto Android. Inspecciona backend y web si forman parte del repositorio accesible y adapta solo lo necesario para mantener el contrato compartido. Si no están disponibles, deja claramente identificados los cambios de API pendientes, implementa la corrección en el entorno disponible y no declares integración real.

## Revisión inicial con evidencia

Lee las instrucciones del repositorio, revisa Git y conserva cambios previos del usuario. Compara el código actual con el estado anterior disponible mediante el historial y las notas; no inventes una comparación si no existe una referencia.

Comprueba las versiones de Gradle, AGP, Kotlin, Compose, JDK y `minSdk`. No actualices todo el stack ni cambies el motor de base de datos como efecto secundario de esta tarea.

Entrega una tabla breve de hallazgos con: requisito, evidencia en archivo/función, estado y acción. Usa estados como correcto, incompleto, incompatible y no verificado. Separa los problemas comprobados de hipótesis y propuestas.

## Fallo prioritario reportado

Actualmente, al intentar comprar 2 o 3 boletos y seleccionar sus asientos, solo puedo seleccionar uno. Quiero elegir una cantidad y completar la compra de todos esos boletos en la misma orden.

El SQL aportado permite varias filas de `boleto` con el mismo `orden_id`: esa columna no es única. Tampoco declara una restricción única por asistente y evento. Por tanto, el esquema no impone una compra de un solo boleto; verifica si la limitación aparece en UI, navegación, repositorio, API o alguna migración posterior.

Reproduce el fallo antes de modificarlo. Sigue el flujo detalle → cantidad/tipo → asientos → checkout → compra → confirmación → Mis boletos. Busca, sin dar por hecho que existan:

- Un estado escalar `asientoSeleccionado` o `selectedSeatId` que se sustituye al pulsar otra butaca.
- Una cantidad fija en 1, un `max` incorrecto o un selector sin conexión con el estado de compra.
- Rutas y callbacks que transportan un solo asiento o descartan la cantidad.
- Solicitudes que aceptan únicamente `asiento_id`, y respuestas que devuelven un boleto en vez del conjunto.
- Uso de `first()`, `firstOrNull()`, índice cero o filtros que conservan solo un boleto por evento.
- Una regla “ya tienes boleto” que bloquea cualquier compra adicional sin una política documentada.
- Mutación de listas no observables que no produce recomposición en Compose.
- Precio, aforo o stock actualizados en 1 aunque la UI muestre otra cantidad.

Identifica y explica la causa real con archivos y funciones concretos. La hipótesis de selección escalar no sustituye a comprobar el código.

## Comportamiento que debes implementar

### Cantidad y selección

1. Permite elegir 1, 2, 3 o más boletos hasta la disponibilidad y los límites reales del evento. No conviertas 3 en un máximo arbitrario ni inventes límites comerciales.
2. El alcance mínimo es comprar varios boletos de un mismo tipo y evento. Si el proyecto ya admite varios tipos en una orden, conserva esa agrupación y calcula cantidades e importes por tipo. No hace falta añadir un carrito de varios eventos.
3. Para eventos sin asiento, la cantidad se conserva hasta emitir exactamente N boletos.
4. Para eventos con asiento, permite seleccionar N identificadores distintos. Pulsar una butaca seleccionada debe permitir quitarla; pulsar otra disponible debe añadirla, sin reemplazar las anteriores.
5. Muestra “Seleccionados X de N”, la lista de localidades y el total actualizado. No permite continuar con menos o más asientos que la cantidad elegida.
6. Conserva zoom, selección accesible por sección/fila/número y leyenda. Indica seleccionado, ocupado y disponible mediante texto/semántica e indicadores además de color.
7. Si cambia la cantidad, el tipo o el evento, revalida las selecciones. Si sobran asientos o una localidad deja de corresponder al tipo, explica qué debe corregir el usuario; no cobres por selecciones ocultas ni elijas reemplazos silenciosos.
8. Cada asiento debe pertenecer al evento de la orden y a una sección compatible con la tarifa. El SQL no define esa asociación entre `tipo_boleto` y sección; inspecciona si el código dispone de otra fuente válida. Si falta, registra el contrato que se necesita y no deduzcas permisos por comparar nombres como “Balcón”.
9. Permite una compra adicional de pago si existe cupo y ninguna política real lo impide. Mantén el límite por usuario únicamente si está definido y es compatible con comprar para varias personas.
10. Conserva la política existente de confirmación gratuita por usuario, salvo que ya admita acompañantes. La nueva compra múltiple no implica por sí sola cambiar esa regla; si un evento gratuito admite N confirmaciones, debe emitir N boletos y aplicar la misma integridad.

### Estado, navegación y API

Usa una fuente coherente del estado de compra, con evento, tipo/cantidades y conjunto o lista sin duplicados de asientos. Utiliza estado observable o colecciones inmutables actualizadas correctamente. Conserva la selección al volver desde pago o detalle y al recrear la pantalla dentro de las garantías del proyecto.

Las rutas, callbacks y solicitudes deben transportar la compra completa; el resultado debe permitir mostrar todos los boletos emitidos. Un DTO o estado de pantalla no obliga a crear una tabla.

Mantén compatibles los contratos web y móvil. Revisa los consumidores antes de modificar un endpoint. Si hace falta evolucionarlo, documenta la solicitud, respuesta, errores y compatibilidad, incluyendo cómo se representa una compra de cantidad 1.

Consulta la [documentación de estado en Compose](https://developer.android.com/develop/ui/compose/state) cuando necesites comprobar el comportamiento de colecciones y recomposición.

### Compra, cobro e inventario

- Una compra aprobada produce una orden, el cobro aprobado correspondiente al total y N boletos individuales asociados a esa orden. Los reintentos de pago pueden generar registros propios; no impongas una única fila de `pago` por orden si eso rompe la trazabilidad.
- Cada boleto tiene código y QR propios y, si corresponde, un asiento diferente. El comprador puede conservarlos en su cuenta para sus acompañantes; no introduzcas reventa, transferencia ni registro obligatorio de cada acompañante.
- Calcula importes con decimales exactos y moneda MXN; en servidor deriva precios desde datos autorizados, sin confiar en el total enviado por el teléfono. El precio confirmado de la orden no debe cambiar al editar después un tipo de boleto.
- Reduce el cupo disponible y el stock de tipos por N, y ocupa los N asientos. `aforo` es la capacidad máxima del evento: no reduzcas ese campo como si fuera el saldo disponible. Calcula ocupación y disponibilidad con una fuente consistente.
- La operación de inventario y emisión debe ser completa o no emitir ninguno. No llames N veces a la compra antigua como operaciones independientes: eso permitiría cobros o compras parciales.
- Si solo quedan 2 lugares y se solicitan 3, informa del límite antes de pagar y vuelve a comprobarlo en el backend. Valida cantidades positivas, asientos únicos, pertenencia al evento y disponibilidad de todos los elementos.
- Evita duplicaciones por doble toque, recomposición, reintento o notificación repetida de la pasarela. Usa identidad persistente de la operación y procesamiento idempotente en servidor cuando esté disponible; deshabilitar el botón no basta.
- Ante fallo de pago o conflicto de inventario, no dejes boletos parciales ni descuentos definitivos de cupo. Revierte o libera las reservas pertenecientes a la operación según el flujo real.
- Una transacción SQL no vuelve atómico el cobro externo. Reutiliza o define una estrategia de reserva/confirmación y compensación; no mantengas bloqueos de base de datos durante una llamada de red. Si el proveedor confirma un cobro que no puede cumplirse, registra el incidente y el reembolso/compensación pendiente, sin simular que nunca hubo un cargo.
- En OXXO/SPEI, generar una referencia no equivale a pago aprobado. Diferencia pendiente, aprobado y rechazado, y emite después de confirmación. Mantén las simulaciones visibles como tales.
- Si se guardan pagos pendientes, persiste la cantidad, precios confirmados y localidades requeridas en un detalle de orden/reserva adecuado antes de cobrar. El esquema actual no tiene líneas de orden. No dependas del carrito en memoria del teléfono para resolver una notificación posterior; propone una migración incremental si no existe ya un mecanismo equivalente.

### Confirmación y acceso

Muestra un resumen con N boletos y acceso a cada QR, por ejemplo “Boleto 1 de 3”, con navegación clara. Mis boletos puede agruparlos por orden/evento, pero debe permitir consultar cada uno por separado.

La validación consume un boleto, no toda la orden. Al escanear uno de los tres, los otros dos deben seguir válidos. El correo o comprobante debe incluir o enlazar todos los boletos cuando el servicio exista. No muestres un mensaje de envío real si solo se abre un cliente de correo.

## Revisión del SQL y consistencia documental

Estos son hallazgos del archivo aportado, no pruebas de que el backend actual carezca de soluciones adicionales. Comprueba migraciones y servicios antes de actuar:

| Hallazgo | Qué debes comprobar o corregir |
| --- | --- |
| `esquema_enzona_v2.sql` ejecuta `DROP TABLE ... CASCADE` y recrea tipos/tablas | Es un script de reconstrucción, no una migración segura. No lo ejecutes contra datos existentes. Prepara migraciones incrementales y verifica en una base de prueba aislada. |
| `boleto.orden_id` no es único | Mantener una orden con N boletos; no añadir un límite artificial de un boleto por orden. |
| `boleto.asiento_id` no tiene unicidad activa | Comprobar protección transaccional y una restricción de base que impida dos boletos vigentes/usados para la misma localidad. Conservar historial de cancelados y permitir reemisión según política; una unicidad global ingenua puede bloquearla. |
| Las claves foráneas de orden, tipo y asiento no comprueban que pertenezcan al mismo evento | Validarlo en servidor y estudiar restricciones compatibles. No confiar solo en filtros de la UI. |
| Stock de tipos y aforo tienen comprobaciones individuales | No demuestran integridad entre tablas ni concurrencia. Comprobar validación por lote, consistencia de cupo y bloqueos/actualizaciones condicionales. |
| `RESERVADO` es solo un estado en `asiento` | No representa por sí mismo propietario ni expiración de reserva. Verificar cómo se identifican, liberan y recuperan los apartados. |
| `estado_pago` solo incluye APROBADO, RECHAZADO y REEMBOLSADO | Revisar estados pendientes y compensaciones del proveedor; preparar evolución compatible cuando sea necesaria. |
| Ciudad y coordenadas ya están en `evento` | Actualizar modelos/DTO/formularios que sigan diciendo que faltan. Las fechas del SQL son `TIMESTAMPTZ`; comprobar si los clientes todavía usan texto libre. |
| Latitud y longitud tienen rangos, pero pueden ser nulas independientemente | Aceptar ambas ausentes o ambas válidas. No convertir una coordenada ausente en cero ni mostrar distancias inventadas. |
| `usuario_rol` es global y no enlaza al validador con el evento | No afirmar autorización por evento a partir de ese rol; revisar la asignación real de personal. |
| `usuario.curp` admite nulos y su tipo es `VARCHAR(255)` | RF-01 exige CURP en el registro y RNF-03 pide cifrado. Verificar validación y protección reales; un tipo de columna no demuestra cifrado. Mantener contraseñas con hash, sin convertirlas a cifrado reversible. |
| `metodo_pago`, imagen de portada y vínculo tarifa/sección no aparecen en el SQL adjunto | Comprobar extensiones existentes y documentarlas; no eliminarlas ni fingir que están representadas en el diagrama. |

Una posible protección de localidades es un índice único parcial adaptado a los estados que retienen su ocupación. Inspecciona duplicados existentes y la política de cancelación antes de elegirlo; no incluyas una solución que impida almacenar el historial. Consulta [índices parciales de PostgreSQL](https://www.postgresql.org/docs/15/indexes-partial.html).

En la documentación, el caso de compra y las pruebas CP-09/CP-10 siguen describiendo un boleto individual: añade criterios explícitos de compra múltiple como ampliación de RF-09, RF-11, RF-12 y RF-16, conservando los identificadores existentes. No presentes esa ampliación como si ya estuviera escrita en la v2.

El maestro conserva campos “Integrante 1…” y recomendaciones de plantilla que dicen borrar al entregar; anótalos como limpieza editorial pendiente. El antiguo documento de visión debe actualizarse al alcance multiplataforma si sigue siendo una referencia vigente. No certifiques cumplimiento legal por repetir lo que afirman los documentos.

La cabecera del SQL declara PostgreSQL 11 y la bibliografía hace referencia a PostgreSQL 15. Identifica la versión desplegada y alinea la documentación; esto no autoriza una actualización de servidor como parte del arreglo. Si preparas una migración desde este archivo, recuerda que `\echo` es un metacomando de psql y no una sentencia SQL para ejecutar mediante JDBC.

## Ubicación y compatibilidad

Comprueba RF-19 y RNF-11: consentimiento antes de usar ubicación, alternativa manual de Orizaba, permiso rechazado, ubicación aproximada o no disponible, y eventos sin coordenadas. No almacenes un historial de ubicaciones si no es necesario para el alcance.

Filtrar por ciudad no demuestra ordenar por cercanía. Para CP-17 debe haber un cálculo real de distancia o una consulta equivalente. El índice `(latitud, longitud)` por sí solo no realiza esa operación. Define unidades y criterio de cercanía; no inventes cobertura o distancias. Estar fuera del área de estudio debe producir información clara y permitir explorar Orizaba manualmente.

Verifica compatibilidad con Android 8.0 mediante configuración y APIs compatibles; una ejecución en un emulador reciente no prueba por sí sola ese requisito. CP-20 requiere ambos clientes y el mismo backend: dos mocks parecidos no demuestran paridad.

## Paleta de la última imagen

Esta es la referencia cromática vigente y sustituye las instrucciones anteriores de conservar únicamente modo oscuro o excluir el coral. Implementa un tema claro principal con fondo gris muy claro, superficies blancas, azules de marca y coral para las acciones destacadas, como muestra la imagen. Si ya existe un modo oscuro, puede conservarse como alternativa coherente, pero no debe impedir comprobar el aspecto claro solicitado. Desactiva el color dinámico cuando sustituya esta identidad.

| Rol de la referencia | Hexadecimal | Aplicación |
| --- | --- | --- |
| Azul marino | #1F3A5F | Encabezados, navegación y botones principales generales. |
| Azul EnZona | #2E86AB | Acentos, iconos y elementos seleccionados, con contraste verificado. |
| Azul profundo | #24708F | Controles azules con texto blanco pequeño; enlaces sobre blanco. |
| Degradado de marca | #274C78 → #2E86AB | Logo o cabecera destacada, con uso moderado. |
| Coral | #F2643B | Acción destacada de comprar/publicar, con texto tinta. |
| Coral oscuro | #D64E2A | Estado presionado o borde; comprobar cada pareja de texto. |
| Coral suave | #FDE9E2 | Contenedores y resaltados suaves. |
| Tinta | #1B2432 | Texto principal y texto sobre el coral principal. |
| Gris texto | #5D6D7E | Texto secundario sobre superficies claras. |
| Gris suave | #9AA6B6 | Decoración y estados deshabilitados; no texto necesario sobre blanco. |
| Línea/borde | #D9DFEA | Separadores discretos; no única señal de un control si falta contraste. |
| Fondo | #EEF1F6 | Fondo general claro. |
| Superficie | #FFFFFF | Tarjetas, campos y hojas. |
| Éxito | #2E9E5B | Indicadores positivos acompañados de icono y texto. |
| Fondo de éxito / texto | #E5F3EC / #1E7A45 | Etiquetas de boleto válido o acceso permitido. |
| Advertencia | #F1B434 | Pendiente/advertencia, con texto azul marino. |
| Error | #C0392B | Mensajes o contenedores de error con blanco. |
| Información | #2E86AB | Tono informativo con pareja accesible. |

Usa los hexadecimales impresos en la imagen, no valores aproximados tomados de la compresión JPEG. Centraliza tokens y roles en el tema de Compose; conserva componentes, navegación por rol y mejoras de accesibilidad. Revisa colores escritos directamente en todas las pantallas, barras de sistema, campos, diálogos, chips, asientos y paneles.

### Correcciones de contraste sin cambiar la identidad

La imagen contiene etiquetas de contraste que no siempre justifican el texto pequeño de sus ejemplos. Los siguientes valores se calcularon a partir de los hexadecimales impresos:

| Texto / fondo | Contraste aproximado | Decisión |
| --- | --- | --- |
| Blanco / #1F3A5F | 11.48:1 | Adecuado para texto normal. |
| Blanco / #2E86AB | 4.11:1 | No usar para texto normal; usar azul profundo de fondo o cambiar la pareja. |
| Blanco / #24708F | 5.54:1 | Adecuado para botones con texto normal. |
| Blanco / #F2643B | 3.15:1 | No usar para etiquetas pequeñas de compra. |
| #1B2432 / #F2643B | 4.95:1 | Pareja propuesta para “Comprar boletos”. |
| Blanco / #D64E2A | 4.22:1 | Tampoco cumple 4.5:1 para texto normal. |
| Blanco / #2E9E5B | 3.41:1 | Preferir etiqueta suave y texto verde oscuro. |
| #1E7A45 / #E5F3EC | 4.68:1 | Pareja propuesta para etiquetas de éxito. |
| #1F3A5F / #F1B434 | 6.19:1 | Pareja propuesta para advertencias. |
| Blanco / #C0392B | 5.44:1 | Adecuado para texto normal. |
| #9AA6B6 / blanco | 2.47:1 | Insuficiente para placeholders necesarios o texto activo. |

Usa 4.5:1 como objetivo mínimo para texto normal y verifica controles e indicadores informativos frente a los colores adyacentes. La negrita por sí sola no convierte cualquier texto pequeño en texto grande. No afirmes conformidad completa de accesibilidad por aprobar unas parejas de colores. Referencia: [WCAG sobre contraste de texto](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html).

Mantén el QR oscuro sobre blanco, completo y con margen, sin degradados ni elementos encima. Conserva el diseño de boleto que ya funciona y los logos originales.

## Pruebas que deben demostrar la corrección

Añade regresiones significativas para la compra múltiple. Identifica estos casos con un prefijo nuevo como `CP-MULTI-*` para no confundirlos con CP-01 a CP-20:

| Caso | Resultado esperado |
| --- | --- |
| Comprar 2 boletos sin asiento | Una orden, total de 2 unidades, dos códigos QR diferentes y cupo disponible reducido en 2. |
| Comprar 3 boletos con asiento | Tres localidades distintas, total correcto y tres boletos asociados a la misma orden. |
| Elegir 3 boletos y solo 2 asientos | No continuar; mensaje claro de cuántos faltan. |
| Pulsar un asiento ya seleccionado | Se quita de la selección y se actualiza el contador, sin duplicados. |
| Reducir cantidad o cambiar tipo | Selección y total se revalidan, sin localidades ocultas ni tarifas incompatibles. |
| Volver desde checkout o recrear pantalla | Se conserva/restaura el estado de compra previsto, sin convertirlo en cantidad 1. |
| Solicitar 3 cuando quedan 2 | Se impide la compra completa; no hay emisión parcial ni cobro aprobado sin gestión del conflicto. |
| Rechazo del pago de 3 boletos | Cero boletos emitidos, sin descuento definitivo de inventario y con reintento coherente. |
| Doble toque o notificación repetida | Una sola emisión completa y un único cobro efectivo por la operación. |
| Dos compras compiten por un mismo asiento | Solo una puede conservarlo; la otra no recibe una compra parcial silenciosa. |
| Validar uno de los tres QR | Solo ese boleto pasa a usado; los otros dos siguen válidos. |
| Reintentar el QR usado | Acceso rechazado para ese boleto, sin consumir los restantes. |
| Boleto comprado en web y abierto/validado en Android | Mismo estado y datos mediante API compartida, si ambos clientes están disponibles. |

Verifica con una prueba que inicialmente reproduzca la restricción a uno y después pase con la corrección. No basta con probar un cálculo aislado de cantidades.

Compila con el Gradle Wrapper y el JDK compatible con las versiones reales; ejecuta las pruebas relevantes. En emulador/dispositivo, completa compras de 2 y 3 con la nueva paleta y revisa legibilidad, áreas táctiles, texto ampliado y paneles por rol. Guarda capturas antes/después si puedes producirlas.

Las pruebas de concurrencia e idempotencia real requieren backend y base de datos; identifica las realizadas contra el mock. Una pasarela simulada no acredita pagos reales. No marques CP-14 o CP-20 como aprobados sin ejecutar sus dependencias. No afirmes haber ejecutado un emulador o modificado un servicio inaccesible.

## Resultado final solicitado

Entrega la causa comprobada del fallo, lo que ya estaba correcto, archivos y contratos modificados, migraciones preparadas sin pérdida de datos, pruebas ejecutadas y resultados, capturas, paleta aplicada y pendientes concretos.

Actualiza una nota técnica del proyecto con la ampliación de compra múltiple y las discrepancias restantes. Conserva el funcionamiento que ya existe y completa las correcciones autorizadas que puedan verificarse en el entorno disponible.
