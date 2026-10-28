# Revisión manual — Sprint 6 (bandeja y cámara 5)

Guía preparada el 24-10-2026, sobre `b153fb1`. La hace Louis.

## Antes de empezar

- Levantar el entorno: `colima start`, `docker compose up -d` y `./mvnw spring-boot:run`.
  La app queda en http://localhost:8080.
- Usar una ventana por rol (normal, incógnito u otro navegador) para que no se mezclen las
  sesiones. La contraseña de los tres usuarios es `Navesitas2026*`.

| Usuario | Rol |
|---|---|
| `revisor@navesitas.pa` | DGMM |
| `agente@navesitas.pa` | Agente |
| `auditor@navesitas.pa` | Auditor |

Naves del agente de demo, con los 4 documentos en `PENDIENTE` al 24-10. Si alguna ya cambió
de estado, sirve otra igual: el SQL de abajo las busca.

| Nave | Estado del trámite | Para qué |
|---|---|---|
| **1801** | Solicitud presentada | Ver que no se revisa fuera de En revisión |
| **1800** | En revisión | Camino completo hasta `ABANDERADA` |
| **1799** | En revisión | Rechazo, nave `RECHAZADA` |
| **1795** | En revisión | Dos pestañas a la vez (opcional) |

Para entrar a una nave: `/expediente.html?naveId=N`.

```sql
-- docker exec -it amp-db psql -U registro -d registro_naves
SELECT n.id, t.estado
  FROM nave n
  JOIN tramites t ON t.nave_id = n.id
  JOIN usuario u ON u.id = n.registrado_por_id
 WHERE u.correo = 'agente@navesitas.pa'
   AND t.estado IN ('EN_REVISION', 'SOLICITUD_PRESENTADA')
   AND (SELECT count(*) FROM documentos d
         WHERE d.nave_id = n.id AND d.vigente AND d.estado = 'PENDIENTE') = 4
 ORDER BY n.id DESC
 LIMIT 10;
```

## A. Bandeja (HU-11)

**Como DGMM (`revisor@`):**

- [ ] A1. Al iniciar sesión te lleva a **/bandeja.html**.
- [ ] A2. Sin filtro aparecen solo los trámites en **Solicitud presentada** y **En revisión**,
  del más antiguo al más nuevo.
- [ ] A3. Los botones de filtro muestran los 6 estados con su cantidad. Al tocar un estado, la
  tabla muestra solo ese estado.
- [ ] A4. La tabla tiene las columnas Nave, Agente, Estado del trámite, Última acción, En espera
  y Expediente. El enlace abre el expediente correcto.
- [ ] A5. Con el navegador a 375 px de ancho (DevTools → modo móvil), cada fila se ve como una
  tarjeta con etiquetas y no hay desplazamiento horizontal de la página.

**Como auditor:**

- [ ] A6. Ve la misma bandeja, solo para leer.

**Como agente:**

- [ ] A7. Al entrar a `/bandeja.html` sale el aviso *"La bandeja de trámites es de la DGMM."*
  y no se ve ningún dato.

En la base de desarrollo no hay ningún estado con 0 trámites, así que no se puede ver el
mensaje de bandeja vacía. Ese caso ya lo prueba TC-42.

## B. Cámara 5: revisión de documentos (HU-12)

**B1. DGMM, nave 1801 (Solicitud presentada)**

- [ ] Propietarios y agente residente están ocultos. Documentos sí se ve, pero **sin**
  formulario de carga.
- [ ] Aparece la nota *"Los documentos se revisan con el trámite En revisión. Ahora está en
  Solicitud presentada."* y no hay botones Aprobar ni Observar.
- [ ] Al usar **Admitir** en la cámara 6, aparecen los botones en la cámara 5 **sin recargar
  la página**.

**B2. DGMM, nave 1800 (En revisión)**

- [ ] **Aprobar** el Certificado de propiedad: sale el aviso *"… aprobado."*, la fila queda en
  APROBADO con el nombre del funcionario y la fecha, y los botones de esa fila desaparecen.
- [ ] Aprobar también el Poder notarial y el ITC.
- [ ] **Observar** el SMC. Comprobar esto:
  - el formulario se abre con el cursor en el campo;
  - si se guarda vacío, sale el error *"Escriba qué debe corregir el agente."*;
  - Cancelar cierra el formulario.
- [ ] Escribir una observación y guardar: el SMC queda OBSERVADO y el texto aparece debajo del
  estado.
- [ ] **Aprobar el trámite** en la cámara 6 da el error *"…faltan documentos sin aprobar: SMC.
  Revíselos en la sección Documentos (paso 5)."*
- [ ] **Descargar** cualquier documento funciona.

**B3. Agente, nave 1800**

- [ ] Ve la observación y el nombre del revisor. El aviso dice *"Hay 1 documento observado:
  SMC. Cárguelo de nuevo con las correcciones."*
- [ ] **No** ve los botones Aprobar ni Observar.
- [ ] Cargar un SMC nuevo (cualquier PDF pequeño): queda como v2 en PENDIENTE. Con "historial",
  la v1 conserva su observación.

**B4. DGMM, nave 1800 otra vez**

- [ ] Aprobar el SMC v2 y luego **Aprobar el trámite**: pasa a Aprobada.
- [ ] En la ficha, **"Estado de la nave" cambia a ABANDERADA** sin recargar.
- [ ] Los botones de revisión desaparecen.

**B5. DGMM, nave 1799**

- [ ] **Rechazar** el trámite con una observación: la nave queda **RECHAZADA**, con la
  etiqueta en rojo.
- [ ] El agente ve lo mismo en su lista de naves (`/naves.html`).

**B6. Auditor, nave 1800**

- [ ] Ve los documentos y las observaciones, y puede descargarlos.
- [ ] No hay botones ni formulario de carga, y aparece el aviso *"Vista de consulta…"*.

**B7. Opcional: dos pestañas de la DGMM en la nave 1795**

- [ ] Aprobar el ITC en la pestaña 1. En la pestaña 2, sin recargar, tocar Aprobar en el ITC:
  debe salir un 409 (*"El documento ya fue revisado."*) y la lista se actualiza sola.

**B8. Teclado**

- [ ] Con Tab se llega a Aprobar y Observar, el foco se ve, y Enter funciona.

**El ADMINISTRADOR no se prueba.** No hay usuario de demo con ese rol, y no se crean cuentas
con privilegios sin avisar antes.

## Evidencia

- Al final no se guardaron capturas: el resultado de cada paso quedó en las secciones 8 y 9
  de `qa/evidencias/sprint-6/LEEME.md`.
- Si algo no coincide, anotar el paso, lo esperado, lo obtenido y la captura. Se registra como
  issue con las etiquetas `bug` y `sprint-6` cuando Louis lo apruebe.
- Al terminar, el resultado se agrega como sección de `qa/evidencias/sprint-6/LEEME.md`.
