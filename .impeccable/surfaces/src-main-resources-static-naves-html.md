---
version: 1
slug: "src-main-resources-static-naves-html"
primary_target: "src/main/resources/static/naves.html"
related_targets: ["src/main/resources/static/expediente.html","src/main/resources/static/login.html","src/main/resources/static/registro-usuario.html","src/main/resources/static/css/app.css"]
---

# Surface brief: pantallas de trabajo de Navesitas

Alcance: las cinco pantallas estáticas (login, registro de usuario, registro de nave, expediente, index). Modo: Operate.

Audiencia y tarea: agente naviero en escritorio, en oficina, registrando una nave, sus propietarios y su agente residente (ver PRODUCT.md).

Restricciones de esta superficie:
- Refresco visual de nivel A + B: solo `css/app.css` y el marcado HTML. Ningún `.js`, ningún `<script>` en línea, ningún `id`, `name`, `for`, `value`, `action`, `method`, `data-*` ni clase funcional (`oculto`, `bloqueado`) cambia. Clases que inyecta el JS y deben seguir estilizadas: `cifra`, `estado-etiqueta`, `enlace-fila`, `vacio`, `pista`, `cuota`, `etiqueta-historial`, `veredicto[data-estado]`, `#resultado[data-visible]`.
- Recursos estáticos nuevos solo bajo `/css/` (las únicas rutas públicas además de `/js/`).
- La identidad está abierta: el texto "Navesitas · Registro de naves comerciales · DGMM" se conserva; no se usa ninguna marca de la AMP ni del Canal.

## Direction contract

THESIS: El trámite como el paso por un juego de esclusas: cada cámara (nombre, nave, propietarios, agente residente) se llena antes de abrir la siguiente. Rechaza el formulario de gobierno genérico, donde un bloque bloqueado es solo un gris apagado sin explicación.

OWN-WORLD: Señalética operativa de esclusa. Negro de muro en la barra; amarillo de seguridad reservado a las placas numeradas de cámara y a la franja de compuerta cerrada; azul de agua para la cámara llena y la acción principal; hormigón claro como fondo; blanco como área de trabajo; verde y rojo de semáforo de esclusa para los avisos, siempre con forma y texto además del color. Atkinson Hyperlegible Next para todo, Mono solo para cifras. Filetes de 1px, radio pequeño, sin sombras.

STORY: El agente ve en qué cámara está, por qué la siguiente sigue cerrada y qué la abre; nunca llena un formulario que no se puede guardar.

FIRST VIEWPORT: Barra negra con Navesitas y la sesión. Título "Registrar nave". Cámara 1 (placa amarilla "1"): consulta de nombre a todo el ancho con su botón azul. Debajo, cámara 2 cerrada: franja de compuerta amarilla y negra en su borde superior, contenido atenuado y la leyenda de qué la abre. Movimiento firma: al quedar libre el nombre, el agua sube en la cámara 2 (banda azul que se llena en ~250 ms) y la franja se retira.

FORM: Esclusas del Canal, candidata #1 de la lista ordenada; seed key b1b9e0eb.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

Raises adoptados de la mano descartada: un solo sistema de filetes de 1px; rejilla con ritmo de 8px; sin sombras ni ornamento; la jerarquía de datos la carga el tamaño de la cifra (el número de registro manda en la ficha); el rojo queda reservado a errores y "alto".

Decisiones abiertas: identidad (Navesitas vs. AMP/DGMM).
