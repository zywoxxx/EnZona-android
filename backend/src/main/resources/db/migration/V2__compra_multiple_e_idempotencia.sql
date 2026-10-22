-- =====================================================================
--  EnZona · V2 · Compra múltiple, idempotencia y líneas de orden
--  Origen: docs/migracion_v2_incremental.sql (nunca se había aplicado).
--  Idempotente. Flyway la ejecuta dentro de una transacción.
-- =====================================================================

-- 1) Coordenadas: ambas presentes o ambas ausentes (nunca 0/0 de relleno).
--    Los CHECK de rango ya existen en el esquema v2 con nombre automático.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_coordenadas_pares') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_coordenadas_pares
            CHECK ((latitud IS NULL) = (longitud IS NULL));
    END IF;
END $$;

-- 2) Un asiento no puede tener dos boletos que retengan su ocupación.
--    Los cancelados o expirados se conservan como historial.
CREATE UNIQUE INDEX IF NOT EXISTS ux_boleto_asiento_vigente
    ON boleto (asiento_id)
    WHERE asiento_id IS NOT NULL AND estado IN ('VALIDO', 'USADO');

CREATE INDEX IF NOT EXISTS idx_boleto_orden   ON boleto (orden_id);
CREATE INDEX IF NOT EXISTS idx_pago_orden     ON pago (orden_id);
CREATE INDEX IF NOT EXISTS idx_orden_evento   ON orden (evento_id);
CREATE INDEX IF NOT EXISTS idx_tipo_evento    ON tipo_boleto (evento_id);
CREATE INDEX IF NOT EXISTS idx_asiento_evento ON asiento (evento_id, estado);

-- 3) Idempotencia de la compra (cabecera Idempotency-Key): única por asistente.
ALTER TABLE orden ADD COLUMN IF NOT EXISTS clave_operacion VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orden_clave_operacion
    ON orden (asistente_id, clave_operacion)
    WHERE clave_operacion IS NOT NULL;

-- 4) Líneas de orden: lo pedido y el precio confirmado al ordenar. Permite
--    emitir los boletos cuando la pasarela confirma un pago pendiente.
CREATE TABLE IF NOT EXISTS orden_detalle (
    id                BIGSERIAL     PRIMARY KEY,
    orden_id          BIGINT        NOT NULL REFERENCES orden(id) ON DELETE CASCADE,
    tipo_boleto_id    BIGINT        REFERENCES tipo_boleto(id) ON DELETE RESTRICT,
    cantidad          INTEGER       NOT NULL CHECK (cantidad > 0),
    precio_unitario   NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),
    asientos_ids      BIGINT[]      NOT NULL DEFAULT '{}',
    CONSTRAINT ck_orden_detalle_asientos
        CHECK (cardinality(asientos_ids) = 0 OR cardinality(asientos_ids) = cantidad)
);
CREATE INDEX IF NOT EXISTS idx_orden_detalle_orden ON orden_detalle (orden_id);
