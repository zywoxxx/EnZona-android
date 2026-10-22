-- =====================================================================
--  EnZona · V6 · Identidad: OTP de verificación y CURP cifrada (RNF-03)
--
--  La CURP se guarda cifrada con AES-256-GCM en `usuario.curp` (cada cifrado
--  usa un vector aleatorio, así que dos cifrados de la misma CURP difieren).
--  Para conservar la unicidad y poder buscar por CURP sin descifrar, se añade
--  un índice ciego: `curp_hash` = HMAC-SHA256(CURP) con clave del servidor.
-- =====================================================================
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS curp_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_curp_hash ON usuario (curp_hash) WHERE curp_hash IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_telefono  ON usuario (telefono)  WHERE telefono IS NOT NULL AND telefono <> '';

-- Código de un solo uso para verificar la cuenta (RF-02). Se guarda su hash,
-- nunca el código en claro; caduca y limita los intentos.
CREATE TABLE IF NOT EXISTS otp_verificacion (
    id           BIGSERIAL    PRIMARY KEY,
    usuario_id   BIGINT       NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    codigo_hash  VARCHAR(100) NOT NULL,
    expira       TIMESTAMPTZ  NOT NULL,
    intentos     INTEGER      NOT NULL DEFAULT 0,
    usado        BOOLEAN      NOT NULL DEFAULT FALSE,
    creado       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_otp_usuario ON otp_verificacion (usuario_id, usado, creado DESC);
