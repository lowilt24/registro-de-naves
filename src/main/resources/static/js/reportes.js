/* HU-17: solo presenta los agregados del servidor; no calcula estadisticas. */
const ROTULOS_REPORTE = {
  CARGA:'Carga', PASAJEROS:'Pasajeros', MIXTA:'Mixta', TANQUERO:'Tanquero', REMOLCADOR:'Remolcador', PESQUERA:'Pesquera',
  REGISTRADA:'Registrada', DOCUMENTOS_CARGADOS:'Documentos cargados', EN_REVISION:'En revisión', RECHAZADA:'Rechazada',
  CERTIFICADO_PROPIEDAD:'Certificado de propiedad', PODER_NOTARIAL:'Poder notarial', ITC:'Certificado de arqueo (ITC)', SMC:'Gestión de la seguridad (SMC)'
};
function filtrosReporte() {
  const p=new URLSearchParams();
  for(const campo of ['desde','hasta']) if($(campo).value) p.set(campo,$(campo).value);
  return p.toString()?'?'+p.toString():'';
}
function pintarConteos(id,datos) {
  $(id).innerHTML=datos.map(c=>'<tr><td>'+escapar(ROTULOS_REPORTE[c.codigo]||c.codigo)+'</td><td class="cifra">'+escapar(c.cantidad)+'</td></tr>').join('');
}
function pintarReporte(r) {
  pintarConteos('reporte-tipos',r.navesPorTipo); pintarConteos('reporte-naves',r.navesPorEstado);
  pintarConteos('reporte-documentos',r.documentosPendientes);
  $('reporte-tramites').innerHTML=r.tramitesPorEstado.map(c=>'<tr><td>'+etiquetaTramite(c.codigo)
    +'</td><td class="cifra">'+escapar(c.cantidad)+'</td><td class="cifra">'
    +(c.diasPromedioEspera==null?'—':escapar(Number(c.diasPromedioEspera).toFixed(2)))+'</td></tr>').join('');
  $('fecha-reporte').textContent='Calculado al '+r.calculadoAl+'. Desde: '+(r.desde||'sin límite')+'. Hasta: '+(r.hasta||'sin límite')+'.';
  $('resultados-reportes').classList.remove('oculto'); $('descargar-csv').disabled=false;
}
async function cargarReporte() {
  $('filtros-reportes').disabled=true; $('descargar-csv').disabled=true;
  $('resultados-reportes').classList.add('oculto'); limpiarErrores(); ocultar($('aviso-reportes'));
  try {
    const {ok,datos}=await api('/api/reportes/registro'+filtrosReporte());
    if(!ok) { pintarErrores(datos); mostrar($('aviso-reportes'),'ocupado',datos?.mensaje||'No se pudo consultar el reporte.'); return; }
    pintarReporte(datos);
  } catch(e) { if(e.message!=='sin sesion') mostrar($('aviso-reportes'),'ocupado','No se pudo contactar el servidor.'); }
  finally { $('filtros-reportes').disabled=false; }
}
$('form-reportes').addEventListener('submit',e=>{e.preventDefault();cargarReporte();});
$('form-reportes').addEventListener('input',()=>{$('resultados-reportes').classList.add('oculto');$('descargar-csv').disabled=true;});
$('limpiar-reportes').addEventListener('click',()=>{$('form-reportes').reset();cargarReporte();});
$('descargar-csv').addEventListener('click',async()=>{
  if($('descargar-csv').disabled) return;
  $('descargar-csv').disabled=true; $('filtros-reportes').disabled=true;
  try {
    const r=await fetch('/api/reportes/registro.csv'+filtrosReporte(),{credentials:'same-origin',headers:{Accept:'text/csv, application/json'}});
    if(r.status===401) {location.href='/login.html?expirada=1';return;}
    if(!r.ok) {const d=await r.json().catch(()=>null);mostrar($('aviso-reportes'),'ocupado',d?.mensaje||'No se pudo descargar el CSV.');return;}
    if(!r.headers.get('Content-Type')?.includes('text/csv')) throw new Error('Respuesta sin CSV');
    const url=URL.createObjectURL(await r.blob()); const enlace=document.createElement('a');
    enlace.href=url; enlace.download='reporte-registro.csv';document.body.appendChild(enlace);enlace.click();enlace.remove();
    setTimeout(()=>URL.revokeObjectURL(url),1000);
  } catch(e) {mostrar($('aviso-reportes'),'ocupado','No se pudo descargar el CSV. Intente de nuevo.');}
  finally {$('descargar-csv').disabled=false;$('filtros-reportes').disabled=false;}
});
pintarSesion(); cargarReporte();
