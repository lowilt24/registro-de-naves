# HU-15 — Constancia de registro de la nave · Sprint 8

Alcance confirmado por la usuaria: acto de registro, no abanderamiento. HU-13
(ITC/SMC) se adelantó al Sprint 7. La simulación de patente será posterior.

## Comportamiento

- `GET /api/naves/{naveId}/constancia` entrega un PDF adjunto con nombre
  `constancia-registro-{naveId}.pdf` y encabezado `X-Content-SHA256`.
- Sin sesión: 401. Nave inexistente: 404. Agente ajeno, administrador u otro
  rol: 403. Solo el AGENTE_NAVIERO dueño, FUNCIONARIO_DGMM y AUDITOR descargan.
  El acceso se comprueba antes del estado, para no revelar el expediente.
- Solo APROBADA permite emitir/descargar. Los otros cinco estados: 409.
- La fecha y el funcionario se obtienen del evento APROBAR del historial,
  nunca del usuario que descarga ni de la fecha de generación. Un expediente
  histórico sin evidencia de aprobación, propietarios o agente residente
  responde 409 explicativo; no se inventan datos.
- Incluye número de registro (ID existente de nave), nombre, tipo, arqueo
  bruto (GT), tonelaje neto (TRN), eslora, manga y puntal (m), todos los
  propietarios y su porcentaje, agente residente vigente, fecha de aprobación
  en America/Panama y nombre del funcionario que aprobó.
- Se genera en el servidor en la primera descarga autorizada. Una constancia
  por nave: bloqueo de fila del trámite y unicidad en base evitan emisiones
  simultáneas. PDF y SHA-256 quedan persistidos juntos en la misma transacción.
  Las siguientes descargas devuelven los mismos bytes y verifican el hash.
- El PDF se guarda en BYTEA en la base, no en recursos públicos. Se usa el
  mismo algoritmo SHA-256 de HU-07; almacenar juntos PDF y hash evita archivos
  huérfanos por rollback y no modifica las cargas existentes.
- El expediente aprobado ya es de solo lectura. La constancia es una copia
  conservada de sus datos en la primera emisión; no se regenera por descarga.
- Texto visible: «Constancia digital del trámite de registro. No constituye
  patente de navegación ni certificación oficial. Sin firma digital».
- En el paso 6 del expediente, el botón aparece solo al consultar APROBADA.
  El servidor vuelve a verificar siempre estado y permisos.

## Terminología y compatibilidad

Aprobar mantiene la nave REGISTRADA; el trámite queda APROBADA. Una migración
nueva corrige únicamente naves ABANDERADA con trámite APROBADA. No se editan
migraciones anteriores, ni se borra historial, ni se agrega simulación de patente.
El valor antiguo del enum se conserva solo para compatibilidad histórica.

## Verificación requerida

Matriz de roles, cinco estados no aprobados, datos y porcentajes del PDF,
acentos, nombres largos y múltiples páginas, estabilidad de bytes/hash,
falta de evidencia histórica e integridad alterada. Regresión de HU-13 y
aprobación, descarga desde la interfaz, sesión vencida y errores visibles.
