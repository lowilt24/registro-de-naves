-- Alternativa al disco para alojamientos con sistema de archivos efímero.
-- Los permisos siguen comprobándose en DocumentoService antes de leer el contenido.
CREATE TABLE archivos_documentales (
    ruta VARCHAR(180) PRIMARY KEY,
    nave_id BIGINT NOT NULL REFERENCES nave(id),
    contenido BYTEA NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (octet_length(contenido) <= 10485760)
);
CREATE INDEX idx_archivos_documentales_nave ON archivos_documentales(nave_id);
