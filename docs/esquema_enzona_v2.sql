-- =====================================================================
--  EnZona — Modelo relacional (v2: soporte de geolocalización)
--  Motor: PostgreSQL 11  (compatible con 12/13/14/15)
--  Cambios v2: el evento incorpora ciudad, latitud y longitud para el
--  descubrimiento "cerca de ti" (área de estudio: Orizaba, Veracruz).
-- =====================================================================
SET client_encoding = 'UTF8';
SET client_min_messages = warning;
BEGIN;

DROP TABLE IF EXISTS validacion_acceso, boleto, pago, orden, asiento,
                     tipo_boleto, evento, categoria, usuario_rol, rol, usuario CASCADE;
DROP TYPE  IF EXISTS resultado_acceso, estado_asiento, estado_boleto,
                     estado_pago, estado_orden, estado_evento, estado_usuario;

CREATE TYPE estado_usuario   AS ENUM ('PENDIENTE', 'ACTIVO', 'BLOQUEADO');
CREATE TYPE estado_evento    AS ENUM ('BORRADOR', 'PUBLICADO', 'REALIZADO', 'CANCELADO');
CREATE TYPE estado_orden     AS ENUM ('PENDIENTE', 'PAGADA', 'CANCELADA', 'REEMBOLSADA');
CREATE TYPE estado_pago      AS ENUM ('APROBADO', 'RECHAZADO', 'REEMBOLSADO');
CREATE TYPE estado_boleto    AS ENUM ('VALIDO', 'USADO', 'CANCELADO', 'EXPIRADO');
CREATE TYPE estado_asiento   AS ENUM ('DISPONIBLE', 'RESERVADO', 'OCUPADO');
CREATE TYPE resultado_acceso AS ENUM ('PERMITIDO', 'RECHAZADO');

CREATE TABLE usuario (
    id                   BIGSERIAL     PRIMARY KEY,
    nombre               VARCHAR(120)  NOT NULL,
    correo               VARCHAR(180)  NOT NULL UNIQUE,
    telefono             VARCHAR(20),
    curp                 VARCHAR(255)  UNIQUE,
    contrasena_hash      VARCHAR(255)  NOT NULL,
    correo_verificado    BOOLEAN       NOT NULL DEFAULT FALSE,
    telefono_verificado  BOOLEAN       NOT NULL DEFAULT FALSE,
    estado               estado_usuario NOT NULL DEFAULT 'PENDIENTE',
    fecha_registro       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE rol (
    id      SMALLSERIAL   PRIMARY KEY,
    nombre  VARCHAR(30)   NOT NULL UNIQUE
);

CREATE TABLE usuario_rol (
    usuario_id  BIGINT   NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    rol_id      SMALLINT NOT NULL REFERENCES rol(id)     ON DELETE RESTRICT,
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE TABLE categoria (
    id      SERIAL       PRIMARY KEY,
    nombre  VARCHAR(60)  NOT NULL UNIQUE
);

CREATE TABLE evento (
    id                BIGSERIAL     PRIMARY KEY,
    organizador_id    BIGINT        NOT NULL REFERENCES usuario(id)   ON DELETE RESTRICT,
    categoria_id      INTEGER       REFERENCES categoria(id)          ON DELETE SET NULL,
    nombre            VARCHAR(160)  NOT NULL,
    descripcion       TEXT,
    lugar             VARCHAR(160)  NOT NULL,
    direccion         VARCHAR(240),
    ciudad            VARCHAR(80)   NOT NULL DEFAULT 'Orizaba',   -- área de estudio
    latitud           NUMERIC(9,6),                               -- geolocalización
    longitud          NUMERIC(9,6),                               -- geolocalización
    fecha_hora_inicio TIMESTAMPTZ   NOT NULL,
    fecha_hora_fin    TIMESTAMPTZ,
    aforo             INTEGER       NOT NULL CHECK (aforo > 0),
    es_de_pago        BOOLEAN       NOT NULL DEFAULT FALSE,
    requiere_asiento  BOOLEAN       NOT NULL DEFAULT FALSE,
    estado            estado_evento NOT NULL DEFAULT 'BORRADOR',
    fecha_creacion    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (fecha_hora_fin IS NULL OR fecha_hora_fin >= fecha_hora_inicio),
    CHECK (latitud  IS NULL OR latitud  BETWEEN -90  AND 90),
    CHECK (longitud IS NULL OR longitud BETWEEN -180 AND 180)
);

CREATE TABLE tipo_boleto (
    id                   BIGSERIAL     PRIMARY KEY,
    evento_id            BIGINT        NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    nombre               VARCHAR(80)   NOT NULL,
    precio               NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (precio >= 0),
    cantidad_total       INTEGER       NOT NULL CHECK (cantidad_total >= 0),
    cantidad_disponible  INTEGER       NOT NULL CHECK (cantidad_disponible >= 0),
    CHECK (cantidad_disponible <= cantidad_total)
);

CREATE TABLE asiento (
    id         BIGSERIAL       PRIMARY KEY,
    evento_id  BIGINT          NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    seccion    VARCHAR(40),
    fila       VARCHAR(10),
    numero     VARCHAR(10),
    estado     estado_asiento  NOT NULL DEFAULT 'DISPONIBLE',
    UNIQUE (evento_id, seccion, fila, numero)
);

CREATE TABLE orden (
    id             BIGSERIAL     PRIMARY KEY,
    asistente_id   BIGINT        NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    evento_id      BIGINT        NOT NULL REFERENCES evento(id)  ON DELETE RESTRICT,
    total          NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
    estado         estado_orden  NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE pago (
    id                   BIGSERIAL    PRIMARY KEY,
    orden_id             BIGINT       NOT NULL REFERENCES orden(id) ON DELETE CASCADE,
    monto                NUMERIC(10,2) NOT NULL CHECK (monto >= 0),
    moneda               CHAR(3)      NOT NULL DEFAULT 'MXN',
    estado               estado_pago  NOT NULL,
    referencia_pasarela  VARCHAR(120),
    fecha                TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE boleto (
    id             BIGSERIAL     PRIMARY KEY,
    orden_id       BIGINT        NOT NULL REFERENCES orden(id)       ON DELETE RESTRICT,
    tipo_boleto_id BIGINT        NOT NULL REFERENCES tipo_boleto(id) ON DELETE RESTRICT,
    asiento_id     BIGINT        REFERENCES asiento(id)              ON DELETE SET NULL,
    codigo         VARCHAR(80)   NOT NULL UNIQUE,
    qr_firma       VARCHAR(255)  NOT NULL,
    estado         estado_boleto NOT NULL DEFAULT 'VALIDO',
    fecha_emision  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_uso      TIMESTAMPTZ
);

CREATE TABLE validacion_acceso (
    id            BIGSERIAL        PRIMARY KEY,
    boleto_id     BIGINT           NOT NULL REFERENCES boleto(id)  ON DELETE CASCADE,
    validador_id  BIGINT           NOT NULL REFERENCES usuario(id) ON DELETE RESTRICT,
    fecha_hora    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    resultado     resultado_acceso NOT NULL,
    offline       BOOLEAN          NOT NULL DEFAULT FALSE,
    sincronizado  BOOLEAN          NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_evento_fecha    ON evento (fecha_hora_inicio);
CREATE INDEX idx_evento_estado   ON evento (estado);
CREATE INDEX idx_evento_ciudad   ON evento (ciudad);
CREATE INDEX idx_evento_geo      ON evento (latitud, longitud);
CREATE INDEX idx_boleto_estado   ON boleto (estado);
CREATE INDEX idx_orden_asistente ON orden (asistente_id);

INSERT INTO rol (nombre) VALUES ('ASISTENTE'), ('ORGANIZADOR'), ('VALIDADOR'), ('ADMIN');
INSERT INTO categoria (nombre) VALUES
    ('Música'), ('Deportivo'), ('Cultural'), ('Taller'), ('Gastronómico'), ('Otro');

COMMIT;
\echo '== Esquema EnZona v2 (con geolocalización) creado =='
