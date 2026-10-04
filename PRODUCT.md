# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Agente naviero (usuario principal).** Registra naves ante la Autoridad Marítima de Panamá en nombre de sus propietarios. Trabaja sentado frente a una computadora de escritorio, en oficina, con los documentos de la nave a mano. Solo puede operar sobre las naves que él mismo registró.
- **Funcionario DGMM.** Rol que se puede elegir al crear la cuenta. Hoy no tiene permisos distintos a los del agente.
- **Administrador.** Existe en el modelo (`Rol.ADMINISTRADOR`) pero no se ofrece en el registro de usuario ni tiene comportamiento propio. Decisión abierta.

## Product Purpose

Sistema digital de registro de naves para la Autoridad Marítima de Panamá (AMP): el acto de registro de la nave, su "cédula". Reemplaza el trámite en papel para ese acto concreto.

El trabajo se considera exitoso cuando un agente naviero completa, sin papel y sin ayuda, el recorrido completo: consultar que el nombre esté libre, registrar la nave, registrar a sus propietarios y designar a su agente residente.

## Positioning

El sistema impone las precondiciones del trámite en lugar de dejar que fallen al final:

- el nombre de la nave se consulta antes de habilitar el resto del formulario;
- no se puede designar agente residente a una nave sin propietarios;
- la suma de participaciones de los propietarios no puede pasar del 100%;
- cada nave queda ligada a quien la registró, y de ahí sale el control de acceso.

## Operating Context

- Proyecto académico Scrum de la Universidad Tecnológica de Panamá (FISC), Grupo 3. Equipo: Product Owner, Scrum Master y dos desarrolladores.
- La cliente es la profesora María Félix Mosquera, que revisa el avance los lunes.
- Se evalúa en cuatro dimensiones: calidad, seguridad, agilidad/DevOps y gestión del proyecto. Cada cambio debe dejar evidencia usable en la documentación del sprint.
- Uso real: escritorio, en oficina.
- Recorrido de la demo (`LEEME-FRONTEND.md`): iniciar sesión → consultar nombre → registrar nave → abrir expediente → registrar propietario → designar agente residente → reemplazarlo y ver el historial.

## Capabilities and Constraints

**Alcance.** Limitado a la Ley 55 de 2008 y solo al acto de registro de la nave. Fuera de alcance: Ley 57, marina mercante, hipotecas, cancelaciones y cálculo automático de tasas.

**Capacidades actuales** (HU-01 a HU-06):

- registro e inicio de sesión de usuarios;
- consulta de disponibilidad del nombre de la nave;
- registro de la nave: clasificación, tonelaje bruto y neto, eslora, manga, puntal, construcción y propulsión;
- registro de propietarios, persona natural o jurídica, con porcentaje de participación; la persona jurídica exige país de constitución;
- designación de agente residente, consulta del vigente y reemplazo con historial;
- expediente de la nave, que reúne todo lo anterior.

**Reglas de negocio:** una nave tiene uno o más propietarios; las participaciones suman como máximo 100%; sin propietarios no se designa agente; el agente reemplazado se conserva como historial; la identificación de un propietario no se repite.

**Errores.** Toda validación indica el campo que falló. Códigos: 201 creado, 400 campo faltante o inválido, 401 sin sesión, 403 la nave no le pertenece, 404 la nave no existe, 409 regla de negocio.

**Control de acceso.** Todo exige sesión. El acceso a una nave se decide por quién la registró, no por rol: los roles se guardan pero ninguna regla de autorización los usa.

**Técnicas.** Java 21 + Spring Boot, PostgreSQL 17 en Docker, migraciones Flyway. El frontend es HTML, CSS y JavaScript estático servido desde `src/main/resources/static/`, sin framework ni paso de compilación. El JavaScript depende de los `id`, clases funcionales (`bloqueado`, `oculto`) y atributos `data-*` del marcado.

**Terminología:** nave, expediente, propietario, persona natural, persona jurídica, participación, agente residente, designación, tonelaje bruto, tonelaje neto, eslora, manga, puntal, AMP, DGMM.

## Brand Commitments

- **Identidad: decisión abierta.** Las pantallas dicen "Navesitas · Registro de naves comerciales · DGMM", pero la cliente es la AMP. No está decidido si "Navesitas" es el nombre del producto, un nombre provisional que debe reemplazarse por la identidad de la AMP/DGMM, o ambas cosas. El trabajo futuro no debe resolver esto por su cuenta.
- No hay logotipo ni recurso institucional en el repositorio.
- **Voz:** español de Panamá, trato formal de "usted" ("Su sesión terminó. Vuelva a entrar…", "La nave indicada no le pertenece.").

## Evidence on Hand

- `qa/evidencias/sprint-3/`: suite Katalon `TS03_Sprint3`, 10/10 PASSED (resumen CSV y JUnit XML).
- `qa/registro-naves-qa/`: 20 casos Katalon a nivel de API (HU-01 a HU-06 e infraestructura).
- `security/reports/fuzz-2026-09-16.json` y `seguridad/`: pruebas de fuzzing con Wfuzz.
- `src/main/resources/db/migration/V3__datos_demo.sql`: datos de demostración.
- Hallazgos de seguridad abiertos: SEC-03 (credenciales en el repositorio) y SEC-05 (CSRF).
- No existen: logotipo, contenido institucional real de la AMP, datos reales del registro de naves, usuarios reales ni testimonios. El trabajo futuro no debe inventarlos.

## Product Principles

1. **Disciplina de alcance.** Ley 55, solo el acto de registro. Lo que implique Ley 57, hipotecas, cancelaciones o tasas queda fuera hasta que el backlog diga lo contrario.
2. **Precondición antes que acción.** Una acción bloqueada debe explicar qué falta, no fallar después de llenar el formulario.
3. **El error nombra el campo.** Nadie debe adivinar qué corregir.
4. **Toda nave tiene autor.** El registro es trazable a quien lo hizo.
5. **Todo cambio deja evidencia.** El proyecto se evalúa por lo que puede demostrar.

## Accessibility & Inclusion

Obligatoria como restricción del dominio: se trata de un sistema de una entidad pública panameña, así que la accesibilidad es una obligación, no una preferencia. No se fijó una versión específica del estándar. El uso en escritorio no reduce los requisitos de navegación por teclado, contraste ni lectores de pantalla.
