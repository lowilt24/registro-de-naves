# Sprint 6 — HU-11 y HU-12, y cierre de la deuda de seguridad

Del 18 al 26-10-2026, en la rama `sprint-6`. Bandeja de trámites de la DGMM (HU-11), revisión
de documentos con cierre del trámite (HU-12), CSRF (SEC-05), credenciales fuera del repo
(SEC-03) y umbral de cobertura en el build (T4).

**Objetivo del sprint:** que la DGMM pueda trabajar sola. Que vea su cola de trámites, revise
cada documento y apruebe una nave que queda abanderada. Y cerrar la deuda de seguridad y de
calidad que venía de sprints anteriores.

> **Borrador del 25-10-2026.** Falta la decisión de la PO sobre el issue #12 (ver
> "Pendiente"). Se completa al cierre.

## Cómo correrlo

Desde este sprint hace falta un archivo `.env` con la contraseña de la base (SEC-03):

```bash
cp .env.example .env      # y poner DB_PASSWORD (en las bases de los sprints 1 a 5 era "registro")
docker compose up -d      # amp-db (PostgreSQL) y amp-correo (Mailpit, http://localhost:8025)
./mvnw verify             # pruebas + JaCoCo; falla si la cobertura baja de 70 %
./mvnw spring-boot:run    # http://localhost:8080
```

Usuarios de demo (contraseña `Navesitas2026*`):

| Correo | Rol | Entra a |
|---|---|---|
| `agente@navesitas.pa` | AGENTE_NAVIERO | Mis naves |
| `revisor@navesitas.pa` | FUNCIONARIO_DGMM | **Bandeja de trámites** (nuevo) |
| `auditor@navesitas.pa` | AUDITOR | **Bandeja de trámites** (nuevo, solo lectura) |

## Qué entra

| Pieza | Qué se hizo | Commit | Dónde |
|---|---|---|---|
| **HU-11** Bandeja de la DGMM (3 pts) | `GET /api/tramites?estado=X` y `GET /api/tramites/conteo-por-estado`. Sin filtro, muestra Solicitud presentada y En revisión, del más antiguo al más nuevo. La DGMM y el auditor entran directo a la bandeja | `c8d5365` | `tramite/BandejaController`, `bandeja.html`, `js/bandeja.js` |
| **HU-12** Revisión de documentos (5 pts) | `POST /api/naves/{id}/documentos/{docId}/revision` con `APROBAR` u `OBSERVAR`. Aprobar el trámite exige los 4 documentos aprobados (D4), y la nave queda `ABANDERADA` o `RECHAZADA` (D5) | `8641ccb` | `documento/DocumentoService`, cámara 5 de `expediente.html`, `js/documentos.js` |
| **V7** | `documentos` suma `observacion`, `revisado_por` y `revisado_en` | `8641ccb` | `V7__revision_de_documentos.sql` |
| **SEC-05** CSRF (T2) | Cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`; el login rota el token | `9f8bdaf`, `2200330` | `SecurityConfig`, `CsrfCookieFilter`, `js/comun.js` |
| **SEC-03** Credenciales (T3) | La contraseña sale de `.env` (no versionado) o de variables de entorno; `.env.example` es la plantilla | `9aad395`, `d2664d7` | `application.properties`, `docker-compose.yml` |
| **T4** Umbral de JaCoCo | `jacoco:check`: el build falla por debajo de 70 % de instrucciones | `9aad395` | `pom.xml` |
| **T5** Ramas de `usuario` y `nave` | 12 pruebas nuevas: correo duplicado, enlace vencido, sesión sin login, etc. | `340f374` | `RegistroUsuarioIntegrationTest` y otras |
| **T6** Katalon y Wfuzz con CSRF | Los 45 scripts de Katalon mandan el token (keyword `Http.enviar`); script nuevo de Wfuzz | `b93ebab`, `85b1a18` | `qa/registro-naves-qa/Keywords/navesitas/Http.groovy`, `seguridad/correr-wfuzz-sprint6.sh` |
| **HU-13** ITC y SMC condicionales | **No entra**: pasa al Sprint 7, porque la regla (D6) no llegó a tiempo | — | — |

**Puntos:** 8 comprometidos (HU-11 + HU-12) y 8 terminados, pendientes de que la PO los acepte.

## Decisiones de la PO

Madeleine aceptó D1 a D8 el 18-10-2026 (detalle en `PLAN-SPRINT6.md` y `DISENO-V7.md`).

| # | Decisión | Cómo quedó |
|---|---|---|
| D1 | Quién ve la bandeja | La DGMM y el auditor (solo lectura); el agente y el ADMINISTRADOR reciben 403 |
| D2 | Cuándo se revisa un documento | Solo con el trámite En revisión, y solo la versión vigente en `PENDIENTE`. Observar exige observación |
| D3 | Observar un documento no mueve el trámite | Implementado: la DGMM observa el trámite aparte |
| D4 | Aprobar el trámite exige los 4 obligatorios en `APROBADO` | 409 con `faltantes` |
| D5 | Estado de la nave al decidir | Aprobada → `ABANDERADA`; Rechazada → `RECHAZADA`; Desistida no la toca. **Falta que la PO confirme lo de Desistida** |
| D6 | Regla de ITC y SMC | No llegó: HU-13 pasa al Sprint 7 |
| D7 | Umbral de JaCoCo | Se implementó 70 % de **instrucciones**, no 80 % de ramas como decía D7. Hoy las dos están por encima (93.6 % y 87.2 %) |
| D8 | Backlog de los Sprints 7 a 9 | Pendiente de la PO |

## Calidad y seguridad

| Métrica | Sprint 6 | Sprint 5 |
|---|---|---|
| LOC / ELOC | 8,146 / 5,685 | 7,111 / 4,993 |
| Cobertura de línea / rama | 93.3 % / 87.2 % | 91.1 % / 81.2 % |
| Maven (JUnit) | 255 de 255 | 205 de 205 |
| Katalon (TS01–TS06) | 53 de 53 | 15 de 15 (TS05) |
| Defectos encontrados / abiertos | 4 / 2 | 2 / 0 |
| Vulnerabilidades (Wfuzz) | 0 nuevas, 0 respuestas 500; SEC-03 y SEC-05 corregidos | 1 (SEC-08), corregida |

Detalle en:

- `qa/metricas/base-sprint-6.md`: línea base;
- `qa/evidencias/sprint-6/LEEME.md`: Katalon, historial de fallas y revisión manual;
- `seguridad/evidencia-sprint6.md`: Wfuzz.

**Defectos del sprint:**

| Issue | Defecto | Severidad | Estado |
|---|---|---|---|
| #12 | Cualquiera puede registrarse como Funcionario DGMM y aprobar trámites ajenos | Alta | **Abierto: decide la PO** |
| #13 | 500 con nombres que crecen al pasar a mayúsculas (`ß` → `SS`) | Media | Corregido (`78ae4c8`) |
| #14 | `docker compose` no arrancaba tras SEC-03 | Media | Corregido (`9754cbd`) |
| #15 | La bandeja se desborda con un nombre de nave largo sin espacios | Baja–media | Abierto; se propone pasarlo al Sprint 7 |

**Revisión manual (25-10):** bandeja, 6 de 7 pasos (A4 falla por el #15); cámara 5, 8 de 8.

Quedó además una **observación de usabilidad**, que no cuenta como defecto: aprobar un documento
o el trámite se ejecuta al primer clic, sin confirmación, y no se puede deshacer.

**Seguridad:** SEC-09 es una observación informativa. Un POST anónimo rechazado por CSRF da
401 y no 403, porque `/error` no es pública. La corrección sugerida es agregar `"/error"` a
`permitAll()`.

## Cambios que afectan a otros

- **`.env` obligatorio.** Sin él, `docker compose up -d` y la app no arrancan. El CI no lo
  necesita, porque usa `SPRING_DATASOURCE_*`.
- **CSRF en todo POST, PUT y DELETE.**
  - Las pantallas lo mandan solas (`comun.js`) y Katalon también (`Http.enviar`).
  - Cualquier script nuevo de pruebas tiene que leer la cookie `XSRF-TOKEN` y mandarla en
    `X-XSRF-TOKEN`.
  - El login borra la cookie, y el token nuevo llega en la petición siguiente.
  - `seguridad/correr-wfuzz-sprint5.sh` ya no sirve: sin token, todos sus POST dan 403.
- **Pruebas de Maven:** `CsrfEnPruebasConfig` mete el token en toda petición de MockMvc, así que
  las pruebas nuevas no necesitan `.with(csrf())`. Que CSRF rechaza de verdad lo prueba
  `CsrfIntegrationTest`.
- **Inicio de sesión por rol:** la DGMM y el auditor entran a `/bandeja.html`, y el agente a
  `/naves.html`.
- **La V7 ya está aplicada** en la base de Louis. Al aplicarla, los documentos existentes
  quedaron en `PENDIENTE`, así que los trámites viejos En revisión necesitan que la DGMM revise
  sus documentos antes de aprobarlos (D4).
- **Historia de la rama (25-10):** un force push reescribió `sprint-6` desde una copia vieja.
  Se recuperó con el merge `3034a3e` sin perder nada. Para la retrospectiva: proteger las ramas
  `sprint-N` contra force push.

## Pendiente

- **PO (Madeleine):**
  - **#12:** corregirlo en este sprint (dos líneas; recomendado) o aceptarlo como limitación
    conocida y pasarlo al Sprint 7. *[Completar con la decisión y la fecha.]*
  - Confirmar D5 en Desistida: la nave queda `REGISTRADA`.
  - Aprobar que el #15 pase al Sprint 7.
  - Responder si una aprobación debería poder deshacerse (observación de usabilidad).
  - Aceptar HU-11 y HU-12, y entregar el backlog de los Sprints 7 a 9 (D8).
- **Ismael:** #15, y el #12 si la PO decide corregirlo ahora.
- **Abdiel:**
  - revisar la corrida de Wfuzz;
  - SEC-09 (`/error` pública);
  - rotar la contraseña de la base: T3 lo pedía y no se hizo, porque la base de desarrollo
    sigue con la de los sprints 1 a 5.
- **Sprint 7 (propuesto):**
  - HU-13 (ITC y SMC condicionales, con la regla D6);
  - alta de funcionarios DGMM, si el #12 se corrige ahora;
  - el #15;
  - confirmación antes de aprobar.
