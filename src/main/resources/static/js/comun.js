/* Navesitas — utilidades compartidas por las pantallas.
   Todo endpoint exige sesion desde el Sprint 3, asi que aqui vive el
   manejo de 401 y el formato de errores del backend. */

const $ = (id) => document.getElementById(id);

/** Escapa texto antes de meterlo en innerHTML. */
function escapar(texto) {
  const d = document.createElement('div');
  d.textContent = texto == null ? '' : texto;
  return d.innerHTML;
}

/**
 * Llama a la API y normaliza el resultado.
 * Si el servidor responde 401 manda al login: la sesion vencio o nunca
 * existio, y no tiene sentido seguir mostrando la pantalla.
 */
async function api(ruta, opciones = {}) {
  const respuesta = await fetch(ruta, {
    headers: opciones.cuerpo ? { 'Content-Type': 'application/json' } : {},
    method: opciones.metodo || 'GET',
    body: opciones.cuerpo ? JSON.stringify(opciones.cuerpo) : undefined
  });

  if (respuesta.status === 401) {
    location.href = '/login.html?expirada=1';
    throw new Error('sin sesion');
  }

  // 204 y respuestas vacias no traen JSON.
  let datos = null;
  const texto = await respuesta.text();
  if (texto) {
    try { datos = JSON.parse(texto); } catch (e) { datos = null; }
  }

  return { ok: respuesta.ok, estado: respuesta.status, datos };
}

/** Pinta un aviso con su color segun el estado. */
function mostrar(elemento, estado, texto) {
  elemento.dataset.estado = estado;
  elemento.textContent = texto;
}

function ocultar(elemento) {
  delete elemento.dataset.estado;
  elemento.textContent = '';
}

/** Limpia los mensajes de error de todos los campos de un contenedor. */
function limpiarErrores(contenedor = document) {
  contenedor.querySelectorAll('[data-error]').forEach(s => s.textContent = '');
}

/**
 * Coloca los errores que devuelve el backend debajo de cada campo.
 * El backend responde { mensaje, campos: { campo: texto } } tanto para la
 * validacion automatica como para las reglas condicionales.
 */
function pintarErrores(datos, contenedor = document) {
  if (!datos || !datos.campos) return;
  Object.entries(datos.campos).forEach(([campo, texto]) => {
    const destino = contenedor.querySelector('[data-error="' + campo + '"]');
    if (destino) destino.textContent = texto;
  });
}

/** Barra superior: quien esta conectado y como salir. */
async function pintarSesion() {
  const caja = $('sesion');
  if (!caja) return;
  try {
    const { datos } = await api('/api/auth/sesion');
    if (datos && datos.autenticado) {
      caja.innerHTML = escapar(datos.nombreCompleto)
        + ' &middot; <a href="#" id="btn-salir">Salir</a>';
      $('btn-salir').addEventListener('click', async (e) => {
        e.preventDefault();
        const f = document.createElement('form');
        f.method = 'post';
        f.action = '/api/auth/logout';
        document.body.appendChild(f);
        f.submit();
      });
    } else {
      caja.innerHTML = '<a href="/login.html">Iniciar sesion</a>';
    }
  } catch (e) { /* api() ya redirigio */ }
}

/** Lee un parametro de la direccion, por ejemplo ?naveId=7 */
function parametro(nombre) {
  return new URLSearchParams(location.search).get(nombre);
}

/** Valor de un campo de texto, o null si esta vacio. */
function texto(id) {
  const v = $(id).value.trim();
  return v === '' ? null : v;
}

/** Valor de un campo numerico, o null si esta vacio. */
function numero(id) {
  const v = $(id).value.trim();
  return v === '' ? null : Number(v);
}
