-- HU-14: administrador para el entorno de demostracion.
-- Usa la misma contrasena de las cuentas demo: Navesitas2026*

INSERT INTO usuario (
    correo,
    password_hash,
    nombre_completo,
    rol,
    correo_validado,
    activo
)
VALUES (
    'admin@navesitas.pa',
    '$2a$10$fsjHC3ZBRgiKDO8cUEaV5udNHU20wq5NUGjBy8iiIj6dHT4ZteW3.',
    'Administrador de Prueba',
    'ADMINISTRADOR',
    true,
    true
);