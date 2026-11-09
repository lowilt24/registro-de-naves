/* Formularios de la etapa académica. Nunca se modifican los estados del registro. */
const patenteNaveId = parametro('naveId');
const patenteBase = '/api/naves/' + encodeURIComponent(patenteNaveId) + '/patente-provisional';
let patenteDatos = null;
let patenteOcupada = false;
function fechaPatente(iso) {
  const fecha = new Date(iso);
  return iso && !Number.isNaN(fecha.getTime()) ? fecha.toLocaleString('es-PA', { timeZone: 'America/Panama', dateStyle: 'medium', timeStyle: 'short' }) : '—';
}
const patenteEstados = { BORRADOR: 'Borrador', EN_REVISION: 'En revisión', SUBSANACION: 'Subsanación', APROBADA: 'Aprobada' };
function tieneAccionPatente(accion) { return patenteDatos?.acciones?.includes(accion); }
function estadoPatente(estado) { return `<span class="tramite-estado" data-estado="${escapar(estado)}">${escapar(patenteEstados[estado] || estado)}</span>`; }
function opcionesOrigenPatente(vacio = true) {
  return (vacio ? '<option value="">No autorizar reutilización</option>' : '') + ['CERTIFICADO_PROPIEDAD', 'PODER_NOTARIAL', 'ITC', 'SMC'].map(t => `<option value="${t}">${t.replaceAll('_', ' ')}</option>`).join('');
}
function motivoPatente() { return '<label>Motivo de la decisión<textarea name="motivo" maxlength="1000" required></textarea></label>'; }
function archivoPatente(e) {
  return `<a data-pp-descarga href="${patenteBase}/evidencias/${encodeURIComponent(e.evidenciaId)}/archivo">Descargar ${escapar(e.nombreArchivo)} (v${escapar(e.version)})</a><small class="pp-hash">SHA-256: ${escapar(e.hashSha256)}</small>`;
}
function requisitoPatente(r) {
  const s = patenteDatos.solicitud;
  const anterior = patenteDatos.envios.at(-1)?.evidencias.find(e => e.requisitoId === r.id);
  const modificable = s.estado === 'BORRADOR' || !anterior || anterior.decision === 'OBSERVADA';
  let html = `<article class="pp-requisito"><h4>${escapar(r.nombre)}</h4><p class="pista">${escapar(r.clase)} · ${escapar(r.motivo)}</p>`;
  html += r.evidenciaId ? archivoPatente(r) : '<p class="nota">Pendiente de adjuntar PDF.</p>';
  if (r.origenDocumentoId) html += `<p class="pista">Copia de la versión exacta HU-07 #${escapar(r.origenDocumentoId)}. Requiere revisión propia de patente.</p>`;
  if (r.decision) html += `<p><strong>${escapar(r.decision)}</strong> ${escapar(r.observacion || '')}</p>`;
  if (tieneAccionPatente('CARGAR') && modificable) {
    html += `<form data-pp="cargar" data-rid="${r.id}"><label>PDF para ${escapar(r.nombre)}<input name="archivo" type="file" accept="application/pdf,.pdf" required></label><p class="pista">Máximo 10 MB. Cada carga conserva una versión nueva.</p><button>Cargar PDF</button></form>`;
    const documentos = patenteDatos.documentosReutilizables.filter(d => d.tipo === r.tipoOrigen);
    if (documentos.length) html += `<form data-pp="referenciar" data-rid="${r.id}"><label>Versión autorizada para reutilizar<select name="documentoId">${documentos.map(d => `<option value="${d.id}">${escapar(d.tipo)} v${escapar(d.version)} · #${escapar(d.id)} · ${escapar(d.nombreArchivo)}</option>`).join('')}</select></label><button class="secundario">Usar esta versión exacta</button></form>`;
  }
  if (tieneAccionPatente('CONFIGURAR') && modificable) html += `<details><summary>Autorizar reutilización documental</summary><p>Seleccione solo un tipo que corresponda a este requisito. Un poder no sustituye automáticamente la designación del residente.</p><form data-pp="reutilizacion" data-rid="${r.id}"><label>Tipo de origen<select name="tipoOrigen">${opcionesOrigenPatente(false)}</select></label>${motivoPatente()}<button class="secundario">Autorizar tipo de origen</button></form></details>`;
  if (tieneAccionPatente('REVISAR') && !r.decision) html += `<form data-pp="revision" data-eid="${r.evidenciaId}"><label>Decisión documental<select name="decision"><option value="APROBAR">Aprobar evidencia</option><option value="OBSERVAR">Observar evidencia</option></select></label><label>Observación (obligatoria al observar)<textarea name="observacion" maxlength="1000"></textarea></label><button>Guardar revisión</button></form>`;
  return html + '</article>';
}
function pintarPatente() {
  const d = patenteDatos, s = d.solicitud;
  $('titulo-patente').textContent = 'Patente provisional: ' + d.nombreNave;
  let html = '';
  if (!s) {
    html = `<section class="tarjeta"><h3>Solicitud nueva</h3><p>${escapar(d.motivo || 'Registro aprobado. Declare la procedencia para determinar el documento de origen.')}</p>`;
    if (tieneAccionPatente('CREAR')) html += '<form data-pp="crear"><label>Procedencia de la nave<select name="procedencia"><option value="REGISTRO_ANTERIOR">Registro anterior: cancelación</option><option value="CONSTRUCCION">Nueva construcción</option><option value="VENTA_JUDICIAL">Venta judicial</option></select></label><div class="acciones"><button>Crear borrador de patente provisional</button></div></form>';
    else html += '<p class="nota">Solo el agente dueño puede crear la solicitud cuando se cumplan las condiciones.</p>';
    $('contenido-patente').innerHTML = html + '</section>'; return;
  }
  html += `<section class="tarjeta"><h3>Solicitud #${escapar(s.id)}</h3><p>Estado de patente: ${estadoPatente(s.estado)}</p><p>Procedencia: ${escapar(s.procedencia.replaceAll('_', ' '))}</p><p>Catálogo v${escapar(s.requisitosVersion)} · ${s.requisitosConfirmados ? 'Confirmado por la DGMM' : 'Pendiente de definición y confirmación DGMM'}</p><p class="nota">${escapar(s.confirmacionMotivo || 'La DGMM debe definir los documentos técnicos y adicionales aplicables antes de que el agente envíe.')}</p>`;
  if (d.rol === 'AUDITOR') html += '<p class="nota">Vista del auditor: consulta y descarga, sin modificar el trámite.</p>';
  if (s.estado === 'SUBSANACION') html += '<p class="nota">Corrija los PDF observados y adjunte los nuevos requisitos, si los hay. Las evidencias anteriores permanecen en el historial.</p>';
  if (tieneAccionPatente('ENVIAR')) html += `<form data-pp="enviar"><button ${s.requisitosConfirmados ? '' : 'disabled'}>Enviar a revisión DGMM</button></form>`;
  html += '</section>';
  if (tieneAccionPatente('CONFIGURAR')) html += `<section class="tarjeta"><h3>Definición de requisitos · DGMM</h3><p>Los certificados técnicos y de seguridad se definen para este caso. No se trasladan automáticamente las reglas del registro.</p><form data-pp="requisito"><div class="rejilla"><label>Nombre del documento<input name="nombre" maxlength="180" required></label><label>Clase<select name="clase"><option value="TECNICO">Técnico y seguridad</option><option value="ADICIONAL">Adicional para el caso</option></select></label></div>${motivoPatente()}<label>Reutilización autorizada (opcional)<select name="tipoOrigen">${opcionesOrigenPatente()}</select></label><div class="acciones"><button>Agregar requisito</button></div></form><hr><form data-pp="confirmar"><label class="pp-checkbox"><input type="checkbox" name="sinTecnicosAdicionales"> Este caso académico no requiere certificados técnicos adicionales (justificar)</label>${motivoPatente()}<button>Confirmar catálogo de requisitos</button></form></section>`;
  html += '<section class="tarjeta"><h3>Documentos de la patente provisional</h3><p>La DGMM revisa la suficiencia y autenticación de los documentos. Use archivos ficticios para esta simulación.</p>' + d.requisitos.map(requisitoPatente).join('') + '</section>';
  if (tieneAccionPatente('REVISAR')) html += '<section class="tarjeta"><h3>Decisión DGMM</h3><form data-pp="devolver"><label>Correcciones solicitadas<textarea name="observacion" maxlength="1000" required></textarea></label><button class="secundario">Devolver a subsanación</button></form><form data-pp="aprobar"><p>La aprobación requiere todas las evidencias aprobadas en este envío. Emite una única simulación.</p><button>Aprobar y emitir patente simulada</button></form></section>';
  if (d.emision) html += `<section class="tarjeta"><h3>Patente provisional simulada emitida</h3><p class="cifra">${escapar(d.emision.numero)}</p><p>${escapar(d.emision.funcionario)} · ${escapar(fechaPatente(d.emision.fecha))}</p><a class="boton" data-pp-descarga href="${patenteBase}/archivo">Descargar patente simulada (PDF)</a><p class="pista">Sin firma oficial. Sin vencimiento definido. La descarga conserva la misma emisión.</p><small class="pp-hash">${escapar(d.emision.hashSha256 || 'El hash SHA-256 se guardará al generar el PDF por primera vez.')}</small><details><summary>Datos inmutables de la emisión</summary><pre class="pp-instantanea">${escapar(d.emision.datosEmitidos)}</pre></details></section>`;
  html += '<section class="tarjeta"><h3>Envíos y versiones conservadas</h3>' + (d.envios.length ? d.envios.map(e => `<details><summary>Envío ${escapar(e.numero)} · ${escapar(fechaPatente(e.fecha))} · Catálogo v${escapar(e.requisitosVersion)}</summary><p>${escapar(e.confirmacionMotivo)}</p>${e.evidencias.map(v => `<div class="pp-requisito"><strong>${escapar(v.nombre)}</strong><p>${escapar(v.motivo)}</p>${archivoPatente(v)}<p>${escapar(v.decision || 'Sin revisión')} · ${escapar(v.observacion || '')} ${escapar(v.funcionario || '')}</p></div>`).join('')}<details><summary>Datos de nave al enviar</summary><pre class="pp-instantanea">${escapar(e.datosNave)}</pre></details></details>`).join('') : '<p class="nota">Todavía no hay envíos.</p>') + '</section>';
  html += '<section class="tarjeta"><h3>Historial de la solicitud</h3><ol class="linea-tiempo">' + d.historial.map(e => `<li><strong>${escapar(e.accion.replaceAll('_', ' '))}</strong><p>${escapar(e.actor)} · ${escapar(e.rol)} · ${escapar(fechaPatente(e.fecha))}</p><p>${escapar(e.estadoAnterior || '')} ${e.estadoNuevo ? '→ ' + escapar(patenteEstados[e.estadoNuevo]) : ''} ${escapar(e.observacion || '')}</p><small>${e.envioId ? 'Envío #' + escapar(e.envioId) + ' ' : ''}${e.evidenciaId ? 'Evidencia #' + escapar(e.evidenciaId) : ''}</small></li>`).join('') + '</ol></section>';
  $('contenido-patente').innerHTML = html;
}
async function cargarPatente() {
  const r = await api(patenteBase);
  if (!r.ok) { patenteDatos = null; $('contenido-patente').innerHTML = ''; throw new Error(r.datos?.mensaje || 'No se pudo abrir la solicitud.'); }
  patenteDatos = r.datos; pintarPatente();
}
// Una respuesta de error se muestra en pantalla; nunca se descarga como PDF.
$('contenido-patente').addEventListener('click', async e => {
  const enlace = e.target.closest('a[data-pp-descarga]'); if (!enlace) return;
  e.preventDefault(); if (enlace.getAttribute('aria-disabled') === 'true') return;
  enlace.setAttribute('aria-disabled', 'true');
  try {
    const r = await fetch(enlace.href);
    if (r.status === 401) { location.href = '/login.html?expirada=1'; return; }
    if (!r.ok) { const d = await r.json(); throw new Error(d.mensaje || 'No se pudo descargar el PDF.'); }
    if (!r.headers.get('Content-Type')?.includes('application/pdf')) throw new Error('El servidor no devolvió un PDF.');
    const url = URL.createObjectURL(await r.blob()), a = document.createElement('a');
    a.href = url;
    a.download = enlace.href.endsWith('/patente-provisional/archivo') ? patenteDatos.emision.numero + '.pdf' : 'evidencia-patente.pdf';
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    mostrar($('aviso-patente'), 'libre', 'PDF descargado.');
    if (enlace.href.endsWith('/patente-provisional/archivo')) await cargarPatente();
  } catch (err) { mostrar($('aviso-patente'), 'ocupado', err.message || 'No se pudo descargar. Puede reintentarlo.'); }
  finally { enlace.removeAttribute('aria-disabled'); }
});
$('contenido-patente').addEventListener('submit', async e => {
  const form = e.target.closest('form[data-pp]'); if (!form) return;
  e.preventDefault(); if (patenteOcupada || !form.reportValidity()) return;
  const valores = Object.fromEntries(new FormData(form));
  const accion = form.dataset.pp, rid = form.dataset.rid;
  const opciones = { metodo: 'POST' }; let ruta = '';
  if (accion === 'crear') opciones.cuerpo = { procedencia: valores.procedencia };
  if (accion === 'requisito') { ruta = '/requisitos'; opciones.cuerpo = { ...valores, tipoOrigen: valores.tipoOrigen || null }; }
  if (accion === 'confirmar') { ruta = '/requisitos/confirmar'; opciones.cuerpo = { motivo: valores.motivo, sinTecnicosAdicionales: !!valores.sinTecnicosAdicionales }; }
  if (accion === 'reutilizacion') { ruta = `/requisitos/${rid}/reutilizacion`; opciones.cuerpo = valores; }
  if (accion === 'referenciar') { ruta = `/requisitos/${rid}/referenciar`; opciones.cuerpo = { documentoId: Number(valores.documentoId) }; }
  if (accion === 'cargar') { ruta = `/requisitos/${rid}/archivo`; opciones.formulario = new FormData(form); }
  if (accion === 'enviar') ruta = '/enviar';
  if (accion === 'revision') { ruta = `/evidencias/${form.dataset.eid}/revision`; opciones.cuerpo = { ...valores, envioId: patenteDatos.solicitud.ultimoEnvioId }; }
  if (accion === 'devolver' || accion === 'aprobar') { ruta = '/' + accion; opciones.cuerpo = { ...valores, envioId: patenteDatos.solicitud.ultimoEnvioId }; }
  patenteOcupada = true;
  $('contenido-patente').querySelectorAll('button').forEach(b => b.disabled = true);
  mostrar($('aviso-patente'), 'neutro', 'Procesando solicitud…');
  try {
    const r = await api(patenteBase + ruta, opciones);
    if (!r.ok) {
      const mensaje = r.datos?.mensaje || 'No se pudo completar la acción.';
      if (r.estado === 409) await cargarPatente();
      throw new Error(mensaje + (r.datos?.campos ? ' ' + Object.values(r.datos.campos).join(' ') : ''));
    }
    patenteDatos = r.datos; pintarPatente(); mostrar($('aviso-patente'), 'libre', 'Acción guardada.');
  } catch (err) { if (err.message !== 'sin sesion') mostrar($('aviso-patente'), 'ocupado', err.message); }
  finally { patenteOcupada = false; $('contenido-patente').querySelectorAll('button').forEach(b => b.disabled = false); if (patenteDatos?.solicitud && !patenteDatos.solicitud.requisitosConfirmados) { const enviar = document.querySelector('[data-pp="enviar"] button'); if (enviar) enviar.disabled = true; } }
});
(async function iniciar() {
  pintarSesion();
  try {
    if (!patenteNaveId || !/^\d+$/.test(patenteNaveId)) throw new Error('Seleccione una nave desde Patentes provisionales.');
    $('expediente-origen').href = '/expediente.html?naveId=' + encodeURIComponent(patenteNaveId);
    await cargarPatente();
  } catch (e) { if (e.message !== 'sin sesion') mostrar($('aviso-patente'), 'ocupado', e.message); }
  finally { $('contenido-patente').setAttribute('aria-busy', 'false'); }
})();
