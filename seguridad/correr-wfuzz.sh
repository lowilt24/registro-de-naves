#!/usr/bin/env bash
# Navesitas — Sprint 2. Pruebas de fuzzing sobre los endpoints de HU-03 y HU-04.
#
# Uso:  ./seguridad/correr-wfuzz.sh [http://localhost:8080]
# La app debe estar corriendo antes de ejecutar esto.

set -u
BASE="${1:-http://localhost:8080}"
SALIDA="seguridad/reportes"
mkdir -p "$SALIDA"

echo "Objetivo: $BASE"

# --- HU-04: fuzzing del parametro nombre en la consulta de disponibilidad ---
# Lo que buscamos: que ningun payload produzca 500 ni exponga SQL.
# --hc 200 oculta las respuestas normales, para que salte lo anormal.
echo "[1/3] Consulta de disponibilidad (GET)"
wfuzz -z file,seguridad/wordlist-nombres.txt \
      --hc 200 \
      -o json -f "$SALIDA/hu04-disponibilidad.json,json" \
      "$BASE/api/naves/disponibilidad?nombre=FUZZ"

# --- HU-03: fuzzing de un campo numerico del formulario ---
echo "[2/3] Registro de nave, campo tonelaje (POST)"
wfuzz -z file,seguridad/wordlist-tonelaje.txt \
      --hc 400,401,409 \
      -H "Content-Type: application/json" \
      -d '{"nombre":"Fuzz Tonelaje","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":FUZZ,"tonelajeNeto":1,"eslora":1,"manga":1,"puntal":1,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":1,"propietarioId":1,"agenteResidenteId":1}' \
      -o json -f "$SALIDA/hu03-tonelaje.json,json" \
      "$BASE/api/naves"

# --- HU-03/HU-04: inyeccion en el campo nombre al guardar ---
echo "[3/3] Registro de nave, campo nombre (POST)"
wfuzz -z file,seguridad/wordlist-nombres.txt \
      --hc 400,401,409 \
      -H "Content-Type: application/json" \
      -d '{"nombre":"FUZZ","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":100,"tonelajeNeto":50,"eslora":10,"manga":5,"puntal":3,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":100,"propietarioId":1,"agenteResidenteId":1}' \
      -o json -f "$SALIDA/hu03-nombre.json,json" \
      "$BASE/api/naves"

echo
echo "Listo. Reportes en $SALIDA/"
echo "Criterio de aceptacion del sprint:"
echo "  - ningun 500"
echo "  - ninguna respuesta que contenga SQL, nombres de tabla o stack traces"
echo "  - los payloads de inyeccion entran como texto literal, no se ejecutan"
