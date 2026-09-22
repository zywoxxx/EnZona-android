-- =====================================================================
--  EnZona — Adición al modelo relacional: métodos de pago guardados
--  Motor: PostgreSQL 15
--
--  Esta tabla es necesaria para la funcionalidad «Métodos de pago
--  guardados» de la aplicación. Está pensada para añadirse a
--  esquema_enzona.sql sin modificar nada de lo existente.
--
--  IMPORTANTE — restricción del Documento de Visión y Alcance:
--  «la plataforma no almacena datos de tarjetas». Por eso esta tabla NO
--  tiene columna para el número de tarjeta ni para el CVV. Solo guarda:
--    · la marca y los últimos cuatro dígitos, para que la persona
--      reconozca cuál de sus tarjetas es;
--    · el token que devuelve la pasarela, que es lo único con lo que se
--      puede volver a cobrar y con el que no se puede reconstruir la
--      tarjeta.
--  La tokenización la realiza el SDK de la pasarela en el cliente; el
--  backend nunca recibe el número completo.
-- =====================================================================

CREATE TYPE tipo_metodo_pago AS ENUM ('TARJETA', 'OXXO', 'SPEI');

CREATE TABLE metodo_pago (
    id                BIGSERIAL         PRIMARY KEY,
    usuario_id        BIGINT            NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    tipo              tipo_metodo_pago  NOT NULL DEFAULT 'TARJETA',
    marca             VARCHAR(40),                       -- Visa, Mastercard, American Express…
    ultimos4          CHAR(4),                           -- solo para identificación visual
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
CREATE UNIQUE INDEX ux_metodo_predeterminado
    ON metodo_pago (usuario_id)
    WHERE predeterminado;

CREATE INDEX idx_metodo_usuario ON metodo_pago (usuario_id);

-- ---------------------------------------------------------------------
--  Enlace opcional con el pago ya existente: permite saber con qué
--  método guardado se cobró cada orden, sin duplicar información.
-- ---------------------------------------------------------------------
ALTER TABLE pago
    ADD COLUMN metodo_pago_id BIGINT REFERENCES metodo_pago(id) ON DELETE SET NULL;
