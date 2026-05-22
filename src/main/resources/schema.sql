-- DDL no destructivo: solo crea tablas si no existen (sin DROP TABLE).
-- Los índices UNIQUE IF NOT EXISTS cubren tablas ya creadas por Hibernate sin restricciones.

CREATE TABLE IF NOT EXISTS usuarios (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(255) NOT NULL,
    password        VARCHAR(255) NOT NULL,
    rol             VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS clientes (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    perfil_riesgo   VARCHAR(50)  NOT NULL,
    asesor_id       BIGINT       NOT NULL
);

CREATE TABLE IF NOT EXISTS activos_financieros (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(50)  NOT NULL,
    nombre          VARCHAR(255) NOT NULL,
    precio_mercado  DOUBLE PRECISION NOT NULL,
    moneda          VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS transacciones (
    id                      BIGSERIAL PRIMARY KEY,
    cliente_id              BIGINT       NOT NULL,
    activo_financiero_id    BIGINT,
    moneda                  VARCHAR(10),
    tipo_operacion          VARCHAR(50)  NOT NULL,
    cantidad                INTEGER,
    precio_ejecucion        DOUBLE PRECISION NOT NULL,
    fecha                   TIMESTAMP    NOT NULL
);

-- Migración suave de columnas añadidas tras el esquema Hibernate original
ALTER TABLE activos_financieros ADD COLUMN IF NOT EXISTS moneda VARCHAR(10);
ALTER TABLE transacciones ADD COLUMN IF NOT EXISTS moneda VARCHAR(10);
ALTER TABLE transacciones ALTER COLUMN activo_financiero_id DROP NOT NULL;

-- Restricciones UNIQUE idempotentes (requeridas para ON CONFLICT en data.sql)
CREATE UNIQUE INDEX IF NOT EXISTS uk_usuarios_username ON usuarios (username);
CREATE UNIQUE INDEX IF NOT EXISTS uk_clientes_email ON clientes (email);
CREATE UNIQUE INDEX IF NOT EXISTS uk_activos_ticker ON activos_financieros (ticker);
