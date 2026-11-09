/* HU-18: las decisiones y explicaciones proceden exclusivamente de HU-13 en el servidor. */
const NOMBRES_GUIA={CERTIFICADO_PROPIEDAD:'Certificado de propiedad',PODER_NOTARIAL:'Poder notarial',ITC:'Certificado de arqueo (ITC)',SMC:'Gestión de la seguridad (SMC)'};
const ESTADOS_GUIA={OBLIGATORIO:'Obligatorio',NO_APLICA:'No aplica',REQUIERE_REVISION:'Requiere revisión'};
function pintarGuia(datos) {
  $('documentos-guia').innerHTML=datos.documentos.map(d=>'<article><h4>'+escapar(NOMBRES_GUIA[d.tipo]||d.tipo)
    +' <span class="doc-estado" data-estado="'+escapar(d.estado)+'">'+escapar(ESTADOS_GUIA[d.estado]||d.estado)
    +'</span></h4><p>'+escapar(d.explicacion)+'</p></article>').join('');
  $('pasos-guia').innerHTML=datos.pasos.map(p=>'<li>'+escapar(p)+'</li>').join('');
  $('resultado-guia').classList.remove('oculto');
  mostrar($('aviso-guia'),datos.requiereRevision?'neutro':'libre',datos.requiereRevision
    ?'Hay requisitos que requieren revisión. No se puede confirmar el expediente ni avanzar hasta aclararlos.'
    :'Estos son los requisitos aplicables a los datos consultados. El expediente se revisará antes de aprobar el registro.');
}
$('form-guia').addEventListener('input',()=>{$('resultado-guia').classList.add('oculto');ocultar($('aviso-guia'));});
$('form-guia').addEventListener('submit',async e=>{
  e.preventDefault(); limpiarErrores(); ocultar($('aviso-guia'));$('resultado-guia').classList.add('oculto');
  $('campos-guia').disabled=true;
  try {
    const {ok,datos}=await api('/api/guia-requisitos',{metodo:'POST',cuerpo:{tipo:$('guia-tipo').value||null,
      servicio:$('guia-servicio').value||null,eslora:$('guia-eslora').value||null,tonelajeBruto:$('guia-arqueo').value||null}});
    if(!ok) {pintarErrores(datos);mostrar($('aviso-guia'),'ocupado',datos?.mensaje||'No se pudo consultar la guía.');return;}
    pintarGuia(datos);
  } catch(e) {if(e.message!=='sin sesion')mostrar($('aviso-guia'),'ocupado','No se pudo contactar el servidor.');}
  finally {$('campos-guia').disabled=false;}
});
pintarSesion();
