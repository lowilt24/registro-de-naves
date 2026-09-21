# Evidencia de calidad — Sprint 3

Corrida de la suite `TS03_Sprint3` (HU-05 y HU-06) en Katalon Studio.

| | |
|---|---|
| Fecha | 20-09-2026 22:29 |
| Suite | `TS03_Sprint3` |
| Casos | 10 |
| Resultado | **10/10 PASSED** |
| Duración | 6.163s |
| Entorno | `localhost:8080`, PostgreSQL 17 en Docker (`amp-db`) |

## Archivos

| Archivo | Para qué sirve |
|---|---|
| `TS03_Sprint3-resumen.csv` | Resultado por caso de prueba. Es la evidencia para la lámina de calidad. |
| `TS03_Sprint3-JUnit.xml` | Mismo resultado en formato JUnit, consumible por GitHub Actions. |

## Por qué no está el reporte completo

Katalon deja el reporte completo en `qa/registro-naves-qa/Reports/`, que está en
`.gitignore`. Ese reporte **no se sube al repositorio a propósito**: registra cada
paso del script, incluida la llamada de inicio de sesión con la contraseña del
usuario de demo en texto plano, y los archivos `.har` guardan las peticiones
completas. Subirlo agravaría el hallazgo **SEC-03** (credenciales en el
repositorio), que sigue abierto.

Los dos archivos de esta carpeta se generaron a partir de ese reporte quitando
los pasos internos y redactando la credencial.

Si se necesita el reporte completo para la revisión, está en la máquina donde se
corrió la suite, en `qa/registro-naves-qa/Reports/20260920_222943/TS03_Sprint3/`.

## Cobertura

| Caso | HU | Qué verifica |
|---|---|---|
| `TC_HU05_01_Propietario_Juridico_Vinculado` | HU-05 | Persona jurídica vinculada a nave propia, 100% por defecto |
| `TC_HU05_02_Propietario_Natural_Sin_Pais` | HU-05 | Persona natural no lleva país de constitución |
| `TC_HU05_03_Juridica_Sin_Pais_Rechazada` | HU-05 | Jurídica sin país de constitución se rechaza indicando el campo |
| `TC_HU05_04_Participacion_Excede_100` | HU-05 | La suma de participaciones no puede pasar del 100% |
| `TC_HU05_05_Identificacion_Duplicada` | HU-05 | No se admiten dos propietarios con la misma identificación |
| `TC_HU06_01_Designacion_Valida` | HU-06 | Designación de agente residente y consulta del vigente |
| `TC_HU06_02_Sin_Propietarios_Rechazada` | HU-06 | No se designa agente a una nave sin propietarios |
| `TC_HU06_03_Reemplazo_Y_Historial` | HU-06 | El agente reemplazado se conserva como historial |
| `TC_HU06_04_Datos_Contacto_Invalidos` | HU-06 | Correo y teléfono inválidos se rechazan por campo |
| `TC_HU06_05_Consulta_Sin_Designacion` | HU-06 | Nave sin agente: 404 y historial vacío |
