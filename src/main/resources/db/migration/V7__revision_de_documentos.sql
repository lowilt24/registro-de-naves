-- =====================================================================
-- Sprint 6 — HU-12: la DGMM revisa cada documento (aprobar u observar).
--
-- Diseno: DISENO-V7.md. Revision de QA: qa/evidencias/sprint-6/
-- revision-diseno-v7.md (23-10-2026: los 224 documentos de la base de
-- desarrollo estan en PENDIENTE, asi que los CHECK entran sin rellenar).
--
-- Solo cambia la tabla documentos. No hay tabla de revisiones: cada
-- version del documento guarda su propia decision, y una correccion entra
-- como version nueva (HU-07). El historial de revisiones son las versiones.
--
-- nave.estado no tiene CHECK, asi que ABANDERADA y RECHAZADA (D5) ya
-- caben. tramites e historial_estados (V6) no se tocan.
-- =====================================================================

ALTER TABLE documentos
    ADD COLUMN observacion   VARCHAR(1000),
    ADD COLUMN revisado_por  BIGINT,
    ADD COLUMN revisado_en   TIMESTAMPTZ;

ALTER TABLE documentos
    ADD CONSTRAINT fk_documentos_revisor
        FOREIGN KEY (revisado_por) REFERENCES usuario (id),

    -- Pendiente: nadie lo ha revisado. Aprobado u observado: se sabe quien
    -- y cuando.
    ADD CONSTRAINT ck_documentos_revision CHECK (
        (estado = 'PENDIENTE' AND revisado_por IS NULL AND revisado_en IS NULL)
     OR (estado IN ('APROBADO', 'OBSERVADO') AND revisado_por IS NOT NULL AND revisado_en IS NOT NULL)),

    -- D2: observar exige decir por que. La base tambien lo sostiene.
    -- (Al aprobar, la observacion es una nota opcional: observacion 5 de QA.)
    ADD CONSTRAINT ck_documentos_observacion CHECK (
        estado <> 'OBSERVADO' OR (observacion IS NOT NULL AND length(trim(observacion)) > 0));

-- Observacion 9 de QA: indice para la llave foranea.
CREATE INDEX idx_documentos_revisado_por ON documentos (revisado_por);
