-- =====================================================================
-- Arreglo de MIG-01 (hallazgo abierto del Sprint 3).
--
-- El problema: la V4 asigna las naves sin dueno al usuario
-- agente@navesitas.pa y despues vuelve obligatoria la columna
-- registrado_por_id. Si ese usuario no existe (base sin datos de demo, o
-- alguien lo borro), el UPDATE deja los nulos donde estaban, el
-- SET NOT NULL falla y la V4 entera se revierte.
--
-- Por que va aqui y no en la V5: la V5 corre despues de la V4, asi que
-- llegaria tarde. Y la V4 no se puede editar: las bases que ya la
-- aplicaron guardan su checksum y Flyway se negaria a arrancar. Esta
-- migracion tiene version 3.1, de modo que en una base que todavia esta
-- en la V3 corre justo antes de la V4 y le deja el camino libre.
--
-- En las bases que ya pasaron la V4 tambien corre (por eso
-- spring.flyway.out-of-order=true) pero no hace nada: ahi ya no hay naves
-- sin dueno. Es idempotente.
-- =====================================================================

-- Si hay naves huerfanas y no existe el agente de demo, se crea un
-- dueno de sistema. Queda inactivo y con una contrasena imposible: no
-- puede iniciar sesion, solo sostiene la llave foranea hasta que un
-- administrador reasigne esas naves.
INSERT INTO usuario (correo, password_hash, nombre_completo, rol, activo)
SELECT 'sistema@navesitas.pa', '!sin-acceso', 'Dueno de sistema (migracion MIG-01)', 'ADMINISTRADOR', FALSE
 WHERE EXISTS     (SELECT 1 FROM nave    WHERE registrado_por_id IS NULL)
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'agente@navesitas.pa')
   AND NOT EXISTS (SELECT 1 FROM usuario WHERE correo = 'sistema@navesitas.pa');

-- Asignar las huerfanas: al agente de demo si existe, si no al de sistema.
-- Despues de esto, el UPDATE de la V4 no encuentra nulos y su
-- SET NOT NULL pasa.
UPDATE nave
   SET registrado_por_id = COALESCE(
         (SELECT id FROM usuario WHERE correo = 'agente@navesitas.pa'),
         (SELECT id FROM usuario WHERE correo = 'sistema@navesitas.pa'))
 WHERE registrado_por_id IS NULL;
