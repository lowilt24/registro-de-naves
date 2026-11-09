const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/constancia.js'), 'utf8');

function entorno(respuesta) {
  const elementos = new Map();
  const registro = { clics: 0, peticiones: 0, urls: [], revocadas: [] };
  const $ = id => {
    if (!elementos.has(id)) elementos.set(id, {
      disabled: false, oculto: true,
      classList: { toggle(_clase, oculto) { elementos.get(id).oculto = oculto; } },
      addEventListener(_evento, callback) { this.click = callback; }
    });
    return elementos.get(id);
  };
  const context = vm.createContext({ $, naveId: 123, location: { href: '' },
    ocultar(e) { e.mensaje = ''; }, mostrar(e, estado, mensaje) { e.estado = estado; e.mensaje = mensaje; },
    async fetch(url, opciones) { registro.peticiones++; registro.url = url; registro.opciones = opciones;
      if (respuesta instanceof Error) throw respuesta;
      return respuesta;
    },
    URL: { createObjectURL(blob) { registro.urls.push(blob); return 'blob:prueba'; }, revokeObjectURL(url) { registro.revocadas.push(url); } },
    document: { body: { appendChild(e) { registro.enlace = e; } }, createElement() {
      return { click() { registro.clics++; }, remove() { registro.retirado = true; } };
    } }, setTimeout(callback) { registro.liberar = callback; }
  });
  vm.runInContext(source, context);
  return { $, context, registro, click: () => $('btn-constancia').click() };
}
test('boton solo visible con tramite APROBADA', () => {
  const e = entorno();
  for (const estado of ['SOLICITUD_PRESENTADA','EN_REVISION','OBSERVADA','RECHAZADA','DESISTIDA',null]) {
    e.context.actualizarConstancia(estado); assert.equal(e.$('constancia-registro').oculto, true);
  }
  e.context.actualizarConstancia('APROBADA'); assert.equal(e.$('constancia-registro').oculto, false);
});
test('descarga PDF con sesion, nombre seguro y libera el recurso', async () => {
  const blob = { tipo: 'PDF de prueba' };
  const e = entorno({ ok: true, status: 200, headers: { get: () => 'application/pdf' }, blob: async () => blob });
  await e.click();
  assert.equal(e.registro.url, '/api/naves/123/constancia');
  assert.equal(e.registro.opciones.credentials, 'same-origin');
  assert.equal(e.registro.enlace.download, 'constancia-registro-123.pdf');
  assert.equal(e.registro.clics, 1); assert.equal(e.registro.retirado, true);
  e.registro.liberar(); assert.deepEqual(e.registro.revocadas, ['blob:prueba']);
  assert.equal(e.$('aviso-constancia').estado, 'libre'); assert.equal(e.$('btn-constancia').disabled, false);
});
test('sesion vencida redirige al login sin descargar', async () => {
  const e = entorno({ status: 401 }); await e.click();
  assert.equal(e.context.location.href, '/login.html?expirada=1'); assert.equal(e.registro.clics, 0);
});
test('403 y 409 muestran mensaje del servidor sin descargar JSON como PDF', async () => {
  for (const status of [403,409]) {
    const e = entorno({ ok: false, status, json: async () => ({ mensaje: 'Acceso o estado no permitido' }) });
    await e.click(); assert.equal(e.$('aviso-constancia').mensaje, 'Acceso o estado no permitido');
    assert.equal(e.registro.clics, 0); assert.equal(e.$('btn-constancia').disabled, false);
  }
});
test('fallo de red o contenido incorrecto muestra error y permite reintentar', async () => {
  for (const respuesta of [new Error('sin red'), { ok: true, headers: { get: () => 'text/html' } }]) {
    const e = entorno(respuesta); await e.click();
    assert.equal(e.$('aviso-constancia').estado, 'ocupado'); assert.equal(e.$('btn-constancia').disabled, false);
    assert.equal(e.registro.clics, 0);
  }
});
test('evita peticion duplicada mientras se descarga', async () => {
  const e = entorno(); e.$('btn-constancia').disabled = true; await e.click();
  assert.equal(e.registro.peticiones, 0);
});
