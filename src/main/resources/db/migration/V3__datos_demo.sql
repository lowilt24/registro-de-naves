-- Datos minimos para poder demostrar el sprint sin cargar todo a mano.
-- Las contrasenas son BCrypt de "Navesitas2026*".

INSERT INTO usuario (correo, password_hash, nombre_completo, rol) VALUES
  ('agente@navesitas.pa', '$2a$10$fsjHC3ZBRgiKDO8cUEaV5udNHU20wq5NUGjBy8iiIj6dHT4ZteW3.', 'Agente Naviero de Prueba', 'AGENTE_NAVIERO'),
  ('revisor@navesitas.pa', '$2a$10$fsjHC3ZBRgiKDO8cUEaV5udNHU20wq5NUGjBy8iiIj6dHT4ZteW3.', 'Funcionario Revisor DGMM', 'FUNCIONARIO_DGMM');

INSERT INTO propietario (nombre, identificacion, nacionalidad) VALUES
  ('Naviera Istmo S.A.', 'RUC-155712345-2-2024', 'Panamena'),
  ('Caribbean Shipping Ltd.', 'REG-BVI-889201', 'Britanica');

INSERT INTO agente_residente (nombre, idoneidad) VALUES
  ('Bufete Maritimo Balboa', 'IDN-4521'),
  ('Consultores Navales del Pacifico', 'IDN-7788');

-- Una nave ya registrada: sirve para demostrar el caso negativo de HU-04.
INSERT INTO nave (
    nombre, nombre_normalizado, tipo, servicio,
    tonelaje_bruto, tonelaje_neto, eslora, manga, puntal,
    anio_construccion, lugar_construccion, material_casco,
    tipo_propulsion, potencia_kw, estado,
    propietario_id, agente_residente_id
) VALUES (
    'Estrella del Istmo', 'ESTRELLA DEL ISTMO', 'CARGA', 'INTERNACIONAL',
    18450.00, 9200.00, 182.50, 27.80, 15.20,
    2019, 'Astillero Hyundai Mipo, Corea del Sur', 'Acero',
    'Motor diesel de dos tiempos', 9500.00, 'REGISTRADA',
    1, 1
);
