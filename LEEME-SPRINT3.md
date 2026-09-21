# Sprint 3 — HU-05 y HU-06

## Cómo aplicarlo

1. Estar en la rama correcta:
   ```
   git checkout sprint-3
   ```
2. Copiar el contenido de esta carpeta sobre la raíz del repo (combinar `src`, reemplazar archivos).
3. **Borrar** el archivo `src/main/java/pa/amp/registro_naves/persona/PartesController.java`.
   Exponía el catálogo viejo de propietarios y agentes, que ya no existe. Si no lo borrás, no compila.
4. Levantar la base y compilar:
   ```
   docker compose up -d
   .\mvnw.cmd clean verify
   ```

## Qué cambió en el modelo

| Antes (Sprint 2) | Ahora (Sprint 3) |
|---|---|
| `nave.propietario_id` NOT NULL | tabla `nave_propietario` (uno o más, con % de participación) |
| `nave.agente_residente_id` NOT NULL | tabla `agentes_residentes` (una designación por nave, con historial) |
| `propietario` (catálogo) | `propietarios` (con tipo, domicilio, país de constitución) |
| `agente_residente` (catálogo) | `agentes_residentes` (designación + datos del poder) |
| `nave.registrado_por_id` opcional | obligatorio — de ahí sale el control de acceso |

La migración **V4** hace todo eso y mueve los datos existentes. No borra nada que tuviera información.

## Endpoints nuevos

| Método | Ruta | Historia |
|---|---|---|
| POST | `/api/propietarios` | HU-05 — registrar y vincular |
| GET | `/api/naves/{id}/propietarios` | HU-05 — listar |
| POST | `/api/naves/{id}/agente-residente` | HU-06 — designar |
| GET | `/api/naves/{id}/agente-residente` | HU-06 — consultar vigente |
| GET | `/api/naves/{id}/agente-residente/historial` | HU-06 — historial |

## Códigos de respuesta

| Código | Cuándo |
|---|---|
| 201 | Creado |
| 400 | Campo obligatorio faltante o formato inválido |
| 401 | Sin sesión |
| 403 | La nave no le pertenece al usuario |
| 404 | La nave no existe |
| 409 | Regla de negocio: sin propietarios, o participación > 100% |

## Hallazgos de seguridad cerrados en este sprint

- **SEC-01** — JSON mal formado ahora devuelve 400 en vez de 500.
- **SEC-02** — el patrón del nombre rechaza `admin'--` y secuencias similares.
- **SEC-04** — todos los endpoints exigen sesión.
- **SEC-07** — se quitó la configuración residual de H2.

Quedan abiertos **SEC-03** (credenciales en el repo) y **SEC-05** (CSRF).

## Dos cosas que se rompen y hay que arreglar

1. **Las pantallas.** `naves.html` todavía pide propietario y agente en el formulario, y esos campos ya no existen. Hay que actualizar el frontend y agregar las pantallas de HU-05 y HU-06.
2. **Los casos de Katalon de HU-03 y HU-04.** Hoy entran sin login y ahora todo exige sesión. Hay que agregarles el paso de inicio de sesión.

Las dos cosas van en la segunda entrega.
