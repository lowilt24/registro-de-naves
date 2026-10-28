# Evidencia de calidad — Sprint 6

HU-11 (bandeja de trámites de la DGMM) y HU-12 (revisión de documentos), sobre la rama
`sprint-6`:

- **`9f8bdaf` (Abdiel) y `2200330` (Ismael):** SEC-05, protección CSRF.
- **`c8d5365` (Ismael):** HU-11.
- **`8641ccb` (Ismael):** HU-12, con la migración V7.
- **`78ae4c8` (Ismael):** corrección del issue #13.
- **QA (Louis y Claude):** `b93ebab`, `bbccc68`, `340f374` y `c68ea55`, más los ajustes de
  este commit.

**Estado: completo (cierre del sprint, 26-10-2026).** Maven 255/255, cobertura, Katalon 53/53,
revisión manual (secciones 8 y 9), Wfuzz (`seguridad/evidencia-sprint6.md`) y línea base
(`qa/metricas/base-sprint-6.md`). Quedan abiertos el #12, que pasa al Sprint 7 por decisión de
la PO, y el #15 (sección 7).

## 1. Migración V7 (24-10-2026, base de desarrollo de Louis)

La revisión previa del diseño está en `revision-diseno-v7.md`. La V7 se aplicó al correr
`./mvnw verify` (`flyway_schema_history` versión 7, `success = t`):

| Comprobación | Resultado |
|---|---|
| Documentos | 252, todos en `PENDIENTE`: los `CHECK` de la V7 entran sin rellenar datos |
| Naves | 309, todas en `REGISTRADA` |

## 2. Pruebas Maven (JUnit)

`./mvnw verify` → **253/253 en verde**, `BUILD SUCCESS` (24-10-2026, sobre `78ae4c8`).
El 25-10, con las 2 pruebas de QA de HU-12 (sección 10): **255/255**.

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
| #12 | Alta | **Abierto**, pasa al Sprint 7 | Cualquiera puede registrarse como `FUNCIONARIO_DGMM` y aprobar trámites ajenos. El 25-10 la PO lo pasó al Sprint 7 como limitación conocida: la DoD se cumple salvo en este punto, por decisión explícita de la PO |
| #13 | Media | Cerrado el 24-10, corregido en `78ae4c8` | 500 con nombres que crecen al pasar a mayúsculas |
| #14 | Media | Cerrado el 25-10, corregido en `9754cbd` | `docker compose` no arranca desde `d2664d7` (SEC-03): el `": "` del mensaje de `DB_PASSWORD` rompe el YAML. El CI no lo detecta porque no usa `docker compose` |
| #15 | Baja–media | **Abierto** (Ismael) | La bandeja se desborda horizontalmente con un nombre de nave largo sin espacios (sección 8, A4) |

## 8. Revisión manual de la bandeja (HU-11)

25-10-2026, sobre `0c67c58`, en escritorio (Chrome). La hizo Louis con la guía
`revision-manual/GUIA.md`, sección A. Antes se tomaron los valores esperados de la API
(`GET /api/tramites` y `/api/tramites/conteo-por-estado`) para comparar con la pantalla.

| Paso | Rol | Qué se revisó | Resultado |
|---|---|---|---|
| A1 | DGMM | Al iniciar sesión va a `/bandeja.html` | ✅ |
| A2 | DGMM | Sin filtro: solo Solicitud presentada y En revisión, del más antiguo al más nuevo | ✅ 349 filas (312 + 37); la primera es "Estrella del Istmo" (36 días) |
| A3 | DGMM | Filtros con los 6 estados y su cantidad; cada filtro muestra solo su estado | ✅ 312, 37, 1, 30, 19 y 68 (467 en total), iguales a la API |
| A4 | DGMM | Columnas y enlace al expediente | ❌ **#15**: hay que desplazarse horizontalmente para ver las columnas. Columnas, orden y enlace "Abrir" correctos |
| A5 | DGMM | 375 px: filas como tarjetas, sin desplazamiento horizontal | ✅ |
| A6 | Auditor | Va a `/bandeja.html` y ve la misma bandeja, sin acciones | ✅ Mismos conteos y filas |
| A7 | Agente | Va a `/naves.html`; en `/bandeja.html` ve el aviso y ningún dato | ✅ La API responde 403 |

**Resultado: 6 de 7 pasos pasan. A4 falla por el #15.**

- **Causa del #15:** la nave 1374 se llama `aaaa…a`, con 120 caracteres sin espacios. Es un
  nombre válido, porque 120 es el máximo. El navegador no puede cortarlo, así que la columna
  Nave se estira y empuja las otras cinco fuera de la vista, en todas las filas.
- **Corrección propuesta:** `overflow-wrap: anywhere` en `th, td` (`app.css:502`).

Comprobado por la API, además de la pantalla:

- un filtro inválido (`?estado=XX`) da 400, no 500;
- el auditor recibe 200 en el conteo y el agente 403.

El mensaje de bandeja vacía no se puede ver aquí, porque ningún estado tiene 0 trámites en la
base de desarrollo. Lo cubre TC-42.

No se guardaron capturas: la evidencia de la revisión manual es esta tabla.

## 9. Revisión manual de la cámara 5 (HU-12)

25-10-2026, sobre `0c67c58`, en escritorio (Chrome). La hizo Louis con la guía
`revision-manual/GUIA.md`, sección B. B2 a B4 se hicieron en la nave **1788** en vez de la
1800 (ver la observación de usabilidad, abajo).

| Paso | Rol | Nave | Qué se revisó | Resultado |
|---|---|---|---|---|
| B1 | DGMM | 1801 | Solicitud presentada: sin carga ni botones de revisión, con la nota del estado; tras Admitir aparecen los botones sin recargar | ✅ |
| B2 | DGMM | 1788 | Aprobar 3 documentos; Observar el SMC (foco, error si está vacío, Cancelar, guardar); aprobar el trámite da 409 por el SMC; descarga | ✅ |
| B3 | Agente | 1788 | Ve la observación y el revisor, sin botones de revisión; carga el SMC v2 en `PENDIENTE` y la v1 conserva su observación | ✅ |
| B4 | DGMM | 1788 | Aprobar el SMC v2 y el trámite: Aprobada, nave `ABANDERADA` sin recargar, sin botones | ✅ |
| B5 | DGMM y agente | 1799 | Rechazar con observación: nave `RECHAZADA` en rojo, también en "Mis naves" | ✅ |
| B6 | Auditor | 1800 | Ve documentos y observaciones, descarga, sin botones ni carga, aviso "Vista de consulta" | ✅ |
| B7 | DGMM | 1788 | Dos pestañas: la segunda aprobación da *"El documento ya fue revisado."* y la lista se actualiza | ✅ |
| B8 | DGMM | — | Tab llega a Aprobar y Observar, el foco se ve, Enter funciona | ✅ |

**Resultado: 8 de 8 pasos pasan.** Hay una observación de usabilidad, que no cuenta como
defecto.

Comprobado en la base al terminar:

| Nave | Trámite | Nave (`nave.estado`) | Documentos |
|---|---|---|---|
| 1801 | En revisión (Admitir de B1) | `REGISTRADA` | 4 en `PENDIENTE` |
| 1788 | **Aprobada** | **`ABANDERADA`** (D5) | 4 vigentes en `APROBADO`; SMC v1 `OBSERVADO`, no vigente, con "Falta la firma del inspector" |
| 1799 | **Rechazada** | **`RECHAZADA`** (D5) | — |
| 1800 | En revisión | `REGISTRADA` | 4 en `APROBADO` (el SMC por accidente) |

- El historial de los trámites registra, en orden, PRESENTAR, ADMITIR y APROBAR o RECHAZAR.
- Revisar documentos no movió ningún trámite (D3).

No se guardaron capturas: la evidencia de la revisión manual es esta tabla.

### Observación de usabilidad: aprobar no pide confirmación y no se puede deshacer

**No se registra como defecto ni cuenta en las métricas.** Es una observación de QA para
comentarla con el equipo y la PO.

- **Qué pasó (B2, DGMM, nave 1800):** Louis aprobó el SMC por accidente, cuando quería
  observarlo. Ya no se pudo observar.
  - El documento queda en `APROBADO`, y la revisión solo se permite sobre la versión vigente
    en `PENDIENTE`, así que la DGMM no tiene cómo corregirlo.
  - La única salida sería que el agente cargue una versión nueva, pero no tiene motivo para
    hacerlo: no recibe ninguna observación.
- **Causa:** el botón Aprobar envía la decisión al primer clic, sin confirmación
  (`static/js/documentos.js:255`).
- **Lo mismo en la cámara 6:** Aprobar el trámite también se ejecuta al primer clic
  (`static/js/tramite.js:113`), y deja el trámite en Aprobada, que es un estado final. Rechazar
  al menos exige escribir la observación antes.
- **Propuesta de Louis:** un mensaje de confirmación antes de aprobar un documento o
  el trámite. Por ejemplo, *"¿Aprobar Gestión de la seguridad (SMC)? Esta decisión no se puede
  cambiar."*, con los botones Aprobar y Cancelar.
- **Pregunta para la PO:** ¿basta con confirmar, o la DGMM también debería poder deshacer una
  aprobación (por ejemplo, reabrir un documento aprobado)? Lo segundo cambia la regla de HU-12.
- **Efecto en la revisión:** la nave 1800 quedó con los 4 documentos en `APROBADO`. Los pasos de
  observar y corregir (resto de B2, B3 y B4) se repiten en la nave **1788** (En revisión, con
  los 4 documentos en `PENDIENTE`).

## 10. Pendiente

- **Ismael (Sprint 7):** #12 y #15 (desborde de la bandeja). El #14 lo corrigió Claude en
  `9754cbd`.
- **Madeleine:** ~~decidir el #12~~ (25-10: pasa al Sprint 7). Falta confirmar que al desistir
  la nave queda en `REGISTRADA` (observación 7 de la revisión de la V7) y responder la pregunta
  de usabilidad de la sección 9.
- **Abdiel:** revisar la corrida de Wfuzz del Sprint 6. La hicieron Louis y Claude el 25-10
  (`seguridad/evidencia-sprint6.md`): 0 respuestas 500. También queda respondida la duda: un
  POST anónimo sin token da 401 en el servidor real (SEC-09, informativo). Corrección
  sugerida: `"/error"` en `permitAll()`.
- **Louis:**
  - ~~revisión manual~~: hecha el 25-10 (secciones 8 y 9);
  - ~~LOC/ELOC y línea base~~: hechos el 25-10 en `qa/metricas/base-sprint-6.md` (LOC 8,146 y
    ELOC 5,685).
- ~~**QA:** dos pruebas más en el endpoint de revisión~~: hechas el 25-10 en
  `DocumentoRevisionIntegrationTest` (`unAgenteQueNoEsElDuenoNoRevisa` y
  `elAdministradorNoRevisaNiLeeLosDocumentos`). Maven: 255 de 255. El ADMINISTRADOR recibe 403
  también al listar documentos (`puedeLeer` → `false`), aunque `documentos.js` lo pone en modo
  lectura.
