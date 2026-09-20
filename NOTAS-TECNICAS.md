# Notas técnicas y decisiones abiertas

## 1. Oracle vs. lo que está en el `pom.xml`

La presentación de Sprint 2 dice "El sistema consulta en Oracle" en cuatro
láminas. El `pom.xml` del repo no tiene driver de Oracle: tiene **H2** y
**PostgreSQL**, y la dependencia de Flyway es `flyway-database-postgresql`.

Este código usa **JPA sin SQL nativo**, así que la lógica de HU-03 y HU-04
funciona igual en cualquiera de los tres motores. Lo que cambia es la
configuración y la sintaxis de las migraciones.

Está armado para correr sobre **H2 en archivo** (cero instalación, los datos
sobreviven al reinicio, sirve para la demo). Se dejó listo
`application-oracle.properties` con los tres pasos que faltan para mover a
Oracle de verdad:

1. Agregar `com.oracle.database.jdbc:ojdbc11` (scope `runtime`).
2. Agregar `org.flywaydb:flyway-database-oracle`.
3. Reescribir V2 y V3 en sintaxis Oracle. Ojo con `BOOLEAN`: solo existe desde
   Oracle 23c; en 19c hay que usar `NUMBER(1)`.

**Decisión que hay que tomar en equipo:** o se hace ese trabajo, o se corrige la
presentación para que diga el motor real. Que la lámina diga una cosa y el
repositorio otra es justo el tipo de detalle que el profesor va a notar.

## 2. Seguridad abierta durante el sprint

`SecurityConfig` deja `/api/naves/**` sin autenticación y apaga CSRF sobre
`/api/**`. Eso es deliberado: Katalon y Wfuzz no tienen que manejar sesión ni
token para correr contra los endpoints.

Está marcado con comentarios en el código. En Sprint 3, cuando HU-05 y HU-06
traigan el manejo real de roles, esa línea pasa a `.hasRole("AGENTE_NAVIERO")` y
CSRF vuelve a activarse con token. Vale la pena mencionarlo en la presentación
como decisión consciente y no dejar que lo encuentren.

## 3. Sobre el `pom.xml`

No lo toqué. Usa los nombres de artefacto de Spring Boot 4 (`spring-boot-starter-webmvc`,
`spring-boot-h2console`, `spring-boot-starter-webmvc-test`), que son distintos a
los de Boot 3. Si `./mvnw verify` falla por alguna dependencia, lo más probable es:

- Falta soporte de Flyway para H2. Si sale un error de "Unsupported Database:
  H2", agregar `org.flywaydb:flyway-database-h2`.
- Falta `spring-boot-starter-test` para JUnit/MockMvc, si los starters `-test`
  del pom no lo cubren.

Manda el error y lo ajustamos.

## 4. El punto que quedó abierto del replanteo

En el plan original, **HU-04 era la pantalla de revisión de documentos del
Funcionario Revisor de la DGMM**. En el plan nuevo, HU-04 es la consulta de
disponibilidad de nombre. La revisión de documentos no tiene sprint asignado en
las 6 semanas.

No es un problema del código, pero conviene preguntarlo en la presentación antes
de que lo pregunten a ustedes.
