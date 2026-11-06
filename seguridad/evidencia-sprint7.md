# Evidencia de seguridad — Sprint 7, HU-13

Fecha: 2026-11-06. Rama `sprint-7`, cambios locales sobre `d7ef001`.

## Ejecutado

- Maven: 308/308 pruebas, incluido acceso por propietario/rol al endpoint de requisitos, IDOR contra otro agente, IDs inválidos y datos de usuario rechazados con 400. Regresiones de PDF, tamaño, permisos de revisión y CSRF incluidas.
- `node --test src/test/js/documentos.test.cjs`: 6/6. La interfaz no declara completo ante errores de evaluación ni ante REQUIERE_REVISION.
- Comprobaciones HTTP con cookies reales en una instancia temporal en `http://localhost:8081`, porque el 8080 ya estaba ocupado. No se detuvo ni reemplazó la aplicación del 8080.

Comando de seguridad en Windows (la excepción de política se limita a ese proceso; no cambia la política del sistema):

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\seguridad\probar-seguridad-sprint7.ps1 -Base http://localhost:8081
```

Resultado HTTP final (repetido con la versión final del script): **13 comprobaciones correctas, 0 fallidas**.

| Comprobación | Obtenido |
|---|---|
| Consulta anónima de requisitos | 401 |
| Consulta por agente propietario, DGMM y auditor | 200 en los tres casos |
| Administrador sobre expediente del agente | 403 |
| ID `abc` e intento de expresión SQL en el ID | 400 |
| IDs `0`, `-1`, `999999999` | 404 |
| POST sin CSRF y con token incorrecto | 403 en ambos casos |
| POST con cookie/cabecera CSRF reales y cuerpo vacío | 400 por validación de datos, sin mutación |

El caso HTTP de IDOR del agente requiere una nave ajena visible en la bandeja. Si no existe, el script lo marca PENDIENTE; no crea registros para completar esa comprobación. IDOR sí se ejecutó y pasó en la integración transaccional de Maven. El reporte local, sin cookies ni secretos, está en `seguridad/reportes/s7-seguridad.json` (ignorado por Git).

Al preparar el script se corrigió el envío involuntario de un cuerpo vacío en GET y se hizo explícita la enumeración de arreglos JSON en Windows PowerShell. La primera invocación fue bloqueada por la política de scripts del equipo; se ejecutó con excepción limitada al proceso. El arranque inicial en 8080 falló por puerto ocupado; `BUILD SUCCESS` de spring-boot:run no se usó como evidencia de arranque. La instancia de 8081 sí respondió por HTTP y se detuvo únicamente su proceso verificado.

## Pendiente / no ejecutado

- TS07_Sprint7: tres casos Katalon creados; XML y referencias comprobados. Sin ejecutable Katalon/Groovy disponible en las ubicaciones revisadas, no se declaran aprobados.
- No se ejecutó Wfuzz. Esta evidencia HTTP corresponde al script PowerShell, no a un escaneo Wfuzz.
- Revisión visual manual de HU-13 pendiente. Las seis pruebas de JavaScript usan DOM simulado.
- Envío SMTP real pendiente de HU-14, sin cambios a la configuración local.

No se alteró PostgreSQL mediante el script de seguridad. Las inserciones de las pruebas Maven se revierten con @Transactional. No se modificaron .env, application.properties ni los controles de seguridad para facilitar las pruebas.
