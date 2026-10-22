-- =====================================================================
--  EnZona · V5 · Métodos de pago guardados
--  Origen: esquema_metodo_pago.sql (raíz del repositorio), reescrito de forma
--  idempotente. La tabla NO tiene columnas para el número de tarjeta ni el CVV:
--  solo marca, últimos cuatro dígitos, vencimiento y el token de la pasarela.
-- =====================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'tipo_metodo_pago') THEN
        CREATE TYPE tipo_metodo_pago AS ENUM ('TARJETA', 'OXXO', 'SPEI');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS metodo_pago (
    id                BIGSERIAL         PRIMARY KEY,
    usuario_id        BIGINT            NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tipo              tipo_metodo_pago  NOT NULL DEFAULT 'TARJETA',
    marca             VARCHAR(40),
    ultimos4          CHAR(4),
    vencimiento       CHAR(5),                           -- 'MM/AA'
    token_pasarela    VARCHAR(180)      NOT NULL,        -- referencia opaca de la pasarela
    predeterminado    BOOLEAN           NOT NULL DEFAULT FALSE,
    fecha_registro    TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CONSTRAINT ck_metodo_tarjeta CHECK (
        tipo <> 'TARJETA' OR (marca IS NOT NULL AND ultimos4 IS NOT NULL AND vencimiento IS NOT NULL)
    ),
    CONSTRAINT ck_ultimos4_digitos CHECK (ultimos4 IS NULL OR ultimos4 ~ '^[0-9]{4}$')
);

-- Un único método predeterminado por usuario
CREATE UNIQUE INDEX IF NOT EXISTS ux_metodo_predeterminado
    ON metodo_pago (usuario_id)
    WHERE predeterminado;
CREATE INDEX IF NOT EXISTS idx_metodo_usuario ON metodo_pago (usuario_id);

-- Con qué método guardado se cobró cada orden, y por qué medio (tarjeta, OXXO, SPEI)
ALTER TABLE pago ADD COLUMN IF NOT EXISTS metodo_pago_id BIGINT REFERENCES metodo_pago(id) ON DELETE SET NULL;
ALTER TABLE pago ADD COLUMN IF NOT EXISTS medio tipo_metodo_pago;
