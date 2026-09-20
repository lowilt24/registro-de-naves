#!/usr/bin/env bash
# =====================================================================
# Navesitas — Sprint 2
# Pruebas de seguridad (fuzzing) sobre los endpoints de HU-03 y HU-04.
#
# No hace falta instalar Wfuzz: corre dentro de un contenedor Docker.
#
# ANTES DE CORRER ESTO:
#   1. La base tiene que estar arriba:   docker compose up -d
#   2. La aplicacion tiene que estar corriendo: ./mvnw spring-boot:run
#   3. Comprobar en el navegador que abre http://localhost:8080/naves.html
#
# COMO SE CORRE (en Windows, desde Git Bash, parado en la raiz del repo):
#   bash seguridad/correr-wfuzz.sh
# =====================================================================

set -u

# Git Bash en Windows reescribe las rutas que empiezan con "/" y rompe
# los volumenes de Docker. Esto lo desactiva.
export MSYS_NO_PATHCONV=1

# pwd -W existe en Git Bash y devuelve C:/Users/... (lo que Docker entiende).
# En Linux o Mac no existe, y ahi alcanza con pwd normal.
PROYECTO="$(pwd -W 2>/dev/null || pwd)"

IMAGEN="ghcr.io/xmendez/wfuzz"

# Wfuzz corre adentro de un contenedor, asi que "localhost" seria el propio
# contenedor. host.docker.internal apunta a tu computadora.
BASE="http://host.docker.internal:8080"

mkdir -p seguridad/reportes

fuzz() {
  docker run --rm -v "$PROYECTO/seguridad:/work" "$IMAGEN" wfuzz "$@"
}

echo "======================================================"
echo " Objetivo: $BASE"
echo " Reportes: seguridad/reportes/"
echo "======================================================"
echo

# ---------------------------------------------------------------------
# PRUEBA 1 — HU-04: inyeccion en la consulta de disponibilidad (GET)
#
# Que buscamos: que ningun payload produzca un error 500 ni devuelva
# mensajes con SQL, nombres de tabla o stack traces.
# Resultado esperado: 200 en todos los casos, tratando el payload como
# texto comun.
# ---------------------------------------------------------------------
echo "[1/3] HU-04 — consulta de disponibilidad de nombre (GET)"
fuzz -c \
     -z file,/work/wordlist-nombres.txt \
     -f /work/reportes/hu04-disponibilidad.json,json \
     "$BASE/api/naves/disponibilidad?nombre=FUZZ"
echo

# ---------------------------------------------------------------------
# PRUEBA 2 — HU-03: payloads invalidos en un campo numerico (POST)
#
# Que buscamos: que el backend rechace con 400 y no reviente con 500.
# Nota: ningun payload de la lista deberia pasar la validacion, asi que
# el nombre "Fuzz Tonelaje" nunca llega a registrarse.
# ---------------------------------------------------------------------
echo "[2/3] HU-03 — campo tonelaje del formulario de registro (POST)"
fuzz -c \
     -z file,/work/wordlist-tonelaje.txt \
     -H "Content-Type: application/json" \
     -d '{"nombre":"Fuzz Tonelaje","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":FUZZ,"tonelajeNeto":1,"eslora":1,"manga":1,"puntal":1,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":1,"propietarioId":1,"agenteResidenteId":1}' \
     -f /work/reportes/hu03-tonelaje.json,json \
     "$BASE/api/naves"
echo

# ---------------------------------------------------------------------
# PRUEBA 3 — HU-03/HU-04: inyeccion en el campo nombre al guardar (POST)
#
# Que buscamos: dos cosas a la vez.
#  a) que los payloads de inyeccion sean rechazados (400) y no ejecutados
#  b) que "Estrella del Istmo" y sus variantes den 409, o sea que el
#     bloqueo de nombre duplicado funciona aunque se salte la pantalla
# ---------------------------------------------------------------------
echo "[3/3] HU-03 — campo nombre del formulario de registro (POST)"
fuzz -c \
     -z file,/work/wordlist-nombres.txt \
     -H "Content-Type: application/json" \
     -d '{"nombre":"FUZZ","tipo":"CARGA","servicio":"CABOTAJE","tonelajeBruto":100,"tonelajeNeto":50,"eslora":10,"manga":5,"puntal":3,"anioConstruccion":2020,"lugarConstruccion":"x","materialCasco":"x","tipoPropulsion":"x","potenciaKw":100,"propietarioId":1,"agenteResidenteId":1}' \
     -f /work/reportes/hu03-nombre.json,json \
     "$BASE/api/naves"
echo

echo "======================================================"
echo " Listo. Los reportes quedaron en seguridad/reportes/"
echo
echo " COMO SE LEE EL RESULTADO:"
echo "   200  respuesta normal, el payload se trato como texto"
echo "   400  rechazado por validacion  -> correcto"
echo "   409  nombre duplicado bloqueado -> correcto"
echo "   500  ERROR DEL SERVIDOR         -> hallazgo, hay que revisarlo"
echo
echo " Criterio de aprobacion del sprint: cero respuestas 500."
echo "======================================================"