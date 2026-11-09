const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
const reporte={desde:null,hasta:null,calculadoAl:'2026-11-09',navesPorTipo:[{codigo:'CARGA',cantidad:2}],
 navesPorEstado:[{codigo:'REGISTRADA',cantidad:2}],tramitesPorEstado:[{codigo:'EN_REVISION',cantidad:1,diasPromedioEspera:0},{codigo:'APROBADA',cantidad:1,diasPromedioEspera:null}],documentosPendientes:[{codigo:'ITC',cantidad:1}]};
function entorno(script) {
 const elementos=new Map(),registro={peticiones:[],descargas:0};
 const $=id=>{
  if(!elementos.has(id)) {
   const e={value:'',disabled:false,innerHTML:'',textContent:'',oculto:false,eventos:{},addEventListener(tipo,fn){this.eventos[tipo]=fn;},reset(){ $('desde').value='';$('hasta').value=''; }};
   e.classList={add(){e.oculto=true;},remove(){e.oculto=false;}};elementos.set(id,e);
  }
  return elementos.get(id);
 };
 const escapar=v=>String(v).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;');
 const control={respuesta:{ok:true,datos:reporte},fetch:{ok:true,status:200,headers:{get:()=> 'text/csv'},blob:async()=>({csv:true})}};
 const context=vm.createContext({ $,URLSearchParams,Number,location:{href:''},escapar,
  etiquetaTramite:c=>'<span>'+escapar(c)+'</span>',pintarSesion(){},limpiarErrores(){registro.errores=null;},pintarErrores(d){registro.errores=d;},
  ocultar(e){e.mensaje='';},mostrar(e,estado,mensaje){e.estado=estado;e.mensaje=mensaje;},
  async api(url,opciones){registro.peticiones.push({url,opciones});if(control.respuesta instanceof Error)throw control.respuesta;return control.respuesta;},
  async fetch(url){registro.csvUrl=url;return control.fetch;},
  URL:{createObjectURL(){return 'blob:csv';},revokeObjectURL(){registro.revocado=true;}},
  document:{body:{appendChild(e){registro.enlace=e;}},createElement(){return {click(){registro.descargas++;},remove(){}};}},
  setTimeout(callback){registro.liberar=callback;}
 });
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/static/js/',script),'utf8'),context);
 return { $,context,control,registro,listo:()=>new Promise(setImmediate) };
}
test('reporte presenta ceros y finales sin inventar promedios',async()=>{
 const e=entorno('reportes.js');await e.listo();
 assert.ok(e.$('reporte-tramites').innerHTML.includes('0.00'));
 assert.ok(e.$('reporte-tramites').innerHTML.includes('—'));
 assert.ok(!e.$('reporte-tramites').innerHTML.includes('null'));
 assert.equal(e.$('descargar-csv').disabled,false);
});
test('reporte usa los mismos filtros al consultar y exportar CSV',async()=>{
 const e=entorno('reportes.js');await e.listo();e.$('desde').value='2026-11-01';e.$('hasta').value='2026-11-09';
 await e.context.cargarReporte();await e.$('descargar-csv').eventos.click();
 assert.equal(e.registro.peticiones.at(-1).url,'/api/reportes/registro?desde=2026-11-01&hasta=2026-11-09');
 assert.equal(e.registro.csvUrl,'/api/reportes/registro.csv?desde=2026-11-01&hasta=2026-11-09');
 assert.equal(e.registro.enlace.download,'reporte-registro.csv');assert.equal(e.registro.descargas,1);
 e.registro.liberar();assert.equal(e.registro.revocado,true);
});
test('filtro rechazado oculta cifras anteriores y muestra el error',async()=>{
 const e=entorno('reportes.js');await e.listo();e.control.respuesta={ok:false,datos:{mensaje:'Rango inválido',campos:{fechas:'Revise fechas'}}};
 await e.context.cargarReporte();assert.equal(e.$('resultados-reportes').oculto,true);
 assert.equal(e.$('descargar-csv').disabled,true);assert.equal(e.$('aviso-reportes').mensaje,'Rango inválido');
 assert.equal(e.$('filtros-reportes').disabled,false);
});
test('cambiar fechas invalida el reporte mostrado',async()=>{
 const e=entorno('reportes.js');await e.listo();e.$('form-reportes').eventos.input();
 assert.equal(e.$('resultados-reportes').oculto,true);assert.equal(e.$('descargar-csv').disabled,true);
});
test('CSV rechazado no descarga JSON y sesión vencida redirige',async()=>{
 const e=entorno('reportes.js');await e.listo();
 e.control.fetch={ok:false,status:403,json:async()=>({mensaje:'Sin permiso'})};await e.$('descargar-csv').eventos.click();
 assert.equal(e.registro.descargas,0);assert.equal(e.$('aviso-reportes').mensaje,'Sin permiso');
 e.control.fetch={status:401};await e.$('descargar-csv').eventos.click();assert.equal(e.context.location.href,'/login.html?expirada=1');
});
test('guia presenta la decision recibida y escapa la explicación',()=>{
 const e=entorno('guia-requisitos.js');
 e.context.pintarGuia({documentos:[{tipo:'ITC',estado:'REQUIERE_REVISION',explicacion:'Falta dato <script>alert(1)</script>'}],requiereRevision:true,pasos:['Consultar nombre','Registrar nave']});
 assert.ok(e.$('documentos-guia').innerHTML.includes('Requiere revisión'));
 assert.ok(!e.$('documentos-guia').innerHTML.includes('<script>'));
 assert.ok(e.$('documentos-guia').innerHTML.includes('&lt;script&gt;'));
 assert.equal(e.$('aviso-guia').estado,'neutro');assert.ok(e.$('aviso-guia').mensaje.includes('No se puede confirmar'));
});
test('guia envia medidas ausentes como null y no altera los campos de entrada',async()=>{
 const e=entorno('guia-requisitos.js');e.$('guia-tipo').value='PASAJEROS';e.$('guia-servicio').value='INTERNACIONAL';
 e.control.respuesta={ok:true,datos:{documentos:[],requiereRevision:true,pasos:[]}};
 await e.$('form-guia').eventos.submit({preventDefault(){}});
 const p=e.registro.peticiones.at(-1);assert.equal(p.url,'/api/guia-requisitos');assert.equal(p.opciones.metodo,'POST');
 assert.equal(p.opciones.cuerpo.eslora,null);assert.equal(p.opciones.cuerpo.tonelajeBruto,null);
 assert.equal(e.$('guia-tipo').value,'PASAJEROS');assert.equal(e.$('campos-guia').disabled,false);
});
test('guia rechazada o error de red no deja requisitos obsoletos visibles',async()=>{
 const e=entorno('guia-requisitos.js');e.control.respuesta={ok:false,datos:{mensaje:'Datos inválidos'}};
 await e.$('form-guia').eventos.submit({preventDefault(){}});
 assert.equal(e.$('resultado-guia').oculto,true);assert.equal(e.$('aviso-guia').mensaje,'Datos inválidos');
 e.control.respuesta=new Error('sin red');await e.$('form-guia').eventos.submit({preventDefault(){}});
 assert.equal(e.$('campos-guia').disabled,false);assert.equal(e.$('aviso-guia').estado,'ocupado');
});
test('guia descarta la orientación anterior al editar los datos',()=>{
 const e=entorno('guia-requisitos.js');e.$('form-guia').eventos.input();assert.equal(e.$('resultado-guia').oculto,true);
});
