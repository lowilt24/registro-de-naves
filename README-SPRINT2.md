# Sprint 2 — HU-03 y HU-04

Código para el sprint de registro de nave y consulta de disponibilidad de nombre.
Se apoya sobre el esqueleto que ya está en `main` (Spring Boot 4.1.1, Java 21,
Flyway, JPA, Security).

## Qué se entrega

| Historia | Dónde vive |
|---|---|
| HU-04 — disponibilidad de nombre | `NaveService.consultarDisponibilidad`, `GET /api/naves/disponibilidad` |
| HU-03 — registro de nave | `NaveService.registrar`, `POST /api/naves` |
| Pantallas | `static/naves.html` + `static/js/naves.js` |
| Esquema | `db/migration/V2__usuarios_personas_naves.sql`, `V3__datos_demo.sql` |
| Pruebas | `NaveIntegrationTest` (espejo de TC-01…TC-06), `NormalizacionNombreTest` |
| Fuzzing | `seguridad/correr-wfuzz.sh` + wordlists |
| CI | `.github/workflows/ci.yml` |

También van incluidas las tablas y endpoints de **HU-01 / HU-02** (usuario,
login) y de **propietario / agente residente**, porque HU-03 no se puede cumplir
sin ellos: la historia exige asociar la nave a un propietario y un agente ya
registrados.

## Cómo correrlo

```bash
git checkout -b sprint-2
# copiar los archivos de esta carpeta sobre la raíz del repo
./mvnw -B verify          # compila y corre las pruebas
./mvnw spring-boot:run
```

Luego: <http://localhost:8080/naves.html>

Cuentas de demo (contraseña `Navesitas2026*`):
- `agente@navesitas.pa` — rol agente naviero
- `revisor@navesitas.pa` — rol funcionario DGMM

Consola de la base: <http://localhost:8080/h2-console>
(JDBC URL `jdbc:h2:file:./data/navesitas`, usuario `sa`, sin contraseña).

## Cómo se cumple HU-04

El bloqueo de nombre duplicado está en **tres capas**, a propósito:

1. **Pantalla** — el formulario nace deshabilitado; se habilita solo cuando la
   consulta devuelve `disponible: true`. Si el usuario edita el nombre después,
   se vuelve a cerrar.
2. **Servicio** — `registrar()` vuelve a verificar antes de guardar, por si
   alguien llama el endpoint directo con Postman o Wfuzz.
3. **Base de datos** — `UNIQUE (nombre_normalizado)` cierra la carrera entre dos
   registros simultáneos. Si salta, se traduce a un 409 con el mismo mensaje.

Los nombres se comparan normalizados (mayúsculas, espacios colapsados), así que
`"estrella  del istmo"` y `"Estrella Del Istmo"` cuentan como el mismo nombre.

## Pendientes antes de la presentación

- [ ] Correr `./mvnw verify` y confirmar que las 11 pruebas pasan.
- [ ] Levantar la app y tomar las 6 capturas de la lámina de evidencia.
- [ ] Armar los test cases en Katalon Studio siguiendo `KATALON.md`.
- [ ] Correr `seguridad/correr-wfuzz.sh` y llenar la lámina de hallazgos.
- [ ] Decidir Oracle vs H2/PostgreSQL (ver `NOTAS-TECNICAS.md`).
