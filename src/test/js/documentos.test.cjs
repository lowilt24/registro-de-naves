const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const tipos = ['CERTIFICADO_PROPIEDAD', 'PODER_NOTARIAL', 'ITC', 'SMC'];

function pantalla(estados = ['OBLIGATORIO','OBLIGATORIO','NO_APLICA','NO_APLICA'], docs = [], falla = false) {
  const elementos = new Map();
  const el = id => {
    if (!elementos.has(id)) elementos.set(id, {
      value:'', checked:false, innerHTML:'', textContent:'', dataset:{},
      options: tipos.map(value => ({value})), addEventListener() {}, classList:{add(){},remove(){},toggle(){}}
    });
    return elementos.get(id);
  };
  const requisitos = estados.map((estado,i) => ({tipo:tipos[i], estado, explicacion:'Decisión backend '+i}));
  const contexto = vm.createContext({
    $:el, naveId:7, URLSearchParams,
    escapar: s => String(s ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;'),
    mostrar:(e,estado,texto) => { e.dataset.estado=estado; e.textContent=texto; },
    ocultar:e => { e.textContent=''; },
    api:async ruta => ruta.endsWith('/requisitos') ? (falla ? {ok:false} : {ok:true,datos:requisitos}) : {ok:true,datos:docs}
  });
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/documentos.js','utf8'),contexto);
  return {el, contexto, ejecutar: expresion => vm.runInContext(expresion, contexto)};
}

test('ausencia de certificados no aplicables no marca faltantes ni solicita su carga', async () => {
  const p=pantalla(undefined, tipos.slice(0,2).map(tipo=>({tipo,estado:'PENDIENTE'})));
  await p.ejecutar('cargarDocumentos()');
  assert.equal(p.el('seccion-documentos').dataset.completo,'si');
  assert.match(p.el('contador-documentos').textContent,/2 de 2/);
  assert.match(p.el('lista-documentos').innerHTML,/No aplica/);
  assert.doesNotMatch(p.el('lista-documentos').innerHTML,/Faltante|data-cargar="ITC"|data-cargar="SMC"/);
  assert.equal(p.el('d-tipo').options.find(o=>o.value==='SMC').disabled,true);
});

test('pendientes se muestran con explicación y no declaran completo aunque existan cuatro PDF', async () => {
  const p=pantalla(['OBLIGATORIO','OBLIGATORIO','REQUIERE_REVISION','REQUIERE_REVISION'],tipos.map(tipo=>({tipo,estado:'APROBADO'})));
  await p.ejecutar('cargarDocumentos()');
  assert.equal(p.el('seccion-documentos').dataset.completo,'no');
  assert.match(p.el('resumen-documentos').textContent,/No se puede admitir, subsanar ni aprobar/);
  assert.match(p.el('requisitos-documentales').innerHTML,/Requiere revisión.*Decisión backend/);
});

test('se consume la decisión recibida, incluido ITC obligatorio y SMC exento', async () => {
  const p=pantalla(['OBLIGATORIO','OBLIGATORIO','OBLIGATORIO','NO_APLICA'],tipos.slice(0,2).map(tipo=>({tipo,estado:'APROBADO'})));
  await p.ejecutar('cargarDocumentos()');
  assert.equal(p.el('seccion-documentos').dataset.completo,'no');
  assert.match(p.el('resumen-documentos').textContent,/ITC/);
  assert.doesNotMatch(p.el('resumen-documentos').textContent,/SMC/);
  assert.match(p.el('lista-documentos').innerHTML,/data-cargar="ITC"/);
});

test('certificado cargado no aplicable conserva versión, descarga y estado documental', async () => {
  const p=pantalla(undefined,[{tipo:'ITC',id:91,version:2,vigente:false,estado:'APROBADO',nombreArchivo:'itc.pdf'}]);
  await p.ejecutar('cargarDocumentos()');
  assert.match(p.el('lista-documentos').innerHTML,/No aplica/);
  assert.match(p.el('lista-documentos').innerHTML,/v2/);
  assert.match(p.el('lista-documentos').innerHTML,/Anterior/);
  assert.match(p.el('lista-documentos').innerHTML,/documentos\/91\/archivo/);
  assert.match(p.el('lista-documentos').innerHTML,/Aprobado/);
});

test('fallo de evaluación elimina cualquier indicación anterior de expediente completo', async () => {
  const p=pantalla(undefined,[],true);
  p.el('seccion-documentos').dataset.completo='si';
  await p.ejecutar('cargarDocumentos()');
  assert.equal(p.el('seccion-documentos').dataset.completo,'no');
  assert.match(p.el('requisitos-documentales').textContent,/No se pudieron evaluar/);
});

test('evaluación vacía o incompleta no permite declarar completo el expediente', async () => {
  const p=pantalla([]);
  await p.ejecutar('cargarDocumentos()');
  assert.equal(p.el('seccion-documentos').dataset.completo,'no');
  assert.equal(p.el('contador-documentos').textContent,'Sin evaluación');
});
