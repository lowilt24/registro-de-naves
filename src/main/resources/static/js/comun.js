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

/** Lee el valor de una cookie por su nombre (SEC-05). */
function getCookie(nombre) {
  const match = document.cookie.match(new RegExp('(^| )' + nombre + '=([^;]+)'));
  return match ? match[2] : null;
}

/**
 * Llama a la API y normaliza el resultado.
 * Si el servidor responde 401 manda al login: la sesion vencio o nunca
 * existio, y no tiene sentido seguir mostrando la pantalla.
 */
async function api(ruta, opciones = {}) {
  const metodo = opciones.metodo || 'GET';
  const cabeceras = opciones.cuerpo ? { 'Content-Type': 'application/json' } : {};

  // SEC-05: Inyectar token CSRF para peticiones mutables
  if (metodo !== 'GET' && metodo !== 'HEAD') {
    const csrfToken = getCookie('XSRF-TOKEN');
    if (csrfToken) cabeceras['X-XSRF-TOKEN'] = csrfToken;
  }

  // Sprint 4: opciones.formulario lleva un FormData (carga de archivos).
  // No se fija Content-Type a mano: el navegador lo pone con el boundary.
  const respuesta = await fetch(ruta, {
    headers: cabeceras,
    method: metodo,
    body: opciones.formulario
      ? opciones.formulario
      : (opciones.cuerpo ? JSON.stringify(opciones.cuerpo) : undefined)
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
        
        // SEC-05: Inyectar token CSRF como input oculto en el formulario de salida
        const csrfToken = getCookie('XSRF-TOKEN');
        if (csrfToken) {
          const input = document.createElement('input');
          input.type = 'hidden';
          input.name = '_csrf';
          input.value = csrfToken;
          f.appendChild(input);
        }
        
        document.body.appendChild(f);
        f.submit();
      });
    } else {
      caja.innerHTML = '<a href="/login.html">Iniciar sesión</a>';
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

// ---------------------------------------------------------------
// Sprint 5 — tramite (HU-09, HU-10). Lo usan naves.js y tramite.js.
// ---------------------------------------------------------------

/** Nombre visible de cada estado del tramite, en el orden de la maquina. */
const NOMBRE_ESTADO_TRAMITE = {
  SOLICITUD_PRESENTADA: 'Solicitud presentada',
  EN_REVISION:          'En revisión',
  OBSERVADA:            'Observada',
  APROBADA:             'Aprobada',
  RECHAZADA:            'Rechazada',
  DESISTIDA:            'Desistida'
};

/**
 * Etiqueta del estado del tramite. Siempre con texto; es otra etiqueta que
 * la del estado de la nave (estado-etiqueta) para no confundirlas (P6).
 */
function etiquetaTramite(estado) {
  if (!estado) return '<span class="pista">Sin trámite</span>';
  return '<span class="tramite-estado" data-estado="' + escapar(estado) + '">'
       + escapar(NOMBRE_ESTADO_TRAMITE[estado] || estado) + '</span>';
}