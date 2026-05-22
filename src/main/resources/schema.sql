-- Esquema PostgreSQL consolidado (sin ALTER ni índices sueltos al final).
-- Compatible con spring.jpa.hibernate.ddl-auto=validate

CREATE TABLE IF NOT EXISTS usuarios (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    rol         VARCHAR(50)  NOT NULL
);

CREATE TABLE IF NOT EXISTS clientes (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    perfil_riesgo   VARCHAR(50)  NOT NULL,
    asesor_id       BIGINT       NOT NULL REFERENCES usuarios (id)
);

CREATE TABLE IF NOT EXISTS activos_financieros (
    id              BIGSERIAL PRIMARY KEY,
    ticker          VARCHAR(50)  NOT NULL UNIQUE,
    nombre          VARCHAR(255) NOT NULL,
    precio_mercado  DOUBLE PRECISION NOT NULL,
    moneda          VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS transacciones (
    id                      BIGSERIAL PRIMARY KEY,
    cliente_id              BIGINT       NOT NULL REFERENCES clientes (id),
    activo_financiero_id    BIGINT       REFERENCES activos_financieros (id),
    moneda                  VARCHAR(10),
    tipo_operacion          VARCHAR(50)  NOT NULL,
    cantidad                INTEGER,
    precio_ejecucion        DOUBLE PRECISION NOT NULL,
    fecha                   TIMESTAMP    NOT NULL
);
