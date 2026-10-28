# Contrato de la API — trámite (HU-09), naves por estado (HU-10) y bandeja de la DGMM (HU-11)

Sprint 5. Todos los endpoints exigen sesión (cookie `JSESSIONID` de
`POST /api/auth/login`). Los errores siempre traen `{ "mensaje": "..." }` y,
si el error es de un campo, también `"campos": { "campo": "texto" }`.

Usuarios de demo (contraseña `Navesitas2026*`):

| Correo | Rol | Qué puede hacer con el trámite |
|---|---|---|
| `agente@navesitas.pa` | AGENTE_NAVIERO | Ver y mover **solo** los trámites de las naves que registró: subsanar y desistir |
| `revisor@navesitas.pa` | FUNCIONARIO_DGMM | Ver y mover **cualquier** trámite: admitir, observar, aprobar, rechazar |
| `auditor@navesitas.pa` | AUDITOR | Solo leer el estado y el historial |

## Estados y acciones

```
SOLICITUD_PRESENTADA --ADMITIR (DGMM)-->  EN_REVISION
SOLICITUD_PRESENTADA --DESISTIR (agente)--> DESISTIDA
EN_REVISION          --APROBAR (DGMM)-->  APROBADA
EN_REVISION          --RECHAZAR (DGMM)--> RECHAZADA
EN_REVISION          --OBSERVAR (DGMM)--> OBSERVADA
OBSERVADA            --SUBSANAR (agente)--> EN_REVISION
OBSERVADA            --DESISTIR (agente)--> DESISTIDA
```

`APROBADA`, `RECHAZADA` y `DESISTIDA` son finales. El trámite nace en
`SOLICITUD_PRESENTADA` cuando el agente registra la nave (`POST /api/naves`).

## Orden de los errores

Se revisan en este orden; sale el primero que aplique:

| Código | Cuándo |
|---|---|
| 401 | Sin sesión |
| 400 | Cuerpo mal formado: falta `accion`, acción inventada, `PRESENTAR`/`MIGRACION`, observación de más de 1000 caracteres, ID no numérico, texto con carácter nulo `\u0000` (SEC-08: "El texto contiene caracteres no permitidos.") |
| 404 | La nave no existe |
| 403 | El rol o la relación con la nave no dan esa acción (agente que aprueba, DGMM que subsana, auditor, nave de otro agente) |
| 409 | La acción no existe desde el estado actual (aprobar sin admitir, cualquier cosa en un estado final) o dos personas cambiaron el trámite a la vez |
| 409 | Admitir o subsanar (destino "En revisión") con documentos obligatorios faltantes. Cuerpo: `{"mensaje": "Faltan documentos obligatorios: ITC, SMC", "faltantes": ["ITC", "SMC"]}`, siempre en el orden `CERTIFICADO_PROPIEDAD`, `PODER_NOTARIAL`, `ITC`, `SMC`. Un documento cuenta si es vigente y está en `PENDIENTE` o `APROBADO` (PO, 13-10-2026) |
| 400 | Observar o rechazar sin observación (`campos.observacion`) |

## Endpoints

### `GET /api/naves/{naveId}/tramite`

```json
{
  "naveId": 7,
  "nave": "Brisa del Pacífico",
  "estado": "SOLICITUD_PRESENTADA",
  "esFinal": false,
  "acciones": ["ADMITIR"],
  "creadoEn": "2026-10-13T17:57:02.123-05:00",
  "actualizadoEn": "2026-10-13T17:57:02.123-05:00"
}
```

`acciones` son las que **quien consulta** puede pedir **en este estado**. La
pantalla pinta un botón por cada una y ninguno más. Para el agente dueño en
`SOLICITUD_PRESENTADA` es `["DESISTIR"]`; para la DGMM, `["ADMITIR"]`; para
el auditor, `[]`.

### `POST /api/naves/{naveId}/tramite/estado`

```json
{ "accion": "OBSERVAR", "observacion": "Falta el certificado de arqueo (ITC)." }
```

`observacion` es obligatoria para `OBSERVAR` y `RECHAZAR` y opcional para el
resto. Responde 200 con el mismo cuerpo que el `GET`, ya en el estado nuevo.

### `GET /api/naves/{naveId}/tramite/historial`

De la más vieja a la más nueva:

```json
[
  { "fecha": "...", "usuario": "Agente Naviero de Prueba", "rol": "AGENTE_NAVIERO",
    "accion": "PRESENTAR", "estadoAnterior": null, "estadoNuevo": "SOLICITUD_PRESENTADA",
    "observacion": null },
  { "fecha": "...", "usuario": "Funcionario Revisor DGMM", "rol": "FUNCIONARIO_DGMM",
    "accion": "ADMITIR", "estadoAnterior": "SOLICITUD_PRESENTADA", "estadoNuevo": "EN_REVISION",
    "observacion": null }
]
```

`MIGRACION` aparece como primera entrada en las naves que ya existían antes
de la V6.

### `GET /api/naves?estado=EN_REVISION` (HU-10)

Las naves **del usuario de la sesión** cuyo trámite está en ese estado. El
parámetro es opcional: sin él la respuesta es la misma lista de siempre.
Un estado que no existe (`?estado=VOLANDO`, `?estado=en_revision`,
`?estado=REGISTRADA`) es 400, nunca 500.

Cada nave trae ahora dos estados, rotulados aparte:

```json
{ "id": 7, "nombre": "...", "estado": "REGISTRADA", "estadoTramite": "EN_REVISION", "...": "..." }
```

`estado` es el de la nave (no cambió; TC_HU03_01 sigue viendo `REGISTRADA`).
`estadoTramite` es el del trámite.

### `GET /api/naves/conteo-por-estado` (HU-10)

```json
{
  "estados": {
    "SOLICITUD_PRESENTADA": 3, "EN_REVISION": 1, "OBSERVADA": 0,
    "APROBADA": 0, "RECHAZADA": 0, "DESISTIDA": 0
  },
  "total": 4
}
```

Trae los seis estados, con cero los que no tienen naves.

### `GET /api/naves/{id}` (cambio)

La DGMM y el auditor ya pueden ver la **ficha** de cualquier nave, para
abrir el expediente por ID mientras no exista la bandeja de la DGMM (P7).
Propietarios, agente residente y documentos siguen siendo solo del agente
que registró la nave (403 para los demás).

### Reglas de la PO del 13-10-2026 fuera de `/tramite`

- **`POST /api/naves` (HU-03):** solo `AGENTE_NAVIERO`. Otro rol recibe 403 "Solo un agente
  naviero puede registrar naves." (issue #10).
- **Expediente de solo lectura:** con el trámite en `APROBADA`, `RECHAZADA` o `DESISTIDA`,
  `POST /api/naves/{id}/documentos`, `POST /api/propietarios` y
  `POST /api/naves/{id}/agente-residente` responden 409 "El trámite ya terminó (Aprobada); el
  expediente es de solo lectura." El orden de los errores es 404 → 403 → 409, así que a quien no
  es dueño se le sigue respondiendo 403.

## Bandeja de la DGMM (HU-11, Sprint 6)

Decisión D1 de la PO (aceptada): la DGMM ve los trámites de todas las naves,
el auditor solo lee y el agente recibe 403. Por defecto se muestran
Solicitud presentada y En revisión, el más antiguo primero. Se filtra por la
tabla `tramites`, nunca por `nave.estado`.

### `GET /api/tramites?estado=EN_REVISION`

`estado` es opcional. Sin él, devuelve `SOLICITUD_PRESENTADA` y
`EN_REVISION`. Con él, solo ese estado. Siempre ordenado por `ultimaAccion`,
de la más vieja a la más nueva.

```json
[
  {
    "naveId": 7,
    "nave": "Brisa del Pacífico",
    "agente": "Agente Naviero de Prueba",
    "estado": "SOLICITUD_PRESENTADA",
    "ultimaAccion": "2026-10-13T17:57:02.123-05:00",
    "diasEnEspera": 5
  }
]
```

| Campo | Qué es |
|---|---|
| `naveId` | ID de la nave; el botón **Abrir** lleva a `expediente.html?naveId=N` |
| `nave` | Nombre de la nave |
| `agente` | Nombre del usuario que registró la nave |
| `estado` | Estado del trámite |
| `ultimaAccion` | Hora del último cambio de estado (`tramites.actualizado_en`) |
| `diasEnEspera` | Días de calendario, en hora de Panamá, desde `ultimaAccion`. 0 = hoy |

Sin trámites devuelve `[]` con 200.

### `GET /api/tramites/conteo-por-estado`

Igual que el de HU-10, pero sobre **todas** las naves:

```json
{ "estados": { "SOLICITUD_PRESENTADA": 3, "EN_REVISION": 1, "OBSERVADA": 0,
               "APROBADA": 0, "RECHAZADA": 0, "DESISTIDA": 0 },
  "total": 4 }
```

### Quién la ve

| Petición | Sin sesión | AGENTE_NAVIERO | AUDITOR | FUNCIONARIO_DGMM |
|---|---|---|---|---|
| `GET /api/tramites` | 401 | 403 | 200 | 200 |
| `GET /api/tramites?estado=EN_REVISION` | 401 | 403 | 200 | 200 |
| `GET /api/tramites/conteo-por-estado` | 401 | 403 | 200 | 200 |
| `GET /api/tramites?estado=VOLANDO` | 401 | **400** | 400 | 400 |

Decisión de la fila marcada: con un estado que no existe, el agente recibe
**400** y no 403. Sigue el orden del equipo (401 → 400 → 404 → 403 → 409):
el parámetro se valida antes que el rol. También dan 400 `en_revision`
(minúsculas), `REGISTRADA` (es un estado de la nave, no del trámite) y
cualquier texto con inyección.

El 403 trae `{ "mensaje": "La bandeja de trámites es de la DGMM. Sus naves están en «Mis naves»." }`.
El administrador también recibe 403 (D1 no lo incluye).

### Entrada después del login

`POST /api/auth/login` redirige según el rol:

| Rol | Va a |
|---|---|
| FUNCIONARIO_DGMM | `/bandeja.html` |
| AUDITOR | `/bandeja.html` |
| AGENTE_NAVIERO, ADMINISTRADOR | `/naves.html` (como antes) |

### Para Wfuzz (Sprint 6)

- `?estado=FUZZ` sobre `/api/tramites`: se espera 400 o 200, nunca 500.
- La bandeja con sesión de agente: se espera 403.

## Para Wfuzz

Superficie nueva:

- `estado` en `GET /api/naves?estado=FUZZ`.
- `{naveId}` en `/api/naves/FUZZ/tramite`, `/tramite/historial` y
  `/tramite/estado` (manipulación del ID del trámite).
- `accion` y `observacion` en el cuerpo del `POST`.

Lo esperado es 400, 403, 404 o 409 según la tabla de arriba, y 0 respuestas 500.
