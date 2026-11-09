# Sprint 10 · Patente provisional simulada

Alcance aceptado por la usuaria el 9 de noviembre de 2026 después de revisar
`Sprint_10_Proceso_Historias_Patente_Provisional.docx` y su referencia AMP.
Historias provisionales HU-S10-01 a HU-S10-06; sin renumeración del backlog.

## Alcance

- Módulo independiente para naves INTERNACIONAL con registro APROBADO y evento
  histórico APROBAR. La nave permanece REGISTRADA.
- Una solicitud por nave. BORRADOR → EN_REVISION → SUBSANACION → EN_REVISION
  → APROBADA. Sin rechazo, desistimiento, reapertura ni vencimiento.
- Cuatro PDF base: designación del residente; documento según procedencia
  (cancelación anterior, construcción o venta judicial); evidencia de
  cumplimiento y comprobante de pago simulado. Sin cobros reales.
- DGMM define por nombre y motivo los certificados técnicos/de seguridad y
  adicionales para cada caso. Confirma el catálogo antes del envío; cualquier
  cambio invalida la confirmación. Si no se requieren técnicos adicionales
  en el caso académico debe declararlo expresamente y justificarlo.
- No se traslada HU-13 automáticamente. La designación no se equipara al
  poder. Autenticación y suficiencia se revisan por DGMM.
- No se infiere la excepción de servicio interior a partir de CABOTAJE.
- PDF SIM-PP-AÑO-ID: «Simulación académica de patente provisional — sin validez
  oficial». Sin firma oficial ni integración AMP.

## Persistencia y transacciones

Migración aditiva V10; no modifica tablas/datos anteriores. `patente/` usa
JdbcTemplate en las transacciones Spring existentes, sin dependencias nuevas.
Lecturas JPA del registro acreditan acceso, aprobación y datos de la nave.

`pp_solicitudes`, `pp_requisitos`, `pp_evidencias`, `pp_envios`,
`pp_envio_items`, `pp_revisiones`, `pp_emisiones`, `pp_eventos` separan borrador,
documentos versionados, envíos congelados, revisiones y emisión.

Los PDF de patente se conservan como BYTEA privado transaccional (máximo
10 MiB, firma `%PDF-`, SHA-256). Sin URL pública ni rutas del cliente. Reutilizar
HU-07 requiere autorización explícita del tipo por DGMM, pertenencia a la misma
nave, ID de versión exacto e integridad de hash. Copia bytes/hash y no hereda
aprobación ni depende del archivo fuente. El nombre se limpia reutilizando
DocumentoService. Los documentos previos de HU-07 conservan su almacenamiento.

Cada envío congela requisitos, motivo/versión del catálogo, IDs de evidencia,
datos de nave, propietarios y residente. Subsanación permite nuevas versiones
solo para observadas o requisitos nuevos; exige otro ID para las observadas
antes de reenviar. Cada envío requiere revisión de todas sus evidencias.
Los envíos anteriores conservan decisiones y archivos.

Bloqueo de nave para crear y de solicitud para mutaciones/descargas PDF;
restricciones UNIQUE impiden duplicados. Revisión, devolución y aprobación
reciben `envioId`: decisiones de pestañas antiguas reciben 409. Una revisión
ya decidida no se sustituye.

Aprobar inserta emisión, cambia estado y añade evento en una transacción.
Repetir aprobación del mismo envío devuelve la emisión existente. El PDF se
genera bajo bloqueo desde la instantánea emitida y guarda bytes/hash; un fallo
de renderizado conserva la emisión. Descargas siguientes verifican hash y
conservan exactamente los bytes, sin leer datos vivos de nave/funcionario.

## Permisos y API

Agente dueño: crear, adjuntar/referenciar y enviar. DGMM: definir/confirmar,
revisar, devolver y aprobar. Agente dueño/DGMM/auditor: consultar y descargar.
Administrador y agente ajeno: 403. Sin sesión: 401. CSRF se conserva.
Auditor no realiza acciones de negocio. No hay endpoints para editar/borrar
historial, envíos, revisiones ni emisiones.

- GET `/api/patentes-provisionales?estado=...`: listado propio para agente.
- Base `/api/naves/{naveId}/patente-provisional`: GET consulta; POST crea.
- POST `/requisitos`, `/requisitos/confirmar`,
  `/requisitos/{id}/reutilizacion`, `/requisitos/{id}/archivo` (multipart),
  `/requisitos/{id}/referenciar`, `/enviar`.
- POST `/evidencias/{id}/revision`, `/devolver`, `/aprobar`: `envioId` actual.
- GET `/archivo` y `/evidencias/{id}/archivo`: PDF autorizado, no-store,
  Content-Disposition attachment y X-Content-SHA256.

Referencia contextual: [AMP, patente de navegación, servicio exterior](https://www.amp.gob.pa/servicios/marina-mercante/abanderamiento-de-naves/servicio-exterior/patente-de-navegacion/),
consultada el 9 de noviembre de 2026. Este catálogo representa el alcance
académico acordado; no es una lista legal exhaustiva ni una emisión oficial.
