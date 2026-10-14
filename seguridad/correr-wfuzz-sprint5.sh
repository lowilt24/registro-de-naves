#!/usr/bin/env bash
# =====================================================================
# Navesitas — Sprint 5
# Pruebas de seguridad (fuzzing) con Wfuzz para HU-09 (estado del
# tramite) y HU-10 (naves por estado del tramite).
#
# Diferencias con correr-wfuzz-sprint4.sh:
#   - No depende de IDs fijos de la base: crea sus propias naves y un
#     segundo agente (validado con el correo que llega a Mailpit), asi
#     la prueba de nave ajena funciona en cualquier base.
#   - Usa cuatro sesiones: agente@ (dueno), un segundo agente (ajeno),
#     revisor@ (DGMM) y auditor@.
#   - Las pruebas 9 y 10 verifican las reglas que la PO agrego el
#     13-10-2026 (issue #10 y expediente de solo lectura). Hasta que Ismael
#     las implemente, su resultado esperado HOY es distinto del final:
#     el script lo indica en cada una.
#
# ANTES DE CORRER:
#   1. docker compose up -d      (amp-db y amp-correo/Mailpit)
#   2. ./mvnw spring-boot:run
#
# COMO SE CORRE (desde la raiz del repo):
#   bash seguridad/correr-wfuzz-sprint5.sh
#
# Deja los reportes JSON y un resumen en seguridad/reportes/ (no se
# versionan). Crea naves de prueba con nombre "Fuzz S5 ..." en la base.
# =====================================================================

set -u
export MSYS_NO_PATHCONV=1

PROYECTO="$(pwd -W 2>/dev/null || pwd)"
IMAGEN="ghcr.io/xmendez/wfuzz"
BASE_HOST="http://localhost:8080"
BASE="http://host.docker.internal:8080"
MAILPIT="http://localhost:8025"
CLAVE="${NAVESITAS_CLAVE:-Navesitas2026*}"
TS="$(date +%s)"
REP="seguridad/reportes"
RESUMEN="$REP/s5-resumen.txt"

mkdir -p "$REP"
: > "$RESUMEN"

# --- Utilidades -------------------------------------------------------

# Inicia sesion y devuelve el valor de JSESSIONID.
sesion() {
  local cj; cj="$(mktemp)"
  curl -s -o /dev/null -c "$cj" \
       --data-urlencode "correo=$1" --data-urlencode "password=$CLAVE" \
       "$BASE_HOST/api/auth/login"
  awk '/JSESSIONID/{print $7}' "$cj"
  rm -f "$cj"
}

# POST JSON con sesion. Imprime el cuerpo y, en la ultima linea, el codigo.
post_json() {  # cookie ruta cuerpo
  curl -s -w '\n%{http_code}' -H "Cookie: JSESSIONID=$1" \
       -H 'Content-Type: application/json' --data-binary "$3" "$BASE_HOST$2"
}

get() {  # cookie ruta
  curl -s -H "Cookie: JSESSIONID=$1" "$BASE_HOST$2"
}

codigo() { tail -n 1; }
cuerpo() { sed '$d'; }
id_de() { grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }

# Registra una nave y devuelve su id.
nave() {  # cookie nombre
  post_json "$1" /api/naves "{\"nombre\":\"$2\",\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"tonelajeBruto\":3200,\"tonelajeNeto\":1700,\"eslora\":78,\"manga\":13,\"puntal\":6.5,\"anioConstruccion\":2017,\"lugarConstruccion\":\"Astillero Balboa\",\"materialCasco\":\"Acero\",\"tipoPropulsion\":\"Diesel\",\"potenciaKw\":2100}" \
    | cuerpo | id_de
}

# El porcentaje se fija para que quepa un segundo propietario en la prueba 9
# (sin el, el primero toma el 100 % y el segundo choca con la regla de HU-05).
propietario_json() {  # naveId sufijo porcentaje
  echo "{\"nombre\":\"Naviera Fuzz $2 S.A.\",\"tipo\":\"JURIDICA\",\"nacionalidad\":\"Panamena\",\"domicilio\":\"Calle 50\",\"paisConstitucion\":\"Panama\",\"identificacion\":\"RUC-FZ-$2\",\"naveId\":$1,\"porcentaje\":$3}"
}
agente_json() {  # sufijo
  echo "{\"nombre\":\"Bufete Fuzz $1\",\"idoneidad\":\"IDN-FZ-$1\",\"telefono\":\"+507 300-4321\",\"correo\":\"fz$1@bufete.pa\",\"poderNumero\":\"PODER-FZ-$1\",\"poderFecha\":\"2026-02-10\",\"poderLugar\":\"Notaria Quinta\"}"
}

PDF="$(mktemp)"; printf '%%PDF-1.4\n%%fuzz\n1 0 obj << /Type /Catalog >> endobj\n%%%%EOF\n' > "$PDF"
documento() {  # cookie naveId tipo -> codigo
  curl -s -o /dev/null -w '%{http_code}' -H "Cookie: JSESSIONID=$1" \
       -F "tipo=$3" -F "archivo=@$PDF;filename=$3.pdf;type=application/pdf" \
       "$BASE_HOST/api/naves/$2/documentos"
}

# Deja la nave lista para pasar a En revision: propietario, agente
# residente y los 4 documentos obligatorios (decision de la PO).
completar_expediente() {  # cookie naveId sufijo
  post_json "$1" /api/propietarios "$(propietario_json "$2" "$3" 50)" > /dev/null
  post_json "$1" "/api/naves/$2/agente-residente" "$(agente_json "$3")" > /dev/null
  for t in CERTIFICADO_PROPIEDAD PODER_NOTARIAL ITC SMC; do documento "$1" "$2" "$t" > /dev/null; done
}

accion() {  # cookie naveId accion [observacion] -> codigo
  local c="{\"accion\":\"$3\"}"
  [ $# -ge 4 ] && c="{\"accion\":\"$3\",\"observacion\":\"$4\"}"
  post_json "$1" "/api/naves/$2/tramite/estado" "$c" | codigo
}

# Wfuzz en Docker con la sesion indicada.
fuzz() {  # cookie [args de wfuzz...]
  local c="$1"; shift
  docker run --rm -v "$PROYECTO/seguridad:/work" "$IMAGEN" wfuzz \
    -H "Cookie: JSESSIONID=$c" "$@"
}
fuzz_sin_sesion() {
  docker run --rm -v "$PROYECTO/seguridad:/work" "$IMAGEN" wfuzz "$@"
}

# Cuenta los codigos de un reporte JSON de Wfuzz: "200x3 400x5".
codigos() {
  grep -o '"code": [0-9]*' "$1" | awk '{print $2}' | sort | uniq -c \
    | awk '{printf "%sx%s ", $2, $1}'
}
# Pares payload -> codigo de un reporte.
detalle() {
  grep -o '"code": [0-9]*, "payload": "[^"]*"' "$1" \
    | sed -E 's/"code": ([0-9]+), "payload": "(.*)"/  \2 -> \1/'
}

anotar() { echo "$*" | tee -a "$RESUMEN"; }

# --- Preparacion ------------------------------------------------------

echo "======================================================"
echo " Preparacion: sesiones y naves de prueba"
echo "======================================================"

A="$(sesion agente@navesitas.pa)"
D="$(sesion revisor@navesitas.pa)"
U="$(sesion auditor@navesitas.pa)"
if [ -z "$A" ] || [ -z "$D" ] || [ -z "$U" ]; then
  echo "ERROR: no se pudo iniciar sesion con los usuarios de demo." >&2
  echo "Revisar que la app este arriba y la clave (NAVESITAS_CLAVE)." >&2
  exit 1
fi

# Segundo agente, nuevo en cada corrida (el "ajeno").
OTRO="fuzz.s5.$TS@navesitas.pa"
curl -s -o /dev/null -H 'Content-Type: application/json' \
     -d "{\"correo\":\"$OTRO\",\"password\":\"$CLAVE\",\"nombreCompleto\":\"Agente Fuzz S5\",\"rol\":\"AGENTE_NAVIERO\"}" \
     "$BASE_HOST/api/auth/registro"
TOKEN=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  MID="$(curl -s --get --data-urlencode "query=to:$OTRO" "$MAILPIT/api/v1/search" \
         | grep -o '"ID":"[^"]*"' | head -1 | cut -d'"' -f4)"
  if [ -n "$MID" ]; then
    TOKEN="$(curl -s "$MAILPIT/api/v1/message/$MID" | grep -o 'validar?token=[A-Za-z0-9_-]*' | head -1)"
  fi
  [ -n "$TOKEN" ] && break
  sleep 1
done
if [ -z "$TOKEN" ]; then
  echo "ERROR: no llego a Mailpit el correo de validacion de $OTRO." >&2
  exit 1
fi
curl -s -o /dev/null "$BASE_HOST/api/auth/$TOKEN"
O="$(sesion "$OTRO")"
[ -z "$O" ] && { echo "ERROR: no se pudo iniciar sesion con $OTRO." >&2; exit 1; }

N_PROPIA="$(nave "$A" "Fuzz S5 Propia $TS")"
N_DESISTIR="$(nave "$A" "Fuzz S5 Desistir $TS")"
N_AJENA="$(nave "$O" "Fuzz S5 Ajena $TS")"

# Nave en estado final (Desistida), con propietario y agente cargados
# antes de cerrarla: sirve para las pruebas 5, 6a y 10.
N_FINAL="$(nave "$A" "Fuzz S5 Final $TS")"
completar_expediente "$A" "$N_FINAL" "F$TS"
accion "$A" "$N_FINAL" DESISTIR > /dev/null

# Nave en En revision, para guardar observaciones de verdad (prueba 6b).
N_REVISION="$(nave "$A" "Fuzz S5 Revision $TS")"
completar_expediente "$A" "$N_REVISION" "R$TS"
accion "$D" "$N_REVISION" ADMITIR > /dev/null

for v in N_PROPIA N_DESISTIR N_AJENA N_FINAL N_REVISION; do
  if [ -z "${!v}" ]; then echo "ERROR: no se pudo preparar $v." >&2; exit 1; fi
done

# Diccionarios de IDs generados en cada corrida (no se versionan).
printf '%s\n' "$N_PROPIA" "$N_AJENA" 0 -1 999999999 2147483648 9223372036854775808 1.5 abc "%27" "1%20OR%201=1" > "$REP/s5-ids-lectura.txt"
printf '%s\n' "$N_DESISTIR" "$N_AJENA" 0 -1 999999999 abc > "$REP/s5-ids-escritura.txt"
printf '%s\n' "api/naves/$N_PROPIA/tramite" "api/naves/$N_PROPIA/tramite/historial" "api/naves/conteo-por-estado" "api/naves?estado=EN_REVISION" > "$REP/s5-rutas.txt"
printf '%s\n' "$A" "$D" "$U" > "$REP/s5-sesiones.txt"

anotar "Corrida Sprint 5 — $(date '+%d-%m-%Y %H:%M')"
anotar "Naves: propia=$N_PROPIA desistir=$N_DESISTIR ajena=$N_AJENA (de $OTRO) final=$N_FINAL revision=$N_REVISION"
anotar ""

# --- Pruebas ----------------------------------------------------------

# PRUEBA 1 — HU-10: filtro por estado del tramite (GET)
# Esperado: 200 para los 6 estados validos, 400 para todo lo demas. Hallazgo: 500.
echo "[1/10] HU-10 — filtro ?estado= (GET)"
fuzz "$A" -c -z file,/work/wordlist-estados.txt,urlencode \
     -f /work/reportes/s5-hu10-estado.json,json \
     "$BASE/api/naves?estado=FUZZ"
echo

# PRUEBA 2 — HU-09: manipulacion del ID de nave al leer el tramite (GET)
# Con la sesion de agente@: propia -> 200; ajena -> 403; inexistente -> 404;
# no numerico -> 400. Hallazgo: 500 o la nave ajena con 200.
echo "[2/10] HU-09 — ID de nave en /tramite (GET)"
fuzz "$A" -c -z file,/work/reportes/s5-ids-lectura.txt \
     -f /work/reportes/s5-hu09-id-tramite.json,json \
     "$BASE/api/naves/FUZZ/tramite"
echo

# PRUEBA 3 — HU-09: manipulacion del ID de nave en el historial (GET)
echo "[3/10] HU-09 — ID de nave en /tramite/historial (GET)"
fuzz "$A" -c -z file,/work/reportes/s5-ids-lectura.txt \
     -f /work/reportes/s5-hu09-id-historial.json,json \
     "$BASE/api/naves/FUZZ/tramite/historial"
echo

# PRUEBA 4 — HU-09: manipulacion del ID de nave al CAMBIAR el estado (POST)
# agente@ pide DESISTIR: su nave -> 200; la ajena -> 403 y debe seguir
# en Solicitud presentada (se verifica al final). Hallazgo: 200 en la ajena.
echo "[4/10] HU-09 — ID de nave en /tramite/estado (POST, desistir)"
fuzz "$A" -c -z file,/work/reportes/s5-ids-escritura.txt \
     -H "Content-Type: application/json" -d '{"accion":"DESISTIR"}' \
     -f /work/reportes/s5-hu09-id-estado.json,json \
     "$BASE/api/naves/FUZZ/tramite/estado"
echo

# PRUEBA 5 — HU-09: valores del campo accion (POST)
# DGMM sobre una nave Desistida (final): ninguna accion puede cambiarla.
# Esperado: 400 (invalida o del sistema), 403 (accion del agente) o 409
# (valida pero el tramite termino). Hallazgo: 500 o 200.
echo "[5/10] HU-09 — campo accion (POST)"
fuzz "$D" -c -z file,/work/wordlist-acciones.txt \
     -H "Content-Type: application/json" -d '{"accion":"FUZZ"}' \
     -f /work/reportes/s5-hu09-accion.json,json \
     "$BASE/api/naves/$N_FINAL/tramite/estado"
echo

# PRUEBA 6a — HU-09: lectura y validacion del campo observacion (POST)
# DGMM observa una nave Desistida: el cuerpo se lee y se valida, y la
# transicion se rechaza. Esperado: 409, o 400 (formato o mas de 1000
# caracteres). Hallazgo: 500.
echo "[6/10] HU-09 — campo observacion, lectura y validacion (POST)"
fuzz "$D" -c -z file,/work/wordlist-observaciones.txt \
     -H "Content-Type: application/json" -d '{"accion":"OBSERVAR","observacion":"FUZZ"}' \
     -f /work/reportes/s5-hu09-observacion.json,json \
     "$BASE/api/naves/$N_FINAL/tramite/estado"
echo

# PRUEBA 6b — HU-09: el campo observacion llega a la base (curl)
# Wfuzz no puede devolver el tramite a En revision entre peticiones, asi
# que cada payload se guarda con un ciclo observar -> subsanar sobre la
# misma nave. Esperado: 200 o 400 al observar, y el historial se puede
# leer despues (200). Hallazgo: 500 al guardar o al leer.
echo "[7/10] HU-09 — campo observacion, guardado en la base (curl)"
SALIDA6B="$REP/s5-hu09-observacion-guardado.txt"
: > "$SALIDA6B"
while IFS= read -r p || [ -n "$p" ]; do
  c="$(accion "$D" "$N_REVISION" OBSERVAR "$p")"
  printf '  %s -> %s\n' "$(printf '%s' "$p" | cut -c1-50)" "$c" >> "$SALIDA6B"
  [ "$c" = "200" ] && accion "$A" "$N_REVISION" SUBSANAR > /dev/null
done < seguridad/wordlist-observaciones.txt
HIST6B="$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: JSESSIONID=$A" "$BASE_HOST/api/naves/$N_REVISION/tramite/historial")"
cat "$SALIDA6B"
echo "  historial despues de guardar -> $HIST6B"
echo

# PRUEBA 7 — Sin sesion (GET y POST)
# Esperado: 401 en todo. Hallazgo: 200 o 500.
echo "[8/10] HU-09/HU-10 — endpoints nuevos sin sesion"
fuzz_sin_sesion -c -z file,/work/reportes/s5-rutas.txt \
     -f /work/reportes/s5-sin-sesion.json,json \
     "$BASE/FUZZ"
fuzz_sin_sesion -c -z list,"$N_PROPIA" \
     -H "Content-Type: application/json" -d '{"accion":"DESISTIR"}' \
     -f /work/reportes/s5-sin-sesion-post.json,json \
     "$BASE/api/naves/FUZZ/tramite/estado"
echo

# PRUEBA 8 — Rol al registrar una nave (POST) [issue #10]
# Sesiones en orden: agente@, revisor@ (DGMM), auditor@.
# Esperado FINAL: 201, 403, 403. HOY (sin la regla): 201, 201, 201.
echo "[9/10] HU-03 — registro de nave por rol (POST) [issue #10]"
fuzz_sin_sesion -c -z file,/work/reportes/s5-sesiones.txt -z list,agente-dgmm-auditor -m zip \
     -H "Cookie: JSESSIONID=FUZZ" -H "Content-Type: application/json" \
     -d "{\"nombre\":\"Fuzz S5 Rol FUZ2Z $TS\",\"tipo\":\"CARGA\",\"servicio\":\"CABOTAJE\",\"tonelajeBruto\":100,\"tonelajeNeto\":50,\"eslora\":10,\"manga\":5,\"puntal\":3,\"anioConstruccion\":2020,\"lugarConstruccion\":\"x\",\"materialCasco\":\"x\",\"tipoPropulsion\":\"x\",\"potenciaKw\":100}" \
     -f /work/reportes/s5-hu03-rol.json,json \
     "$BASE/api/naves"
echo

# PRUEBA 9 — Escrituras en un expediente cerrado (curl)
# Nave Desistida. Esperado FINAL: 409 en las tres. HOY: 201.
echo "[10/10] HU-09 — escrituras con el tramite Desistido"
E_DOC="$(documento "$A" "$N_FINAL" ITC)"
E_PROP="$(post_json "$A" /api/propietarios "$(propietario_json "$N_FINAL" "X$TS" 10)" | codigo)"
E_AGEN="$(post_json "$A" "/api/naves/$N_FINAL/agente-residente" "$(agente_json "X$TS")" | codigo)"
echo "  documento -> $E_DOC   propietario -> $E_PROP   agente residente -> $E_AGEN"
echo

# Verificacion de la prueba 4: la nave ajena no cambio.
ESTADO_AJENA="$(get "$O" "/api/naves/$N_AJENA/tramite" | grep -o '"estado":"[A-Z_]*"' | cut -d'"' -f4)"

# --- Resumen ----------------------------------------------------------

total500=0
for f in s5-hu10-estado s5-hu09-id-tramite s5-hu09-id-historial s5-hu09-id-estado \
         s5-hu09-accion s5-hu09-observacion s5-sin-sesion s5-sin-sesion-post s5-hu03-rol; do
  n="$(grep -o '"code": 500' "$REP/$f.json" 2>/dev/null | wc -l | tr -d ' ')"
  total500=$((total500 + n))
done
n6b="$(grep -c -- '-> 500' "$SALIDA6B")"
total500=$((total500 + n6b))
[ "$HIST6B" = "500" ] && total500=$((total500 + 1))

anotar "1. HU-10 filtro ?estado=          : $(codigos "$REP/s5-hu10-estado.json")"
anotar "2. HU-09 ID en /tramite           : $(codigos "$REP/s5-hu09-id-tramite.json")"
detalle "$REP/s5-hu09-id-tramite.json" | tee -a "$RESUMEN"
anotar "3. HU-09 ID en /historial         : $(codigos "$REP/s5-hu09-id-historial.json")"
anotar "4. HU-09 ID en /estado (desistir) : $(codigos "$REP/s5-hu09-id-estado.json")"
detalle "$REP/s5-hu09-id-estado.json" | tee -a "$RESUMEN"
anotar "   nave ajena despues             : $ESTADO_AJENA (debe ser SOLICITUD_PRESENTADA)"
anotar "5. HU-09 campo accion             : $(codigos "$REP/s5-hu09-accion.json")"
anotar "6a. HU-09 observacion (validacion): $(codigos "$REP/s5-hu09-observacion.json")"
anotar "6b. HU-09 observacion (guardado)  : $(awk '{print $NF}' "$SALIDA6B" | sort | uniq -c | awk '{printf "%sx%s ", $2, $1}')historial=$HIST6B"
anotar "7. Sin sesion                     : $(codigos "$REP/s5-sin-sesion.json")$(codigos "$REP/s5-sin-sesion-post.json")"
# Sin el ID de sesion: solo el rol y el codigo.
anotar "8. Registro por rol [#10]         : $(detalle "$REP/s5-hu03-rol.json" | sed -E 's/[0-9A-F]{32} - //' | tr '\n' ' ')(final: agente 201, dgmm 403, auditor 403)"
anotar "9. Expediente cerrado             : documento=$E_DOC propietario=$E_PROP agente=$E_AGEN (final: 409)"
anotar ""
anotar "Respuestas 500: $total500"

rm -f "$PDF"

echo "======================================================"
echo " Listo. Reportes y resumen en seguridad/reportes/ (no se versionan)."
echo
echo " Criterio del sprint: cero 500, ninguna nave ajena con 200 en las"
echo " pruebas 2-4, y la nave ajena sigue en SOLICITUD_PRESENTADA."
echo " Las pruebas 8 y 9 pasan cuando muestren 201/403/403 y 409."
echo "======================================================"
