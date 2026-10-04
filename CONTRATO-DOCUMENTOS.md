# Contrato de API — documentos de la nave (HU-07 y HU-08)

Lo que la pantalla de documentos (cámara 5) y los casos de Katalon TC-17 a
TC-24 esperan del backend. Si algo de esto cambia, hay que cambiar también
`documentos.js` y los casos de Katalon.

**Estado:** implementado en `pa.amp.registro_naves.documento` y cubierto por
`DocumentoIntegrationTest` (18 pruebas). Todo lo de abajo está verificado
contra la aplicación real, incluido el 413 por HTTP con archivos de 10 MB + 1 KB
y de 15 MB.

Todas las rutas exigen sesión. Sin sesión → **401**.

---

## Tipos de documento

Lista cerrada. El backend rechaza cualquier otro valor con 400.

| Valor | Se muestra como |
|---|---|
| `CERTIFICADO_PROPIEDAD` | Certificado de propiedad |
| `PODER_NOTARIAL` | Poder notarial |
| `ITC` | Certificado internacional de arqueo (ITC) |
| `SMC` | Certificado de gestión de la seguridad (SMC) |

> T-07.1 (Abdiel con Louis) define la lista final. Si cambian los nombres,
> hay que cambiarlos en `TIPOS_DOCUMENTO` de `documentos.js` y en los
> casos de Katalon.

## Estados

`PENDIENTE` (al cargar), `APROBADO`, `OBSERVADO`.

`FALTANTE` **no** es un estado de la base: lo calcula la pantalla para los
tipos que no tienen ningún documento.

---

## 1. Cargar documento — HU-07

```
POST /api/naves/{naveId}/documentos
Content-Type: multipart/form-data
```

| Parte | Tipo | |
|---|---|---|
| `tipo` | texto | uno de los cuatro valores |
| `archivo` | archivo | el PDF |

### Respuestas

| Código | Cuándo | Cuerpo |
|---|---|---|
| **201** | Guardado | `DocumentoResponse` |
| **400** | Falta `tipo` o `archivo`, o el tipo no es de la lista | `{ mensaje, campos: { tipo \| archivo: "…" } }` |
| **403** | La nave es de otro usuario | `{ mensaje }` |
| **404** | La nave no existe | `{ mensaje }` |
| **409** | La nave no tiene propietario o no tiene agente vigente | `{ mensaje }` |
| **413** | El archivo pasa de 10 MB | `{ mensaje }` |
| **415** | No empieza con `%PDF-` (firma binaria) | `{ mensaje, campos: { archivo: "…" } }` |

El orden de las comprobaciones importa para que los casos de prueba sean
deterministas: **401 → 404 → 403 → 400 → 413 → 415 → 409**.

### Ojo con el límite de tamaño (esto rompe el 413 si se hace mal)

Spring trae un límite por defecto de **1 MB** para multipart. Además, si
el archivo es rechazado por el límite del servlet, Tomcat cierra la
conexión sin leer el resto cuando pasa de 2 MB (`max-swallow-size`), y el
cliente recibe "conexión reiniciada" en vez de un 413. Katalon lo reporta
como error de red, no como 413, y TC-19 falla.

La forma que funciona: dejar el límite del servlet **por encima** de 10 MB
y verificar los 10 MB en el servicio.

```properties
spring.servlet.multipart.max-file-size=12MB
spring.servlet.multipart.max-request-size=13MB
```

```java
private static final long LIMITE = 10L * 1024 * 1024;
if (archivo.getSize() > LIMITE) {
    throw new ArchivoDemasiadoGrandeException(...);   // → 413
}
```

Y por si llega algo de más de 12 MB, manejar también
`MaxUploadSizeExceededException` → 413 en `ManejadorGlobalDeErrores`.

### Nombre en disco

Nunca usar `getOriginalFilename()` para armar la ruta. Guardar como
`{naveId}/{uuid}.pdf` dentro de la carpeta de almacenamiento, y conservar
el nombre original **solo** como dato en la columna `nombre_archivo`,
limpiado (sin `/`, `\`, `..` ni caracteres de control).

---

## 2. Consultar documentos — HU-08

```
GET /api/naves/{naveId}/documentos
GET /api/naves/{naveId}/documentos?tipo=ITC
GET /api/naves/{naveId}/documentos?estado=PENDIENTE
GET /api/naves/{naveId}/documentos?historial=true
```

| Parámetro | | |
|---|---|---|
| `tipo` | opcional | filtra por tipo |
| `estado` | opcional | `PENDIENTE`, `APROBADO` u `OBSERVADO` |
| `historial` | opcional, `false` por defecto | `false`: solo la versión vigente de cada tipo. `true`: todas las versiones |

Una nave sin documentos responde **200 con `[]`**, no 404.

Orden: por tipo (alfabético por el valor: CERTIFICADO_PROPIEDAD, ITC,
PODER_NOTARIAL, SMC) y, dentro del tipo, versión descendente. La pantalla
ordena por su cuenta, así que esto solo importa para quien consuma la API.

`estado=FALTANTE` responde **400**: faltante no es un estado guardado.

| Código | Cuándo |
|---|---|
| **200** | Lista, aunque esté vacía |
| **400** | `tipo` o `estado` con un valor que no existe |
| **403** | Nave de otro usuario |
| **404** | Nave inexistente |

---

## 3. Descargar archivo — HU-08

```
GET /api/naves/{naveId}/documentos/{documentoId}/archivo
```

| Código | Cuándo |
|---|---|
| **200** | `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="…"` |
| **403** | La nave es de otro usuario |
| **404** | La nave no existe, el documento no existe, **o el documento no pertenece a esa nave** |

El último caso es el IDOR: sin esa comprobación, alguien pide
`/api/naves/{suNave}/documentos/{documentoAjeno}/archivo` y pasa el
control de propiedad porque la nave de la ruta sí es suya.

---

## DocumentoResponse

```json
{
  "id": 42,
  "naveId": 7,
  "tipo": "ITC",
  "nombreArchivo": "itc-estrella-del-istmo.pdf",
  "tamanoBytes": 284311,
  "hashSha256": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "version": 2,
  "vigente": true,
  "estado": "PENDIENTE",
  "creadoEn": "2026-10-06T14:32:10-05:00"
}
```

`vigente` es `true` solo en la versión más alta de cada tipo.

---

## Nave — hora de creación

Observación de la profesora: la ficha de la nave muestra fecha **y hora**.
La pantalla lee `creadoEn` de `GET /api/naves/{id}` y, si no viene, cae a
`fechaRegistro`. Cuando la V5 agregue `creado_en`, basta con exponerlo en
`NaveResponse` como `creadoEn` (ISO 8601 con zona).
