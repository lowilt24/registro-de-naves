# Línea base de métricas — Sprint 6

Medición sobre la rama `sprint-6` el 25-10-2026, en `449a15d`, con HU-11 y HU-12 completas,
SEC-03 y SEC-05 corregidos y la regla `check` de JaCoCo en el build. El código de producción
es el de `d2664d7`: los commits posteriores solo tocan `qa/`, `seguridad/` y
`docker-compose.yml` (#14, `9754cbd`). El tamaño no cambia con ellos.

**Es la medición previa al cierre (26-10).** Si antes del cierre se corrigen los issues
abiertos, hay que actualizar las filas de defectos y volver a medir el tamaño.

## Resumen para el acta

Tabla en el formato que pidió la profesora, más la cobertura de rama como fila adicional.

| Métrica | Sprint 6 | Sprint 5 |
|---|---|---|
| LOC | **8,146** | 7,111 |
| ELOC | **5,685** | 4,993 |
| Cobertura de líneas (JaCoCo) | **93.3 %** (1,124 de 1,205 líneas) | 91.1 % |
| Cobertura de ramas (JaCoCo), adicional | **87.2 %** (293 de 336 ramas) | 81.2 % |
| Casos de prueba pasados: Katalon (TS01–TS06) | **53 de 53** (100 %); TS06: 10 de 10 | 15 de 15 (TS05) |
| Casos de prueba pasados: Maven (JUnit) | **253 de 253** (100 %) | 205 de 205 |
| Defectos encontrados | **4** (#12, #13, #14, #15) | 2 |
| Defectos abiertos | **2** (#12, #15) | 0 |
| Densidad de defectos (encontrados) | **0.70** por KELOC (4 ÷ 5.685) | 0.40 |
| Densidad de defectos (abiertos) | **0.35** por KELOC (2 ÷ 5.685) | 0.00 |
| Vulnerabilidades encontradas (Wfuzz) | **0** nuevas (1 informativa, SEC-09); corregidas SEC-03 y SEC-05; **0** respuestas 500 | 1 (SEC-08), corregida |

**Cómo se obtuvo cada cifra**

- **LOC y ELOC:** `cloc src/main` sobre Java, JS, HTML, CSS y SQL. LOC = blank + comment +
  code; ELOC = solo `code`. Es el mismo método del Sprint 5.
- **Cobertura:** JaCoCo con `./mvnw verify`, solo sobre el Java de producción (72 clases). El
  build ahora falla por debajo de 70 % de instrucciones (T4); hoy está en 93.6 %.
- **Casos de Maven:** `./mvnw verify` del 25-10 sobre `0c67c58`: 253 pruebas, 0 fallos, 0
  errores y 0 saltadas. El código de producción es el mismo que en `449a15d`.
- **Katalon:** Louis corrió TS01 a TS06 en Katalon Studio el 24-10, dos veces, con el mismo
  resultado. Desde entonces el código de producción solo cambió en `d2664d7` (SEC-03), que no
  toca la API.
- **Defectos:** issues `bug` + `sprint-6`.
- **Vulnerabilidades:** Wfuzz con `seguridad/correr-wfuzz-sprint6.sh`, dos corridas el 25-10
  con el mismo resultado. Detalle en `seguridad/evidencia-sprint6.md`.
- **Densidad de defectos** = defectos ÷ (ELOC / 1000), con las dos definiciones de siempre:
  **encontrados** (la de la profesora) y **abiertos** al momento de medir.

## Casos de prueba de Katalon

| Suite | Historias | Casos | Pasados | Corrida |
|---|---|---:|---:|---|
| `TS06_Sprint6` | HU-11 (TC-40 a TC-43), HU-12 (TC-44 a TC-49) | 10 | 10 | 24-10-2026 (evidencia en `qa/evidencias/sprint-6/`) |
| `TS05_Sprint5` | HU-09, HU-10 (regresión; TC-31 y TC-39 ajustados a D4 y D5) | 15 | 15 | 24-10-2026 |
| `TS01`–`TS04` | HU-01 a HU-08 (regresión) | 28 | 28 | 24-10-2026 |

Los 45 scripts anteriores pasaron a mandar el token CSRF (`b93ebab`). El historial de fallas
del sprint está en `qa/evidencias/sprint-6/LEEME.md`.

**Revisión manual (25-10):**

- bandeja, 6 de 7 pasos: A4 falla por el #15;
- cámara 5, 8 de 8 pasos.

No entra en la fila de casos pasados: está en el mismo LEEME, secciones 8 y 9.

## Registro de defectos

| Issue | Defecto | Severidad | Estado |
|---|---|---|---|
| [#12](https://github.com/lowilt24/registro-de-naves/issues/12) | Cualquiera puede registrarse como Funcionario DGMM y aprobar trámites ajenos | Alta | **Abierto**: decide la PO. Con este abierto no se cumple la DoD |
| [#13](https://github.com/lowilt24/registro-de-naves/issues/13) | `POST /api/naves` da 500 si el nombre en mayúsculas pasa de 120 caracteres | Media | Corregido en el sprint (`78ae4c8`) |
| [#14](https://github.com/lowilt24/registro-de-naves/issues/14) | `docker compose` no arranca: el mensaje de `DB_PASSWORD` rompe el YAML | Media | Corregido en el sprint (`9754cbd`) |
| [#15](https://github.com/lowilt24/registro-de-naves/issues/15) | La bandeja se desborda horizontalmente con un nombre de nave largo sin espacios | Baja–media | **Abierto** (Ismael) |

No se cuentan como defectos:

- **La observación de usabilidad de la revisión manual:** aprobar no pide confirmación.
  Decisión de Louis.
- **Los hallazgos de seguridad (SEC-xx):** van en la fila de vulnerabilidades.

```bash
gh issue list --label bug --label sprint-6 --state all    # encontrados
gh issue list --label bug --label sprint-6 --state open   # abiertos
```

## Tamaño por lenguaje (`src/main`)

| Lenguaje | Archivos | En blanco | Comentarios | ELOC (code) | LOC |
|---|---:|---:|---:|---:|---:|
| Java | 70 | 668 | 746 | 2,850 | 4,264 |
| JS | 6 | 217 | 198 | 1,153 | 1,568 |
| HTML | 6 | 85 | 23 | 728 | 836 |
| CSS | 1 | 104 | 102 | 663 | 869 |
| SQL | 8 | 80 | 238 | 291 | 609 |
| **Total** | **91** | **1,154** | **1,307** | **5,685** | **8,146** |

Frente al Sprint 5: +7 archivos, +1,035 LOC y +692 ELOC. El grueso está en Java (HU-11, HU-12
y SEC-05) y en JS (la bandeja y la revisión en la cámara 5).

Las pruebas (`src/test`: 17 archivos Java y 1 de propiedades) se cuentan aparte: 3,837 LOC y
2,923 ELOC.

## Cobertura por paquete (JaCoCo)

| Paquete | Rama | Línea |
|---|---:|---:|
| `pa.amp.registro_naves.common` | 100.0 % (10/10) | 91.4 % (64/70) |
| `pa.amp.registro_naves.tramite` | 92.6 % (138/149) | 97.2 % (317/326) |
| `pa.amp.registro_naves.persona` | 91.7 % (22/24) | 94.8 % (183/193) |
| `pa.amp.registro_naves.documento` | 84.4 % (54/64) | 91.4 % (213/233) |
| `pa.amp.registro_naves.config` | 81.2 % (13/16) | 98.2 % (56/57) |
| `pa.amp.registro_naves.usuario` | 79.5 % (31/39) | 85.7 % (132/154) |
| `pa.amp.registro_naves.nave` | 73.5 % (25/34) | 93.5 % (158/169) |
| `pa.amp.registro_naves (raíz)` | sin ramas | 33.3 % (1/3) |

- **`usuario`** subió de 61.5 % a 79.5 % de rama con `RegistroUsuarioIntegrationTest` (T5).
- **`nave` y `documento`:** el resto de sus ramas solo se alcanza con una carrera de
  concurrencia real. Por ejemplo, el `UPDATE` sin filas de `DocumentoService.revisar`.

## Alcance y limitaciones de la medición

- **Solo el Java de producción.** La cobertura no mide el JavaScript del frontend, y por eso
  el #15 (CSS) no lo detecta ninguna prueba automática.
- **Katalon no suma cobertura:** corre fuera de Maven, sin el agente de JaCoCo.
- **El umbral del build es de instrucciones (70 %), no de ramas.** La propuesta del Sprint 4
  hablaba de un piso de rama; hoy las dos están muy por encima.
- **Limitaciones conocidas del producto:**
  - HU-13 pasa al Sprint 7;
  - falta que la PO confirme D5 en Desistida (la nave queda `REGISTRADA`);
  - ITC y SMC siguen sin ser condicionales.

## Cómo reproducir

```bash
./mvnw verify                     # reporte en target/site/jacoco/index.html
cloc src/main --include-lang=Java,JavaScript,HTML,CSS,SQL
cloc src/test
bash seguridad/correr-wfuzz-sprint6.sh
```
