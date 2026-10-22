package mx.uv.enzona.eventos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Repositorios JPA del módulo de eventos. */
public final class Repositorios {

    private Repositorios() {
    }

    public interface EventoRepositorio extends JpaRepository<Evento, Long> {

        /**
         * Lee el evento bloqueando la fila en modo compartido (FOR SHARE): varias
         * compras pueden leerlo a la vez, pero una cancelación (que actualiza la
         * fila) espera a que terminen, y viceversa.
         */
        @Lock(LockModeType.PESSIMISTIC_READ)
        @Query("select e from Evento e where e.id = :id")
        Optional<Evento> buscarParaCompra(@Param("id") Long id);

        /** Bloqueo exclusivo (FOR UPDATE) para las operaciones que cambian el evento. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select e from Evento e where e.id = :id")
        Optional<Evento> buscarParaModificar(@Param("id") Long id);

        Optional<Evento> findByOrganizadorIdAndNombre(Long organizadorId, String nombre);
    }

    public interface CategoriaRepositorio extends JpaRepository<Categoria, Integer> {

        Optional<Categoria> findByNombreIgnoreCase(String nombre);

        List<Categoria> findAllByOrderByNombreAsc();
    }

    public interface TipoBoletoRepositorio extends JpaRepository<TipoBoleto, Long> {

        List<TipoBoleto> findByEventoIdOrderByIdAsc(Long eventoId);

        /**
         * Descuento de inventario por lote (DA-05, RNF-05). Solo prospera si quedan
         * al menos N: PostgreSQL serializa el acceso a la fila y quien llega después
         * reevalúa la condición. 0 filas afectadas = sin cupo.
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                UPDATE tipo_boleto SET cantidad_disponible = cantidad_disponible - :n
                 WHERE id = :id AND cantidad_disponible >= :n
                """, nativeQuery = true)
        int descontar(@Param("id") Long id, @Param("n") int n);

        /** Devuelve al inventario lo reservado (pago rechazado, vencido o cancelación). */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                UPDATE tipo_boleto SET cantidad_disponible = LEAST(cantidad_total, cantidad_disponible + :n)
                 WHERE id = :id
                """, nativeQuery = true)
        int reponer(@Param("id") Long id, @Param("n") int n);
    }

    public interface AsientoRepositorio extends JpaRepository<Asiento, Long> {

        List<Asiento> findByEventoIdOrderByIdAsc(Long eventoId);

        long countByEventoId(Long eventoId);

        /**
         * Toma los asientos solo si siguen DISPONIBLE. Se comprueba que el número
         * de filas afectadas sea igual al de asientos pedidos (todo o nada).
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                UPDATE asiento SET estado = CAST(:nuevo AS estado_asiento)
                 WHERE evento_id = :evento AND id IN (:ids) AND estado = 'DISPONIBLE'
                """, nativeQuery = true)
        int tomar(@Param("evento") Long eventoId, @Param("ids") List<Long> ids, @Param("nuevo") String nuevo);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                UPDATE asiento SET estado = CAST(:nuevo AS estado_asiento)
                 WHERE id IN (:ids) AND estado = CAST(:actual AS estado_asiento)
                """, nativeQuery = true)
        int cambiar(@Param("ids") List<Long> ids, @Param("actual") String actual, @Param("nuevo") String nuevo);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = "UPDATE asiento SET estado = 'DISPONIBLE' WHERE id IN (:ids)", nativeQuery = true)
        int liberar(@Param("ids") List<Long> ids);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = "DELETE FROM asiento WHERE evento_id = :evento", nativeQuery = true)
        int borrarDelEvento(@Param("evento") Long eventoId);
    }

    public interface CargoRepositorio extends JpaRepository<CargoOrganizador, Long> {
    }
}
