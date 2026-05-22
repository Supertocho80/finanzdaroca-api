-- DML idempotente: no duplica datos en reinicios (ON CONFLICT / WHERE NOT EXISTS).

-- Usuario asesor (contraseña BCrypt: secret123)
INSERT INTO usuarios (username, password, rol)
VALUES ('asesor_aleix', '$2a$10$w4rU83t/x/P0tI13809u/unhP5s892k3dFIfwXnJ/5q.Q5G32o5K2', 'ASESOR')
ON CONFLICT (username) DO NOTHING;

-- Cliente asignado al asesor
INSERT INTO clientes (nombre, email, perfil_riesgo, asesor_id)
SELECT 'Amancio Ortega', 'amancio.ortega@finanzas.com', 'CONSERVADOR', u.id
FROM usuarios u
WHERE u.username = 'asesor_aleix'
ON CONFLICT (email) DO NOTHING;

-- Activos financieros (precios orientativos; Yahoo los actualizará)
INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('AAPL', 'Apple Inc.', 175.00, 'USD')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('SAN.MC', 'Banco Santander', 4.25, 'EUR')
ON CONFLICT (ticker) DO NOTHING;

INSERT INTO activos_financieros (ticker, nombre, precio_mercado, moneda)
VALUES ('BP.L', 'BP plc', 485.50, 'GBp')
ON CONFLICT (ticker) DO NOTHING;

-- Historial de transacciones (idempotente por clave de negocio)
INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, NULL, 'EUR', 'DEPOSITO', 1, 10000.00, TIMESTAMP '2024-06-01 09:00:00'
FROM clientes c
WHERE c.email = 'amancio.ortega@finanzas.com'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.tipo_operacion = 'DEPOSITO'
        AND t.precio_ejecucion = 10000.00
  );

INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, a.id, NULL, 'COMPRA', 10, 170.00, TIMESTAMP '2024-06-02 10:30:00'
FROM clientes c, activos_financieros a
WHERE c.email = 'amancio.ortega@finanzas.com'
  AND a.ticker = 'AAPL'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.activo_financiero_id = a.id
        AND t.tipo_operacion = 'COMPRA'
        AND t.cantidad = 10
        AND t.precio_ejecucion = 170.00
  );

INSERT INTO transacciones (cliente_id, activo_financiero_id, moneda, tipo_operacion, cantidad, precio_ejecucion, fecha)
SELECT c.id, a.id, NULL, 'COMPRA', 200, 475.00, TIMESTAMP '2024-06-03 11:15:00'
FROM clientes c, activos_financieros a
WHERE c.email = 'amancio.ortega@finanzas.com'
  AND a.ticker = 'BP.L'
  AND NOT EXISTS (
      SELECT 1 FROM transacciones t
      WHERE t.cliente_id = c.id
        AND t.activo_financiero_id = a.id
        AND t.tipo_operacion = 'COMPRA'
        AND t.cantidad = 200
        AND t.precio_ejecucion = 475.00
  );
