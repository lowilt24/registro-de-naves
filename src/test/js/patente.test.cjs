const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function entorno(datos) {
  const elementos = new Map();
  const $ = id => {
    if (!elementos.has(id)) elementos.set(id, {innerHTML:'',textContent:'',eventos:{},setAttribute(){},querySelectorAll(){return [];},addEventListener(t,f){this.eventos[t]=f;}});
    return elementos.get(id);
  };
  const control = {respuesta:{ok:true,datos},llamadas:[],descargas:0};
  const escapar = t => String(t ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
  const contexto = vm.createContext({$,escapar,parametro:()=> '7',encodeURIComponent,pintarSesion(){},
    mostrar(e,estado,texto){e.estado=estado;e.textContent=texto;},document:{querySelector(){return null;},body:{appendChild(){}},createElement(){return {click(){control.descargas++;},remove(){}};}},
    location:{href:''},URL:{createObjectURL:()=> 'blob:pdf',revokeObjectURL(){}},setTimeout:fn=>fn(),
    fetch:async()=>control.pdf,
    FormData: class {constructor(form){this.form=form;}*[Symbol.iterator](){yield* Object.entries(this.form.valores || {});}},
    async api(ruta,opciones){control.llamadas.push({ruta,opciones});return control.respuesta;}});
  vm.runInContext(fs.readFileSync('src/main/resources/static/js/patente-provisional.js','utf8'),contexto);
  return {$,control,contexto,listo:()=>new Promise(setImmediate)};
}
function datos(rol='AGENTE_NAVIERO',estado='BORRADOR') {
  return {naveId:7,nombreNave:'Océano <script>',rol,solicitud:{id:2,estado,procedencia:'CONSTRUCCION',requisitosVersion:2,requisitosConfirmados:true,ultimoEnvioId:9},
    requisitos:[{id:1,nombre:'Documento <img onerror=1>',clase:'BASE',motivo:'Motivo',evidenciaId:3,version:1,nombreArchivo:'uno.pdf',hashSha256:'a'.repeat(64)}],envios:[],historial:[],documentosReutilizables:[],emision:null,
    acciones:rol==='AGENTE_NAVIERO'?['CARGAR','ENVIAR']:rol==='AUDITOR'?[]:['CONFIGURAR','CONFIRMAR_REQUISITOS']};
}
test('patente escapa texto y conserva nombre Registrada', async()=> {
  const e=entorno(datos());await e.listo();const html=e.$('contenido-patente').innerHTML;
  assert.ok(html.includes('&lt;img onerror=1&gt;'));assert.ok(!html.includes('<img'));assert.ok(html.includes('data-pp="cargar"'));
  assert.equal(e.$('titulo-patente').textContent,'Patente provisional: Océano <script>');
});
test('auditor solo consulta y descarga sin formularios',async()=> {
  const e=entorno(datos('AUDITOR'));await e.listo();const html=e.$('contenido-patente').innerHTML;
  assert.ok(!html.includes('<form'));assert.ok(html.includes('/evidencias/3/archivo'));assert.ok(html.includes('Vista del auditor'));
});
test('sin confirmación DGMM no se habilita envío',async()=> {
  const d=datos();d.solicitud.requisitosConfirmados=false;const e=entorno(d);await e.listo();
  assert.match(e.$('contenido-patente').innerHTML, /<button disabled>Enviar a revisión DGMM/);
});
test('subsanación permite corregir otra vez solo la evidencia originalmente observada',async()=> {
  const d=datos('AGENTE_NAVIERO','SUBSANACION');d.envios=[{numero:1,fecha:'hoy',evidencias:[{requisitoId:1,evidenciaId:2,nombre:'Anterior',decision:'OBSERVADA'}]}];
  const e=entorno(d);await e.listo();assert.ok(e.$('contenido-patente').innerHTML.includes('data-pp="cargar"'));
  d.envios[0].evidencias[0].decision='APROBADA';e.contexto.pintarPatente();assert.ok(!e.$('contenido-patente').innerHTML.includes('data-pp="cargar"'));
});
test('revisión envía contexto del envío y no cambia formulario antes del éxito',async()=> {
  const d=datos('FUNCIONARIO_DGMM','EN_REVISION');d.acciones=['REVISAR','APROBAR','DEVOLVER'];const e=entorno(d);await e.listo();
  const form={dataset:{pp:'revision',eid:'3'},valores:{decision:'OBSERVAR',observacion:'Falta firma'},reportValidity:()=>true};
  await e.$('contenido-patente').eventos.submit({target:{closest:()=>form},preventDefault(){}});
  const llamada=e.control.llamadas.at(-1);assert.equal(llamada.opciones.cuerpo.envioId,9);assert.equal(llamada.opciones.cuerpo.decision,'OBSERVAR');
  assert.ok(llamada.ruta.endsWith('/evidencias/3/revision'));
});
test('respuesta 403 elimina datos y acciones anteriores',async()=> {
  const e=entorno(datos());await e.listo();e.control.respuesta={ok:false,datos:{mensaje:'No autorizado'}};
  await assert.rejects(()=>e.contexto.cargarPatente(),/No autorizado/);assert.equal(e.$('contenido-patente').innerHTML,'');
});
test('409 recarga la solicitud antes de volver a permitir acciones',async()=> {
  const e=entorno(datos());await e.listo();e.control.respuesta={ok:false,estado:409,datos:{mensaje:'El envío cambió'}};
  const form={dataset:{pp:'enviar'},valores:{},reportValidity:()=>true};
  await e.$('contenido-patente').eventos.submit({target:{closest:()=>form},preventDefault(){}});
  assert.equal(e.control.llamadas.at(-1).opciones,undefined);assert.equal(e.$('contenido-patente').innerHTML,'');
});
function enlace() {const a={href:'/api/naves/7/patente-provisional/evidencias/3/archivo',atributos:{},getAttribute(n){return this.atributos[n];},setAttribute(n,v){this.atributos[n]=v;},removeAttribute(n){delete this.atributos[n];}};return a;}
test('descarga rechazada muestra mensaje, permite reintento y no guarda JSON',async()=> {
  const e=entorno(datos());await e.listo();e.control.pdf={ok:false,status:409,json:async()=>({mensaje:'Hash no coincide'})};const a=enlace();
  await e.$('contenido-patente').eventos.click({target:{closest:()=>a},preventDefault(){}});
  assert.equal(e.control.descargas,0);assert.equal(e.$('aviso-patente').textContent,'Hash no coincide');assert.equal(a.getAttribute('aria-disabled'),undefined);
  e.control.pdf={ok:true,status:200,headers:{get:()=> 'application/pdf'},blob:async()=>({})};
  await e.$('contenido-patente').eventos.click({target:{closest:()=>a},preventDefault(){}});assert.equal(e.control.descargas,1);
});
test('descarga con sesión vencida redirige sin crear archivo',async()=> {
  const e=entorno(datos());await e.listo();e.control.pdf={status:401};const a=enlace();
  await e.$('contenido-patente').eventos.click({target:{closest:()=>a},preventDefault(){}});
  assert.equal(e.control.descargas,0);assert.equal(e.contexto.location.href,'/login.html?expirada=1');
});
