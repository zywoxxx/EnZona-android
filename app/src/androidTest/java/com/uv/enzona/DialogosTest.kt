package com.uv.enzona

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.uv.enzona.data.model.PoliticaCancelacion
import com.uv.enzona.data.model.TipoCancelacion
import com.uv.enzona.ui.components.DialogoCancelarEvento
import com.uv.enzona.ui.components.DialogoConvertirseOrganizador
import com.uv.enzona.ui.components.DialogoDejarOrganizador
import com.uv.enzona.ui.theme.EnZonaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Pruebas de Compose (instrumentadas) de los tres diálogos v3. Se ejecutan en
 * emulador con `gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class DialogosTest {

    @get:Rule
    val regla = createComposeRule()

    private fun politica(tipo: TipoCancelacion, cobrado: String, comision: String, n: Int, permitida: Boolean = true, bloqueo: String? = null) =
        PoliticaCancelacion(
            permitida = permitida, tipo = if (permitida) tipo else null,
            importeCobrado = BigDecimal(cobrado), comision = BigDecimal(comision), reembolso = BigDecimal(cobrado),
            boletosAfectados = n, motivoBloqueo = bloqueo,
        )

    // ------------------------------------------------------------ cancelar evento

    @Test
    fun cancelarEvento_dePago_muestraReembolsoYComision_yElBotonAceptaComision() {
        var confirmado = false
        regla.setContent {
            EnZonaTheme {
                DialogoCancelarEvento(
                    nombreEvento = "Concierto", politica = politica(TipoCancelacion.DE_PAGO_CON_COMISION, "1500.00", "150.00", 3),
                    administrativa = false, procesando = false, error = null,
                    onCancelar = {}, onConfirmar = { confirmado = true },
                )
            }
        }
        regla.onNodeWithText("Se reembolsarán \$1,500.00 MXN a 3 asistentes. Se aplicará una comisión de cancelación del 10 % (\$150.00 MXN) a tu cuenta.").assertIsDisplayed()
        regla.onNodeWithText("Comisión de cancelación: \$150.00 MXN").assertIsDisplayed()
        regla.onNodeWithTag("boton_confirmar_cancelacion").assertIsEnabled()
        regla.onNodeWithText("Cancelar y aceptar comisión").assertIsDisplayed().performClick()
        assertTrue(confirmado)
    }

    @Test
    fun cancelarEvento_gratuito_indicaConfirmacionesAnuladas_sinComision() {
        regla.setContent {
            EnZonaTheme {
                DialogoCancelarEvento(
                    nombreEvento = "Feria", politica = politica(TipoCancelacion.GRATUITO, "0.00", "0.00", 3),
                    administrativa = false, procesando = false, error = null, onCancelar = {}, onConfirmar = {},
                )
            }
        }
        regla.onNodeWithText("Se cancelará el evento y se anularán 3 confirmaciones.").assertIsDisplayed()
        regla.onNodeWithText("Cancelar evento").assertIsDisplayed()
    }

    @Test
    fun cancelarEvento_bloqueado_deshabilitaElBoton_yMuestraElMotivo() {
        var confirmado = false
        regla.setContent {
            EnZonaTheme {
                DialogoCancelarEvento(
                    nombreEvento = "Feria",
                    politica = politica(TipoCancelacion.GRATUITO, "0.00", "0.00", 3, permitida = false, bloqueo = "El evento ya comenzó. No se puede cancelar; márcalo como finalizado."),
                    administrativa = false, procesando = false, error = null, onCancelar = {}, onConfirmar = { confirmado = true },
                )
            }
        }
        regla.onNodeWithText("El evento ya comenzó. No se puede cancelar; márcalo como finalizado.").assertIsDisplayed()
        regla.onNodeWithTag("boton_confirmar_cancelacion").assertIsNotEnabled()
        assertFalse(confirmado)
    }

    @Test
    fun cancelarEvento_administrativa_exigeMotivo() {
        var motivoRecibido: String? = "sin llamar"
        regla.setContent {
            EnZonaTheme {
                DialogoCancelarEvento(
                    nombreEvento = "Concierto", politica = politica(TipoCancelacion.ADMINISTRATIVA, "1500.00", "0.00", 3),
                    administrativa = true, procesando = false, error = null, onCancelar = {}, onConfirmar = { motivoRecibido = it },
                )
            }
        }
        regla.onNodeWithTag("boton_confirmar_cancelacion").assertIsNotEnabled()
        regla.onNodeWithText("Motivo de la cancelación").performTextInput("Contenido reportado")
        regla.onNodeWithTag("boton_confirmar_cancelacion").assertIsEnabled().performClick()
        assertEquals("Contenido reportado", motivoRecibido)
    }

    // ------------------------------------------------------------ convertirse en organizador

    @Test
    fun convertirseEnOrganizador_cancelarNoConfirma_yConfirmarSi() {
        var confirmaciones = 0
        var cancelaciones = 0
        regla.setContent {
            EnZonaTheme { DialogoConvertirseOrganizador(onCancelar = { cancelaciones++ }, onConfirmar = { confirmaciones++ }) }
        }
        regla.onNodeWithText("¿Convertirte en organizador?").assertIsDisplayed()
        regla.onNodeWithText("• Aparece la pestaña Mis eventos.").assertIsDisplayed()
        regla.onNodeWithTag("boton_cancelar_organizador").performClick()
        assertEquals(0, confirmaciones)   // CP-ROL-01: cancelar no activa el rol
        assertEquals(1, cancelaciones)
        regla.onNodeWithTag("boton_confirmar_organizador").performClick()
        assertEquals(1, confirmaciones)   // CP-ROL-02
    }

    // ------------------------------------------------------------ dejar de ser organizador

    @Test
    fun dejarOrganizador_conMotivoDeBloqueo_deshabilitaConfirmar() {
        var confirmado = false
        regla.setContent {
            EnZonaTheme {
                DialogoDejarOrganizador(
                    motivoBloqueo = "Tienes 1 evento(s) publicado(s) con boletos vigentes: «Concierto».",
                    onCancelar = {}, onConfirmar = { confirmado = true },
                )
            }
        }
        regla.onNodeWithText("Tienes 1 evento(s) publicado(s) con boletos vigentes: «Concierto».").assertIsDisplayed()
        regla.onNodeWithTag("boton_confirmar_dejar").assertIsNotEnabled()   // CP-ROL-04
        assertFalse(confirmado)
    }

    @Test
    fun dejarOrganizador_sinBloqueo_confirma() {
        var confirmado = false
        regla.setContent {
            EnZonaTheme { DialogoDejarOrganizador(motivoBloqueo = null, onCancelar = {}, onConfirmar = { confirmado = true }) }
        }
        regla.onNodeWithTag("boton_confirmar_dejar").assertIsEnabled().performClick()
        assertTrue(confirmado)   // CP-ROL-03
    }
}
