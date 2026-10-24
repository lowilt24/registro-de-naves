# Evidencia de calidad — Sprint 6

HU-11 (bandeja de trámites de la DGMM) y HU-12 (revisión de documentos), sobre la rama
`sprint-6`:

- **`9f8bdaf` (Abdiel) y `2200330` (Ismael):** SEC-05, protección CSRF.
- **`c8d5365` (Ismael):** HU-11.
- **`8641ccb` (Ismael):** HU-12, con la migración V7.
- **`78ae4c8` (Ismael):** corrección del issue #13.
- **QA (Louis y Claude):** `b93ebab`, `bbccc68`, `340f374` y `c68ea55`, más los ajustes de
  este commit.

**Estado: parcial (24-10-2026).** Maven, cobertura y Katalon están completos. Faltan Wfuzz
del Sprint 6 (Abdiel), la revisión manual de las pantallas, LOC/ELOC y el #12 (sección 7).

## 1. Migración V7 (24-10-2026, base de desarrollo de Louis)

La revisión previa del diseño está en `revision-diseno-v7.md`. La V7 se aplicó al correr
`./mvnw verify` (`flyway_schema_history` versión 7, `success = t`):

| Comprobación | Resultado |
|---|---|
| Documentos | 252, todos en `PENDIENTE`: los `CHECK` de la V7 entran sin rellenar datos |
| Naves | 309, todas en `REGISTRADA` |

## 2. Pruebas Maven (JUnit)

`./mvnw verify` → **253/253 en verde**, `BUILD SUCCESS` (24-10-2026, sobre `78ae4c8`).

| Clase | Pruebas | Novedad del Sprint 6 |
|---|---|---|
| `DocumentoRevisionIntegrationTest` | 14 | Nueva (Ismael, HU-12): espejo de TC-44 a TC-49 |
| `BandejaIntegrationTest` | 14 | Nueva (Ismael, HU-11), con 4 de QA |
| `RegistroUsuarioIntegrationTest` | 9 | Nueva (QA, T5) |
| `CsrfIntegrationTest` | 6 | Nueva (Ismael, SEC-05) |
| `AutorizacionTramiteTest` | 48 | +1: solo la DGMM revisa documentos |
| `TramiteIntegrationTest` | 29 | 2 ajustadas a D4 |
| `DocumentoIntegrationTest` | 18 | 2 ajustadas: la DGMM y el auditor leen documentos |
| `NaveIntegrationTest` | 14 | +2 de QA (T5) y +1 del #13 |
| `PropietarioIntegrationTest` | 10 | +1 de QA (T5) |
| Resto | 91 | — |

## 3. Cobertura (JaCoCo)

| | Cierre del Sprint 5 (`qa/metricas/base-sprint-5.md`) | 24-10-2026 |
|---|---|---|
| Línea | 91.1 % | **93.3 %** |
| Rama | 81.2 % | **87.2 %** |

Cobertura de rama por paquete: `common` 100 %, `tramite` 92.6 %, `persona` 91.7 %,
`documento` 84.4 %, `config` 81.2 %, `usuario` 79.5 %, `nave` 73.5 %.

Ramas que no se alcanzan con pruebas normales:

- `DocumentoService.revisar`, cuando el `UPDATE` no cambia filas: solo se llega con dos
  revisiones simultáneas reales. `unaSegundaRevisionSimultaneaNoPisaLaPrimera` prueba el
  repositorio, no ese camino del servicio.
- `nave`: el resto de sus ramas necesita una carrera de concurrencia.

## 4. Katalon

Louis corrió cada suite dos veces en Katalon Studio el 24-10-2026, entre las 12:46 y las
12:51. Las dos rondas dieron el mismo resultado; aquí va la segunda.

| Suite | Casos | Resultado | Archivos |
|---|---|---|---|
| TS01_Sprint1 | 5 | 5/5 | `TS01_Sprint1-resumen.csv`, `TS01_Sprint1-JUnit.xml` |
| TS02_Sprint2 | 5 | 5/5 | `TS02_Sprint2-*` |
| TS03_Sprint3 | 10 | 10/10 | `TS03_Sprint3-*` |
| TS04_Sprint4 | 8 | 8/8 | `TS04_Sprint4-*` |
| TS05_Sprint5 | 15 | 15/15 | `TS05_Sprint5-*` |
| **TS06_Sprint6** | **10** | **10/10** | `TS06_Sprint6-*` |
| **Total** | **53** | **53/53** | |

TS06 tiene HU-11 (TC-40 a TC-43) y HU-12 (TC-44 a TC-49). Los de HU-12 se escribieron antes
que el código (`c68ea55`, con `isRun=false`). Con HU-12 en el repo se activaron sin cambiar
ningún script.

Los reportes están depurados con el mismo formato que en los Sprints 4 y 5: el CSV solo trae
la fila de la suite y una por caso, y el JUnit va sin `system-out`, `system-err` ni
`properties`. Se comprobó que no quedan `JSESSIONID`, `XSRF-TOKEN` ni contraseñas.

### Casos ajustados en este sprint

| Caso | Cambio | Motivo |
|---|---|---|
| Los 45 scripts | Usan `Http.enviar` y `Http.sesion` (`b93ebab`) | SEC-05: los POST, PUT y DELETE llevan el token CSRF |
| TC-31 (`TC_HU09_07`) | La DGMM aprueba los 4 documentos antes de aprobar el trámite; la nave queda `ABANDERADA` | HU-12, D4 y D5 (antes, P6: la nave no cambiaba) |
| TC-39 (`TC_HU09_10`) | La DGMM aprueba los 4 documentos antes de aprobar el trámite | HU-12, D4 |

## 5. Historial de fallas del Sprint 6

El 53/53 y el 253/253 son el estado final. Durante el sprint fallaron estas cosas:

| # | Fecha | Qué falló | Causa | Corrección |
|---|---|---|---|---|
| 1 | 18-10 | **CI en rojo** en `9f8bdaf`: 91 pruebas JUnit con 403. En la app, el login y los POST dejaron de funcionar ([corrida](https://github.com/lowilt24/registro-de-naves/actions/runs/39173131564)) | Al activar CSRF, MockMvc no mandaba el token. La cookie `XSRF-TOKEN` no llegaba al navegador (token perezoso de Spring Security 6) y `login.html` no enviaba el token | `2200330`: `CsrfCookieFilter`, token en las pantallas y `CsrfEnPruebasConfig`. CI en verde, 221 pruebas ([corrida](https://github.com/lowilt24/registro-de-naves/actions/runs/39195530055)) |
| 2 | 18–23-10 | **Katalon TS01–TS05 con 403** en los POST | Los scripts no enviaban el token CSRF | `b93ebab` (T6): 42/42 con el arnés. Solo queda constancia en el mensaje del commit, no hay corrida guardada |
| 3 | 23-10 | **Defectos #12 y #13** (sección 7) | — | #13 corregido en `78ae4c8`. #12 abierto |
| 4 | 23-10 | **TC-44 a TC-49 fallaban** con el arnés | Esperado: HU-12 todavía no existía. TC-48 mostró que se podía aprobar un trámite con documentos en `PENDIENTE` | `8641ccb` (D4). Ahora 6/6 |
| 5 | 24-10 | **TC-31 y TC-39 fallaban** con el arnés (51/53): 409 al aprobar el trámite | Los casos no contemplaban D4 ni D5. No es un bug de la app | Ajuste de los scripts (sección 4). Ahora pasan, también en Katalon |

El arnés que corre los scripts fuera de Katalon vive en el scratchpad, no en el repo. No
corre TC_INFRA_01, que es de navegador; en Katalon pasó.

## 6. Comprobación del #13

`NaveIntegrationTest.unNombreQueCreceAlPasarAMayusculasEs400NoUn500`:

- 61 `ß` → **400** en `campos.nombre`; antes daba 500.
- Un nombre de 120 caracteres sigue entrando (201).

En verde dentro de las 253.

## 7. Defectos del Sprint 6

| Issue | Severidad | Estado | Nota |
|---|---|---|---|
| #12 | Alta | **Abierto** | Cualquiera puede registrarse como `FUNCIONARIO_DGMM` y aprobar trámites ajenos. Decide la PO. Con este abierto no se cumple la DoD |
| #13 | Media | Corregido en `78ae4c8` (falta cerrar el issue) | 500 con nombres que crecen al pasar a mayúsculas |

## 8. Pendiente

- **Madeleine:** decidir el #12 y confirmar que al desistir la nave queda en `REGISTRADA`
  (observación 7 de la revisión de la V7).
- **Abdiel:** Wfuzz del Sprint 6 adaptado a CSRF y aclarar el 401 frente al 403 de un POST
  anónimo sin token.
- **Louis:**
  - revisión manual de la bandeja y de la cámara 5 (DGMM, agente y auditor);
  - LOC/ELOC con `cloc` y la línea base `qa/metricas/base-sprint-6.md`.
- **QA (opcional):** dos pruebas más en el endpoint de revisión: un agente que no es el
  dueño, y el ADMINISTRADOR.
