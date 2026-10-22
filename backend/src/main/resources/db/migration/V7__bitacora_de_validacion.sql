-- =====================================================================
--  EnZona · V7 · Bitácora de validación de acceso con motivo detallado
--
--  El enum `resultado_acceso` solo distingue PERMITIDO / RECHAZADO. Se conserva
--  y se añade `motivo` con el detalle: PERMITIDO, YA_USADO, CANCELADO,
--  EVENTO_CANCELADO, EXPIRADO, FIRMA_INVALIDA, NO_ENCONTRADO, OTRO_EVENTO.
--
--  `boleto_id` pasa a admitir NULL: un QR con firma inválida o con un código
--  inexistente no corresponde a ningún boleto, pero el intento debe quedar en
--  la bitácora (se guarda el código leído).
-- =====================================================================
ALTER TABLE validacion_acceso ALTER COLUMN boleto_id DROP NOT NULL;

ALTER TABLE validacion_acceso
    ADD COLUMN IF NOT EXISTS motivo        VARCHAR(30),
    ADD COLUMN IF NOT EXISTS codigo_leido  VARCHAR(80),
    ADD COLUMN IF NOT EXISTS evento_id     BIGINT REFERENCES evento(id) ON DELETE SET NULL,
    -- Momento del escaneo en el dispositivo (modo sin conexión); `fecha_hora` es el registro en servidor
    ADD COLUMN IF NOT EXISTS fecha_escaneo TIMESTAMPTZ,
    -- Identificador del escaneo generado por el dispositivo: evita duplicar al reenviar un lote
    ADD COLUMN IF NOT EXISTS clave_cliente VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_validacion_boleto ON validacion_acceso (boleto_id);
CREATE INDEX IF NOT EXISTS idx_validacion_evento ON validacion_acceso (evento_id, fecha_hora DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_validacion_clave_cliente
    ON validacion_acceso (validador_id, clave_cliente)
    WHERE clave_cliente IS NOT NULL;

-- Personal de acceso designado por evento (el rol VALIDADOR vive en usuario_rol;
-- esta tabla indica en qué eventos puede validar cada cuenta)
CREATE TABLE IF NOT EXISTS evento_personal_acceso (
    evento_id   BIGINT      NOT NULL REFERENCES evento(id)  ON DELETE CASCADE,
    usuario_id  BIGINT      NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    designado   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (evento_id, usuario_id)
);
