-- =====================================================================
--  EnZona · V4 · Cancelación de eventos, cargos y notificaciones
--  Origen: docs/migracion_v3_incremental.sql + docs/migracion_v4_incremental.sql
--  (ninguna se había aplicado). Idempotente.
-- =====================================================================

-- 1) Registro de la cancelación en el propio evento (RF-14)
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
    -- Un evento cancelado registra cuándo se canceló. (No se exige cancelado_por:
    -- la FK es ON DELETE SET NULL y exigirlo impediría dar de baja esa cuenta.)
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_evento_cancelacion_fecha') THEN
        ALTER TABLE evento ADD CONSTRAINT ck_evento_cancelacion_fecha
            CHECK (estado <> 'CANCELADO' OR fecha_cancelacion IS NOT NULL);
    END IF;
END $$;

-- 2) Cargos al organizador: comisión de cancelación pendiente de cobro
CREATE TABLE IF NOT EXISTS cargo_organizador (
    id              BIGSERIAL      PRIMARY KEY,
    organizador_id  BIGINT         NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    evento_id       BIGINT         REFERENCES evento(id) ON DELETE SET NULL,
    concepto        VARCHAR(200)   NOT NULL,
    importe         NUMERIC(12,2)  NOT NULL CHECK (importe >= 0),
    pagado          BOOLEAN        NOT NULL DEFAULT FALSE,
    fecha           TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cargo_organizador ON cargo_organizador (organizador_id, pagado);

-- 3) Reembolsos: se registran contra el pago y la referencia de la pasarela.
--    La plataforma no almacena datos de tarjeta.
ALTER TABLE pago ADD COLUMN IF NOT EXISTS referencia_reembolso VARCHAR(120);
ALTER TABLE pago ADD COLUMN IF NOT EXISTS fecha_reembolso      TIMESTAMPTZ;

-- 4) Notificaciones del asistente (v3 + v4 en un solo paso)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_notificacion') THEN
        CREATE TYPE tipo_notificacion AS ENUM
            ('COMPRA_CONFIRMADA', 'RECORDATORIO', 'EVENTO_CANCELADO', 'FECHA_CAMBIADA');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS notificacion_asistente (
    id          BIGSERIAL          PRIMARY KEY,
    usuario_id  BIGINT             NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    evento_id   BIGINT             REFERENCES evento(id) ON DELETE SET NULL,
    tipo        tipo_notificacion  NOT NULL,
    texto       VARCHAR(500)       NOT NULL,
    fecha       TIMESTAMPTZ        NOT NULL DEFAULT now(),
    enviada     BOOLEAN            NOT NULL DEFAULT FALSE,
    leida       BOOLEAN            NOT NULL DEFAULT FALSE,
    boleto_id   BIGINT             REFERENCES boleto(id) ON DELETE SET NULL
);

-- Si la tabla ya existía con la forma de v3, se completan las columnas de v4
ALTER TABLE notificacion_asistente
    ADD COLUMN IF NOT EXISTS tipo      tipo_notificacion NOT NULL DEFAULT 'EVENTO_CANCELADO',
    ADD COLUMN IF NOT EXISTS leida     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS boleto_id BIGINT REFERENCES boleto(id) ON DELETE SET NULL;

-- Un solo recordatorio por usuario y evento
CREATE UNIQUE INDEX IF NOT EXISTS uq_notificacion_recordatorio
    ON notificacion_asistente (usuario_id, evento_id)
    WHERE tipo = 'RECORDATORIO';

CREATE INDEX IF NOT EXISTS idx_notificacion_usuario_leida
    ON notificacion_asistente (usuario_id, leida, fecha DESC);
