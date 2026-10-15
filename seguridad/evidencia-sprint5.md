# Evidencia de seguridad — Sprint 5 (Wfuzz)

Fuzzing de HU-09 (estado del trámite) y HU-10 (naves por estado del trámite) con
`seguridad/correr-wfuzz-sprint5.sh`. Wfuzz 3.1.0 en contenedor Docker (Colima) contra la app
en `localhost:8080`.

> **Responsable.** Desde el Sprint 5, seguridad es de Abdiel. Esta corrida la hicieron
> Louis y Claude porque la computadora de Abdiel tuvo problemas. Si Abdiel puede, revisa
> los resultados antes del cierre.

**Hubo dos corridas el 13-10-2026:**

- **Previa**, sobre el código de `c858cb5`. Se repitió dos veces seguidas, con el mismo
  resultado. Encontró **SEC-08**.
- **Final (23:24)**, sobre la versión con las reglas de la PO y la corrección de SEC-08.
  Es la que va como evidencia del sprint: **0 respuestas 500**.

Las reglas y la corrección las implementaron Louis y Claude, porque el sprint se presentaba
al día siguiente.

## Resultado de la corrida final

| Prueba | Códigos | Veredicto |
|---|---|---|
| 1. HU-10: filtro por estado | 200×7, 400×21 | OK |
| 2. HU-09: ID de nave al leer el trámite | 200×1, 400×5, 403×1, 404×4 | OK: la nave ajena da 403 |
| 3. HU-09: ID de nave en el historial | 200×1, 400×5, 403×1, 404×4 | OK |
| 4. HU-09: ID de nave al cambiar el estado | 200×1, 400×1, 403×1, 404×3 | OK: la nave ajena da 403 y no cambia |
| 5. HU-09: campo `accion` | 400×13, 403×2, 409×7 | OK |
| 6a. HU-09: `observacion` (lectura y validación) | 400×4, 409×15 | OK: los dos payloads con carácter nulo ahora dan 400 |
| 6b. HU-09: `observacion` (guardado en la base) | 200×15, 400×4; historial 200 | **OK: SEC-08 corregido** |
| 7. Sin sesión | 401×5 | OK |
| 8. Registro de nave por rol | agente 201, DGMM 403, auditor 403 | **OK: issue #10 corregido** |
| 9. Escrituras con el trámite Desistido | documento 409, propietario 409, agente 409 | **OK: regla de la PO** |

**Vulnerabilidades encontradas en el sprint: 1 (SEC-08), corregida. Abiertas al cierre: 0**
de este sprint (siguen SEC-03 y SEC-05, de sprints anteriores).

La sección siguiente es la corrida **previa**, que se conserva como registro de cómo se
encontró SEC-08.

## Qué cambia respecto del Sprint 4

- **No depende de IDs fijos de la base.** El script crea sus naves y un segundo agente,
  validado con el correo que llega a Mailpit. La prueba de nave ajena funciona en cualquier
  base, también en una vacía.
- **Usa cuatro sesiones:** `agente@` (dueño), un segundo agente (ajeno), `revisor@` (DGMM) y
  `auditor@`.
- **Deja un resumen** con el conteo de códigos por prueba (`seguridad/reportes/s5-resumen.txt`)
  y no muestra los IDs de sesión.
- **Diccionarios nuevos:** `wordlist-estados.txt` (28), `wordlist-acciones.txt` (22) y
  `wordlist-observaciones.txt` (19).

## Resultado de la corrida previa (`c858cb5`)

| Prueba | Endpoint | Peticiones | Códigos | Veredicto |
|---|---|---:|---|---|
| 1. HU-10 — filtro por estado | `GET /api/naves?estado=` | 28 | 200×7, 400×21 | OK: sin 500 |
| 2. HU-09 — ID de nave al leer el trámite | `GET /api/naves/{id}/tramite` | 11 | 200×1, 400×5, 403×1, 404×4 | OK: la nave ajena da 403 |
| 3. HU-09 — ID de nave en el historial | `GET /api/naves/{id}/tramite/historial` | 11 | 200×1, 400×5, 403×1, 404×4 | OK: la nave ajena da 403 |
| 4. HU-09 — ID de nave al cambiar el estado | `POST /api/naves/{id}/tramite/estado` | 6 | 200×1, 400×1, 403×1, 404×3 | OK: la nave ajena da 403 y sigue en Solicitud presentada |
| 5. HU-09 — campo `accion` | `POST .../tramite/estado` | 22 | 400×13, 403×2, 409×7 | OK: sin 500 ni 200 |
| 6a. HU-09 — `observacion`: lectura y validación | `POST .../tramite/estado` | 19 | 400×2, 409×17 | OK: sin 500 |
| 6b. HU-09 — `observacion`: guardado en la base | `POST .../tramite/estado` (curl) | 19 | 200×15, 400×3, **500×1** | **SEC-08** |
| 7. Sin sesión | 4 GET + 1 POST | 5 | 401×5 | OK |
| 8. Registro de nave por rol | `POST /api/naves` | 3 | 201×3 | Pendiente: issue #10 (se espera agente 201, DGMM 403, auditor 403) |
| 9. Escrituras con el trámite Desistido | documento, propietario, agente | 3 | 201×3 | Pendiente: regla de la PO (se espera 409×3) |

**Vulnerabilidades encontradas: 1 (SEC-08).** Fuera de ese caso, ninguna petición produjo un
500, ninguna inyección se ejecutó y ningún usuario leyó ni cambió el trámite de una nave
ajena. Las pruebas 8 y 9 no son vulnerabilidades: documentan reglas que la PO pidió el
13-10-2026 y que todavía no existen. Ya hay issue (#10) y casos de Katalon (TC-38 y TC-39).

### Detalle de las pruebas 2 a 4 (control de acceso)

Con la sesión de `agente@navesitas.pa`:

| ID probado | Lectura (2 y 3) | Escritura (4) | Por qué |
|---|---|---|---|
| Nave propia | 200 | 200 | Es suya (en la 4, desiste su nave de prueba) |
| Nave del segundo agente | 403 | 403 | Ajena; después se verificó que sigue en `SOLICITUD_PRESENTADA` |
| 0, -1, 999999999, 2147483648 | 404 | 404 | No existen |
| 9223372036854775808, 1.5, abc, `'`, `1 OR 1=1` | 400 | 400 | No es un ID válido |

### Detalle de la prueba 5 (campo `accion`)

La DGMM actúa sobre una nave Desistida, así que ninguna acción puede cambiarla.

| Código | Payloads | Por qué |
|---|---|---|
| 400 | `PRESENTAR`, `MIGRACION`, `VOLAR`, `admitir`, `Aprobar`, inyecciones, JSON roto | Acción del sistema, inexistente o cuerpo ilegible |
| 403 | `SUBSANAR`, `DESISTIR` | Son acciones del agente |
| 409 | `ADMITIR`, `OBSERVAR`, `APROBAR`, `RECHAZAR` y variantes que se leen como `APROBAR` | Acción válida, pero el trámite ya terminó |

Las variantes que terminan en 409 son `APROBAR`, ` APROBAR` (con espacio), `APROBAR\u0000`
y la clave duplicada `{"accion":"","accion":"APROBAR"}`. La lectura del JSON las convierte en
`APROBAR` y luego pasan por la misma autorización y la misma máquina de estados: **no es
vulnerabilidad**.

## SEC-08 — Carácter nulo en la observación del trámite (500) — **CORREGIDO**

| | |
|---|---|
| Estado | **Corregido** el 13-10-2026. Verificado en la corrida final de Wfuzz y en `TramiteIntegrationTest` / `ManejadorGlobalDeErroresTest` |
| Severidad | Baja: no expone datos ni salta controles, pero devuelve 500 y deja una traza en el log |
| Endpoint | `POST /api/naves/{id}/tramite/estado` (observar, rechazar, subsanar, etc.) |
| Payload | `{"accion":"OBSERVAR","observacion":"nulo\u0000intermedio"}` |
| Esperado | 400: "la observación tiene caracteres no permitidos" |
| Obtenido | **500**. En el log: `ERROR: invalid byte sequence for encoding "UTF8": 0x00`, al insertar en `historial_estados` (`DataIntegrityViolationException` sin manejar) |

PostgreSQL no acepta el byte `0x00` en columnas de texto. `TramiteService.limpiar` hace
`trim()`, que quita el nulo solo en los extremos: `"\u0000"` se vuelve vacío (400 correcto),
pero un nulo en medio del texto llega a la base. El trámite no cambia (la transacción se
revierte), así que no hay inconsistencia de datos.

**Mismo origen, otros campos.** Con un nulo en `lugarConstruccion` (HU-03), la respuesta no es
500 sino **409 "Nombre no disponible"**: `NaveService.registrar` atrapa cualquier
`DataIntegrityViolationException` y supone que es el nombre duplicado. El mensaje es
engañoso. `nombre` sí da 400, porque tiene validación de formato.

**Corrección aplicada:**

- **Un solo lugar para todos los DTO** (`config/TextoSinNulosConfig`): el lector de cadenas
  de Jackson rechaza cualquier texto con `U+0000`. El manejador global lo convierte en
  **400** "El texto contiene caracteres no permitidos."
- **Red de seguridad para lo que no llega por JSON** (formularios, multipart): si PostgreSQL
  rechaza el byte (SQLState `22021`), la respuesta también es **400**. Cualquier otro error
  de integridad sigue siendo 500.
- **`NaveService.registrar`** responde "Nombre no disponible" (409) solo cuando choca
  `uk_nave_nombre_normalizado`. Con un carácter nulo en `lugarConstruccion` ahora da 400.

## Observaciones informativas (no se cuentan como vulnerabilidad)

- **El filtro de estado acepta espacios alrededor** (`" EN_REVISION "` → 200, igual que
  `EN_REVISION`): Spring recorta el valor antes de convertirlo. Las minúsculas sí dan 400.
- **El JSON se lee con tolerancia:** lo describe la prueba 5. Una clave duplicada se queda con
  el último valor.
- **403 frente a 404** (nave ajena frente a inexistente): es la misma decisión deliberada de
  `AccesoNave` documentada en el Sprint 4. HU-09 la reutiliza.
- **SEC-05 (CSRF)** sigue abierto. `/api/**` ignora el token CSRF, por eso los POST del script
  funcionan sin él. No se volvió a probar en este sprint.
