-- =====================================================================
--  EnZona · V3 · estado_pago admite PENDIENTE (OXXO / SPEI)
--  ALTER TYPE ... ADD VALUE se ejecuta FUERA de transacción (ver el archivo
--  V3__estado_pago_pendiente.sql.conf): un valor nuevo de un enum no puede
--  usarse en la misma transacción que lo crea.
-- =====================================================================
ALTER TYPE estado_pago ADD VALUE IF NOT EXISTS 'PENDIENTE';
