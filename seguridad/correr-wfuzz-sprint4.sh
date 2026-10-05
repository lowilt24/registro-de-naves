#!/usr/bin/env bash
# =====================================================================
# Navesitas — Sprint 4
# Pruebas de seguridad (fuzzing) con Wfuzz, al dia con el estado actual.
#
# Diferencia con correr-wfuzz.sh (Sprint 2):
#   - Desde el Sprint 3 todo /api/** exige sesion (SEC-04). Este script
#     inicia sesion primero y manda la cookie en cada peticion. Sin esto,
#     todo responde 401 y las pruebas no prueban nada.
#   - El cuerpo de /api/naves ya no lleva propietarioId ni agenteResidenteId.
#   - Agrega la prueba pendiente de manipulacion del ID de nave (HU-05,
#     control de acceso por dueno en AccesoNave).
#
# ANTES DE CORRER:
#   1. docker compose up -d
#   2. ./mvnw spring-boot:run   (y abrir http://localhost:8080/login.html)
#
# COMO SE CORRE (desde la raiz del repo):
#   bash seguridad/correr-wfuzz-sprint4.sh
# =====================================================================

set -u
export MSYS_NO_PATHCONV=1

PROYECTO="$(pwd -W 2>/dev/null || pwd)"
IMAGEN="ghcr.io/xmendez/wfuzz"
BASE_HOST="http://localhost:8080"
BASE="http://host.docker.internal:8080"
USUARIO="agente@navesitas.pa"
CLAVE="${NAVESITAS_CLAVE:-Navesitas2026*}"

mkdir -p seguridad/reportes

# --- Iniciar sesion y capturar la cookie (SEC-04) -------------------
CJ="$(mktemp)"
curl -s -o /dev/null -c "$CJ" \
     --data-urlencode "correo=$USUARIO" \
     --data-urlencode "password=$CLAVE" \
     "$BASE_HOST/api/auth/login"
COOKIE="$(awk '/JSESSIONID/{print $7}' "$CJ")"
rm -f "$CJ"
if [ -z "$COOKIE" ]; then
  echo "ERROR: no se pudo iniciar sesion con $USUARIO." >&2
  echo "Revisar que la app este arriba y la clave (variable NAVESITAS_CLAVE)." >&2
  exit 1
fi
CABECERA_COOKIE="Cookie: JSESSIONID=$COOKIE"

echo "======================================================"
echo " Objetivo: $BASE   (sesion: $USUARIO)"
echo " Reportes: seguridad/reportes/"
echo "======================================================"
echo

fuzz() {
  docker run --rm -v "$PROYECTO/seguridad:/work" "$IMAGEN" wfuzz \
    -H "$CABECERA_COOKIE" "$@"
}

# PRUEBA 1 — HU-04: inyeccion en la consulta de disponibilidad (GET)
# Esperado: 200 en todos; el payload se trata como texto. Hallazgo: 500.
echo "[1/4] HU-04 — consulta de disponibilidad de nombre (GET)"
fuzz -c -z file,/work/wordlist-nombres.txt \
     -f /work/reportes/s4-hu04-disponibilidad.json,json \
     "$BASE/api/naves/disponibilidad?nombre=FUZZ"
echo

# PRUEBA 2 — HU-03: payloads invalidos en un campo numerico (POST)
# Esperado: 400 (validacion). Hallazgo: 500. El nombre no llega a crearse.
echo "[2/4] HU-03 — campo tonelaje del formulario de registro (POST)"
fuzz -c -z file,/work/wordlist-tonelaje.txt \
     -H "Content-Type: application/json" \
     -d '{"nombre":"Fuzz Tonelaje S4","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":FUZZ,"tonelajeNeto":1,"eslora":1,"manga":1,"puntal":1,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":1}' \
     -f /work/reportes/s4-hu03-tonelaje.json,json \
     "$BASE/api/naves"
echo

# PRUEBA 3 — HU-03/HU-04: inyeccion en el campo nombre al guardar (POST)
# Esperado: 400 (inyeccion rechazada) o 409 (nombre duplicado). Hallazgo: 500.
echo "[3/4] HU-03 — campo nombre del formulario de registro (POST)"
fuzz -c -z file,/work/wordlist-nombres.txt \
     -H "Content-Type: application/json" \
     -d '{"nombre":"FUZZ","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":100,"tonelajeNeto":50,"eslora":10,"manga":5,"puntal":3,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":100}' \
     -f /work/reportes/s4-hu03-nombre.json,json \
     "$BASE/api/naves"
echo

# PRUEBA 4 — HU-05: manipulacion del ID de nave (GET) [pendiente del Sprint 3]
# AccesoNave decide por dueno. Con la sesion de agente@navesitas.pa:
#   nave 1   -> 200 (es suya)
#   66/67/92 -> 403 (son de otro usuario: louis@123.com)
#   inexistente/limite/no numerico -> 404 o 400
# Esperado: ningun 500 y ninguna nave ajena devuelta con 200.
echo "[4/4] HU-05 — manipulacion del ID de nave (GET)"
fuzz -c -z file,/work/wordlist-ids.txt \
     -f /work/reportes/s4-hu05-id-nave.json,json \
     "$BASE/api/naves/FUZZ"
echo

echo "======================================================"
echo " Listo. Reportes en seguridad/reportes/ (no se versionan)."
echo
echo " COMO SE LEE:"
echo "   200  respuesta normal / recurso propio"
echo "   400  rechazado por validacion     -> correcto"
echo "   401  sin sesion                   -> revisar login del script"
echo "   403  nave de otro usuario          -> control de acceso OK"
echo "   404  no existe                     -> correcto"
echo "   409  nombre duplicado bloqueado    -> correcto"
echo "   500  ERROR DEL SERVIDOR            -> HALLAZGO"
echo
echo " Criterio del sprint: cero 500 y ninguna nave ajena con 200."
echo "======================================================"
