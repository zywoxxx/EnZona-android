-- =====================================================================
-- EnZona · migración incremental v4 (sobre v2 + v3). NO ejecuta DROP.
-- Se aplica con psql sobre una base con datos; cada sentencia es idempotente.
-- Cambios: notificaciones del asistente con tipo, lectura y boleto relacionado.
-- El diagrama ER (usuario, rol, usuario_rol, categoria, evento, tipo_boleto,
-- asiento, orden, pago, boleto, validacion_acceso) ya existe en v2: la app se
-- alineó a él sin cambios de esquema (ver notas_v4_modelo_notificaciones.md).
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) Tipo de notificación
-- ---------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_notificacion') THEN
        CREATE TYPE tipo_notificacion AS ENUM
            ('COMPRA_CONFIRMADA', 'RECORDATORIO', 'EVENTO_CANCELADO', 'FECHA_CAMBIADA');
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2) notificacion_asistente: tipo, leída y boleto relacionado
--    (la tabla la creó migracion_v3_incremental.sql)
-- ---------------------------------------------------------------------
ALTER TABLE notificacion_asistente
    ADD COLUMN IF NOT EXISTS tipo      tipo_notificacion NOT NULL DEFAULT 'EVENTO_CANCELADO',
    ADD COLUMN IF NOT EXISTS leida     BOOLEAN           NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS boleto_id BIGINT            REFERENCES boleto(id) ON DELETE SET NULL;

-- Las filas creadas en v3 eran todas de cancelación; el DEFAULT las cubre.
ALTER TABLE notificacion_asistente ALTER COLUMN tipo DROP DEFAULT;

-- Un solo recordatorio por usuario y evento (lo mismo que garantiza la app)
CREATE UNIQUE INDEX IF NOT EXISTS uq_notificacion_recordatorio
    ON notificacion_asistente (usuario_id, evento_id)
    WHERE tipo = 'RECORDATORIO';

CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_leida
    ON notificacion_asistente (usuario_id, leida, fecha DESC);

-- ---------------------------------------------------------------------
-- 3) Recordatorios: consulta que ejecuta el job diario del backend
--    (equivalente a MockRepository.generarRecordatorios). Referencia, no se
--    ejecuta aquí.
-- ---------------------------------------------------------------------
-- INSERT INTO notificacion_asistente (usuario_id, evento_id, tipo, texto, boleto_id)
-- SELECT o.asistente_id, e.id, 'RECORDATORIO',
--        '«' || e.nombre || '» es en ' || (e.fecha_hora_inicio::date - now()::date) || ' días.',
--        MIN(b.id)
--   FROM boleto b JOIN orden o ON o.id = b.orden_id JOIN evento e ON e.id = o.evento_id
--  WHERE b.estado = 'VALIDO' AND e.estado = 'PUBLICADO'
--    AND e.fecha_hora_inicio BETWEEN now() AND now() + INTERVAL '7 days'
--  GROUP BY o.asistente_id, e.id
-- ON CONFLICT DO NOTHING;
