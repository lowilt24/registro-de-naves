# Publicar Registro de Naves y seguir actualizándolo

**Actualización: el usuario eligió costo cero. Seguir `PUBLICAR-GRATIS.md` y el
perfil `gratis`. La opción de pago descrita abajo queda como alternativa; no es
necesario contratar un disco para el modo PostgreSQL de adjuntos.**

Preparación para sprint-8; presentación final indicada para el 30 de noviembre de
2026. Publicar no congela el código: se sigue trabajando en Git y se despliegan
las versiones que se decida presentar. Esta guía no crea servicios ni contrata planes.

## Qué se preparó

- Avisos visibles de resultado en operaciones y confirmación de inicio de sesión
  después de comprobar la sesión real. No se guardan contraseñas en el navegador.
- Estados de espera y bloqueo de envíos repetidos en acceso, registro y reenvío.
- Mensajes visibles del administrador, que antes podían permanecer ocultos.
- `Dockerfile` de Java 21, `.dockerignore` sin secretos ni adjuntos y perfil `prod`.
- Configuración externa de PostgreSQL, SMTP, URL pública y disco de documentos.
- Cookies de sesión seguras por HTTPS, CSRF conservado y supresión de enlaces
  secretos de validación en los registros de producción.

Una confirmación en pantalla no prueba que un email haya llegado al buzón.
La entrega debe verificarse con el proveedor y con una cuenta de prueba real.

## Opción que encaja con el proyecto actual

Render: servicio web Docker, PostgreSQL y disco persistente. Conserva el backend
Java y el SMTP existentes. Los precios se deben confirmar en
https://render.com/pricing antes de contratar; el servicio web, la base de datos,
el disco y cualquier dominio se pagan por separado.

La opción gratuita duerme por inactividad, no admite disco persistente y bloquea
SMTP en los puertos habituales. Su base PostgreSQL gratuita vence a los 30 días.
Por eso no es equivalente a una demo estable con carga de documentos y correo.
Fuente: https://render.com/docs/free

Railway también aloja Java y PostgreSQL, pero bloquea SMTP en Free/Trial/Hobby;
elegirlo con esos planes requeriría cambiar el envío a una API HTTPS de correo.
Fuente: https://docs.railway.com/networking/outbound-networking

## Pasos para Render

1. **Revisar la rama a presentar.** Usar `sprint-8` con estos cambios subidos al
   repositorio `lowilt24/registro-de-naves`. No usar un servicio de sitio estático:
   el sistema necesita ejecutar Spring Boot y PostgreSQL.

2. **Preparar correo real.** Elegir un proveedor SMTP con STARTTLS en puerto 587.
   Por ejemplo, Resend requiere dominio verificado y clave de API; host
   `smtp.resend.com`, usuario `resend`, contraseña igual a la clave de API y un
   remitente del dominio verificado. No sirve Mailpit para enviar al público.
   También puede usarse otro proveedor con sus propias credenciales.
   Fuente: https://resend.com/docs/send-with-smtp

3. **Crear PostgreSQL.** En Render elegir New → Postgres, región igual a la futura
   aplicación y versión compatible con PostgreSQL 16 del desarrollo. Guardar las
   credenciales en un gestor seguro. La URL JDBC no es la URL `postgresql://`
   literal del panel: debe tener forma
   `jdbc:postgresql://HOST:5432/BASE` (sin usuario ni contraseña incrustados).
   En conexiones externas añadir `?sslmode=require` y usar el host externo.
   En la aplicación alojada usar el host interno de la misma región.

4. **Inicializar la base y asegurar las cuentas antes de exponer el servicio.**
   El proyecto contiene cuentas demo con contraseña conocida en sus migraciones.
   No publicar esas cuentas activas. Para una base nueva, se puede arrancar una
   vez la aplicación **solo en 127.0.0.1**, conectada a la base cloud mediante
   `DB_URL`, `DB_USER` y `DB_PASSWORD`; Flyway aplica el esquema. Este arranque es
   preparación administrativa, no el sitio de presentación. Detenerla después.
   Desde pgAdmin/psql conectado a la base cloud, sustituir la cuenta admin demo
   por correo y contraseña privados y desactivar los otros usuarios demo.
   Véase el ejemplo SQL al final. No modificar migraciones ya aplicadas.

   Para ese arranque administrativo, desde la carpeta del proyecto y una terminal
   nueva de PowerShell (sustituir host/base/usuario):

   ```powershell
   $env:DB_URL = 'jdbc:postgresql://HOST_EXTERNO:5432/BASE?sslmode=require'
   $env:DB_USER = 'USUARIO_DE_LA_BASE_CLOUD'
   $clavePreparacion = Read-Host 'Contraseña de PostgreSQL cloud' -AsSecureString
   $env:DB_PASSWORD = [Net.NetworkCredential]::new('', $clavePreparacion).Password
   .\mvnw.cmd -DskipTests package
   java -jar target/registro-naves-0.0.1-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=8081
   ```

   Cuando termine de iniciar, detener con Ctrl+C y cerrar esa terminal para no
   conservar variables de la base cloud en futuras ejecuciones de desarrollo.

5. **Crear el servicio web.** New → Web Service → conectar GitHub → seleccionar
   `lowilt24/registro-de-naves`, rama `sprint-8`, runtime Docker y archivo
   `./Dockerfile`. La raíz es la del repositorio, donde está `pom.xml`:
   no introducir `registro-de-naves-sprint8` como subdirectorio, ese nombre solo
   corresponde a la carpeta local de trabajo. Dejar el comando Docker sin override.
   Inicialmente desactivar despliegues automáticos para controlar lo presentado.
   Fuente: https://render.com/docs/docker

6. **Elegir los recursos y el disco.** Para el flujo existente se necesita un
   servicio con disco persistente; montarlo en `/var/data`. La aplicación guarda
   los adjuntos en `/var/data/almacen`. Comprobar que el usuario 10001 del contenedor
   puede escribir ahí. No resolverlo haciendo pública la carpeta de documentos.
   Base de datos y disco son persistencias distintas: se necesitan ambas.
   Fuente: https://render.com/docs/disks

7. **Configurar Environment.** Introducir los valores siguientes en el panel,
   nunca en GitHub ni en un archivo que se vaya a subir:

   | Variable | Valor |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_URL` | `jdbc:postgresql://HOST_INTERNO:5432/BASE` |
   | `DB_USER` | Usuario de la base cloud |
   | `DB_PASSWORD` | Contraseña de la base cloud |
   | `NAVESITAS_URL_BASE` | `https://NOMBRE-DEL-SERVICIO.onrender.com` |
   | `NAVESITAS_ALMACEN_RUTA` | `/var/data/almacen` |
   | `SMTP_HOST` | Host del proveedor |
   | `SMTP_PORT` | `587` para el perfil STARTTLS preparado |
   | `SMTP_USER` | Usuario SMTP |
   | `SMTP_PASSWORD` | Clave SMTP/API del proveedor |
   | `SMTP_FROM` | Remitente autorizado por el proveedor |

   Usar la URL HTTPS asignada al servicio. Cambiarla si se añade un dominio;
   afecta a los enlaces enviados por correo. No dejar `localhost` en producción.
   Render asigna `PORT`; el perfil lo utiliza automáticamente.

8. **Desplegar.** Configurar Health Check Path `/actuator/health`. El contenedor
   compila el JAR; Flyway comprueba/aplica las migraciones al arrancar. Revisar
   Events/Logs hasta que el despliegue quede disponible. Si falta una variable,
   no hay conexión a PostgreSQL o el disco no es escribible, corregir ese punto
   antes de seguir. No deshabilitar CSRF ni permisos para resolver un error.

9. **Probar la URL pública desde computadora y celular.** Registrar una cuenta
   de prueba, comprobar el aviso, recibir el email en un buzón real, abrir el
   enlace HTTPS, iniciar sesión y ver la confirmación una sola vez. Revisar
   spam y el panel del proveedor si el mensaje no llega. Probar credenciales
   incorrectas, sesión cerrada, registro duplicado y validación expirada.

10. **Probar el expediente completo.** Registrar una nave, vincular propietario
    y agente, cargar documentos, revisar/aprobar con el rol correspondiente y
    descargar el PDF. Comprobar que un usuario ajeno no accede al expediente.
    Reiniciar o volver a desplegar el servicio y verificar que los datos y los adjuntos
    siguen disponibles. Descargar después del reinicio, no solo antes.

11. **Preparar recuperación.** Guardar respaldo de la base y de los adjuntos.
    Antes de una migración nueva comprobar ambos. No borrar la base para cambiar
    de sprint ni regenerar los identificadores del expediente.

12. **Actualizar hasta sprint-10.** Seguir editando y probando, subir los cambios
    y desplegar un commit elegido. Integrar en sprint-10 las mejoras de sprint-8
    que se quieran conservar antes de cambiar la rama del servicio. Mantener
    PostgreSQL y disco; Flyway aplica solo las migraciones nuevas. En sprint-10
    comprobar además el QR con la URL pública y los recursos autorizados de firma
    y logo. La vigencia de seis meses todavía requiere implementación separada.
    No volver a desplegar sprint-8 contra un esquema ya migrado a sprint-10 sin
    comprobar compatibilidad; usar un entorno de ensayo o restaurar una copia.

## Ejemplo de preparación de cuentas (solo base nueva de demostración)

En una sesión privada de administración de PostgreSQL y **antes** de publicar,
reemplazar los dos valores de ejemplo por datos propios. No subir el SQL con
contraseñas reales. `pgcrypto` está soportado por Render Postgres:
https://render.com/docs/postgresql-extensions

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
BEGIN;
UPDATE usuario
SET correo = 'REEMPLAZAR-POR-TU-CORREO',
    password_hash = crypt('REEMPLAZAR-POR-UNA-CONTRASENA-LARGA-Y-UNICA', gen_salt('bf', 12)),
    nombre_completo = 'Administración', correo_validado = true,
    token_validacion = NULL, token_expira_en = NULL, activo = true
WHERE correo = 'admin@navesitas.pa' AND rol = 'ADMINISTRADOR';
-- Debe actualizar exactamente una fila. Verificar antes de confirmar.
UPDATE usuario SET activo = false
WHERE correo IN ('agente@navesitas.pa', 'revisor@navesitas.pa', 'auditor@navesitas.pa');
COMMIT;
```

Si ya hay datos reales o la cuenta admin ya fue cambiada, adaptar el procedimiento;
no repetirlo a ciegas. El administrador crea usuarios internos desde el sistema.

## Límites de la revisión local

Las pruebas de interfaz y los dobles SMTP verifican comportamiento del código;
no demuestran entrega de un correo real. El despliegue Docker requiere el motor
Docker activo y la prueba integral requiere PostgreSQL. Estas comprobaciones
deben cerrarse en el entorno cloud antes de considerar terminada la publicación.
