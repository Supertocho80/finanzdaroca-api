-- Datos semilla idempotentes (PostgreSQL).
-- Contraseña de todos los usuarios: secret123 (BCrypt verificado con Spring).

-- ---------------------------------------------------------------------------
-- Usuarios (DO UPDATE sincroniza password si el hash en BD era incorrecto)
-- ---------------------------------------------------------------------------
INSERT INTO usuarios (username, password, rol)
VALUES ('admin_jefe', '$2a$10$ENb5KFHiHaplfD9u3y85r.Dk/O5wOuZzBHpoyGiMyLgnP3XkCZ9DO', 'ADMIN')
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    rol = EXCLUDED.rol;

INSERT INTO usuarios (username, password, rol)
VALUES ('asesor_aleix', '$2a$10$ENb5KFHiHaplfD9u3y85r.Dk/O5wOuZzBHpoyGiMyLgnP3XkCZ9DO', 'ASESOR')
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    rol = EXCLUDED.rol;

INSERT INTO usuarios (username, password, rol)
VALUES ('asesor_maria', '$2a$10$ENb5KFHiHaplfD9u3y85r.Dk/O5wOuZzBHpoyGiMyLgnP3XkCZ9DO', 'ASESOR')
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    rol = EXCLUDED.rol;

-- ---------------------------------------------------------------------------
-- Clientes
-- ---------------------------------------------------------------------------
INSERT INTO clientes (nombre, email, perfil_riesgo, asesor_id)
SELECT 'Amancio Ortega', 'amancio.ortega@finanzas.com', 'CONSERVADOR', u.id
FROM usuarios u
WHERE u.username = 'asesor_aleix'
ON CONFLICT (email) DO NOTHING;

INSERT INTO clientes (nombre, email, perfil_riesgo, asesor_id)
SELECT 'Elon Musk', 'elon.musk@finanzas.com', 'AGRESIVO', u.id
FROM usuarios u
WHERE u.username = 'asesor_aleix'
ON CONFLICT (email) DO NOTHING;

INSERT INTO clientes (nombre, email, perfil_riesgo, asesor_id)
SELECT 'Jeff Bezos', 'jeff.bezos@finanzas.com', 'MODERADO', u.id
FROM usuarios u
WHERE u.username = 'asesor_maria'
ON CONFLICT (email) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Activos financieros (precios orientativos; Yahoo los actualizará)
-- ---------------------------------------------------------------------------
INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('AAPL', 'Apple Inc.', 175.00, 'USD')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('SAN.MC', 'Banco Santander', 4.25, 'EUR')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('BP.L', 'BP plc', 485.50, 'GBp')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('MSFT', 'Microsoft Corporation', 420.00, 'USD')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('NVDA', 'NVIDIA Corporation', 950.00, 'USD')
ON CONFLICT (ticker) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Transacciones iniciales
-- ---------------------------------------------------------------------------

-- Amancio Ortega: depósito 10.000 EUR
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, NULL, 'EUR', 'DEPOSITO', 1, 10000.00, TIMESTAMP '2024-06-01 09:00:00'
FROM clientes c
WHERE c.email = 'amancio.ortega@finanzas.com'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.tipo_operacion = 'DEPOSITO'
        AND t.moneda = 'EUR'
        AND t.precio_ejecucion = 10000.00
  );

-- Amancio Ortega: compra 10 AAPL
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, a.id, NULL, 'COMPRA', 10, 170.00, TIMESTAMP '2024-06-02 10:30:00'
FROM clientes c
CROSS JOIN activos_financieros a
WHERE c.email = 'amancio.ortega@finanzas.com'
  AND a.ticker = 'AAPL'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.activo_financiero_id = a.id
        AND t.tipo_operacion = 'COMPRA'
        AND t.cantidad = 10
  );

-- Elon Musk: depósito 50.000 USD
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, NULL, 'USD', 'DEPOSITO', 1, 50000.00, TIMESTAMP '2024-07-01 09:00:00'
FROM clientes c
WHERE c.email = 'elon.musk@finanzas.com'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.tipo_operacion = 'DEPOSITO'
        AND t.moneda = 'USD'
        AND t.precio_ejecucion = 50000.00
  );

-- Elon Musk: compra 50 NVDA
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, a.id, NULL, 'COMPRA', 50, 900.00, TIMESTAMP '2024-07-02 11:00:00'
FROM clientes c
CROSS JOIN activos_financieros a
WHERE c.email = 'elon.musk@finanzas.com'
  AND a.ticker = 'NVDA'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.activo_financiero_id = a.id
        AND t.tipo_operacion = 'COMPRA'
        AND t.cantidad = 50
  );

-- Jeff Bezos: depósito 20.000 EUR
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, NULL, 'EUR', 'DEPOSITO', 1, 20000.00, TIMESTAMP '2024-08-01 09:00:00'
FROM clientes c
WHERE c.email = 'jeff.bezos@finanzas.com'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.tipo_operacion = 'DEPOSITO'
        AND t.moneda = 'EUR'
        AND t.precio_ejecucion = 20000.00
  );

-- Jeff Bezos: compra 1000 SAN.MC
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, a.id, NULL, 'COMPRA', 1000, 4.20, TIMESTAMP '2024-08-02 10:15:00'
FROM clientes c
CROSS JOIN activos_financieros a
WHERE c.email = 'jeff.bezos@finanzas.com'
  AND a.ticker = 'SAN.MC'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.activo_financiero_id = a.id
        AND t.tipo_operacion = 'COMPRA'
        AND t.cantidad = 1000
  );
