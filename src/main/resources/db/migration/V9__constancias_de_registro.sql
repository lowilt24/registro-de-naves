-- HU-15: una constancia inmutable por nave. PDF y hash se guardan juntos.
CREATE TABLE constancias_registro (
    nave_id BIGINT PRIMARY KEY REFERENCES nave(id),
    aprobacion_id BIGINT NOT NULL REFERENCES historial_estados(id),
    contenido BYTEA NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    generado_en TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_constancia_hash CHECK (length(hash_sha256) = 64)
);

-- Acto de registro: no se altera el tramite ni su historial de decisiones.
UPDATE nave SET estado = 'REGISTRADA'
 WHERE estado = 'ABANDERADA'
   AND EXISTS (SELECT 1 FROM tramites t WHERE t.nave_id = nave.id AND t.estado = 'APROBADA');
