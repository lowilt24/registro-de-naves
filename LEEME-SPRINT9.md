# Sprint 9 — HU-17 y HU-18

Implementación local solicitada por la usuaria el 9 de noviembre de 2026.
Rama `sprint-9`, desde `origin/main` con Sprint 8 integrado. La usuaria confirmó
el funcionamiento en su prueba manual y autorizó publicar ambas historias en
`origin/sprint-9`. Se conserva la configuración local y las eliminaciones
previas fuera del commit. No se realiza merge a `main`.

Contrato y criterios técnicos: [CONTRATO-SPRINT9.md](CONTRATO-SPRINT9.md).

## HU-17 — Reportes y estadísticas

Desde la bandeja de DGMM y auditor se accede a `/reportes.html`.

- Naves por tipo y por estado; trámites por estado y espera actual promedio;
  documentos vigentes pendientes de revisión, por tipo.
- Filtros Desde/Hasta opcionales, inclusivos en America/Panama. Cada bloque
  explica su fecha: registro de nave, último cambio de trámite y carga del
  documento, respectivamente. Son estados actuales, no un corte histórico.
- Espera en días de calendario desde el último cambio del trámite. No se
  atribuye espera a estados finales ni a grupos vacíos: se muestra «—».
- CSV de los mismos agregados y filtros, UTF-8 con BOM para Excel, sin datos
  personales ni texto libre que pueda convertirse en una fórmula.
- JSON `/api/reportes/registro` y CSV `/api/reportes/registro.csv`: solo DGMM y
  auditor. Otros roles: 403; sin sesión: 401. Filtros inválidos: 400.
- Los conteos se calculan en la base existente mediante consultas agregadas
  parametrizadas y una transacción de lectura repetible. Los grupos vacíos
  muestran cero. El valor legado ABANDERADA se agrupa como REGISTRADA.
- En móvil las filas del resumen de trámites se apilan y conservan los rótulos
  Cantidad/Espera (días); no hace falta desplazar esa tabla lateralmente.

## HU-18 — Guía previa de requisitos

Desde el registro de naves, el agente accede a `/guia-requisitos.html`.

- Tipos comerciales Carga, Pasajeros y Mixta, servicio, eslora y arqueo bruto.
  Las medidas pueden dejarse vacías para orientar antes del registro.
- `POST /api/guia-requisitos` llama directamente a la evaluación de HU-13 con
  una nave transitoria. No duplica los umbrales ni persiste naves o trámites.
- Muestra documentos obligatorios, no aplicables y pendientes, con las
  explicaciones de HU-13. Naves mixtas, servicios no cubiertos y datos ausentes
  mantienen Requiere revisión; no se inventan exenciones ni nuevas reglas.
- Muestra los pasos hasta la constancia tras aprobar; no genera una patente.
- Solo agente naviero: los demás reciben 403. Se mantienen sesión y CSRF.
  Datos inválidos o tipos fuera del alcance: 400.
- Editar las entradas oculta la orientación anterior hasta volver a consultar.

## Verificación ejecutada

Se usó PostgreSQL 17 temporal en 55432 y aplicación temporal en 8082, separados
de la base y aplicación de la usuaria. Las nuevas funciones solo consultan.
Sin migraciones, dependencias nuevas ni cambios a HU-13, HU-15 o HU-16.

- Maven `verify`: **396 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**.
  Incluye las 355 previas y 41 nuevas de Sprint 9.
- JaCoCo: instrucciones **95.33 %**, ramas **89.68 %**, líneas **94.69 %**.
  Umbral del 70 % aprobado.
- JavaScript: **21/21 aprobadas**, nueve nuevas. Sintaxis de ambos scripts
  nuevos y `git diff --check` aprobados.
- Integración: matriz de roles, páginas, filtros inválidos, fechas inclusivas
  en Panamá, un solo extremo, rangos vacíos, promedios, fechas futuras,
  exclusión de versiones documentales previas y documentos ya revisados,
  CSV con los mismos filtros y sin datos personales, terminología REGISTRADA.
- Guía: límites 23.99/24/24.01 m y 499/500/501 GT, pasajeros, mixtas, cabotaje,
  servicio mixto, medidas ausentes y datos inválidos. Se verificó que consultar
  no cambia las cantidades de naves, trámites ni documentos.
- HTTP con sesiones y CSRF reales: reportes/CSV 200 para DGMM y auditor y
  403 para agente/administrador; filtros inválidos 400 y sin sesión 401.
  CSV con BOM UTF-8; rango sin datos en cero. Guía solo para agente, decisiones
  de HU-13 correctas, pendiente para mixtas o datos insuficientes. Los reportes
  antes y después de las consultas fueron idénticos.
- Edge: navegación desde Bandeja y Mis naves, sesiones reales de los tres
  roles, capturas a **1440, 768, 390 y 320 px**, sin desbordamiento horizontal
  de página y con foco visible. Descarga CSV idéntica a la respuesta HTTP,
  rango invertido con error sin cifras obsoletas, guía de carga y advertencia
  para tipo mixto. Se inspeccionaron capturas de escritorio y móvil.
- La primera corrida de integración rechazó el parámetro artificial `_csrf`
  que la configuración compartida agrega a los GET de MockMvc. Se construyó
  un cliente de prueba que reproduce las peticiones reales, conservando CSRF
  explícito en los POST. La corrida final completa pasó.
- Tras la inspección visual se ajustó la tabla móvil de trámites para apilar
  sus filas. Se reempaquetaron los recursos y se verificó otra vez en Edge:
  sin desplazamiento lateral de esa tabla y con foco visible mediante Tab.
- Comprobación de lectura en la aplicación de desarrollo, puerto 8080:
  reportes como DGMM y guía como agente respondieron 200. No se registraron
  naves ni se modificaron expedientes durante esta comprobación.
- No se ejecutaron Katalon ni Wfuzz para estas historias; no se declara que
  estas pruebas los sustituyan.

### Confirmación manual reportada — 9 de noviembre de 2026

La usuaria confirmó que las naves aparecen en la bandeja y que la guía
funciona; después solicitó publicar HU-17 y HU-18 en la rama de Sprint 9.
No detalló una comprobación manual individual de todos los filtros o del CSV;
esas comprobaciones se respaldan con la evidencia automatizada anterior.

Artefactos regenerables ignorados por Git en `target/sprint9-*`: logs, CSV de
QA, resultados HTTP y capturas por página, rol y tamaño. No contienen la
  configuración local. La aplicación y PostgreSQL temporales se detuvieron
  al finalizar; `amp-db`, `amp-correo` y la aplicación de desarrollo se conservaron.

## Prueba manual

1. Recargar con Ctrl+F5 e iniciar sesión como DGMM (`revisor@navesitas.pa`) o
   auditor (`auditor@navesitas.pa`), contraseña demo habitual.
2. En la bandeja, abrir Reportes y estadísticas. Comparar cantidades con los
   registros existentes; consultar un rango válido y descargar el CSV.
3. Probar un rango sin datos y fechas invertidas. No deben quedar resultados
   anteriores visibles después de un error.
4. Como agente (`agente@navesitas.pa`), abrir la Guía de requisitos desde el
   registro. Carga/Internacional/24 m/499 GT: ITC obligatorio y SMC no aplica.
5. Consultar Pasajeros/Internacional: SMC obligatorio. Cambiar a Mixta o dejar
   medidas vacías: se mantienen los requisitos pendientes según HU-13.
6. Comprobar los pasos y el enlace al registro; la consulta no crea una nave.
7. Usar ventanas separadas para probar los roles y no reemplazar la sesión.

## Archivos

- Paquetes Java `reporte/` y `guia/`: consultas, CSV y guía con la regla de HU-13.
- `SecurityConfig.java`: permisos de las dos páginas nuevas.
- `reportes.html`, `guia-requisitos.html` y sus scripts: formularios y resultados.
- `bandeja.html`, `naves.html`: accesos desde la navegación existente.
- `css/app.css`: estilos de decisiones de la guía y tabla móvil.
- `Sprint9IntegrationTest.java`, `sprint9.test.cjs`: pruebas nuevas.
- Contrato y este documento: alcance, decisiones y evidencia.
