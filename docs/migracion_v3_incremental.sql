-- =====================================================================
--  EnZona — Migración incremental v3 (SIN pérdida de datos)
--  Motor: PostgreSQL 11+ (probar primero en una base aislada)
--
--  Cambios v3: registro de la cancelación de eventos (RF-14 con regla de
--  comisión), cargos al organizador y avisos a asistentes. Se aplica después
--  de migracion_v2_incremental.sql. Idempotente (IF NOT EXISTS), sin \echo.
--
--  NO EJECUTADO en esta tarea: no hay backend ni base de datos en el
--  repositorio. No ejecutar esquema_enzona_v2.sql (DROP ... CASCADE) contra
--  datos existentes.
-- =====================================================================
BEGIN;

-- ---------------------------------------------------------------------
-- 1) Registro de la cancelación en el propio evento.
--    Se eligió añadir columnas a `evento` (no una tabla cancelacion_evento):
--    un evento se cancela una sola vez, la consulta del organizador y del
--    asistente parte del evento, y no hace falta historial de varias
--    cancelaciones. Se crea una u otra, no ambas.
-- ---------------------------------------------------------------------
ALTER TABLE evento
    ADD COLUMN IF NOT EXISTS fecha_cancelacion    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelado_por        BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS motivo_cancelacion   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS comision_cancelacion NUMERIC(12,2) NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_comision_cancelacion') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_comision_cancelacion
            CHECK (comision_cancelacion >= 0);
    END IF;
    -- Coherencia: un evento cancelado registra fecha y quién lo canceló
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_cancelacion_completa') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_cancelacion_completa
            CHECK (estado <> 'CANCELADO' OR (fecha_cancelacion IS NOT NULL AND cancelado_por IS NOT NULL));
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 2) Cargos al organizador: la comisión de cancelación (10 % de lo cobrado)
--    queda como cargo pendiente en su cuenta. No hay datos de tarjeta.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cargo_organizador (
    id              BIGSERIAL      PRIMARY KEY,
    organizador_id  BIGINT         NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    evento_id       BIGINT         REFERENCES evento(id) ON DELETE SET NULL,
    concepto        VARCHAR(160)   NOT NULL,
    importe         NUMERIC(12,2)  NOT NULL CHECK (importe >= 0),
    pagado          BOOLEAN        NOT NULL DEFAULT FALSE,
    fecha           TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cargo_organizador ON cargo_organizador (organizador_id, pagado);

-- ---------------------------------------------------------------------
-- 3) Avisos a asistentes (cancelación, cambio de fecha). El envío real lo
--    hace el módulo de notificaciones (correo); esta tabla es la bitácora.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notificacion_asistente (
    id          BIGSERIAL     PRIMARY KEY,
    usuario_id  BIGINT        NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    evento_id   BIGINT        REFERENCES evento(id) ON DELETE SET NULL,
    texto       VARCHAR(500)  NOT NULL,
    fecha       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    enviada     BOOLEAN       NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_notificacion_usuario ON notificacion_asistente (usuario_id, enviada);

-- ---------------------------------------------------------------------
-- 4) Reembolsos: se registran contra el pago y su referencia de pasarela
--    (pago.estado = 'REEMBOLSADO'). No se añaden campos de tarjeta. Si el
--    proveedor exige guardar el identificador del reembolso, va aquí:
-- ---------------------------------------------------------------------
ALTER TABLE pago ADD COLUMN IF NOT EXISTS referencia_reembolso VARCHAR(120);
ALTER TABLE pago ADD COLUMN IF NOT EXISTS fecha_reembolso      TIMESTAMPTZ;

-- ---------------------------------------------------------------------
-- 5) Coordenadas obligatorias para publicar (RF-08 v3): se valida en la
--    aplicación; a nivel de esquema se documenta como restricción opcional
--    (activar solo cuando todos los eventos publicados tengan coordenadas):
-- ---------------------------------------------------------------------
-- ALTER TABLE evento ADD CONSTRAINT ck_evento_publicado_con_coordenadas
--     CHECK (estado <> 'PUBLICADO' OR (latitud IS NOT NULL AND longitud IS NOT NULL));

COMMIT;
