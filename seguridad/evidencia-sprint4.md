# Evidencia de seguridad — Sprint 4 (Wfuzz)

Fuzzing de los endpoints con `seguridad/correr-wfuzz-sprint4.sh`, Wfuzz en
contenedor Docker contra la app en `localhost:8080`, con sesión iniciada
(`agente@navesitas.pa`). Fecha: 05-10-2026.

Diferencia con la corrida del Sprint 2: desde el Sprint 3 todo `/api/**`
exige sesión (SEC-04), así que el script inicia sesión y manda la cookie;
además se corrigió el cuerpo de `/api/naves` (sin `propietarioId` ni
`agenteResidenteId`) y se agregó la prueba de manipulación del ID de nave
que quedaba pendiente del Sprint 3.

## Resultado

| Prueba | Endpoint | Peticiones | Códigos | Veredicto |
|---|---|---:|---|---|
| 1. HU-04 — inyección en consulta de nombre | `GET /api/naves/disponibilidad?nombre=` | 16 | 200×11, 400×5 | OK: sin 500 |
| 2. HU-03 — tonelaje inválido | `POST /api/naves` | 12 | 400×12 | OK: todo rechazado |
| 3. HU-03 — inyección en el nombre | `POST /api/naves` | 16 | 400×12, 409×4 | OK: inyección rechazada, duplicado bloqueado |
| 4. HU-05 — manipulación del ID de nave | `GET /api/naves/{id}` | 9 | 200×1, 400×2, 403×3, 404×3 | OK: sin acceso a naves ajenas |

**Vulnerabilidades encontradas: 0.** Ninguna petición produjo un error 500,
ninguna inyección se ejecutó y ninguna nave de otro usuario se devolvió.

### Detalle de la prueba 4 (control de acceso, `AccesoNave`)

Con la sesión de `agente@navesitas.pa`:

| ID probado | Respuesta | Por qué |
|---|---|---|
| 1 | 200 | Nave propia |
| 66, 67, 92 | 403 | Naves de otro usuario (`louis@123.com`) |
| 0, -1, 99999999 | 404 | No existen |
| 1.5, abc | 400 | ID no numérico |

## Observación informativa (no es un 500, no se cuenta como vulnerabilidad)

`AccesoNave` responde **403** si la nave existe pero es de otro usuario y
**404** si no existe. Esa diferencia le confirma a quien sondea que un ID
existe (enumeración). Es una decisión **deliberada y documentada** en
`AccesoNave.java`: se dejó distinguible a propósito en este sprint para que
esta misma prueba pueda separar los dos casos. Una versión endurecida
respondería 404 en ambos. Queda anotado para decidir si se endurece en un
sprint siguiente.

## Hallazgos de seguridad abiertos (fuera de Wfuzz)

- **SEC-03**: credenciales del usuario de demo en el repositorio. Por eso los
  reportes crudos (`seguridad/reportes/`) no se versionan: la petición de
  login lleva la contraseña.
- **SEC-05**: CSRF desactivado para `/api/**` (`SecurityConfig`).

## Cómo reproducir

```bash
docker compose up -d
./mvnw spring-boot:run
bash seguridad/correr-wfuzz-sprint4.sh    # reportes en seguridad/reportes/ (no versionados)
```
