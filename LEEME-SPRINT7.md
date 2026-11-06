# Sprint 7 — HU-14 y HU-13: usuarios internos y requisitos documentales

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

## HU-13 — ITC y SMC solo cuando apliquen (D6)

**Estado al pausar (2026-11-06): implementada, pendiente de validación. No se declara aceptada ni cerrada.**

Implementada en Sprint 7 por priorización y aprobación de Madeleine Velásquez (PO), según las instrucciones recibidas el 2026-11-06. HU-14 y su evidencia anterior se conservan.

### Decisión y alcance

| Navegación / servicio | Tipo confirmado | ITC | SMC |
|---|---|---|---|
| INTERNACIONAL | CARGA | Obligatorio con eslora >= 24 m; no aplica por debajo | Obligatorio con arqueo bruto >= 500 GT; no aplica por debajo |
| INTERNACIONAL | PASAJEROS | Obligatorio con eslora >= 24 m; no aplica por debajo | Obligatorio, independientemente del GT |
| Cualquier otro servicio o sin dato | Cualquiera | Requiere revisión | Requiere revisión |
| INTERNACIONAL | MIXTA, TANQUERO, REMOLCADOR, PESQUERA o tipo ausente | Requiere revisión | Requiere revisión |

Los umbrales son inclusivos. Los datos necesarios ausentes o no válidos dejan pendiente solo la regla que los necesita: SMC de pasajeros no depende del GT ni de la eslora; ITC no depende del GT. Certificado de propiedad y poder notarial siguen siendo obligatorios.

Se reutilizan `Nave.tipo`, `Nave.servicio`, `Nave.eslora` y `Nave.tonelajeBruto` (arqueo bruto, GT, no peso). El catálogo actual de servicio es INTERNACIONAL/CABOTAJE/MIXTO: solo INTERNACIONAL habilita las reglas aprobadas. No se inventan alias para NACIONAL ni equivalencias de TANQUERO con CARGA. Los servicios no internacionales quedan pendientes, sin resolver sus requisitos normativos.

**Migraciones necesarias: ninguna.** Los cuatro datos ya existen con validaciones; no se agrega un campo duplicado ni se cambian los registros históricos a INTERNACIONAL. Las ausencias/valores inválidos en altas siguen produciendo 400, incluidos los enums ajenos al catálogo. Los casos incompletos en el evaluador quedan REQUIERE_REVISION.

### Backend y cámara 5

- `RequisitosDocumentoService`: tabla inmutable con umbrales y condiciones; evaluación única OBLIGATORIO/NO_APLICA/REQUIERE_REVISION y explicación por documento.
- `GET /api/naves/{naveId}/documentos/requisitos`: misma autorización de lectura que el expediente (agente propietario, DGMM y auditor); sin exposición a otros agentes.
- Admitir y Subsanar requieren los obligatorios vigentes PENDIENTE o APROBADO. Aprobar exige esos mismos obligatorios en APROBADO. Se retiró la lista fija `TipoDocumento.OBLIGATORIOS`.
- Cualquier requisito pendiente bloquea las tres operaciones con HTTP 409 y un mensaje que incluye REQUIERE_REVISION. Cargar los cuatro PDF no resuelve la excepción.
- No existe mecanismo aprobado para resolver manualmente los casos pendientes; no se agregó uno. Para esas naves el flujo permanece bloqueado hasta definirlo con la PO.
- Cámara 5 consume las decisiones del backend, muestra estados y explicaciones, cuenta solo obligatorios y no ofrece la carga de certificados no aplicables. Si falla la evaluación, no muestra el expediente como completo.
- Los documentos no aplicables ya cargados conservan versiones, estado, historial y descarga; la evaluación no los borra ni modifica. Tampoco cambia los permisos de revisión, la firma PDF, el máximo de 10 MB, CSRF o las validaciones existentes.

### Verificación HU-13

- Maven: `mvnw.cmd -Dlogging.level.root=WARN verify` — **308 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**. Incluye las 272 anteriores y 36 nuevas (19 de evaluación, 17 de integración).
- Casos nuevos: 23.99/24/24.01 m; carga 499/500/501 GT; pasajeros <500 GT y sin GT; servicios no cubiertos; tipos mixtos/no clasificados; datos ausentes; certificados obligatorios y exentos en la misma nave; pendientes con cuatro documentos; consistencia de Admitir/Subsanar/Aprobar; conservación de documentos; permisos y datos inválidos 400.
- JaCoCo: instrucciones **94.11 %**, ramas **88.71 %**, líneas **93.56 %**. Umbral del 70 % aprobado.
- `node --test src/test/js/documentos.test.cjs` — **6/6 aprobadas**: renderizado de decisiones recibidas, no aplicables ausentes, pendientes, conservación de versiones/descargas y fallo de evaluación sin falso completo. Son pruebas de JavaScript con DOM simulado, no una revisión visual en navegador.
- Los fixtures de cuatro suites de regresión que esperaban avanzar con cuatro certificados ahora declaran explícitamente servicio INTERNACIONAL; sus demás comprobaciones se conservan.
- TS07_Sprint7: tres casos nuevos de Katalon (matriz de umbrales/pendientes, flujo condicional y permisos/datos inválidos), con auxiliar `Keywords/navesitas/Hu13.groovy`. **Pendientes de ejecución**: Katalon y Groovy no están disponibles en PATH ni se encontró instalación en las ubicaciones habituales revisadas. Validar XML no equivale a ejecutar estos casos.
- `seguridad/probar-seguridad-sprint7.ps1`: cookies y CSRF reales; permisos, IDOR si hay nave ajena, IDs inválidos y POST rechazados. No crea ni modifica datos; reporte local ignorado en `seguridad/reportes/s7-seguridad.json`. Resultado HTTP final en 8081: **13 correctas, 0 fallidas**; IDOR sin nave ajena disponible queda pendiente en HTTP y está cubierto en integración. Detalles en `seguridad/evidencia-sprint7.md`. Se detuvo solo la instancia temporal; el proceso existente en 8080 se conservó.

### Prueba manual de cámara 5 y flujo completo

1. Arrancar PostgreSQL y la aplicación con la configuración local habitual. No hace falta migración nueva. El pendiente SMTP de HU-14 se conserva.
2. Como agente demo, registrar CARGA / INTERNACIONAL, arqueo bruto 499 GT (neto menor o igual al bruto), eslora 24 m y los demás datos válidos; vincular propietario y designar agente residente.
3. Abrir cámara 5: propiedad, poder e ITC deben aparecer Obligatorio; SMC, No aplica. Su ausencia no debe aparecer como Faltante. Cargar solo los tres obligatorios en PDF.
4. Como DGMM, admitir el trámite. Si se intenta antes de cargar ITC debe responder 409 con ITC en faltantes, sin SMC.
5. Como DGMM, observar el trámite; como agente, subsanarlo. Volver como DGMM, aprobar los tres documentos y aprobar el trámite. Debe quedar APROBADA y la nave ABANDERADA sin exigir SMC.
6. Repetir con 23.99 m y 499 GT: ITC y SMC No aplica; bastan propiedad y poder. Repetir con 24.01 m/500 GT: ambos certificados obligatorios. En PASAJEROS internacional de 100 GT el SMC es obligatorio.
7. Crear otra nave con CABOTAJE o tipo MIXTA: cámara 5 muestra Requiere revisión y la explicación. Admitir debe dar 409 incluso con cuatro PDF. El bloqueo por evaluación pendiente es el mismo para Subsanar y Aprobar; esos estados se cubren en pruebas de integración, sin agregar un atajo manual a la aplicación.
8. Verificar que la descarga y el historial de certificados previamente cargados siguen disponibles aunque su resultado sea No aplica. No existe edición de ficha en el alcance; la prueba de cambio de aplicabilidad se realiza en integración transaccional.

La revisión visual manual de HU-13 queda pendiente. Se autoriza guardar únicamente los 32 archivos de HU-13 en un commit y publicarlo en origin/sprint-7, sin merge a main. `application.properties`, `.env`, secretos y archivos ajenos quedan excluidos. El resultado del push y de GitHub Actions se informa al finalizar; no se anticipa su aprobación en este documento.

### Punto de pausa y pendientes para retomar

| Verificación | Estado real al pausar |
|---|---|
| Maven completo y JaCoCo | Ejecutados: 308/308 pruebas y umbral aprobado; no se repitieron por esta actualización documental |
| JavaScript de cámara 5 | Ejecutado: 6/6, con DOM simulado |
| Seguridad por HTTP mediante PowerShell | Ejecutada: 13 correctas, 0 fallidas; no equivale a Wfuzz |
| IDOR entre agentes en Maven | Ejecutado y aprobado en integración transaccional |
| IDOR entre agentes por HTTP | PENDIENTE: no había una nave ajena disponible en la bandeja; no se declara aprobado |
| Katalon TS07_Sprint7 | NO EJECUTADO: tres casos creados, XML/referencias comprobados; falta ejecutable disponible |
| Wfuzz Sprint 7 | NO EJECUTADO: pendiente preparar/ejecutar el escaneo y conservar evidencia; no se declara aprobado |
| Revisión visual y recorrido manual de HU-13 | PENDIENTE: cámara 5, mensajes, estados y flujo completo en navegador |
| Validación final de la historia por la PO | PENDIENTE: la aprobación de D6 no equivale a aceptación de la implementación |
| SMTP real de HU-14 | Pendiente anterior conservado; no fue validado con HU-13 |

Al retomar: ejecutar TS07 en Katalon; preparar/ejecutar Wfuzz para la superficie HU-13; repetir IDOR por HTTP con dos agentes y una nave ajena conocida; realizar el recorrido visual descrito en la sección de prueba manual y registrar resultados; finalmente solicitar validación de la PO. Los casos normativos no cubiertos siguen en REQUIERE_REVISION hasta que la PO defina su resolución; no se ha agregado un mecanismo manual.

Revisión previa al commit: cambios de HU-13 revisados, configuración local con hash inicial conservado, sin referencias activas a la lista fija OBLIGATORIOS, sin migración adicional. La evidencia ejecutada se conserva sin transformar los pendientes en aprobaciones.

### Inventario de archivos HU-13

- `qa/registro-naves-qa/Keywords/navesitas/Hu13.groovy`
- `qa/registro-naves-qa/Scripts/TC_HU13_01_Umbrales_Y_Pendientes/Script1790000007101.groovy`
- `qa/registro-naves-qa/Scripts/TC_HU13_02_Flujo_Condicional/Script1790000007102.groovy`
- `qa/registro-naves-qa/Scripts/TC_HU13_03_Permisos_Y_Datos_Invalidos/Script1790000007103.groovy`
- `qa/registro-naves-qa/Test Cases/TC_HU13_01_Umbrales_Y_Pendientes.tc`
- `qa/registro-naves-qa/Test Cases/TC_HU13_02_Flujo_Condicional.tc`
- `qa/registro-naves-qa/Test Cases/TC_HU13_03_Permisos_Y_Datos_Invalidos.tc`
- `qa/registro-naves-qa/Test Suites/TS07_Sprint7.groovy`
- `qa/registro-naves-qa/Test Suites/TS07_Sprint7.ts`
- `seguridad/evidencia-sprint7.md`
- `seguridad/probar-seguridad-sprint7.ps1`
- `src/main/java/pa/amp/registro_naves/documento/RequisitosDocumentoController.java`
- `src/main/java/pa/amp/registro_naves/documento/RequisitosDocumentoService.java`
- `src/test/java/pa/amp/registro_naves/documento/RequisitosDocumentoServiceTest.java`
- `src/test/java/pa/amp/registro_naves/tramite/RequisitosDocumentoIntegrationTest.java`
- `src/test/js/documentos.test.cjs`
- `CONTRATO-DOCUMENTOS.md`
- `CONTRATO-TRAMITE.md`
- `DISENO-V7.md`
- `LEEME-SPRINT7.md`
- `src/main/java/pa/amp/registro_naves/documento/TipoDocumento.java`
- `src/main/java/pa/amp/registro_naves/tramite/DocumentosFaltantesException.java`
- `src/main/java/pa/amp/registro_naves/tramite/TramiteService.java`
- `src/main/resources/static/css/app.css`
- `src/main/resources/static/expediente.html`
- `src/main/resources/static/js/documentos.js`
- `src/main/resources/static/js/expediente.js`
- `src/main/resources/static/naves.html`
- `src/test/java/pa/amp/registro_naves/documento/DocumentoRevisionIntegrationTest.java`
- `src/test/java/pa/amp/registro_naves/tramite/BandejaIntegrationTest.java`
- `src/test/java/pa/amp/registro_naves/tramite/TramiteIntegrationTest.java`
- `src/test/java/pa/amp/registro_naves/tramite/TramiteInvariantesIntegrationTest.java`
