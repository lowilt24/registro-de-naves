/* Navesitas — estado del tramite (HU-09), camara 6 del expediente.

   El servidor decide que acciones puede pedir quien mira (rol, relacion
   con la nave y estado actual) y las manda en "acciones". Aqui se pinta un
   boton por cada una y ninguno mas: si no viene, no se ofrece.

   Usa naveId, que define expediente.js, y fechaHora, de documentos.js. */

const NOMBRE_ACCION = {
  ADMITIR:  'Admitir',
  OBSERVAR: 'Observar',
  APROBAR:  'Aprobar',
  RECHAZAR: 'Rechazar',
  SUBSANAR: 'Subsanar',
  DESISTIR: 'Desistir'
};

/** Como se lee cada accion en la linea de tiempo. */
const ACCION_PASADA = {
  PRESENTAR: 'Presentó la solicitud',
  MIGRACION: 'Estado inicial (migración del Sprint 5)',
  ADMITIR:   'Admitió',
  OBSERVAR:  'Observó',
  APROBAR:   'Aprobó',
  RECHAZAR:  'Rechazó',
  SUBSANAR:  'Subsanó',
  DESISTIR:  'Desistió'
};

const NOMBRE_ROL = {
  AGENTE_NAVIERO:   'Agente naviero',
  FUNCIONARIO_DGMM: 'DGMM',
  AUDITOR:          'Auditor',
  ADMINISTRADOR:    'Administrador'
};

/** Las que llevan el boton principal; las demas van como secundarias. */
const ACCION_PRINCIPAL = ['ADMITIR', 'APROBAR', 'SUBSANAR'];
/** Llevan a un estado final. */
const ACCION_FINAL = ['APROBAR', 'RECHAZAR', 'DESISTIR'];
/** P5 (propuesta): sin observacion el servidor responde 400. */
const ACCION_CON_OBSERVACION = ['OBSERVAR', 'RECHAZAR'];

let accionEnCurso = false;

// ---------------------------------------------------------------
// Estado actual y botones
// ---------------------------------------------------------------
async function cargarTramite() {
  const { ok, estado, datos } = await api('/api/naves/' + encodeURIComponent(naveId) + '/tramite');

  if (!ok) {
    $('tramite-actual').innerHTML = '<p class="vacio">'
      + escapar((datos && datos.mensaje) || 'No se pudo consultar el estado del trámite.') + '</p>';
    $('form-tramite').classList.add('oculto');
    if (estado === 403 || estado === 404) $('seccion-tramite').classList.add('bloqueado');
    return;
  }

  pintarTramite(datos);
  await cargarLineaDeTiempo();
}

function pintarTramite(t) {
  const seccion = $('seccion-tramite');
  seccion.dataset.final = t.esFinal ? 'si' : 'no';

  const acciones = Array.isArray(t.acciones) ? t.acciones : [];

  let html = '<table><tr><th>Estado del trámite</th><td>' + etiquetaTramite(t.estado) + '</td></tr>'
    + '<tr><th>Último cambio</th><td class="cifra">' + fechaHora(t.actualizadoEn) + '</td></tr></table>';

  if (t.esFinal) {
    html += '<p class="nota">El trámite terminó; ya no admite cambios.</p>';
  } else if (acciones.includes('SUBSANAR')) {
    // P4 (propuesta): subsanar reutiliza la carga de HU-07.
    html += '<p class="nota">Corrija o vuelva a cargar los documentos observados en la '
          + 'cámara 5 y después pulse Subsanar.</p>';
  } else if (acciones.length === 0) {
    html += '<p class="nota">No tiene acciones pendientes sobre este trámite.</p>';
  }
  $('tramite-actual').innerHTML = html;

  // Ficha de la nave (expediente.js): el mismo dato, rotulado aparte.
  const enFicha = $('ficha-estado-tramite');
  if (enFicha) enFicha.innerHTML = etiquetaTramite(t.estado);

  const form = $('form-tramite');
  form.classList.toggle('oculto', acciones.length === 0);
  if (acciones.length === 0) return;

  const pideObservacion = acciones.some(a => ACCION_CON_OBSERVACION.includes(a));
  $('pista-observacion').textContent = pideObservacion
    ? '(obligatoria para observar o rechazar)'
    : '(opcional)';
  $('nota-final').classList.toggle('oculto', !acciones.some(a => ACCION_FINAL.includes(a)));

  $('botones-tramite').innerHTML = acciones.map(a => {
    const clase = ACCION_PRINCIPAL.includes(a) ? '' : ' class="secundario"';
    return '<button type="button"' + clase + ' data-accion="' + escapar(a) + '">'
         + escapar(NOMBRE_ACCION[a] || a) + '</button>';
  }).join('');
}

$('botones-tramite').addEventListener('click', (e) => {
  const boton = e.target.closest('button[data-accion]');
  if (boton) ejecutarAccion(boton.dataset.accion);
});

async function ejecutarAccion(accion) {
  if (accionEnCurso) return;
  const form = $('form-tramite');
  limpiarErrores(form);
  ocultar($('aviso-tramite'));

  const observacion = $('t-observacion').value.trim();

  // Mismo criterio que el servidor, para no hacer un viaje en vano.
  if (ACCION_CON_OBSERVACION.includes(accion) && observacion === '') {
    form.querySelector('[data-error="observacion"]').textContent =
      'Escriba la observación: es obligatoria al observar o rechazar.';
    $('t-observacion').focus();
    return;
  }

  accionEnCurso = true;
  const botones = $('botones-tramite').querySelectorAll('button');
  botones.forEach(b => b.disabled = true);

  try {
    const { ok, estado, datos } = await api(
      '/api/naves/' + encodeURIComponent(naveId) + '/tramite/estado',
      { metodo: 'POST', cuerpo: { accion, observacion: observacion || null } });

    if (ok) {
      $('t-observacion').value = '';
      pintarTramite(datos);
      mostrar($('aviso-tramite'), 'libre',
        'Listo. El trámite quedó en ' + (NOMBRE_ESTADO_TRAMITE[datos.estado] || datos.estado) + '.');
      await cargarLineaDeTiempo();
      return;
    }

    // 400 observacion, 403 rol o nave ajena, 409 transicion invalida o
    // cambio simultaneo. En 409 el estado pudo cambiar: se recarga.
    pintarErrores(datos, form);
    mostrar($('aviso-tramite'), 'ocupado', mensajeDeError(datos));
    if (estado === 409) {
      const aviso = $('aviso-tramite').textContent;
      await cargarTramite();
      mostrar($('aviso-tramite'), 'ocupado', aviso);
    }

  } catch (err) {
    if (err.message !== 'sin sesion') {
      mostrar($('aviso-tramite'), 'ocupado', 'No se pudo contactar el servidor.');
    }
  } finally {
    accionEnCurso = false;
    $('botones-tramite').querySelectorAll('button').forEach(b => b.disabled = false);
  }
}

/**
 * Sprint 5 (PO): el 409 de documentos faltantes trae la lista aparte.
 * Se nombran como en la camara 5 (TIPOS_DOCUMENTO, de documentos.js).
 */
function mensajeDeError(datos) {
  if (datos && Array.isArray(datos.faltantes) && datos.faltantes.length) {
    const nombres = datos.faltantes.map(t =>
      (TIPOS_DOCUMENTO.find(d => d.valor === t) || { nombre: t }).nombre);
    return 'Faltan documentos obligatorios: ' + nombres.join(', ')
         + '. Cárguelos en la sección Documentos antes de continuar.';
  }
  return (datos && datos.mensaje) || 'No se pudo cambiar el estado del trámite.';
}

// ---------------------------------------------------------------
// Linea de tiempo
// ---------------------------------------------------------------
async function cargarLineaDeTiempo() {
  const lista = $('linea-tiempo');
  const { ok, datos } = await api('/api/naves/' + encodeURIComponent(naveId) + '/tramite/historial');

  if (!ok || !Array.isArray(datos)) {
    lista.innerHTML = '<li class="vacio">No se pudo cargar la línea de tiempo.</li>';
    return;
  }
  if (datos.length === 0) {
    lista.innerHTML = '<li class="vacio">Todavía no hay movimientos.</li>';
    return;
  }

  // La mas reciente arriba: es la que se busca primero.
  lista.innerHTML = datos.slice().reverse().map(h => `
    <li>
      <p class="linea-encabezado">
        <time class="cifra" datetime="${escapar(h.fecha)}">${fechaHora(h.fecha)}</time>
        <b>${escapar(ACCION_PASADA[h.accion] || h.accion)}</b>
      </p>
      <p class="linea-detalle">
        ${escapar(h.usuario)} <span class="pista">(${escapar(NOMBRE_ROL[h.rol] || h.rol)})</span>
        &middot;
        ${h.estadoAnterior ? etiquetaTramite(h.estadoAnterior) + ' <span aria-label="pasa a">&rarr;</span> ' : ''}${etiquetaTramite(h.estadoNuevo)}
      </p>
      ${h.observacion ? '<p class="linea-observacion">' + escapar(h.observacion) + '</p>' : ''}
    </li>`).join('');
}
