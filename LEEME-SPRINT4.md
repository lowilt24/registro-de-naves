# Sprint 4 — HU-07 y HU-08

Carga y consulta de documentos de la nave, más las observaciones de la
profesora y el arreglo de MIG-01.

## Cómo correrlo

```
docker compose up -d
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

`docker compose up -d` levanta dos cosas: PostgreSQL y **Mailpit**, el
servidor de correo de desarrollo. Los correos de validación no salen a
internet: se ven en <http://localhost:8025>.

Usuarios de demo (contraseña `Navesitas2026*`), ya validados por la V5:

| Correo | Rol |
|---|---|
| `agente@navesitas.pa` | AGENTE_NAVIERO |
| `revisor@navesitas.pa` | FUNCIONARIO_DGMM |
| `auditor@navesitas.pa` | AUDITOR (nuevo) |

## Qué entra

| Pieza | Dónde |
|---|---|
| HU-07: carga con firma `%PDF-`, límite 10 MB, hash SHA-256, versiones | `documento/DocumentoService`, `AlmacenArchivos` |
| HU-08: consulta con filtros, descarga con verificación de dueño | `documento/DocumentoController` |
| Pantalla (cámara 5) | `expediente.html`, `js/documentos.js` |
| Tabla `documentos`, hora de la nave, rol, validación de correo | `V5__documentos_y_observaciones.sql` |
| Arreglo de MIG-01 | `V3_1__propietario_de_naves_huerfanas.sql` |
| Validación de correo | `usuario/UsuarioService`, `AuthController`, `login.html` |
| Katalon TC-17 a TC-24 | suite `TS04_Sprint4` |
| Contrato de la API | `CONTRATO-DOCUMENTOS.md` |

Los PDF se guardan en `almacen/`, en la raíz del proyecto, fuera de
`static/`: no tienen URL. La única forma de bajarlos es el endpoint de
descarga, que verifica que la nave sea del usuario. La carpeta está en
`.gitignore`.

## MIG-01: por qué es V3_1 y no parte de la V5

La V4 asigna las naves sin dueño a `agente@navesitas.pa` y después vuelve
obligatoria la columna. Si ese usuario no existe, la V4 falla y se revierte.
La V5 no lo puede arreglar porque corre **después** de la V4. Y la V4 no se
puede editar: las bases que ya la tienen guardan su checksum y Flyway se
negaría a arrancar.

La V3_1 corre antes de la V4 en las bases que todavía están en la V3. En las
que ya pasaron la V4 también corre (`spring.flyway.out-of-order=true`) pero no
hace nada.

Probado en los tres escenarios del plan, con PostgreSQL:

| Escenario | Resultado |
|---|---|
| Base vacía, V1 → V5 | Pasa |
| Base en V4 con datos de los sprints 1 a 3 | Pasa; aplica V3_1 (sin efecto) y V5 |
| Base en V3, naves sin dueño, **sin** el agente de demo | Pasa; las naves quedan a nombre de `sistema@navesitas.pa` (inactivo) |
| El mismo, **sin** V3_1 (control) | Falla en la V4: `column "registrado_por_id" contains null values` |

## Pruebas

`mvnw verify`: **58 pruebas**, todas pasan. Nuevas: 18 en
`DocumentoIntegrationTest` y 9 en `ValidacionCorreoIntegrationTest`.

## Cambios que afectan a otros

- **Una cuenta nueva no entra hasta validar el correo.** El login avisa y
  ofrece reenviar el enlace.
- **Nadie se puede registrar como AUDITOR ni ADMINISTRADOR.** Antes el
  formulario de registro aceptaba cualquier rol que mandara el cliente.
- `NaveResponse` ya no trae `fechaRegistro`: ahora es `creadoEn`, con zona.
- Errores del cliente que antes daban 500 ahora dan su código: ruta
  inexistente 404, método no permitido 405, parámetro mal formado 400,
  tipo de contenido equivocado 415.

## Pendiente

- **Listado de usuarios.** La observación pide que el rol "se muestre en el
  listado de usuarios", y ese listado no existe. El rol ya está explícito en
  la base; falta la pantalla.
- **SEC-03 y SEC-05** siguen abiertos (credenciales en el repo, CSRF).
- Spring Security revisa si la cuenta está deshabilitada **antes** que la
  contraseña: con cualquier contraseña, el login dice si un correo tiene una
  cuenta sin validar. Es comportamiento de Spring; para el informe de
  seguridad.
