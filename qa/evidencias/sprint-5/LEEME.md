# Evidencia de calidad — Sprint 5

HU-09 (estado del trámite) y HU-10 (naves por estado del trámite), sobre la rama
`sprint-5` en `c858cb5` (código de Ismael) más las pruebas de QA de este bloque.

**Estado: parcial.** Faltan la corrida de `TS05_Sprint5` en Katalon (Louis) y la regla
de documentos obligatorios que pidió la PO (Ismael). Cuando estén, se agregan aquí
`TS05_Sprint5-resumen.csv` y `TS05_Sprint5-JUnit.xml`, igual que en el Sprint 4.

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

`./mvnw verify` → **194/194 en verde**, `BUILD SUCCESS`.

| Clase | Pruebas | Autor |
|---|---|---|
| `EstadoTramiteTest` | 54 (7 transiciones válidas, 41 inválidas y reglas) | Ismael |
| `AutorizacionTramiteTest` | 47 (matriz rol × acción × dueño) | Ismael |
| `TramiteIntegrationTest` | 20 | Ismael |
| `TramiteInvariantesIntegrationTest` | 15 | QA (Louis) |
| Resto (Sprints 1–4) | 58 | — |

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
| Total (63 clases) | **90.7 %** (916/1010) | **81.9 %** (195/238) |
| Paquete `tramite` | 96.3 % (233/242) | 91.2 % (103/113) |
| Sprint 4 (referencia) | 88.2 % | 73.5 % |

Se alcanza la meta de 80 % de rama propuesta para el Sprint 5.

## 4. Tamaño (cloc, mismo método que el Sprint 4)

`cloc src/main --include-lang=Java,JavaScript,HTML,CSS,SQL`

| | Sprint 4 | Ahora |
|---|---|---|
| LOC (blank + comment + code) | 5,370 | **6,861** |
| ELOC (code) | 3,843 | **4,839** |

Medición intermedia: la línea base oficial va en `qa/metricas/base-sprint-5.md` al cierre.

## 5. Katalon — `TS05_Sprint5` (por correr)

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

Auxiliares (no van en la suite):

- `TC_AUX_Crear_Segundo_Agente`: registra un agente y valida su correo con Mailpit.
- `TC_AUX_Nave_Con_Documentos`: nave con los 4 documentos obligatorios.

TC-31 y TC-32 cargan los 4 documentos antes de admitir, así que pasan antes y después de
la regla nueva. **TC-33 va con `isRun=false`** hasta que Ismael suba la regla.

**Réplica previa sin Katalon** (13-10-2026): las aserciones de los 12 casos activos se
replicaron por HTTP contra `localhost:8080`, con resultado **12/12**. Esto no reemplaza
la corrida de Katalon: solo confirma que lo esperado coincide con el comportamiento actual.

## 6. Hallazgos de este bloque

| # | Hallazgo | Tipo | Estado |
|---|---|---|---|
| 1 | La DGMM puede registrar una nave y luego admitirla y aprobarla ella misma (reproducido: nave 291, historial `PRESENTAR` / `ADMITIR` / `APROBAR`, todo por `revisor@`). `POST /api/naves` no exige el rol `AGENTE_NAVIERO`. | Regla de negocio (D1) | Para la PO / Ismael |
| 2 | `TramiteIntegrationTest.sinParametroLaListaNoCambia` exige que todas las naves de `agente@` estén en `REGISTRADA`: depende de los datos de la base (mismo patrón que el issue #4). | Defecto de prueba | Issue #9 (Ismael) |
| 3 | Criterio de documentos obligatorios (PO, 13-10-2026) sin implementar. | Pendiente | Ismael |
| 4 | Con la nueva regla, casi todo `TramiteIntegrationTest` (admite naves sin documentos) pasará a dar 409. | Riesgo | Ismael |
| 5 | En la respuesta del cambio de estado, `creadoEn` viene en UTC (`Z`) y `actualizadoEn` en hora local (`-05:00`). | Menor (cosmético) | Informativo |
