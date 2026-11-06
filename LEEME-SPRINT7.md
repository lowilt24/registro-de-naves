# Sprint 7 — HU-14: alta administrativa de usuarios internos

Verificado el 2026-11-06 en `sprint-7`, sobre `50611de` (correccion #12).

## Comportamiento

- Solo ADMINISTRADOR accede a `/admin-usuarios.html` y `POST /api/admin/usuarios`.
- El login del administrador abre directamente el alta administrativa, con cierre de sesion.
- El alta acepta exclusivamente FUNCIONARIO_DGMM y AUDITOR. El registro publico sigue limitado a AGENTE_NAVIERO.
- Las cuentas nuevas deben confirmar el correo antes de entrar. La respuesta no incluye hash de contrasena ni token.
- V8 agrega la cuenta demo `admin@navesitas.pa`, con la contrasena de las cuentas demo existentes.
- CSRF permanece activo: cookie XSRF-TOKEN y cabecera X-XSRF-TOKEN.

## Bloqueo de Maven / Flyway

No habia procesos Java activos al diagnosticar. `mvnw clean` reprodujo el error al eliminar `target/classes/db/migration`. Las carpetas generadas tenian el atributo ReadOnly y estaban bajo OneDrive. Se quito ReadOnly exclusivamente de `target` y sus descendientes (con ruta absoluta verificada). No se modificaron ACL, fuentes ni datos existentes de PostgreSQL. `mvnw clean` termino con BUILD SUCCESS.

La reconstruccion copio `V8__administrador_demo.sql` con V mayuscula. Flyway valido nueve migraciones y aplico V8: esquema public de version 7 a 8.

## Evidencia automatizada

Comando final: `mvnw.cmd -Dlogging.level.root=WARN verify`.

- 272 pruebas: 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS.
- Incluye las 256 anteriores y 16 nuevas de AdminUsuarioIntegrationTest.
- Permisos: anonimo y los tres roles no administradores; acceso a pagina y endpoint.
- Creacion y login de ambos roles, bloqueo antes de validar, activacion y enlace de un solo uso.
- Datos faltantes/invalidos, JSON incorrecto, rol nulo/desconocido/no permitido, correo duplicado ignorando mayusculas.
- Contrasena BCrypt y token almacenado como SHA-256; ausencia de campos sensibles en respuesta.
- CSRF sin token y con token incorrecto: 403; cookie y cabecera reales: 201. La suite nueva no usa el auxiliar csrf() para evitar sustituir el repositorio real de tokens.
- JaCoCo: instrucciones 93.76 %, ramas 87.65 %, lineas 93.37 %. Umbral del 70 % aprobado.
- JavaScript de login y alta: sintaxis comprobada con Node. `git diff --check` sin errores.

La primera corrida detecto dos problemas en las pruebas nuevas (expectativa 302 frente a 401 sin Accept HTML, e interferencia del auxiliar csrf() con la cookie real). Se corrigieron y la corrida completa final paso.

## Comprobacion de arranque y limite del entorno

`spring-boot:run` arranco; login HTTP 200 con cookie CSRF. La aplicacion temporal (PID 15284) se detuvo despues de verificar su clase principal y su padre Maven del proyecto (PID 3336). No se detuvieron contenedores ni procesos ajenos.

`/actuator/health` respondio 503 por timeout SMTP. `amp-db` estaba activo y `amp-correo` figuraba healthy; una conexion al puerto local 1025 tampoco recibio saludo SMTP. No se modifico la configuracion local para ocultar este fallo. Las pruebas de validacion usan NotificadorCorreo sustituido: prueban el enlace y la activacion, pero no confirman entrega SMTP real. En esa comprobacion automatizada no se hizo revision visual en navegador; la prueba manual posterior se registra a continuacion.

`application.properties` conserva su hash SHA-256 inicial: `52A6A089EB37FE04D6C821350E875CC348A8E73DF564A0783D7761070229E6A4`. Se conservaron los cambios previos y no se mostraron secretos de .env. Sin commit, push ni merge.

## Evidencia manual reportada por el usuario — 2026-11-06

El usuario confirmo que la prueba manual de HU-14 funciono para FUNCIONARIO_DGMM y AUDITOR, activando ambas cuentas mediante los enlaces obtenidos del log de la aplicacion.

| Rol | Resultado manual reportado | Activacion |
|---|---|---|
| FUNCIONARIO_DGMM | Correcto | Enlace del log |
| AUDITOR | Correcto | Enlace del log |

Esta evidencia confirma el flujo manual de alta y activacion de ambos roles. No acredita entrega de correo por SMTP. El envio real por SMTP permanece pendiente; se conserva el diagnostico de timeout anterior. No se incluyen correos de las cuentas probadas ni tokens de activacion en este documento.

## Archivos propuestos para el commit de HU-14

Rutas relativas a la raiz del repositorio. Lista revisada sin modificar el area de staging.

| Archivo | Contenido |
|---|---|
| `src/main/java/pa/amp/registro_naves/config/SecurityConfig.java` | Acceso administrativo y destino del login por rol; CSRF activo. |
| `src/main/java/pa/amp/registro_naves/usuario/UsuarioService.java` | Alta limitada a DGMM/AUDITOR y logica compartida de cuenta y validacion. |
| `src/main/java/pa/amp/registro_naves/usuario/AdminUsuarioController.java` | POST administrativo con validacion de entrada y respuesta sin secretos. |
| `src/main/resources/db/migration/V8__administrador_demo.sql` | Administrador demo; nombre de migracion con V mayuscula. |
| `src/main/resources/static/admin-usuarios.html` | Formulario de alta, tratamiento de errores, CSRF y cierre de sesion. |
| `src/main/resources/static/login.html` | Texto de acceso para los cuatro roles. |
| `src/test/java/pa/amp/registro_naves/usuario/AdminUsuarioIntegrationTest.java` | 16 pruebas de HU-14, incluidos permisos, activacion y CSRF real. |
| `LEEME-SPRINT7.md` | Evidencia automatizada, manual, diagnostico y pendiente SMTP. |

Excluidos del commit: `src/main/resources/application.properties` (configuracion local conservada) y `.env` (ignorado por Git; sin leer ni mostrar secretos). No usar `git add .` para preparar este commit.

Revision del diff: no se identificaron bloqueos funcionales adicionales para HU-14; SecurityConfig y UsuarioService incluyen tambien cambios previos de formato y comentarios que se conservaron. `git diff --check` sin errores en archivos rastreados. La evidencia automatizada corresponde a la verificacion anterior de 272/272; esta actualizacion documental no requirio repetir Maven. Pendiente operativo: entrega SMTP real.
