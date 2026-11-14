# Patente provisional: presentación y verificación

El PDF se genera con PDFBox a partir de `pp_emisiones.datos_emitidos`, la misma
instantánea guardada por la emisión existente. No se vuelve a leer el expediente
actual para reemplazar los datos históricos. Se conservan numeración, fecha de
emisión, requisitos, permisos y condición de **sin vencimiento definido**.

## Configuración

- `NAVESITAS_URL_BASE`: URL pública del sistema, por ejemplo
  `https://registro.su-dominio.example`. Se reutiliza `navesitas.url-base`.
  El valor de desarrollo existente sigue siendo `http://localhost:8080`;
  cámbielo por una dirección accesible desde el teléfono **antes de generar el PDF**.
  No se deduce la URL de cabeceras enviadas por el visitante.
- `PATENTE_LOGO_RUTA`: ruta local a un PNG/JPEG autorizado del logo. Opcional.
  En esta rama no se encontró un archivo de logo existente. Sin configuración,
  se imprime el nombre Registro de Naves sin inventar una imagen institucional.
- `PATENTE_FIRMA_RUTA`: ruta local a un PNG/JPEG de firma autorizada. Opcional.
  Sin ella se imprime «Firma pendiente». El nombre corresponde al funcionario
  de la instantánea; el cargo corresponde al rol DGMM que puede emitir.
  Una imagen de firma **no es una firma digital criptográfica**.

Los recursos deben configurarse antes de la primera descarga. Una ruta inválida
impide generar el PDF, pero conserva la emisión para reintentar. Una vez generado,
el archivo se conserva con su hash; cambiar la configuración no reescribe
automáticamente un PDF ya generado con este diseño.

## Verificación y privacidad

La migración V11 asigna UUID aleatorios únicos a las emisiones nuevas y existentes.
El QR se genera localmente con ZXing; no se envían datos a servicios de QR externos.
Apunta a `verificar-patente.html#UUID`. El fragmento no se transmite en la petición
de la página ni como referente. La página consulta por GET
`/api/verificacion/patentes/{UUID}`, sin sesión y sin caché.

La respuesta contiene únicamente número, fecha de emisión, estado de solicitud,
condición de vigencia y alcance académico. No expone propietarios, funcionarios,
solicitantes, documentos, rutas de almacenamiento, instantánea ni enlaces de descarga.
Un identificador inválido devuelve 400; uno inexistente, 404. La verificación
confirma la emisión registrada; no demuestra que una copia del documento no haya
sido alterada y no reemplaza una firma criptográfica.

## Conservación de archivos y permisos

La primera descarga de una emisión con el formato anterior verifica su hash,
conserva los bytes y hash originales en `contenido_anterior` / `hash_anterior`,
y genera la versión 2 desde la misma instantánea. No emite otra patente ni
modifica su número. Las descargas posteriores devuelven exactamente los mismos bytes.
Se mantiene el bloqueo transaccional existente para descargas concurrentes.

Las rutas de emisión y descarga mantienen sus permisos. Solo se añade acceso
público GET a la página y la consulta mínima de verificación. No se permite
descargar el PDF ni adjuntos de forma anónima. CSRF permanece habilitado.

## Comprobaciones

`PatentePdfTest`: A4, texto extraíble, conservación de todas las líneas de la
instantánea, lectura del QR desde el PDF renderizado, URL configurable,
documentos largos y márgenes. Genera `target/patente-diseno-prueba.pdf` e imágenes
de sus páginas para inspección visual; sus datos son exclusivamente de prueba.

`VerificacionPatenteWebTest`: respuesta pública mínima, errores 400/404,
descarga autenticada, cabeceras, denegación anónima y protección CSRF.
Usa dobles del servicio; no valida consultas reales a PostgreSQL.

`PatenteIntegrationTest`: emisión, validaciones, concurrencia, integridad,
descarga y verificación con PostgreSQL. Requiere la base de datos configurada y
las migraciones hasta V11. No se debe afirmar que estas pruebas pasaron si la
base no está disponible.
