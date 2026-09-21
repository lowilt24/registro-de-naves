# Sprint 3 — pantallas

Segunda parte del Sprint 3: las pantallas de HU-05 y HU-06, más el arreglo
de la de registro de nave.

## Cómo aplicarlo

1. Estar en la rama `sprint-3`.
2. Copiar el contenido de esta carpeta sobre la raíz del repo (combinar y reemplazar).
3. `.\mvnw.cmd spring-boot:run`
4. Entrar a `http://localhost:8080/login.html` con `agente@navesitas.pa` / `Navesitas2026*`.

No hace falta recompilar con `verify`: solo cambian archivos estáticos.

## Archivos

| Archivo | Qué hace |
|---|---|
| `naves.html` / `js/naves.js` | Registro de nave, **sin** propietario ni agente |
| `expediente.html` / `js/expediente.js` | HU-05 y HU-06, por nave |
| `js/comun.js` | Utilidades compartidas y manejo de sesión vencida |
| `login.html` | Agrega el aviso de sesión expirada |
| `css/app.css` | Estilos del expediente |

## El recorrido de la demo

1. Entrar con el usuario agente.
2. Consultar un nombre libre, registrar la nave.
3. Clic en **Abrir expediente de la nave**.
4. Intentar designar agente residente: el formulario está **bloqueado** y
   avisa que falta un propietario. Esa es la precondición de HU-06.
5. Registrar un propietario persona jurídica con 60% de participación.
   Notar que el campo *país de constitución* aparece solo al elegir Jurídica.
6. El formulario de agente se desbloquea. Designar uno.
7. Designar otro: el anterior pasa a "Designaciones anteriores".

Ese recorrido cubre los dos criterios completos y sirve como guion de la demo.

## Qué queda

- Casos de Katalon para HU-05 y HU-06.
- Actualizar los de HU-03 y HU-04 para que inicien sesión.
- La prueba de Wfuzz de manipulación del ID de nave.
