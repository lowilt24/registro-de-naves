/* Bandeja separada del trámite de registro. La API restringe también la propiedad. */
let consultaPatentes = 0;
async function cargarPatentes() {
  const consulta = ++consultaPatentes;
  try {
    const filtro = $('filtro-patente').value;
    const r = await api('/api/patentes-provisionales' + (filtro ? '?estado=' + encodeURIComponent(filtro) : ''));
    if (consulta !== consultaPatentes) return;
    if (!r.ok) throw new Error(r.datos?.mensaje || 'No se pudieron consultar las solicitudes.');
    $('lista-patentes').innerHTML = r.datos.map(s => `<tr><td>${escapar(s.nombre)}</td><td>${escapar(s.estado.replaceAll('_', ' '))}</td><td>${escapar(s.numero || 'Sin emitir')}</td><td><a href="/patente-provisional.html?naveId=${encodeURIComponent(s.naveId)}">Abrir solicitud</a></td></tr>`).join('');
    $('patentes-vacias').classList.toggle('oculto', r.datos.length !== 0);
  } catch (e) { if (e.message !== 'sin sesion') mostrar($('aviso-patente'), 'ocupado', e.message); }
}
$('filtro-patente').addEventListener('change', cargarPatentes);
$('form-iniciar').addEventListener('submit', e => {
  e.preventDefault(); if ($('nave-elegida').value) location.href = '/patente-provisional.html?naveId=' + encodeURIComponent($('nave-elegida').value);
});
(async function iniciar() {
  pintarSesion();
  const sesion = await api('/api/auth/sesion');
  if (sesion.datos?.rol === 'AGENTE_NAVIERO') {
    const r = await api('/api/naves?estado=APROBADA');
    if (!r.ok) { mostrar($('aviso-patente'), 'ocupado', r.datos?.mensaje || 'No se pudieron consultar las naves.'); return; }
    const naves = r.datos.filter(n => n.servicio === 'INTERNACIONAL');
    $('iniciar-patente').classList.remove('oculto');
    $('nave-elegida').innerHTML = '<option value="">Seleccione una nave</option>' + naves.map(n => `<option value="${escapar(n.id)}">${escapar(n.nombre)}</option>`).join('');
    $('sin-naves').textContent = naves.length ? '' : 'Necesita una nave internacional con el registro aprobado. Puede completar su registro en Mis naves.';
    $('form-iniciar').classList.toggle('oculto', !naves.length);
  } else { $('volver').href = '/bandeja.html'; }
  await cargarPatentes();
})();
