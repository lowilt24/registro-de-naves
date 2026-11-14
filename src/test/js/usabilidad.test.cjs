const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

function pantalla(responder) {
  const mapa=new Map(), almacenamiento=new Map();
  function elemento(id='') {
    const e={id,value:'',textContent:'',innerHTML:'',disabled:false,hidden:false,dataset:{},atributos:{},eventos:{},hijos:[],
      setAttribute(k,v){this.atributos[k]=v;},addEventListener(k,v){this.eventos[k]=v;},
      append(...h){h.forEach(x=>{this.hijos.push(x);if(x.id)mapa.set(x.id,x);});},
      appendChild(x){this.append(x);},querySelector(){return null;},
      querySelectorAll(){return [];},classList:{remove(){},add(){}}};
    if(id)mapa.set(id,e);return e;
  }
  const body=elemento();
  const ctx=vm.createContext({URLSearchParams,Error,JSON,document:{body,cookie:'XSRF-TOKEN=csrf-prueba',
    getElementById:id=>mapa.get(id)||null,createElement:()=>elemento(),querySelectorAll:()=>[],querySelector:()=>null},
    sessionStorage:{getItem:k=>almacenamiento.get(k),setItem:(k,v)=>almacenamiento.set(k,v),removeItem:k=>almacenamiento.delete(k)},
    location:{href:'',search:''},window:{addEventListener(){}},fetch:responder});
  return {ctx,elemento,mapa,almacenamiento,ejecutar:s=>vm.runInContext(s,ctx)};
}
const respuesta=(status,datos)=>({status,ok:status>=200&&status<300,text:async()=>JSON.stringify(datos),json:async()=>datos});
function comunes(p){p.ejecutar(fs.readFileSync('src/main/resources/static/js/comun.js','utf8'));}
function acceso(p,archivo){
  for(const id of ['form-login','btn-entrar','aviso-login','form-reenvio','aviso-reenvio','correo-reenvio','form-registro','aviso-registro','btn-crear','nombreCompleto','correo','password','rol'])p.elemento(id);
  const html=fs.readFileSync('src/main/resources/static/'+archivo,'utf8');
  for(const m of html.matchAll(/<script>([\s\S]*?)<\/script>/g))p.ejecutar(m[1]);
}
test('solo una respuesta de guardado exitosa anuncia éxito y mantiene CSRF',async()=>{
  let opciones;
  const p=pantalla(async(r,o)=>{opciones=o;return respuesta(201,{id:8});});comunes(p);
  await p.ejecutar("api('/api/naves',{metodo:'POST',cuerpo:{nombre:'Prueba'}})");
  assert.match(p.mapa.get('aviso-global-texto').textContent,/Nave registrada correctamente/);
  assert.equal(opciones.headers['X-XSRF-TOKEN'],'csrf-prueba');
});
test('rechazo del servidor anuncia error y no un guardado',async()=>{
  const p=pantalla(async()=>respuesta(409,{mensaje:'Nombre ocupado'}));comunes(p);
  const r=await p.ejecutar("api('/api/naves',{metodo:'POST',cuerpo:{}})");
  assert.equal(r.ok,false);assert.equal(p.mapa.get('aviso-global').dataset.estado,'ocupado');
  assert.equal(p.mapa.get('aviso-global-texto').textContent,'Nombre ocupado');
});
test('sin conexión se informa el error y 401 conserva la redirección al login',async()=>{
  const p=pantalla(async()=>{throw Error('offline');});comunes(p);
  await assert.rejects(p.ejecutar("api('/api/naves')"));
  assert.match(p.mapa.get('aviso-global-texto').textContent,/No se pudo conectar/);
  const q=pantalla(async()=>respuesta(401,{}));comunes(q);
  await assert.rejects(q.ejecutar("api('/api/naves')"),/sin sesion/);
  assert.equal(q.ctx.location.href,'/login.html?expirada=1');
});
test('confirmación de login solo tras sesión real y una sola vez',async()=>{
  const p=pantalla(async()=>respuesta(200,{autenticado:true,nombreCompleto:'Persona'}));
  p.elemento('sesion');p.elemento('btn-salir');p.almacenamiento.set('navesitas-login-pendiente','1');comunes(p);
  await p.ejecutar('pintarSesion()');
  assert.match(p.mapa.get('aviso-global-texto').textContent,/Sesión iniciada correctamente/);
  p.mapa.get('aviso-global').hidden=true;
  await p.ejecutar('pintarSesion()');assert.equal(p.mapa.get('aviso-global').hidden,true);
  const q=pantalla(async()=>respuesta(200,{autenticado:false}));q.elemento('sesion');comunes(q);
  q.almacenamiento.set('navesitas-login-pendiente','1');await q.ejecutar('pintarSesion()');
  assert.equal(q.mapa.has('aviso-global'),false);
});
test('registrar cuenta bloquea doble envío y recupera el botón tras error',async()=>{
  let resolver,llamadas=0;
  const p=pantalla(()=>{llamadas++;return new Promise(r=>resolver=r);});acceso(p,'registro-usuario.html');
  const enviar=p.mapa.get('form-registro').eventos.submit;
  const primera=enviar({preventDefault(){}});
  await enviar({preventDefault(){}});assert.equal(llamadas,1);assert.equal(p.mapa.get('btn-crear').disabled,true);
  resolver(respuesta(400,{mensaje:'Revise los campos'}));await primera;
  assert.equal(p.mapa.get('btn-crear').disabled,false);assert.equal(p.ctx.location.href,'');
  assert.equal(p.mapa.get('aviso-registro').dataset.estado,'ocupado');
});
test('registro exitoso conduce a validación y no afirma entrega de correo',async()=>{
  const p=pantalla(async()=>respuesta(201,{}));acceso(p,'registro-usuario.html');
  await p.mapa.get('form-registro').eventos.submit({preventDefault(){}});
  assert.equal(p.ctx.location.href,'/login.html?creado=1');
  assert.match(p.mapa.get('aviso-registro').textContent,/Cuenta registrada correctamente/);
  assert.doesNotMatch(p.mapa.get('aviso-registro').textContent,/entregado|enviado/);
});
test('aviso se puede cerrar y el texto nunca se interpreta como HTML',()=>{
  const p=pantalla();comunes(p);p.ejecutar("notificar('<img src=x onerror=alert(1)>')");
  assert.equal(p.mapa.get('aviso-global-texto').textContent,'<img src=x onerror=alert(1)>');
  assert.equal(p.mapa.get('aviso-global-texto').innerHTML,'');
  p.mapa.get('aviso-global').hijos[1].eventos.click();assert.equal(p.mapa.get('aviso-global').hidden,true);
});
