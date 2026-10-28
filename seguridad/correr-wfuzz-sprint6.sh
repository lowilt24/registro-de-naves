#!/usr/bin/env bash
# =====================================================================
# Navesitas — Sprint 6
# Pruebas de seguridad (fuzzing) con Wfuzz para HU-11 (bandeja de la
# DGMM), HU-12 (revision de documentos) y SEC-05 (CSRF).
#
# Diferencias con correr-wfuzz-sprint5.sh:
#   - SEC-05: todo POST lleva el token CSRF (cookie XSRF-TOKEN y cabecera
#     X-XSRF-TOKEN), igual que comun.js. El login rota el token, asi que
#     cada sesion guarda el suyo. El script del Sprint 5 ya no sirve:
#     sin token, todos sus POST dan 403.
#   - Pruebas nuevas de CSRF (11 y 12), incluida la duda de si un POST
#     anonimo sin token da 401 o 403.
#   - Comprobacion de SEC-03 (13): ninguna contrasena en los archivos
#     versionados.
#
# ANTES DE CORRER:
#   1. docker compose up -d      (amp-db y amp-correo/Mailpit)
#   2. ./mvnw spring-boot:run    (con .env, ver .env.example)
#
# COMO SE CORRE (desde la raiz del repo):
#   bash seguridad/correr-wfuzz-sprint6.sh
#
# Deja los reportes JSON y un resumen en seguridad/reportes/ (no se
# versionan). Crea naves de prueba con nombre "Fuzz S6 ..." en la base.
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
RESUMEN="$REP/s6-resumen.txt"
JARS="$(mktemp -d)"   # cookies de las sesiones; se borran al final

mkdir -p "$REP"
: > "$RESUMEN"

# --- Utilidades -------------------------------------------------------

# Cada sesion es un frasco de cookies de curl: $JARS/<nombre>.
# sid y tok leen JSESSIONID y XSRF-TOKEN de ese frasco.
sid() { awk '$6=="JSESSIONID"{print $7}' "$JARS/$1" 2>/dev/null; }
tok() { awk '$6=="XSRF-TOKEN"{print $7}' "$JARS/$1" 2>/dev/null; }

# Pide la cookie XSRF-TOKEN de una sesion anonima.
anonima() {  # nombre
  rm -f "$JARS/$1"
  curl -s -o /dev/null -c "$JARS/$1" "$BASE_HOST/login.html"
}

# Inicia sesion con el token CSRF. El login borra la cookie XSRF-TOKEN
# (rota el token) y el nuevo llega en la peticion siguiente, igual que en
# el navegador: por eso se pide /api/auth/sesion despues.
sesion() {  # nombre correo
  anonima "$1"
  curl -s -o /dev/null -b "$JARS/$1" -c "$JARS/$1" -H "X-XSRF-TOKEN: $(tok "$1")" \
       --data-urlencode "correo=$2" --data-urlencode "password=$CLAVE" \
       "$BASE_HOST/api/auth/login"
  curl -s -o /dev/null -b "$JARS/$1" -c "$JARS/$1" "$BASE_HOST/api/auth/sesion"
  sid "$1"
}

# POST JSON con sesion y token. Imprime el cuerpo y, en la ultima linea, el codigo.
post_json() {  # nombre ruta cuerpo
  curl -s -w '\n%{http_code}' -b "$JARS/$1" -H "X-XSRF-TOKEN: $(tok "$1")" \
       -H 'Content-Type: application/json' --data-binary "$3" "$BASE_HOST$2"
}

get() {  # nombre ruta
  curl -s -b "$JARS/$1" "$BASE_HOST$2"
}
get_codigo() {  # nombre ruta
  curl -s -o /dev/null -w '%{http_code}' -b "$JARS/$1" "$BASE_HOST$2"
}

codigo() { tail -n 1; }
cuerpo() { sed '$d'; }
id_de() { grep -o '"id":[0-9]*' | head -1 | cut -d: -f2; }

# Registra una nave y devuelve su id.
nave() {  # nombre-sesion nombre-nave
  post_json "$1" /api/naves "{\"nombre\":\"$2\",\"tipo\":\"CARGA\",\"servicio\":\"INTERNACIONAL\",\"tonelajeBruto\":3200,\"tonelajeNeto\":1700,\"eslora\":78,\"manga\":13,\"puntal\":6.5,\"anioConstruccion\":2017,\"lugarConstruccion\":\"Astillero Balboa\",\"materialCasco\":\"Acero\",\"tipoPropulsion\":\"Diesel\",\"potenciaKw\":2100}" \
    | cuerpo | id_de
}

propietario_json() {  # naveId sufijo
  echo "{\"nombre\":\"Naviera Fuzz $2 S.A.\",\"tipo\":\"JURIDICA\",\"nacionalidad\":\"Panamena\",\"domicilio\":\"Calle 50\",\"paisConstitucion\":\"Panama\",\"identificacion\":\"RUC-FZ6-$2\",\"naveId\":$1,\"porcentaje\":100}"
}
agente_json() {  # sufijo
  echo "{\"nombre\":\"Bufete Fuzz $1\",\"idoneidad\":\"IDN-FZ6-$1\",\"telefono\":\"+507 300-4321\",\"correo\":\"fz6$1@bufete.pa\",\"poderNumero\":\"PODER-FZ6-$1\",\"poderFecha\":\"2026-02-10\",\"poderLugar\":\"Notaria Quinta\"}"
}

PDF="$(mktemp)"; printf '%%PDF-1.4\n%%fuzz\n1 0 obj << /Type /Catalog >> endobj\n%%%%EOF\n' > "$PDF"
# Carga un documento. Imprime el cuerpo y, en la ultima linea, el codigo.
documento() {  # nombre-sesion naveId tipo
  curl -s -w '\n%{http_code}' -b "$JARS/$1" -H "X-XSRF-TOKEN: $(tok "$1")" \
       -F "tipo=$3" -F "archivo=@$PDF;filename=$3.pdf;type=application/pdf" \
       "$BASE_HOST/api/naves/$2/documentos"
}

completar_expediente() {  # nombre-sesion naveId sufijo
  post_json "$1" /api/propietarios "$(propietario_json "$2" "$3")" > /dev/null
  post_json "$1" "/api/naves/$2/agente-residente" "$(agente_json "$3")" > /dev/null
  for t in CERTIFICADO_PROPIEDAD PODER_NOTARIAL ITC SMC; do documento "$1" "$2" "$t" > /dev/null; done
}

accion() {  # nombre-sesion naveId accion -> codigo
  post_json "$1" "/api/naves/$2/tramite/estado" "{\"accion\":\"$3\"}" | codigo
}

# Id del documento vigente de un tipo, y su estado.
doc_id() {  # nombre-sesion naveId tipo
  get "$1" "/api/naves/$2/documentos?tipo=$3" | id_de
}
doc_estado() {  # nombre-sesion naveId tipo
  get "$1" "/api/naves/$2/documentos?tipo=$3" | grep -o '"estado":"[A-Z]*"' | head -1 | cut -d'"' -f4
}

revisar() {  # nombre-sesion naveId docId cuerpo -> codigo
  post_json "$1" "/api/naves/$2/documentos/$3/revision" "$4" | codigo
}

# Wfuzz en Docker con la sesion y el token CSRF indicados.
fuzz() {  # nombre-sesion [args de wfuzz...]
  local s="$1"; shift
  docker run --rm -v "$PROYECTO/seguridad:/work" "$IMAGEN" wfuzz \
    -H "Cookie: JSESSIONID=$(sid "$s"); XSRF-TOKEN=$(tok "$s")" \
    -H "X-XSRF-TOKEN: $(tok "$s")" "$@"
}
# Wfuzz sin cabeceras puestas por el script (las pone la prueba).
fuzz_crudo() {
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

sesion A agente@navesitas.pa  > /dev/null
sesion D revisor@navesitas.pa > /dev/null
sesion U auditor@navesitas.pa > /dev/null
for s in A D U; do
  if [ -z "$(sid $s)" ] || [ -z "$(tok $s)" ]; then
    echo "ERROR: no se pudo iniciar sesion ($s) o falta el token CSRF." >&2
    echo "Revisar que la app este arriba y la clave (NAVESITAS_CLAVE)." >&2
    exit 1
  fi
done

# Segundo agente, nuevo en cada corrida (el "ajeno"). El registro es
# publico, pero desde SEC-05 tambien lleva token CSRF.
OTRO="fuzz.s6.$TS@navesitas.pa"
anonima R
curl -s -o /dev/null -b "$JARS/R" -H "X-XSRF-TOKEN: $(tok R)" -H 'Content-Type: application/json' \
     -d "{\"correo\":\"$OTRO\",\"password\":\"$CLAVE\",\"nombreCompleto\":\"Agente Fuzz S6\",\"rol\":\"AGENTE_NAVIERO\"}" \
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
sesion O "$OTRO" > /dev/null
[ -z "$(sid O)" ] && { echo "ERROR: no se pudo iniciar sesion con $OTRO." >&2; exit 1; }

# N_REV: del agente, En revision, con los 4 documentos en PENDIENTE.
N_REV="$(nave A "Fuzz S6 Revision $TS")"
completar_expediente A "$N_REV" "R$TS"
accion D "$N_REV" ADMITIR > /dev/null

# N_SP: del agente, en Solicitud presentada (no se revisa: 409).
N_SP="$(nave A "Fuzz S6 Presentada $TS")"
completar_expediente A "$N_SP" "P$TS"

# N_AJENA: del segundo agente, En revision.
N_AJENA="$(nave O "Fuzz S6 Ajena $TS")"
completar_expediente O "$N_AJENA" "J$TS"
accion D "$N_AJENA" ADMITIR > /dev/null

for v in N_REV N_SP N_AJENA; do
  if [ -z "${!v}" ]; then echo "ERROR: no se pudo preparar $v." >&2; exit 1; fi
done

D_CERT="$(doc_id A "$N_REV" CERTIFICADO_PROPIEDAD)"
D_PODER="$(doc_id A "$N_REV" PODER_NOTARIAL)"
D_ITC="$(doc_id A "$N_REV" ITC)"
D_SMC="$(doc_id A "$N_REV" SMC)"
D_SP="$(doc_id A "$N_SP" ITC)"
D_AJENA="$(doc_id O "$N_AJENA" ITC)"
for v in D_CERT D_PODER D_ITC D_SMC D_SP D_AJENA; do
  if [ -z "${!v}" ]; then echo "ERROR: no se pudo leer el documento $v." >&2; exit 1; fi
done

# Diccionarios generados en cada corrida (no se versionan).
printf '%s\n' "$D_CERT" "$D_AJENA" "$D_SP" 0 -1 999999999 2147483648 9223372036854775808 1.5 abc "%27" "1%20OR%201=1" > "$REP/s6-ids-documento.txt"
printf '%s\n' "$N_REV" "$N_AJENA" "$N_SP" 0 -1 999999999 abc > "$REP/s6-ids-nave.txt"
printf '%s\n' "api/tramites" "api/tramites?estado=EN_REVISION" "api/tramites/conteo-por-estado" "api/naves/$N_REV/documentos" > "$REP/s6-rutas.txt"
printf '%s\n' "$(tok A)" "" "abc" "$(tok A)x" "$(tok D)" > "$REP/s6-tokens.txt"

anotar "Corrida Sprint 6 — $(date '+%d-%m-%Y %H:%M') — commit $(git rev-parse --short HEAD 2>/dev/null)"
anotar "Naves: revision=$N_REV presentada=$N_SP ajena=$N_AJENA (de $OTRO)"
anotar "Documentos de $N_REV: cert=$D_CERT poder=$D_PODER itc=$D_ITC smc=$D_SMC; itc de $N_SP=$D_SP; itc de la ajena=$D_AJENA"
anotar ""

# --- Pruebas ----------------------------------------------------------

# PRUEBA 1 — HU-11: filtro por estado de la bandeja (GET)
# DGMM. Esperado: 200 para los estados validos, 400 para lo demas. Hallazgo: 500.
echo "[1/13] HU-11 — filtro ?estado= de la bandeja (GET)"
fuzz D -c -z file,/work/wordlist-estados.txt,urlencode \
     -f /work/reportes/s6-hu11-estado.json,json \
     "$BASE/api/tramites?estado=FUZZ"
echo

# PRUEBA 2 — HU-11: quien ve la bandeja (curl)
# Esperado: agente 403, agente ajeno 403, DGMM 200, auditor 200, sin sesion 401.
echo "[2/13] HU-11 — acceso a la bandeja por rol"
anonima X
P2=""
for s in A O D U X; do
  P2="$P2$s:$(get_codigo $s /api/tramites)/$(get_codigo $s /api/tramites/conteo-por-estado) "
done
echo "  $P2"
echo

# PRUEBA 3 — HU-12: ID del documento en la ruta de revision (POST)
# DGMM observa en la nave N_REV. Solo D_CERT es de esa nave -> 200.
# Documentos de otras naves -> 404; no numericos -> 400. Hallazgo: 200 o 500.
echo "[3/13] HU-12 — ID de documento en /revision (POST)"
fuzz D -c -z file,/work/reportes/s6-ids-documento.txt \
     -H "Content-Type: application/json" -d '{"decision":"OBSERVAR","observacion":"Fuzz S6"}' \
     -f /work/reportes/s6-hu12-id-documento.json,json \
     "$BASE/api/naves/$N_REV/documentos/FUZZ/revision"
echo

# PRUEBA 4 — HU-12: ID de la nave en la ruta de revision (POST)
# DGMM aprueba D_PODER cambiando la nave. Solo N_REV -> 200; las otras -> 404
# (el documento no es de esa nave); no numericos -> 400.
echo "[4/13] HU-12 — ID de nave en /revision (POST)"
fuzz D -c -z file,/work/reportes/s6-ids-nave.txt \
     -H "Content-Type: application/json" -d '{"decision":"APROBAR"}' \
     -f /work/reportes/s6-hu12-id-nave.json,json \
     "$BASE/api/naves/FUZZ/documentos/$D_PODER/revision"
echo

# PRUEBA 5 — HU-12: quien revisa (curl)
# APROBAR D_ITC. Esperado: agente dueno 403, agente ajeno 403, auditor 403,
# sin sesion 401. El ITC debe seguir en PENDIENTE. Despues, la DGMM sobre
# N_SP (Solicitud presentada) -> 409.
echo "[5/13] HU-12 — revision por rol y por estado del tramite"
P5=""
for s in A O U X; do
  P5="$P5$s:$(revisar $s "$N_REV" "$D_ITC" '{"decision":"APROBAR"}') "
done
ITC_TRAS_ROLES="$(doc_estado D "$N_REV" ITC)"
P5_SP="$(revisar D "$N_SP" "$D_SP" '{"decision":"APROBAR"}')"
echo "  $P5 itc=$ITC_TRAS_ROLES  dgmm-en-solicitud-presentada:$P5_SP"
echo

# PRUEBA 6 — HU-12: valores del campo decision (POST)
# DGMM sobre D_ITC (PENDIENTE). Las variantes de APROBAR que el servidor
# acepta (minusculas, espacios) aprueban una sola vez: 200 la primera y
# 409 las demas. Lo invalido -> 400. Hallazgo: 500.
echo "[6/13] HU-12 — campo decision (POST)"
fuzz D -c -z file,/work/wordlist-decisiones.txt \
     -H "Content-Type: application/json" -d '{"decision":"FUZZ"}' \
     -f /work/reportes/s6-hu12-decision.json,json \
     "$BASE/api/naves/$N_REV/documentos/$D_ITC/revision"
echo

# PRUEBA 7a — HU-12: campo observacion, lectura y validacion (POST)
# DGMM observa D_SMC. La primera observacion valida gana (200) y las demas
# chocan con 409; el JSON con caracter nulo y lo que pasa de 1000
# caracteres -> 400. Hallazgo: 500.
echo "[7/13] HU-12 — campo observacion, lectura y validacion (POST)"
fuzz D -c -z file,/work/wordlist-observaciones.txt \
     -H "Content-Type: application/json" -d '{"decision":"OBSERVAR","observacion":"FUZZ"}' \
     -f /work/reportes/s6-hu12-observacion.json,json \
     "$BASE/api/naves/$N_REV/documentos/$D_SMC/revision"
echo

# PRUEBA 7b — HU-12: la observacion llega a la base y se lee (curl)
# Por cada payload, el agente carga un SMC nuevo (vuelve a PENDIENTE) y la
# DGMM lo observa. Esperado: 200 o 400. Al final, el agente y el auditor
# leen el historial de documentos (200). Hallazgo: 500 al guardar o al leer.
echo "[8/13] HU-12 — campo observacion, guardado en la base (curl)"
SALIDA7B="$REP/s6-hu12-observacion-guardado.txt"
: > "$SALIDA7B"
while IFS= read -r p || [ -n "$p" ]; do
  nuevo="$(documento A "$N_REV" SMC | cuerpo | id_de)"
  c="$(revisar D "$N_REV" "$nuevo" "{\"decision\":\"OBSERVAR\",\"observacion\":\"$p\"}")"
  printf '  %s -> %s\n' "$(printf '%s' "$p" | cut -c1-50)" "$c" >> "$SALIDA7B"
done < seguridad/wordlist-observaciones.txt
LEE_AGENTE="$(get_codigo A "/api/naves/$N_REV/documentos?historial=true")"
LEE_AUDITOR="$(get_codigo U "/api/naves/$N_REV/documentos?historial=true")"
cat "$SALIDA7B"
echo "  lectura del historial -> agente $LEE_AGENTE, auditor $LEE_AUDITOR"
echo

# PRUEBA 8 — HU-12 (D4): aprobar el tramite con documentos sin aprobar (curl)
# El SMC esta OBSERVADO. Esperado: 409 con "faltantes".
echo "[9/13] HU-12 — aprobar el tramite con un documento observado (D4)"
R8="$(post_json D "/api/naves/$N_REV/tramite/estado" '{"accion":"APROBAR"}')"
P8="$(printf '%s' "$R8" | codigo)"
F8="$(printf '%s' "$R8" | cuerpo | grep -o '"faltantes":\[[^]]*\]')"
echo "  $P8 $F8"
echo

# PRUEBA 9 — Sin sesion (GET y POST)
# Esperado: 401 en todo (el POST lleva token anonimo valido, asi que lo que
# responde es la falta de sesion).
echo "[10/13] HU-11/HU-12 — endpoints nuevos sin sesion"
fuzz_crudo -c -z file,/work/reportes/s6-rutas.txt \
     -f /work/reportes/s6-sin-sesion.json,json \
     "$BASE/FUZZ"
fuzz_crudo -c -z list,"$D_SMC" \
     -H "Cookie: XSRF-TOKEN=$(tok X)" -H "X-XSRF-TOKEN: $(tok X)" \
     -H "Content-Type: application/json" -d '{"decision":"APROBAR"}' \
     -f /work/reportes/s6-sin-sesion-post.json,json \
     "$BASE/api/naves/$N_REV/documentos/FUZZ/revision"
echo

# PRUEBA 10 — SEC-05: valor de la cabecera X-XSRF-TOKEN (POST)
# Sesion del agente, con su cookie XSRF-TOKEN. El cuerpo pide una accion
# invalida: si el token pasa, responde 400 sin cambiar nada; si no, 403.
# Payloads: token correcto (400), vacio, "abc", token alterado y el token
# de la sesion de la DGMM (403 los cuatro).
echo "[11/13] SEC-05 — cabecera X-XSRF-TOKEN (POST)"
fuzz_crudo -c -z file,/work/reportes/s6-tokens.txt \
     -H "Cookie: JSESSIONID=$(sid A); XSRF-TOKEN=$(tok A)" -H "X-XSRF-TOKEN: FUZZ" \
     -H "Content-Type: application/json" -d '{"accion":"VOLAR"}' \
     -f /work/reportes/s6-csrf-cabecera.json,json \
     "$BASE/api/naves/$N_SP/tramite/estado"
echo

# PRUEBA 11 — SEC-05: casos que Wfuzz no arma bien (curl)
#   a. con sesion, sin cabecera                          -> 403
#   b. con sesion, cabecera correcta pero sin la cookie  -> 403
#   c. con sesion, cookie y cabecera inventadas e iguales -> 400 (pasa: ver evidencia)
#   d. sin sesion y sin token, POST /api/naves           -> la duda de Abdiel (401 o 403)
#   e. sin sesion y sin token, POST /api/auth/registro   -> 403 (ruta publica)
echo "[12/13] SEC-05 — sin cabecera, sin cookie, token inventado y anonimo"
RUTA11="$BASE_HOST/api/naves/$N_SP/tramite/estado"
C11A="$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: JSESSIONID=$(sid A); XSRF-TOKEN=$(tok A)" \
        -H 'Content-Type: application/json' -d '{"accion":"VOLAR"}' "$RUTA11")"
C11B="$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: JSESSIONID=$(sid A)" -H "X-XSRF-TOKEN: $(tok A)" \
        -H 'Content-Type: application/json' -d '{"accion":"VOLAR"}' "$RUTA11")"
C11C="$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: JSESSIONID=$(sid A); XSRF-TOKEN=inventado-$TS" \
        -H "X-XSRF-TOKEN: inventado-$TS" -H 'Content-Type: application/json' -d '{"accion":"VOLAR"}' "$RUTA11")"
C11D="$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d '{}' "$BASE_HOST/api/naves")"
C11E="$(curl -s -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' -d '{}' "$BASE_HOST/api/auth/registro")"
ESTADO_SP="$(get A "/api/naves/$N_SP/tramite" | grep -o '"estado":"[A-Z_]*"' | head -1 | cut -d'"' -f4)"
echo "  a=$C11A b=$C11B c=$C11C d=$C11D e=$C11E  tramite de $N_SP: $ESTADO_SP"
echo

# PRUEBA 12 — SEC-03: contrasenas en los archivos versionados
# Esperado: .env no esta versionado y ninguna propiedad o compose trae la
# contrasena escrita. No se imprime ningun valor.
echo "[13/13] SEC-03 — contrasenas en el repositorio"
ENV_VERSIONADO="$(git ls-files .env | wc -l | tr -d ' ')"
PROPS="$(git grep -h -E '^(spring\.datasource\.password|DB_PASSWORD)=' HEAD -- '*.properties' '.env.example' \
         | sed -E 's/=.*\$\{.*/=<variable>/; s/=cambie-esta-contrasena$/=<marcador>/' | sort | uniq -c | tr -s ' ' | tr '\n' ';')"
COMPOSE="$(git grep -c -E 'POSTGRES_PASSWORD[=:] *[^$" ]' HEAD -- docker-compose.yml | wc -l | tr -d ' ')"
echo "  .env versionado: $ENV_VERSIONADO  propiedades: $PROPS  compose con contrasena escrita: $COMPOSE"
echo

# Verificaciones finales: la nave ajena no cambio.
AJENA_DOCS="$(get O "/api/naves/$N_AJENA/documentos" | grep -o '"estado":"[A-Z]*"' | sort | uniq -c | tr -s ' ' | tr '\n' ' ')"

# --- Resumen ----------------------------------------------------------

total500=0
for f in s6-hu11-estado s6-hu12-id-documento s6-hu12-id-nave s6-hu12-decision \
         s6-hu12-observacion s6-sin-sesion s6-sin-sesion-post s6-csrf-cabecera; do
  n="$(grep -o '"code": 500' "$REP/$f.json" 2>/dev/null | wc -l | tr -d ' ')"
  total500=$((total500 + n))
done
n7b="$(grep -c -- '-> 500' "$SALIDA7B")"
total500=$((total500 + n7b))
for c in $P2 $P5 $P5_SP $LEE_AGENTE $LEE_AUDITOR $P8 $C11A $C11B $C11C $C11D $C11E; do
  case "$c" in *500*) total500=$((total500 + 1)) ;; esac
done

anotar "1. HU-11 filtro ?estado=             : $(codigos "$REP/s6-hu11-estado.json")"
anotar "2. HU-11 bandeja/conteo por rol      : $P2(esperado A:403/403 O:403/403 D:200/200 U:200/200 X:401/401)"
anotar "3. HU-12 ID de documento             : $(codigos "$REP/s6-hu12-id-documento.json")"
detalle "$REP/s6-hu12-id-documento.json" | tee -a "$RESUMEN"
anotar "4. HU-12 ID de nave                  : $(codigos "$REP/s6-hu12-id-nave.json")"
detalle "$REP/s6-hu12-id-nave.json" | tee -a "$RESUMEN"
anotar "5. HU-12 revision por rol            : $P5(esperado A:403 O:403 U:403 X:401) itc=$ITC_TRAS_ROLES (debe ser PENDIENTE); solicitud presentada=$P5_SP (409)"
anotar "6. HU-12 campo decision              : $(codigos "$REP/s6-hu12-decision.json")"
anotar "7a. HU-12 observacion (validacion)   : $(codigos "$REP/s6-hu12-observacion.json")"
anotar "7b. HU-12 observacion (guardado)     : $(awk '{print $NF}' "$SALIDA7B" | sort | uniq -c | awk '{printf "%sx%s ", $2, $1}')lectura agente=$LEE_AGENTE auditor=$LEE_AUDITOR"
anotar "8. D4 aprobar con SMC observado      : $P8 $F8 (esperado 409 con faltantes)"
anotar "9. Sin sesion                        : $(codigos "$REP/s6-sin-sesion.json")$(codigos "$REP/s6-sin-sesion-post.json")"
anotar "10. SEC-05 cabecera X-XSRF-TOKEN     : $(codigos "$REP/s6-csrf-cabecera.json")(esperado 400x1 403x4)"
anotar "11. SEC-05 casos sueltos             : sin-cabecera=$C11A sin-cookie=$C11B inventado=$C11C anonimo-naves=$C11D anonimo-registro=$C11E; tramite $N_SP=$ESTADO_SP"
anotar "12. SEC-03 repositorio               : .env versionado=$ENV_VERSIONADO; $PROPS compose con contrasena=$COMPOSE"
anotar "    nave ajena, documentos al final  : $AJENA_DOCS(deben ser 4 PENDIENTE)"
anotar ""
anotar "Respuestas 500: $total500"

rm -f "$PDF"; rm -rf "$JARS"

echo "======================================================"
echo " Listo. Reportes y resumen en seguridad/reportes/ (no se versionan)."
echo
echo " Criterio del sprint: cero 500; ningun documento de otra nave ni otro"
echo " rol con 200 (pruebas 3-5); la nave ajena intacta; CSRF rechaza todo"
echo " POST sin el token correcto."
echo "======================================================"
