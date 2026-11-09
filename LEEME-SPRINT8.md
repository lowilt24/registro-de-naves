# Sprint 8 — HU-15 y HU-16

Implementada y verificada el 9 de noviembre de 2026 en `sprint-8`.
Descarga y contenido del PDF confirmados manualmente por la usuaria.
HU-13 (ITC/SMC) corresponde al trabajo adelantado en Sprint 7.

## Comportamiento

Contrato: [CONTRATO-CONSTANCIA.md](CONTRATO-CONSTANCIA.md).

- Tras aprobar, el trámite queda APROBADA y la nave REGISTRADA. No es un
  abanderamiento ni una simulación de patente.
- El paso 6 del expediente aprobado muestra «Descargar constancia de registro
  (PDF)». Disponible para agente dueño, DGMM y auditor.
- `GET /api/naves/{naveId}/constancia`: PDF adjunto y `X-Content-SHA256`.
  Sin sesión 401; otros roles/agentes ajenos 403; estado no aprobado 409;
  nave inexistente 404. Los permisos se verifican antes del estado.
- Número de registro = ID existente de la nave. Incluye nombre, tipo,
  tonelajes, dimensiones, todos los propietarios y sus porcentajes, agente
  residente, fecha y funcionario del evento de aprobación.
- Primera descarga: genera y conserva PDF y SHA-256 en una transacción.
  Las siguientes verifican el hash y entregan los mismos bytes. Un bloqueo
  de fila evita dos constancias diferentes en primeras descargas simultáneas.
- PDFBox 3.0.8 genera el PDF con fuente Unicode embebida, ajuste de líneas
  y páginas. Incluye acentos del español. Si un carácter no está cubierto
  por la fuente, devuelve 409 explicativo sin sustituir el nombre ni guardar
  un PDF corrupto. No tiene firma digital y lo indica expresamente.
- Un registro histórico sin evento APROBAR, propietarios o agente residente
  devuelve 409 explicativo; no se inventa una fecha ni un funcionario.

## Migración V9

Agrega `constancias_registro`: PDF BYTEA, SHA-256, evento de aprobación y fecha
de generación; una fila por nave. Al guardar PDF y hash juntos se evita la
descoordinación entre disco y base de datos. Las cargas de HU-07 no cambian.

Corrige a REGISTRADA las naves con valor legado ABANDERADA y trámite APROBADA.
No modifica el historial ni las migraciones previas. El enum antiguo permanece
solo por compatibilidad histórica. V9 se aplica al iniciar la aplicación.

## Evidencia ejecutada

Se usó PostgreSQL 17 temporal, en el puerto 55432, separado de `amp-db`.
Aplicación de QA temporal en 8082; la instancia del usuario no se detuvo.
Los datos de demostración generados para estas verificaciones solo existieron
en esa base temporal. No se cambiaron `.env` ni `application.properties`.

- Maven `verify`: **332 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
  Incluye 24 nuevas de constancias y la regresión completa de HU-13 y HU-12.
- JaCoCo: instrucciones **94.28 %**, ramas **87.74 %**, líneas **93.74 %**.
  Umbral del 70 % aprobado.
- JavaScript: `node --test src/test/js/documentos.test.cjs src/test/js/constancia.test.cjs`:
  **12/12 aprobadas**, seis nuevas de constancia. Sintaxis de los dos scripts
  afectados y `git diff --check` aprobados.
- Integración: roles, cinco estados no aprobados, ID inválido/inexistente,
  SHA-256 persistido, hash alterado, acentos, porcentajes, datos completos,
  fecha en America/Panama y funcionario real, múltiples páginas y nombres
  largos, repetición estable y dos primeras descargas concurrentes.
- Migración: esquema real validado por Flyway/JPA. El UPDATE de V9 se probó
  con rollback sobre una nave aprobada y otra no aprobada: solo corrigió la
  aprobada, sin alterar la otra.
- HTTP con sesiones y CSRF reales: registrar nave CARGA/INTERNACIONAL/24 m/
  499 GT, propietarios 60/40, agente residente, tres PDF (sin SMC), admitir,
  aprobar documentos, aprobar trámite y descargar. Antes 409; después
  agente/DGMM/auditor 200, administrador 403, sin sesión 401. Hash verificado
  y archivos idénticos entre roles y primeras descargas concurrentes.
- Edge: inicio de sesión real de los tres roles autorizados, expediente
  aprobado a **1440 y 390 px**, sin desbordamiento horizontal y con foco
  visible. El PDF descargado con el botón coincidió byte por byte con HTTP.
  Se inspeccionaron visualmente capturas del expediente y del PDF renderizado.
- Primera corrida detectó un error de mapeo del identificador; se corrigió
  antes de la verificación final completa. El primer script de navegador
  intentó leer el archivo antes de completarse la descarga; se ajustó la
  espera del script y la comprobación final pasó.

Artefactos regenerables, ignorados por Git, en `target/`: logs de verificación,
PDF e imagen de ejemplo, PDF HTTP y capturas de Edge por rol y tamaño.
No se ejecutaron Katalon ni Wfuzz para HU-15; esta evidencia no los sustituye.

### Confirmación manual reportada por la usuaria — 9 de noviembre de 2026

Después del reinicio completo de la aplicación, la usuaria confirmó que pudo
descargar la constancia desde el sistema. Al solicitarle revisar los datos del
PDF contra el expediente, confirmó también su conformidad con el contenido.
Esta confirmación corresponde a descarga y contenido; las comprobaciones de
otros roles y errores se respaldan por la evidencia automatizada anterior.

## Prueba manual en desarrollo

1. Reiniciar la aplicación con PostgreSQL disponible para aplicar V9.
2. Entrar como agente dueño y abrir el expediente de una nave con trámite
   APROBADA. Si aún no está aprobado, completar el recorrido de HU-13.
3. Confirmar nave REGISTRADA y, en el paso 6, descargar la constancia.
4. Revisar todos los datos del PDF contra la ficha y el historial; descargar
   otra vez y comparar SHA-256 con `Get-FileHash -Algorithm SHA256`.
5. En sesiones separadas, repetir como DGMM y auditor. Deben descargar la
   misma constancia; un agente ajeno y administrador deben recibir 403.
6. Para una nave no aprobada, el botón no aparece y una petición directa
   autorizada a la constancia responde 409.

### Si aparece un error 500 al descargar después de actualizar

PDFBox es una dependencia nueva. Una aplicación que ya estaba ejecutándose
antes de modificar `pom.xml` puede recargar las clases con DevTools sin
incorporar los JAR nuevos a su classpath. Ni Ctrl+F5 ni la recarga de DevTools
sustituyen el reinicio completo de Maven: detener el proceso de la aplicación
y volver a ejecutar `mvnw.cmd spring-boot:run`, después iniciar sesión de nuevo.

Este caso se reprodujo en la instancia local del usuario: V9 estaba aplicada,
pero el proceso anterior no tenía PDFBox ni FontBox en su classpath. Se reinició
únicamente la aplicación; PostgreSQL y los demás servicios se conservaron.
Después del reinicio, la constancia del expediente afectado respondió 200,
`application/pdf`; su SHA-256 coincidió con el encabezado del servidor y una
segunda descarga entregó exactamente los mismos bytes.

## Archivos de implementación

- `pom.xml`: dependencia PDFBox.
- `src/main/java/pa/amp/registro_naves/constancia/`: entidad, repositorio,
  servicio, controlador y maquetación del PDF.
- `TramiteRepository.java`: bloqueo para emitir una sola constancia.
- `TramiteService.java` y `EstadoNave.java`: resultado de registro y
  compatibilidad con el estado legado.
- `V9__constancias_de_registro.sql`: persistencia y corrección de terminología.
- `expediente.html`, `js/constancia.js`, `js/tramite.js`: descarga en el paso 6.
- `ConstanciaRegistroIntegrationTest.java`, `constancia.test.cjs`: pruebas nuevas.
- `DocumentoRevisionIntegrationTest.java`, `RequisitosDocumentoIntegrationTest.java`:
  expectativas de nave REGISTRADA después de aprobar.
- `CONTRATO-CONSTANCIA.md` y este documento: alcance, decisiones y evidencia.

Los cambios anteriores de diseño y configuración y las cuatro eliminaciones
locales encontradas al inicio se conservaron. No forman parte de HU-15.

## HU-16 — Avisos por correo al agente

Implementada y verificada el 9 de noviembre de 2026. La usuaria confirmó que
funciona después de recibir el recorrido manual de prueba con Mailpit.
Contrato: [CONTRATO-AVISOS.md](CONTRATO-AVISOS.md).

Confirmación manual reportada el 9 de noviembre de 2026: «sí funciona».
No se detallaron los pasos ejecutados ni se reportó una comprobación individual
de todos los tipos de aviso; esos casos se respaldan con la evidencia automatizada.

### Comportamiento

- Avisa al agente que registró la nave cuando la DGMM admite, observa,
  aprueba o rechaza el trámite, o cuando observa un documento.
- Incluye nave y número de registro, acción, estado o documento/versión,
  observación si existe y enlace al expediente con inicio de sesión.
- No envía avisos por aprobar documentos ni por acciones del propio agente
  (subsanar/desistir). No adjunta documentos ni incluye secretos del sistema.
- Reutiliza `NotificadorCorreo` y su transporte SMTP. En desarrollo los
  mensajes se capturan en Mailpit: `http://localhost:8025`, no en correo externo.
- Los eventos contienen una instantánea de datos simples; el listener
  AFTER_COMMIT no accede a entidades JPA. El envío ocurre solo tras confirmar
  la decisión. Un rollback o una operación rechazada no envían correo.
- Si falla SMTP o no está configurado, la decisión queda guardada y un WARN
  `HU-16` registra referencia, nave y clase del error, sin destinatario ni
  observación. No hay cola persistente ni reintentos automáticos; tampoco se
  promete entrega si el proceso se interrumpe después del commit.
- No hay migraciones, dependencias ni pantallas nuevas para HU-16.

### Corrección de SMTP en desarrollo

Se reprodujo el timeout del saludo SMTP tanto en el Mailpit de desarrollo
como en uno temporal. La opción `--smtp-disable-rdns`, comprobada con el
ejecutable de Mailpit v1.31.1, eliminó la espera de DNS inverso del puente Docker.
Se agregó únicamente esa opción a `docker-compose.yml` y se validó el archivo.

Antes de recrear `amp-correo` se comprobó por API que contenía **0 mensajes**;
se aplicó `docker compose up -d --no-deps correo`, sin recrear PostgreSQL ni
modificar `.env` o `application.properties`. El servidor volvió a responder
inmediatamente con SMTP 220. No se perdieron mensajes existentes.

### Evidencia ejecutada de HU-16

- Pruebas específicas: **23/23 aprobadas** (19 de integración y 4 unitarias).
  Incluyen las cuatro decisiones DGMM, observación de documento, envío solo
  tras commit, rollback sin envío, fallo SMTP sin revertir trámite/documento
  y con registro de error, roles rechazados, operación inválida, repetición
  sin duplicar aviso y acciones que no deben enviar correo.
- Regresión completa en PostgreSQL 17 temporal: **355 pruebas, 0 fallos,
  0 errores, 0 omitidas; BUILD SUCCESS**. Incluye HU-13 y HU-15.
- JaCoCo: instrucciones **94.69 %**, ramas **88.64 %**, líneas **94.26 %**;
  umbral del 70 % aprobado. `git diff --check` aprobado.
- SMTP real: aplicación temporal en 8082, PostgreSQL temporal en 55432 y
  Mailpit temporal en 11026/18026; no se usó la base del usuario.
- Recorrido HTTP con sesiones y CSRF reales: registrar nave, propietario y
  agente residente; subir propiedad, poder e ITC; admitir; observar ITC;
  cargar su versión nueva; aprobar documentos; observar trámite; subsanar;
  aprobar trámite y descargar la constancia de HU-15.
- Mailpit recibió **4 avisos**: Admitir, Observar documento, Observar trámite
  y Aprobar trámite. Se verificaron destinatario único (agente dueño), nave,
  enlace correcto y ambas observaciones. No se enviaron avisos por aprobar
  documentos o subsanar. La constancia de HU-15 siguió respondiendo 200.
- Artefactos de QA ignorados por Git en `target/hu16-*`: logs y resultado HTTP
  sin contenido de correo ni credenciales. Los servicios temporales de QA se
  detuvieron al finalizar; la aplicación y los servicios del usuario permanecen.
- Katalon y Wfuzz no ejecutados para esta historia. No se acredita entrega
  a un proveedor externo: el transporte real se verificó contra Mailpit.

### Recorrido manual de HU-16

1. Usar una nave cuyo trámite no sea final. En Edge normal, agente; en
   InPrivate, DGMM, para mantener sesiones independientes.
2. Como DGMM, admitir el trámite o, en revisión, observar un documento con un
   comentario. También se puede observar el trámite con una observación.
3. Abrir `http://localhost:8025`: buscar el mensaje dirigido al correo del
   agente dueño. Revisar nave, acción, comentario y enlace al expediente.
4. Abrir el enlace desde la sesión del agente: debe mantener los permisos
   habituales y solicitar login si no hay sesión.
5. La constancia ya aprobada no se modifica para probar avisos; no se envían
   retrospectivamente por decisiones anteriores a HU-16.

Archivos: paquete `aviso/`, ampliación de las tres clases `NotificadorCorreo*`,
publicación de eventos en `TramiteService` y `DocumentoService`, dos clases
de pruebas, `docker-compose.yml`, contrato y esta evidencia.

La usuaria autorizó publicar únicamente HU-15 y HU-16 en `origin/sprint-8`.
La configuración local, las cuatro eliminaciones previas y el rediseño visual
se mantienen fuera del commit. No se realiza merge a `main`.
