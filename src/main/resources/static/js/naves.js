/* Navesitas — pantalla de registro de nave.
   HU-03 (registro) con la compuerta de HU-04 (disponibilidad de nombre).

   Sprint 3: el formulario ya no pide propietario ni agente residente.
   Esos se agregan despues, desde el expediente de la nave. */

const campoNombre  = $('nombre');
const veredicto    = $('veredicto');
const btnConsultar = $('btn-consultar');
const camposNave   = $('campos-nave');
const formNave     = $('form-nave');
const avisoForm    = $('aviso-form');
const resultado    = $('resultado');

let nombreAprobado = null;   // ultimo nombre confirmado como disponible

// ---------------------------------------------------------------
// HU-04 — consulta de disponibilidad
// ---------------------------------------------------------------
async function consultarDisponibilidad() {
  const nombre = campoNombre.value;

  btnConsultar.disabled = true;
  mostrar(veredicto, 'neutro', 'Consultando el registro...');

  try {
    const { ok, datos } = await api(
      '/api/naves/disponibilidad?nombre=' + encodeURIComponent(nombre));

    if (!ok) {
      cerrarCompuerta((datos && datos.mensaje) || 'No se pudo consultar el nombre.');
      return;
    }

    if (datos.disponible) {
      nombreAprobado = nombre;
      camposNave.disabled = false;
      mostrar(veredicto, 'libre', datos.mensaje);
    } else {
      cerrarCompuerta(datos.mensaje);
    }
  } catch (e) {
    if (e.message !== 'sin sesion') {
      cerrarCompuerta('No se pudo contactar el servidor. Intente de nuevo.');
    }
  } finally {
    btnConsultar.disabled = false;
  }
}

function cerrarCompuerta(mensaje) {
  nombreAprobado = null;
  camposNave.disabled = true;
  mostrar(veredicto, 'ocupado', mensaje);
}

// Si el usuario edita el nombre despues de aprobarlo, la compuerta se cierra:
// no debe poder guardar con un nombre que nunca se consulto.
campoNombre.addEventListener('input', () => {
  if (nombreAprobado !== null && campoNombre.value !== nombreAprobado) {
    nombreAprobado = null;
    camposNave.disabled = true;
    mostrar(veredicto, 'neutro', 'El nombre cambió. Consulte la disponibilidad otra vez.');
  }
});

campoNombre.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); consultarDisponibilidad(); }
});

btnConsultar.addEventListener('click', consultarDisponibilidad);

// ---------------------------------------------------------------
// HU-03 — registro de nave
// ---------------------------------------------------------------
formNave.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores();
  ocultar(avisoForm);

  if (nombreAprobado === null) {
    mostrar(avisoForm, 'ocupado', 'Consulte la disponibilidad del nombre antes de guardar.');
    return;
  }

  const cuerpo = {
    nombre:            campoNombre.value.trim(),
    tipo:              texto('tipo'),
    servicio:          texto('servicio'),
    tonelajeBruto:     numero('tonelajeBruto'),
    tonelajeNeto:      numero('tonelajeNeto'),
    eslora:            numero('eslora'),
    manga:             numero('manga'),
    puntal:            numero('puntal'),
    anioConstruccion:  numero('anioConstruccion'),
    lugarConstruccion: texto('lugarConstruccion'),
    materialCasco:     texto('materialCasco'),
    tipoPropulsion:    texto('tipoPropulsion'),
    potenciaKw:        numero('potenciaKw')
  };

  $('btn-guardar').disabled = true;

  try {
    const { estado, datos } = await api('/api/naves', { metodo: 'POST', cuerpo });

    if (estado === 201) {
      mostrarResultado(datos);
      mostrar(avisoForm, 'libre',
        'La nave quedó guardada y su trámite quedó en Solicitud presentada. '
        + 'Ahora puede vincularle propietarios.');
      camposNave.disabled = true;
      nombreAprobado = null;
      await cargarConteo();
      await cargarNaves();
      return;
    }

    // 400: validacion de campos. 409: nombre en uso o tonelaje incoherente.
    pintarErrores(datos);
    mostrar(avisoForm, 'ocupado', (datos && datos.mensaje) || 'No se pudo guardar la nave.');

  } catch (err) {
    if (err.message !== 'sin sesion') {
      mostrar(avisoForm, 'ocupado', 'No se pudo contactar el servidor. Intente de nuevo.');
    }
  } finally {
    $('btn-guardar').disabled = false;
  }
});

function mostrarResultado(nave) {
  $('tabla-resultado').innerHTML = `
    <tr><th>Número de registro</th><td class="cifra">${nave.id}</td></tr>
    <tr><th>Nombre</th><td>${escapar(nave.nombre)}</td></tr>
    <tr><th>Tipo y servicio</th><td>${nave.tipo} &middot; ${nave.servicio}</td></tr>
    <tr><th>Tonelaje</th><td class="cifra">${nave.tonelajeBruto} TRB / ${nave.tonelajeNeto} TRN</td></tr>
    <tr><th>Dimensiones</th><td class="cifra">${nave.eslora} &times; ${nave.manga} &times; ${nave.puntal} m</td></tr>
    <tr><th>Estado de la nave</th><td><span class="estado-etiqueta">${escapar(nave.estado)}</span></td></tr>
    <tr><th>Estado del trámite</th><td>${etiquetaTramite(nave.estadoTramite)}</td></tr>`;
  $('enlace-expediente').href = '/expediente.html?naveId=' + nave.id;
  resultado.dataset.visible = 'si';
}

$('btn-limpiar').addEventListener('click', () => {
  formNave.reset();
  campoNombre.value = '';
  nombreAprobado = null;
  camposNave.disabled = true;
  limpiarErrores();
  ocultar(veredicto);
  ocultar(avisoForm);
  delete resultado.dataset.visible;
});

// ---------------------------------------------------------------
// Lista de naves propias, con el filtro de HU-10
// ---------------------------------------------------------------

/** Estado elegido en los chips; '' es "Todas". */
let filtroEstado = '';
let conteo = null;          // ultima respuesta de /api/naves/conteo-por-estado
let consultaNaves = 0;      // para descartar respuestas viejas si se pulsa rapido

async function cargarConteo() {
  try {
    const { ok, datos } = await api('/api/naves/conteo-por-estado');
    conteo = ok ? datos : null;
  } catch (e) {
    conteo = null;
  }
  pintarChips();
}

function pintarChips() {
  const caja = $('filtro-estado');
  if (!conteo || !conteo.estados) {
    caja.innerHTML = '';
    return;
  }
  const opciones = [['', 'Todas', conteo.total]].concat(
    Object.keys(NOMBRE_ESTADO_TRAMITE).map(e =>
      [e, NOMBRE_ESTADO_TRAMITE[e], conteo.estados[e] || 0]));

  caja.innerHTML = opciones.map(([estado, nombre, cantidad]) => `
    <button type="button" class="chip" data-estado="${estado}"
            aria-pressed="${estado === filtroEstado ? 'true' : 'false'}">
      ${escapar(nombre)} <span class="cifra">${cantidad}</span>
    </button>`).join('');
}

$('filtro-estado').addEventListener('click', async (e) => {
  const chip = e.target.closest('button[data-estado]');
  if (!chip || chip.dataset.estado === filtroEstado) return;
  filtroEstado = chip.dataset.estado;
  pintarChips();
  await cargarNaves();
});

async function cargarNaves() {
  const cuerpo = $('lista-naves');
  const sinResultados = $('sin-resultados');
  const caja = $('caja-lista-naves');
  const esta = ++consultaNaves;

  const ruta = filtroEstado
    ? '/api/naves?estado=' + encodeURIComponent(filtroEstado)
    : '/api/naves';

  try {
    const { ok, datos } = await api(ruta);
    if (esta !== consultaNaves) return;   // llego tarde: ya se pidio otro filtro

    sinResultados.classList.add('oculto');
    caja.classList.remove('oculto');

    if (!ok || !Array.isArray(datos)) {
      cuerpo.innerHTML = '<tr><td colspan="6">' + escapar((datos && datos.mensaje)
        || 'No se pudo cargar el registro.') + '</td></tr>';
      return;
    }

    if (datos.length === 0) {
      // HU-10: sin resultados se dice, en vez de dejar una tabla vacia.
      caja.classList.add('oculto');
      sinResultados.textContent = filtroEstado
        ? 'No tiene naves con el trámite en «' + NOMBRE_ESTADO_TRAMITE[filtroEstado]
          + '». Elija otro estado o «Todas».'
        : 'Todavía no ha registrado naves.';
      sinResultados.classList.remove('oculto');
      return;
    }

    cuerpo.innerHTML = datos.map(n => `
      <tr>
        <td>${escapar(n.nombre)}</td>
        <td>${escapar(n.tipo)}</td>
        <td class="cifra">${n.tonelajeBruto}</td>
        <td><span class="estado-etiqueta" data-estado="${escapar(n.estado)}">${escapar(n.estado)}</span></td>
        <td>${etiquetaTramite(n.estadoTramite)}</td>
        <td><a class="enlace-fila" href="/expediente.html?naveId=${n.id}">Abrir</a></td>
      </tr>`).join('');
  } catch (e) { /* api() ya redirigio si fue 401 */ }
}

pintarSesion();
cargarConteo();
cargarNaves();
