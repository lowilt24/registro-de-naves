# Sprint 5 — HU-09 y HU-10 (parte técnica de Ismael)

Estado del trámite (HU-09) y naves por estado (HU-10), según el
**Plan del Sprint 5**, sección "Trabajo técnico (Ismael)". La suite Katalon
TS05 y la regresión son de Louis; Wfuzz y el umbral de JaCoCo, de Abdiel.
Aquí no se tocan.

## Qué entra

| Paso del plan | Qué se hizo | Dónde |
|---|---|---|
| 1. Migración | Tablas `tramites` (una por nave, `CHECK` de los 6 estados) e `historial_estados`. Backfill idempotente: crea los trámites que faltan y su entrada inicial; si se corre dos veces no duplica nada | `V6__tramites_e_historial.sql` |
| 2. HU-09 backend | Registrar una nave crea su trámite. Ver estado + acciones permitidas, cambiar estado, ver historial | paquete `tramite/` |
| 3. HU-10 backend | `GET /api/naves?estado=X` y `GET /api/naves/conteo-por-estado` | `NaveController`, `TramiteService` |
| 4. Pantallas | Cámara 6 "Estado del trámite" en el expediente (estado, botones solo de lo permitido, línea de tiempo). En "Mis naves", chips con conteo y mensaje cuando no hay resultados | `expediente.html`, `js/tramite.js`, `naves.html`, `js/naves.js` |
| 5. Pruebas | Unitarias de la máquina (las 7 válidas y las 41 inválidas) y de `AutorizacionTramite` (rol × acción × dueño). Integración: 401, 404, 403, 409, 400, historial, un solo trámite por nave, aislamiento entre dos agentes | `src/test/java/.../tramite/` |
| Issues del Sprint 4 | #6 textos sin tildes, #7 "Contacto ·" suelto | `js/*.js`, `login.html`, `NaveRequest`, `ManejadorGlobalDeErrores` |

`mvnw verify`: **179 pruebas**, todas pasan (eran 58). Las nuevas son 54 de
`EstadoTramiteTest`, 47 de `AutorizacionTramiteTest` y 20 de
`TramiteIntegrationTest`.

Contrato de la API (para Katalon y Wfuzz): `CONTRATO-TRAMITE.md`.

## Propuestas que se aplicaron mientras la PO responde

El plan deja P1 a P9 pendientes. Se programó con la **propuesta** de cada
una. Si Madeleine decide otra cosa, esto es lo que cambia:

| # | Propuesta aplicada | Si cambia, se toca |
|---|---|---|
| P1 | El trámite nace en Solicitud presentada al registrar la nave | `EstadoTramite.INICIAL` y el `DEFAULT` de la V6 |
| P2 | Agente que aprueba o rechaza: **403**. Orden 401 → 404 → 403 → 409 | `TramiteService.cambiarEstado` |
| P3 | REGISTRADA y DOCUMENTOS_CARGADOS → Solicitud presentada; EN_REVISION → En revisión; ABANDERADA → Aprobada; RECHAZADA → Rechazada | El `CASE` de la V6 (**antes** de que la V6 llegue a `main`) |
| P4 | Subsanar reutiliza la carga de HU-07 (cámara 5); la pantalla lo dice | Nada en el backend |
| P5 | Observar y rechazar exigen observación; vacía → 400 | `AccionTramite.exigeObservacion` |
| P6 | Aprobada **no** cambia `nave.estado`. Ficha, tabla y cámara rotulan "Estado de la nave" y "Estado del trámite" por separado | — |
| P7 | Sin bandeja: la DGMM abre `expediente.html?naveId=N` | Se podrá hacer en el Sprint 6 |
| P8 | AUDITOR solo lee estado e historial | `AutorizacionTramite.puedeLeer` |
| P9 | Los trámites de la V6 llevan entrada inicial (`MIGRACION`, sin estado anterior, a nombre de quien registró la nave) | Bloque 4 de la V6 |

**P1 y P3 bloquean la V6.** Mientras `sprint-5` no se una a `main`, la V6 se
puede corregir. Después ya no, porque Flyway guarda su checksum y una
corrección iría en una V7.

## Cambios que afectan a otros

- **`NaveResponse` trae un campo nuevo, `estadoTramite`.** `estado` sigue
  siendo el de la nave y sigue diciendo `REGISTRADA` (TC_HU03_01 no cambia).
- **La DGMM y el auditor pueden ver la ficha de cualquier nave**
  (`GET /api/naves/{id}`). Sin eso la DGMM no podía abrir el expediente para
  admitir u observar. Propietarios, agente y documentos siguen siendo solo
  del agente dueño.
- **Mensajes con tilde** (issue #6). Ningún caso de Katalon compara esos
  textos, así que no rompe nada. Se revisaron los `verifyMatch` de TS01 a TS04.
  El marcador interno `'sin sesion'` de `comun.js` **no** se cambió: lo
  comparan otros tres archivos.
- **CSS nuevo en `app.css`.** El plan dice que, si la línea de tiempo pide
  CSS nuevo, se consulte antes. Quedó en una sección aparte al final
  ("Tramite (HU-09, HU-10)"), solo con los tokens de `DESIGN.md`. **Falta el
  visto bueno del equipo antes del PR.**
- En pantalla angosta, la tabla "Mis naves" tiene una columna más.

## Pendiente

- Respuestas de la PO a P1 y P3 (bloquean la V6) y al resto.
- La DGMM revisa documentos (aprobar u observar cada uno). Es parte de P4 y
  queda para el Sprint 6.
- Bandeja de la DGMM (P7).

---

# Paso a paso para Ismael

Sigue los pasos en orden, sin saltarte ninguno. Lo que va en `gris` se copia
tal cual en la terminal y se ejecuta con **Enter**.

## Paso 1 — Abrir el proyecto y la terminal

1. Abre **Docker Desktop** (menú Inicio → escribe "Docker" → Enter). Espera
   a que abajo a la izquierda diga **Engine running** (en verde).
2. Abre **VS Code**. Arriba a la izquierda: **File → Open Folder…** → elige
   `Documentos\registro-de-naves` → **Seleccionar carpeta**.
3. Abre la terminal: en la barra de arriba, **Terminal → New Terminal**.
   Aparece abajo, en un panel.

## Paso 2 — Cambiarte a la rama del Sprint 5

Los cambios ya están en tu computadora, en una rama llamada `sprint-5`.

```
git checkout sprint-5
```

Debe decir `Switched to branch 'sprint-5'`. Para confirmarlo:

```
git log --oneline -3
```

La primera línea debe decir **"Sprint 5: estado del tramite y naves por
estado (HU-09, HU-10)"**.

## Paso 3 — Levantar la base de datos

```
docker compose up -d
```

Espera a que diga `Started` (o `Running`) en `amp-db` y `amp-correo`.

## Paso 4 — La consulta que pide el plan ANTES de la V6

El plan pide anotar cuántas naves hay en cada estado **antes** de que corra
la V6. La V6 corre sola la primera vez que arrancas la app o las pruebas, así
que esto va primero:

```
docker exec -it amp-db psql -U registro -d registro_naves -c "SELECT estado, count(*) FROM nave GROUP BY estado"
```

Saca una captura del resultado: es evidencia. Si todo dice `REGISTRADA`,
todas las naves pasan a "Solicitud presentada".

## Paso 5 — Correr las pruebas

```
.\mvnw.cmd clean verify
```

Tarda unos minutos. La primera vez descarga JaCoCo (lo agregó el equipo), así
que necesitas internet. Al final debe decir:

```
Tests run: 179, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Si sale `BUILD FAILURE`, copia lo que salió en rojo y pásamelo.

## Paso 6 — Probarlo en el navegador

1. Arranca la app:
   ```
   .\mvnw.cmd spring-boot:run
   ```
   Espera la línea `Started RegistroNavesApplication`. **No cierres esa terminal.**
2. En Chrome entra a <http://localhost:8080/login.html> con
   `agente@navesitas.pa` / `Navesitas2026*`.
3. En "Mis naves" (abajo) verás una fila de botones: **Todas, Solicitud
   presentada, En revisión…** con un número cada uno. La tabla ahora tiene
   dos columnas de estado: **Estado de la nave** y **Estado del trámite**.
4. Registra una nave nueva (arriba, pasos 1 y 2). Fíjate en el **número de
   registro** que te da (por ejemplo, 12).
5. Abre una **ventana de incógnito** (Ctrl + Shift + N) y entra con
   `revisor@navesitas.pa` / `Navesitas2026*`.
6. En esa ventana escribe en la barra de direcciones
   `localhost:8080/expediente.html?naveId=12` (cambia 12 por tu número).
7. Baja hasta la cámara **6 · Estado del trámite**. Pulsa **Admitir**.
8. Escribe algo en **Observación** (por ejemplo, "Falta el ITC") y pulsa
   **Observar**. En la **Línea de tiempo** aparecen las acciones, la más
   nueva arriba.
9. Vuelve a la ventana normal (la del agente), abre el expediente de esa nave
   (botón **Abrir** en "Mis naves") y baja a la cámara 6. Ahora tienes
   **Subsanar** y **Desistir**. Pulsa **Subsanar**.
10. Vuelve a "Mis naves" y pulsa el chip **En revisión**: solo sale esa nave.
    Pulsa **Aprobada**: sale el mensaje "No tiene naves…" en vez de una tabla
    vacía.
11. Para apagar la app: en la terminal, **Ctrl + C**.

## Paso 7 — Subirlo a GitHub

```
git push -u origin sprint-5
```

Si pide iniciar sesión, usa tu cuenta de GitHub como en los sprints
anteriores. Luego entra a
<https://github.com/lowilt24/registro-de-naves/tree/sprint-5> y comprueba que
arriba sale tu commit. En la pestaña **Actions** el CI debe quedar en verde
(el plan pide "CI en verde").

## Paso 8 — Avisar al equipo

Puedes mandar esto por el grupo:

> Subí a `sprint-5` mi parte técnica: V6 (trámites e historial), HU-09 y HU-10
> en backend, cámara 6 en el expediente, chips de filtro en Mis naves y las
> pruebas (179 en verde). También cerré los issues #6 y #7.
> **Louis:** el contrato de la API para TS05 está en `CONTRATO-TRAMITE.md`.
> **Abdiel:** ahí mismo está la superficie nueva para Wfuzz.
> **Madeleine:** usé las propuestas de P1 a P9; P1 y P3 hay que confirmarlas
> antes de unir a `main`.
> **Equipo:** agregué CSS nuevo en una sección aparte de `app.css` para la
> línea de tiempo y los chips; el plan pide revisarlo antes del PR.
