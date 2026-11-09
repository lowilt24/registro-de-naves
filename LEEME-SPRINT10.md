# Probar Sprint 10

Use archivos ficticios. La aplicación muestra «Simulación académica de patente
provisional — sin validez oficial». No realiza pagos ni trámites oficiales.

1. Como agente complete/apruebe un registro internacional con propietarios y
   residente. Desde **Patentes provisionales** seleccione la nave, declare
   procedencia y cree el borrador. También hay acceso desde el expediente.
2. Como DGMM abra **Patentes provisionales**. Agregue requisitos técnicos y
   adicionales con motivo. Si procede, autorice tipo HU-07 para reutilización.
   Confirme el catálogo. En el caso académico sin técnicos adicionales marque
   la declaración y justifíquela.
3. Como agente adjunte los cuatro PDF base y los exigidos por DGMM (máximo
   10 MiB). Puede elegir una versión HU-07 exacta autorizada. Envíe a revisión.
   Sin catálogo confirmado o archivos requeridos recibe 409.
4. Como DGMM descargue y revise cada evidencia. Para probar subsanación,
   observe una con explicación y devuelva indicando la corrección. El agente
   debe adjuntar otra versión; no puede modificar las demás evidencias.
   Reenvíe y revise todos los archivos del nuevo envío.
5. Apruebe con todas las evidencias aprobadas. Descargue como agente, DGMM y
   auditor. Número, datos y bytes se conservan. El registro sigue aprobado y
   la nave sigue Registrada. No se permite otra solicitud de la misma nave.
6. Consulte envíos/versiones e historial con actor, fecha y decisiones.
   Auditor solo consulta. Agente ajeno y administrador reciben 403.

Use perfiles separados o InPrivate para mantener sesiones por rol; las
pestañas normales comparten sesión. Cuentas demo: `agente@navesitas.pa`,
`revisor@navesitas.pa`, `auditor@navesitas.pa`. Clave pública demo:
`Navesitas2026*`.

Contrato: [DISENO-SPRINT10.md](DISENO-SPRINT10.md).
Java: `mvnw.cmd verify`. JavaScript: `node --test src/test/js/*.test.cjs`.
Configure una base PostgreSQL temporal con SPRING_DATASOURCE_URL,
SPRING_DATASOURCE_USERNAME y SPRING_DATASOURCE_PASSWORD para aislar las pruebas.
No cambie `.env` ni application.properties para ejecutarlas.

## Verificación realizada el 9 de noviembre de 2026

- `mvnw.cmd verify`: 419 pruebas Java, 0 fallos/errores; migración V10 aplicada
  en PostgreSQL temporal aislado. Incluye creación/aprobación/descarga concurrentes,
  rollback de emisión y estado ante fallo al guardar historial, permisos,
  subsanación, evidencia versionada, PDF y hash.
- JavaScript: 30 pruebas aprobadas, incluidas descarga fallida, 401, recarga tras
  409, controles de auditor, observadas y escape de contenido.
- Flujo HTTP completo con sesiones reales/CSRF: reutilización exacta de ITC,
  devolución y reenvío, decisión obsoleta rechazada, emisión única concurrente,
  PDF idéntico para agente/auditor con hash verificado y permisos 401/403.
- Edge: flujo completo por formularios y descarga PDF; seis pantallas en
  1440, 768, 390 y 320 px, sin desbordamiento ni excepciones JavaScript y con
  foco visible. Inspección visual de escritorio, móvil y PDF generado.
- Cobertura global JaCoCo: instrucciones 94.62 %, ramas 87.33 %, líneas 94.99 %.
- `.env`, application.properties y eliminaciones locales previas conservados.
  Sin commits, push ni merge. Las naves de QA se crearon solo en la base temporal.

Evidencia local reproducible de esta sesión en `target/sprint10-*` (ignorado
por Git): logs, capturas, informe de navegador y PDF de prueba. La configuración
SMTP real no se usó para enviar avisos durante esta revisión; Sprint 10 no
introduce notificaciones nuevas.
