# Casos de prueba en Katalon Studio

- **Sprint 2 — TC-01 a TC-06 (HU-03, HU-04):** contra la pantalla
  `http://localhost:8080/naves.html`. Suite `TS02_Sprint2`.
- **Sprint 3 — TC-07 a TC-16 (HU-05, HU-06):** contra la API. Suite
  `TS03_Sprint3`. Ver la sección al final.

## Object Repository

Estos son los `id` reales del HTML. Si cambian en el código, hay que
actualizarlos aquí.

| Objeto | Selector |
|---|---|
| campo_nombre | `id=nombre` |
| btn_consultar | `id=btn-consultar` |
| veredicto | `id=veredicto` |
| campos_nave (fieldset) | `id=campos-nave` |
| tipo | `id=tipo` |
| servicio | `id=servicio` |
| tonelaje_bruto | `id=tonelajeBruto` |
| tonelaje_neto | `id=tonelajeNeto` |
| eslora / manga / puntal | `id=eslora`, `id=manga`, `id=puntal` |
| anio | `id=anioConstruccion` |
| lugar | `id=lugarConstruccion` |
| material | `id=materialCasco` |
| propulsion | `id=tipoPropulsion` |
| potencia | `id=potenciaKw` |
| btn_guardar | `id=btn-guardar` |
| aviso_form | `id=aviso-form` |
| resultado | `id=resultado` |

El estado del veredicto se lee del atributo `data-estado`: vale `libre`,
`ocupado` o `neutro`. Es más estable que comparar el texto completo.

> **Cambio del Sprint 3.** Los locators `id=propietarioId` e
> `id=agenteResidenteId` ya no existen: la nave dejó de pedir propietario y
> agente en el formulario de registro. Ahora se asignan después, desde el
> expediente, con HU-05 y HU-06.

---

## TC-01 — HU-03, positivo: registrar nave con datos completos

1. Abrir `/naves.html`.
2. Escribir en `campo_nombre` un nombre nuevo (usar un sufijo aleatorio para que
   la prueba sea repetible: `Nave Katalon ${random}`).
3. Clic en `btn_consultar`.
4. **Verificar** que `veredicto` tenga `data-estado = libre`.
5. **Verificar** que `campos_nave` ya no esté deshabilitado.
6. Llenar: tipo `CARGA`, servicio `INTERNACIONAL`, TRB `12000`, TRN `6400`,
   eslora `150`, manga `22.5`, puntal `13`, año `2020`, lugar `Astillero Balboa`,
   material `Acero`, propulsión `Motor diesel`, potencia `7800`.
   (Desde el Sprint 3 ya no se selecciona propietario ni agente en este paso.)
7. Clic en `btn_guardar`.
8. **Verificar** que `resultado` sea visible y contenga el texto `REGISTRADA`.

## TC-02 — HU-03, negativo: campos obligatorios vacíos

1. Consultar un nombre libre para abrir el formulario.
2. Dejar todos los campos vacíos.
3. Clic en `btn_guardar`.
4. **Verificar** que `aviso_form` tenga `data-estado = ocupado`.
5. **Verificar** que el `span` con `data-error="tipo"` no esté vacío.
6. **Verificar** que `resultado` **no** sea visible.

## TC-03 — HU-03, negativo: valores numéricos inválidos

1. Consultar un nombre libre.
2. Llenar todo bien excepto: TRB `-5` y año `1500`.
3. Clic en `btn_guardar`.
4. **Verificar** mensaje de error en `data-error="tonelajeBruto"` y en
   `data-error="anioConstruccion"`.
5. **Verificar** que no se creó la nave.

## TC-04 — HU-04, positivo: nombre disponible

1. Abrir `/naves.html`.
2. Escribir `Gaviota del Darien` en `campo_nombre`.
3. Clic en `btn_consultar`.
4. **Verificar** `data-estado = libre` y que el texto contenga `disponible`.
5. **Verificar** que `campos_nave` quedó habilitado.

## TC-05 — HU-04, negativo: nombre ya registrado

1. Escribir `Estrella del Istmo` (viene cargado por `V3__datos_demo.sql`).
2. Clic en `btn_consultar`.
3. **Verificar** `data-estado = ocupado`.
4. **Verificar** que `campos_nave` sigue deshabilitado.

## TC-06 — HU-04, negativo: bloqueo con variación de formato

Este es el caso que demuestra que la comparación es por nombre normalizado y no
por texto literal.

1. Escribir `  ESTRELLA   del   istmo  ` (mayúsculas mezcladas y espacios de más).
2. Clic en `btn_consultar`.
3. **Verificar** `data-estado = ocupado`.
4. **Verificar** que `btn_guardar` no se puede usar.

---

# Sprint 3 — HU-05 y HU-06

Suite **`TS03_Sprint3`**, diez casos (TC-07 a TC-16).

A diferencia de los del Sprint 2, estos van **contra la API** y no contra la
pantalla, igual que los scripts de HU-03 y HU-04. No usan Object Repository.

## Cómo correrlos

1. Levantar la base y la aplicación: `docker compose up -d` y `./mvnw spring-boot:run`.
2. En Katalon: **File → Refresh** (si los archivos se agregaron fuera del IDE).
3. Ejecutar la suite `TS03_Sprint3`.

Cada caso **crea su propia nave** con un sufijo de marca de tiempo, así que son
repetibles y no dependen de los datos de `V3__datos_demo.sql`. Todos inician
sesión primero: desde el Sprint 3 todo `/api/**` exige sesión (hallazgo SEC-04).

## Casos

| Caso | HU | Verifica | Espera |
|---|---|---|---|
| TC-07 `TC_HU05_01_Propietario_Juridico_Vinculado` | HU-05 | Persona jurídica vinculada a nave propia | 201, `tipo=JURIDICA`, 100% por defecto, aparece en el listado |
| TC-08 `TC_HU05_02_Propietario_Natural_Sin_Pais` | HU-05 | Persona natural no se constituye en ningún país | 201, `paisConstitucion` nulo aunque se envíe |
| TC-09 `TC_HU05_03_Juridica_Sin_Pais_Rechazada` | HU-05 | Regla condicional de HU-05 | 400 con `campos.paisConstitucion` |
| TC-10 `TC_HU05_04_Participacion_Excede_100` | HU-05 | Suma de cuotas de copropiedad | 201 al 60%, luego 409 con otro 60% |
| TC-11 `TC_HU05_05_Identificacion_Duplicada` | HU-05 | RUC/cédula/pasaporte único | 201, luego 409 |
| TC-12 `TC_HU06_01_Designacion_Valida` | HU-06 | Designar agente y consultar el vigente | 201 con `vigente=true`, GET 200 |
| TC-13 `TC_HU06_02_Sin_Propietarios_Rechazada` | HU-06 | Precondición central de HU-06 | 409 |
| TC-14 `TC_HU06_03_Reemplazo_Y_Historial` | HU-06 | El agente anterior se conserva | Historial con 2, un solo vigente |
| TC-15 `TC_HU06_04_Datos_Contacto_Invalidos` | HU-06 | Formato de correo y teléfono | 400 con `campos.correo` y `campos.telefono` |
| TC-16 `TC_HU06_05_Consulta_Sin_Designacion` | HU-06 | Nave sin agente designado | 404, e historial `[]` |

## Qué no cubren

No hay casos de **401** (sin sesión) ni **403** (nave de otro usuario). Esos
criterios existen en `LEEME-SPRINT3.md` pero corresponden al bloque de
seguridad, no al de calidad.

---

## Nota para la presentación

Los seis casos del Sprint 2 ya están escritos como pruebas de backend en
`NaveIntegrationTest.java`, y los de HU-05 y HU-06 en
`PropietarioIntegrationTest.java` y `AgenteIntegrationTest.java`. Todas corren
solas en el pipeline de GitHub Actions. Katalon prueba la pantalla y la API;
JUnit prueba el endpoint. Mencionar las dos capas suma a la lámina de calidad.

La evidencia de la última corrida está en `qa/evidencias/sprint-3/`. El reporte
completo de Katalon **no se versiona**: registra la contraseña del usuario de
demo en texto plano (ver el LEEME de esa carpeta).
