-- =====================================================================
--  EnZona — Migración incremental hacia el modelo v2 (SIN pérdida de datos)
--  Motor: PostgreSQL 11+ (probar primero en una base aislada)
--
--  Contexto: `esquema_enzona_v2.sql` es un script de RECONSTRUCCIÓN
--  (DROP TABLE ... CASCADE). No debe ejecutarse contra una base con datos.
--  Este archivo aplica solo las diferencias v1 -> v2 y las protecciones que
--  necesita la compra múltiple, de forma idempotente (IF NOT EXISTS).
--
--  Nota: `\echo` es un metacomando de psql, no SQL; por eso no aparece aquí y
--  el archivo puede ejecutarse con psql o mediante JDBC/Flyway.
--
--  NO EJECUTADO en esta tarea: no hay backend ni base de datos en el
--  repositorio. Revisar y aplicar con el equipo de backend.
-- =====================================================================
BEGIN;

-- ---------------------------------------------------------------------
-- 1) Geolocalización del evento (v2): ciudad y coordenadas opcionales.
--    latitud y longitud pueden faltar juntas; nunca se rellenan con 0.
-- ---------------------------------------------------------------------
ALTER TABLE evento
    ADD COLUMN IF NOT EXISTS ciudad   VARCHAR(80) NOT NULL DEFAULT 'Orizaba',
    ADD COLUMN IF NOT EXISTS latitud  NUMERIC(9,6),
    ADD COLUMN IF NOT EXISTS longitud NUMERIC(9,6);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_latitud') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_latitud
            CHECK (latitud IS NULL OR latitud BETWEEN -90 AND 90);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_longitud') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_longitud
            CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180);
    END IF;
    -- Ambas coordenadas presentes o ambas ausentes
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_coordenadas_pares') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_coordenadas_pares
            CHECK ((latitud IS NULL) = (longitud IS NULL));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_evento_ciudad ON evento (ciudad);
CREATE INDEX IF NOT EXISTS idx_evento_geo    ON evento (latitud, longitud);
-- Nota: este índice compuesto no ordena por cercanía. Para CP-17 hace falta
-- una consulta de distancia (fórmula de Haversine en SQL o PostGIS/earthdistance).

-- ---------------------------------------------------------------------
-- 2) Compra múltiple: una orden con N boletos ya está permitida por el
--    esquema (boleto.orden_id no es único). Lo que falta es proteger la
--    localidad: dos boletos que RETIENEN su ocupación (VALIDO o USADO) no
--    pueden apuntar al mismo asiento. Los cancelados/expirados se conservan
--    como historial y permiten reemitir la localidad (índice único parcial).
--    Antes de crearlo, comprobar duplicados existentes:
--      SELECT asiento_id, count(*) FROM boleto
--       WHERE asiento_id IS NOT NULL AND estado IN ('VALIDO','USADO')
--       GROUP BY asiento_id HAVING count(*) > 1;
-- ---------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS ux_boleto_asiento_vigente
    ON boleto (asiento_id)
    WHERE asiento_id IS NOT NULL AND estado IN ('VALIDO', 'USADO');

-- Consultas frecuentes de la compra múltiple y del resumen de orden
CREATE INDEX IF NOT EXISTS idx_boleto_orden ON boleto (orden_id);
CREATE INDEX IF NOT EXISTS idx_pago_orden   ON pago (orden_id);

-- ---------------------------------------------------------------------
-- 3) Idempotencia de la operación de compra: la clave que envía el cliente
--    (cabecera Idempotency-Key) se guarda en la orden para que un reintento o
--    una notificación repetida devuelva la misma orden sin cobrar ni emitir
--    dos veces. Única por asistente.
-- ---------------------------------------------------------------------
ALTER TABLE orden ADD COLUMN IF NOT EXISTS clave_operacion VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orden_clave_operacion
    ON orden (asistente_id, clave_operacion)
    WHERE clave_operacion IS NOT NULL;

-- ---------------------------------------------------------------------
-- 4) Pagos pendientes (OXXO / SPEI): el enum estado_pago solo tiene APROBADO,
--    RECHAZADO y REEMBOLSADO. Se añade PENDIENTE de forma compatible.
--    (ALTER TYPE ... ADD VALUE no puede ir dentro de una transacción en
--    PostgreSQL < 12; ejecutar esta sentencia por separado si aplica.)
-- ---------------------------------------------------------------------
-- ALTER TYPE estado_pago ADD VALUE IF NOT EXISTS 'PENDIENTE';

-- ---------------------------------------------------------------------
-- 5) Líneas de orden (propuesta): el esquema no guarda qué se pidió antes de
--    cobrar. Para resolver una notificación posterior de la pasarela sin
--    depender del carrito en memoria del teléfono, se propone persistir la
--    solicitud confirmada (tipo, cantidad, precio unitario confirmado y
--    asientos requeridos) al crear la orden PENDIENTE.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orden_detalle (
    id                BIGSERIAL     PRIMARY KEY,
    orden_id          BIGINT        NOT NULL REFERENCES orden(id) ON DELETE CASCADE,
    tipo_boleto_id    BIGINT        REFERENCES tipo_boleto(id) ON DELETE RESTRICT,
    cantidad          INTEGER       NOT NULL CHECK (cantidad > 0),
    precio_unitario   NUMERIC(10,2) NOT NULL CHECK (precio_unitario >= 0),  -- precio confirmado al ordenar
    asientos_ids      BIGINT[]      NOT NULL DEFAULT '{}',                  -- localidades requeridas
    CONSTRAINT ck_orden_detalle_asientos
        CHECK (cardinality(asientos_ids) = 0 OR cardinality(asientos_ids) = cantidad)
);
CREATE INDEX IF NOT EXISTS idx_orden_detalle_orden ON orden_detalle (orden_id);

-- ---------------------------------------------------------------------
-- 6) Extensión previa que NO aparece en el diagrama v2: métodos de pago
--    guardados (ver esquema_metodo_pago.sql en la raíz). Se conserva.
-- ---------------------------------------------------------------------

COMMIT;
