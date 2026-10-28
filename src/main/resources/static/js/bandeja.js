/* Navesitas — bandeja de tramites de la DGMM (HU-11).

   Lista los tramites de todas las naves. Por defecto, los que esperan una
   decision de la DGMM (Solicitud presentada y En revision); los chips
   filtran por cada uno de los seis estados. Cada fila abre el expediente,
   donde la camara 6 ofrece las acciones.

   La ve la DGMM y el auditor; a un agente el servidor le responde 403. */

/** Chip por defecto: sin ?estado, el servidor devuelve lo pendiente. */
const POR_ATENDER = '';

let filtroBandeja = POR_ATENDER;
let conteoBandeja = null;
let consultaBandeja = 0;   // descarta respuestas viejas si se pulsa rapido

function fechaCorta(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d)) return escapar(iso);
  const zona = { timeZone: 'America/Panama' };
  const fecha = d.toLocaleDateString('es', { ...zona, day: 'numeric', month: 'short', year: 'numeric' })
    .replace('.', '');
  const hora = d.toLocaleTimeString('es', { ...zona, hour: '2-digit', minute: '2-digit', hour12: false });
  return fecha + ', ' + hora;
}

function textoDias(dias) {
  if (dias === 0) return 'Hoy';
  if (dias === 1) return '1 día';
  return dias + ' días';
}

// ---------------------------------------------------------------
// Conteo y chips
// ---------------------------------------------------------------
async function cargarConteoBandeja() {
  const { ok, estado, datos } = await api('/api/tramites/conteo-por-estado');
  if (estado === 403) {
    sinPermiso(datos);
    return false;
  }
  conteoBandeja = ok ? datos : null;
  pintarChipsBandeja();
  return true;
}

function pintarChipsBandeja() {
  const caja = $('filtro-bandeja');
  if (!conteoBandeja || !conteoBandeja.estados) {
    caja.innerHTML = '';
    return;
  }
  const e = conteoBandeja.estados;
  const opciones = [[POR_ATENDER, 'Por atender', (e.SOLICITUD_PRESENTADA || 0) + (e.EN_REVISION || 0)]]
    .concat(Object.keys(NOMBRE_ESTADO_TRAMITE).map(k => [k, NOMBRE_ESTADO_TRAMITE[k], e[k] || 0]));

  caja.innerHTML = opciones.map(([valor, nombre, cantidad]) => `
    <button type="button" class="chip" data-estado="${valor}"
            aria-pressed="${valor === filtroBandeja ? 'true' : 'false'}">
      ${escapar(nombre)} <span class="cifra">${cantidad}</span>
    </button>`).join('');
}

$('filtro-bandeja').addEventListener('click', async (ev) => {
  const chip = ev.target.closest('button[data-estado]');
  if (!chip || chip.dataset.estado === filtroBandeja) return;
  filtroBandeja = chip.dataset.estado;
  pintarChipsBandeja();
  await cargarBandeja();
});

// ---------------------------------------------------------------
// Lista
// ---------------------------------------------------------------
async function cargarBandeja() {
  const esta = ++consultaBandeja;
  const cuerpo = $('lista-bandeja');
  const vacia = $('bandeja-vacia');
  const caja = $('caja-bandeja');

  const ruta = filtroBandeja
    ? '/api/tramites?estado=' + encodeURIComponent(filtroBandeja)
    : '/api/tramites';

  try {
    const { ok, estado, datos } = await api(ruta);
    if (esta !== consultaBandeja) return;   // ya se pidio otro filtro

    if (estado === 403) { sinPermiso(datos); return; }

    vacia.classList.add('oculto');
    caja.classList.remove('oculto');

    if (!ok || !Array.isArray(datos)) {
      cuerpo.innerHTML = '<tr><td colspan="6">' + escapar((datos && datos.mensaje)
        || 'No se pudo cargar la bandeja.') + '</td></tr>';
      return;
    }

    if (datos.length === 0) {
      // Criterio de HU-11: mensaje claro en vez de una tabla vacia.
      caja.classList.add('oculto');
      vacia.textContent = filtroBandeja
        ? 'No hay trámites en «' + NOMBRE_ESTADO_TRAMITE[filtroBandeja] + '».'
        : 'No hay trámites esperando una decisión de la DGMM. Todo está al día.';
      vacia.classList.remove('oculto');
      return;
    }

    cuerpo.innerHTML = datos.map(t => `
      <tr>
        <td>${escapar(t.nave)}<br><span class="pista">Registro <span class="cifra">${t.naveId}</span></span></td>
        <td>${escapar(t.agente)}</td>
        <td>${etiquetaTramite(t.estado)}</td>
        <td><span class="cifra">${fechaCorta(t.ultimaAccion)}</span></td>
        <td><span class="cifra">${textoDias(t.diasEnEspera)}</span></td>
        <td><a class="enlace-fila" href="/expediente.html?naveId=${encodeURIComponent(t.naveId)}">Abrir</a></td>
      </tr>`).join('');
  } catch (e) { /* api() ya redirigio si fue 401 */ }
}

/** Un agente que llega aqui: se le explica y se le manda a sus naves. */
function sinPermiso(datos) {
  $('seccion-bandeja').classList.add('oculto');
  const aviso = $('aviso-bandeja');
  mostrar(aviso, 'ocupado', (datos && datos.mensaje) || 'La bandeja de trámites es de la DGMM.');
  aviso.insertAdjacentHTML('beforeend', ' <a href="/naves.html">Ir a Mis naves</a>');
}

(async function iniciar() {
  pintarSesion();
  if (await cargarConteoBandeja()) {
    await cargarBandeja();
  }
})();
