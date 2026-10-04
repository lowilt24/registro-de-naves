# Evidencia de calidad — Sprint 4

Corrida de la suite `TS04_Sprint4` (HU-07 y HU-08) en Katalon Studio.

| | |
|---|---|
| Fecha | 04-10-2026 17:36 |
| Suite | `TS04_Sprint4` |
| Casos | 8 (TC-17 a TC-24) |
| Resultado | **8/8 PASSED** |
| Duración | 8.210s |
| Entorno | `localhost:8080`, PostgreSQL 17 en Docker (`amp-db`), Katalon Studio 11.5.0 |

## Archivos

| Archivo | Para qué sirve |
|---|---|
| `TS04_Sprint4-resumen.csv` | Resultado por caso de prueba. Es la evidencia para la lámina de calidad. |
| `TS04_Sprint4-JUnit.xml` | Mismo resultado en formato JUnit. |

Igual que en el Sprint 3, el reporte completo de Katalon no se sube: registra la
llamada de inicio de sesión con la contraseña del usuario de demo (hallazgo
**SEC-03**, abierto). Estos dos archivos se generaron a partir de ese reporte
quitando los pasos internos y la salida de consola. El completo está en la
máquina donde se corrió, en
`qa/registro-naves-qa/Reports/20261004_173638/TS04_Sprint4/`.

## Cobertura

| Caso | Script | HU | Qué verifica | Espera |
|---|---|---|---|---|
| TC-17 | `TC_HU07_01_Carga_PDF_Valida` | HU-07 | Carga, hash SHA-256, tamaño; recargar crea la versión 2 | 201 |
| TC-18 | `TC_HU07_02_Archivo_No_PDF` | HU-07 | Texto y ejecutable con nombre `.pdf`, rechazados por la firma binaria | 415 |
| TC-19 | `TC_HU07_03_Archivo_Sobre_Limite` | HU-07 | PDF de 10 MB + 1 KB | 413 |
| TC-20 | `TC_HU07_04_Nave_Ajena` | HU-07 | Carga en nave ajena / inexistente | 403 / 404 |
| TC-21 | `TC_HU08_01_Consulta_Con_Documentos` | HU-08 | Listado con tipo, fecha, versión y estado | 200 |
| TC-22 | `TC_HU08_02_Consulta_Sin_Documentos` | HU-08 | Nave sin documentos | 200 `[]` |
| TC-23 | `TC_HU08_03_Filtro_Por_Tipo` | HU-08 | Filtros por tipo y estado; tipo inventado | 200 / 400 |
| TC-24 | `TC_HU08_04_Descarga_Nave_Ajena` | HU-08 | Descarga ajena, IDOR con nave propia, sin sesión | 403 / 404 / 401 |

## Nota sobre la primera corrida

Las corridas anteriores de la misma tarde fallaron por dos motivos ajenos a la
aplicación: la primera, porque la aplicación no estaba levantada; la segunda,
porque los scripts no enviaban el `Content-Type` multipart y el servidor
respondía 415 a toda carga. Corregido en el commit "Katalon TC-17 a TC-24:
Content-Type multipart". En esa segunda corrida TC-18 pasó por casualidad
(esperaba 415 y recibió 415 por otra causa); desde la corrección también
verifica `campos.archivo`, que solo aparece cuando el rechazo viene de la firma
del PDF.
