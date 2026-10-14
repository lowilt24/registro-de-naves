# Línea base de métricas — Sprint 5

Medición sobre la rama `sprint-5` el 13-10-2026, con HU-09 y HU-10 completas y las reglas
que la PO agregó ese día.

## Resumen para el acta

Tabla en el formato que pidió la profesora, más la cobertura de rama como fila adicional.

| Métrica | Sprint 5 | Sprint 4 |
|---|---|---|
| LOC | **7,111** | 5,370 |
| ELOC | **4,993** | 3,843 |
| Cobertura de líneas (JaCoCo) | **91.1 %** (978 de 1,074 líneas) | 88.2 % |
| Cobertura de ramas (JaCoCo), adicional | **81.2 %** (216 de 266 ramas) | 73.5 % |
| Casos de prueba pasados: Katalon (TS05) | **15 de 15** (100 %); regresión de TS04: 8 de 8 | 8 de 8 (TS04) |
| Casos de prueba pasados: Maven (JUnit) | **205 de 205** (100 %) | 58 de 58 |
| Defectos encontrados | **2** (#9, #10) | 4 |
| Defectos abiertos | **0** | 2 |
| Densidad de defectos (encontrados) | **0.40** por KELOC (2 ÷ 4.993) | 1.04 |
| Densidad de defectos (abiertos) | **0.00** por KELOC | 0.52 |
| Vulnerabilidades encontradas (Wfuzz) | **1** (SEC-08), corregida; corrida final: **0** respuestas 500 | 0 |

**Cómo se obtuvo cada cifra**

- **LOC y ELOC:** `cloc src/main` sobre Java, JS, HTML, CSS y SQL. LOC = blank + comment +
  code; ELOC = solo `code`.
- **Cobertura:** JaCoCo con `./mvnw verify`, solo sobre el Java de producción (67 clases).
  La meta de 80 % de rama propuesta para el Sprint 5 se cumple.
- **Defectos:** issues `bug` + `sprint-5`. Los dos se corrigieron en el sprint.
- **Vulnerabilidades:** Wfuzz con `seguridad/correr-wfuzz-sprint5.sh`. La corrida previa
  encontró un 500 (SEC-08); la corrida final, sobre la versión corregida, dio 0. Detalle en
  `seguridad/evidencia-sprint5.md`.
- **Densidad de defectos** = defectos ÷ (ELOC / 1000), con las dos definiciones del
  Sprint 4: **encontrados** (la de la profesora) y **abiertos** al cierre.

## Casos de prueba de Katalon

| Suite | Historias | Casos | Pasados | Corrida |
|---|---|---:|---:|---|
| `TS05_Sprint5` | HU-09, HU-10 (y HU-03: TC-38) | 15 | 15 | 14-10-2026 00:02 (evidencia en `qa/evidencias/sprint-5/`) |
| `TS04_Sprint4` | HU-07, HU-08 (regresión; TC-24 ajustado) | 8 | 8 | 13-10-2026 23:42 (evidencia en `qa/evidencias/sprint-5/`) |
| `TS01`–`TS03` | HU-01 a HU-06 | 20 | 20 | Sprint 4 (sin cambios) |

Antes de la corrida final hubo corridas de 12/12 y una de 14/15, en la que falló un caso por
un defecto del propio caso. El detalle está en `qa/evidencias/sprint-5/LEEME.md`.

## Registro de defectos

| Issue | Defecto | Encontrado | Estado |
|---|---|---|---|
| [#9](https://github.com/lowilt24/registro-de-naves/issues/9) | La prueba `sinParametroLaListaNoCambia` depende de los datos de la base | Sprint 5 | Corregido en el sprint |
| [#10](https://github.com/lowilt24/registro-de-naves/issues/10) | La DGMM puede registrar una nave y aprobar su propia solicitud | Sprint 5 | Corregido en el sprint |
| [#6](https://github.com/lowilt24/registro-de-naves/issues/6) | Textos visibles sin tildes | Sprint 4 | Corregido en el Sprint 5 (`c858cb5`) |
| [#7](https://github.com/lowilt24/registro-de-naves/issues/7) | La fila "Contacto" muestra un punto suelto | Sprint 4 | Corregido en el Sprint 5 (`c858cb5`) |

Los issues #6 y #7 se encontraron en el Sprint 4, así que no entran en el conteo del
Sprint 5 aunque se hayan corregido en este sprint. Los hallazgos de seguridad (SEC-xx) no
se cuentan aquí: van en la fila de vulnerabilidades.

```bash
gh issue list --label bug --label sprint-5 --state all    # encontrados
gh issue list --label bug --label sprint-5 --state open   # abiertos
```

## Tamaño por lenguaje (`src/main`)

| Lenguaje | Archivos | En blanco | Comentarios | ELOC (code) | LOC |
|---|---:|---:|---:|---:|---:|
| Java | 66 | 613 | 601 | 2,539 | 3,753 |
| JS | 5 | 175 | 153 | 890 | 1,218 |
| HTML | 5 | 74 | 19 | 648 | 741 |
| CSS | 1 | 96 | 93 | 638 | 827 |
| SQL | 7 | 75 | 219 | 278 | 572 |
| **Total** | **84** | **1,033** | **1,085** | **4,993** | **7,111** |

Las pruebas (`src/test`, 13 archivos) se cuentan aparte: 2,521 LOC y 1,948 ELOC.

## Cobertura por paquete (JaCoCo)

| Paquete | Rama | Línea |
|---|---:|---:|
| `pa.amp.registro_naves.tramite` | 91.6 % (109/119) | 96.6 % (259/268) |
| `pa.amp.registro_naves.common` | 100.0 % (10/10) | 88.6 % (62/70) |
| `pa.amp.registro_naves.documento` | 79.4 % (27/34) | 89.3 % (159/178) |
| `pa.amp.registro_naves.persona` | 79.2 % (19/24) | 94.3 % (182/193) |
| `pa.amp.registro_naves.config` | 75.0 % (6/8) | 97.7 % (42/43) |
| `pa.amp.registro_naves.nave` | 65.6 % (21/32) | 92.1 % (152/165) |
| `pa.amp.registro_naves.usuario` | 61.5 % (24/39) | 78.6 % (121/154) |
| `pa.amp.registro_naves (raíz)` | sin ramas | 33.3 % (1/3) |

`usuario` sigue siendo el paquete con menos cobertura de rama. Ahí quedan sin probar
criterios del Sprint 4: correo duplicado (HU-01), enlace de validación vencido y consulta
de sesión sin login.

## Alcance y limitaciones de la medición

- **Solo el Java de producción.** La cobertura no mide el JavaScript del frontend.
- **Katalon no suma cobertura:** corre fuera de Maven, sin el agente de JaCoCo.
- **No hay umbral en el build.** Sigue sin la regla `check` de JaCoCo, así que el CI no
  falla por cobertura.
- **Limitaciones conocidas del producto, que pasan al Sprint 6 por decisión de la PO:**
  la bandeja de la DGMM y la revisión de documentos por la DGMM. Tampoco son condicionales
  todavía los documentos ITC y SMC.

## Cómo reproducir

```bash
./mvnw verify                     # reporte en target/site/jacoco/index.html
cloc src/main --include-lang=Java,JavaScript,HTML,CSS,SQL
cloc src/test
bash seguridad/correr-wfuzz-sprint5.sh
```
