package com.uv.enzona.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Paleta EnZona v2 — "Paleta de color de la plataforma"
 * (docs/WhatsApp Image 2026-09-11 at 11.25.23.jpeg). Se usan los hexadecimales
 * impresos en la imagen, no valores aproximados.
 *
 * Tema claro: fondo gris muy claro, superficies blancas, azules de marca y
 * coral para la acción destacada. Los colores de MARCA están separados de los
 * SEMÁNTICOS (éxito, advertencia, error, información).
 *
 * Parejas de texto verificadas (WCAG, texto normal ≥ 4.5:1):
 *   blanco / azul marino 11.5:1 ✓ · blanco / azul profundo 5.5:1 ✓
 *   tinta / coral 4.95:1 ✓ · blanco / coral 3.15:1 ✗ (no se usa en texto)
 *   blanco / azul EnZona 4.1:1 ✗ (solo iconos y superficies) · azul profundo / blanco 5.5:1 ✓ (enlaces)
 *   verde éxito texto #1E7A45 / #E5F3EC 4.7:1 ✓ · azul marino / advertencia 6.2:1 ✓ · blanco / error 5.4:1 ✓
 *   gris texto / blanco 5.3:1 ✓ · gris suave / blanco 2.5:1 ✗ (solo decoración)
 */

// ------------------------------------------------------------- marca
val AzulMarino = Color(0xFF1F3A5F)        // primario: encabezados, navegación, botones generales
val AzulEnZona = Color(0xFF2E86AB)        // acento: iconos, seleccionados, superficies (no texto pequeño)
val AzulProfundo = Color(0xFF24708F)      // controles azules con texto blanco; enlaces sobre blanco
val AzulDegradadoInicio = Color(0xFF274C78)
val AzulDegradadoFin = AzulEnZona

val Coral = Color(0xFFF2643B)             // acción destacada (comprar, confirmar, publicar) con texto tinta
val CoralOscuro = Color(0xFFD64E2A)       // presionado / borde
val CoralSuave = Color(0xFFFDE9E2)        // contenedores y resaltados suaves

// Tintes del azul marino (escala de la imagen, sección 6) para contenedores
val AzulTinte50 = Color(0xFFEAF0F7)
val AzulTinte100 = Color(0xFFD7E1EE)
val AzulTinte200 = Color(0xFFB9C9DD)

// ------------------------------------------------------------- neutrales
val Tinta = Color(0xFF1B2432)             // texto principal; texto sobre coral
val GrisTexto = Color(0xFF5D6D7E)         // texto secundario
val GrisSuave = Color(0xFF9AA6B6)         // decoración, deshabilitados (no texto necesario)
val LineaBorde = Color(0xFFD9DFEA)        // separadores discretos
val Fondo = Color(0xFFEEF1F6)             // fondo general
val Superficie = Color(0xFFFFFFFF)        // tarjetas, campos, hojas
val SuperficieBaja = Color(0xFFF6F8FB)    // paneles apenas distintos del blanco
val SuperficieAlta = Color(0xFFE6EBF3)    // paneles tonales sobre tarjetas

// ------------------------------------------------------------- semánticos
val Exito = Color(0xFF2E9E5B)
val ExitoContenedor = Color(0xFFE5F3EC)
val SobreExitoContenedor = Color(0xFF1E7A45)

val Advertencia = Color(0xFFF1B434)       // con texto azul marino
val AdvertenciaContenedor = Color(0xFFFBF0D5)
val SobreAdvertenciaContenedor = AzulMarino

val ErrorRojo = Color(0xFFC0392B)         // con blanco
val ErrorContenedor = Color(0xFFF9E3E0)
val SobreErrorContenedor = Color(0xFF8E2A1F)

val Informacion = AzulEnZona
val InformacionContenedor = Color(0xFFE3F0F6)
val SobreInformacionContenedor = Color(0xFF1B4F63)

// ------------------------------------------------------------- QR
val BlancoQr = Color.White                // el QR siempre va sobre blanco puro
val NegroQr = Tinta

// ------------------------------------------------------------- alias de compatibilidad
// Nombres de la paleta anterior (etapas 3 y 4 los importan directamente).
// Se conservan como alias sobre la paleta v2 para que esas pantallas adopten el
// tema claro sin editarlas una por una; se irán sustituyendo por roles del tema.
val MoradoFondo = Fondo
val MoradoSuperficie = Superficie
val MoradoSuperficieAlta = SuperficieAlta
val MoradoSuperficieMasAlta = AzulTinte100
val MoradoSuperficieBaja = SuperficieBaja
val MoradoClaro = LineaBorde
val MoradoContornoTenue = LineaBorde
val NaranjaAcento = Coral
val SobreNaranja = Tinta
val NaranjaContenedor = CoralSuave
val SobreNaranjaContenedor = Tinta
val Lavanda = AzulProfundo
val SobreLavanda = Color.White
val AmarilloAcento = Advertencia
val SobreAmarillo = AzulMarino
val AmarilloContenedor = AdvertenciaContenedor
val SobreAmarilloContenedor = SobreAdvertenciaContenedor
val RosaAcento = CoralOscuro
val TextoPrincipal = Tinta
val TextoSecundario = GrisTexto
val TextoTerciario = GrisSuave
val VerdeOk = SobreExitoContenedor        // como color de texto/icono sobre claro debe ser el verde oscuro
val VerdeContenedor = ExitoContenedor
val SobreVerdeContenedor = SobreExitoContenedor
val AmarilloAviso = Advertencia
val AvisoContenedor = AdvertenciaContenedor
val SobreAvisoContenedor = SobreAdvertenciaContenedor
val RojoError = ErrorRojo
val RojoContenedor = ErrorContenedor
val SobreRojoContenedor = SobreErrorContenedor
