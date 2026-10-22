package mx.uv.enzona.eventos;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Generación del plano de asientos de una sección (RF-09): filas A, B, C… y
 * asientos 1..N.
 *
 * Se hace con UNA sentencia por sección (generate_series). Insertar fila por
 * fila con JPA son cientos de idas y vueltas a la base: contra un servidor
 * remoto, un teatro de 250 butacas tardaba minutos.
 */
public final class PlanoAsientos {

    private PlanoAsientos() {
    }

    /** @return número de asientos creados */
    public static int generar(JdbcClient jdbc, Long eventoId, String seccion, int filas, int asientosPorFila) {
        return jdbc.sql("""
                INSERT INTO asiento (evento_id, seccion, fila, numero, estado)
                SELECT :evento, CAST(:seccion AS varchar), chr(64 + f), CAST(n AS varchar), 'DISPONIBLE'
                  FROM generate_series(1, :filas) AS f, generate_series(1, :porFila) AS n
                 ORDER BY f, n
                ON CONFLICT (evento_id, seccion, fila, numero) DO NOTHING
                """)
                .param("evento", eventoId)
                .param("seccion", seccion)
                .param("filas", filas)
                .param("porFila", asientosPorFila)
                .update();
    }
}
