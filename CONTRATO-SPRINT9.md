# Sprint 9 — HU-17 y HU-18

Implementación solicitada por la usuaria según el backlog del Word.
No incluye simulación de patente, cambios a HU-13 ni datos ficticios.

## HU-17 — Reportes

- DGMM y auditor: página `/reportes.html`, JSON `GET /api/reportes/registro`
  y CSV `GET /api/reportes/registro.csv`. Otros roles: 403; sin sesión API: 401.
- Filtros opcionales `desde` y `hasta`, formato YYYY-MM-DD, años 1–9999.
  Límite inicial y final inclusivos en America/Panama. Se admite un solo
  extremo. Fecha vacía/inválida, rango invertido, filtro desconocido o
  repetido: 400. No se interpreta texto del cliente como SQL.
- Naves por tipo y por estado: fecha de registro `creado_en`.
- Trámites por estado: fecha del último cambio `actualizado_en`.
  Espera actual = días de calendario desde ese cambio hasta hoy en Panamá,
  mínimo cero. Promedio con dos decimales, solo en estados no finales.
  En estados finales o grupos vacíos no hay promedio (null / «—»).
- Documentos pendientes de revisión: versiones vigentes PENDIENTE, por
  fecha de carga `creado_en`, agrupadas por tipo. No cuenta versiones previas.
- Son cifras del estado actual, no una reconstrucción histórica. No son
  tiempos promedio de resolución. La pantalla explica estas diferencias.
- Grupos sin datos aparecen con cero; no se fabrican estadísticas.
  El valor legado ABANDERADA se agrupa como REGISTRADA, según HU-15.
- CSV: mismos filtros, permisos y agregados; UTF-8 con BOM, comas y campos
  entre comillas. Solo categorías controladas, cantidades, promedios y fechas:
  no hay nombres, correos, observaciones ni campos que ejecuten fórmulas.
- Las consultas de un reporte comparten una transacción de lectura repetible.

## HU-18 — Guía de requisitos

- Agente naviero: `/guia-requisitos.html` y `POST /api/guia-requisitos`.
  Otros roles: 403. Se mantienen sesión y CSRF.
- Tipos comerciales: CARGA, PASAJEROS y MIXTA. Otros tipos: 400.
- Se solicita tipo y servicio; eslora y arqueo bruto son opcionales para
  orientar antes del registro. Si se proporcionan deben ser positivos y
  respetar la precisión del formulario de nave. Datos inválidos: 400.
- Se crea una nave transitoria (no persistida) y se llama a
  `RequisitosDocumentoService.evaluar`. No se duplican umbrales ni se crean
  equivalencias. Se muestran los cuatro documentos con decisión y explicación.
- Datos ausentes y casos no cubiertos conservan REQUIERE_REVISION; se explica
  que no se puede confirmar el expediente ni avanzar hasta aclararlos.
- Muestra el recorrido: nombre, registro, propietarios, agente residente,
  documentos aplicables, revisión/correcciones y constancia tras aprobar.
- La consulta no registra naves, modifica expedientes ni envía correos.

## Verificación

Matriz de roles y sesión, datos reales de QA controlados, límites inclusivos
de fechas y zona, promedios, finales, vacíos, vigencia documental, CSV y
filtros erróneos; guía comparada con HU-13 en sus límites y casos pendientes;
regresión completa, interfaz en escritorio/móvil y pruebas sin modificar
la base ni la configuración local de la usuaria.
