import navesitas.Hu13 as H
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
String agente = H.login('agente@navesitas.pa'), dgmm = H.login('revisor@navesitas.pa')
// ITC obligatorio y SMC no aplica en la misma nave, durante todo el flujo.
def id = H.nave(agente, [eslora:24])
H.preparar(agente,id)
['CERTIFICADO_PROPIEDAD','PODER_NOTARIAL'].each { H.cargar(agente,id,it) }
def fallo = H.accion(dgmm,id,'ADMITIR')
WS.verifyResponseStatusCode(fallo,409)
assert H.json(fallo).faltantes == ['ITC']
H.cargar(agente,id,'ITC')
WS.verifyResponseStatusCode(H.accion(dgmm,id,'ADMITIR'),200)
WS.verifyResponseStatusCode(H.accion(dgmm,id,'OBSERVAR'),200)
WS.verifyResponseStatusCode(H.accion(agente,id,'SUBSANAR'),200)
WS.verifyResponseStatusCode(H.accion(dgmm,id,'APROBAR'),409)
def docs = H.json(H.pedir(dgmm,'/api/naves/'+id+'/documentos'))
assert docs*.tipo.sort() == ['CERTIFICADO_PROPIEDAD','ITC','PODER_NOTARIAL']
docs.each { d -> WS.verifyResponseStatusCode(H.pedir(dgmm,'/api/naves/'+id+'/documentos/'+d.id+'/revision',[decision:'APROBAR']),200) }
WS.verifyResponseStatusCode(H.accion(dgmm,id,'APROBAR'),200)
// Un pendiente no se resuelve cargando todos los certificados.
def pendiente = H.nave(agente,[servicio:'CABOTAJE'])
H.preparar(agente,pendiente)
['CERTIFICADO_PROPIEDAD','PODER_NOTARIAL','ITC','SMC'].each { H.cargar(agente,pendiente,it) }
def bloqueo = H.accion(dgmm,pendiente,'ADMITIR')
WS.verifyResponseStatusCode(bloqueo,409)
assert H.json(bloqueo).mensaje.contains('REQUIERE_REVISION')
