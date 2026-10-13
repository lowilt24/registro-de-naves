/* Navesitas — expediente de una nave.
   HU-05: registro y vinculacion de propietarios.
   HU-06: designacion y consulta del agente residente.
   HU-07 y HU-08 (camara 5) viven en documentos.js, que se carga antes.
   HU-09 (camara 6) vive en tramite.js, que tambien se carga antes.

   La nave se toma de la direccion: expediente.html?naveId=7 */

const naveId = parametro('naveId');

let cuotaDisponible = 100;   // lo que queda libre para repartir
let tienePropietarios = false;

// ---------------------------------------------------------------
// Carga de la nave
// ---------------------------------------------------------------
async function cargarNave() {
  if (!naveId) {
    mostrar($('aviso-nave'), 'ocupado', 'No se indicó qué nave abrir.');
    bloquearTodo();
    return false;
  }

  const { ok, estado, datos } = await api('/api/naves/' + encodeURIComponent(naveId));

  if (!ok) {
    // 404: no existe. 403: existe pero es de otro agente.
    const mensaje = estado === 403
      ? 'Esta nave pertenece a otro agente. No puede abrir su expediente.'
      : (datos && datos.mensaje) || 'No se encontró la nave indicada.';
    mostrar($('aviso-nave'), 'ocupado', mensaje);
    bloquearTodo();
    return false;
  }

  $('titulo-nave').textContent = 'Expediente: ' + datos.nombre;
  document.title = datos.nombre + ' — Navesitas';

  $('ficha-nave').innerHTML = `
    <div><span>Registro</span><b class="cifra">${datos.id}</b></div>
    <div><span>Tipo</span><b>${datos.tipo}</b></div>
    <div><span>Servicio</span><b>${datos.servicio}</b></div>
    <div><span>Tonelaje bruto</span><b class="cifra">${datos.tonelajeBruto}</b></div>
    <div><span>Eslora</span><b class="cifra">${datos.eslora} m</b></div>
    <div><span>Estado de la nave</span><b><span class="estado-etiqueta">${escapar(datos.estado)}</span></b></div>
    <div><span>Estado del trámite</span><b id="ficha-estado-tramite">${etiquetaTramite(datos.estadoTramite)}</b></div>
    <div><span>Fecha de registro</span><b class="cifra">${fechaHora(datos.creadoEn || datos.fechaRegistro)}</b></div>`;

  return true;
}

function bloquearTodo() {
  $('tarjeta-nave').classList.add('oculto');
  $('seccion-propietarios').classList.add('bloqueado');
  $('seccion-agente').classList.add('bloqueado');
  $('seccion-documentos').classList.add('bloqueado');
  $('seccion-tramite').classList.add('bloqueado');
}

/**
 * Sprint 5: la DGMM y el auditor abren el expediente de cualquier nave
 * para ver y mover el tramite (D1, P7, P8). Propietarios, agente y
 * documentos siguen siendo del agente que registro la nave, asi que esas
 * camaras no se muestran.
 */
function vistaDeConsulta(rol) {
  ['seccion-propietarios', 'seccion-agente', 'seccion-documentos']
    .forEach(id => $(id).classList.add('oculto'));
  mostrar($('aviso-nave'), 'neutro', rol === 'FUNCIONARIO_DGMM'
    ? 'Vista de la DGMM: aquí ve la ficha de la nave y gestiona el estado de su trámite. '
      + 'Propietarios, agente residente y documentos los gestiona el agente que registró la nave.'
    : 'Vista de consulta: puede leer el estado del trámite y su línea de tiempo, sin cambiarlos.');
}

async function rolDeSesion() {
  try {
    const { datos } = await api('/api/auth/sesion');
    return datos && datos.autenticado ? datos.rol : null;
  } catch (e) {
    return null;
  }
}

// ---------------------------------------------------------------
// HU-05 — propietarios
// ---------------------------------------------------------------
async function cargarPropietarios() {
  const cuerpo = $('lista-propietarios');
  const { ok, datos } = await api('/api/naves/' + naveId + '/propietarios');

  if (!ok || !Array.isArray(datos)) {
    cuerpo.innerHTML = '<tr><td colspan="4">No se pudieron cargar los propietarios.</td></tr>';
    return;
  }

  tienePropietarios = datos.length > 0;

  if (!tienePropietarios) {
    cuerpo.innerHTML = '<tr><td colspan="4" class="vacio">Todavía no hay propietarios vinculados.</td></tr>';
    cuotaDisponible = 100;
  } else {
    cuerpo.innerHTML = datos.map(p => `
      <tr>
        <td>${escapar(p.nombre)}${p.paisConstitucion
              ? '<br><span class="pista">Constituida en ' + escapar(p.paisConstitucion) + '</span>' : ''}</td>
        <td>${p.tipo === 'JURIDICA' ? 'Jurídica' : 'Natural'}</td>
        <td>${escapar(p.nacionalidad)}</td>
        <td><span class="cuota">${p.porcentaje}%</span></td>
      </tr>`).join('');

    const asignado = datos.reduce((total, p) => total + Number(p.porcentaje), 0);
    cuotaDisponible = Math.round((100 - asignado) * 100) / 100;
  }

  $('cuota-disponible').textContent = 'Participación disponible: ' + cuotaDisponible + '%';
  $('p-porcentaje').max = cuotaDisponible;
  $('p-porcentaje').placeholder = cuotaDisponible;

  actualizarPrecondicionAgente();
}

// El pais de constitucion solo aplica a una sociedad.
$('p-tipo').addEventListener('change', () => {
  const esJuridica = $('p-tipo').value === 'JURIDICA';
  $('caja-pais').classList.toggle('oculto', !esJuridica);
  if (!esJuridica) $('p-paisConstitucion').value = '';
});

$('form-propietario').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = $('form-propietario');
  limpiarErrores(form);
  ocultar($('aviso-propietario'));

  const cuerpo = {
    nombre:           texto('p-nombre'),
    tipo:             texto('p-tipo'),
    nacionalidad:     texto('p-nacionalidad'),
    domicilio:        texto('p-domicilio'),
    paisConstitucion: texto('p-paisConstitucion'),
    identificacion:   texto('p-identificacion'),
    naveId:           Number(naveId),
    porcentaje:       numero('p-porcentaje')
  };

  $('btn-propietario').disabled = true;

  try {
    const { estado, datos } = await api('/api/propietarios', { metodo: 'POST', cuerpo });

    if (estado === 201) {
      mostrar($('aviso-propietario'), 'libre',
        'Propietario registrado y vinculado a la nave.');
      form.reset();
      $('caja-pais').classList.add('oculto');
      await cargarPropietarios();
      return;
    }

    // 400 validacion, 403 nave ajena, 404 nave inexistente, 409 cuota o duplicado
    pintarErrores(datos, form);
    mostrar($('aviso-propietario'), 'ocupado',
      (datos && datos.mensaje) || 'No se pudo registrar el propietario.');

  } catch (err) {
    if (err.message !== 'sin sesion') {
      mostrar($('aviso-propietario'), 'ocupado', 'No se pudo contactar el servidor.');
    }
  } finally {
    $('btn-propietario').disabled = false;
  }
});

// ---------------------------------------------------------------
// HU-06 — agente residente
// ---------------------------------------------------------------
function actualizarPrecondicionAgente() {
  $('precondicion-agente').classList.toggle('oculto', tienePropietarios);
  $('campos-agente').disabled = !tienePropietarios;
  actualizarCamaraDocumentos();
}

async function cargarAgente() {
  const caja = $('agente-vigente');
  const { ok, estado, datos } = await api('/api/naves/' + naveId + '/agente-residente');

  if (estado === 404) {
    caja.innerHTML = '<p class="vacio">Esta nave no tiene agente residente designado.</p>';
    $('leyenda-agente').textContent = 'Designar agente residente';
    tieneAgente = false;
    actualizarCamaraDocumentos();
    await cargarHistorial();
    return;
  }

  if (!ok) {
    caja.innerHTML = '<p class="vacio">No se pudo consultar el agente residente.</p>';
    tieneAgente = false;
    actualizarCamaraDocumentos();
    return;
  }

  // Precondicion de HU-07: con agente vigente se abre la camara 5.
  tieneAgente = true;
  actualizarCamaraDocumentos();

  caja.innerHTML = `
    <table>
      <tr><th>Agente vigente</th><td>${escapar(datos.nombre)}</td></tr>
      ${datos.idoneidad ? `<tr><th>Idoneidad</th><td class="cifra">${escapar(datos.idoneidad)}</td></tr>` : ''}
      <tr><th>Contacto</th><td>${contactoAgente(datos)}</td></tr>
      ${datos.poderNumero ? `<tr><th>Poder</th><td>${escapar(datos.poderNumero)}
          ${datos.poderFecha ? ' del ' + datos.poderFecha : ''}
          ${datos.poderLugar ? '<br><span class="pista">' + escapar(datos.poderLugar) + '</span>' : ''}</td></tr>` : ''}
    </table>`;

  // Ya hay agente: el formulario pasa a ser un reemplazo.
  $('leyenda-agente').textContent = 'Reemplazar agente residente';

  await cargarHistorial();
}

/**
 * Issue #7: telefono y correo se unen solo si existen. Sin ninguno se dice,
 * en vez de dejar un punto suelto ("Contacto ·").
 */
function contactoAgente(agente) {
  const partes = [agente.telefono, agente.correo]
    .filter(v => v != null && String(v).trim() !== '')
    .map(escapar);
  return partes.length > 0
    ? partes.join(' &middot; ')
    : '<span class="pista">Sin datos de contacto</span>';
}

async function cargarHistorial() {
  const { ok, datos } = await api('/api/naves/' + naveId + '/agente-residente/historial');
  if (!ok || !Array.isArray(datos)) return;

  const anteriores = datos.filter(a => !a.vigente);
  $('caja-historial').classList.toggle('oculto', anteriores.length === 0);

  $('lista-historial').innerHTML = anteriores.map(a => `
    <tr>
      <td>${escapar(a.nombre)}</td>
      <td class="cifra">${a.idoneidad ? escapar(a.idoneidad) : '—'}</td>
      <td>${a.fechaDesignacion ? a.fechaDesignacion.substring(0, 10) : '—'}</td>
      <td><span class="etiqueta-historial">Reemplazado</span></td>
    </tr>`).join('');
}

$('form-agente').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = $('form-agente');
  limpiarErrores(form);
  ocultar($('aviso-agente'));

  const cuerpo = {
    nombre:      texto('a-nombre'),
    idoneidad:   texto('a-idoneidad'),
    telefono:    texto('a-telefono'),
    correo:      texto('a-correo'),
    poderNumero: texto('a-poderNumero'),
    poderFecha:  texto('a-poderFecha'),
    poderLugar:  texto('a-poderLugar')
  };

  $('btn-agente').disabled = true;

  try {
    const { estado, datos } = await api(
      '/api/naves/' + naveId + '/agente-residente', { metodo: 'POST', cuerpo });

    if (estado === 201) {
      mostrar($('aviso-agente'), 'libre',
        'Agente residente designado. La designación anterior, si la había, quedó reemplazada.');
      form.reset();
      await cargarAgente();
      return;
    }

    // 409 es la precondicion de HU-06: la nave no tiene propietarios.
    pintarErrores(datos, form);
    mostrar($('aviso-agente'), 'ocupado',
      (datos && datos.mensaje) || 'No se pudo designar el agente residente.');

  } catch (err) {
    if (err.message !== 'sin sesion') {
      mostrar($('aviso-agente'), 'ocupado', 'No se pudo contactar el servidor.');
    }
  } finally {
    $('btn-agente').disabled = false;
  }
});

// ---------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------
(async function iniciar() {
  pintarSesion();
  if (!(await cargarNave())) return;

  const rol = await rolDeSesion();
  if (rol === 'AGENTE_NAVIERO') {
    await cargarPropietarios();
    await cargarAgente();
    await cargarDocumentos();
  } else {
    vistaDeConsulta(rol);
  }
  await cargarTramite();
})();
