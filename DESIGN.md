---
name: Navesitas
description: Registro de naves comerciales como el paso por un juego de esclusas; cada cámara se llena antes de abrir la siguiente.
colors:
  muro: "#15181c"
  muro-texto: "#b9c0c8"
  tinta: "#15181c"
  tinta-suave: "#4f555c"
  hormigon: "#e4e7e9"
  hormigon-alto: "#f2f4f5"
  superficie: "#ffffff"
  filete: "#cdd2d6"
  filete-campo: "#6c737a"
  seguridad: "#f2c400"
  agua: "#1f4e8c"
  agua-hondo: "#173d6e"
  agua-clara: "#e3ecf6"
  siga: "#1e7a46"
  siga-tinta: "#155a33"
  siga-fondo: "#e6f3eb"
  alto: "#b42318"
  alto-tinta: "#8e1c13"
  alto-fondo: "#fcebe9"
  neutro-fondo: "#eef0f2"
typography:
  headline:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.1875rem"
    fontWeight: 700
    lineHeight: 1.3
  marca:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    letterSpacing: "0.01em"
  body:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  body-compacto:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
  label-dato:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "0.8125rem"
    fontWeight: 700
    letterSpacing: "0.04em"
  placa:
    fontFamily: "Atkinson Hyperlegible Next, system-ui, -apple-system, Segoe UI, sans-serif"
    fontSize: "1.375rem"
    fontWeight: 800
    lineHeight: 1
    fontFeature: "tnum"
  cifra:
    fontFamily: "Atkinson Hyperlegible Mono, ui-monospace, SF Mono, Menlo, monospace"
    fontSize: "1rem"
    fontWeight: 400
    letterSpacing: "0"
    fontFeature: "tnum"
  cifra-mayor:
    fontFamily: "Atkinson Hyperlegible Mono, ui-monospace, SF Mono, Menlo, monospace"
    fontSize: "2rem"
    fontWeight: 700
    lineHeight: 1.1
    fontFeature: "tnum"
rounded:
  radio: "2px"
spacing:
  e1: "4px"
  e2: "8px"
  e3: "16px"
  e4: "24px"
  e5: "32px"
  e6: "40px"
  e7: "48px"
  e8: "64px"
components:
  barra:
    backgroundColor: "{colors.muro}"
    textColor: "{colors.superficie}"
    typography: "{typography.marca}"
    padding: "8px 24px"
    height: "56px"
  tarjeta:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.radio}"
    padding: "24px 32px"
  button-primary:
    backgroundColor: "{colors.agua}"
    textColor: "{colors.superficie}"
    typography: "{typography.body}"
    rounded: "{rounded.radio}"
    padding: "0 24px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.agua-hondo}"
    textColor: "{colors.superficie}"
  button-primary-active:
    backgroundColor: "{colors.muro}"
    textColor: "{colors.superficie}"
  button-secondary:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.radio}"
    padding: "0 24px"
    height: "44px"
  button-secondary-hover:
    backgroundColor: "{colors.hormigon-alto}"
    textColor: "{colors.tinta}"
  input:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.tinta}"
    typography: "{typography.body}"
    rounded: "{rounded.radio}"
    padding: "0 12px"
    height: "44px"
  input-disabled:
    backgroundColor: "{colors.hormigon-alto}"
  placa-activa:
    backgroundColor: "{colors.seguridad}"
    textColor: "{colors.muro}"
    typography: "{typography.placa}"
    rounded: "{rounded.radio}"
    size: "40px"
  placa-cruzada:
    backgroundColor: "{colors.agua}"
    textColor: "{colors.superficie}"
    typography: "{typography.placa}"
    rounded: "{rounded.radio}"
    size: "40px"
  placa-cerrada:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.muro}"
    typography: "{typography.placa}"
    rounded: "{rounded.radio}"
    size: "40px"
  cerrojo:
    backgroundColor: "{colors.hormigon-alto}"
    textColor: "{colors.tinta}"
    typography: "{typography.body-compacto}"
    rounded: "{rounded.radio}"
    padding: "8px 16px"
  veredicto-libre:
    backgroundColor: "{colors.siga-fondo}"
    textColor: "{colors.siga-tinta}"
    rounded: "{rounded.radio}"
    padding: "12px 16px 12px 48px"
  veredicto-ocupado:
    backgroundColor: "{colors.alto-fondo}"
    textColor: "{colors.alto-tinta}"
    rounded: "{rounded.radio}"
    padding: "12px 16px 12px 48px"
  veredicto-neutro:
    backgroundColor: "{colors.neutro-fondo}"
    textColor: "{colors.tinta}"
    rounded: "{rounded.radio}"
    padding: "12px 16px 12px 48px"
  etiqueta-estado:
    backgroundColor: "{colors.siga-fondo}"
    textColor: "{colors.siga-tinta}"
    typography: "{typography.label-dato}"
    rounded: "{rounded.radio}"
    padding: "2px 8px"
  etiqueta-cuota:
    backgroundColor: "{colors.agua-clara}"
    textColor: "{colors.agua}"
    typography: "{typography.cifra}"
    rounded: "{rounded.radio}"
    padding: "2px 8px"
  etiqueta-historial:
    backgroundColor: "{colors.hormigon-alto}"
    textColor: "{colors.tinta-suave}"
    typography: "{typography.label-dato}"
    rounded: "{rounded.radio}"
    padding: "2px 8px"
---

# Design System: Navesitas

## Overview

**Creative North Star: "Esclusas del Canal"**

El trámite de registro se recorre como el paso de un buque por un juego de esclusas. Cada paso es una cámara numerada (1 nombre y 2 datos de la nave en la pantalla de registro; 3 propietarios y 4 agente residente en el expediente) y una cámara no se abre hasta que la anterior cumple su condición. El lenguaje visual viene de la señalética operativa de una esclusa: muro negro, hormigón claro, amarillo de seguridad para las placas numeradas y para la franja de compuerta, azul de agua para la cámara llena y la acción principal, y verde/rojo de semáforo para los avisos. Modo de trabajo: Operate. Es una herramienta de oficina para un agente naviero en escritorio, densa y sobria, no una página de presentación.

El sistema existe para que el usuario vea en qué cámara está, por qué la siguiente sigue cerrada y qué la abre. Un bloque deshabilitado nunca es solo un gris apagado: lleva la franja de compuerta en su borde superior, una placa blanca y, cuando hace falta, una leyenda que dice qué lo habilita. Se rechaza explícitamente el formulario de gobierno genérico, donde un bloque bloqueado no explica nada.

No se usa ninguna marca de la AMP ni del Canal de Panamá; la referencia es el oficio de la esclusa, no su identidad institucional. La identidad del producto ("Navesitas" frente a AMP/DGMM) es una decisión abierta registrada en PRODUCT.md y este sistema no la resuelve.

**Key Characteristics:**
- Cámaras numeradas con tres estados visibles: cerrada, activa y cruzada.
- Franja de compuerta amarilla y negra como firma; se retira hacia los muros al abrirse la cámara.
- Una sola familia tipográfica (Atkinson Hyperlegible Next) y su Mono solo para cifras.
- Filetes de 1 px, radio de 2 px, sin sombras de elevación.
- Ritmo de espaciado de 8 px.
- Ningún estado se comunica solo con color: cada aviso lleva forma y texto.

## Colors

Paleta de señalética industrial: neutros fríos de hormigón y muro, con tres señales saturadas (amarillo de seguridad, azul de agua, verde/rojo de semáforo) cuyo uso está acotado por regla.

### Primary
- **Azul de agua** (agua): la cámara llena (línea de agua de 6 px en el borde superior), la placa de una cámara cruzada, el botón principal, los enlaces, el anillo de foco de los campos, el cursor y `accent-color`. Blanco sobre azul de agua mide 8.31:1.
- **Agua honda** (agua-hondo): estado hover del botón principal y de los enlaces.
- **Agua clara** (agua-clara): fondo de la etiqueta de cuota de participación.

### Secondary
- **Amarillo de seguridad** (seguridad): placas numeradas de la cámara activa y franja de compuerta. En el muro negro de la barra también señala hover y foco de los enlaces, y es el color de la selección de texto. Muro sobre amarillo mide 10.74:1.

### Tertiary
- **Verde siga** (siga, siga-tinta, siga-fondo): aviso de "nombre libre" y etiqueta de estado "REGISTRADA". La tinta verde sobre su fondo mide 7.23:1.
- **Rojo alto** (alto, alto-tinta, alto-fondo): errores de campo y aviso de "alto" (nombre ocupado, acceso denegado). La tinta roja sobre su fondo mide 7.83:1 y sobre blanco 9.03:1.

### Neutral
- **Negro de muro** (muro): barra superior, borde de las placas, estado activo (presionado) del botón principal y la mitad oscura de la franja de compuerta.
- **Gris de muro** (muro-texto): texto secundario sobre la barra negra (9.70:1).
- **Tinta** (tinta): texto principal; mismo valor que el muro, con rol distinto.
- **Tinta suave** (tinta-suave): sumarios, pistas, encabezados de tabla, rótulos de ficha (6.07:1 sobre hormigón).
- **Hormigón** (hormigon): fondo de página. Gris frío, no crema.
- **Hormigón alto** (hormigon-alto): fondo de campos deshabilitados, de la leyenda de cerrojo, del aviso de precondición y del hover de filas.
- **Superficie** (superficie): blanco de las tarjetas y de los campos; es el área de trabajo.
- **Filete** (filete): bordes de tarjeta, separadores de fieldset y de filas de tabla.
- **Filete de campo** (filete-campo): borde de campos y del botón secundario. Es el contraste más bajo verificado del sistema: 4.80:1 sobre blanco.
- **Fondo neutro** (neutro-fondo): fondo del aviso informativo.

### Named Rules
**Regla del amarillo acotado.** El amarillo de seguridad pertenece a las placas de cámara y a la franja de compuerta; fuera de ellas solo aparece como señal sobre el muro negro (hover y foco en la barra) y en la selección de texto. Nunca es fondo de tarjeta, de botón ni de aviso.

**Regla del rojo de alto.** El rojo se reserva a errores y a la señal de "alto". No se usa para decoración, énfasis ni acciones destructivas que no sean un error.

**Regla de la forma más el color.** Ningún estado se comunica solo con color. Cada aviso lleva una forma (círculo con visto para siga, octágono de alto para detenerse, anillo con "i" para información) además del texto; cada cámara cerrada lleva la franja y la leyenda, no solo atenuación.

## Typography

**Display Font:** Atkinson Hyperlegible Next (con system-ui, -apple-system, Segoe UI, sans-serif)
**Body Font:** Atkinson Hyperlegible Next (misma familia)
**Label/Mono Font:** Atkinson Hyperlegible Mono (con ui-monospace, SF Mono, Menlo, monospace), solo para cifras

**Character:** Una familia diseñada para legibilidad máxima, servida localmente desde `/css/fuentes/` (licencia OFL, rango de peso 200–800). La jerarquía la marcan el tamaño y el peso, no un contraste entre familias; la Mono aparece únicamente donde hay números que comparar o copiar.

### Hierarchy
- **Headline** (700, 1.75 rem, 1.2; 1.5 rem por debajo de 720 px): título de pantalla ("Registrar nave", "Expediente: …"), con `text-wrap: balance`.
- **Title** (700, 1.1875 rem, 1.3): título de cámara o tarjeta, junto a su placa.
- **Marca** (700, 1.125 rem, +0.01em): el nombre del producto en la barra.
- **Body** (400, 1 rem, 1.5): texto corrido y campos; el sumario se limita a 64ch.
- **Body compacto** (400, 0.9375 rem): avisos, tablas, notas y leyendas de cerrojo.
- **Label** (600, 0.875 rem): rótulos de campo; la pista entre paréntesis baja a 400 en tinta suave.
- **Label de dato** (700, 0.8125 rem, +0.04em, mayúsculas): encabezados de columna y rótulos de la ficha de la nave. Solo rotulan un dato que está debajo o al lado.
- **Placa** (800, 1.375 rem, 1, cifras tabulares): el número de cámara.
- **Cifra** (Mono, 1 rem, cifras tabulares): números de los datos (tonelaje, eslora, idoneidad, porcentajes) y los campos `number`.
- **Cifra mayor** (Mono 700, 2 rem, 1.1): el número de registro en la ficha; es la cifra que manda.

### Named Rules
**Regla de la Mono para cifras.** La Mono solo se aplica a números que se comparan o se transcriben. Los títulos, rótulos y botones van siempre en Atkinson Hyperlegible Next.

**Regla de la mayúscula que rotula.** Las mayúsculas espaciadas existen solo como rótulo de un dato (columna de tabla, campo de ficha). No se colocan sobre un título como antetítulo.

## Layout

Columna única centrada de 960 px como máximo (`main`), con 40 px arriba, 24 px a los lados y 64 px abajo. Las pantallas de acceso (inicio de sesión, crear cuenta) usan la variante estrecha de 480 px. La barra negra ocupa todo el ancho, con altura mínima de 56 px.

Todo el espaciado sale de la escala de 8 px (e1–e8). Las tarjetas se separan 24 px entre sí; los fieldset internos se separan 32 px y se dividen con un filete superior de 1 px que nace de la leyenda. Los campos se ordenan en una rejilla de dos o tres columnas con 16 px de separación vertical y 24 px horizontal. Las acciones van a 24 px del último campo; la acción secundaria que descarta lo escrito ("Limpiar formulario") se aleja a la derecha, lejos de Guardar.

Por debajo de 720 px las rejillas pasan a una columna, el relleno lateral baja a 16 px, el botón de consulta ocupa todo el ancho y las tablas de listado se apilan: cada fila se convierte en un bloque y cada celda muestra el rótulo de su columna en el estilo de label de dato.

## Elevation & Depth

Sistema plano. No hay sombras de elevación: la profundidad la dan el contraste tonal (tarjeta blanca sobre hormigón) y los filetes de 1 px. La única `box-shadow` del sistema es un anillo interior de 1 px rojo que engrosa el borde de un campo con error; es un borde, no elevación.

### Named Rules
**Regla del muro plano.** Ninguna superficie flota. Si algo debe destacar, lo hace con filete, tono o la línea superior de cámara, nunca con sombra.

## Shapes

Esquinas casi rectas (radio de 2 px) en todo: tarjetas, campos, botones, placas, avisos y etiquetas. Bordes de 1 px; el único borde de 2 px es el contorno negro de la placa. La geometría recurrente es la línea superior de 6 px de las cámaras, que sigue el radio superior de la tarjeta, y la franja de compuerta: un patrón SVG de 16 px con diagonales amarillas y negras.

## Components

### Buttons
Firmes y directos, sin adorno.
- **Shape:** esquinas casi rectas (2 px), altura mínima de 44 px.
- **Primary:** fondo azul de agua, texto blanco 700 a 1 rem, relleno horizontal de 24 px, borde de 1 px del mismo azul.
- **Hover / Focus:** hover a agua honda; presionado a negro de muro; transición de 150 ms ease-out. El foco es un contorno de 2 px en tinta con 2 px de separación (en la barra, el contorno es amarillo).
- **Secondary:** fondo blanco, texto en tinta, borde en filete de campo; hover a hormigón alto con borde en tinta.
- **Disabled:** opacidad 0.5 y cursor no permitido.

### Chips
- **Style:** etiquetas en línea de 2 px × 8 px, borde de 1 px, 0.8125 rem, 700. Estado de la nave en verde siga con espaciado de 0.03em; cuota de participación en azul de agua sobre agua clara, en Mono; historial en tinta suave sobre hormigón alto.
- **State:** no son interactivas; describen un estado del dato.

### Cards / Containers
- **Corner Style:** 2 px.
- **Background:** superficie blanca sobre hormigón.
- **Shadow Strategy:** ninguna (ver Elevation & Depth).
- **Border:** 1 px en filete.
- **Internal Padding:** 24 px × 32 px; 24 px × 16 px por debajo de 720 px. Las cámaras suben su relleno superior a 32 px para dejar sitio a la línea superior.

### Inputs / Fields
- **Style:** 44 px de alto, relleno horizontal de 12 px, borde de 1 px en filete de campo, fondo blanco, 2 px de radio. El select usa un chevrón SVG propio en tinta.
- **Focus:** contorno de 2 px en azul de agua sin separación y borde en azul de agua; hover sin foco oscurece el borde a tinta.
- **Error / Disabled:** error: borde rojo alto engrosado con un anillo interior de 1 px y mensaje debajo en tinta roja 600 a 0.875 rem, que nombra el campo. Deshabilitado: fondo hormigón alto y cursor no permitido.

### Navigation
- **Barra:** franja negra de muro, marca en blanco 700, subtítulo en gris de muro a 0.875 rem, sesión y enlace "Salir" a la derecha (en móvil, en su propia línea). Los enlaces son blancos y pasan a amarillo en hover.
- **Migas:** enlace de regreso en azul de agua 600 a 0.875 rem, con chevrón SVG de 16 px y subrayado solo en hover.

### Avisos (veredicto)
Semáforo de la esclusa. Bloque con borde de 1 px, 2 px de radio, texto 600 a 0.9375 rem y un icono SVG de 20 px a la izquierda (relleno izquierdo de 48 px).
- **Libre / siga:** círculo verde con visto blanco, tinta verde sobre fondo verde claro.
- **Ocupado / alto:** octágono rojo con barra blanca, tinta roja sobre fondo rojo claro.
- **Neutro / información:** anillo con "i" en tinta suave, tinta sobre fondo neutro.
Se muestra solo cuando el JS asigna `data-estado`.

### Cámara (componente firma)
Una tarjeta numerada que representa un paso del trámite. Su estado sale del propio formulario: está cerrada mientras su fieldset esté deshabilitado o cuando el JS le añade la clase funcional de bloqueo; está cruzada cuando su condición ya se cumplió (nombre libre, al menos un propietario con cuota, agente vigente designado).
- **Cerrada:** franja de compuerta en el borde superior (6 px), placa blanca con borde negro, contenido atenuado (fieldset a 0.6; cámara bloqueada con sus hijos a 0.5) y, en la cámara 2, la leyenda de cerrojo en hormigón alto que dice qué la abre. Los errores de campo se ocultan mientras está cerrada.
- **Activa:** línea de agua azul en el borde superior y placa amarilla con borde negro.
- **Cruzada:** línea de agua y placa en azul de agua con número blanco.
- **Movimiento:** al abrirse, las dos hojas de la compuerta se retiran hacia los muros (máscara sobre la propiedad registrada `--hoja`, de 50 % a 0 %, 260 ms con salida exponencial `cubic-bezier(.16, 1, .3, 1)`) y dejan a la vista la línea de agua, que es fija. La placa cambia de color en 200 ms. Con movimiento reducido, el cambio es instantáneo.
- **Adaptación registrada:** el contrato de dirección describía el movimiento firma como "el agua sube en la cámara 2 (~250 ms)". Lo construido es la retirada de las hojas. Motivo: se lee como una compuerta de busco que se abre, no requiere cambio de maquetación, se resuelve solo con CSS, y una banda que subiera quedaría oculta bajo la franja cerrada.

### Ficha de la nave
Rejilla automática de columnas de 10 rem como mínimo. Cada dato lleva un rótulo de label de dato encima y su valor en 600 a 1 rem; el número de registro, primer dato, sube a cifra mayor.

## Do's and Don'ts

### Do:
- **Do** representar cada paso del trámite como una cámara numerada con placa y línea superior, y derivar su estado del formulario (fieldset deshabilitado o clase de bloqueo), no de un color suelto.
- **Do** acompañar toda cámara cerrada con la franja de compuerta y, si el motivo no es evidente, con una leyenda de cerrojo que diga qué la abre.
- **Do** usar la escala de 8 px (4, 8, 16, 24, 32, 40, 48, 64 px), filetes de 1 px y radio de 2 px.
- **Do** mantener controles de 44 px de alto y foco visible: contorno de 2 px en tinta (amarillo sobre la barra, azul de agua en campos).
- **Do** acompañar cada aviso de una forma SVG y texto, en tinta oscura sobre fondo claro de su señal.
- **Do** cargar fuentes y cualquier recurso estático nuevo bajo `/css/`; solo `/css/**` y `/js/**` son públicos y la pantalla de inicio de sesión no tiene sesión.
- **Do** limitar los cambios a la presentación: conservar `id`, `name`, `for`, `value`, `data-*`, las clases funcionales `oculto` y `bloqueado`, y seguir estilizando `#resultado[data-visible="si"]`, `.veredicto[data-estado]` y las clases que inyecta el JS (cifra, estado-etiqueta, enlace-fila, vacio, pista, cuota, etiqueta-historial).
- **Do** escribir el texto de ejemplo en español de Panamá con trato de "usted" ("Se habilita cuando el nombre consultado esté libre.").
- **Do** verificar contraste AA en cada combinación nueva; el piso actual es 4.80:1 (borde de campo sobre blanco).

### Don't:
- **Don't** usar el amarillo de seguridad fuera de placas, franja de compuerta, señales sobre la barra negra y selección de texto.
- **Don't** usar el rojo para nada que no sea un error o la señal de "alto".
- **Don't** comunicar un estado solo con color o solo con atenuación.
- **Don't** añadir sombras de elevación ni degradados decorativos.
- **Don't** usar la Mono para títulos, rótulos o botones.
- **Don't** colocar rótulos en mayúsculas como antetítulo encima de un título.
- **Don't** usar logotipos, colores ni marcas de la AMP o del Canal de Panamá mientras la identidad siga abierta.
- **Don't** animar algo más que la apertura de la compuerta y los cambios de color de estado, y nunca sin respetar el movimiento reducido.
