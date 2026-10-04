/* Navesitas — camara 5 del expediente: documentos de la nave.
   HU-07: carga de documentos.
   HU-08: consulta del estado de los documentos.

   Se carga antes que expediente.js. Aqui solo se definen funciones y se
   enganchan los eventos de la camara 5; expediente.js las llama al
   arrancar, cuando ya conoce la nave. naveId lo define expediente.js.

   Contrato con el backend: CONTRATO-DOCUMENTOS.md en la raiz del repo. */

/** Lista cerrada de HU-07. El orden es el del expediente. */
const TIPOS_DOCUMENTO = [
  { valor: 'CERTIFICADO_PROPIEDAD', nombre: 'Certificado de propiedad' },
  { valor: 'PODER_NOTARIAL',        nombre: 'Poder notarial' },
  { valor: 'ITC',                   nombre: 'Certificado de arqueo (ITC)' },
  { valor: 'SMC',                   nombre: 'Gestión de la seguridad (SMC)' }
];

const NOMBRE_ESTADO = {
  FALTANTE:  'Faltante',
  PENDIENTE: 'Pendiente',
  APROBADO:  'Aprobado',
  OBSERVADO: 'Observado'
};

/** 10 MB. El servidor es quien decide; aqui solo se evita subir de balde. */
const LIMITE_BYTES = 10 * 1024 * 1024;

let tieneAgente = false;     // lo actualiza expediente.js al cargar el agente
let versionesVigentes = {};  // tipo -> version vigente, para avisar del reemplazo
let consultaEnCurso = 0;     // descarta respuestas viejas si se cambian filtros rapido

function nombreTipo(valor) {
  const t = TIPOS_DOCUMENTO.find(t => t.valor === valor);
  return t ? t.nombre : valor;
}

function tamanoLegible(bytes) {
  if (bytes == null) return '—';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

/**
 * Fecha y hora en la zona de Panama, por ejemplo "6 oct 2026, 14:32".
 * El mes va en letras a proposito: con 06/10/2026 no se sabe si es
 * 6 de octubre o 10 de junio, y es-PA ordena mes/dia.
 */
function fechaHora(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (isNaN(d)) return escapar(iso);
  const zona = { timeZone: 'America/Panama' };
  const fecha = d.toLocaleDateString('es', { ...zona, day: 'numeric', month: 'short', year: 'numeric' })
    .replace('.', '');
  const hora = d.toLocaleTimeString('es', { ...zona, hour: '2-digit', minute: '2-digit', hour12: false });
  return fecha + ', ' + hora;
}

function etiquetaEstado(estado) {
  return '<span class="doc-estado" data-estado="' + estado + '">'
       + (NOMBRE_ESTADO[estado] || escapar(estado)) + '</span>';
}

// ---------------------------------------------------------------
// Estado de la camara: cerrada hasta que haya propietario y agente
// ---------------------------------------------------------------
function actualizarCamaraDocumentos() {
  const abierta = tienePropietarios && tieneAgente;
  $('campos-documento').disabled = !abierta;
}

// ---------------------------------------------------------------
// HU-08 — consulta
// ---------------------------------------------------------------
async function cargarDocumentos() {
  // Si el usuario cambia dos filtros seguidos salen dos consultas, y la
  // primera puede volver despues que la segunda. Solo se pinta la ultima.
  const turno = ++consultaEnCurso;
  const cuerpo = $('lista-documentos');
  const tipo = $('f-tipo').value;
  const estado = $('f-estado').value;
  const historial = $('f-historial').checked;

  // FALTANTE no existe en la base: se piden todos y se calcula aqui.
  const parametros = new URLSearchParams();
  if (tipo) parametros.set('tipo', tipo);
  if (estado && estado !== 'FALTANTE') parametros.set('estado', estado);
  if (historial) parametros.set('historial', 'true');

  const ruta = '/api/naves/' + naveId + '/documentos'
             + (parametros.toString() ? '?' + parametros : '');

  const { ok, datos } = await api(ruta);
  if (turno !== consultaEnCurso) return;
  if (!ok || !Array.isArray(datos)) {
    cuerpo.innerHTML = '<tr><td colspan="6" class="vacio">No se pudieron cargar los documentos.</td></tr>';
    ocultar($('resumen-documentos'));
    return;
  }

  // El resumen de faltantes siempre se calcula sobre el expediente
  // completo, no sobre lo filtrado: si no, filtrar por ITC haria creer
  // que solo falta el ITC.
  const completo = (tipo || estado || historial)
    ? await consultarVigentes()
    : datos;
  if (turno !== consultaEnCurso) return;
  pintarResumen(completo);

  const filas = [];
  const tiposConDocumento = new Set(datos.map(d => d.tipo));

  // Los cuatro tipos aparecen aunque no se hayan cargado (criterio 2 de HU-08).
  TIPOS_DOCUMENTO
    .filter(t => !tipo || t.valor === tipo)
    .forEach(t => {
      const delTipo = datos.filter(d => d.tipo === t.valor);
      if (estado !== 'FALTANTE') {
        delTipo.forEach(d => filas.push(filaDocumento(d)));
      }
      const falta = !completo.some(d => d.tipo === t.valor);
      if (falta && !tiposConDocumento.has(t.valor) && (!estado || estado === 'FALTANTE')) {
        filas.push(filaFaltante(t));
      }
    });

  cuerpo.innerHTML = filas.length
    ? filas.join('')
    : '<tr><td colspan="6" class="vacio">Ningún documento coincide con el filtro.</td></tr>';
}

/** Versiones vigentes de todos los tipos, sin filtros. */
async function consultarVigentes() {
  const { ok, datos } = await api('/api/naves/' + naveId + '/documentos');
  return ok && Array.isArray(datos) ? datos : [];
}

function filaDocumento(d) {
  const version = 'v' + d.version
    + (d.vigente === false ? ' <span class="etiqueta-historial">Anterior</span>' : '');
  const descarga = '/api/naves/' + naveId + '/documentos/' + d.id + '/archivo';
  return `
    <tr${d.vigente === false ? ' class="fila-anterior"' : ''}>
      <td>${escapar(nombreTipo(d.tipo))}</td>
      <td><div class="archivo">${escapar(d.nombreArchivo)}<span class="pista cifra">${tamanoLegible(d.tamanoBytes)}</span></div></td>
      <td><span class="cifra">${version}</span></td>
      <td><span class="cifra fecha">${fechaHora(d.creadoEn)}</span></td>
      <td>${etiquetaEstado(d.estado)}</td>
      <td><a class="enlace-fila" href="${descarga}" download>Descargar</a></td>
    </tr>`;
}

function filaFaltante(t) {
  return `
    <tr class="fila-faltante">
      <td>${escapar(t.nombre)}</td>
      <td>—</td>
      <td>—</td>
      <td>—</td>
      <td>${etiquetaEstado('FALTANTE')}</td>
      <td><a class="enlace-fila" href="#form-documento" data-cargar="${t.valor}">Cargar</a></td>
    </tr>`;
}

/**
 * El "para saber que falta" de HU-08. Tres casos, cada uno con su icono
 * y su texto: faltan documentos (neutro), hay observados (alto), o el
 * expediente documental esta completo (siga).
 */
function pintarResumen(vigentes) {
  const aviso = $('resumen-documentos');
  const cargados = new Set(vigentes.map(d => d.tipo));
  const faltan = TIPOS_DOCUMENTO.filter(t => !cargados.has(t.valor));
  const observados = vigentes.filter(d => d.estado === 'OBSERVADO');

  versionesVigentes = {};
  vigentes.forEach(d => { versionesVigentes[d.tipo] = d.version; });

  $('contador-documentos').textContent =
    (TIPOS_DOCUMENTO.length - faltan.length) + ' de ' + TIPOS_DOCUMENTO.length + ' cargados';

  // La placa pasa a azul (camara cruzada) cuando no falta ninguno.
  $('seccion-documentos').dataset.completo = faltan.length === 0 ? 'si' : 'no';

  const textoFaltan = faltan.length
    ? (faltan.length === 1 ? 'Falta 1' : 'Faltan ' + faltan.length)
      + ' de ' + TIPOS_DOCUMENTO.length + ' documentos: '
      + faltan.map(t => t.nombre).join(', ') + '.'
    : '';

  if (observados.length) {
    // Lo observado manda (alto), pero sin esconder lo que falta.
    const textoObservados = (observados.length === 1
        ? 'Hay 1 documento observado: '
        : 'Hay ' + observados.length + ' documentos observados: ')
      + observados.map(d => nombreTipo(d.tipo)).join(', ')
      + '. Cárguelo de nuevo con las correcciones.';
    mostrar(aviso, 'ocupado', textoObservados + (textoFaltan ? ' ' + textoFaltan : ''));
  } else if (faltan.length) {
    mostrar(aviso, 'neutro', textoFaltan);
  } else {
    mostrar(aviso, 'libre', 'Los cuatro documentos del expediente están cargados.');
  }

  avisarVersion();
}

// Filtros
['f-tipo', 'f-estado', 'f-historial'].forEach(id =>
  $(id).addEventListener('change', () => cargarDocumentos()));

// "Cargar" en una fila faltante: preselecciona el tipo y lleva al formulario.
$('lista-documentos').addEventListener('click', (e) => {
  const enlace = e.target.closest('[data-cargar]');
  if (!enlace) return;
  e.preventDefault();
  if ($('campos-documento').disabled) {
    $('cerrojo-documentos').scrollIntoView({ behavior: 'smooth', block: 'center' });
    return;
  }
  $('d-tipo').value = enlace.dataset.cargar;
  avisarVersion();
  $('d-archivo').focus();
});

// ---------------------------------------------------------------
// HU-07 — carga
// ---------------------------------------------------------------

/** Si ya hay un documento de ese tipo, avisa que la carga crea una version nueva. */
function avisarVersion() {
  const nota = $('nota-version');
  const actual = versionesVigentes[$('d-tipo').value];
  nota.textContent = actual
    ? 'Ya hay versión ' + actual + '. Esta carga crea la versión ' + (actual + 1)
      + ' y la anterior queda en el historial.'
    : '';
}
$('d-tipo').addEventListener('change', avisarVersion);

/** Mensaje de respaldo cuando el servidor no manda JSON (por ejemplo un 413 de un proxy). */
const MENSAJE_POR_CODIGO = {
  403: 'Esta nave pertenece a otro agente.',
  404: 'La nave ya no existe.',
  409: 'La nave necesita al menos un propietario y un agente residente designado.',
  413: 'El archivo supera el límite de 10 MB.',
  415: 'El archivo no es un PDF válido.'
};

$('form-documento').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = $('form-documento');
  limpiarErrores(form);
  ocultar($('aviso-documento'));

  const tipo = $('d-tipo').value;
  const archivo = $('d-archivo').files[0];

  // Comprobaciones de comodidad: evitan subir 10 MB para nada. La
  // validacion que cuenta (firma %PDF-, tamano, propiedad) es la del
  // servidor, y es la que prueban Katalon y Wfuzz.
  let falla = false;
  if (!tipo) {
    form.querySelector('[data-error="tipo"]').textContent = 'Seleccione el tipo de documento.';
    falla = true;
  }
  if (!archivo) {
    form.querySelector('[data-error="archivo"]').textContent = 'Seleccione el archivo PDF.';
    falla = true;
  } else if (archivo.size > LIMITE_BYTES) {
    form.querySelector('[data-error="archivo"]').textContent =
      'El archivo supera el límite de 10 MB.';
    falla = true;
  }
  if (falla) {
    mostrar($('aviso-documento'), 'ocupado', 'Revise los campos marcados.');
    return;
  }

  const datosFormulario = new FormData();
  datosFormulario.append('tipo', tipo);
  datosFormulario.append('archivo', archivo);

  $('btn-documento').disabled = true;
  mostrar($('aviso-documento'), 'neutro', 'Cargando ' + archivo.name + '…');

  try {
    const { estado, datos } = await api('/api/naves/' + naveId + '/documentos',
      { metodo: 'POST', formulario: datosFormulario });

    if (estado === 201) {
      mostrar($('aviso-documento'), 'libre',
        nombreTipo(datos.tipo) + ' cargado como versión ' + datos.version
        + '. Queda pendiente de revisión.');
      form.reset();
      avisarVersion();
      await cargarDocumentos();
      return;
    }

    pintarErrores(datos, form);
    mostrar($('aviso-documento'), 'ocupado',
      (datos && datos.mensaje) || MENSAJE_POR_CODIGO[estado] || 'No se pudo cargar el documento.');

  } catch (err) {
    if (err.message !== 'sin sesion') {
      mostrar($('aviso-documento'), 'ocupado', 'No se pudo contactar el servidor.');
    }
  } finally {
    $('btn-documento').disabled = false;
  }
});
