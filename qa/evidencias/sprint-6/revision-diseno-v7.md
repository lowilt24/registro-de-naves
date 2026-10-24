# Revisión de QA de `DISENO-V7.md` (HU-12)

- **Revisó:** Louis (QA), con Claude, el 23-10-2026, sobre `sprint-6` en `340f374`.
- **Para:** Ismael, antes de escribir `V7__revision_de_documentos.sql`. Una vez en `main`, la V7 no se puede editar.
- **Alcance:** el SQL propuesto, el endpoint de revisión y los cambios de D4 y D5. Se revisaron contra el código, la base de desarrollo y `PLAN-SPRINT6.md`.

## Comprobación previa en la base

La pidió el diseño: si ya hubiera documentos en `APROBADO` u `OBSERVADO`, los `CHECK` de la V7 fallarían.

```sql
SELECT estado, vigente, count(*) FROM documentos GROUP BY estado, vigente;
--  PENDIENTE | f |  14
--  PENDIENTE | t | 210

SELECT estado, count(*) FROM nave GROUP BY estado;
--  REGISTRADA | 302

SELECT conname FROM pg_constraint WHERE conrelid = 'nave'::regclass AND contype = 'c';
--  (ninguna)
```

- Los 224 documentos están en `PENDIENTE`. Los dos `CHECK` se aplican sin rellenar datos.
- `nave.estado` no tiene `CHECK`, así que `ABANDERADA` y `RECHAZADA` ya caben.
- Al 23-10, la V7 todavía no existe en `db/migration`, que llega hasta la V6.

## Observaciones, de mayor a menor importancia

| # | Observación | Evidencia | Qué hacer |
|---|---|---|---|
| 1 | **Los dos documentos dan un orden de errores distinto.** El diseño dice 401 → 400 → 404 → 403 → 409 y lo llama "regla del equipo". El criterio de HU-12 en el plan, aceptado por la PO, dice 401 → 404 → 403 → 409 → 400, "igual que en HU-09" | `PLAN-SPRINT6.md:62` y `DISENO-V7.md:78` | Elegir uno antes de programar. Hasta entonces vale el del plan. Define TC-46 |
| 2 | **Hoy la DGMM no puede listar ni descargar documentos (403).** El diseño solo cambia la pantalla de la cámara 5, no el backend | `DocumentoService.java:150` y `:169` usan `AccesoNave.exigirPropia`, que solo deja pasar al dueño | Abrir el listado y la descarga a `FUNCIONARIO_DGMM` y decidir si también al `AUDITOR`. Si no, la DGMM revisa documentos que no puede abrir |
| 3 | **Se rompe una prueba que ya existe.** `TramiteIntegrationTest.observar()` pone `OBSERVADO` sin `revisado_por` ni observación, y con la V7 viola los dos `CHECK` | `TramiteIntegrationTest.java:519` | Ajustar ese método en el mismo commit que la V7, o que use el endpoint nuevo |
| 4 | **Dos revisiones simultáneas se pisan.** `Documento` no tiene `@Version`: `Tramite` sí. Si dos funcionarios revisan el mismo documento, gana el último y nadie se entera | `@Version` solo está en `Tramite.java:41` | `@Version` o `UPDATE … WHERE estado = 'PENDIENTE' AND vigente`. Si no se modifica ninguna fila, 409 |
| 5 | **La observación al aprobar no está definida.** El `CHECK` permite que un documento `APROBADO` tenga observación | `DISENO-V7.md:47-53` | Decidir si se guarda, se ignora o da 400. Propuesta: guardarla como nota opcional |
| 6 | **El formato de `revisadoPor` en la respuesta no está definido.** Si es el correo, el agente vería el correo del funcionario | `DISENO-V7.md:76` | Devolver el nombre, no el correo |
| 7 | **D5 no cubre Desistida:** solo dice qué pasa con la nave en Aprobado y Rechazado | `DISENO-V7.md:99-101` | Confirmarlo con la PO. Propuesta: la nave queda en `REGISTRADA` |
| 8 | **Con el trámite En revisión, el agente puede reemplazar un documento ya `APROBADO`.** La versión nueva entra como `PENDIENTE` y D4 la bloquea, así que no hay agujero | Flujo de HU-07 y `exigirExpedienteAbierto` | Fijarlo con una prueba de integración, que ya cubre TC-49 |
| 9 | Menores | — | Ver abajo |

**Menores (observación 9):**
- `revisado_por` no tiene índice. Es opcional, porque no se borran usuarios.
- En el 409 de D4, el mensaje debería decir "sin aprobar", no "faltan": un documento `PENDIENTE` existe, solo no está aprobado.
- El bug #13 no conviene mezclarlo con la V7. Se corrige en el servicio, sin migración.

## Lo que está bien

- El historial de revisiones son las versiones del documento, así que no hace falta una tabla nueva.
- La V6 no se toca.
- Un documento de otra nave da 404, con el mismo control que la descarga de HU-08.
- D4 cuenta solo las versiones vigentes.
- D5 se aplica en la misma transacción que el cambio del trámite.
- La observación admite hasta 1000 caracteres, igual que `historial_estados.observacion` y que `@Size` en `TramiteDtos`.
  - La validación en Java (`trim().isEmpty()`) es más estricta que el `trim()` de PostgreSQL. Así, todo lo que pasa la validación del servicio también cumple `ck_documentos_observacion`.

## Casos de Katalon preparados (TS06, `isRun=false`)

Cada caso provoca un solo error a la vez, así que no depende del orden que se elija. La única excepción es el bloque "orden" de TC-46, escrito con el orden del plan.

| Caso | TC | Qué cubre |
|---|---|---|
| `TC_HU12_01_Aprobar_Documento` | TC-44 | La DGMM aprueba un documento vigente y pendiente (200); revisarlo otra vez da 409 |
| `TC_HU12_02_Observar_Y_Nueva_Version` | TC-45 | Observar exige observación (400), con un límite de 1000. D3: el trámite no se mueve. La versión nueva entra `PENDIENTE` y la anterior conserva su observación; revisar la anterior da 409 |
| `TC_HU12_03_Acceso_Y_Errores` | TC-46 | 401, 403 (agente y auditor), 404 (nave inexistente y documento de otra nave), 400 (decisión inventada y carácter nulo) y el orden |
| `TC_HU12_04_Tramite_No_En_Revision` | TC-47 | D2: con el trámite en Solicitud presentada o en Observada, revisar da 409 |
| `TC_HU12_05_Aprobar_Tramite_Exige_Aprobados` | TC-48 | D4: aprobar con un documento sin aprobar da 409 con `faltantes`. Con los cuatro aprobados, 200 y la nave pasa a `ABANDERADA` (D5) |
| `TC_HU12_06_Rechazo_Y_Reemplazo` | TC-49 | D5: al rechazar, la nave pasa a `RECHAZADA`. Observación 8: un documento aprobado que se reemplaza vuelve a bloquear la aprobación |

Se activan cuando el código de HU-12 esté en `sprint-6`. Antes hay que ajustar los nombres de los campos (`observacion`, `revisadoPor` y `revisadoEn`), si cambian respecto del diseño.
