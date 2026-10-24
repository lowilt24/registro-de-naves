# Diseño de la V7 — revisión de documentos (HU-12)

> **Estado (24-10-2026): implementado.** La V7 está en
> `db/migration/V7__revision_de_documentos.sql`. Abajo, al final, la
> respuesta a cada observación de la revisión de QA
> (`qa/evidencias/sprint-6/revision-diseno-v7.md`). Lo que quedó fijado
> manda sobre el texto original de este diseño: en particular, el **orden de
> los errores** es el del plan (401 → 404 → 403 → 409 → 400), no el que se
> proponía aquí. El contrato definitivo está en `CONTRATO-DOCUMENTOS.md`,
> sección 4.

**Para que Louis lo revise antes de que la migración exista.** Una vez que la
V7 llegue a `main` ya no se puede editar: cualquier corrección iría en una V8.
Por eso este documento va primero y el archivo `V7__revision_de_documentos.sql`
se escribe el lunes 19 con lo que salga de aquí.

Reglas de la PO que aplica (Madeleine aceptó D1–D8 el 18-10-2026):

| # | Regla | Dónde vive |
|---|---|---|
| D2 | La DGMM revisa un documento solo con el trámite **En revisión**. Observar exige observación (vacía: 400) | Servicio |
| D3 | Observar un documento **no** mueve el trámite. La DGMM observa el trámite aparte | Servicio (no se toca la máquina) |
| D4 | Aprobar el trámite exige los cuatro obligatorios **APROBADOS**. Si no, 409 con la lista de faltantes, en el formato del 409 de Admitir (`faltantes: [...]`) | Servicio |
| D5 | Trámite Aprobado → nave `ABANDERADA`; Rechazado → nave `RECHAZADA`, en la misma transacción | Servicio |

## Qué cambia en la base

Solo la tabla `documentos`. No hace falta tabla nueva:

- Cada carga nueva ya crea una **versión** nueva en `PENDIENTE` (HU-07), y la
  anterior queda con `vigente = false`.
- Se propone que solo se pueda revisar un documento **vigente y PENDIENTE**.
  Una vez aprobado u observado, esa versión ya no cambia. Si la DGMM lo
  observa, el agente carga una versión nueva y esa es la que se revisa.
- Así, el historial de revisiones **son** las versiones, y cada versión
  guarda su propia decisión.

`nave.estado` no tiene `CHECK`, así que `ABANDERADA` y `RECHAZADA` ya caben.
`tramites` e `historial_estados` (V6) no se tocan.

## SQL propuesto

```sql
-- V7__revision_de_documentos.sql  (BORRADOR: no está en db/migration todavía)

ALTER TABLE documentos
    ADD COLUMN observacion   VARCHAR(1000),
    ADD COLUMN revisado_por  BIGINT,
    ADD COLUMN revisado_en   TIMESTAMPTZ;

ALTER TABLE documentos
    ADD CONSTRAINT fk_documentos_revisor
        FOREIGN KEY (revisado_por) REFERENCES usuario (id),

    -- Pendiente: nadie lo ha revisado. Aprobado u observado: se sabe quién y cuándo.
    ADD CONSTRAINT ck_documentos_revision CHECK (
        (estado = 'PENDIENTE' AND revisado_por IS NULL AND revisado_en IS NULL)
     OR (estado IN ('APROBADO', 'OBSERVADO') AND revisado_por IS NOT NULL AND revisado_en IS NOT NULL)),

    -- D2: observar exige decir por qué. La base también lo sostiene.
    ADD CONSTRAINT ck_documentos_observacion CHECK (
        estado <> 'OBSERVADO' OR (observacion IS NOT NULL AND length(trim(observacion)) > 0));
```

Las filas que ya existen están todas en `PENDIENTE` (nadie podía revisar
antes de HU-12), así que cumplen los dos `CHECK` sin backfill.

**A revisar, Louis:** si en tu base de desarrollo hay algún documento en
`APROBADO` u `OBSERVADO` puesto a mano para probar Katalon, el segundo
`ALTER` falla. Compruébalo antes con:

```sql
SELECT estado, count(*) FROM documentos GROUP BY estado;
```

## Endpoint

`POST /api/naves/{naveId}/documentos/{documentoId}/revision`

```json
{ "decision": "OBSERVAR", "observacion": "El ITC está vencido." }
```

`decision`: `APROBAR` u `OBSERVAR`. Responde 200 con el documento ya revisado
(los campos de HU-08 más `observacion`, `revisadoPor` y `revisadoEn`).

Orden de los errores (regla del equipo 401 → 400 → 404 → 403 → 409):

| Código | Cuándo |
|---|---|
| 401 | Sin sesión |
| 400 | `decision` vacía o inventada; observar sin observación; observación de más de 1000 caracteres; carácter nulo (SEC-08) |
| 404 | La nave no existe, o el documento no es de **esa** nave (mismo control de IDOR que la descarga de HU-08) |
| 403 | No es FUNCIONARIO_DGMM (el agente y el auditor no revisan) |
| 409 | El trámite no está En revisión (D2); el documento no es la versión vigente; el documento ya fue revisado |

**Cambio de orden respecto a HU-09:** en el Sprint 5, "falta la observación"
salía **después** del 409. Con la regla nueva del equipo sale **antes** de
todo, como 400. Para HU-12 se hace con el orden nuevo. HU-09 se deja como
está para no romper TS05; si el equipo quiere igualarlo, es un cambio
pequeño en `TramiteService.cambiarEstado` y en dos casos de TS05.

## Cambios en el trámite (D4 y D5)

- **Aprobar el trámite**: antes de la transición se exige que los cuatro
  obligatorios vigentes estén en `APROBADO`. Si no, 409 con
  `{ "mensaje": "...", "faltantes": ["ITC", "SMC"] }`, igual que Admitir.
- **Aprobado** → `nave.estado = ABANDERADA`. **Rechazado** →
  `nave.estado = RECHAZADA`. En la misma transacción que el cambio del
  trámite: si una falla, no queda ninguna.
- `TRAMITES` sigue siendo la única fuente del estado del trámite (D4 del
  Sprint 5). `nave.estado` solo se escribe aquí, nunca se lee para decidir.

## Pantalla

Cámara 5 del expediente:

- Cada documento muestra su observación debajo del estado, si la tiene.
- Para la DGMM, con el trámite En revisión, cada documento vigente y
  pendiente tiene dos botones: **Aprobar** y **Observar** (este pide la
  observación).
- Hoy la DGMM no ve la cámara 5 (`vistaDeConsulta` la oculta). Con HU-12 se
  le muestra en modo revisión: ve el listado y descarga, pero no carga.

## Respuesta a la revisión de QA (Louis, 23-10-2026)

| # | Observación | Qué se hizo |
|---|---|---|
| 1 | Dos órdenes de errores distintos | Se usa el del criterio de HU-12 aceptado por la PO: **401 → 404 → 403 → 409 → 400**, igual que HU-09. Para lograrlo, `decision` y `observacion` llegan como texto y las valida el servicio al final. Solo un JSON roto (o el carácter nulo de SEC-08) da 400 antes, porque no se puede leer. TC-46 queda tal como está |
| 2 | La DGMM recibía 403 al listar y descargar | `AccesoNave.exigirLectura`: la DGMM y el auditor leen la ficha, listan y descargan documentos de cualquier nave. Escribir sigue pidiendo `exigirPropia`. `GET /api/naves/{id}` usa la misma regla, sin duplicarla |
| 3 | `TramiteIntegrationTest.observar()` violaba los `CHECK` | El método ahora pone observación, revisor y fecha (`Documento#marcarRevisado`). Dos pruebas de esa clase y dos de `DocumentoIntegrationTest` se ajustaron a D4 y a la observación 2 |
| 4 | Dos revisiones simultáneas se pisaban | `UPDATE … WHERE id = ? AND vigente AND estado = 'PENDIENTE'` (`DocumentoRepository#revisar`). Si no cambia ninguna fila, 409. No se usó `@Version` porque `version` ya es el número de versión del documento. Prueba: `unaSegundaRevisionSimultaneaNoPisaLaPrimera` |
| 5 | Observación al aprobar sin definir | Se guarda como nota opcional |
| 6 | Formato de `revisadoPor` | El nombre del funcionario, no el correo |
| 7 | D5 no cubría Desistida | La nave queda `REGISTRADA`. Prueba: `desistirDejaLaNaveRegistrada`. Falta que la PO lo confirme |
| 8 | Reemplazar un documento aprobado | La versión nueva entra `PENDIENTE` y D4 vuelve a bloquear la aprobación. Prueba: `reemplazarUnAprobadoVuelveABloquearYRechazarMarcaLaNave` (espejo de TC-49) |
| 9 | Menores | Índice `idx_documentos_revisado_por`. El 409 de D4 dice "sin aprobar". El #13 se corrigió en `NaveService`, sin migración |

Los campos son los que esperan TC_HU12_01 a 06 (`decision`, `observacion`,
`revisadoPor`, `revisadoEn` y `faltantes`), así que se pueden activar sin
cambios.
