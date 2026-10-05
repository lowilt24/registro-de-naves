# Línea base de métricas — Sprint 4

Medición sobre la rama `sprint-4` (commit `b366ea1`), 04-10-2026.

## Resumen para el acta

Tabla en el formato que pidió la profesora, más la cobertura de rama como fila adicional.

| Métrica | Resultado |
|---|---|
| LOC | **5,370** |
| ELOC | **3,843** |
| Cobertura de líneas (JaCoCo) | **88.2 %** (657 de 745 líneas) |
| Cobertura de ramas (JaCoCo), adicional | **73.5 %** (86 de 117 ramas) |
| Casos de prueba pasados: Katalon | **28 de 28** (100 %) |
| Casos de prueba pasados: Maven (JUnit) | **58 de 58** (100 %) |
| Defectos encontrados | **4** |
| Defectos abiertos | **2** |
| Densidad de defectos (encontrados) | **1.04** por KELOC (4 ÷ 3.843) |
| Densidad de defectos (abiertos) | **0.52** por KELOC (2 ÷ 3.843) |
| Vulnerabilidades encontradas (Wfuzz) | ___ (la completa quien lleva seguridad) |

**Cómo se obtuvo cada cifra**

- LOC y ELOC: `cloc src/main` sobre Java, JS, HTML, CSS y SQL. LOC = blank + comment +
  code; ELOC = solo `code` (ver la sección siguiente).
- Cobertura: JaCoCo 0.8.15 con `./mvnw clean verify`, solo sobre el Java de producción
  (51 clases).
- Densidad de defectos = defectos ÷ (ELOC / 1000). Se reporta con las dos definiciones:
  - **encontrados**: todos los defectos detectados en el sprint, incluidos los ya
    corregidos. Es la definición de la profesora.
  - **abiertos**: los que siguen sin corregir al cierre.

## Casos de prueba de Katalon

| Suite | Historias | Casos | Pasados | Corrida |
|---|---|---:|---:|---|
| `TS01_Sprint1` | HU-01, HU-02, infraestructura | 5 | 5 | 04-10-2026 23:54 |
| `TS02_Sprint2` | HU-03, HU-04 | 5 | 5 | 04-10-2026 23:53 y 23:54 |
| `TS03_Sprint3` | HU-05, HU-06 | 10 | 10 | 04-10-2026 23:53 |
| `TS04_Sprint4` | HU-07, HU-08 | 8 | 8 | 04-10-2026 17:36 (evidencia en `qa/evidencias/sprint-4/`) |
| **Total** | | **28** | **28** | |

- Las corridas de TS01, TS02 y TS03 son del PO en Katalon Studio. Sus reportes completos
  están en `qa/registro-naves-qa/Reports/` (excluido del repositorio por SEC-03).
- Una primera corrida de TS01 (23:45) se abortó antes de ejecutar el primer caso porque
  Selenium no pudo crear la sesión del navegador. Fue un fallo del entorno de pruebas, no
  de la aplicación; la corrida de las 23:54 pasó completa.
- La corrida de TS04 es anterior al commit `489fcdc` (22:26), que ajustó el
  `Content-Type` de los casos TC-17 a TC-24. Conviene repetirla para que la evidencia
  corresponda a la versión final de los casos.

## Registro de defectos

Los defectos se registran como issues de GitHub con la etiqueta `bug` y la del sprint
en que se encontraron (`sprint-3`, `sprint-4`). Cada issue cerrado apunta al commit que
lo corrige. Los hallazgos de seguridad (SEC-xx) no se cuentan aquí: van en la fila de
vulnerabilidades.

| Issue | Defecto | Encontrado | Estado |
|---|---|---|---|
| [#4](https://github.com/lowilt24/registro-de-naves/issues/4) | La prueba `reportaNombreDisponible` depende de los datos de la base | Sprint 4 | Cerrado (`01cd437`) |
| [#5](https://github.com/lowilt24/registro-de-naves/issues/5) | Botón anidado dentro de un enlace en "Abrir expediente" | Sprint 4 | Cerrado (`1726070`) |
| [#6](https://github.com/lowilt24/registro-de-naves/issues/6) | Textos visibles sin tildes generados desde JS y backend | Sprint 4 | **Abierto** |
| [#7](https://github.com/lowilt24/registro-de-naves/issues/7) | La fila "Contacto" muestra un punto suelto | Sprint 4 | **Abierto** |
| [#3](https://github.com/lowilt24/registro-de-naves/issues/3) | MIG-01: la V4 falla si no existe el usuario de demo | Sprint 3 | Cerrado en el Sprint 4 (`9ef953b`) |

MIG-01 se encontró en el Sprint 3, así que no entra en el conteo del Sprint 4 aunque se
haya corregido en este sprint.

Para reproducir el conteo:

```bash
gh issue list --label bug --label sprint-4 --state all    # encontrados
gh issue list --label bug --label sprint-4 --state open   # abiertos
```

## LOC frente a ELOC

- **LOC** (*lines of code*): todas las líneas del archivo, incluidas las líneas en
  blanco y los comentarios.
- **ELOC** (*effective lines of code*): solo las líneas con código, es decir, la
  columna `code` de `cloc`, sin blancos ni comentarios.

El proyecto comenta mucho (sobre todo Java y SQL), así que la diferencia no es menor:
la LOC de producción supera a la ELOC en un 40 %. Para
calcular densidades se usa la **ELOC**, porque un comentario no puede tener un defecto
funcional.

## Tamaño por lenguaje (`src/main`)

| Lenguaje | Archivos | En blanco | Comentarios | ELOC (code) | LOC |
|---|---:|---:|---:|---:|---:|
| Java | 53 | 466 | 359 | 1,812 | 2,637 |
| JS | 4 | 125 | 107 | 640 | 872 |
| HTML | 5 | 63 | 12 | 617 | 692 |
| CSS | 1 | 89 | 81 | 559 | 729 |
| SQL | 6 | 63 | 162 | 215 | 440 |
| **Total** | **69** | **806** | **721** | **3,843** | **5,370** |

No entran en el total: los archivos `.properties` (83 LOC / 37 ELOC, que son
configuración) ni las dos licencias OFL de las fuentes (`Text`, 186 líneas, que no
son código). Las pruebas (`src/test`, 7 archivos Java) se cuentan aparte: 1,200 LOC
y 926 ELOC.

## Cobertura por paquete (JaCoCo)

| Paquete | Rama | Línea |
|---|---:|---:|
| `pa.amp.registro_naves.usuario` | 61.5 % (24/39) | 78.6 % (121/154) |
| `pa.amp.registro_naves.documento` | 79.4 % (27/34) | 89.1 % (155/174) |
| `pa.amp.registro_naves.persona` | 79.2 % (19/24) | 94.2 % (178/189) |
| `pa.amp.registro_naves.nave` | 81.2 % (13/16) | 91.3 % (126/138) |
| `pa.amp.registro_naves.config` | 75.0 % (3/4) | 97.1 % (33/34) |
| `pa.amp.registro_naves.common` | sin ramas | 81.1 % (43/53) |
| `pa.amp.registro_naves (raíz)` | sin ramas | 33.3 % (1/3) |

## Alcance y limitaciones de la medición

- **La cobertura mide solo el Java de producción** (51 clases). El JavaScript del
  frontend no está instrumentado.
- **Los casos Katalon no suman cobertura.** Corren contra la app levantada, fuera de
  Maven y sin el agente de JaCoCo. Una decisión que solo cubre Katalon aparece aquí
  como no cubierta.
- **No se fijó ningún umbral** (regla `check` de JaCoCo). La corrida de CI no falla por
  cobertura; el umbral lo decide el equipo con esta cifra en la mano.
- No se modificó ninguna prueba para medir: la cifra es la del estado real de la suite.

## Cómo reproducir

```bash
./mvnw clean verify               # reporte en target/site/jacoco/index.html
cloc src/main                     # tamaño de producción
cloc src/test                     # tamaño de pruebas
```

En CI, cada corrida publica el reporte de JaCoCo como artefacto `reporte-cobertura`.
