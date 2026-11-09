# HU-16 — Avisos por correo al agente · Sprint 8

Alcance solicitado por la usuaria, según los criterios del backlog del Word.

- Destinatario: correo del agente naviero que registró la nave. No el agente
  residente, los propietarios ni el funcionario que realiza la acción.
- Se notifica cada cambio exitoso que realiza la DGMM: ADMITIR, OBSERVAR,
  APROBAR y RECHAZAR el trámite, y OBSERVAR un documento vigente pendiente.
  Aprobar un documento no envía aviso. Registrar, subsanar y desistir como
  agente tampoco: HU-16 se refiere a las decisiones de la DGMM.
- El mensaje de texto plano incluye nombre y número de registro de la nave,
  acción, nuevo estado o documento y versión, observación si existe y enlace
  al expediente, construido con `navesitas.url-base` (configuración existente).
  No adjunta documentos ni incluye contraseñas, tokens, hashes, datos de
  propietarios o enlaces que eviten el inicio de sesión y sus permisos.
- Reutiliza `NotificadorCorreo` y el JavaMailSender de validación de cuentas.
  En desarrollo se recibe en Mailpit, no en una bandeja de correo externa.
- Se publica una instantánea inmutable dentro de la transacción y se envía
  con un listener AFTER_COMMIT. Una validación fallida, conflicto o rollback
  no notifica; un fallo SMTP ocurre después de guardar y no revierte la acción.
- El envío es síncrono después del commit, con los tiempos de espera SMTP ya
  configurados. No hay cola persistente, reintentos ni pantalla nueva.
- Sin SMTP o si falla: se registra el identificador del evento, la nave y
  la clase del error. No se registran correo, observación ni contenido del
  mensaje. No se informa falsamente que se entregó.
- Se preservan permisos, CSRF, sesiones, estados y la constancia de HU-15.

Verificación: contenido/destinatario, cuatro transiciones y observación de
documento, ausencia de avisos por operaciones rechazadas o rollback, estado
persistido aunque falle el correo, regresión completa y recepción SMTP real
en Mailpit de QA. Las pruebas no modifican la base de datos del usuario.
