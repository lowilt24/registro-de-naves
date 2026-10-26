# Evidencia de seguridad — Sprint 6 (Wfuzz)

Fuzzing de HU-11 (bandeja de la DGMM), HU-12 (revisión de documentos) y SEC-05 (CSRF), más
una comprobación de SEC-03, con `seguridad/correr-wfuzz-sprint6.sh`. Wfuzz 3.1.0 en contenedor
Docker (Colima) contra la app en `localhost:8080`.

> **Responsable.** Desde el Sprint 6, seguridad es de Abdiel. Esta corrida la hicieron
> Louis y Claude el 25-10-2026, porque Abdiel tuvo problemas para hacerla. Si Abdiel puede,
> revisa los resultados antes del cierre.

**Dos corridas el 25-10-2026 (20:23 y 20:24), sobre `449a15d`.** El código de producción es
el de `d2664d7`, porque los commits posteriores solo tocan `qa/`. Las dos dieron el mismo
resultado. La única diferencia está en la prueba 6 y es esperada (ver la prueba 6).

**Resultado: 0 respuestas 500, ningún acceso indebido y ninguna vulnerabilidad nueva.** Hay dos
observaciones informativas sobre CSRF (SEC-09 y la nota del *double-submit*). SEC-03 y SEC-05,
abiertos desde el Sprint 4, quedan corregidos.

## Qué cambia respecto del Sprint 5

- **CSRF en todos los POST.** El script maneja el token igual que `comun.js`: cookie
  `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`, con cada sesión guardada en un frasco de cookies
  propio. El login borra la cookie, y el token nuevo llega en la petición siguiente; por eso,
  después del login el script pide `/api/auth/sesion`.
  - El script del Sprint 5 ya no sirve: sin token, todos sus POST dan 403.
- **Pruebas nuevas:** bandeja (HU-11), revisión de documentos (HU-12), CSRF (SEC-05) y
  contraseñas en el repositorio (SEC-03).
- **Diccionario nuevo:** `wordlist-decisiones.txt` (20). Se reutilizan `wordlist-estados.txt`
  (28) y `wordlist-observaciones.txt` (19).
- Igual que en el Sprint 5, el script crea sus naves y un segundo agente validado con
  Mailpit, no depende de IDs fijos y no imprime los IDs de sesión ni los tokens.

## Datos de la corrida

Primera corrida. La segunda creó las naves 1950 a 1952, con el mismo resultado.

| Nave | Dueño | Estado | Para qué |
|---|---|---|---|
| 1947 | `agente@` | En revisión, 4 documentos `PENDIENTE` | Revisión de documentos |
| 1948 | `agente@` | Solicitud presentada | 409 por estado y CSRF (acción inválida, sin efecto) |
| 1949 | segundo agente (`fuzz.s6.…@navesitas.pa`) | En revisión | Nave y documentos ajenos |

## Resultado

| Prueba | Endpoint | Peticiones | Códigos | Veredicto |
|---|---|---:|---|---|
| 1. HU-11: filtro de la bandeja | `GET /api/tramites?estado=` | 28 | 200×7, 400×21 | OK |
| 2. HU-11: acceso por rol | `GET /api/tramites` y `/conteo-por-estado` | 10 | agente 403/403, agente ajeno 403/403, DGMM 200/200, auditor 200/200, sin sesión 401/401 | OK |
| 3. HU-12: ID del documento | `POST /api/naves/1947/documentos/{id}/revision` | 12 | 200×1, 400×5, 404×6 | OK: los documentos de otras naves dan 404 |
| 4. HU-12: ID de la nave | `POST /api/naves/{id}/documentos/2072/revision` | 7 | 200×1, 400×1, 404×5 | OK: el documento no se revisa por la ruta de otra nave |
| 5. HU-12: revisión por rol y por estado | `POST .../revision` (curl) | 5 | agente dueño 403, agente ajeno 403, auditor 403, sin sesión 401; DGMM en Solicitud presentada 409 | OK: el ITC sigue `PENDIENTE` |
| 6. HU-12: campo `decision` | `POST .../revision` | 20 | 200×1, 400×8, 409×11 (2.ª corrida: 400×9, 409×10) | OK |
| 7a. HU-12: `observacion` (lectura y validación) | `POST .../revision` | 19 | 200×1, 400×2, 409×16 | OK |
| 7b. HU-12: `observacion` (guardado y lectura) | `POST .../revision` (curl) | 19 | 200×15, 400×4; lectura del agente y del auditor 200 | OK |
| 8. HU-12: aprobar el trámite sin todo aprobado (D4) | `POST .../tramite/estado` | 1 | 409, con `faltantes: [CERTIFICADO_PROPIEDAD, SMC]` | OK |
| 9. Sin sesión | 4 GET + 1 POST | 5 | 401×5 | OK |
| 10. SEC-05: cabecera `X-XSRF-TOKEN` | `POST .../tramite/estado` | 5 | 400×1 (token correcto), 403×4 | OK |
| 11. SEC-05: casos sueltos | varios (curl) | 5 | ver abajo | OK, con dos observaciones informativas |
| 12. SEC-03: contraseñas en el repositorio | `git ls-files` y `git grep` | — | ver abajo | OK |

En todas las pruebas, la nave ajena terminó con sus 4 documentos en `PENDIENTE`.

### Prueba 3: ID del documento

Con la sesión de la DGMM, observando por la ruta de la nave 1947:

| ID probado | Código | Por qué |
|---|---|---|
| 2071 (Certificado de la 1947) | 200 | Es de esa nave y está `PENDIENTE` |
| 2077 (ITC de la 1948) y 2081 (ITC de la ajena) | 404 | No son de la nave de la ruta |
| 0, -1, 999999999, 2147483648 | 404 | No existen |
| `abc`, `1.5`, `9223372036854775808`, `%27`, `1 OR 1=1` | 400 | No son un ID válido |

La prueba 4 es la misma idea, al revés: el Poder notarial de la 1947 solo se aprueba por la
ruta de la 1947. Por la de la 1948, la de la ajena, 0, -1 o 999999999 da 404; por `abc`, 400.

### Prueba 6: campo `decision`

- `aprobar` (en minúsculas) da 200, porque el servidor acepta mayúsculas o minúsculas y
  espacios al borde.
- Lo inválido da 400: `RECHAZAR`, `PENDIENTE`, `VOLAR`, `APROBAR\u0000`, inyección SQL, HTML,
  `${jndi:…}`, etc.
- Una vez aprobado, el resto da 409: el documento ya no está `PENDIENTE`, y el 409 va antes
  que el 400 en el orden de errores de HU-12.

Wfuzz manda las peticiones en paralelo, así que cuántas inválidas llegan antes que `aprobar`
cambia entre corridas: 8 o 9 con 400.

### Pruebas 7a y 7b: campo `observacion`

- **7a.** La primera observación válida gana (200) y las demás chocan con 409. Los dos
  payloads con carácter nulo dan 400.
  - La que ganó fue `'); DROP TABLE historial_estados;--`. Se guardó como texto: las pruebas
    siguientes leyeron y escribieron con normalidad.
- **7b.** Antes de cada payload, el agente carga un SMC nuevo, así que la DGMM siempre observa
  un documento `PENDIENTE`.
  - 200×15. Los 4 con 400 son los dos con carácter nulo y los dos de más de 1000 caracteres.
  - Después, el agente y el auditor leen el historial de documentos con 200: lo guardado no
    rompe la lectura.

### Prueba 11: CSRF, casos sueltos

Con la sesión del agente, sobre `POST /api/naves/1948/tramite/estado` con una acción
inválida. Si CSRF deja pasar la petición responde 400; si no, 403.

| Caso | Código | Lectura |
|---|---|---|
| a. Sin cabecera `X-XSRF-TOKEN` | 403 | OK |
| b. Cabecera correcta, sin la cookie `XSRF-TOKEN` | 403 | OK |
| c. Cookie y cabecera con el mismo valor inventado | 400 (pasa) | Informativo: es como funciona el *double-submit* (ver abajo) |
| d. Sin sesión ni token: `POST /api/naves` | 401 | Responde la duda pendiente de Abdiel (ver SEC-09) |
| e. Sin sesión ni token: `POST /api/auth/registro` (pública) | **401** | SEC-09 |

El trámite de la 1948 siguió en Solicitud presentada.

### Prueba 12: SEC-03

- `.env` no está versionado.
- Las tres propiedades `spring.datasource.password` leen `${DB_PASSWORD}`.
- `.env.example` trae un marcador, no una contraseña.
- `docker-compose.yml` no tiene ninguna contraseña escrita.
- El script no imprime ningún valor.

## Hallazgos y observaciones

### SEC-03: credenciales en el repo — corregido en `d2664d7`

Abierto desde el Sprint 4. Ver la prueba 12.

- **Queda pendiente:** `9aad395` subió un `.env` con una contraseña real, y `.env.example`
  traía otra. Las dos siguen en la historia pública del repo y se consideran expuestas: no se
  deben usar en ningún entorno.
- **Efecto secundario de la corrección:** `docker-compose.yml` dejó de arrancar. Es el issue
  #14, de calidad: el CI no lo detecta porque no usa `docker compose`.

### SEC-05: CSRF — corregido en `9f8bdaf` y `2200330`

Abierto desde el Sprint 4. Con sesión, todo POST sin el token correcto da 403 (pruebas 10 y
11a–b), y con el token correcto pasa.

**Observación informativa: *double-submit* sin firma.** `CookieCsrfTokenRepository` no guarda
el token en el servidor: solo compara la cookie con la cabecera. Por eso un valor inventado
pasa si viene igual en las dos (caso 11c).

- No es explotable desde otro sitio, porque un atacante no puede escribir cookies en el
  dominio de la app.
- Depende de dos cosas que hoy no se controlan en el código:
  - que no haya subdominios ni HTTP sin cifrar desde donde inyectar una cookie;
  - el `SameSite` de la cookie de sesión. `JSESSIONID` sale sin `SameSite`, así que vale el
    valor por defecto del navegador (`Lax` en Chrome).
- Para producción convendría declarar `SameSite` en `JSESSIONID` y servir solo por HTTPS.
- No se cuenta como vulnerabilidad.

### SEC-09 (baja, informativo): un POST anónimo rechazado por CSRF da 401, no 403

- **Qué pasa:** sin sesión y sin token, `POST /api/auth/registro` da **401**, aunque es una
  ruta pública. Con el token correcto, la misma petición da 400 (validación de campos), así
  que el rechazo viene de CSRF. `POST /api/naves` también da 401.
- **Causa probable:** CSRF responde 403 con `sendError`, y Tomcat reenvía ese error a
  `/error`. Pero `/error` no está entre las rutas públicas de `SecurityConfig`
  (`GET /error` sin sesión da 401), así que el reenvío termina en 401.
  - MockMvc no hace ese reenvío, por eso `CsrfIntegrationTest` ve 403.
  - Con sesión, el reenvío pasa, y por eso los casos 11a y 11b dan 403.
- **Riesgo:** ninguno de acceso, porque la petición se rechaza igual. El problema es el código
  equivocado:
  - para la ruta pública de registro, 401 ("no autenticado") confunde;
  - `comun.js` trata un 401 como sesión vencida y manda al login.
- **Corrección sugerida:** agregar `"/error"` a los `permitAll()` de `SecurityConfig`. Es para
  Abdiel, que lleva SEC-05.
- **Responde la duda pendiente de Abdiel:** en el servidor real, un POST anónimo sin token da
  401, no 403.

## Conteo para la línea base

- **Vulnerabilidades nuevas en el sprint: 0.** SEC-09 es informativo: no da acceso a nada.
- **Corregidas en el sprint:** SEC-03 y SEC-05, las dos abiertas desde el Sprint 4.
- **Respuestas 500 en la corrida: 0.**

## Cómo reproducir

```bash
docker compose up -d          # con .env (ver .env.example)
./mvnw spring-boot:run
bash seguridad/correr-wfuzz-sprint6.sh
cat seguridad/reportes/s6-resumen.txt
```
