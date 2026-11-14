-- Identificador público aleatorio, independiente de los ids correlativos.
ALTER TABLE pp_emisiones ADD COLUMN verificacion_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE pp_emisiones ADD CONSTRAINT uq_pp_verificacion UNIQUE (verificacion_id);
-- Conserva el PDF previo y su hash al presentar una nueva versión del diseño.
ALTER TABLE pp_emisiones ADD COLUMN contenido_anterior BYTEA;
ALTER TABLE pp_emisiones ADD COLUMN hash_anterior VARCHAR(64);
ALTER TABLE pp_emisiones ADD COLUMN pdf_version INTEGER NOT NULL DEFAULT 1;
