# Render gratuito: pasos para este proyecto

Esta es la opción elegida por el usuario: **sin contratar planes de pago**.

- Aplicación: Render Free, rama `sprint-8`, Docker, región Virginia.
- Base: el PostgreSQL gratuito de Render ya creado, también Virginia.
- Adjuntos: en PostgreSQL (`BYTEA`), no en el disco efímero del servidor.
- Correo: API HTTPS de Brevo; el SMTP no se usa en este perfil.
- Dirección: la URL HTTPS gratuita `*.onrender.com`, sin comprar dominio web.

## Lo que debes hacer en el formulario de Render

1. Mantener **Free**, no añadir disco y no introducir datos de pago para este flujo.
2. Rama `sprint-8`, Docker, raíz vacía, Dockerfile `./Dockerfile`, contexto `.`,
   Health Check Path `/actuator/health`. Docker Command y Pre-Deploy vacíos.
3. En Environment Variables usar exactamente estas variables:

   | KEY | VALUE |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `gratis` |
   | `DB_URL` | `jdbc:postgresql://HOST_INTERNO:5432/NOMBRE_REAL_BASE` |
   | `DB_USER` | Usuario real del PostgreSQL gratuito |
   | `DB_PASSWORD` | Contraseña de ese PostgreSQL |
   | `NAVESITAS_URL_BASE` | `https://NOMBRE-DEL-SERVICIO.onrender.com` |
   | `BREVO_API_KEY` | Clave API de Brevo, no su clave SMTP |
   | `CORREO_REMITENTE` | Dirección de email verificada como remitente en Brevo |

   Si antes pusiste `SPRING_PROFILES_ACTIVE=prod`, **reemplazarlo por `gratis`**.
   No combinar ambos perfiles. No hacen falta `SMTP_HOST`, `SMTP_USER`,
   `SMTP_PASSWORD`, `SMTP_PORT`, `SMTP_FROM` ni un disco persistente.
   No compartir claves, contraseñas o capturas que revelen sus valores.

4. Antes de desplegar, preparar las cuentas demo como explica el apartado de
   seguridad de `PUBLICAR-EN-WEB.md`: las contraseñas demo son públicas. La base
   debe inicializarse y la cuenta admin debe quedar protegida antes de publicar.
5. Confirmar que se desplegará un commit que incluya el perfil `gratis`.
   Al conectar por Public Git Repository, actualizar mediante Manual Deploy.

## Brevo sin pagar

1. Crear una cuenta **Free** en https://www.brevo.com/ y verificar el email.
2. Completar el registro con información real para la revisión del proveedor.
   No contratar campañas, IP dedicada ni complementos de pago.
3. Añadir un remitente desde los ajustes de remitentes y verificarlo con el código
   recibido en esa dirección. Puede usarse una dirección que ya controles;
   el proveedor puede reemplazar el remitente visible para cumplir las políticas
   de Gmail/Yahoo. No se suplanta `no-responder@navesitas.pa`.
4. En SMTP & API → API Keys, crear una clave API y copiarla directamente a
   `BREVO_API_KEY` en Render. No usar la contraseña de tu correo ni la clave SMTP.
5. Comprobar que el envío transaccional está habilitado. Algunas cuentas requieren
   activación/revisión por soporte; si se pide, solicitarla antes de la presentación.
6. Poner el email verificado en `CORREO_REMITENTE`.

No es necesario importar a los usuarios de la aplicación a una campaña de marketing.
El sistema envía mensajes transaccionales en texto plano al registrar/validar cuentas
y avisos ya existentes del trámite. No se añaden correos automáticos por iniciar sesión.

Fuentes: https://developers.brevo.com/docs/send-a-transactional-email
https://help.brevo.com/hc/en-us/articles/208836149-Create-a-new-sender-From-name-and-From-email
https://help.brevo.com/hc/en-us/articles/16045394674066-Troubleshooting-Domain-authentication
https://help.brevo.com/hc/en-us/articles/115000188150-Troubleshooting-Issues-with-Brevo-SMTP

## Qué cambió en el código

El perfil `gratis` desactiva la autoconfiguración SMTP. `NotificadorCorreoBrevo`
envía al endpoint oficial HTTPS con la clave en una cabecera, con tiempos de espera
y sin registrar tokens, claves ni cuerpos de error del proveedor. Si falla el correo
de validación, la cuenta queda pendiente y admite reenvío: no se salta la validación.

`AlmacenArchivos` conserva el modo `disco` para desarrollo y añade `postgres`.
La migración aditiva `V9_1` crea `archivos_documentales`. Carga del archivo y metadatos
participan en la transacción de registro documental; se mantienen UUID, hash SHA-256,
tope de 10 MB, comprobación PDF y permisos. Los endpoints de descarga siguen protegidos
y no hay URL pública al contenido. La descarga no guarda una copia permanente en disco.
La patente de sprint-10 puede reutilizar estos adjuntos usando la misma abstracción.

**Cambiar de modo no importa automáticamente archivos antiguos del disco.** Activar
`gratis` antes de cargar documentos en la base cloud nueva. Si ya existe documentación
en disco, conservarla e importarla con un procedimiento controlado antes de cambiar.

## Límites del alojamiento gratuito

Render Free duerme después de 15 minutos sin tráfico; la siguiente apertura puede
tardar alrededor de un minuto. Las sesiones pueden terminar cuando el servidor se
reinicia. Abrir el sitio con antelación para la presentación y volver a iniciar sesión.

El PostgreSQL Free tiene 1 GB **compartido por datos, versiones de documentos y PDF**
y vence a los 30 días desde su creación. Usar PDF de prueba pequeños, vigilar el tamaño
desde el panel y respaldar toda la base antes de que venza. El respaldo incluye los
adjuntos guardados en PostgreSQL. Esta solución gratuita no elimina ese vencimiento
ni convierte el plan en un servicio permanente.

Brevo Free tiene una cuota de 300 correos al día según su oferta consultada. La entrega
depende de la cuenta aprobada, el remitente, la cuota y los filtros del destinatario.
Si no llega un email, revisar el panel transaccional y spam; no desactivar validación.

Fuentes: https://render.com/docs/free
https://www.brevo.com/products/transactional-email/

## Comprobación antes de dar por terminado el despliegue

- Registrar una cuenta real de prueba, recibir su email y activar la cuenta.
- Entrar y comprobar el aviso de inicio correcto; probar contraseña incorrecta.
- Registrar nave y cargar un PDF pequeño. Descargarlo con la cuenta autorizada.
- Confirmar que otro usuario no puede acceder al documento.
- Volver a desplegar/reiniciar y descargar **el mismo PDF**: debe conservar sus bytes.
- Si ya se emitió constancia/patente, verificar que también se conserva.
- Comprobar crecimiento de la base y guardar respaldo antes del vencimiento.

Las pruebas con un doble JDBC/HTTP no prueban persistencia en un PostgreSQL real
ni entrega al buzón. Esas verificaciones se completan después de configurar el
entorno cloud. No se envían correos reales desde las pruebas automatizadas.
