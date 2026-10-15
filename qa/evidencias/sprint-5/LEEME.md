# Evidencia de calidad — Sprint 5

HU-09 (estado del trámite) y HU-10 (naves por estado del trámite), sobre la rama
`sprint-5`:

- **`c858cb5` (Ismael):** las dos historias.
- **QA:** pruebas y casos de Katalon.
- **Louis y Claude, el 13-10-2026:** las reglas que la PO agregó ese día (documentos
  obligatorios, registro solo para el agente naviero y expediente de solo lectura) y la
  corrección de SEC-08. Se hicieron así para poder presentar el sprint al día siguiente.

**Estado: final.** Maven, cobertura, tamaño, Wfuzz y Katalon (TS05 15/15 y regresión de
TS04 8/8).

## 1. Migración V6 (13-10-2026, base de desarrollo de Louis)

Antes de la V6 (consulta que pedía el plan):

```
SELECT estado, count(*) FROM nave GROUP BY estado;
 REGISTRADA | 29
```

Después de la V6 (`./mvnw verify` la aplicó; `flyway_schema_history` versión 6, `success = t`):

| Comprobación | Resultado |
|---|---|
| Naves / trámites | 29 / 29 |
| Naves sin exactamente un trámite | 0 |
| Estado de los trámites | 29 en `SOLICITUD_PRESENTADA` (mapeo P3, confirmado por la PO) |
| Entradas iniciales del historial | 29 `MIGRACION`, todas sin estado anterior (P9) |

## 2. Pruebas Maven (JUnit)

`./mvnw verify` → **205/205 en verde**, `BUILD SUCCESS` (versión final).

| Clase | Pruebas | Autor |
|---|---|---|
| `EstadoTramiteTest` | 54 (7 transiciones válidas, 41 inválidas y reglas) | Ismael |
| `AutorizacionTramiteTest` | 47 (matriz rol × acción × dueño) | Ismael |
| `TramiteIntegrationTest` | 29: las 20 de Ismael más 9 de las reglas de la PO y SEC-08 | Ismael; Louis y Claude |
| `TramiteInvariantesIntegrationTest` | 15 | QA (Louis) |
| `ManejadorGlobalDeErroresTest` | 2 (red de seguridad de SEC-08) | Louis y Claude |
| Resto (Sprints 1–4) | 58 | — |

Las 9 pruebas nuevas de `TramiteIntegrationTest`:

- **Documentos obligatorios:**
  - sin documentos, 409 con los 4 `faltantes` en orden;
  - el orden es el oficial aunque se carguen en otro orden;
  - un documento `OBSERVADO` cuenta como faltante;
  - Subsanar también exige los documentos y pasa al corregirlos.
- **Expediente de solo lectura:**
  - con el trámite Aprobado o Desistido, documentos, propietario y agente dan 409, y leer
    sigue permitido;
  - al que no es dueño se le sigue respondiendo 403.
- **Registro:** solo el agente naviero registra naves; la DGMM y el auditor reciben 403
  (issue #10).
- **SEC-08:** un carácter nulo en la observación o en `lugarConstruccion` da 400.

Además se corrigieron:

- `sinParametroLaListaNoCambia` (issue #9): ahora mira solo la nave de la prueba.
- `DocumentoIntegrationTest.idorConNavePropiaYDocumentoAjeno`: usa un segundo agente.

`TramiteInvariantesIntegrationTest` cubre lo que pedía el plan de pruebas y no estaba:

- V6:
  - cada nave tiene exactamente un trámite;
  - cada trámite empieza con una sola entrada inicial;
  - ejecutarla dos veces no duplica nada;
  - mapeo P3 de los 5 valores de `EstadoNave`.
- Unicidad: un segundo trámite para la misma nave choca con `uk_tramites_nave`, y `crearPara` no duplica.
- `CHECK` de estados: `BORRADOR` se rechaza.
- 409 por cambio simultáneo (bloqueo optimista).
- `ADMINISTRADOR`: 403 al leer y al actuar.
- HU-10:
  - un agente sin naves recibe conteo en cero y listas vacías;
  - el conteo coincide con el filtro.

No depende de los datos de demo: crea sus usuarios y naves, y `@Transactional` los deshace
(verificado: 0 usuarios o naves de QA en la base después de correrla).

## 3. Cobertura (JaCoCo)

| | Línea | Rama |
|---|---|---|
| Total (67 clases) | **91.1 %** (978/1074) | **81.2 %** (216/266) |
| Paquete `tramite` | 96.6 % (259/268) | 91.6 % (109/119) |
| Sprint 4 (referencia) | 88.2 % | 73.5 % |

Se alcanza la meta de 80 % de rama propuesta para el Sprint 5.

## 4. Tamaño (cloc, mismo método que el Sprint 4)

`cloc src/main --include-lang=Java,JavaScript,HTML,CSS,SQL`

| | Sprint 4 | Ahora |
|---|---|---|
| LOC (blank + comment + code) | 5,370 | **7,111** |
| ELOC (code) | 3,843 | **4,993** |

La línea base completa está en `qa/metricas/base-sprint-5.md`.

## 5. Katalon — `TS05_Sprint5`

| Suite | Corrida | Casos | Resultado | Archivos |
|---|---|---:|---|---|
| `TS05_Sprint5` | 14-10-2026 00:02 | 15 | **15/15 PASSED** (11.17 s) | `TS05_Sprint5-resumen.csv`, `TS05_Sprint5-JUnit.xml` |
| `TS04_Sprint4` (regresión, con el TC-24 ajustado) | 13-10-2026 23:42 | 8 | **8/8 PASSED** | `TS04_Sprint4-resumen.csv`, `TS04_Sprint4-JUnit.xml` |

Las corrió Louis en Katalon Studio. Los archivos están depurados como en el Sprint 4: sin
pasos ni consola, porque el reporte completo registra la contraseña de demo (SEC-03).

**Corridas anteriores (no son la evidencia final):**

- **13-10 a las 23:14 y 23:43: 12/12.** TC-33, TC-38 y TC-39 no corrieron. En la de las
  23:43, Katalon Studio tenía abierta la versión vieja de la suite y **la guardó encima de
  la nueva**, devolviendo esos tres casos a `isRun=false`. Se restauró desde git.
  *Lección:* antes de correr una suite que cambió en el disco, cerrar sus pestañas sin
  guardar y refrescar el proyecto.
- **13-10 a las 23:57: 14/15.** Falló TC-38 por un defecto del caso, no de la aplicación:
  los dos registros dieron 403, pero el caso además exigía que la DGMM no tuviera ninguna
  nave con ese prefijo de nombre, y en la base había una creada antes de la regla. Se
  corrigió para comparar el total de naves de cada usuario antes y después del intento. La
  corrida siguiente dio 15/15.

Mismo formato que TS04: casos por API, con los helpers al inicio de cada script.

| Caso | Script | HU | Qué verifica | Espera |
|---|---|---|---|---|
| TC-25 | `TC_HU09_01_Tramite_Nace_Al_Registrar` | HU-09/03 | Nace en Solicitud presentada; historial con `PRESENTAR`; nave sigue `REGISTRADA` | 201 / 200 |
| TC-26 | `TC_HU09_02_Acciones_Por_Rol` | HU-09 | Agente `[DESISTIR]`, DGMM `[ADMITIR]`, auditor `[]` y no actúa | 200 / 403 |
| TC-27 | `TC_HU09_03_Sin_Sesion_E_Inexistente` | HU-09 | Sin sesión; nave inexistente | 401 / 404 |
| TC-28 | `TC_HU09_04_Permisos_403` | HU-09 | Agente aprueba o rechaza, DGMM desiste, agente ajeno lee o actúa | 403 |
| TC-29 | `TC_HU09_05_Transicion_Invalida_409` | HU-09 | Saltos fuera de la máquina; acción inexistente o del sistema | 409 / 400 |
| TC-30 | `TC_HU09_06_Desistir_Estado_Final` | HU-09 | Desistir; Desistida no admite más cambios | 200 / 409 |
| TC-31 | `TC_HU09_07_Flujo_Revision_Completo` | HU-09 | Admitir → observar → subsanar → aprobar; historial completo | 200 / 400 |
| TC-32 | `TC_HU09_08_Rechazo_Con_Observacion` | HU-09 | Rechazar sin observación o con más de 1000 caracteres; Rechazada es final | 400 / 200 / 409 |
| TC-33 | `TC_HU09_09_Admitir_Sin_Documentos` | HU-09 | Criterio de la PO: faltan documentos obligatorios, con `faltantes` ordenado | 409 |
| TC-34 | `TC_HU10_01_Filtro_Por_Estado` | HU-10 | Filtro por estado del trámite; sin filtro no cambia | 200 |
| TC-35 | `TC_HU10_02_Conteo_Por_Estado` | HU-10 | 6 estados, suma = total, cada número = filtro | 200 |
| TC-36 | `TC_HU10_03_Estado_Invalido_400` | HU-10 | `VOLANDO`, minúsculas, numérico, inyección, `REGISTRADA` | 400 |
| TC-37 | `TC_HU10_04_Sin_Resultados_Y_Aislamiento` | HU-10 | Agente nuevo con todo en cero; aislamiento entre agentes | 200 |
| TC-38 | `TC_HU03_04_Registro_Solo_Agente` | HU-03/09 | Decisión de la PO: la DGMM y el auditor no registran naves | 403 |
| TC-39 | `TC_HU09_10_Expediente_Final_Solo_Lectura` | HU-09 | Decisión de la PO: con el trámite Aprobado o Desistido no se cargan documentos, propietarios ni agente | 409 |

Auxiliares (no van en la suite):

- `TC_AUX_Crear_Segundo_Agente`: registra un agente y valida su correo con Mailpit.
- `TC_AUX_Nave_Con_Documentos`: nave con los 4 documentos obligatorios.

TC-31 y TC-32 cargan los 4 documentos antes de admitir, así que pasan antes y después de
la regla nueva.

TC-33, TC-38 y TC-39 se activaron cuando las reglas quedaron implementadas, y TS05 queda
con 15 casos activos. Antes de implementarlas se confirmó que fallaban por la razón
esperada: la DGMM registraba con 201 y se cargaba un documento con el trámite Aprobado con
201. TC-39 verifica el **mensaje** de solo lectura y no solo el 409, porque un 409 por otra
regla (la participación del propietario) no prueba que el expediente esté cerrado.

**TC-24 (`TC_HU08_04_Descarga_Nave_Ajena`, Sprint 4) se ajustó:** el "otro usuario" que
registra una nave para probar el IDOR ahora es un segundo agente (`TC_AUX_Crear_Segundo_Agente`)
y no `revisor@`, que con la regla de TC-38 ya no podrá registrar. Verificado hoy: 403 / 404 / 403.

**Réplica sin Katalon** (13-10-2026): las aserciones se replicaron por HTTP contra
`localhost:8080`, con dos resultados:

- **12/12** con el código de Ismael;
- **15/15** con la versión final, incluidos TC-33, TC-38 y TC-39.

Esto no reemplaza la corrida de Katalon: solo confirma que lo esperado coincide con el
comportamiento actual.

## 6. Hallazgos de este bloque

| # | Hallazgo | Tipo | Estado |
|---|---|---|---|
| 1 | La DGMM puede registrar una nave y luego admitirla y aprobarla ella misma (reproducido: nave 291, historial `PRESENTAR` / `ADMITIR` / `APROBAR`, todo por `revisor@`). `POST /api/naves` no exige el rol `AGENTE_NAVIERO`. | Regla de negocio (D1) | **Corregido** (issue #10): solo el agente registra; TC-38 |
| 2 | `TramiteIntegrationTest.sinParametroLaListaNoCambia` exige que todas las naves de `agente@` estén en `REGISTRADA`: depende de los datos de la base (mismo patrón que el issue #4). | Defecto de prueba | **Corregido** (issue #9) |
| 3 | Criterio de documentos obligatorios (PO, 13-10-2026). | Regla nueva | **Implementado**; TC-33 |
| 6 | Con el trámite Aprobado, Rechazado o Desistido, el agente aún cambiaba documentos, propietarios y agente residente. PO (13-10): solo lectura, 409. | Regla nueva | **Implementado**; TC-39 |
| 4 | Con las reglas nuevas, `TramiteIntegrationTest` y `DocumentoIntegrationTest` iban a fallar: admitían naves sin documentos y registraban una nave con `REVISOR`. | Riesgo | **Resuelto**: las pruebas cargan el expediente completo y usan un segundo agente |
| 7 | SEC-08: carácter nulo en los textos (500). Ver `seguridad/evidencia-sprint5.md`. | Seguridad | **Corregido**; Wfuzz final con 0 respuestas 500 |
| 5 | En la respuesta del cambio de estado, `creadoEn` viene en UTC (`Z`) y `actualizadoEn` en hora local (`-05:00`). | Menor (cosmético) | Informativo |
