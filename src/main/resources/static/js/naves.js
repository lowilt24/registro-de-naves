/* Navesitas — Sprint 2. Pantalla de registro de nave (HU-03) con la
   compuerta de disponibilidad de nombre (HU-04).

   Los id de los elementos son el contrato con los casos de Katalon:
   si cambian aqui, hay que actualizar el Object Repository. */

const $ = (id) => document.getElementById(id);

const campoNombre   = $('nombre');
const veredicto     = $('veredicto');
const btnConsultar  = $('btn-consultar');
const camposNave    = $('campos-nave');
const formNave      = $('form-nave');
const avisoForm     = $('aviso-form');
const resultado     = $('resultado');

let nombreAprobado = null;   // ultimo nombre confirmado como disponible

// ---------------------------------------------------------------
// HU-04 — consulta de disponibilidad
// ---------------------------------------------------------------
async function consultarDisponibilidad() {
  const nombre = campoNombre.value;

  btnConsultar.disabled = true;
  mostrar(veredicto, 'neutro', 'Consultando el registro...');

  try {
    const r = await fetch('/api/naves/disponibilidad?nombre=' + encodeURIComponent(nombre));
    const datos = await r.json();

    if (!r.ok) {
      cerrarCompuerta(datos.mensaje || 'No se pudo consultar el nombre.');
      return;
    }

    if (datos.disponible) {
      abrirCompuerta(datos.mensaje, nombre);
    } else {
      cerrarCompuerta(datos.mensaje);
    }
  } catch (e) {
    cerrarCompuerta('No se pudo contactar el servidor. Intente de nuevo.');
  } finally {
    btnConsultar.disabled = false;
  }
}

function abrirCompuerta(mensaje, nombre) {
  nombreAprobado = nombre;
  camposNave.disabled = false;
  mostrar(veredicto, 'libre', mensaje);
}

function cerrarCompuerta(mensaje) {
  nombreAprobado = null;
  camposNave.disabled = true;
  mostrar(veredicto, 'ocupado', mensaje);
}

function mostrar(elemento, estado, texto) {
  elemento.dataset.estado = estado;
  elemento.textContent = texto;
}

function ocultar(elemento) {
  delete elemento.dataset.estado;
  elemento.textContent = '';
}

// Si el usuario edita el nombre despues de aprobarlo, la compuerta se cierra:
// no debe poder guardar con un nombre que nunca se consulto.
campoNombre.addEventListener('input', () => {
  if (nombreAprobado !== null && campoNombre.value !== nombreAprobado) {
    nombreAprobado = null;
    camposNave.disabled = true;
    mostrar(veredicto, 'neutro', 'El nombre cambio. Consulte la disponibilidad otra vez.');
  }
});

campoNombre.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') { e.preventDefault(); consultarDisponibilidad(); }
});

btnConsultar.addEventListener('click', consultarDisponibilidad);

// ---------------------------------------------------------------
// HU-03 — registro de nave
// ---------------------------------------------------------------
function limpiarErrores() {
  document.querySelectorAll('[data-error]').forEach(s => s.textContent = '');
  ocultar(avisoForm);
}

function valorNumero(id) {
  const v = $(id).value.trim();
  return v === '' ? null : Number(v);
}

function valorTexto(id) {
  const v = $(id).value.trim();
  return v === '' ? null : v;
}

formNave.addEventListener('submit', async (e) => {
  e.preventDefault();
  limpiarErrores();

  if (nombreAprobado === null) {
    mostrar(avisoForm, 'ocupado', 'Consulte la disponibilidad del nombre antes de guardar.');
    return;
  }

  const cuerpo = {
    nombre:             campoNombre.value.trim(),
    tipo:               valorTexto('tipo'),
    servicio:           valorTexto('servicio'),
    tonelajeBruto:      valorNumero('tonelajeBruto'),
    tonelajeNeto:       valorNumero('tonelajeNeto'),
    eslora:             valorNumero('eslora'),
    manga:              valorNumero('manga'),
    puntal:             valorNumero('puntal'),
    anioConstruccion:   valorNumero('anioConstruccion'),
    lugarConstruccion:  valorTexto('lugarConstruccion'),
    materialCasco:      valorTexto('materialCasco'),
    tipoPropulsion:     valorTexto('tipoPropulsion'),
    potenciaKw:         valorNumero('potenciaKw'),
    propietarioId:      valorNumero('propietarioId'),
    agenteResidenteId:  valorNumero('agenteResidenteId')
  };

  $('btn-guardar').disabled = true;

  try {
    const r = await fetch('/api/naves', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(cuerpo)
    });
    const datos = await r.json();

    if (r.status === 201) {
      mostrarResultado(datos);
      mostrar(avisoForm, 'libre', 'La nave quedo guardada con estado REGISTRADA.');
      camposNave.disabled = true;
      nombreAprobado = null;
      await cargarNaves();
      return;
    }

    // 400: fallo la validacion de campos. 409: regla de negocio (nombre en uso).
    if (datos.campos) {
      Object.entries(datos.campos).forEach(([campo, mensaje]) => {
        const destino = document.querySelector('[data-error="' + campo + '"]');
        if (destino) destino.textContent = mensaje;
      });
    }
    mostrar(avisoForm, 'ocupado', datos.mensaje || 'No se pudo guardar la nave.');

  } catch (err) {
    mostrar(avisoForm, 'ocupado', 'No se pudo contactar el servidor. Intente de nuevo.');
  } finally {
    $('btn-guardar').disabled = false;
  }
});

function mostrarResultado(nave) {
  $('tabla-resultado').innerHTML = `
    <tr><th>Numero de registro</th><td class="cifra">${nave.id}</td></tr>
    <tr><th>Nombre</th><td>${escapar(nave.nombre)}</td></tr>
    <tr><th>Tipo y servicio</th><td>${nave.tipo} &middot; ${nave.servicio}</td></tr>
    <tr><th>Tonelaje</th><td class="cifra">${nave.tonelajeBruto} TRB / ${nave.tonelajeNeto} TRN</td></tr>
    <tr><th>Dimensiones</th><td class="cifra">${nave.eslora} &times; ${nave.manga} &times; ${nave.puntal} m</td></tr>
    <tr><th>Propietario</th><td>${escapar(nave.propietario)}</td></tr>
    <tr><th>Agente residente</th><td>${escapar(nave.agenteResidente)}</td></tr>
    <tr><th>Estado</th><td><span class="estado-etiqueta">${nave.estado}</span></td></tr>`;
  resultado.dataset.visible = 'si';
}

$('btn-limpiar').addEventListener('click', () => {
  formNave.reset();
  campoNombre.value = '';
  nombreAprobado = null;
  camposNave.disabled = true;
  limpiarErrores();
  ocultar(veredicto);
  delete resultado.dataset.visible;
});

// ---------------------------------------------------------------
// Carga inicial
// ---------------------------------------------------------------
function escapar(texto) {
  const d = document.createElement('div');
  d.textContent = texto == null ? '' : texto;
  return d.innerHTML;
}

async function llenarSelector(idSelector, url, etiqueta) {
  const selector = $(idSelector);
  try {
    const datos = await (await fetch(url)).json();
    selector.innerHTML = '<option value="">Seleccione</option>';
    datos.forEach(d => {
      const o = document.createElement('option');
      o.value = d.id;
      o.textContent = d.nombre + ' (' + d[etiqueta] + ')';
      selector.appendChild(o);
    });
  } catch (e) {
    selector.innerHTML = '<option value="">No se pudo cargar la lista</option>';
  }
}

async function cargarNaves() {
  const cuerpo = $('lista-naves');
  try {
    const naves = await (await fetch('/api/naves')).json();
    if (naves.length === 0) {
      cuerpo.innerHTML = '<tr><td colspan="4">Todavia no hay naves registradas.</td></tr>';
      return;
    }
    cuerpo.innerHTML = naves.map(n => `
      <tr>
        <td>${escapar(n.nombre)}</td>
        <td>${n.tipo}</td>
        <td class="cifra">${n.tonelajeBruto}</td>
        <td><span class="estado-etiqueta">${n.estado}</span></td>
      </tr>`).join('');
  } catch (e) {
    cuerpo.innerHTML = '<tr><td colspan="4">No se pudo cargar el registro.</td></tr>';
  }
}

async function mostrarSesion() {
  try {
    const s = await (await fetch('/api/auth/sesion')).json();
    $('sesion').innerHTML = s.autenticado
      ? escapar(s.nombreCompleto) + ' &middot; <a href="/login.html">Salir</a>'
      : '<a href="/login.html">Iniciar sesion</a>';
  } catch (e) { /* la barra sin sesion no bloquea el registro */ }
}

llenarSelector('propietarioId', '/api/propietarios', 'identificacion');
llenarSelector('agenteResidenteId', '/api/agentes-residentes', 'idoneidad');
cargarNaves();
mostrarSesion();
