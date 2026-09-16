# Casos de prueba en Katalon Studio — Sprint 2

Los seis casos de la lámina de calidad, contra la pantalla
`http://localhost:8080/naves.html`.

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
| propietario | `id=propietarioId` |
| agente | `id=agenteResidenteId` |
| btn_guardar | `id=btn-guardar` |
| aviso_form | `id=aviso-form` |
| resultado | `id=resultado` |

El estado del veredicto se lee del atributo `data-estado`: vale `libre`,
`ocupado` o `neutro`. Es más estable que comparar el texto completo.

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
   material `Acero`, propulsión `Motor diesel`, potencia `7800`, y seleccionar el
   primer propietario y el primer agente.
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

## Nota para la presentación

Los mismos seis casos ya están escritos como pruebas de backend en
`NaveIntegrationTest.java`, y corren solos en el pipeline de GitHub Actions.
Katalon prueba la pantalla; JUnit prueba el endpoint. Mencionar las dos capas
suma a la lámina de calidad.
